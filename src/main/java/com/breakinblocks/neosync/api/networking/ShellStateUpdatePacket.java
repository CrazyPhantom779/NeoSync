package com.breakinblocks.neosync.api.networking;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.api.shell.ShellStateUpdateType;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

public record ShellStateUpdatePacket(
    ShellStateUpdateType kind,
    @Nullable ShellState addedState,
    @Nullable UUID targetUuid,
    float progress,
    @Nullable DyeColor color,
    @Nullable BlockPos pos
) implements CustomPacketPayload {
    public static final Type<ShellStateUpdatePacket> TYPE =
        new Type<>(NeoSync.locate("shell/state/update"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShellStateUpdatePacket> STREAM_CODEC =
        StreamCodec.of(ShellStateUpdatePacket::encode, ShellStateUpdatePacket::decode);

    public ShellStateUpdatePacket(ShellStateUpdateType kind, ShellState state) {
        this(
            kind,
            adoptAddedState(kind, state),
            state == null ? null : state.getUuid(),
            state == null ? 0F : state.getProgress(),
            state == null ? null : state.getColor(),
            state == null ? null : state.getPos()
        );
        if (state == null && kind != ShellStateUpdateType.NONE) {
            throw new IllegalStateException("ShellStateUpdatePacket requires a non-null state for kind " + kind);
        }
    }

    private static @Nullable ShellState adoptAddedState(ShellStateUpdateType kind, ShellState state) {
        return kind == ShellStateUpdateType.ADD ? state : null;
    }

    @Override
    public Type<ShellStateUpdatePacket> type() {
        return TYPE;
    }

    public void send(ServerPlayer player) {
        NeoSyncDebug.info(
            "shell-delta-packet",
            "send player={} kind={} targetUuid={} progress={} color={} pos={} added={}",
            player.getName().getString(),
            this.kind,
            this.targetUuid,
            this.progress,
            this.color,
            this.pos,
            this.addedState == null ? "null" : NeoSyncDebug.describeShell(this.addedState)
        );
        PacketDistributor.sendToPlayer(player, this);
    }

    private static void encode(RegistryFriendlyByteBuf buf, ShellStateUpdatePacket payload) {
        NeoSyncDebug.info(
            "shell-delta-packet",
            "encode kind={} targetUuid={} progress={} color={} pos={} added={}",
            payload.kind,
            payload.targetUuid,
            payload.progress,
            payload.color,
            payload.pos,
            payload.addedState == null ? "null" : NeoSyncDebug.describeShell(payload.addedState)
        );
        buf.writeEnum(payload.kind);
        switch (payload.kind) {
            case ADD -> {
                if (payload.addedState == null) {
                    throw new IllegalStateException("ADD packet requires non-null addedState");
                }
                ShellState.STREAM_CODEC.encode(buf, payload.addedState);
            }
            case REMOVE -> {
                if (payload.targetUuid == null) {
                    throw new IllegalStateException("REMOVE packet requires non-null targetUuid");
                }
                buf.writeUUID(payload.targetUuid);
            }
            case UPDATE -> {
                if (payload.targetUuid == null || payload.pos == null) {
                    throw new IllegalStateException("UPDATE packet requires non-null targetUuid and pos");
                }
                buf.writeUUID(payload.targetUuid);
                buf.writeVarInt((int) (payload.progress * 100));
                buf.writeVarInt(payload.color == null ? Byte.MAX_VALUE : payload.color.getId());
                buf.writeBlockPos(payload.pos);
            }
            case NONE -> {
            }
        }
    }

    private static ShellStateUpdatePacket decode(RegistryFriendlyByteBuf buf) {
        ShellStateUpdateType kind = buf.readEnum(ShellStateUpdateType.class);
        ShellStateUpdatePacket decoded = switch (kind) {
            case ADD -> new ShellStateUpdatePacket(kind, ShellState.STREAM_CODEC.decode(buf), null, 0F, null, null);
            case REMOVE -> new ShellStateUpdatePacket(kind, null, buf.readUUID(), 0F, null, null);
            case UPDATE -> {
                UUID uuid = buf.readUUID();
                float progress = Mth.clamp(buf.readVarInt() / 100F, 0F, 1F);
                int colorId = buf.readVarInt();
                DyeColor color = colorId < 0 || colorId > 15 ? null : DyeColor.byId(colorId);
                BlockPos pos = buf.readBlockPos();
                yield new ShellStateUpdatePacket(kind, null, uuid, progress, color, pos);
            }
            case NONE -> new ShellStateUpdatePacket(kind, null, null, 0F, null, null);
        };

        NeoSyncDebug.info(
            "shell-delta-packet",
            "decode kind={} targetUuid={} progress={} color={} pos={} added={}",
            decoded.kind,
            decoded.targetUuid,
            decoded.progress,
            decoded.color,
            decoded.pos,
            decoded.addedState == null ? "null" : NeoSyncDebug.describeShell(decoded.addedState)
        );
        return decoded;
    }

    public static void handle(ShellStateUpdatePacket payload, IPayloadContext context) {
        NeoSyncDebug.info(
            "shell-delta-packet",
            "handle kind={} targetUuid={} progress={} color={} pos={} contextPlayer={}",
            payload.kind,
            payload.targetUuid,
            payload.progress,
            payload.color,
            payload.pos,
            context.player()
        );
        context.enqueueWork(() -> ClientPacketDispatch.onShellStateUpdate(payload));
    }
}
