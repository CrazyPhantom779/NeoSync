package com.breakinblocks.neosync.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CustomVertexFormats {
    // Placeholder: aliases vanilla NEW_ENTITY until the custom voxel shader format is rebuilt for 1.21.1.
    public static final VertexFormat POSITION_COLOR_OVERLAY_LIGHT_NORMAL = DefaultVertexFormat.NEW_ENTITY;

    private CustomVertexFormats() {}
}
