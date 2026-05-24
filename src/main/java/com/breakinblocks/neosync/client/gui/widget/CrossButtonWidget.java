package com.breakinblocks.neosync.client.gui.widget;

import net.minecraft.client.gui.components.events.GuiEventListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.util.Mth;
import com.breakinblocks.neosync.client.utils.render.ColorUtil;
import com.breakinblocks.neosync.client.utils.render.RenderSystemUtil;
import com.breakinblocks.neosync.common.utils.math.QuarticFunction;
import com.breakinblocks.neosync.common.utils.math.Radians;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.List;


@OnlyIn(Dist.CLIENT)
public class CrossButtonWidget extends AbstractButtonWidget {
    private static final Component DEFAULT_DESCRIPTION = Component.translatable("gui.neosync.default.cross_button.title");
    private static final int DEFAULT_COLOR = ColorUtil.fromDyeColor(DyeColor.WHITE);
    private static final float DEFAULT_STEP = Radians.R_PI_32;

    private final float step;
    private final float[] color;
    private final Component description;
    private final float x0;
    private final float y0;
    private final float x1;
    private final float y1;
    private final float borderRadius;
    private final float stickWidth;
    private final float stickHeight;
    private final float angle;

    public CrossButtonWidget(float x, float y, float width, float height, float thickness, Runnable onClick) {
        this(x, y, width, height, thickness, DEFAULT_STEP, DEFAULT_COLOR, DEFAULT_DESCRIPTION, onClick);
    }

    public CrossButtonWidget(float x, float y, float width, float height, float thickness, int color, Runnable onClick) {
        this(x, y, width, height, thickness, DEFAULT_STEP, color, DEFAULT_DESCRIPTION, onClick);
    }

    public CrossButtonWidget(float x, float y, float width, float height, float thickness, Component description, Runnable onClick) {
        this(x, y, width, height, thickness, DEFAULT_STEP, DEFAULT_COLOR, description, onClick);
    }

    public CrossButtonWidget(float x, float y, float width, float height, float thickness, int color, Component description, Runnable onClick) {
        this(x, y, width, height, thickness, DEFAULT_STEP, color, description, onClick);
    }

    public CrossButtonWidget(float x, float y, float width, float height, float thickness, float step, int color, Component description, Runnable onClick) {
        super(x, y, width, height, onClick);
        this.step = step;
        this.color = ColorUtil.toRGBA(color);
        this.description = description;

        float shiftY = (float)new QuarticFunction(4, -4 * height, height * height + width * width - 4 * thickness * thickness, 2 * height * thickness * thickness, thickness * thickness * (thickness * thickness - width * width)).getRoot(1);
        float shiftX = (float)Math.sqrt(thickness * thickness - shiftY * shiftY);
        this.angle = (float)Math.acos(shiftY / thickness);
        this.x0 = x;
        this.y0 = y + height - shiftY;
        this.x1 = x + shiftX;
        this.y1 = y;
        this.borderRadius = thickness * 0.5F;
        this.stickHeight = thickness;
        this.stickWidth = Mth.sqrt(Mth.square(width - shiftX) + Mth.square(height - shiftY));
    }

    @Override
    protected void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        RenderSystemUtil.drawRectangle(guiGraphics.pose(), this.x0, this.y0, this.stickWidth, this.stickHeight, this.borderRadius, 1, -this.angle, this.step, this.color[0], this.color[1], this.color[2], this.color[3]);
        RenderSystemUtil.drawRectangle(guiGraphics.pose(), this.x1, this.y1, this.stickWidth, this.stickHeight, this.borderRadius, 1, this.angle, this.step, this.color[0], this.color[1], this.color[2], this.color[3]);
    }

    @Override
    public List<? extends GuiEventListener> children() {
        return Collections.emptyList();
    }

    @Override
    protected Component getWidgetDescription() {
        return this.description;
    }

    private boolean focused;

    @Override
    public void setFocused(boolean focused) {
        this.focused = focused;
    }

    @Override
    public boolean isFocused() {
        return focused;
    }
}
