package com.breakinblocks.neosync.mixins.client;

import com.google.common.collect.ImmutableMap;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.client.render.entity.ShellEntityRenderer;
import com.breakinblocks.neosync.common.block.entity.ShellEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@OnlyIn(Dist.CLIENT)
@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {
    @Shadow
    @Final
    private ItemRenderer itemRenderer;

    @Shadow
    @Final
    private Font font;

    @Shadow
    @Final
    private EntityModelSet entityModels;

    @Shadow
    @Final
    private BlockRenderDispatcher blockRenderDispatcher;

    @Unique
    private Map<String, EntityRenderer<? extends Player>> sync$shellRenderers = ImmutableMap.of();

    @SuppressWarnings("unchecked")
    @Inject(method = "getRenderer", at = @At("HEAD"), cancellable = true)
    private <T extends Entity> void sync$getRenderer(T entity, CallbackInfoReturnable<EntityRenderer<? super T>> cir) {
        if (entity instanceof ShellEntity shell) {
            PlayerInfo playerInfo = shell.getPlayerInfo();
            String modelType = (playerInfo != null && playerInfo.getSkin().model() == net.minecraft.client.resources.PlayerSkin.Model.SLIM) ? "slim" : "default";
            EntityRenderer<? extends Player> renderer = this.sync$shellRenderers.get(modelType);
            if (renderer != null) {
                cir.setReturnValue((EntityRenderer<? super T>) renderer);
            }
        }
    }

    @Inject(method = "onResourceManagerReload", at = @At("HEAD"))
    private void sync$reload(ResourceManager manager, CallbackInfo ci) {
        EntityRenderDispatcher dispatcher = (EntityRenderDispatcher)(Object)this;
        EntityRendererProvider.Context context = new EntityRendererProvider.Context(
                dispatcher,
                this.itemRenderer,
                this.blockRenderDispatcher,
                dispatcher.getItemInHandRenderer(),
                manager,
                this.entityModels,
                this.font
        );

        this.sync$shellRenderers = ImmutableMap.of(
                "default", createShellEntityRenderer(context, false),
                "slim", createShellEntityRenderer(context, true)
        );
    }

    @Unique
    private static ShellEntityRenderer createShellEntityRenderer(EntityRendererProvider.Context context, boolean slim) {
        ShellEntityRenderer shellEntityRenderer = new ShellEntityRenderer(context, slim);
        return shellEntityRenderer;
    }
}