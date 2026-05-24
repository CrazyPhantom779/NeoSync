package com.breakinblocks.neosync.client.entity;

import com.breakinblocks.neosync.api.shell.ShellState;

import java.util.IdentityHashMap;
import java.util.Map;

public final class ClientShellEntities {
    private static final Map<ShellState, ShellEntity> CACHE = new IdentityHashMap<>();

    private ClientShellEntities() {
    }

    public static ShellEntity get(ShellState state) {
        return CACHE.computeIfAbsent(state, ShellEntity::new);
    }

    public static void clear() {
        CACHE.clear();
    }
}
