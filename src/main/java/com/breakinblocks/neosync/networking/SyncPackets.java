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

        registrar.playToServer(
                SynchronizationRequestPacket.TYPE,
                SynchronizationRequestPacket.STREAM_CODEC,
                SynchronizationRequestPacket::handle);
        registrar.playToClient(
                SynchronizationResponsePacket.TYPE,
                SynchronizationResponsePacket.STREAM_CODEC,
                SynchronizationResponsePacket::handle);
        registrar.playToClient(
                ShellUpdatePacket.TYPE,
                ShellUpdatePacket.STREAM_CODEC,
                ShellUpdatePacket::handle);
        registrar.playToClient(
                ShellStateUpdatePacket.TYPE,
                ShellStateUpdatePacket.STREAM_CODEC,
                ShellStateUpdatePacket::handle);
        registrar.playToClient(
                ShellContainerStatePacket.TYPE,
                ShellContainerStatePacket.STREAM_CODEC,
                ShellContainerStatePacket::handle);
        registrar.playToClient(
                PlayerIsAlivePacket.TYPE,
                PlayerIsAlivePacket.STREAM_CODEC,
                PlayerIsAlivePacket::handle);
        registrar.playToClient(
                ShellDestroyedPacket.TYPE,
                ShellDestroyedPacket.STREAM_CODEC,
                ShellDestroyedPacket::handle);
    }
}
