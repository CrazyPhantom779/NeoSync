package com.breakinblocks.neosync.api.networking;

import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

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

    public static void onShellContainerState(ShellContainerStatePacket payload) {
        invokeClient("onShellContainerState", payload);
    }

    public static void onPlayerIsAlive(PlayerIsAlivePacket payload) {
        invokeClient("onPlayerIsAlive", payload);
    }

    public static void onShellDestroyed(ShellDestroyedPacket payload) {
        invokeClient("onShellDestroyed", payload);
    }

    private static void invokeClient(String methodName, Object payload) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            NeoSyncDebug.warn(
                "client-dispatch",
                "ignored {} for {} because current dist is {}",
                methodName,
                payload.getClass().getSimpleName(),
                FMLEnvironment.dist
            );
            return;
        }

        try {
            Class<?> handlerClass = Class.forName(CLIENT_HANDLER);
            Method method = handlerClass.getMethod(methodName, payload.getClass());
            NeoSyncDebug.info(
                "client-dispatch",
                "dispatching {} for {} via {}",
                methodName,
                payload.getClass().getSimpleName(),
                CLIENT_HANDLER
            );
            method.invoke(null, payload);
        } catch (ReflectiveOperationException e) {
            NeoSyncDebug.error(
                "client-dispatch",
                "failed to dispatch {} for {}",
                e,
                methodName,
                payload.getClass().getName()
            );
            throw new RuntimeException("Failed to dispatch NeoSync client packet " + payload.getClass().getName(), e);
        }
    }
}
