package com.breakinblocks.neosync.api.networking;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;

public record SynchronizationResponsePacket(
    ResourceLocation startWorld,
    BlockPos startPos,
    Direction startFacing,
    ResourceLocation targetWorld,
    BlockPos targetPos,
    Direction targetFacing,
    Optional<ShellState> storedState
) implements CustomPacketPayload {
    public static final Type<SynchronizationResponsePacket> TYPE =
        new Type<>(NeoSync.locate("shell/synchronization/response"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SynchronizationResponsePacket> STREAM_CODEC =
        StreamCodec.of(SynchronizationResponsePacket::encode, SynchronizationResponsePacket::decode);

    @Override
    public Type<SynchronizationResponsePacket> type() {
        return TYPE;
    }

    public void send(ServerPlayer player) {
        NeoSyncDebug.info(
            "sync-response-packet",
            "send player={} start={} {} target={} {} storedStatePresent={}",
            player.getName().getString(),
            this.startWorld,
            this.startPos,
            this.targetWorld,
            this.targetPos,
            this.storedState.isPresent()
        );
        PacketDistributor.sendToPlayer(player, this);
    }

    private static void encode(RegistryFriendlyByteBuf buf, SynchronizationResponsePacket payload) {
        NeoSyncDebug.info(
            "sync-response-packet",
            "encode start={} {} target={} {} storedStatePresent={}",
            payload.startWorld,
            payload.startPos,
            payload.targetWorld,
            payload.targetPos,
            payload.storedState.isPresent()
        );
        buf.writeResourceLocation(payload.startWorld);
        buf.writeBlockPos(payload.startPos);
        buf.writeVarInt(payload.startFacing.get3DDataValue());
        buf.writeResourceLocation(payload.targetWorld);
        buf.writeBlockPos(payload.targetPos);
        buf.writeVarInt(payload.targetFacing.get3DDataValue());
        ByteBufCodecs.optional(ShellState.STREAM_CODEC).encode(buf, payload.storedState);
    }

    private static SynchronizationResponsePacket decode(RegistryFriendlyByteBuf buf) {
        ResourceLocation startWorld = buf.readResourceLocation();
        BlockPos startPos = buf.readBlockPos();
        Direction startFacing = Direction.from3DDataValue(buf.readVarInt());
        ResourceLocation targetWorld = buf.readResourceLocation();
        BlockPos targetPos = buf.readBlockPos();
        Direction targetFacing = Direction.from3DDataValue(buf.readVarInt());
        Optional<ShellState> storedState = ByteBufCodecs.optional(ShellState.STREAM_CODEC).decode(buf);

        NeoSyncDebug.info(
            "sync-response-packet",
            "decode start={} {} target={} {} storedStatePresent={}",
            startWorld,
            startPos,
            targetWorld,
            targetPos,
            storedState.isPresent()
        );
        return new SynchronizationResponsePacket(
            startWorld,
            startPos,
            startFacing,
            targetWorld,
            targetPos,
            targetFacing,
            storedState
        );
    }

    public static void handle(SynchronizationResponsePacket payload, IPayloadContext context) {
        NeoSyncDebug.info(
            "sync-response-packet",
            "handle start={} {} target={} {} storedStatePresent={} contextPlayer={}",
            payload.startWorld,
            payload.startPos,
            payload.targetWorld,
            payload.targetPos,
            payload.storedState.isPresent(),
            context.player()
        );
        context.enqueueWork(() -> ClientPacketDispatch.onSynchronizationResponse(payload));
    }
}
