package com.breakinblocks.neosync.common.utils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.Vec3;

import java.util.Optional;

public final class BlockPosUtil {
    public static Optional<Direction> getHorizontalFacing(BlockPos pos, BlockGetter blockView) {
        BlockState state = blockView.getBlockState(pos);

        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return Optional.of(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
        }

        return Optional.empty();
    }

    public static boolean hasPlayerInside(BlockPos pos, EntityGetter world) {
        return hasPlayerInside(Vec3.atCenterOf(pos), world);
    }

    public static boolean hasPlayerInside(Vec3 center, EntityGetter world) {
        return world.getNearestPlayer(center.x, center.y, center.z, 1.0D, false) != null;
    }

    public static boolean isEntityInside(Entity entity, BlockPos pos) {
        return isEntityInside(entity, Vec3.atCenterOf(pos));
    }

    public static boolean isEntityInside(Entity entity, Vec3 center) {
        double dX = Math.abs(center.x - entity.getX());
        double dZ = Math.abs(center.z - entity.getZ());

        final double MAX_DELTA = 0.01D;
        return dX < MAX_DELTA && dZ < MAX_DELTA;
    }

    public static void moveEntity(Entity entity, BlockPos target, Direction facing, boolean inside) {
        moveEntity(entity, Vec3.atCenterOf(target), facing, inside);
    }

    public static void moveEntity(Entity entity, Vec3 targetCenter, Direction facing, boolean inside) {
        Direction targetDirection = facing.getOpposite();

        double targetX = targetCenter.x;
        double targetZ = targetCenter.z;

        if (!inside) {
            targetX += targetDirection.getStepX();
            targetZ += targetDirection.getStepZ();
        }

        Vec3 currentPos = entity.position();

        final double MAX_SPEED = 0.33D;
        double velocityX = getMinVelocity(targetX - currentPos.x, MAX_SPEED);
        double velocityZ = getMinVelocity(targetZ - currentPos.z, MAX_SPEED);

        float yaw = targetDirection.toYRot();

        entity.setDeltaMovement(velocityX, 0.0D, velocityZ);
        entity.setXRot(0.0F);
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
        entity.setYBodyRot(yaw);
        entity.yRotO = yaw;

        if (entity instanceof LivingEntity livingEntity) {
            livingEntity.yBodyRotO = yaw;
            livingEntity.yHeadRotO = yaw;
        }
    }

    private static double getMinVelocity(double velocity, double absLimit) {
        return Math.abs(velocity) < absLimit ? velocity : absLimit * Math.signum(velocity);
    }
}