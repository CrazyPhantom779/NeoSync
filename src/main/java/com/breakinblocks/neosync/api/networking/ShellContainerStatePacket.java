package com.breakinblocks.neosync.api.networking;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public record ShellContainerStatePacket(
        BlockPos pos,
        Optional<ShellState> shell,
        Optional<DyeColor> color
) implements CustomPacketPayload {
    public static final Type<ShellContainerStatePacket> TYPE = new Type<>(NeoSync.locate("shell/container/state"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShellContainerStatePacket> STREAM_CODEC = StreamCodec.of(
            ShellContainerStatePacket::encode,
            ShellContainerStatePacket::decode
    );

    public ShellContainerStatePacket(BlockPos pos, @Nullable ShellState shell, @Nullable DyeColor color) {
        this(pos, Optional.ofNullable(shell), Optional.ofNullable(color));
    }

    @Override
    public Type<ShellContainerStatePacket> type() {
        return TYPE;
    }

    public void send(ServerLevel world, BlockPos pos, double radius) {
        NeoSyncDebug.info("container-packet", "send container-state pos={} shellPresent={} color={} radius={}", NeoSyncDebug.describe(world, pos), this.shell.isPresent(), this.color.orElse(null), radius);
        PacketDistributor.sendToPlayersNear(world, null, pos.getX(), pos.getY(), pos.getZ(), radius, this);
    }

    private static void encode(RegistryFriendlyByteBuf buf, ShellContainerStatePacket payload) {
        buf.writeBlockPos(payload.pos);
        ByteBufCodecs.optional(ShellState.STREAM_CODEC).encode(buf, payload.shell);
        buf.writeVarInt(payload.color.map(DyeColor::getId).orElse((int) Byte.MAX_VALUE));
    }

    private static ShellContainerStatePacket decode(RegistryFriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Optional<ShellState> shell = ByteBufCodecs.optional(ShellState.STREAM_CODEC).decode(buf);
        int colorId = buf.readVarInt();
        Optional<DyeColor> color = colorId < 0 || colorId > 15 ? Optional.empty() : Optional.of(DyeColor.byId(colorId));
        return new ShellContainerStatePacket(pos, shell, color);
    }

    public static void handle(ShellContainerStatePacket payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientPacketDispatch.onShellContainerState(payload));
    }
}
