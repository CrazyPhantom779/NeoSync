package com.breakinblocks.neosync.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.breakinblocks.neosync.NeoSync;

public class SyncBlocks {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(NeoSync.MOD_ID);

    public static final DeferredBlock<ShellStorageBlock> SHELL_STORAGE = BLOCKS.register("shell_storage",
            () -> new ShellStorageBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .mapColor(MapColor.COLOR_GRAY)
                    .requiresCorrectToolForDrops()
                    .strength(1.8F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn(SyncBlocks::never)
                    .isRedstoneConductor(SyncBlocks::never)
                    .isSuffocating(SyncBlocks::never)
                    .isViewBlocking(SyncBlocks::never)));

    public static final DeferredBlock<ShellConstructorBlock> SHELL_CONSTRUCTOR = BLOCKS.register("shell_constructor",
            () -> new ShellConstructorBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.GLASS)
                    .mapColor(MapColor.COLOR_GRAY)
                    .requiresCorrectToolForDrops()
                    .strength(1.8F)
                    .sound(SoundType.GLASS)
                    .noOcclusion()
                    .isValidSpawn(SyncBlocks::never)
                    .isRedstoneConductor(SyncBlocks::never)
                    .isSuffocating(SyncBlocks::never)
                    .isViewBlocking(SyncBlocks::never)));

    public static final DeferredBlock<TreadmillBlock> TREADMILL = BLOCKS.register("treadmill",
            () -> new TreadmillBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.STONE)
                    .mapColor(MapColor.COLOR_GRAY)
                    .requiresCorrectToolForDrops()
                    .strength(1.8F)
                    .isValidSpawn(SyncBlocks::never)
                    .isRedstoneConductor(SyncBlocks::never)
                    .isSuffocating(SyncBlocks::never)
                    .isViewBlocking(SyncBlocks::never)));

    private static boolean never(BlockState state, BlockGetter world, BlockPos pos) {
        return false;
    }

    private static Boolean never(BlockState state, BlockGetter world, BlockPos pos, EntityType<?> type) {
        return false;
    }
}
