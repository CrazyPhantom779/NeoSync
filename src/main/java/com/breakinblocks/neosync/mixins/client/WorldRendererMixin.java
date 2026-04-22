package com.breakinblocks.neosync.mixins.client;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@OnlyIn(Dist.CLIENT)
@Mixin(value = LevelRenderer.class, priority = 1010)
abstract class WorldRendererMixin {
    @Shadow @Final
    private Minecraft minecraft;

    /**
     * Forces the renderer to render the player when they aren't the camera entity (so shell
     * sync camera transitions can still see the player body).
     */
    @Redirect(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Camera;getEntity()Lnet/minecraft/world/entity/Entity;", ordinal = 3), require = 0)
    private Entity getFocusedEntity(Camera camera) {
        LocalPlayer player = this.minecraft.player;
        if (player != null && player != this.minecraft.getCameraEntity() && !player.isSpectator()) {
            return player;
        }
        return camera.getEntity();
    }
}