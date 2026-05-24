package com.breakinblocks.neosync.integration.sable;

import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

public final class NeoSyncSableCompat {
    private static Object companion;
    private static Method projectOutOfSubLevel;

    private NeoSyncSableCompat() {
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
    }
}