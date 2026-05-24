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
    private BlockPosUtil() {
    }

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

        final double MAX_DELTA = 0.45D;
        return dX < MAX_DELTA && dZ < MAX_DELTA;
    }

    public static void moveEntity(Entity entity, BlockPos target, Direction facing, boolean inside) {
        Direction outsideDirection = facing.getOpposite();
        moveEntity(entity, Vec3.atCenterOf(target), Vec3.atCenterOf(target.relative(outsideDirection)), inside);
    }

    public static void moveEntity(Entity entity, Vec3 insideCenter, Vec3 outsideCenter, boolean inside) {
        Vec3 target = inside ? insideCenter : outsideCenter;
        Vec3 currentPos = entity.position();

        final double MAX_SPEED = 0.33D;
        double velocityX = getMinVelocity(target.x - currentPos.x, MAX_SPEED);
        double velocityZ = getMinVelocity(target.z - currentPos.z, MAX_SPEED);

        Vec3 outsideVector = outsideCenter.subtract(insideCenter);
        float yaw = entity.getYRot();

        if (outsideVector.lengthSqr() > 1.0E-7D) {
            yaw = (float) (Math.atan2(outsideVector.z, outsideVector.x) * 180.0D / Math.PI) - 90.0F;
        }

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