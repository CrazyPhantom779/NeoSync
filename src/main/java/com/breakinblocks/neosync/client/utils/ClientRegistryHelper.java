package com.breakinblocks.neosync.client.utils;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientRegistryHelper {
    private ClientRegistryHelper() {}

    public static HolderLookup.Provider tryProvider() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.level != null) {
                return mc.level.registryAccess();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}

