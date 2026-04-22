package com.breakinblocks.neosync.client.gui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
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
}
