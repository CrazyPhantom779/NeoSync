package com.breakinblocks.neosync.client.block.entity;

import com.breakinblocks.neosync.client.gui.ShellSelectorGUI;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

public final class ShellStorageClientHooks {
    private ShellStorageClientHooks() {
    }

    public static boolean isLocalPlayer(Entity entity) {
        return Minecraft.getInstance().player == entity;
    }

    public static boolean hasNoScreen() {
        return Minecraft.getInstance().screen == null;
    }

    public static void openShellSelector(BlockPos currentContainerPos, Runnable onCloseCallback, Runnable onRemovedCallback) {
        NeoSyncDebug.info("storage-client-hook", "opening shell selector currentContainer={}", currentContainerPos);
        Minecraft.getInstance().setScreen(new ShellSelectorGUI(currentContainerPos, onCloseCallback, onRemovedCallback));
    }

    @Nullable
    public static Entity getLocalPlayer() {
        return Minecraft.getInstance().player;
    }
}
