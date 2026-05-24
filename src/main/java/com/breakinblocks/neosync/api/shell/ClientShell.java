package com.breakinblocks.neosync.api.shell;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * Client-side version of the {@link Shell}.
 */
@OnlyIn(Dist.CLIENT)
public interface ClientShell extends Shell {
    @Override
    default boolean isClient() {
        return true;
    }

    @Nullable
    default PlayerSyncEvents.SyncFailureReason beginSync(ShellState state) {
        return beginSync(state, null);
    }

    @Nullable
    PlayerSyncEvents.SyncFailureReason beginSync(ShellState state, @Nullable BlockPos currentContainerPos);

    /**
     * Handles the end of an asynchronous sync operation.
     *
     * @param startWorld Identifier of the world the sync operation was triggered in.
     * @param startPos Position the sync operation was triggered at.
     * @param startFacing Direction the player was looking at when the sync process started.
     * @param targetWorld Identifier of the target shell's world.
     * @param targetPos Position of the target shell.
     * @param targetFacing Direction the target shell is currently looking at.
     * @param storedState New state that was generated during the sync process, if any; otherwise, null.
     */
    void endSync(ResourceLocation startWorld, BlockPos startPos, Direction startFacing, ResourceLocation targetWorld, BlockPos targetPos, Direction targetFacing, @Nullable ShellState storedState);
}
