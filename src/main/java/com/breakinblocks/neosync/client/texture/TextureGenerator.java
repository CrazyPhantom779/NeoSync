package com.breakinblocks.neosync.client.texture;

import net.minecraft.client.renderer.texture.AbstractTexture;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
@FunctionalInterface
public interface TextureGenerator {
    Stream<AbstractTexture> generateTextures();
}
