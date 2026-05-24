package com.breakinblocks.neosync.common.block.entity;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.breakinblocks.neosync.NeoSync;
import com.breakinblocks.neosync.common.block.SyncBlocks;

public class SyncBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, NeoSync.MOD_ID);

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShellStorageBlockEntity>> SHELL_STORAGE =
            BLOCK_ENTITIES.register("shell_storage",
                    () -> BlockEntityType.Builder.of(ShellStorageBlockEntity::new,
                            SyncBlocks.SHELL_STORAGE.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ShellConstructorBlockEntity>> SHELL_CONSTRUCTOR =
            BLOCK_ENTITIES.register("shell_constructor",
                    () -> BlockEntityType.Builder.of(ShellConstructorBlockEntity::new,
                            SyncBlocks.SHELL_CONSTRUCTOR.get()).build(null));

    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TreadmillBlockEntity>> TREADMILL =
            BLOCK_ENTITIES.register("treadmill",
                    () -> BlockEntityType.Builder.of(TreadmillBlockEntity::new,
                            SyncBlocks.TREADMILL.get()).build(null));
}

