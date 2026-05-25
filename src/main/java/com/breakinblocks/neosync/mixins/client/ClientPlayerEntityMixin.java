package com.breakinblocks.neosync.mixins.client;

import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.breakinblocks.neosync.api.networking.SynchronizationRequestPacket;
import com.breakinblocks.neosync.api.shell.ClientShell;
import com.breakinblocks.neosync.api.shell.ShellPriority;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.client.entity.PersistentCameraEntity;
import com.breakinblocks.neosync.client.entity.PersistentCameraEntityGoal;
import com.breakinblocks.neosync.client.gui.controller.DeathScreenController;
import com.breakinblocks.neosync.client.gui.hud.HudController;
import com.breakinblocks.neosync.common.entity.KillableEntity;
import com.breakinblocks.neosync.common.entity.LookingEntity;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.breakinblocks.neosync.common.utils.WorldUtil;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
@Mixin(LocalPlayer.class)
public abstract class ClientPlayerEntityMixin extends AbstractClientPlayer implements ClientShell, KillableEntity, LookingEntity {
    @Final
    @Shadow
    protected Minecraft minecraft;

    @Unique
    private boolean sync$isArtificial = false;

    @Unique
    private ConcurrentMap<UUID, ShellState> sync$shellsById = new ConcurrentHashMap<>();

    @Unique
    @Nullable
    private UUID neosync$currentShellUuid;

    @Unique
    @Nullable
    private UUID neosync$pendingShellUuid;

    @Unique
    private boolean neosync$pendingSyncWasDeath;

