package com.breakinblocks.neosync.client.utils;

import com.google.common.collect.Queues;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.common.utils.WorldUtil;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;

@OnlyIn(Dist.CLIENT)
public final class PlayerUtil {
    private static final ResourceLocation ANY_WORLD = ResourceLocation.fromNamespaceAndPath(NeoSync.MOD_ID, "any_world");
    private static final ConcurrentMap<ResourceLocation, ConcurrentLinkedQueue<PlayerUpdate>> UPDATES = new ConcurrentHashMap<>();

    static {
        NeoForge.EVENT_BUS.register(PlayerUtil.class);
    }

    public static void recordPlayerUpdate(PlayerUpdate playerUpdate) {
        recordPlayerUpdate(null, playerUpdate);
    }

    public static void recordPlayerUpdate(ResourceLocation worldId, PlayerUpdate playerUpdate) {
        worldId = worldId == null ? ANY_WORLD : worldId;

        Minecraft client = Minecraft.getInstance();
        if (client != null && client.player != null && existsInTargetWorld(client.player, worldId)) {
            playerUpdate.onLoad(client.player, client.player.clientLevel, client);
        } else {
            UPDATES.computeIfAbsent(worldId, id -> Queues.newConcurrentLinkedQueue()).add(playerUpdate);
        }
    }

    private static boolean existsInTargetWorld(Entity entity, ResourceLocation worldId) {
        return worldId == ANY_WORLD || WorldUtil.isOf(worldId, entity.level());
    }

    private static void executeUpdates(LocalPlayer player, ClientLevel world, Minecraft client, ConcurrentLinkedQueue<PlayerUpdate> queue) {
        if (queue == null) {
            return;
        }

        while (!queue.isEmpty()) {
            queue.poll().onLoad(player, world, client);
        }
    }

    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || !event.getLevel().isClientSide() || event.getEntity() != client.player) {
            return;
        }
        ClientLevel world = (ClientLevel) event.getLevel();
        executeUpdates(client.player, world, client, UPDATES.get(WorldUtil.getId(world)));
        executeUpdates(client.player, world, client, UPDATES.get(ANY_WORLD));
    }

    @FunctionalInterface
    public interface PlayerUpdate {
        void onLoad(LocalPlayer player, ClientLevel world, Minecraft client);
    }

    private PlayerUtil() {
    }
}
