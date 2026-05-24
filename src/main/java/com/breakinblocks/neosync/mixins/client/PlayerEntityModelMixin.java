package com.breakinblocks.neosync.mixins.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import com.breakinblocks.neosync.client.model.VoxelProvider;
import com.breakinblocks.neosync.client.utils.render.ModelUtil;
import com.breakinblocks.neosync.common.utils.math.Voxel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.stream.Stream;

@OnlyIn(Dist.CLIENT)
@Mixin(PlayerModel.class)
abstract class PlayerEntityModelMixin implements VoxelProvider {
    @Shadow @Final
    private boolean slim;

    @Override
    @SuppressWarnings("ConstantConditions")
    public Stream<Voxel> getVoxels() {
        PlayerModel<?> model = (PlayerModel<?>)(Object)this;

        float cX = -2;
        float cY = 0;
        float cZ = -2;

        ModelPart head = ModelUtil.copy(model.head);
        head.x = cX - 2;
        head.y = cY - 8;
        head.z = cZ - 2;

        ModelPart body = ModelUtil.copy(model.body);
        body.x = cX - 2;
        body.y = cY;
        body.z = cZ;

        ModelPart leftArm = ModelUtil.copy(model.leftArm);
        ModelPart rightArm = ModelUtil.copy(model.rightArm);
        if (this.slim) {
            leftArm.x = cX + 6;
            leftArm.y = cY + 0.5F;
            leftArm.z = cZ;

            rightArm.x = cX - 5;
            rightArm.y = cY + 0.5F;
            rightArm.z = cZ;
        } else {
            leftArm.x = cX + 6;
            leftArm.y = cY;
            leftArm.z = cZ;

            rightArm.x = cX - 6;
            rightArm.y = cY;
            rightArm.z = cZ;
        }

        ModelPart leftLeg = ModelUtil.copy(model.leftLeg);
        leftLeg.x = cX + 1.9F;
        leftLeg.y = cY + 12;
        leftLeg.z = cZ;

        ModelPart rightLeg = ModelUtil.copy(model.rightLeg);
        rightLeg.x = cX - 1.9F;
        rightLeg.y = cY + 12;
        rightLeg.z = cZ;

        return Stream.of(leftLeg, rightLeg, leftArm, rightArm, body, head).flatMap(ModelUtil::asVoxels);
    }
}
