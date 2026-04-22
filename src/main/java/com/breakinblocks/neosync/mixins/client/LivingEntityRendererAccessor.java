package com.breakinblocks.neosync.mixins.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@OnlyIn(Dist.CLIENT)
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAccessor<T extends LivingEntity, M extends EntityModel<T>> {
    @Invoker("addLayer")
    boolean executeAddFeature(RenderLayer<T, M> feature);
}