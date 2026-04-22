package com.breakinblocks.neosync.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class MatrixStackStorage {
    private static PoseStack modelMatrixStack;

    public static void saveModelMatrixStack(PoseStack matrixStack) {
        modelMatrixStack = matrixStack;
    }

    public static PoseStack getModelMatrixStack() {
        if (modelMatrixStack != null) {
            return modelMatrixStack;
        }
        // 1.21.1 no longer passes a PoseStack through renderLevel, so if nothing else has captured
        // one yet we fall back to a snapshot of the global modelview stack.
        PoseStack fallback = new PoseStack();
        fallback.mulPose(RenderSystem.getModelViewStack());
        return fallback;
    }

    private MatrixStackStorage() {}
}
