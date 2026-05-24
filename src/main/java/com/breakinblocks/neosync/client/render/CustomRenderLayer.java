package com.breakinblocks.neosync.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.BiFunction;

@OnlyIn(Dist.CLIENT)
public final class CustomRenderLayer extends RenderType {
    private static final RenderType VOXELS;
    private static final BiFunction<ResourceLocation, Boolean, RenderType> ENTITY_TRANSLUCENT_PARTIALLY_TEXTURED;

    private CustomRenderLayer(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize, boolean affectsCrumbling, boolean sortOnUpload, Runnable setupState, Runnable clearState) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setupState, clearState);
    }

    public static RenderType getVoxels() {
        return VOXELS;
    }

    public static RenderType getEntityTranslucentPartiallyTextured(ResourceLocation textureId, float cutoutY) {
        return getEntityTranslucentPartiallyTextured(textureId, cutoutY, true);
    }

    public static RenderType getEntityTranslucentPartiallyTextured(ResourceLocation textureId, float cutoutY, boolean affectsOutline) {
        CustomGameRenderer.initRenderTypeEntityTranslucentPartiallyTexturedShader(cutoutY, MatrixStackStorage.getModelMatrixStack().last().pose());
        return ENTITY_TRANSLUCENT_PARTIALLY_TEXTURED.apply(textureId, affectsOutline);
    }

    static {
        VOXELS = create("voxels",
                CustomVertexFormats.POSITION_COLOR_OVERLAY_LIGHT_NORMAL,
                VertexFormat.Mode.QUADS,
                256,
                false,
                false,
                CompositeState.builder()
                        .setShaderState(new ShaderStateShard(CustomGameRenderer::getRenderTypeVoxelShader))
                        .setTransparencyState(NO_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .createCompositeState(true)
        );

        ENTITY_TRANSLUCENT_PARTIALLY_TEXTURED = Util.memoize((id, outline) -> {
            CompositeState state = CompositeState.builder()
                    .setShaderState(new ShaderStateShard(CustomGameRenderer::getRenderTypeEntityTranslucentPartiallyTexturedShader))
                    .setTextureState(new TextureStateShard(id, false, false))
                    .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                    .setCullState(CULL)
                    .setLightmapState(LIGHTMAP)
                    .setOverlayState(OVERLAY)
                    .setWriteMaskState(COLOR_DEPTH_WRITE)
                    .createCompositeState(outline);

            return create("entity_translucent_partially_textured",
                    DefaultVertexFormat.NEW_ENTITY,
                    VertexFormat.Mode.QUADS,
                    256,
                    true,
                    true,
                    state
            );
        });
    }
}
