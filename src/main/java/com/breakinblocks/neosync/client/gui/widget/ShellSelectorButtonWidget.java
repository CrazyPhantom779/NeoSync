package com.breakinblocks.neosync.client.gui.widget;

import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.breakinblocks.neosync.api.shell.ClientShell;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.client.entity.ClientShellEntities;
import com.breakinblocks.neosync.client.entity.ShellEntity;
import com.breakinblocks.neosync.client.gl.MSAAFramebuffer;
import com.breakinblocks.neosync.client.render.MatrixStackStorage;
import com.breakinblocks.neosync.client.utils.render.ColorUtil;
import com.breakinblocks.neosync.client.utils.render.RenderSystemUtil;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.breakinblocks.neosync.common.utils.math.Radians;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector3f;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;
import java.util.function.BiPredicate;

@OnlyIn(Dist.CLIENT)
public class ShellSelectorButtonWidget extends AbstractWidget {
    private static final DyeColor DEFAULT_COLOR = DyeColor.BLACK;
    private static final float DEFAULT_ALPHA = 0.6F;
    private static final float DEFAULT_HOVERED_ALPHA = 0.8F;
    private static final float DEFAULT_PRESSED_ALPHA = 1F;
    private static final double DEFAULT_STEP = Radians.R_PI_128;

    private final double cX;
    private final double cY;
    private final double majorR;
    private final double minorR;
    private final double diffR;
    private final double minorRBorder;
    private final double from;
    private final double to;
    private final double step;
    private final DyeColor color;
    private final float alpha;
    private final float hoveredAlpha;
    private final float pressedAlpha;
    private final BiPredicate<Double, Double> belongsToSectorPredicate;

    @Nullable
    public BlockPos currentContainerPos;
    public ShellState shell;

    public ShellSelectorButtonWidget(double cX, double cY, double majorR, double minorR, double borderWidth, double from, double to) {
        this(cX, cY, majorR, minorR, borderWidth, from, to, DEFAULT_STEP, DEFAULT_COLOR, DEFAULT_ALPHA, DEFAULT_HOVERED_ALPHA, DEFAULT_PRESSED_ALPHA);
    }

    public ShellSelectorButtonWidget(double cX, double cY, double majorR, double minorR, double borderWidth, double from, double to, DyeColor color) {
        this(cX, cY, majorR, minorR, borderWidth, from, to, DEFAULT_STEP, color, DEFAULT_ALPHA, DEFAULT_HOVERED_ALPHA, DEFAULT_PRESSED_ALPHA);
    }

    public ShellSelectorButtonWidget(double cX, double cY, double majorR, double minorR, double borderWidth, double from, double to, DyeColor color, float alpha, float hoveredAlpha, float pressedAlpha) {
        this(cX, cY, majorR, minorR, borderWidth, from, to, DEFAULT_STEP, color, alpha, hoveredAlpha, pressedAlpha);
    }

    public ShellSelectorButtonWidget(double cX, double cY, double majorR, double minorR, double borderWidth, double from, double to, double step, DyeColor color, float alpha, float hoveredAlpha, float pressedAlpha) {
        boolean isFullCircle = Math.abs(Radians.R_2_PI - to + from) < step;

        if (isFullCircle) {
            double tmp = from;
            from = -to;
            to = -tmp;
        }

        this.cX = cX;
        this.cY = cY;
        this.majorR = majorR;
        this.minorR = minorR;
        this.diffR = majorR - minorR;
        this.minorRBorder = majorR - borderWidth;
        this.from = from;
        this.to = to;
        this.step = step;
        this.color = color;
        this.alpha = alpha;
        this.hoveredAlpha = hoveredAlpha;
        this.pressedAlpha = pressedAlpha;

        double x0 = majorR * Math.cos(from) + cX;
        double y0 = majorR * Math.sin(from) + cY;
        double x1 = majorR * Math.cos(to) + cX;
        double y1 = majorR * Math.sin(to) + cY;
        double a0 = (y0 - cY) / (x0 - cX);
        double b0 = -a0 * cX + cY;
        double a1 = (y1 - cY) / (x1 - cX);
        double b1 = -a1 * cX + cY;

        if (isFullCircle) {
            this.belongsToSectorPredicate = (x, y) -> true;
        } else if (from < Radians.R_PI_2 && to > Radians.R_PI_2) {
            this.belongsToSectorPredicate = (x, y) -> liesOnOrAboveLine(x, y, a0, b0) && liesOnOrAboveLine(x, y, a1, b1);
        } else if (from < Radians.R_3_PI_2 && to > Radians.R_3_PI_2) {
            this.belongsToSectorPredicate = (x, y) -> liesOnOrBelowLine(x, y, a0, b0) && liesOnOrBelowLine(x, y, a1, b1);
        } else if (y1 > y0) {
            this.belongsToSectorPredicate = (x, y) -> liesOnOrAboveLine(x, y, a0, b0) && liesOnOrBelowLine(x, y, a1, b1);
        } else {
            this.belongsToSectorPredicate = (x, y) -> liesOnOrBelowLine(x, y, a0, b0) && liesOnOrAboveLine(x, y, a1, b1);
        }
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return this.shell != null
                && liesOnCircle(mouseX, mouseY, this.cX, this.cY, this.majorR)
                && !liesOnCircle(mouseX, mouseY, this.cX, this.cY, this.minorR)
                && this.belongsToSectorPredicate.test(mouseX, mouseY);
    }

