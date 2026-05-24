package com.breakinblocks.neosync.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent;
import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.client.render.SyncRenderers;
import com.breakinblocks.neosync.common.block.entity.SyncBlockEntities;
import com.breakinblocks.neosync.common.item.SyncItems;
import com.breakinblocks.neosync.integration.jade.JadeIntegration;

@EventBusSubscriber(modid = NeoSync.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class SyncClientExtensions {
    private SyncClientExtensions() {}

    @SubscribeEvent
    public static void registerItemExtensions(RegisterClientExtensionsEvent event) {
        event.registerItem(
                SyncRenderers.createItemRenderer(SyncBlockEntities.SHELL_CONSTRUCTOR.get(), SyncItems.SHELL_CONSTRUCTOR.get().getBlock()),
                SyncItems.SHELL_CONSTRUCTOR.get());

        event.registerItem(
                SyncRenderers.createItemRenderer(SyncBlockEntities.SHELL_STORAGE.get(), SyncItems.SHELL_STORAGE.get().getBlock()),
                SyncItems.SHELL_STORAGE.get());

        event.registerItem(
                SyncRenderers.createItemRenderer(SyncBlockEntities.TREADMILL.get(), SyncItems.TREADMILL.get().getBlock()),
                SyncItems.TREADMILL.get());
    }

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        if (ModList.get().isLoaded("jade")) {
            event.enqueueWork(JadeIntegration::register);
        }
    }
}

