package com.breakinblocks.neosync.client.networking;

import com.breakinblocks.neosync.api.networking.PlayerIsAlivePacket;
import com.breakinblocks.neosync.api.networking.ShellContainerStatePacket;
import com.breakinblocks.neosync.api.networking.ShellDestroyedPacket;
import com.breakinblocks.neosync.api.networking.ShellStateUpdatePacket;
import com.breakinblocks.neosync.api.networking.ShellUpdatePacket;
import com.breakinblocks.neosync.api.networking.SynchronizationResponsePacket;
import com.breakinblocks.neosync.api.shell.ClientShell;
import com.breakinblocks.neosync.api.shell.Shell;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.api.shell.ShellStateContainer;
import com.breakinblocks.neosync.client.gui.ShellSelectorGUI;
import com.breakinblocks.neosync.common.block.entity.AbstractShellContainerBlockEntity;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientNetworkHandler {
    private ClientNetworkHandler() {
    }

    public static void onSynchronizationResponse(SynchronizationResponsePacket payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            NeoSyncDebug.warn("client-net", "received sync response with no local player");
            return;
        }

        NeoSyncDebug.info(
            "client-net",
            "sync response start={} {} target={} {} storedStatePresent={}",
            payload.startWorld(),
            payload.startPos(),
            payload.targetWorld(),
            payload.targetPos(),
            payload.storedState().isPresent()
        );
        ((ClientShell) player).endSync(
            payload.startWorld(),
            payload.startPos(),
            payload.startFacing(),
            payload.targetWorld(),
            payload.targetPos(),
            payload.targetFacing(),
            payload.storedState().orElse(null)
        );
    }

    public static void onShellUpdate(ShellUpdatePacket payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            NeoSyncDebug.warn(
                "client-net",
                "received shell update with no local player states={}",
                payload.states().size()
            );
            return;
        }

        NeoSyncDebug.info(
            "client-net",
            "shell update world={} artificial={} states={}",
            payload.worldId(),
            payload.isArtificial(),
            payload.states().size()
        );
        Shell shell = (Shell) player;
        shell.changeArtificialStatus(payload.isArtificial());
        shell.setAvailableShellStates(payload.states().stream());
        refreshSelectorIfOpen("full-update");
    }

    public static void onShellStateUpdate(ShellStateUpdatePacket payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            NeoSyncDebug.warn(
                "client-net",
                "received shell state update with no local player kind={}",
                payload.kind()
            );
            return;
        }

        NeoSyncDebug.info(
            "client-net",
            "shell state update kind={} target={} progress={} color={} pos={} added={}",
            payload.kind(),
            payload.targetUuid(),
            payload.progress(),
            payload.color(),
            payload.pos(),
            payload.addedState() == null ? "null" : payload.addedState().getUuid()
        );

        Shell shell = (Shell) player;
        switch (payload.kind()) {
            case ADD -> shell.add(payload.addedState());
            case REMOVE -> {
                ShellState removed = shell.getShellStateByUuid(payload.targetUuid());
                if (removed != null) {
                    shell.remove(removed);
                } else {
                    NeoSyncDebug.warn(
                        "client-net",
                        "REMOVE ignored because shell {} was not in client list",
                        payload.targetUuid()
                    );
                }
            }
            case UPDATE -> {
                ShellState updated = shell.getShellStateByUuid(payload.targetUuid());
                if (updated != null) {
                    updated.setProgress(payload.progress());
                    updated.setColor(payload.color());
                    updated.setPos(payload.pos());
                } else {
                    NeoSyncDebug.warn(
                        "client-net",
                        "UPDATE ignored because shell {} was not in client list",
                        payload.targetUuid()
                    );
                }
            }
            case NONE -> {
            }
        }

        refreshSelectorIfOpen("delta-update:" + payload.kind());
    }

    public static void onShellContainerState(ShellContainerStatePacket payload) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            NeoSyncDebug.warn(
                "client-net",
                "received container-state with no client level pos={}",
                payload.pos()
            );
            return;
        }

        ShellState shellState = payload.shell().orElse(null);
        DyeColor color = payload.color().orElse(null);
        NeoSyncDebug.info(
            "client-net",
            "container-state pos={} shell={} color={}",
            payload.pos(),
            shellState == null ? "null" : shellState.getUuid(),
            color
        );
        if (!applyContainerState(level, payload.pos(), shellState, color)) {
            NeoSyncDebug.warn(
                "client-net",
                "container-state failed to find client ShellStateContainer around {}",
                payload.pos()
            );
        }
    }

    private static boolean applyContainerState(
        ClientLevel level,
        BlockPos pos,
        ShellState shellState,
        DyeColor color
    ) {
        BlockPos[] candidates = new BlockPos[] {pos, pos.below(), pos.above()};
        for (BlockPos candidate : candidates) {
            BlockEntity blockEntity = level.getBlockEntity(candidate);
            if (blockEntity instanceof AbstractShellContainerBlockEntity container) {
                NeoSyncDebug.info(
                    "client-net",
                    "applying full visual container-state to BE at {} class={}",
                    candidate,
                    blockEntity.getClass().getSimpleName()
                );
                container.applyClientVisualState(shellState, color, "packet");
                blockEntity.setChanged();
                return true;
            }
            if (blockEntity instanceof ShellStateContainer container) {
                NeoSyncDebug.info(
                    "client-net",
                    "applying shell-only container-state to BE at {} class={}",
                    candidate,
                    blockEntity == null ? "null" : blockEntity.getClass().getSimpleName()
                );
                container.setShellState(shellState);
                if (blockEntity != null) {
                    blockEntity.setChanged();
                }
                return true;
            }
        }

        ShellStateContainer container = ShellStateContainer.find(level, pos);
        if (container != null) {
            NeoSyncDebug.info("client-net", "applying container-state through ShellStateContainer.find at {}", pos);
            container.setShellState(shellState);
            return true;
        }
        return false;
    }

    public static void onPlayerIsAlive(PlayerIsAlivePacket payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        Player updated = player.clientLevel.getPlayerByUUID(payload.playerUuid());
        if (updated == null) {
            return;
        }

        if (updated.getHealth() <= 0) {
            updated.setHealth(0.01F);
        }
        updated.deathTime = 0;
        NeoSyncDebug.info("client-net", "player alive packet applied for {}", payload.playerUuid());
    }

    public static void onShellDestroyed(ShellDestroyedPacket payload) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        NeoSyncDebug.info("client-net", "shell destroyed effects at {}", payload.pos());
        for (int i = 0; i < 3; ++i) {
            player.clientLevel.addDestroyBlockEffect(payload.pos(), Blocks.DEEPSLATE.defaultBlockState());
            player.clientLevel.addDestroyBlockEffect(payload.pos().above(), Blocks.DEEPSLATE.defaultBlockState());
        }
        player.clientLevel.playSound(
            player,
            payload.pos(),
            SoundEvents.DEEPSLATE_BREAK,
            SoundSource.BLOCKS,
            1.0F,
            player.getVoicePitch()
        );
    }

    private static void refreshSelectorIfOpen(String reason) {
        Screen screen = Minecraft.getInstance().screen;
        if (screen instanceof ShellSelectorGUI selector) {
            NeoSyncDebug.info("client-net", "refreshing ShellSelectorGUI because {}", reason);
            selector.refreshFromNetwork();
        }
    }
}
