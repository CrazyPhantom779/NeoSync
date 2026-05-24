package com.breakinblocks.neosync.api.shell;

import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public interface ServerShell extends Shell {
    @Override
    default boolean isClient() {
        return false;
    }

    default Either<ShellState, PlayerSyncEvents.SyncFailureReason> sync(ShellState state) {
        return sync(state, null);
    }

    Either<ShellState, PlayerSyncEvents.SyncFailureReason> sync(ShellState state, @Nullable BlockPos currentContainerPos);

    void apply(ShellState state);

    static Optional<ServerShell> getByUuid(MinecraftServer server, UUID uuid) {
        return Optional.ofNullable((ServerShell) server.getPlayerList().getPlayer(uuid));
    }

    static Optional<ServerShell> getByName(MinecraftServer server, String name) {
        return Optional.ofNullable((ServerShell) server.getPlayerList().getPlayerByName(name));
    }
}