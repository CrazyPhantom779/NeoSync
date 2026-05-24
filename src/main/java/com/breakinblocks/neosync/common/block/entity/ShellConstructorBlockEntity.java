package com.breakinblocks.neosync.common.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.energy.IEnergyStorage;
import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.block.ShellConstructorBlock;
import com.breakinblocks.neosync.common.config.SyncConfig;
import com.breakinblocks.neosync.common.entity.damage.FingerstickDamageSource;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import org.jetbrains.annotations.Nullable;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import net.minecraft.world.phys.Vec3;

public class ShellConstructorBlockEntity extends AbstractShellContainerBlockEntity implements IEnergyStorage {
    public ShellConstructorBlockEntity(BlockPos pos, BlockState state) {
        super(SyncBlockEntities.SHELL_CONSTRUCTOR.get(), pos, state);
    }

    @Override
    public void onServerTick(Level world, BlockPos pos, BlockState state) {
        super.onServerTick(world, pos, state);

        Vec3 shellCenter = NeoSyncSableCompat.projectBlockCenter(world, pos);
        boolean hasPlayerInside = BlockPosUtil.hasPlayerInside(shellCenter, world);

        if (ShellConstructorBlock.isOpen(state) != hasPlayerInside) {
            ShellConstructorBlock.setOpen(state, world, pos, hasPlayerInside);
            this.setChanged();
            this.sync();
        }
    }

    public InteractionResult onUse(Level world, BlockPos pos, Player player, InteractionHand hand) {
        PlayerSyncEvents.ShellConstructionFailureReason failureReason = this.beginShellConstruction(player);
        if (failureReason == null) {
            return InteractionResult.SUCCESS;
        } else {
            player.displayClientMessage(failureReason.toText(), true);
            return InteractionResult.CONSUME;
        }
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

            this.setChanged();
            this.sync();
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
            bottom.shell.setProgress(bottom.shell.getProgress() + (float) accepted / capacity);
            bottom.setChanged();
            bottom.sync();

            if (bottom.level != null && !bottom.level.isClientSide && bottom.worldPosition != null && bottom.getBlockState() != null) {
                bottom.setShellState(bottom.shell);
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
        return bottom != null && bottom.shell != null
                ? (int) SyncConfig.getInstance().shellConstructorCapacity()
                : 0;
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return true;
    }
}
