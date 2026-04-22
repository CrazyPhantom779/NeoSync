package com.breakinblocks.neosync.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.breakinblocks.neosync.NeoSync;
import org.joml.Matrix4f;

import javax.annotation.Nullable;
import java.io.IOException;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = NeoSync.MOD_ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class CustomGameRenderer {
    @Nullable
    private static ShaderInstance renderTypeEntityTranslucentPartiallyTexturedShader;
    @Nullable
    private static ShaderInstance renderTypeVoxelShader;

    private static float cachedCutoutY = 0.0f;
    private static final Matrix4f cachedModelMatrix = new Matrix4f();

    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent event) throws IOException {
        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(NeoSync.MOD_ID, "rendertype_entity_translucent_partially_textured"),
                        DefaultVertexFormat.NEW_ENTITY
                ),
                shader -> renderTypeEntityTranslucentPartiallyTexturedShader = shader
        );

        event.registerShader(new ShaderInstance(event.getResourceProvider(), ResourceLocation.fromNamespaceAndPath(NeoSync.MOD_ID, "rendertype_voxel"),
                        CustomVertexFormats.POSITION_COLOR_OVERLAY_LIGHT_NORMAL
                ),
                shader -> renderTypeVoxelShader = shader
        );
    }

    public static void initRenderTypeEntityTranslucentPartiallyTexturedShader(float cutoutY, Matrix4f modelMatrix) {
        cachedCutoutY = cutoutY;
        cachedModelMatrix.set(modelMatrix);

        if (renderTypeEntityTranslucentPartiallyTexturedShader != null) {
            var cutoutUniform = renderTypeEntityTranslucentPartiallyTexturedShader.getUniform("CutoutY");
            if (cutoutUniform != null) {
                cutoutUniform.set(cachedCutoutY);
            }

            var modelMatUniform = renderTypeEntityTranslucentPartiallyTexturedShader.getUniform("ModelMat");
            if (modelMatUniform != null) {
                modelMatUniform.set(cachedModelMatrix);
            }
        }
    }

    @Nullable
    public static ShaderInstance getRenderTypeEntityTranslucentPartiallyTexturedShader() {
        return renderTypeEntityTranslucentPartiallyTexturedShader;
    }

    @Nullable
    public static ShaderInstance getRenderTypeVoxelShader() {
        return renderTypeVoxelShader;
    }

    public static void initClient() {
    }
}