package com.breakinblocks.neosync.client.utils.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderBuffers;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.common.utils.math.Radians;
import org.joml.Matrix4f;

import java.util.function.Consumer;

@OnlyIn(Dist.CLIENT)
public final class RenderSystemUtil {
    public static final int MAX_LIGHT_LEVEL = (15 << 20) | (15 << 4);
    private static final int SECTION_LAYER_SIZE = 256;
    private static final RenderBuffers RENDER_BUFFERS = new RenderBuffers(SECTION_LAYER_SIZE);
    private static final ByteBufferBuilder IMMEDIATE_BYTE_BUFFER = new ByteBufferBuilder(SECTION_LAYER_SIZE);

    public static void drawTriangleStrip(Consumer<VertexConsumer> consumer) {
        drawTriangleStrip(consumer, DefaultVertexFormat.POSITION_COLOR);
    }

    public static void drawTriangleStrip(Consumer<VertexConsumer> consumer, VertexFormat format) {
        draw(consumer, VertexFormat.Mode.TRIANGLE_STRIP, format);
    }

    public static void drawAnnulusSector(PoseStack poseStack, double cX, double cY, double majorR, double minorR, double from, double to, double step, float r, float g, float b, float a) {
        drawTriangleStrip(consumer -> drawAnnulusSector(poseStack, consumer, cX, cY, majorR, minorR, from, to, step, r, g, b, a));
    }

    public static void drawAnnulusSector(PoseStack poseStack, VertexConsumer consumer, double cX, double cY, double majorR, double minorR, double from, double to, double step, float r, float g, float b, float a) {
        to += step / 32;
        Matrix4f matrix = poseStack.last().pose();
        for (double i = from; i < to; i += step) {
            double sin = Math.sin(i);
            double cos = Math.cos(i);

            double x0 = majorR * cos + cX;
            double y0 = majorR * sin + cY;

            double x1 = minorR * cos + cX;
            double y1 = minorR * sin + cY;

            consumer.addVertex(matrix, (float)x0, (float)y0, 0).setColor(r, g, b, a);
            consumer.addVertex(matrix, (float)x1, (float)y1, 0).setColor(r, g, b, a);
        }
    }

    public static void drawRectangle(PoseStack poseStack, float x, float y, float width, float height, float borderRadius, float scale, float rotation, float step, float r, float g, float b, float a) {
        drawTriangleStrip(consumer -> drawRectangle(poseStack, consumer, x, y, width, height, borderRadius, scale, rotation, step, r, g, b, a));
    }

    public static void drawRectangle(PoseStack poseStack, VertexConsumer consumer, float x, float y, float width, float height, float borderRadius, float scale, float rotation, float step, float r, float g, float b, float a) {
        poseStack.pushPose();
        try {
            poseStack.translate(x, y, 0);
            poseStack.mulPose(Axis.ZP.rotation(rotation));
            poseStack.scale(scale, scale, 1);
            poseStack.translate(-x, -y, 0);
            Matrix4f matrix = poseStack.last().pose();

            drawQuadrant(matrix, consumer, x + width - borderRadius, y + height - borderRadius, borderRadius, 0, step, r, g, b, a);
            drawQuadrant(matrix, consumer, x + borderRadius, y + height - borderRadius, borderRadius, 1, step, r, g, b, a);
            drawQuadrant(matrix, consumer, x + borderRadius, y + borderRadius, borderRadius, 2, step, r, g, b, a);
            drawQuadrant(matrix, consumer, x + width - borderRadius, y + borderRadius, borderRadius, 3, step, r, g, b, a);

            consumer.addVertex(matrix, x + width, y + height - borderRadius, 0).setColor(r, g, b, a);
            consumer.addVertex(matrix, x + borderRadius, y + borderRadius, 0).setColor(r, g, b, a);
            consumer.addVertex(matrix, x + borderRadius, y + height - borderRadius, 0).setColor(r, g, b, a);
        } finally {
            poseStack.popPose();
        }
    }

    private static void drawQuadrant(Matrix4f matrix, VertexConsumer consumer, float cX, float cY, float radius, int index, float step, float r, float g, float b, float a) {
        float start = Radians.R_PI_2 * index;
        float end = Radians.R_PI_2 * (index + 1);
        for (float i = start; i < end; i += step) {
            float x = radius * (float)Math.cos(i) + cX;
            float y = radius * (float)Math.sin(i) + cY;
            consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
            consumer.addVertex(matrix, cX, cY, 0).setColor(r, g, b, a);
        }
        float x = radius * (float)Math.cos(end) + cX;
        float y = radius * (float)Math.sin(end) + cY;
        consumer.addVertex(matrix, x, y, 0).setColor(r, g, b, a);
        consumer.addVertex(matrix, cX, cY, 0).setColor(r, g, b, a);
    }

    public static void draw(Consumer<VertexConsumer> consumer, VertexFormat.Mode drawMode, VertexFormat format) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder bufferBuilder = Tesselator.getInstance().begin(drawMode, format);
        consumer.accept(bufferBuilder);
        MeshData mesh = bufferBuilder.build();
        if (mesh != null) {
            BufferUploader.drawWithShader(mesh);
        }

        RenderSystem.disableBlend();
    }

    public static Font getTextRenderer() {
        return Minecraft.getInstance().font;
    }

    public static MultiBufferSource.BufferSource getEntityVertexConsumerProvider() {
        return RENDER_BUFFERS.bufferSource();
    }

    public static void drawCenteredText(GuiGraphics guiGraphics, Component text, float cX, float cY, int color) {
        drawCenteredText(guiGraphics, text, cX, cY, 1F, color);
    }

    public static void drawCenteredText(GuiGraphics guiGraphics, Component text, float cX, float cY, float scale, int color) {
        drawCenteredText(guiGraphics, text, cX, cY, scale, color, false);
    }

    public static void drawCenteredText(GuiGraphics guiGraphics, Component text, float cX, float cY, float scale, int color, boolean shadow) {
        drawCenteredText(guiGraphics, text, getTextRenderer(), cX, cY, scale, color, shadow);
    }

    public static void drawCenteredText(GuiGraphics guiGraphics, Component text, Font font, float cX, float cY, float scale, int color, boolean shadow) {
        MultiBufferSource.BufferSource immediate = MultiBufferSource.immediate(IMMEDIATE_BYTE_BUFFER);
        drawCenteredText(guiGraphics, text, immediate, font, cX, cY, scale, color, shadow);
        immediate.endBatch();
    }

    public static void drawCenteredText(GuiGraphics guiGraphics, Component text, MultiBufferSource bufferSource, Font font, float cX, float cY, float scale, int color, boolean shadow) {
        float height = font.lineHeight * scale;
        float width = font.width(text) * scale;
        cX -= width / 2F;
        cY -= height / 2F;

        PoseStack poseStack = guiGraphics.pose();

        poseStack.pushPose();
        try {
            poseStack.translate(cX, cY, 0);
            poseStack.scale(scale, scale, 1F);
            poseStack.translate(-cX, -cY, 0);
            guiGraphics.drawString(font, text, (int) cX, (int) cY, color, shadow);
        } finally {
            poseStack.popPose();
        }
    }

    private RenderSystemUtil() {
    }
}
