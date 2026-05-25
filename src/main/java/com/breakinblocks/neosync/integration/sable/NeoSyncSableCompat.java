package com.breakinblocks.neosync.integration.sable;

import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
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
    private static boolean attemptedReflection;
    private static boolean reflectionAvailable;

    private NeoSyncSableCompat() {
    }

    public static BlockPos projectOut(Level level, BlockPos pos) {
        Vec3 projected = projectOut(level, Vec3.atCenterOf(pos));
        BlockPos result = BlockPos.containing(projected);
        if (!result.equals(pos)) {
            NeoSyncDebug.info(
                "sable",
                "projectOut block {} -> {} in {}",
                pos.toShortString(),
                result.toShortString(),
                level == null ? "null-level" : level.dimension().location()
            );
        }
        return result;
    }

    public static Vec3 projectBlockCenter(Level level, BlockPos pos) {
        return projectOut(level, Vec3.atCenterOf(pos));
    }

    public static Vec3 projectNeighborCenter(Level level, BlockPos pos, Direction direction) {
        return projectOut(level, Vec3.atCenterOf(pos.relative(direction)));
    }

    public static Vec3 projectOut(Level level, Vec3 position) {
        if (level == null || position == null || !isLoaded()) {
            return position;
        }
        if (!ensureLoaded()) {
            return position;
        }

        try {
            Object result = projectOutOfSubLevel.invoke(companion, level, position);
            if (result instanceof Vec3 projected) {
                if (projected.distanceToSqr(position) > 1.0E-6D) {
                    NeoSyncDebug.info(
                        "sable",
                        "projected vec {} -> {} in {}",
                        NeoSyncDebug.describeVec(position),
                        NeoSyncDebug.describeVec(projected),
                        level.dimension().location()
                    );
                }
                return projected;
            }

            NeoSyncDebug.warn("sable", "projectOutOfSubLevel returned non-Vec3 {}", result);
        } catch (Throwable throwable) {
            NeoSyncDebug.error(
                "sable",
                "projectOut failed for {} in {}",
                throwable,
                NeoSyncDebug.describeVec(position),
                level.dimension().location()
            );
        }

        return position;
    }

    public static double distanceSquared(Level level, Vec3 a, Vec3 b) {
        if (a == null || b == null) {
            return Double.MAX_VALUE;
        }
        if (level == null || !isLoaded()) {
            return a.distanceToSqr(b);
        }
        if (!ensureLoaded()) {
            return projectOut(level, a).distanceToSqr(projectOut(level, b));
        }

        try {
            if (distanceSquaredWithSubLevels != null) {
                Object result = distanceSquaredWithSubLevels.invoke(companion, level, a, b);
                if (result instanceof Double value) {
                    return value;
                }
                NeoSyncDebug.warn("sable", "distanceSquaredWithSubLevels returned non-Double {}", result);
            }
        } catch (Throwable throwable) {
            NeoSyncDebug.error(
                "sable",
                "distanceSquared failed for a={} b={} in {}",
                throwable,
                NeoSyncDebug.describeVec(a),
                NeoSyncDebug.describeVec(b),
                level.dimension().location()
            );
        }

        Vec3 projectedA = projectOut(level, a);
        Vec3 projectedB = projectOut(level, b);
        double fallback = projectedA.distanceToSqr(projectedB);
        NeoSyncDebug.info(
            "sable",
            "distance fallback a={} b={} projectedA={} projectedB={} result={}",
            NeoSyncDebug.describeVec(a),
            NeoSyncDebug.describeVec(b),
            NeoSyncDebug.describeVec(projectedA),
            NeoSyncDebug.describeVec(projectedB),
            fallback
        );
        return fallback;
    }

    public static boolean isLoaded() {
        return ModList.get().isLoaded("sable") || ModList.get().isLoaded("sable_companion");
    }

    private static boolean ensureLoaded() {
        if (attemptedReflection) {
            return reflectionAvailable;
        }

        attemptedReflection = true;
        try {
            Class<?> clazz = Class.forName("dev.ryanhcode.sable.companion.SableCompanion");
            companion = clazz.getField("INSTANCE").get(null);
            projectOutOfSubLevel = clazz.getMethod("projectOutOfSubLevel", Level.class, Vec3.class);

            try {
                distanceSquaredWithSubLevels =
                    clazz.getMethod("distanceSquaredWithSubLevels", Level.class, Vec3.class, Vec3.class);
            } catch (NoSuchMethodException ignored) {
                distanceSquaredWithSubLevels = null;
            }

            reflectionAvailable = true;
            NeoSyncDebug.info(
                "sable",
                "loaded Sable companion hooks projectMethod={} distanceMethodPresent={}",
                projectOutOfSubLevel.getName(),
                distanceSquaredWithSubLevels != null
            );
        } catch (Throwable throwable) {
            reflectionAvailable = false;
            NeoSyncDebug.error("sable", "failed to load Sable companion hooks", throwable);
        }

        return reflectionAvailable;
    }
}
