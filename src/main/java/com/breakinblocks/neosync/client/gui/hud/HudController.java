package com.breakinblocks.neosync.client.gui.hud;

import com.breakinblocks.neosync.NeoSync;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = NeoSync.MOD_ID, value = Dist.CLIENT)
public final class HudController {
    private static Boolean wasHudHidden;

    private HudController() {}

    public static void show() {
        setHudHidden(false);
    }

    public static void hide() {
        setHudHidden(true);
    }

    public static void restore() {
        Options opts = options();
        if (opts == null) {
            wasHudHidden = null;
            return;
        }
        if (wasHudHidden != null) {
            opts.hideGui = wasHudHidden;
            wasHudHidden = null;
        }
    }

    private static void setHudHidden(boolean value) {
        Options opts = options();
        if (opts == null) {
            return;
        }
        if (wasHudHidden == null) {
            wasHudHidden = opts.hideGui;
        }
        opts.hideGui = value;
    }

    private static Options options() {
        Minecraft mc = Minecraft.getInstance();
        return mc == null ? null : mc.options;
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        restore();
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        restore();
    }

    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        restore();
    }
}