    private ClientPlayerEntityMixin(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Override
    public @Nullable PlayerSyncEvents.SyncFailureReason beginSync(
        ShellState state,
        @Nullable BlockPos currentContainerPos
    ) {
        ClientLevel world = this.clientLevel;
        if (world == null) {
            return PlayerSyncEvents.SyncFailureReason.OTHER_PROBLEM;
        }

        PlayerSyncEvents.SyncFailureReason failureReason =
            this.canBeApplied(state) && state.getProgress() >= ShellState.PROGRESS_DONE
                ? PlayerSyncEvents.ALLOW_SYNCING.invoker().allowSync(this, state)
                : PlayerSyncEvents.SyncFailureReason.INVALID_SHELL;

        if (failureReason != null) {
            NeoSyncDebug.warn(
                "client-sync",
                "beginSync denied state={} reason={}",
                describeShell(state),
                failureReason.toText().getString()
            );
            return failureReason;
        }

        this.neosync$pendingShellUuid = state == null ? null : state.getUuid();
        this.neosync$pendingSyncWasDeath = this.isDeadOrDying();
        PlayerSyncEvents.START_SYNCING.invoker().onStartSyncing(this, state);

        BlockPos pos = this.blockPosition();
        BlockPos cameraPos = NeoSyncSableCompat.projectOut(world, pos);
        BlockPos cameraTargetPos = NeoSyncSableCompat.projectOut(world, state.getPos());
        Direction facing = BlockPosUtil.getHorizontalFacing(pos, world)
            .orElse(this.getDirection().getOpposite());

        SynchronizationRequestPacket request = new SynchronizationRequestPacket(state, currentContainerPos);

        NeoSyncDebug.info(
            "client-sync",
            "beginSync state={} currentPos={} cameraPos={} targetRaw={} cameraTarget={} currentContainer={} deathSync={}",
            describeShell(state),
            pos,
            cameraPos,
            state.getPos(),
            cameraTargetPos,
            currentContainerPos,
            this.neosync$pendingSyncWasDeath
        );

        PersistentCameraEntityGoal cameraGoal = this.neosync$pendingSyncWasDeath
            ? PersistentCameraEntityGoal.limbo(cameraPos, facing, cameraTargetPos, __ -> request.send())
            : PersistentCameraEntityGoal.stairwayToHeaven(cameraPos, facing, cameraTargetPos, __ -> request.send());

        HudController.hide();

        if (this.isDeadOrDying()) {
            DeathScreenController.suspend();
        }

        this.minecraft.setScreen(null);
        PersistentCameraEntity.setup(this.minecraft, cameraGoal);
        return null;
    }

    @Override
    public void endSync(
        ResourceLocation startWorld,
        BlockPos startPos,
        Direction startFacing,
        ResourceLocation targetWorld,
        BlockPos targetPos,
        Direction targetFacing,
        @Nullable ShellState storedState
    ) {
        boolean syncFailed = Objects.equals(startWorld, targetWorld) && Objects.equals(startPos, targetPos);
        boolean wasDeathSync = this.neosync$pendingSyncWasDeath;
        this.neosync$pendingSyncWasDeath = false;

        NeoSyncDebug.info(
            "client-sync",
            "endSync failed={} start={} {} target={} {} storedState={} pendingUuid={} deathSync={}",
            syncFailed,
            startWorld,
            startPos,
            targetWorld,
            targetPos,
            describeShell(storedState),
            this.neosync$pendingShellUuid,
            wasDeathSync
        );

        if (syncFailed) {
            this.neosync$pendingShellUuid = null;
            PersistentCameraEntity.unset(this.minecraft);
            HudController.restore();
            DeathScreenController.restore();
            return;
        }

        this.neosync$currentShellUuid = this.neosync$pendingShellUuid;
        this.neosync$pendingShellUuid = null;

        if (this.getHealth() <= 0) {
            this.setHealth(0.01F);
        }

        this.deathTime = 0;

        float yaw = targetFacing.getOpposite().toYRot();
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yBodyRotO = this.yBodyRot = yaw;
        this.yHeadRotO = this.yHeadRot = yaw;
        this.setXRot(0);
        this.xRotO = 0;

        Runnable restore = () -> {
            PersistentCameraEntity.unset(this.minecraft);
            HudController.restore();
            DeathScreenController.restore();
            PlayerSyncEvents.STOP_SYNCING.invoker().onStopSyncing(this, startPos, storedState);
        };

        boolean enableCamera = Objects.equals(startWorld, targetWorld);
        if (enableCamera) {
            PersistentCameraEntityGoal cameraGoal = wasDeathSync
                ? createDeathArrivalGoal(startPos, startFacing, targetPos, targetFacing, restore)
                : PersistentCameraEntityGoal.highwayToHell(startPos, startFacing, targetPos, targetFacing, __ -> restore.run());
            PersistentCameraEntity.setup(this.minecraft, cameraGoal);
        } else {
            restore.run();
        }
    }

    @Unique
    private static PersistentCameraEntityGoal createDeathArrivalGoal(
        BlockPos startPos,
        Direction startFacing,
        BlockPos targetPos,
        Direction targetFacing,
        Runnable onFinished
    ) {
        Vec3 center = new Vec3(
            (startPos.getX() + targetPos.getX()) * 0.5D + 0.5D,
            PersistentCameraEntityGoal.MAX_Y,
            (startPos.getZ() + targetPos.getZ()) * 0.5D + 0.5D
        );

        return PersistentCameraEntityGoal.tp(
            new BlockPos((int) Math.floor(center.x), (int) center.y, (int) Math.floor(center.z)),
            startFacing.toYRot(),
            90
        ).then(
            PersistentCameraEntityGoal.highwayToHell(
                startPos,
                startFacing,
                targetPos,
                targetFacing,
                __ -> onFinished.run()
            )
        );
    }

    @Override
    public UUID getShellOwnerUuid() {
        return this.getGameProfile().getId();
    }

    @Override
    public boolean isArtificial() {
        return this.sync$isArtificial;
    }

    @Override
    public void changeArtificialStatus(boolean isArtificial) {
        NeoSyncDebug.info(
            "client-shell",
            "changeArtificialStatus {} -> {}",
            this.sync$isArtificial,
            isArtificial
        );
        this.sync$isArtificial = isArtificial;
    }

    @Override
    public void setAvailableShellStates(Stream<ShellState> states) {
        this.sync$shellsById = states.collect(
            Collectors.toConcurrentMap(ShellState::getUuid, x -> x)
        );
        NeoSyncDebug.info(
            "client-shell",
            "setAvailableShellStates count={}",
            this.sync$shellsById.size()
        );
    }

    @Override
    public Stream<ShellState> getAvailableShellStates() {
        return this.sync$shellsById.values().stream();
    }

    @Override
    public ShellState getShellStateByUuid(UUID uuid) {
        return uuid == null ? null : this.sync$shellsById.get(uuid);
    }

    @Override
    public void add(ShellState state) {
        if (!shouldTrackShellState(state)) {
            NeoSyncDebug.warn("client-shell", "add ignored null/invalid state");
            return;
        }

        NeoSyncDebug.info("client-shell", "add state={}", describeShell(state));
        this.sync$shellsById.put(state.getUuid(), state);
    }

    @Override
    public void remove(ShellState state) {
        if (state != null) {
            NeoSyncDebug.info("client-shell", "remove state={}", describeShell(state));
            this.sync$shellsById.remove(state.getUuid());
        }
    }

    @Override
    public void update(ShellState state) {
        if (!shouldTrackShellState(state)) {
            NeoSyncDebug.warn("client-shell", "update ignored null/invalid state");
            return;
        }

        NeoSyncDebug.info("client-shell", "update state={}", describeShell(state));
        this.sync$shellsById.put(state.getUuid(), state);
    }

    @Override
    public boolean changeLookingEntityLookDirection(double cursorDeltaX, double cursorDeltaY) {
        return this.minecraft.getCameraEntity() instanceof PersistentCameraEntity;
    }

    @Override
    public void onKillableEntityDeath() {
        BlockPos pos = this.blockPosition();
        ResourceLocation world = WorldUtil.getId(this.level());
        Comparator<ShellState> comparator = ShellPriority.asComparator(world, pos, ShellPriority.NATURAL);

        ShellState respawnShell = this.sync$shellsById.values().stream()
            .filter(ClientPlayerEntityMixin::isFinishedShell)
            .min(comparator)
            .orElse(null);

        if (respawnShell == null) {
            NeoSyncDebug.warn(
                "client-sync",
                "death-triggered beginSync found no valid respawn shell availableCount={}",
                this.sync$shellsById.size()
            );
            return;
        }

        NeoSyncDebug.info(
            "client-sync",
            "death-triggered beginSync using respawn shell={}",
            describeShell(respawnShell)
        );

        PlayerSyncEvents.SyncFailureReason failureReason = this.beginSync(respawnShell);
        if (failureReason != null) {
            NeoSyncDebug.warn(
                "client-sync",
                "death-triggered beginSync failed shell={} reason={}",
                describeShell(respawnShell),
                failureReason.toText().getString()
            );
        }
    }

    @Inject(method = "aiStep", at = @At("HEAD"), cancellable = true)
    private void sync$updatePostDeath(CallbackInfo ci) {
        if (this.isDeadOrDying()) {
            if (this.minecraft.screen instanceof DeathScreen) {
                this.deathTime = Mth.clamp(this.deathTime, 0, 19);
            } else {
                this.deathTime = Mth.clamp(++this.deathTime, 0, 20);
                if (this.updateKillableEntityPostDeath()) {
                    ci.cancel();
                }
            }
        }
    }

    @Override
    public @Nullable UUID neosync$getCurrentShellUuid() {
        return this.neosync$currentShellUuid;
    }

    @Override
    public void neosync$setCurrentShellUuid(@Nullable UUID uuid) {
        NeoSyncDebug.info(
            "client-shell",
            "set current shell uuid {} -> {}",
            this.neosync$currentShellUuid,
            uuid
        );
        this.neosync$currentShellUuid = uuid;
    }

    @Unique
    private static boolean shouldTrackShellState(@Nullable ShellState state) {
        return state != null;
    }

    @Unique
    private static boolean isFinishedShell(@Nullable ShellState state) {
        return state != null && state.getProgress() >= ShellState.PROGRESS_DONE;
    }

    @Unique
    private static String describeShell(@Nullable ShellState state) {
        if (state == null) {
            return "null";
        }

        return "uuid=" + state.getUuid()
            + ",owner=" + state.getOwnerName()
            + ",progress=" + state.getProgress()
            + ",pos=" + (state.getPos() == null ? "null" : state.getPos().toShortString())
            + ",world=" + state.getWorld();
    }
}
