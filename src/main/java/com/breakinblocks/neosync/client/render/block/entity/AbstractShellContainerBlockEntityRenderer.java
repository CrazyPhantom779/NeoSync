package com.breakinblocks.neosync.client.render.block.entity;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.client.model.AbstractShellContainerModel;
import com.breakinblocks.neosync.client.model.DoubleBlockModel;
import com.breakinblocks.neosync.common.block.entity.AbstractShellContainerBlockEntity;
import com.breakinblocks.neosync.common.block.entity.ShellEntity;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractShellContainerBlockEntityRenderer<T extends AbstractShellContainerBlockEntity> extends DoubleBlockEntityRenderer<T> {
    public AbstractShellContainerBlockEntityRenderer(BlockEntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(T blockEntity, float tickDelta, PoseStack matrices, MultiBufferSource vertexConsumers, int light, int overlay) {
        super.render(blockEntity, tickDelta, matrices, vertexConsumers, light, overlay);
        if (blockEntity.getShellState() != null) {
            this.renderShell(blockEntity.getShellState(), blockEntity, tickDelta, this.getBlockState(blockEntity), matrices, vertexConsumers, light);
        }
    }

    protected void renderShell(ShellState shellState, T blockEntity, float tickDelta, BlockState blockState, PoseStack matrices, MultiBufferSource vertexConsumers, int light) {
        float yaw = this.getFacing(blockState).getOpposite().toYRot();
        ShellEntity shellEntity = this.createEntity(shellState, blockEntity, tickDelta);

        EntityRenderer<? super ShellEntity> renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(shellEntity);
        renderer.render(shellEntity, yaw, 0, matrices, vertexConsumers, light);
    }

    @Override
    protected DoubleBlockModel getModel(T blockEntity, BlockState blockState, float tickDelta) {
        AbstractShellContainerModel model = this.getShellContainerModel(blockEntity, blockState, tickDelta);
        model.doorOpenProgress = blockEntity.getDoorOpenProgress(tickDelta);
        return model;
    }

    protected abstract ShellEntity createEntity(ShellState shellState, T blockEntity, float tickDelta);

    protected abstract AbstractShellContainerModel getShellContainerModel(T blockEntity, BlockState blockState, float tickDelta);
}