package com.breakinblocks.neosync.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.client.model.DoubleBlockModel;
import com.breakinblocks.neosync.common.block.entity.DoubleBlockEntity;

@OnlyIn(Dist.CLIENT)
public abstract class DoubleBlockEntityRenderer<T extends BlockEntity & DoubleBlockEntity> implements BlockEntityRenderer<T> {
    protected final BlockEntityRendererProvider.Context context;

    public DoubleBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        this.context = context;
    }

    @Override
    public void render(T blockEntity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        BlockState blockState = this.getBlockState(blockEntity);

        matrices.pushPose();
        try {
            Direction face = this.getFacing(blockState);
            float rotation = face.toYRot();

            matrices.translate(0.5D, 0.75D, 0.5D);
            matrices.scale(-0.5F, -0.5F, 0.5F);
            matrices.mulPose(Axis.YP.rotationDegrees(rotation));

            DoubleBlockModel model = this.getModel(blockEntity, blockState, tickDelta);
            ResourceLocation textureId = this.getTextureId();
            VertexConsumer consumer = vertexConsumers.getBuffer(model.renderType(textureId));

            if (blockEntity.hasLevel()) {
                model.render(blockEntity.getBlockType(blockState), matrices, consumer, light, overlay);
            } else {
                model.renderToBuffer(matrices, consumer, light, overlay, 0xFFFFFFFF);
            }
        } finally {
            matrices.popPose();
        }
    }

    protected BlockState getBlockState(T blockEntity) {
        return blockEntity.hasLevel() ? blockEntity.getBlockState() : this.getDefaultState();
    }

    protected Direction getFacing(BlockState blockState) {
        return blockState.getValue(BlockStateProperties.HORIZONTAL_FACING);
    }

    protected abstract DoubleBlockModel getModel(T blockEntity, BlockState blockState, float tickDelta);

    protected abstract BlockState getDefaultState();

    protected abstract ResourceLocation getTextureId();
}
