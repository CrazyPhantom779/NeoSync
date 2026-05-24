package com.breakinblocks.neosync.client.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.FastColor;
import net.minecraft.util.Tuple;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
public final class TextureGenerators {
    public static final TextureGenerator PlayerEntityPartiallyTexturedTextureGenerator = new PlayerEntityPartiallyTexturedTextureGenerator();

    private static class PlayerEntityPartiallyTexturedTextureGenerator implements TextureGenerator {
        private static final int TEXTURE_SIZE = 64;
        private static final int BLOCKS = 32;
        private static final int TRANSPARENT = FastColor.ABGR32.color(0, 0, 0, 0);
        private static final int WHITE = FastColor.ABGR32.color(255, 255, 255, 255);

        @SuppressWarnings("unchecked")
        private static final Tuple<Tuple<Integer, Integer>, Tuple<Tuple<Integer, Integer>, Tuple<Integer, Integer>>>[] REGIONS = new Tuple[]
                {
                        // top
                        new Tuple<>(new Tuple<>(0, 0), new Tuple<>(new Tuple<>(8, 0), new Tuple<>(8, 8))),

                        // face
                        new Tuple<>(new Tuple<>(1, 8), new Tuple<>(new Tuple<>(0, 8), new Tuple<>(32, 8))),

                        // neck
                        new Tuple<>(new Tuple<>(9, 9), new Tuple<>(new Tuple<>(16, 0), new Tuple<>(8, 8))),
                        new Tuple<>(new Tuple<>(9, 9), new Tuple<>(new Tuple<>(20, 16), new Tuple<>(8, 4))),

                        // shoulders
                        new Tuple<>(new Tuple<>(9, 9), new Tuple<>(new Tuple<>(44, 16), new Tuple<>(4, 4))),
                        new Tuple<>(new Tuple<>(9, 9), new Tuple<>(new Tuple<>(36, 48), new Tuple<>(4, 4))),

                        // body
                        new Tuple<>(new Tuple<>(9, 20), new Tuple<>(new Tuple<>(16, 20), new Tuple<>(24, 12))),

                        // arms
                        new Tuple<>(new Tuple<>(9, 20), new Tuple<>(new Tuple<>(40, 20), new Tuple<>(16, 12))),
                        new Tuple<>(new Tuple<>(9, 20), new Tuple<>(new Tuple<>(32, 52), new Tuple<>(16, 12))),

                        // body bottom
                        new Tuple<>(new Tuple<>(21, 21), new Tuple<>(new Tuple<>(28, 16), new Tuple<>(8, 4))),

                        // fists
                        new Tuple<>(new Tuple<>(21, 21), new Tuple<>(new Tuple<>(48, 16), new Tuple<>(4, 4))),
                        new Tuple<>(new Tuple<>(21, 21), new Tuple<>(new Tuple<>(40, 48), new Tuple<>(4, 4))),

                        // legs' top
                        new Tuple<>(new Tuple<>(21, 21), new Tuple<>(new Tuple<>(4, 16), new Tuple<>(4, 4))),
                        new Tuple<>(new Tuple<>(21, 21), new Tuple<>(new Tuple<>(20, 48), new Tuple<>(4, 4))),

                        // legs
                        new Tuple<>(new Tuple<>(21, 32), new Tuple<>(new Tuple<>(0, 20), new Tuple<>(16, 12))),
                        new Tuple<>(new Tuple<>(21, 32), new Tuple<>(new Tuple<>(16, 52), new Tuple<>(16, 12))),
                };

        private final int multiplier;

        public PlayerEntityPartiallyTexturedTextureGenerator() {
            this(1);
        }

        public PlayerEntityPartiallyTexturedTextureGenerator(int multiplier) {
            this.multiplier = multiplier;
        }

        @Override
        public Stream<AbstractTexture> generateTextures() {
            int textureSize = TEXTURE_SIZE * multiplier;
            int textureCount = 32 * multiplier;

            List<AbstractTexture> textures = new ArrayList<>(textureCount + 1);
            for (int i = 0; i <= textureCount; ++i) {
                textures.add(generateTexture(textureSize, i / (float)textureCount));
            }

            return textures.stream();
        }

        private static AbstractTexture generateTexture(int textureSize, float emptiness) {
            NativeImage img = new NativeImage(textureSize, textureSize, false);
            int multiplier = textureSize / TEXTURE_SIZE;
            float lastBlock = BLOCKS * multiplier * emptiness;
            img.fillRect(0, 0, textureSize, textureSize, TRANSPARENT);

            for (int i = REGIONS.length - 1; i >= 0; --i) {
                Tuple<Integer, Integer> limit = REGIONS[i].getA();
                if (lastBlock > limit.getB()) {
                    break;
                }

                Tuple<Integer, Integer> pos = REGIONS[i].getB().getA();
                Tuple<Integer, Integer> size = REGIONS[i].getB().getB();
                if (lastBlock > limit.getA()) {
                    int oldY = pos.getB() * multiplier;
                    int y = (int)((pos.getB() + lastBlock - limit.getA()) * multiplier);
                    int height = size.getB() * multiplier - (y - oldY);
                    img.fillRect(pos.getA() * multiplier, y, size.getA() * multiplier, height, WHITE);
                } else {
                    img.fillRect(pos.getA() * multiplier, pos.getB() * multiplier, size.getA() * multiplier, size.getB() * multiplier, WHITE);
                }
            }

            return new DynamicTexture(img);
        }
    }
}