    @Override
    public void setFocused(boolean focused) {
    }

    @Override
    public boolean isFocused() {
        return false;
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderSector(guiGraphics.pose());

        if (this.shell != null) {
            if (this.shell.getProgress() < ShellState.PROGRESS_PRINTING) {
                this.renderShellAndProgress(guiGraphics);
            } else {
                MSAAFramebuffer.renderAfterUsage(() -> this.renderShellAndProgress(guiGraphics));
            }
        }
    }

    @Override
    protected void onMouseClick(double mouseX, double mouseY, int button) {
        Minecraft client = Minecraft.getInstance();

        if (client.player == null || this.shell == null || this.shell.getProgress() < ShellState.PROGRESS_DONE) {
            return;
        }

        NeoSyncDebug.info("selector-button", "clicked shell={} progress={} currentContainer={}", this.shell.getUuid(), this.shell.getProgress(), this.currentContainerPos);
        PlayerSyncEvents.SyncFailureReason failureReason = ((ClientShell) client.player).beginSync(this.shell, this.currentContainerPos);

        if (failureReason != null) {
            NeoSyncDebug.warn("selector-button", "beginSync failed shell={} reason={}", this.shell.getUuid(), failureReason.toText().getString());

            if (client.screen != null) {
                client.screen.onClose();
            }

            client.player.displayClientMessage(failureReason.toText(), true);
        }
    }

    private void renderSector(PoseStack matrices) {
        float alpha = this.isPressed() ? this.pressedAlpha : this.isHovered() ? this.hoveredAlpha : this.alpha;
        DyeColor borderColor = this.shell == null ? null : this.shell.getColor();
        DyeColor color = borderColor != null && (this.isHovered() || this.isPressed()) ? borderColor : this.color;
        float[] rgb = ColorUtil.toFloatRgb(color);
        RenderSystemUtil.drawAnnulusSector(matrices, this.cX, this.cY, this.majorR, this.minorR, this.from, this.to, this.step, rgb[0], rgb[1], rgb[2], alpha);

        if (borderColor != null) {
            rgb = ColorUtil.toFloatRgb(borderColor);
            RenderSystemUtil.drawAnnulusSector(matrices, this.cX, this.cY, this.majorR, this.minorRBorder, this.from, this.to, this.step, rgb[0], rgb[1], rgb[2], 1F);
        }
    }

    private void renderShellAndProgress(GuiGraphics guiGraphics) {
        this.renderShell(guiGraphics);

        if (this.shell.getProgress() < ShellState.PROGRESS_DONE) {
            this.renderProgress(guiGraphics);
        }
    }

