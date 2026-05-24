package com.breakinblocks.neosync.client.utils.render;

import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.common.utils.math.Voxel;
import com.breakinblocks.neosync.common.utils.math.VoxelIterator;

import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@OnlyIn(Dist.CLIENT)
public final class ModelUtil {
    public static ModelPart copy(ModelPart original) {
        ModelPart copy = new ModelPart(original.cubes, original.children);
        copy.copyFrom(original);
        return copy;
    }

    public static Stream<Voxel> asVoxels(ModelPart part) {
        return asVoxels(0, 0, 0, part);
    }

    public static Stream<Voxel> asVoxels(float x, float y, float z, ModelPart part) {
        final float pivotX = x + part.x;
        final float pivotY = y + part.y;
        final float pivotZ = z + part.z;
        return Stream.concat(
                part.cubes.stream().flatMap(cube -> asVoxels(pivotX, pivotY, pivotZ, cube)),
                part.children.values().stream().flatMap(p -> asVoxels(pivotX, pivotY, pivotZ, p))
        );
    }

    public static Stream<Voxel> asVoxels(float x, float y, float z, ModelPart.Cube cube) {
        int sizeX = (int)(cube.maxX - cube.minX);
        int sizeY = (int)(cube.maxY - cube.minY);
        int sizeZ = (int)(cube.maxZ - cube.minZ);
        Iterator<Voxel> iterator = new VoxelIterator(x, y, z, sizeX, sizeY, sizeZ);
        Spliterator<Voxel> spliterator = Spliterators.spliteratorUnknownSize(iterator, 0);
        return StreamSupport.stream(spliterator, false);
    }

    private ModelUtil() {
    }
}

