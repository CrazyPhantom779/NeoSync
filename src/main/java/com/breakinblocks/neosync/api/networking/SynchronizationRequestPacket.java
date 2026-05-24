package com.breakinblocks.neosync.api.networking;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.api.shell.ServerShell;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import com.breakinblocks.neosync.common.utils.WorldUtil;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.Optional;
import java.util.UUID;

public record SynchronizationRequestPacket(Optional<UUID> shellUuid) implements CustomPacketPayload {
    public static final Type<SynchronizationRequestPacket> TYPE = new Type<>(NeoSync.locate("shell/synchronization/request"));

    public static final StreamCodec<net.minecraft.network.FriendlyByteBuf, SynchronizationRequestPacket> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(UUIDUtil.STREAM_CODEC),
            SynchronizationRequestPacket::shellUuid,
            SynchronizationRequestPacket::new
    );

    public SynchronizationRequestPacket(ShellState shell) {
        this(shell == null ? Optional.empty() : Optional.of(shell.getUuid()));
    }

    @Override
    public Type<SynchronizationRequestPacket> type() {
        return TYPE;
    }

    public void send() {
        PacketDistributor.sendToServer(this);
    }

    public static void handle(SynchronizationRequestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player instanceof ServerShell shell)) {
                return;
            }

            ShellState state = payload.shellUuid.map(shell::getShellStateByUuid).orElse(null);

            BlockPos currentPos = player.blockPosition();
            Level currentWorld = player.level();
            ResourceLocation currentWorldId = WorldUtil.getId(currentWorld);

            BlockPos responseCurrentPos = NeoSyncSableCompat.projectOut(currentWorld, currentPos);

            Direction currentFacing = BlockPosUtil.getHorizontalFacing(currentPos, currentWorld)
                    .orElse(player.getDirection().getOpposite());

            Either<?, ?> result = shell.sync(state);

            result.ifLeft(storedStateObject -> {
                if (state == null) {
                    return;
                }

                ShellState storedState = (ShellState) storedStateObject;

                ResourceLocation targetWorldId = state.getWorld();
                BlockPos targetPos = state.getPos();

                Level targetWorld = player.getServer() == null
                        ? null
                        : WorldUtil.findWorld(player.getServer().getAllLevels(), targetWorldId).orElse(null);

                BlockPos responseTargetPos = targetWorld == null
                        ? targetPos
                        : NeoSyncSableCompat.projectOut(targetWorld, targetPos);

                Direction targetFacing = player.getDirection().getOpposite();

                PacketDistributor.sendToPlayer(player, new SynchronizationResponsePacket(
                        currentWorldId,
                        responseCurrentPos,
                        currentFacing,
                        targetWorldId,
                        responseTargetPos,
                        targetFacing,
                        Optional.of(storedState)
                ));
            }).ifRight(failureReasonObject -> {
                var failureReason = (com.breakinblocks.neosync.api.event.PlayerSyncEvents.SyncFailureReason) failureReasonObject;

                player.sendSystemMessage(failureReason.toText());

                PacketDistributor.sendToPlayer(player, new SynchronizationResponsePacket(
                        currentWorldId,
                        responseCurrentPos,
                        currentFacing,
                        currentWorldId,
                        responseCurrentPos,
                        currentFacing,
                        Optional.empty()
                ));
            });
        });
    }
}