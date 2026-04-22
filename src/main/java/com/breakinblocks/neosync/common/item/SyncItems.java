package com.breakinblocks.neosync.common.item;

import net.minecraft.world.item.Item;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.common.block.SyncBlocks;

public class SyncItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(NeoSync.MOD_ID);

    public static final DeferredItem<Item> SYNC_CORE = ITEMS.register("sync_core",
            () -> new Item(new Item.Properties().stacksTo(16)));

    public static final DeferredItem<ShellConstructorItem> SHELL_CONSTRUCTOR = ITEMS.register("shell_constructor",
            () -> new ShellConstructorItem(SyncBlocks.SHELL_CONSTRUCTOR.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredItem<ShellStorageItem> SHELL_STORAGE = ITEMS.register("shell_storage",
            () -> new ShellStorageItem(SyncBlocks.SHELL_STORAGE.get(), new Item.Properties().stacksTo(1)));

    public static final DeferredItem<TreadmillItem> TREADMILL = ITEMS.register("treadmill",
            () -> new TreadmillItem(SyncBlocks.TREADMILL.get(), new Item.Properties().stacksTo(1)));
}
