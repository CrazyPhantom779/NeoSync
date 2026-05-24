package com.breakinblocks.neosync.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.AgeableListModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.client.render.CustomRenderLayer;
import com.breakinblocks.neosync.client.utils.render.ModelUtil;
import com.breakinblocks.neosync.common.utils.math.Voxel;
import org.joml.Vector3f;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

@OnlyIn(Dist.CLIENT)
public class VoxelModel extends Model {
    public float completeness;
    public float destructionProgress;

    public final float sizeX;
    public final float sizeY;
    public final float sizeZ;
    public final float pivotX;
    public final float pivotY;
    public final float pivotZ;

    private final List<Voxel> voxels;
    private final ModelPart voxel;
    private final long seed;
    private final Random random;

    private VoxelModel(Stream<Voxel> voxels, boolean isUpsideDown, long seed) {
        super(id -> CustomRenderLayer.getVoxels());
        this.completeness = 0;
        this.voxels = voxels.collect(Collectors.toList());

        this.voxel = new ModelPart(List.of(new ModelPart.Cube(0, 0, 0, 0, 0, 1, 1, 1, 0, 0, 0, true, 1, 1, Arrays.stream(Direction.values()).collect(Collectors.toSet()))), Map.of());

        Tuple<Vector3f, Vector3f> pivotAndSize = computePivotAndSize(isUpsideDown, this.voxels);
        Vector3f pivot = pivotAndSize.getA();
        Vector3f size = pivotAndSize.getB();
        this.pivotX = pivot.x();
        this.pivotY = pivot.y();
        this.pivotZ = pivot.z();
        this.sizeX = size.x();
        this.sizeY = size.y();
        this.sizeZ = size.z();

        this.seed = seed;
        this.random = new Random();
    }

    @Override
    public void renderToBuffer(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        if (this.destructionProgress > 0) {
            this.renderWithDestruction(matrices, vertices, light, overlay, color);
        } else {
            this.renderWithoutDestruction(matrices, vertices, light, overlay, color);
        }
    }

    private void renderWithoutDestruction(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        ModelPart voxel = this.voxel;
        List<Voxel> voxels = this.voxels;
        int size = Mth.clamp((int)(voxels.size() * this.completeness), 0, voxels.size());

        for (int i = 0; i < size; ++i) {
            Voxel v = voxels.get(i);
            voxel.setPos(v.x, v.y, v.z);
            voxel.render(matrices, vertices, light, overlay, color);
        }
    }

    private void renderWithDestruction(PoseStack matrices, VertexConsumer vertices, int light, int overlay, int color) {
        Random rand = this.random;
        ModelPart voxel = this.voxel;
        List<Voxel> voxels = this.voxels;
        int size = Mth.clamp((int)(voxels.size() * this.completeness), 0, voxels.size());

        float minY = this.pivotY;
        float maxY = this.pivotY + this.sizeY;
        if (minY > maxY) {
            float tmp = minY;
            minY = maxY;
            maxY = tmp;
        }

        float g = -this.sizeY * 0.005F;
        float t = this.destructionProgress * 20;
        float k = t * t / 2;
        rand.setSeed(this.seed);
        for (int i = 0; i < size; ++i) {
            float vX = rand.nextFloat() - 0.5F;
            float vY = g * (rand.nextFloat() - 0.5F);
            float vZ = rand.nextFloat() - 0.5F;

            Voxel v = voxels.get(i);
            float x = v.x + vX * t;
            float y = Mth.clamp(v.y + vY * t + g * k, minY, maxY);
            float z = v.z + vZ * t;

            voxel.setPos(x, y, z);
            voxel.render(matrices, vertices, light, overlay, color);
        }
    }

    public static VoxelModel fromModel(AgeableListModel<?> model, Random random) {
        Stream<Voxel> voxels = model instanceof VoxelProvider voxelProvider ? voxelProvider.getVoxels() : extractVoxels(model);
        boolean isUpsideDown = !(model instanceof VoxelProvider voxelProvider) || voxelProvider.isUpsideDown();

        Map<Float, List<Voxel>> voxelMap = new HashMap<>();
        voxels.forEach(x -> {
            if (!voxelMap.containsKey(x.y)) {
                voxelMap.put(x.y, new ArrayList<>());
            }
            voxelMap.get(x.y).add(x);
        });

        for (List<Voxel> voxelsAtY : voxelMap.values()) {
            for (int i = voxelsAtY.size() - 1; i > 0; --i) {
                int j = random.nextInt(i + 1);
                Voxel tmp = voxelsAtY.get(j);
                voxelsAtY.set(j, voxelsAtY.get(i));
                voxelsAtY.set(i, tmp);
            }
        }

        int sign = isUpsideDown ? -1 : 1;
        Stream<Voxel> orderedVoxels = voxelMap.entrySet().stream().sorted((a, b) -> sign * (int)Math.signum(a.getKey() - b.getKey())).flatMap(x -> x.getValue().stream());
        return new VoxelModel(orderedVoxels, isUpsideDown, random.nextLong());
    }

    private static Stream<Voxel> extractVoxels(AgeableListModel<?> model) {
        return Stream.concat(
                StreamSupport.stream(invokeProtectedIterable(model, "headParts").spliterator(), false),
                StreamSupport.stream(invokeProtectedIterable(model, "bodyParts").spliterator(), false)
        ).flatMap(ModelUtil::asVoxels);
    }

    @SuppressWarnings("unchecked")
    private static Iterable<ModelPart> invokeProtectedIterable(AgeableListModel<?> model, String methodName) {
        try {
            Method m = AgeableListModel.class.getDeclaredMethod(methodName);
            m.setAccessible(true);
            Object result = m.invoke(model);
            return result == null ? List.of() : (Iterable<ModelPart>) result;
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to access " + methodName + " on AgeableListModel", e);
        }
    }

    private static Vector3f ZERO = new Vector3f(0, 0, 0);

    private static Tuple<Vector3f, Vector3f> computePivotAndSize(boolean isUpsideDown, List<Voxel> voxels) {
        if (voxels.size() == 0) {
            return new Tuple<>(ZERO, ZERO);
        }

        float minX = Float.MAX_VALUE;
        float maxX = Float.MIN_VALUE;
        float minY = Float.MAX_VALUE;
        float maxY = Float.MIN_VALUE;
        float minZ = Float.MAX_VALUE;
        float maxZ = Float.MIN_VALUE;

        for (Voxel voxel : voxels) {
            if (voxel.x > maxX) {
                maxX = voxel.x;
            }
            if (voxel.x < minX) {
                minX = voxel.x;
            }
            if (voxel.y > maxY) {
                maxY = voxel.y;
            }
            if (voxel.y < minY) {
                minY = voxel.y;
            }
            if (voxel.z > maxZ) {
                maxZ = voxel.z;
            }
            if (voxel.z < minZ) {
                minZ = voxel.z;
            }
        }

        int signX = isUpsideDown ? -1 : 1;
        int signY = isUpsideDown ? -1 : 1;

        Vector3f pivot = new Vector3f(
                signX == 1 ? minX : maxX,
                signY == 1 ? minY : maxY,
                minZ
        );

        Vector3f size = new Vector3f(
                signX * (maxX - minX + 1),
                signY * (maxY - minY + 1),
                (maxZ - minZ + 1)
        );

        return new Tuple<>(pivot, size);
    }
}
