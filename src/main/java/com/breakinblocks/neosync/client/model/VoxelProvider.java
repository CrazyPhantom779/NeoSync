package com.breakinblocks.neosync.client.model;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.common.utils.math.Voxel;

import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
public interface VoxelProvider {
    /**
     * Returns a stream of voxels that make up this model.
     * @return stream of voxels
     */
    Stream<Voxel> getVoxels();

    /**
     * Returns whether the model should be built from top to bottom (true) or bottom to top (false).
     * @return true if upside down building
     */
    default boolean isUpsideDown() {
        return true;
    }
}
