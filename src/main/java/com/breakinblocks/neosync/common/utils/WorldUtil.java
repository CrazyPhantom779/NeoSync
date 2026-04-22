package com.breakinblocks.neosync.common.utils;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import java.util.Optional;
import java.util.stream.StreamSupport;

public final class WorldUtil {
    public static ResourceLocation getId(Level world) {
        return world.dimension().location();
    }

    public static boolean isOf(ResourceLocation id, Level world) {
        return world.dimension().location().equals(id);
    }

    public static <T extends Level> Optional<T> findWorld(Iterable<T> worlds, ResourceLocation id) {
        return StreamSupport.stream(worlds.spliterator(), false)
                .filter(world -> isOf(id, world))
                .findAny();
    }
}