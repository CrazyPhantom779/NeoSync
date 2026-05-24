package com.breakinblocks.neosync.mixins.client;

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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.breakinblocks.neosync.api.networking.SynchronizationRequestPacket;
import com.breakinblocks.neosync.api.shell.ClientShell;
import com.breakinblocks.neosync.api.shell.ShellPriority;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.client.gui.controller.DeathScreenController;
import com.breakinblocks.neosync.client.gui.hud.HudController;
import com.breakinblocks.neosync.common.entity.KillableEntity;
import com.breakinblocks.neosync.common.entity.LookingEntity;
import com.breakinblocks.neosync.client.entity.PersistentCameraEntity;
import com.breakinblocks.neosync.client.entity.PersistentCameraEntityGoal;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import com.breakinblocks.neosync.common.utils.WorldUtil;
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
public abstract class  ClientPlayerEntityMixin extends AbstractClientPlayer implements ClientShell, KillableEntity, LookingEntity {
    @Final
    @Shadow
    protected Minecraft minecraft;

    @Unique
    private boolean sync$isArtificial = false;

    @Unique
    private ConcurrentMap<UUID, ShellState> sync$shellsById = new ConcurrentHashMap<>();

    private ClientPlayerEntityMixin(ClientLevel world, GameProfile profile) {
        super(world, profile);
    }

    @Override
    public @Nullable PlayerSyncEvents.SyncFailureReason beginSync(ShellState state) {
        ClientLevel world = this.clientLevel;
        if (world == null) {
            return PlayerSyncEvents.SyncFailureReason.OTHER_PROBLEM;
        }

        PlayerSyncEvents.SyncFailureReason failureReason =
                this.canBeApplied(state) && state.getProgress() >= ShellState.PROGRESS_DONE
                        ? PlayerSyncEvents.ALLOW_SYNCING.invoker().allowSync(this, state)
                        : PlayerSyncEvents.SyncFailureReason.INVALID_SHELL;

        if (failureReason != null) {
            return failureReason;
        }

        PlayerSyncEvents.START_SYNCING.invoker().onStartSyncing(this, state);

        BlockPos pos = this.blockPosition();
        Direction facing = BlockPosUtil.getHorizontalFacing(pos, world).orElse(this.getDirection().getOpposite());
        SynchronizationRequestPacket request = new SynchronizationRequestPacket(state);
        PersistentCameraEntityGoal cameraGoal = this.isDeadOrDying()
                ? PersistentCameraEntityGoal.limbo(pos, facing, state.getPos(), __ -> request.send())
                : PersistentCameraEntityGoal.stairwayToHeaven(pos, facing, state.getPos(), __ -> request.send());

        HudController.hide();
        if (this.isDeadOrDying()) {
            DeathScreenController.suspend();
        }
        this.minecraft.setScreen(null);
        PersistentCameraEntity.setup(this.minecraft, cameraGoal);
        return null;
    }

    @Override
    public void endSync(ResourceLocation startWorld, BlockPos startPos, Direction startFacing, ResourceLocation targetWorld, BlockPos targetPos, Direction targetFacing, @Nullable ShellState storedState) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        boolean syncFailed = Objects.equals(startPos, targetPos);

        if (!syncFailed) {
            if (this.getHealth() <= 0) {
                this.setHealth(0.01F);
            }
            this.deathTime = 0;
        }

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
            if (!syncFailed) {
                PlayerSyncEvents.STOP_SYNCING.invoker().onStopSyncing(this, startPos, storedState);
            }
        };

        boolean enableCamera = Objects.equals(startWorld, targetWorld);
        if (enableCamera) {
            PersistentCameraEntityGoal cameraGoal = PersistentCameraEntityGoal.highwayToHell(startPos, startFacing, targetPos, targetFacing, __ -> restore.run());
            PersistentCameraEntity.setup(this.minecraft, cameraGoal);
        } else {
            restore.run();
        }
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
        this.sync$isArtificial = isArtificial;
    }

    @Override
    public void setAvailableShellStates(Stream<ShellState> states) {
        this.sync$shellsById = states.collect(Collectors.toConcurrentMap(ShellState::getUuid, x -> x));
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
        if (this.canBeApplied(state)) {
            this.sync$shellsById.put(state.getUuid(), state);
        }
    }

    @Override
    public void remove(ShellState state) {
        if (state != null) {
            this.sync$shellsById.remove(state.getUuid());
        }
    }

    @Override
    public void update(ShellState state) {
        if (this.canBeApplied(state) || state != null && this.sync$shellsById.containsKey(state.getUuid())) {
            this.sync$shellsById.put(state.getUuid(), state);
        }
    }

    @Override
    public boolean changeLookingEntityLookDirection(double cursorDeltaX, double cursorDeltaY) {
        return this.minecraft.getCameraEntity() instanceof PersistentCameraEntity;
    }

    @Override
    public void onKillableEntityDeath() {
        boolean canRespawn = this.sync$shellsById.values().stream()
                .anyMatch(s -> this.canBeApplied(s) && s.getProgress() >= ShellState.PROGRESS_DONE);
        BlockPos pos = this.blockPosition();
        ResourceLocation world = WorldUtil.getId(this.level());
        Comparator<ShellState> comparator = ShellPriority.asComparator(world, pos, ShellPriority.NATURAL);
        ShellState respawnShell = canRespawn ? this.sync$shellsById.values().stream()
                .filter(x -> this.canBeApplied(x) && x.getProgress() >= ShellState.PROGRESS_DONE)
                .min(comparator)
                .orElse(null) : null;
        if (respawnShell != null) {
            this.beginSync(respawnShell);
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
}