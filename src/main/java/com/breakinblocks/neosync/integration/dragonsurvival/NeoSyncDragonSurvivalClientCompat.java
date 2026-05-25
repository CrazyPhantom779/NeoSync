package com.breakinblocks.neosync.integration.dragonsurvival;

import com.breakinblocks.neosync.api.shell.ShellState;
import net.minecraft.client.player.AbstractClientPlayer;
import org.jetbrains.annotations.Nullable;

public final class NeoSyncDragonSurvivalClientCompat {
    private NeoSyncDragonSurvivalClientCompat() {
    }

    public static void clear() {
    }

    public static boolean hasDragonShellData(@Nullable ShellState shell) {
        return false;
    }

    @Nullable
    public static AbstractClientPlayer getRenderPlayer(ShellState shell, boolean previewMode) {
        return null;
    }
}
