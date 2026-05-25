package com.breakinblocks.neosync.client.entity;

import com.breakinblocks.neosync.common.block.ShellConstructorBlock;
import com.breakinblocks.neosync.common.block.ShellStorageBlock;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class PostSyncMachineEgress {
    private static final int SUPPRESS_UI_TICKS = 20;
    private static final int MIN_EGRESS_TICKS = 4;
    private static final int MAX_EGRESS_TICKS = 60;

    private static boolean active;
    private static int suppressUiTicks;
    private static int egressTicks;
    private static BlockPos machinePos;
    private static Vec3 insideCenter;
    private static Vec3 outsideCenter;
    private static MachineKind machineKind;

    private PostSyncMachineEgress() {
    }

    public static void start(ClientLevel level, BlockPos shellPos) {
        if (level == null || shellPos == null) {
            clear();
            return;
        }

        EgressPath path = resolve(level, shellPos);
        if (path == null) {
            clear();
            return;
        }

        active = true;
        suppressUiTicks = SUPPRESS_UI_TICKS;
        egressTicks = 0;
        machinePos = path.machinePos;
        insideCenter = path.insideCenter;
        outsideCenter = path.outsideCenter;
        machineKind = path.machineKind;

        keepMachineOpen(level);

        NeoSyncDebug.info(
            "post-sync-egress",
            "starting egress machine={} kind={} inside={} outside={}",
            machinePos,
            machineKind,
            NeoSyncDebug.describeVec(insideCenter),
            NeoSyncDebug.describeVec(outsideCenter)
        );
    }

    public static void tick(LocalPlayer player) {
        if (suppressUiTicks > 0) {
            suppressUiTicks--;
        }

        if (!active || player == null) {
            return;
        }

        if (!(player.level() instanceof ClientLevel level) || insideCenter == null || outsideCenter == null || machinePos == null || machineKind == null) {
            clear();
            return;
        }

        keepMachineOpen(level);

        boolean isInside = BlockPosUtil.isEntityInside(player, insideCenter);
        if (!isInside && egressTicks >= MIN_EGRESS_TICKS) {
            NeoSyncDebug.info(
                "post-sync-egress",
                "finished because player is no longer inside machine={} ticks={}",
                machinePos,
                egressTicks
            );
            clear();
            return;
        }

        egressTicks++;
        BlockPosUtil.moveEntity(player, insideCenter, outsideCenter, false);

        if (egressTicks >= MAX_EGRESS_TICKS) {
            NeoSyncDebug.warn(
                "post-sync-egress",
                "forcing finish because max ticks reached machine={} ticks={} inside={}",
                machinePos,
                egressTicks,
                BlockPosUtil.isEntityInside(player, insideCenter)
            );
            clear();
        }
    }

    public static boolean suppressesMachineUi() {
        return active || suppressUiTicks > 0;
    }

    public static void clear() {
        active = false;
        machinePos = null;
        insideCenter = null;
        outsideCenter = null;
        machineKind = null;
        egressTicks = 0;
    }

    private static void keepMachineOpen(ClientLevel level) {
        if (machinePos == null || machineKind == null) {
            return;
        }

        BlockState state = level.getBlockState(machinePos);
        switch (machineKind) {
            case STORAGE -> {
                if (state.getBlock() instanceof ShellStorageBlock && state.hasProperty(ShellStorageBlock.FACING)) {
                    ShellStorageBlock.setOpen(state, level, machinePos, true);
                }
            }
            case CONSTRUCTOR -> {
                if (state.getBlock() instanceof ShellConstructorBlock && state.hasProperty(ShellConstructorBlock.FACING)) {
                    ShellConstructorBlock.setOpen(state, level, machinePos, true);
                }
            }
        }
    }

    private static EgressPath resolve(ClientLevel level, BlockPos shellPos) {
        for (BlockPos pos : new BlockPos[]{shellPos, shellPos.below(), shellPos.above()}) {
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof ShellStorageBlock && state.hasProperty(ShellStorageBlock.FACING)) {
                BlockPos bottom = ShellStorageBlock.isBottom(state) ? pos : pos.below();
                BlockState bottomState = level.getBlockState(bottom);
                Direction exit = bottomState.getValue(ShellStorageBlock.FACING).getOpposite();
                return new EgressPath(
                    bottom,
                    NeoSyncSableCompat.projectBlockCenter(level, bottom),
                    NeoSyncSableCompat.projectNeighborCenter(level, bottom, exit),
                    MachineKind.STORAGE
                );
            }

            if (state.getBlock() instanceof ShellConstructorBlock && state.hasProperty(ShellConstructorBlock.FACING)) {
                BlockPos bottom = ShellConstructorBlock.isBottom(state) ? pos : pos.below();
                BlockState bottomState = level.getBlockState(bottom);
                Direction exit = bottomState.getValue(ShellConstructorBlock.FACING).getOpposite();
                return new EgressPath(
                    bottom,
                    NeoSyncSableCompat.projectBlockCenter(level, bottom),
                    NeoSyncSableCompat.projectNeighborCenter(level, bottom, exit),
                    MachineKind.CONSTRUCTOR
                );
            }
        }

        return null;
    }

    private record EgressPath(BlockPos machinePos, Vec3 insideCenter, Vec3 outsideCenter, MachineKind machineKind) {
    }

    private enum MachineKind {
        STORAGE,
        CONSTRUCTOR
    }
}
