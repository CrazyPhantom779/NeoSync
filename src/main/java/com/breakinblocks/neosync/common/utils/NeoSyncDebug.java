package com.breakinblocks.neosync.common.utils;

import com.breakinblocks.neosync.NeoSync;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class NeoSyncDebug {
    private static final Logger LOGGER = LoggerFactory.getLogger(NeoSync.NAME + "/Debug");

    private NeoSyncDebug() {
    }

    public static void info(String area, String message, Object... args) {
        LOGGER.info("[{}] " + message, merge(area, args));
    }

    public static void warn(String area, String message, Object... args) {
        LOGGER.warn("[{}] " + message, merge(area, args));
    }

    public static void error(String area, String message, Throwable throwable, Object... args) {
        LOGGER.error("[{}] " + message, merge(area, args), throwable);
    }

    public static String describe(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return "level/pos=null";
        }

        ResourceLocation dimension = level.dimension().location();
        return dimension + " @ " + pos.toShortString();
    }

    public static String describeBlock(Level level, BlockPos pos) {
        if (level == null || pos == null) {
            return "level/pos=null";
        }

        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return describe(level, pos)
                + " state=" + state
                + " be=" + (blockEntity == null ? "null" : blockEntity.getClass().getSimpleName());
    }

    private static Object[] merge(String area, Object[] args) {
        Object[] merged = new Object[args.length + 1];
        merged[0] = area;
        System.arraycopy(args, 0, merged, 1, args.length);
        return merged;
    }
}
