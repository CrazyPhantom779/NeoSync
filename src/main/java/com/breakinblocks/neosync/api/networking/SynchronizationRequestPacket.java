package com.breakinblocks.neosync.api.networking;

import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.breakinblocks.neosync.api.shell.ServerShell;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import com.breakinblocks.neosync.common.utils.WorldUtil;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.UUID;

public record SynchronizationRequestPacket(
        Optional<UUID> shellUuid,
        Optional<BlockPos> currentContainerPos
) implements CustomPacketPayload {
    public static final Type<SynchronizationRequestPacket> TYPE =
            new Type<>(NeoSync.locate("shell/synchronization/request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SynchronizationRequestPacket> STREAM_CODEC =
            StreamCodec.of(SynchronizationRequestPacket::encode, SynchronizationRequestPacket::decode);

    public SynchronizationRequestPacket(ShellState shell) {
        this(shell, null);
    }

    public SynchronizationRequestPacket(ShellState shell, @Nullable BlockPos currentContainerPos) {
        this(
                shell == null ? Optional.empty() : Optional.of(shell.getUuid()),
                Optional.ofNullable(currentContainerPos)
        );
    }

    @Override
    public Type<SynchronizationRequestPacket> type() {
        return TYPE;
    }

    public void send() {
        PacketDistributor.sendToServer(this);
    }

    private static void encode(RegistryFriendlyByteBuf buf, SynchronizationRequestPacket payload) {
        buf.writeBoolean(payload.shellUuid().isPresent());
        payload.shellUuid().ifPresent(buf::writeUUID);

        buf.writeBoolean(payload.currentContainerPos().isPresent());
        payload.currentContainerPos().ifPresent(buf::writeBlockPos);
    }

    private static SynchronizationRequestPacket decode(RegistryFriendlyByteBuf buf) {
        Optional<UUID> shellUuid = buf.readBoolean()
                ? Optional.of(buf.readUUID())
                : Optional.empty();

        Optional<BlockPos> currentContainerPos = buf.readBoolean()
                ? Optional.of(buf.readBlockPos())
                : Optional.empty();

        return new SynchronizationRequestPacket(shellUuid, currentContainerPos);
    }

    public static void handle(SynchronizationRequestPacket payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !(player instanceof ServerShell shell)) {
                return;
            }

            ShellState state = payload.shellUuid().map(shell::getShellStateByUuid).orElse(null);

            BlockPos currentPos = player.blockPosition();
            Level currentWorld = player.level();
            ResourceLocation currentWorldId = WorldUtil.getId(currentWorld);

            BlockPos responseCurrentPos = NeoSyncSableCompat.projectOut(currentWorld, currentPos);

            Direction currentFacing = BlockPosUtil.getHorizontalFacing(currentPos, currentWorld)
                    .orElse(player.getDirection().getOpposite());

            Either<?, ?> result = shell.sync(state, payload.currentContainerPos().orElse(null));

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

            if (storedState == null) {
                PacketDistributor.sendToPlayer(player, new SynchronizationResponsePacket(
                        currentWorldId,
                        responseCurrentPos,
                        currentFacing,
                        currentWorldId,
                        responseCurrentPos,
                        currentFacing,
                        Optional.empty()
                ));
                return;
            }
            }).ifRight(failureReasonObject -> {
                PlayerSyncEvents.SyncFailureReason failureReason =
                        (PlayerSyncEvents.SyncFailureReason) failureReasonObject;

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