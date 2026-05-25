package com.breakinblocks.neosync.networking;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.api.networking.PlayerIsAlivePacket;
import com.breakinblocks.neosync.api.networking.ShellContainerStatePacket;
import com.breakinblocks.neosync.api.networking.ShellDestroyedPacket;
import com.breakinblocks.neosync.api.networking.ShellStateUpdatePacket;
import com.breakinblocks.neosync.api.networking.ShellUpdatePacket;
import com.breakinblocks.neosync.api.networking.SynchronizationRequestPacket;
import com.breakinblocks.neosync.api.networking.SynchronizationResponsePacket;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = NeoSync.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class SyncPackets {
    private static final String PROTOCOL_VERSION = "1";

    private SyncPackets() {
    }

    @SubscribeEvent
    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        NeoSyncDebug.info("network", "registering NeoSync packets protocol={}", PROTOCOL_VERSION);
        PayloadRegistrar registrar = event.registrar(NeoSync.MOD_ID).versioned(PROTOCOL_VERSION);

        logRegistration("playToServer", SynchronizationRequestPacket.TYPE);
        registrar.playToServer(
            SynchronizationRequestPacket.TYPE,
            SynchronizationRequestPacket.STREAM_CODEC,
            SynchronizationRequestPacket::handle
        );

        logRegistration("playToClient", SynchronizationResponsePacket.TYPE);
        registrar.playToClient(
            SynchronizationResponsePacket.TYPE,
            SynchronizationResponsePacket.STREAM_CODEC,
            SynchronizationResponsePacket::handle
        );

        logRegistration("playToClient", ShellUpdatePacket.TYPE);
        registrar.playToClient(
            ShellUpdatePacket.TYPE,
            ShellUpdatePacket.STREAM_CODEC,
            ShellUpdatePacket::handle
        );

        logRegistration("playToClient", ShellStateUpdatePacket.TYPE);
        registrar.playToClient(
            ShellStateUpdatePacket.TYPE,
            ShellStateUpdatePacket.STREAM_CODEC,
            ShellStateUpdatePacket::handle
        );

        logRegistration("playToClient", ShellContainerStatePacket.TYPE);
        registrar.playToClient(
            ShellContainerStatePacket.TYPE,
            ShellContainerStatePacket.STREAM_CODEC,
            ShellContainerStatePacket::handle
        );

        logRegistration("playToClient", PlayerIsAlivePacket.TYPE);
        registrar.playToClient(
            PlayerIsAlivePacket.TYPE,
            PlayerIsAlivePacket.STREAM_CODEC,
            PlayerIsAlivePacket::handle
        );

        logRegistration("playToClient", ShellDestroyedPacket.TYPE);
        registrar.playToClient(
            ShellDestroyedPacket.TYPE,
            ShellDestroyedPacket.STREAM_CODEC,
            ShellDestroyedPacket::handle
        );
    }

    private static void logRegistration(String direction, CustomPacketPayload.Type<?> type) {
        NeoSyncDebug.info("network", "register {} {}", direction, type.id());
    }
}
