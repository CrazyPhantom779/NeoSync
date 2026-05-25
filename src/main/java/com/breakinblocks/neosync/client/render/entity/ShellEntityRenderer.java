package com.breakinblocks.neosync.client.render.entity;

import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.client.entity.ShellEntity;
import com.breakinblocks.neosync.client.model.ShellModel;
import com.breakinblocks.neosync.integration.dragonsurvival.NeoSyncDragonSurvivalClientCompat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ShellEntityRenderer extends PlayerRenderer {
    private final ShellModel<PlayerModel<AbstractClientPlayer>> shellModel;

    public ShellEntityRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(context, slim);
        this.shellModel = new ShellModel<>(this.getModel());
        this.shadowRadius = 0;
        this.shadowStrength = 0;
    }

    @Override
    public void render(
        AbstractClientPlayer player,
        float yaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        poseStack.pushPose();

        try {
            poseStack.translate(0.5, 0, 0.5);

            if (player instanceof ShellEntity shell
                && !shell.isActive
                && shell.getState().getProgress() >= ShellState.PROGRESS_DONE) {
                AbstractClientPlayer dragonPlayer =
                    NeoSyncDragonSurvivalClientCompat.getRenderPlayer(shell.getState(), false);

                if (dragonPlayer != null) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180F));
                    poseStack.scale(-1.0F, -1.0F, 1.0F);
                    this.scale(player, poseStack, partialTicks);
                    poseStack.translate(0.0D, -1.501F, 0.0D);
                    poseStack.mulPose(Axis.YP.rotationDegrees(yaw));
                    this.renderDragonPlayer(dragonPlayer, shell.pitchProgress, yaw, partialTicks, poseStack, bufferSource, packedLight);
                    return;
                }
            }

            if (player instanceof ShellEntity shell && !shell.isActive) {
                float progress = shell.getState().getProgress();

                poseStack.mulPose(Axis.YP.rotationDegrees(180F));
                poseStack.scale(-1.0F, -1.0F, 1.0F);
                this.scale(player, poseStack, partialTicks);
                poseStack.translate(0.0D, -1.501F, 0.0D);

                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));

                this.applyStateToModel(this.shellModel, shell.getState());

                VertexConsumer vertexConsumer = this.getVertexConsumerForPartiallyTexturedEntity(
                    shell,
                    progress,
                    this.shellModel.getLayer(player.getSkin().texture()),
                    bufferSource
                );
                this.shellModel.renderToBuffer(poseStack, vertexConsumer, packedLight, getOverlayCoords(player, 0), 0xFFFFFFFF);

                if (progress >= ShellState.PROGRESS_DONE) {
                    for (RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer : this.layers) {
                        layer.render(poseStack, bufferSource, packedLight, player, 0, 0, partialTicks, 0, 0, 0);
                    }
                }
            } else {
                AbstractClientPlayer renderPlayer = player;

                if (player instanceof ShellEntity shell && shell.getState().getProgress() >= ShellState.PROGRESS_DONE) {
                    AbstractClientPlayer dragonPlayer =
                        NeoSyncDragonSurvivalClientCompat.getRenderPlayer(shell.getState(), false);
                    if (dragonPlayer != null) {
                        renderPlayer = dragonPlayer;
                    }
                }

                Direction direction = Direction.fromYRot(yaw);
                poseStack.mulPose(Axis.YP.rotationDegrees(yaw));

                if (direction == Direction.WEST || direction == Direction.EAST) {
                    poseStack.mulPose(Axis.YP.rotationDegrees(180F));
                }

                float pitchProgress = player instanceof ShellEntity shell ? shell.pitchProgress : 0;
                this.renderDragonPlayer(renderPlayer, pitchProgress, yaw, partialTicks, poseStack, bufferSource, packedLight);
            }
        } finally {
            poseStack.popPose();
        }
    }

    private void renderDragonPlayer(
        AbstractClientPlayer player,
        float pitchProgress,
        float yaw,
        float partialTicks,
        PoseStack poseStack,
        MultiBufferSource bufferSource,
        int packedLight
    ) {
        float maxPitch = player.getItemBySlot(EquipmentSlot.CHEST).isEmpty() ? 15 : 5;
        float pitch = maxPitch * pitchProgress;

        float prevXRot = player.getXRot();
        float prevXRotO = player.xRotO;
        player.setXRot(pitch);
        player.xRotO = pitch;

        try {
            super.render(player, yaw, partialTicks, poseStack, bufferSource, packedLight);
        } finally {
            player.setXRot(prevXRot);
            player.xRotO = prevXRotO;
        }
    }

    @SuppressWarnings("unused")
    private VertexConsumer getVertexConsumerForPartiallyTexturedEntity(
        ShellEntity shell,
        float progress,
        RenderType baseLayer,
        MultiBufferSource bufferSource
    ) {
        return bufferSource.getBuffer(baseLayer);
    }

    @Override
    protected boolean shouldShowName(AbstractClientPlayer player) {
        return player.shouldShowName() && super.shouldShowName(player);
    }

    protected void applyStateToModel(ShellModel<PlayerModel<AbstractClientPlayer>> model, ShellState state) {
        var animalModel = model.parentModel;

        animalModel.head.setRotation(0, 0, 0);
        animalModel.body.setRotation(0, 0, 0);
        animalModel.rightArm.setRotation(0, 0, 0);
        animalModel.leftArm.setRotation(0, 0, 0);
        animalModel.rightLeg.setRotation(0, 0, 0);
        animalModel.leftLeg.setRotation(0, 0, 0);
        model.parentModel.young = false;
        model.setBuildProgress(state.getProgress());
    }
}
