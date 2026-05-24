package com.breakinblocks.neosync.common.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import com.breakinblocks.neosync.common.block.entity.ShellStorageBlockEntity;

@SuppressWarnings("deprecation")
public class ShellStorageBlock extends AbstractShellContainerBlock {
    public static final MapCodec<ShellStorageBlock> CODEC = simpleCodec(ShellStorageBlock::new);
    public static final BooleanProperty ENABLED = BlockStateProperties.ENABLED;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public ShellStorageBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.getStateDefinition().any().setValue(OPEN, false).setValue(ENABLED, false).setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends ShellStorageBlock> codec() {
        return CODEC;
    }

    public static boolean isEnabled(BlockState state) {
        return state.getValue(ENABLED);
    }

    public static boolean isPowered(BlockState state) {
        return state.getValue(POWERED);
    }

    public static void setPowered(BlockState state, Level world, BlockPos pos, boolean powered) {
        if (state.getValue(POWERED) != powered) {
            world.setBlock(pos, state.setValue(POWERED, powered), 10);

            BlockPos secondPos = pos.relative(getDirectionTowardsAnotherPart(state));
            BlockState secondState = world.getBlockState(secondPos);
            if (secondState != null) {
                world.setBlock(secondPos, secondState.setValue(POWERED, powered), 10);
            }
        }
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ShellStorageBlockEntity(pos, state);
    }

    @Override
    public void neighborChanged(BlockState state, Level world, BlockPos pos, Block block, BlockPos fromPos, boolean notify) {
        if (!world.isClientSide) {
            boolean enabled = state.getValue(ENABLED);
            boolean shouldBeEnabled = shouldBeEnabled(state, world, pos);
            if (enabled != shouldBeEnabled) {
                BlockPos secondPartPos = pos.relative(getDirectionTowardsAnotherPart(state));
                if (enabled) {
                    world.scheduleTick(pos, this, 4);
                    world.scheduleTick(secondPartPos, this, 4);
                } else {
                    world.setBlock(pos, state.setValue(ENABLED, true), 2);
                    BlockState secondPartState = world.getBlockState(secondPartPos);
                    if (secondPartState.is(this)) {
                        world.setBlock(secondPartPos, secondPartState.setValue(ENABLED, true), 2);
                    }
                }
            }
        }
    }

    @Override
    public void tick(BlockState state, ServerLevel world, BlockPos pos, RandomSource random) {
        if (state.getValue(ENABLED) && !shouldBeEnabled(state, world, pos)) {
            world.setBlock(pos, state.setValue(ENABLED, false), 2);
        }
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        super.entityInside(state, world, pos, entity);
        if (world.isClientSide && entity instanceof Player && isBottom(state)) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof ShellStorageBlockEntity) {
                ((ShellStorageBlockEntity)blockEntity).onEntityCollisionClient(entity, state);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(ENABLED);
        builder.add(POWERED);
    }

    private static boolean shouldBeEnabled(BlockState state, Level world, BlockPos pos) {
        return world.hasNeighborSignal(pos) || world.hasNeighborSignal(pos.relative(getDirectionTowardsAnotherPart(state)));
    }
}
