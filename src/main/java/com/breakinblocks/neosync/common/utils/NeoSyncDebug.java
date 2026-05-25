package com.breakinblocks.neosync.common.utils;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.api.shell.ShellState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
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

    public static void error(String area, String message, Object... args) {
        LOGGER.error("[{}] " + message, merge(area, args));
    }

    public static void error(String area, String message, Throwable throwable, Object... args) {
        LOGGER.error("[{}] " + message, merge(area, args), throwable);
    }

    public static String describe(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return "level/pos=null";
        }
        ResourceLocation dimension = level.dimension().location();
        return dimension + " @ " + pos.toShortString();
    }

    public static String describeBlock(@Nullable Level level, @Nullable BlockPos pos) {
        if (level == null || pos == null) {
            return "level/pos=null";
        }
        BlockState state = level.getBlockState(pos);
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return describe(level, pos)
            + " state=" + state
            + " be=" + (blockEntity == null ? "null" : blockEntity.getClass().getSimpleName());
    }

    public static String describeEntity(@Nullable Entity entity) {
        if (entity == null) {
            return "entity=null";
        }
        return entity.getClass().getSimpleName()
            + "{name=" + entity.getName().getString()
            + ",uuid=" + entity.getUUID()
            + ",pos=" + describeVec(entity.position())
            + "}";
    }

    public static String describeVec(@Nullable Vec3 vec) {
        if (vec == null) {
            return "vec=null";
        }
        return "("
            + round(vec.x) + ", "
            + round(vec.y) + ", "
            + round(vec.z) + ")";
    }

    public static String describeShell(@Nullable ShellState state) {
        if (state == null) {
            return "null";
        }
        return "uuid=" + state.getUuid()
            + ",owner=" + state.getOwnerName()
            + ",progress=" + state.getProgress()
            + ",color=" + state.getColor()
            + ",pos=" + (state.getPos() == null ? "null" : state.getPos().toShortString())
            + ",world=" + state.getWorld();
    }

    private static String round(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    private static Object[] merge(String area, Object[] args) {
        Object[] merged = new Object[args.length + 1];
        merged[0] = area;
        System.arraycopy(args, 0, merged, 1, args.length);
        return merged;
    }
}
