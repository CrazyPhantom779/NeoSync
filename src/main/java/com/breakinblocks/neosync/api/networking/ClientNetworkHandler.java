package com.breakinblocks.neosync.api.networking;

/**
 * Legacy compatibility shim.
 *
 * <p>The real client handler lives in {@code com.breakinblocks.neosync.client.networking.ClientNetworkHandler}.
 * Keep this class free of net.minecraft.client imports so it cannot crash a dedicated server if it is loaded.</p>
 */
@Deprecated(forRemoval = false)
public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }
}
