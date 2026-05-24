package com.breakinblocks.neosync.common.block;

import com.breakinblocks.neosync.common.block.entity.AbstractShellContainerBlockEntity;
import com.breakinblocks.neosync.common.block.entity.TickableBlockEntity;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.common.utils.ItemUtil;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

@SuppressWarnings("deprecation")
public abstract class AbstractShellContainerBlock extends BaseEntityBlock {
    public static final EnumProperty<DoubleBlockHalf> HALF = BlockStateProperties.DOUBLE_BLOCK_HALF;
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    public static final BooleanProperty OPEN = BlockStateProperties.OPEN;
    public static final EnumProperty<ComparatorOutputType> OUTPUT = EnumProperty.create("output", ComparatorOutputType.class);

    private static final VoxelShape SOLID_SHAPE_TOP;
    private static final VoxelShape SOLID_SHAPE_BOTTOM;
    private static final VoxelShape NORTH_SHAPE_TOP;
    private static final VoxelShape NORTH_SHAPE_BOTTOM;
    private static final VoxelShape SOUTH_SHAPE_TOP;
    private static final VoxelShape SOUTH_SHAPE_BOTTOM;
    private static final VoxelShape EAST_SHAPE_TOP;
    private static final VoxelShape EAST_SHAPE_BOTTOM;
    private static final VoxelShape WEST_SHAPE_TOP;
    private static final VoxelShape WEST_SHAPE_BOTTOM;

