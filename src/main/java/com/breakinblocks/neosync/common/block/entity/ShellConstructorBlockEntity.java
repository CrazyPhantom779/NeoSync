package com.breakinblocks.neosync.common.block.entity;

import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.block.ShellConstructorBlock;
import com.breakinblocks.neosync.common.config.SyncConfig;
import com.breakinblocks.neosync.common.entity.damage.FingerstickDamageSource;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ShellConstructorBlockEntity extends AbstractShellContainerBlockEntity implements IEnergyStorage {
    private static final double INSIDE_RADIUS = 1.15D;
    private static final double INSIDE_RADIUS_SQR = INSIDE_RADIUS * INSIDE_RADIUS;

    public ShellConstructorBlockEntity(BlockPos pos, BlockState state) {
        super(SyncBlockEntities.SHELL_CONSTRUCTOR.get(), pos, state);
    }

    @Override
    public void onServerTick(Level world, BlockPos pos, BlockState state) {
        super.onServerTick(world, pos, state);
        if (!(world instanceof ServerLevel serverLevel)) {
            return;
        }

        BlockState liveState = world.getBlockState(pos);
        if (!(liveState.getBlock() instanceof ShellConstructorBlock) || !liveState.hasProperty(ShellConstructorBlock.HALF)) {
            NeoSyncDebug.warn(
                "constructor",
                "server tick skipped because live state is not constructor at {} live={}",
                NeoSyncDebug.describe(world, pos),
                liveState
            );
            return;
        }

        if (!ShellConstructorBlock.isBottom(liveState)) {
            NeoSyncDebug.warn("constructor", "server tick on upper half ignored at {}", NeoSyncDebug.describe(world, pos));
            return;
        }

        Vec3 bottomCenter = NeoSyncSableCompat.projectBlockCenter(world, pos);
        Vec3 topCenter = NeoSyncSableCompat.projectNeighborCenter(world, pos, Direction.UP);
        List<ServerPlayer> playersInside = getPlayersInsideProjected(serverLevel, bottomCenter, topCenter);
        boolean hasPlayerInside = !playersInside.isEmpty();
        boolean isOpen = ShellConstructorBlock.isOpen(liveState);

        if (isOpen != hasPlayerInside) {
            NeoSyncDebug.info(
                "constructor",
                "door update at {} open={} -> {} shell={} bottomCenter={} topCenter={} playersInside={}",
                NeoSyncDebug.describe(world, pos),
                isOpen,
                hasPlayerInside,
                NeoSyncDebug.describeShell(this.shell),
                NeoSyncDebug.describeVec(bottomCenter),
                NeoSyncDebug.describeVec(topCenter),
                playersInside.size()
            );
            ShellConstructorBlock.setOpen(liveState, world, pos, hasPlayerInside);
            this.setChanged();
            this.sync("constructor-door");
        }

        if (this.shell != null && hasPlayerInside) {
            this.ejectPlayers(serverLevel, pos, liveState, playersInside, false, "serverTick");
        }
    }

    @Override
    public InteractionResult onUse(Level world, BlockPos pos, Player player, InteractionHand hand) {
        PlayerSyncEvents.ShellConstructionFailureReason failureReason = this.beginShellConstruction(player);

        if (failureReason == null) {
            NeoSyncDebug.info("constructor", "onUse success at {} player={}", NeoSyncDebug.describe(world, pos), player.getName().getString());
            return InteractionResult.SUCCESS;
        }

        NeoSyncDebug.warn(
            "constructor",
            "onUse failed at {} player={} reason={}",
            NeoSyncDebug.describe(world, pos),
            player.getName().getString(),
            failureReason.toText().getString()
        );
        player.displayClientMessage(failureReason.toText(), true);
        return InteractionResult.CONSUME;
    }

    @Nullable
    private PlayerSyncEvents.ShellConstructionFailureReason beginShellConstruction(Player player) {
        PlayerSyncEvents.ShellConstructionFailureReason failureReason = this.shell == null
            ? PlayerSyncEvents.ALLOW_SHELL_CONSTRUCTION.invoker().allowShellConstruction(player, this)
            : PlayerSyncEvents.ShellConstructionFailureReason.OCCUPIED;

        if (failureReason != null) {
            return failureReason;
        }

        if (player instanceof ServerPlayer serverPlayer) {
            SyncConfig config = SyncConfig.getInstance();
            float damage = serverPlayer.server.isHardcore() ? config.hardcoreFingerstickDamage() : config.fingerstickDamage();
            boolean isCreative = !serverPlayer.gameMode.isSurvival();
            boolean isLowOnHealth = (player.getHealth() + player.getAbsorptionAmount()) <= damage;
            boolean hasTotemOfUndying = player.getMainHandItem().is(Items.TOTEM_OF_UNDYING) || player.getOffhandItem().is(Items.TOTEM_OF_UNDYING);

            NeoSyncDebug.info(
                "constructor",
                "beginShellConstruction at {} player={} creative={} lowHealth={} hasTotem={} damage={}",
                NeoSyncDebug.describe(this.level, this.worldPosition),
                player.getName().getString(),
                isCreative,
                isLowOnHealth,
                hasTotemOfUndying,
                damage
            );

            if (isLowOnHealth && !isCreative && !hasTotemOfUndying && config.warnPlayerInsteadOfKilling()) {
                return PlayerSyncEvents.ShellConstructionFailureReason.NOT_ENOUGH_HEALTH;
            }

            player.hurt(FingerstickDamageSource.fingerstick(player), damage);

            ShellState newShell = ShellState.empty(serverPlayer, this.worldPosition);
            if (isCreative && config.enableInstantShellConstruction()) {
                newShell.setProgress(ShellState.PROGRESS_DONE);
            }

            this.setShellState(newShell);
            ShellConstructorBlock.setOpen(this.getBlockState(), this.level, this.worldPosition, true);
            this.forceShellStateUpdate("constructor-begin");

            if (this.level instanceof ServerLevel serverLevel) {
                BlockState liveState = this.getBlockState();
                this.ejectPlayers(
                    serverLevel,
                    this.worldPosition,
                    liveState,
                    getPlayersInsideProjected(
                        serverLevel,
                        NeoSyncSableCompat.projectBlockCenter(serverLevel, this.worldPosition),
                        NeoSyncSableCompat.projectNeighborCenter(serverLevel, this.worldPosition, Direction.UP)
                    ),
                    true,
                    "construction-start"
                );
            }
        }

        return null;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        ShellConstructorBlockEntity bottom = (ShellConstructorBlockEntity) this.getBottomPart().orElse(null);
        if (bottom == null || bottom.shell == null || bottom.shell.getProgress() >= ShellState.PROGRESS_DONE) {
            return 0;
        }

        int capacity = (int) SyncConfig.getInstance().shellConstructorCapacity();
        int missingFE = (int) Math.ceil((ShellState.PROGRESS_DONE - bottom.shell.getProgress()) * capacity);
        int accepted = Math.min(maxReceive, missingFE);

        if (accepted > 0 && !simulate) {
            float oldProgress = bottom.shell.getProgress();
            bottom.shell.setProgress(oldProgress + (float) accepted / capacity);

            NeoSyncDebug.info(
                "constructor",
                "receiveEnergy at {} accepted={} progress={} -> {}",
                NeoSyncDebug.describe(bottom.level, bottom.worldPosition),
                accepted,
                oldProgress,
                bottom.shell.getProgress()
            );

            bottom.forceShellStateUpdate("constructor-energy");

            if (oldProgress < ShellState.PROGRESS_DONE
                && bottom.shell.getProgress() >= ShellState.PROGRESS_DONE
                && bottom.level instanceof ServerLevel serverLevel) {
                BlockState liveState = bottom.getBlockState();
                bottom.ejectPlayers(
                    serverLevel,
                    bottom.worldPosition,
                    liveState,
                    getPlayersInsideProjected(
                        serverLevel,
                        NeoSyncSableCompat.projectBlockCenter(serverLevel, bottom.worldPosition),
                        NeoSyncSableCompat.projectNeighborCenter(serverLevel, bottom.worldPosition, Direction.UP)
                    ),
                    true,
                    "construction-complete"
                );
            }
        }

        return accepted;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        ShellConstructorBlockEntity bottom = (ShellConstructorBlockEntity) this.getBottomPart().orElse(null);
        if (bottom == null || bottom.shell == null) {
            return 0;
        }

        int cap = (int) SyncConfig.getInstance().shellConstructorCapacity();
        return (int) (bottom.shell.getProgress() * cap);
    }

    @Override
    public int getMaxEnergyStored() {
        ShellConstructorBlockEntity bottom = (ShellConstructorBlockEntity) this.getBottomPart().orElse(null);
        return bottom != null && bottom.shell != null ? (int) SyncConfig.getInstance().shellConstructorCapacity() : 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }

    private static List<ServerPlayer> getPlayersInsideProjected(ServerLevel world, Vec3 bottomCenter, Vec3 topCenter) {
        List<ServerPlayer> players = new ArrayList<>();
        for (ServerPlayer player : world.players()) {
            double bottomDistance = NeoSyncSableCompat.distanceSquared(world, player.position(), bottomCenter);
            double topDistance = NeoSyncSableCompat.distanceSquared(world, player.position(), topCenter);
            double nearestDistance = Math.min(bottomDistance, topDistance);

            if (nearestDistance < INSIDE_RADIUS_SQR) {
                NeoSyncDebug.info(
                    "constructor",
                    "inside check matched player={} nearestDistance={} bottomDistance={} topDistance={} pos={}",
                    player.getName().getString(),
                    nearestDistance,
                    bottomDistance,
                    topDistance,
                    NeoSyncDebug.describeVec(player.position())
                );
                players.add(player);
            }
        }
        return players;
    }

    private void ejectPlayers(
        ServerLevel world,
        BlockPos pos,
        BlockState state,
        List<ServerPlayer> players,
        boolean hardEject,
        String reason
    ) {
        if (players.isEmpty()) {
            return;
        }

        if (!state.hasProperty(ShellConstructorBlock.FACING)) {
            NeoSyncDebug.warn(
                "constructor",
                "cannot eject players at {} because state lacks facing: {}",
                NeoSyncDebug.describe(world, pos),
                state
            );
            return;
        }

        Vec3 bottomCenter = NeoSyncSableCompat.projectBlockCenter(world, pos);
        Vec3 topCenter = NeoSyncSableCompat.projectNeighborCenter(world, pos, Direction.UP);
        Vec3 outsideCenter = NeoSyncSableCompat.projectNeighborCenter(
            world,
            pos,
            state.getValue(ShellConstructorBlock.FACING).getOpposite()
        );

        for (ServerPlayer player : players) {
            Vec3 insideCenter = nearestEntryCenter(world, player.position(), bottomCenter, topCenter);
            double beforeDistance = NeoSyncSableCompat.distanceSquared(world, player.position(), insideCenter);

            NeoSyncDebug.info(
                "constructor",
                "ejecting player={} reason={} hardEject={} insideCenter={} outsideCenter={} distance={}",
                player.getName().getString(),
                reason,
                hardEject,
                NeoSyncDebug.describeVec(insideCenter),
                NeoSyncDebug.describeVec(outsideCenter),
                beforeDistance
            );

            BlockPosUtil.moveEntity(player, insideCenter, outsideCenter, false);

            if (hardEject || beforeDistance < 0.20D * 0.20D) {
                player.connection.teleport(
                    outsideCenter.x,
                    Math.max(outsideCenter.y - 0.5D, player.getY()),
                    outsideCenter.z,
                    player.getYRot(),
                    player.getXRot()
                );
                player.setDeltaMovement(Vec3.ZERO);
            }
        }

        this.setChanged();
        this.sync("constructor-eject:" + reason);
    }

    private static Vec3 nearestEntryCenter(ServerLevel world, Vec3 playerPos, Vec3 bottomCenter, Vec3 topCenter) {
        double bottomDistance = NeoSyncSableCompat.distanceSquared(world, playerPos, bottomCenter);
        double topDistance = NeoSyncSableCompat.distanceSquared(world, playerPos, topCenter);
        return bottomDistance <= topDistance ? bottomCenter : topCenter;
    }
}
