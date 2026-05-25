package com.breakinblocks.neosync.api.networking;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public record ShellUpdatePacket(
    ResourceLocation worldId,
    boolean isArtificial,
    List<ShellState> states
) implements CustomPacketPayload {
    public static final Type<ShellUpdatePacket> TYPE = new Type<>(NeoSync.locate("shell/update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShellUpdatePacket> STREAM_CODEC =
        StreamCodec.composite(
            ResourceLocation.STREAM_CODEC,
            ShellUpdatePacket::worldId,
            ByteBufCodecs.BOOL,
            ShellUpdatePacket::isArtificial,
            ShellState.STREAM_CODEC.apply(ByteBufCodecs.collection(ArrayList::new)),
            ShellUpdatePacket::states,
            ShellUpdatePacket::new
        );

    public ShellUpdatePacket(ResourceLocation worldId, boolean isArtificial, Collection<ShellState> states) {
        this(worldId, isArtificial, states == null ? List.of() : List.copyOf(states));
    }

    @Override
    public Type<ShellUpdatePacket> type() {
        return TYPE;
    }

    public void send(ServerPlayer player) {
        NeoSyncDebug.info(
            "shell-full-packet",
            "send player={} world={} artificial={} states={}",
            player.getName().getString(),
            this.worldId,
            this.isArtificial,
            this.states.size()
        );
        PacketDistributor.sendToPlayer(player, this);
    }

    public static void handle(ShellUpdatePacket payload, IPayloadContext context) {
        NeoSyncDebug.info(
            "shell-full-packet",
            "handle world={} artificial={} states={} contextPlayer={}",
            payload.worldId,
            payload.isArtificial,
            payload.states.size(),
            context.player()
        );
        context.enqueueWork(() -> ClientPacketDispatch.onShellUpdate(payload));
    }
}
