package com.breakinblocks.neosync.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.common.utils.math.Radians;

import java.util.function.Function;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractShellContainerModel extends DoubleBlockModel {
    protected static final DoubleBlockHalf BOTTOM = DoubleBlockHalf.LOWER;
    protected static final DoubleBlockHalf TOP = DoubleBlockHalf.UPPER;

    public final ModelPart doorLL;
    public final ModelPart doorLU;
    public final ModelPart doorRL;
    public final ModelPart doorRU;

    public float doorOpenProgress;

    public AbstractShellContainerModel() {
        super(256, 256);
        ModelPart rightDoor = this.createRotationTemplate(0, Radians.R_PI, 0);

        this.doorLL = this.addCuboid(BOTTOM, 224, 32, -15, -8, -15, 15, 31, 1);
        this.doorLU = this.addCuboid(TOP, 224, 0, -15, -6, -15, 15, 30, 1);

        this.doorRL = this.addCuboid(BOTTOM, 224, 32, 15, -8, -14, 15, 31, 1, rightDoor);
        this.doorRU = this.addCuboid(TOP, 224, 0, 15, -6, -14, 15, 30, 1, rightDoor);
    }

    public AbstractShellContainerModel(Function<ResourceLocation, RenderType> layerFactory) {
        super(layerFactory, 256, 256);
        ModelPart rightDoor = this.createRotationTemplate(0, Radians.R_PI, 0);

        this.doorLL = this.addCuboid(BOTTOM, 224, 32, -15, -8, -15, 15, 31, 1);
        this.doorLU = this.addCuboid(TOP, 224, 0, -15, -6, -15, 15, 30, 1);

        this.doorRL = this.addCuboid(BOTTOM, 224, 32, 15, -8, -14, 15, 31, 1, rightDoor);
        this.doorRU = this.addCuboid(TOP, 224, 0, 15, -6, -14, 15, 30, 1, rightDoor);
    }

    @Override
    public void render(DoubleBlockHalf type, PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        this.doorLL.yRot = this.doorLU.yRot = Radians.R_PI_2 * this.doorOpenProgress;
        this.doorRL.yRot = this.doorRU.yRot = Radians.R_PI - Radians.R_PI_2 * this.doorOpenProgress;

        this.doorLL.z = this.doorLU.z = -15 + 15 * this.doorOpenProgress;
        this.doorRL.z = this.doorRU.z = -14 + 14 * this.doorOpenProgress;
        this.doorRL.x = this.doorRU.x = 15 - this.doorOpenProgress;

        super.render(type, matrices, vertices, light, overlay, color);
    }

    @Override
    protected void translate(PoseStack matrices) {
        matrices.translate(0, -2F, 0);
    }
}
