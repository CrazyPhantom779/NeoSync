package com.breakinblocks.neosync.api.networking;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.DistExecutor;

import java.lang.reflect.Method;

public final class ClientPacketDispatch {
    private static final String CLIENT_HANDLER = "com.breakinblocks.neosync.client.networking.ClientNetworkHandler";

    private ClientPacketDispatch() {
    }

    public static void onSynchronizationResponse(SynchronizationResponsePacket payload) {
        invokeClient("onSynchronizationResponse", payload);
    }

    public static void onShellUpdate(ShellUpdatePacket payload) {
        invokeClient("onShellUpdate", payload);
    }

    public static void onShellStateUpdate(ShellStateUpdatePacket payload) {
        invokeClient("onShellStateUpdate", payload);
    }

    public static void onPlayerIsAlive(PlayerIsAlivePacket payload) {
        invokeClient("onPlayerIsAlive", payload);
    }

    public static void onShellDestroyed(ShellDestroyedPacket payload) {
        invokeClient("onShellDestroyed", payload);
    }

    private static void invokeClient(String methodName, Object payload) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            try {
                Class<?> handlerClass = Class.forName(CLIENT_HANDLER);
                Method method = handlerClass.getMethod(methodName, payload.getClass());
                method.invoke(null, payload);
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException("Failed to dispatch NeoSync client packet " + payload.getClass().getName(), e);
            }
        });
    }
}