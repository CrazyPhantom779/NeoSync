package com.breakinblocks.neosync.integration.sable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public final class NeoSyncSableCompat {
    private static Object companion;
    private static Method projectOutOfSubLevel;
    private static Method distanceSquaredWithSubLevels;

    private NeoSyncSableCompat() {
    }

    public static BlockPos projectOut(Level level, BlockPos pos) {
        Vec3 projected = projectOut(level, Vec3.atCenterOf(pos));
        return BlockPos.containing(projected);
    }

    public static Vec3 projectBlockCenter(Level level, BlockPos pos) {
        return projectOut(level, Vec3.atCenterOf(pos));
    }

    public static Vec3 projectNeighborCenter(Level level, BlockPos pos, Direction direction) {
        return projectOut(level, Vec3.atCenterOf(pos.relative(direction)));
    }

    public static Vec3 projectOut(Level level, Vec3 position) {
        if (!isLoaded()) {
            return position;
        }

        try {
            ensureLoaded();

            Object result = projectOutOfSubLevel.invoke(companion, level, position);
            return result instanceof Vec3 vec ? vec : position;
        } catch (Throwable ignored) {
            return position;
        }
    }

    public static double distanceSquared(Level level, Vec3 a, Vec3 b) {
        if (!isLoaded()) {
            return a.distanceToSqr(b);
        }

        try {
            ensureLoaded();

            if (distanceSquaredWithSubLevels != null) {
                Object result = distanceSquaredWithSubLevels.invoke(companion, level, a, b);
                return result instanceof Double value ? value : a.distanceToSqr(b);
            }
        } catch (Throwable ignored) {
        }

        return projectOut(level, a).distanceToSqr(projectOut(level, b));
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("sable") || ModList.get().isLoaded("sable_companion");
    }

    private static void ensureLoaded() throws ReflectiveOperationException {
        if (companion != null && projectOutOfSubLevel != null) {
            return;
        }

        Class<?> clazz = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
        companion = clazz.getField("INSTANCE").get(null);
        projectOutOfSubLevel = clazz.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);

        try {
            distanceSquaredWithSubLevels = clazz.getMethod("distanceSquaredWithSubLevels", Level.class, Vec3.class, Vec3.class);
        } catch (NoSuchMethodException ignored) {
            distanceSquaredWithSubLevels = null;
        }
    }
}