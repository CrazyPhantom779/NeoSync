package com.breakinblocks.neosync.client.gui.controller;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;

@OnlyIn(Dist.CLIENT)
public final class DeathScreenController {
    private static boolean suspended;

    public static boolean isSuspended() {
        return suspended;
    }

    public static void suspend() {
        suspended = true;
    }

    public static void restore() {
        suspended = false;
    }

    static {
        NeoForge.EVENT_BUS.register(DeathScreenController.class);
    }

    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        restore();
    }
}