package com.breakinblocks.neosync.mixins.common;

import com.breakinblocks.neosync.api.shell.Shell;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.api.shell.ShellStateManager;
import com.breakinblocks.neosync.api.shell.ShellStateUpdateType;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.breakinblocks.neosync.common.utils.nbt.OfflinePlayerNbtManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Tuple;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin implements ShellStateManager {
    @Shadow
    @Final
    private PlayerList playerList;

    @Unique
    private final ConcurrentMap<UUID, ConcurrentMap<UUID, Tuple<ShellStateUpdateType, ShellState>>> sync$pendingShellStates =
        new ConcurrentHashMap<>();

    @Override
    public void setAvailableShellStates(UUID owner, Stream<ShellState> states) {
        List<ShellState> collected = states.toList();
        Shell shell = this.sync$getShellById(owner);
        NeoSyncDebug.info(
            "server-manager",
            "setAvailableShellStates owner={} online={} count={}",
            owner,
            shell != null,
            collected.size()
        );
        if (shell != null) {
            shell.setAvailableShellStates(collected.stream());
        }
    }

    @Override
    public Stream<ShellState> getAvailableShellStates(UUID owner) {
        Shell shell = this.sync$getShellById(owner);
        NeoSyncDebug.info("server-manager", "getAvailableShellStates owner={} online={}", owner, shell != null);
        return shell == null ? Stream.of() : shell.getAvailableShellStates();
    }

    @Override
    public @Nullable ShellState getShellStateByUuid(UUID owner, UUID uuid) {
        Shell shell = this.sync$getShellById(owner);
        NeoSyncDebug.info(
            "server-manager",
            "getShellStateByUuid owner={} uuid={} online={}",
            owner,
            uuid,
            shell != null
        );
        return shell == null ? null : shell.getShellStateByUuid(uuid);
    }

    @Override
    public void add(ShellState state) {
        if (state == null) {
            return;
        }
        Shell shell = this.sync$getShellByItsState(state);
        NeoSyncDebug.info(
            "server-manager",
            "add state={} online={}",
            NeoSyncDebug.describeShell(state),
            shell != null
        );
        if (shell == null) {
            this.sync$putPendingUpdate(state, ShellStateUpdateType.ADD);
        } else {
            shell.add(state);
        }
    }

    @Override
    public void remove(ShellState state) {
        if (state == null) {
            return;
        }
        Shell shell = this.sync$getShellByItsState(state);
        NeoSyncDebug.info(
            "server-manager",
            "remove state={} online={}",
            NeoSyncDebug.describeShell(state),
            shell != null
        );
        if (shell == null) {
            this.sync$putPendingUpdate(state, ShellStateUpdateType.REMOVE);
        } else {
            shell.remove(state);
        }
    }

    @Override
    public void update(ShellState state) {
        if (state == null) {
            return;
        }
        Shell shell = this.sync$getShellByItsState(state);
        NeoSyncDebug.info(
            "server-manager",
            "update state={} online={}",
            NeoSyncDebug.describeShell(state),
            shell != null
        );
        if (shell == null) {
            this.sync$putPendingUpdate(state, ShellStateUpdateType.UPDATE);
        } else {
            shell.update(state);
        }
    }

    @Override
    public Collection<Tuple<ShellStateUpdateType, ShellState>> peekPendingUpdates(UUID owner) {
        Map<UUID, Tuple<ShellStateUpdateType, ShellState>> shells = this.sync$pendingShellStates.get(owner);
        Collection<Tuple<ShellStateUpdateType, ShellState>> updates = shells == null ? List.of() : shells.values();
        NeoSyncDebug.info(
            "server-manager",
            "peekPendingUpdates owner={} count={}",
            owner,
            updates.size()
        );
        return updates;
    }

    @Override
    public void clearPendingUpdates(UUID owner) {
        Collection<Tuple<ShellStateUpdateType, ShellState>> updates = this.peekPendingUpdates(owner);
        NeoSyncDebug.info(
            "server-manager",
            "clearPendingUpdates owner={} count={}",
            owner,
            updates.size()
        );
        this.sync$pendingShellStates.remove(owner);
    }

    @Inject(method = "stopServer", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/players/PlayerList;removeAll()V"))
    private void sync$onShutdown(CallbackInfo ci) {
        for (Map.Entry<UUID, ConcurrentMap<UUID, Tuple<ShellStateUpdateType, ShellState>>> entry :
            this.sync$pendingShellStates.entrySet()) {
            UUID userId = entry.getKey();
            Collection<Tuple<ShellStateUpdateType, ShellState>> updates = entry.getValue().values();
            if (updates.isEmpty()) {
                continue;
            }

            NeoSyncDebug.info(
                "server-manager",
                "flushing pending shell updates for offline owner={} count={}",
                userId,
                updates.size()
            );

            OfflinePlayerNbtManager.editPlayerNbt((MinecraftServer) (Object) this, userId, nbt -> {
                Map<UUID, ShellState> shells = nbt.getList("Shells", Tag.TAG_COMPOUND)
                    .stream()
                    .map(x -> ShellState.fromNbt((CompoundTag) x))
                    .collect(Collectors.toMap(ShellState::getUuid, x -> x));

                for (Tuple<ShellStateUpdateType, ShellState> update : updates) {
                    ShellState state = update.getB();
                    NeoSyncDebug.info(
                        "server-manager",
                        "flushing pending update owner={} kind={} state={}",
                        userId,
                        update.getA(),
                        NeoSyncDebug.describeShell(state)
                    );
                    switch (update.getA()) {
                        case ADD, UPDATE -> {
                            if (userId.equals(state.getOwnerUuid())) {
                                shells.put(state.getUuid(), state);
                            }
                        }
                        case REMOVE -> shells.remove(state.getUuid());
                        case NONE -> {
                        }
                    }
                }

                ListTag shellList = new ListTag();
                shells.values().stream()
                    .map(x -> x.writeNbt(new CompoundTag()))
                    .forEach(shellList::add);
                nbt.put("Shells", shellList);
            });
        }

        this.sync$pendingShellStates.clear();
    }

    @Unique
    private void sync$putPendingUpdate(ShellState state, ShellStateUpdateType updateType) {
        if (state == null || updateType == ShellStateUpdateType.NONE) {
            return;
        }

        ConcurrentMap<UUID, Tuple<ShellStateUpdateType, ShellState>> updates =
            this.sync$pendingShellStates.computeIfAbsent(state.getOwnerUuid(), ignored -> new ConcurrentHashMap<>());
        updates.put(state.getUuid(), new Tuple<>(updateType, state));

        NeoSyncDebug.info(
            "server-manager",
            "queued pending update owner={} kind={} state={} pendingCount={}",
            state.getOwnerUuid(),
            updateType,
            NeoSyncDebug.describeShell(state),
            updates.size()
        );
    }

    @Unique
    private @Nullable Shell sync$getShellById(UUID id) {
        return this.isValidShellOwnerUuid(id) ? (Shell) this.playerList.getPlayer(id) : null;
    }

    @Unique
    private @Nullable Shell sync$getShellByItsState(ShellState state) {
        return this.isValidShellState(state) ? (Shell) this.playerList.getPlayer(state.getOwnerUuid()) : null;
    }
}
