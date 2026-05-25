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
    private static final int MAX_EGRESS_TICKS = 40;

    private static boolean active;
    private static int suppressUiTicks;
    private static int egressTicks;
    private static BlockPos machinePos;
    private static Vec3 insideCenter;
    private static Vec3 outsideCenter;

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

        NeoSyncDebug.info(
            "post-sync-egress",
            "starting egress machine={} inside={} outside={}",
            machinePos,
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

        if (player.level() == null || insideCenter == null || outsideCenter == null) {
            clear();
            return;
        }

        if (player.distanceToSqr(outsideCenter.x, outsideCenter.y, outsideCenter.z) < 0.20D * 0.20D) {
            NeoSyncDebug.info("post-sync-egress", "finished because player reached outside center machine={}", machinePos);
            clear();
            return;
        }

        egressTicks++;
        BlockPosUtil.moveEntity(player, insideCenter, outsideCenter, false);

        if (egressTicks >= MAX_EGRESS_TICKS) {
            NeoSyncDebug.info("post-sync-egress", "finished because max egress ticks reached machine={}", machinePos);
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
        egressTicks = 0;
    }

    private static EgressPath resolve(ClientLevel level, BlockPos shellPos) {
        for (BlockPos pos : new BlockPos[]{shellPos, shellPos.below(), shellPos.above()}) {
            BlockState state = level.getBlockState(pos);

            if (state.getBlock() instanceof ShellStorageBlock && state.hasProperty(ShellStorageBlock.FACING)) {
                BlockPos bottom = ShellStorageBlock.isBottom(state) ? pos : pos.below();
                BlockState bottomState = level.getBlockState(bottom);
                Direction exit = bottomState.getValue(ShellStorageBlock.FACING).getOpposite();
                ShellStorageBlock.setOpen(bottomState, level, bottom, true);
                return new EgressPath(
                    bottom,
                    NeoSyncSableCompat.projectBlockCenter(level, bottom),
                    NeoSyncSableCompat.projectNeighborCenter(level, bottom, exit)
                );
            }

            if (state.getBlock() instanceof ShellConstructorBlock && state.hasProperty(ShellConstructorBlock.FACING)) {
                BlockPos bottom = ShellConstructorBlock.isBottom(state) ? pos : pos.below();
                BlockState bottomState = level.getBlockState(bottom);
                Direction exit = bottomState.getValue(ShellConstructorBlock.FACING).getOpposite();
                ShellConstructorBlock.setOpen(bottomState, level, bottom, true);
                return new EgressPath(
                    bottom,
                    NeoSyncSableCompat.projectBlockCenter(level, bottom),
                    NeoSyncSableCompat.projectNeighborCenter(level, bottom, exit)
                );
            }
        }

        return null;
    }

    private record EgressPath(BlockPos machinePos, Vec3 insideCenter, Vec3 outsideCenter) {
    }
}
