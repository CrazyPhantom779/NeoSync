package com.breakinblocks.neosync.client.gui.widget;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;
import net.minecraft.util.Mth;
import com.mojang.math.Axis;
import com.breakinblocks.neosync.client.utils.render.ColorUtil;
import com.breakinblocks.neosync.client.utils.render.RenderSystemUtil;
import com.breakinblocks.neosync.common.utils.math.QuarticFunction;
import com.breakinblocks.neosync.common.utils.math.Radians;

@OnlyIn(Dist.CLIENT)
public class ArrowButtonWidget extends Button {
    private static final Component DEFAULT_DESCRIPTION = null;
    private static final int DEFAULT_COLOR = ColorUtil.fromDyeColor(DyeColor.WHITE);
    private static final float DEFAULT_STEP = Radians.R_PI_32;

    public final ArrowType type;
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

    public ArrowButtonWidget(float x, float y, float width, float height, ArrowType type, float thickness, Runnable onClick) {
        this(x, y, width, height, type, thickness, DEFAULT_STEP, DEFAULT_COLOR, DEFAULT_DESCRIPTION, onClick);
    }

    public ArrowButtonWidget(float x, float y, float width, float height, ArrowType type, float thickness, Component description, Runnable onClick) {
        this(x, y, width, height, type, thickness, DEFAULT_STEP, DEFAULT_COLOR, description, onClick);
    }

    public ArrowButtonWidget(float x, float y, float width, float height, ArrowType type, float thickness, int color, Runnable onClick) {
        this(x, y, width, height, type, thickness, DEFAULT_STEP, color, DEFAULT_DESCRIPTION, onClick);
    }

    public ArrowButtonWidget(float x, float y, float width, float height, ArrowType type, float thickness, int color, Component description, Runnable onClick) {
        this(x, y, width, height, type, thickness, DEFAULT_STEP, color, description, onClick);
    }

    public ArrowButtonWidget(float x, float y, float width, float height, ArrowType type, float thickness, float step, int color, Component description, Runnable onClick) {
        super((int) x, (int) y, (int) (type.isVertical() ? width : height), (int) (type.isVertical() ? height : width), description, button -> onClick.run(), Button.DEFAULT_NARRATION);
        this.type = type;
        this.step = step;
        this.color = ColorUtil.toRGBA(color);
        this.description = description;

        float shiftY = (float)new QuarticFunction(1, -2 * height, height * height + width * width / 4, 0, -width * width / 4 * thickness * thickness).getRoot(1);
        this.angle = (float)Math.acos(shiftY / thickness);

        this.x0 = x;
        this.y0 = y + height - shiftY;
        this.x1 = x + width / 2;
        this.y1 = y;

        this.borderRadius = thickness * 0.5F;
        this.stickHeight = thickness;
        this.stickWidth = Mth.sqrt(Mth.square(width / 2) + Mth.square(height - shiftY));
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        guiGraphics.pose().pushPose();
        this.type.transform(guiGraphics.pose(), this.getX(), this.getY(), this.width, this.height);
        RenderSystemUtil.drawRectangle(guiGraphics.pose(), this.x0, this.y0, this.stickWidth, this.stickHeight, this.borderRadius, 1F, -this.angle, this.step, color[0], color[1], color[2], color[3]);
        RenderSystemUtil.drawRectangle(guiGraphics.pose(), this.x1, this.y1, this.stickWidth, this.stickHeight, this.borderRadius, 1F, this.angle, this.step, color[0], color[1], color[2], color[3]);
        guiGraphics.pose().popPose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.visible && this.type.isValidKey(keyCode)) {
            this.onPress();
            return true;
        }
        return false;
    }

    public enum ArrowType {
        UP(0, 265, 266, 87), // KP_UP, PAGE_UP, W
        RIGHT(1, 262, 68), // KP_RIGHT, D
        DOWN(2, 264, 267, 83), // KP_DOWN, PAGE_DOWN, S
        LEFT(3, 263, 65); // KP_LEFT, A

        private final int i;
        private final int[] keyCodes;

        ArrowType(int i, int... keyCodes) {
            this.i = i;
            this.keyCodes = keyCodes;
        }

        public boolean isValidKey(int keyCode) {
            for (int code : this.keyCodes) {
                if (code == keyCode) {
                    return true;
                }
            }

            return false;
        }

        public boolean isUp() {
            return this == UP;
        }

        public boolean isRight() {
            return this == RIGHT;
        }

        public boolean isLeft() {
            return this == LEFT;
        }

        public boolean isDown() {
            return this == DOWN;
        }

        public boolean isVertical() {
            return this == UP || this == DOWN;
        }

        public boolean isHorizontal() {
            return this == LEFT || this == RIGHT;
        }

        public void transform(PoseStack matrices, float x, float y, float width, float height) {
            if (this.isHorizontal()) {
                float tmp = width;
                //noinspection SuspiciousNameCombination
                width = height;
                height = tmp;
            }
            matrices.translate(x, y, 0);
            matrices.mulPose(Axis.ZP.rotation(Radians.R_PI_2 * this.i));
            matrices.translate(-x - (this.i == 2 || this.i == 3 ? width : 0), -y - ((this.i == 1 || this.i == 2 ? height : 0)), 0);
        }
    }
}