    private void renderShell(GuiGraphics guiGraphics) {
        final double SHELL_WIDTH_HALF = 0.26F;
        final double SHELL_HEIGHT_HALF = 0.35F;
        final float SHELL_SCALE = 0.365F;

        ShellEntity shellEntity = ClientShellEntities.get(this.shell);
        shellEntity.refreshFromState();
        shellEntity.pitchProgress = 0;
        shellEntity.isActive = this.shell.getProgress() >= ShellState.PROGRESS_DONE;

        double r = this.minorR + this.diffR / 2;
        double tAngle = (this.to + this.from) / 2;
        double shellCX = r * Math.cos(tAngle) + this.cX;
        double shellCY = r * Math.sin(tAngle) + this.cY;
        double tX = shellCX - this.diffR * SHELL_WIDTH_HALF;
        double tY = shellCY + this.diffR * SHELL_HEIGHT_HALF;
        float scale = (float) this.diffR * SHELL_SCALE;
        PoseStack matrices = guiGraphics.pose();
        matrices.pushPose();

        try {
            matrices.translate(tX, tY, this.majorR);
            matrices.scale(scale, -scale, 1F);
            matrices.mulPose(Axis.XP.rotationDegrees(15));
            matrices.mulPose(Axis.YP.rotationDegrees(40));
            RenderSystem.setupGui3DDiffuseLighting(new Vector3f((float) this.cX * 2, (float) this.cY * 2, -1), new Vector3f(0, 0, 1));
            MatrixStackStorage.saveModelMatrixStack(matrices);
            MultiBufferSource.BufferSource immediate = RenderSystemUtil.getEntityVertexConsumerProvider();
            EntityRenderDispatcher renderDispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
            renderDispatcher.render(shellEntity, 0, 0, 0, 0, 0, matrices, immediate, RenderSystemUtil.MAX_LIGHT_LEVEL);
            immediate.endBatch();
        } finally {
            matrices.popPose();
        }
    }

    private void renderProgress(GuiGraphics guiGraphics) {
        final float FONT_SCALE = 0.15F;
        final float BOX_SCALE = 1.6F;
        Font font = RenderSystemUtil.getTextRenderer();
        double r = this.minorR + this.diffR / 2;
        double tAngle = (this.to + this.from) / 2;
        float shellCX = (float) (r * Math.cos(tAngle) + this.cX);
        float shellCY = (float) (r * Math.sin(tAngle) + this.cY);
        float fontHeight = (float) this.diffR * FONT_SCALE;
        float fontScale = fontHeight / font.lineHeight;
        int progress = (int) Math.floor(this.shell.getProgress() * 100);
        Component progressText = Component.translatable("gui.neosync.shell_selector.progress_percent", progress);
        float boxHeight = fontHeight * BOX_SCALE;
        float progressTextWidth = font.width(progressText) * fontScale;
        float progressBoxWidth = Math.max(boxHeight * 2F, progressTextWidth);
        float boxTop = shellCY - boxHeight / 2 - font.lineHeight * fontScale * 0.125F;
        float boxLeft = shellCX - progressBoxWidth / 2F;
        PoseStack matrices = guiGraphics.pose();
        matrices.pushPose();

        try {
            matrices.translate(0, 0, this.majorR * 2);
            RenderSystemUtil.drawRectangle(matrices, boxLeft, boxTop, progressBoxWidth, boxHeight, boxHeight * 0.25F, 1F, 0, (float) this.step, 0F, 0F, 0F, 0.8F);
            RenderSystemUtil.drawCenteredText(guiGraphics, progressText, shellCX, shellCY, fontScale, ColorUtil.fromDyeColor(DyeColor.WHITE), true);
        } finally {
            matrices.popPose();
        }
    }

    @Override
    public List children() {
        return Collections.emptyList();
    }

    @Override
    protected Component getWidgetDescription() {
        if (this.shell == null) {
            return null;
        }

        Minecraft client = Minecraft.getInstance();
        BlockPos pos = client.level == null ? this.shell.getPos() : NeoSyncSableCompat.projectOut(client.level, this.shell.getPos());
        return Component.translatable("gui.neosync.shell_selector.position", pos.getX(), pos.getY(), pos.getZ());
    }

    private static boolean liesOnCircle(double x, double y, double cX, double cY, double r) {
        return Mth.square(cX - x) + Mth.square(cY - y) <= r * r;
    }

    private static boolean liesOnOrAboveLine(double x, double y, double a, double b) {
        return y >= a * x + b;
    }

    private static boolean liesOnOrBelowLine(double x, double y, double a, double b) {
        return y <= a * x + b;
    }
}
