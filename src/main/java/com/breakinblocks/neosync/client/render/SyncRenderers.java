package com.breakinblocks.neosync.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderers;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import com.breakinblocks.neosync.client.render.block.entity.ShellConstructorBlockEntityRenderer;
import com.breakinblocks.neosync.client.render.block.entity.ShellStorageBlockEntityRenderer;
import com.breakinblocks.neosync.client.render.block.entity.TreadmillBlockEntityRenderer;
import com.breakinblocks.neosync.common.block.entity.SyncBlockEntities;

@OnlyIn(Dist.CLIENT)
public final class SyncRenderers {
    public static void initClient() {
        register(ShellStorageBlockEntityRenderer::new, SyncBlockEntities.SHELL_STORAGE.get());
        register(ShellConstructorBlockEntityRenderer::new, SyncBlockEntities.SHELL_CONSTRUCTOR.get());
        register(TreadmillBlockEntityRenderer::new, SyncBlockEntities.TREADMILL.get());
    }

    private static <E extends BlockEntity> void register(BlockEntityRendererProvider<E> rendererFactory, BlockEntityType<E> blockEntityType) {
        BlockEntityRenderers.register(blockEntityType, rendererFactory);
    }

    /**
     * Creates an IClientItemExtensions instance for blocks that need custom item rendering.
     */
    public static IClientItemExtensions createItemRenderer(BlockEntityType<?> blockEntityType, Block block) {
        return new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    renderer = new SyncBlockEntityItemRenderer(blockEntityType, block);
                }
                return renderer;
            }
        };
    }

    /**
     * Custom item renderer for block entities
     */
    @OnlyIn(Dist.CLIENT)
    private static class SyncBlockEntityItemRenderer extends BlockEntityWithoutLevelRenderer {
        private final BlockEntityType<?> blockEntityType;
        private final Block block;
        private BlockEntity renderEntity;

        public SyncBlockEntityItemRenderer(BlockEntityType<?> blockEntityType, Block block) {
            super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                    Minecraft.getInstance().getEntityModels());
            this.blockEntityType = blockEntityType;
            this.block = block;
        }

        @Override
        public void renderByItem(ItemStack stack, ItemDisplayContext displayContext, PoseStack poseStack, MultiBufferSource buffer, int packedLight, int packedOverlay) {
            if (renderEntity == null) {
                renderEntity = blockEntityType.create(BlockPos.ZERO, block.defaultBlockState());
            }

            if (renderEntity != null) {
                Minecraft.getInstance().getBlockEntityRenderDispatcher().renderItem(renderEntity, poseStack, buffer, packedLight, packedOverlay);
            }
        }
    }
}