    protected AbstractShellContainerBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(
                this.getStateDefinition().any()
                        .setValue(OPEN, false)
                        .setValue(HALF, DoubleBlockHalf.LOWER)
                        .setValue(FACING, Direction.NORTH)
                        .setValue(OUTPUT, ComparatorOutputType.PROGRESS)
        );
    }

    public static void setOpen(BlockState state, Level world, BlockPos pos, boolean open) {
        setPropertyForBothParts(state, world, pos, OPEN, open);
    }

    public static boolean isOpen(BlockState state) {
        return state.getValue(OPEN);
    }

    public static boolean isBottom(BlockState state) {
        DoubleBlockHalf half = state.getValue(HALF);
        return half == DoubleBlockHalf.LOWER;
    }

    public static DoubleBlockHalf getShellContainerHalf(BlockState state) {
        return state.getValue(HALF);
    }

    public static Direction getDirectionTowardsAnotherPart(BlockState state) {
        return isBottom(state) ? Direction.UP : Direction.DOWN;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor world, BlockPos pos, BlockPos neighborPos) {
        DoubleBlockHalf doubleBlockHalf = state.getValue(HALF);
        if (direction.getAxis() == Direction.Axis.Y && (doubleBlockHalf == DoubleBlockHalf.LOWER) == (direction == Direction.UP)) {
            return neighborState.is(this) && neighborState.getValue(HALF) != doubleBlockHalf ? state.setValue(FACING, neighborState.getValue(FACING)) : Blocks.AIR.defaultBlockState();
        } else {
            return doubleBlockHalf == DoubleBlockHalf.LOWER && direction == Direction.DOWN && !state.canSurvive(world, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, direction, neighborState, world, pos, neighborPos);
        }
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Level world = ctx.getLevel();
        BlockPos blockPos = ctx.getClickedPos();
        if (blockPos.getY() < world.getMaxBuildHeight() - 1 && world.getBlockState(blockPos.above()).canBeReplaced(ctx)) {
            return this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection()).setValue(HALF, DoubleBlockHalf.LOWER);
        }

        return null;
    }

    @Override
    public void setPlacedBy(Level world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        world.setBlock(pos.above(), state.setValue(HALF, DoubleBlockHalf.UPPER), 3);
    }

    @Override
    public void entityInside(BlockState state, Level world, BlockPos pos, Entity entity) {
        super.entityInside(state, world, pos, entity);
        if (!world.isClientSide && entity instanceof Player && isBottom(state)) {
            setOpen(state, world, pos, true);
        }
    }

    @Override
    public BlockState playerWillDestroy(Level world, BlockPos pos, BlockState state, Player player) {
        boolean bottom = isBottom(state);
        BlockPos bottomPos = bottom ? pos : pos.below();
        if (!world.isClientSide && player.isCreative()) {
            if (!bottom) {
                BlockState blockState = world.getBlockState(bottomPos);
                if (blockState.getBlock() == state.getBlock() && blockState.getValue(HALF) == DoubleBlockHalf.LOWER) {
                    world.setBlock(bottomPos, Blocks.AIR.defaultBlockState(), 35);
                    world.levelEvent(player, 2001, bottomPos, Block.getId(blockState));
                }
            }
        }
        return super.playerWillDestroy(world, pos, state, player);
    }

    @Override
    public void onRemove(BlockState state, Level world, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (isBottom(state) && world.getBlockEntity(pos) instanceof AbstractShellContainerBlockEntity shellContainer) {
                shellContainer.onBreak(world, pos);
            }
            world.removeBlockEntity(pos);
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (ItemUtil.isWrench(stack)) {
            if (!world.isClientSide) {
                world.setBlock(pos, state.cycle(OUTPUT), 10);
                world.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
            return ItemInteractionResult.sidedSuccess(world.isClientSide);
        }

        BlockPos targetPos = isBottom(state) ? pos : pos.below();
        if (world.getBlockEntity(targetPos) instanceof AbstractShellContainerBlockEntity shellContainer) {
            InteractionResult result = shellContainer.onUse(world, targetPos, player, hand);
            return switch (result) {
                case SUCCESS -> ItemInteractionResult.SUCCESS;
                case CONSUME -> ItemInteractionResult.CONSUME;
                case FAIL -> ItemInteractionResult.FAIL;
                case PASS -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
                default -> ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            };
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
        BlockPos targetPos = isBottom(state) ? pos : pos.below();
        if (world.getBlockEntity(targetPos) instanceof AbstractShellContainerBlockEntity shellContainer) {
            return shellContainer.onUse(world, targetPos, player, InteractionHand.MAIN_HAND);
        }
        return super.useWithoutItem(state, world, pos, player, hit);
    }

    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos) {
        return world.getBlockEntity(pos) instanceof AbstractShellContainerBlockEntity shellContainer
                ? state.getValue(OUTPUT) == ComparatorOutputType.PROGRESS
                ? shellContainer.getProgressComparatorOutput()
                : shellContainer.getInventoryComparatorOutput()
                : 0;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @OnlyIn(Dist.CLIENT)
    public long getSeed(BlockState state, BlockPos pos) {
        return Mth.getSeed(pos.getX(), pos.below(state.getValue(HALF) == DoubleBlockHalf.LOWER ? 0 : 1).getY(), pos.getZ());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(HALF, FACING, OPEN, OUTPUT);
    }

    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
        if (!isBottom(state)) {
            return null;
        }
        return world.isClientSide ? TickableBlockEntity::clientTicker : TickableBlockEntity::serverTicker;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        boolean isBottom = isBottom(state);
        if (!isOpen(state)) {
            return isBottom ? SOLID_SHAPE_BOTTOM : SOLID_SHAPE_TOP;
        }

        Direction direction = state.getValue(FACING);
        return switch (direction) {
            case NORTH -> isBottom ? NORTH_SHAPE_BOTTOM : NORTH_SHAPE_TOP;
            case SOUTH -> isBottom ? SOUTH_SHAPE_BOTTOM : SOUTH_SHAPE_TOP;
            case EAST -> isBottom ? EAST_SHAPE_BOTTOM : EAST_SHAPE_TOP;
            case WEST -> isBottom ? WEST_SHAPE_BOTTOM : WEST_SHAPE_TOP;
            default -> throw new IllegalArgumentException();
        };
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public enum ComparatorOutputType implements StringRepresentable {
        PROGRESS,
        INVENTORY;

        @Override
        public String getSerializedName() {
            return this == PROGRESS ? "progress" : "inventory";
        }

        @Override
        public String toString() {
            return this.getSerializedName();
        }
    }

    private static <T extends Comparable<T>> void setPropertyForBothParts(
            BlockState state,
            Level world,
            BlockPos pos,
            Property<T> property,
            T value
    ) {
        if (!state.hasProperty(HALF)) {
            return;
        }

        setPropertyIfPresent(world, pos, property, value);

        BlockPos otherPartPos = pos.relative(getDirectionTowardsAnotherPart(state));
        setPropertyIfPresent(world, otherPartPos, property, value);
    }

    private static <T extends Comparable<T>> void setPropertyIfPresent(
            Level world,
            BlockPos pos,
            Property<T> property,
            T value
    ) {
        BlockState currentState = world.getBlockState(pos);

        if (!currentState.hasProperty(property)) {
            return;
        }

        if (currentState.getValue(property).equals(value)) {
            return;
        }

        world.setBlock(pos, currentState.setValue(property, value), Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
    }

    static {
        final VoxelShape ROOF = Block.box(0, 15, 0, 16, 16, 16);
        final VoxelShape FLOOR = Block.box(0, 0, 0, 16, 1, 16);
        final VoxelShape NORTH_WALL = Block.box(0, 0, 0, 16, 16, 1);
        final VoxelShape SOUTH_WALL = Block.box(0, 0, 15, 16, 16, 16);
        final VoxelShape EAST_WALL = Block.box(15, 0, 0, 16, 16, 16);
        final VoxelShape WEST_WALL = Block.box(0, 0, 0, 1, 16, 16);

        final VoxelShape NORTH_SHAPE = Shapes.or(NORTH_WALL, EAST_WALL, WEST_WALL).optimize();
        final VoxelShape SOUTH_SHAPE = Shapes.or(SOUTH_WALL, EAST_WALL, WEST_WALL).optimize();
        final VoxelShape EAST_SHAPE = Shapes.or(NORTH_WALL, SOUTH_WALL, EAST_WALL).optimize();
        final VoxelShape WEST_SHAPE = Shapes.or(NORTH_WALL, SOUTH_WALL, WEST_WALL).optimize();

        SOLID_SHAPE_TOP = Shapes.or(NORTH_WALL, SOUTH_WALL, EAST_WALL, WEST_WALL, ROOF).optimize();
        SOLID_SHAPE_BOTTOM = Shapes.or(NORTH_WALL, SOUTH_WALL, EAST_WALL, WEST_WALL, FLOOR).optimize();

        NORTH_SHAPE_TOP = Shapes.or(NORTH_SHAPE, ROOF).optimize();
        NORTH_SHAPE_BOTTOM = Shapes.or(NORTH_SHAPE, FLOOR).optimize();
        SOUTH_SHAPE_TOP = Shapes.or(SOUTH_SHAPE, ROOF).optimize();
        SOUTH_SHAPE_BOTTOM = Shapes.or(SOUTH_SHAPE, FLOOR).optimize();
        EAST_SHAPE_TOP = Shapes.or(EAST_SHAPE, ROOF).optimize();
        EAST_SHAPE_BOTTOM = Shapes.or(EAST_SHAPE, FLOOR).optimize();
        WEST_SHAPE_TOP = Shapes.or(WEST_SHAPE, ROOF).optimize();
        WEST_SHAPE_BOTTOM = Shapes.or(WEST_SHAPE, FLOOR).optimize();
    }
}
