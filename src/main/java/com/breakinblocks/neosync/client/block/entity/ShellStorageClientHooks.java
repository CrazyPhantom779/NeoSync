package com.breakinblocks.neosync.client.block.entity;

import com.breakinblocks.neosync.client.entity.PersistentCameraEntity;
import com.breakinblocks.neosync.client.entity.PostSyncMachineEgress;
import com.breakinblocks.neosync.client.gui.ShellSelectorGUI;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

public final class ShellStorageClientHooks {
    private static final int AUTO_OPEN_RESPAWN_COOLDOWN_TICKS = 30;

    private ShellStorageClientHooks() {
    }

    public static boolean isLocalPlayer(Entity entity) {
        return Minecraft.getInstance().player == entity;
    }

    public static boolean hasNoScreen() {
        return Minecraft.getInstance().screen == null;
    }

    public static boolean isCameraTransitionActive() {
        Minecraft client = Minecraft.getInstance();
        Entity camera = client.getCameraEntity();
        return camera instanceof PersistentCameraEntity
            || (client.player != null && camera != null && camera != client.player);
    }

    public static boolean shouldAllowAutoOpen(@Nullable Entity entity) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        if (!isLocalPlayer(player)) {
            return false;
        }

        if (!player.isAlive()) {
            return false;
        }

        if (player.tickCount < AUTO_OPEN_RESPAWN_COOLDOWN_TICKS) {
            NeoSyncDebug.info(
                "storage-client-hook",
                "suppressing auto-open because player is within respawn cooldown tickCount={}",
                player.tickCount
            );
            return false;
        }

        if (isCameraTransitionActive()) {
            NeoSyncDebug.info(
                "storage-client-hook",
                "suppressing auto-open because a camera transition is active camera={}",
                Minecraft.getInstance().getCameraEntity()
            );
            return false;
        }

        if (PostSyncMachineEgress.suppressesMachineUi()) {
            NeoSyncDebug.info(
                "storage-client-hook",
                "suppressing auto-open because post-sync machine egress is active"
            );
            return false;
        }

        return hasNoScreen();
    }

    public static void openShellSelector(
        BlockPos currentContainerPos,
        Runnable onCloseCallback,
        Runnable onRemovedCallback
    ) {
        Minecraft client = Minecraft.getInstance();

        if (isCameraTransitionActive() || PostSyncMachineEgress.suppressesMachineUi()) {
            NeoSyncDebug.info(
                "storage-client-hook",
                "refusing to open shell selector during camera transition/egress currentContainer={}",
                currentContainerPos
            );
            return;
        }

        if (client.screen instanceof ShellSelectorGUI existing) {
            NeoSyncDebug.info(
                "storage-client-hook",
                "refreshing existing shell selector currentContainer={}",
                currentContainerPos
            );
            existing.refreshFromNetwork();
            return;
        }

        NeoSyncDebug.info("storage-client-hook", "opening shell selector currentContainer={}", currentContainerPos);
        client.setScreen(new ShellSelectorGUI(currentContainerPos, onCloseCallback, onRemovedCallback));
    }

    @Nullable
    public static Entity getLocalPlayer() {
        return Minecraft.getInstance().player;
    }
}
