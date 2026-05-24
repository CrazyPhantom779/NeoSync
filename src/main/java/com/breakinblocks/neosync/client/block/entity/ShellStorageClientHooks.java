package com.breakinblocks.neosync.client.block.entity;

import com.breakinblocks.neosync.client.gui.ShellSelectorGUI;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;

public final class ShellStorageClientHooks {
    private ShellStorageClientHooks() {
    }

    public static boolean isLocalPlayer(Entity entity) {
        return Minecraft.getInstance().player == entity;
    }

    public static boolean hasNoScreen() {
        return Minecraft.getInstance().screen == null;
    }

    public static void openShellSelector(Runnable onCloseCallback, Runnable onRemovedCallback) {
        Minecraft.getInstance().setScreen(new ShellSelectorGUI(onCloseCallback, onRemovedCallback));
    }
}