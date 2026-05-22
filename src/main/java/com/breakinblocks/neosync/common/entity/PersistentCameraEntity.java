package com.breakinblocks.neosync.common.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import com.breakinblocks.neosync.NeoSync;

import java.util.Objects;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = NeoSync.MOD_ID, value = Dist.CLIENT)
public class PersistentCameraEntity extends LocalPlayer {
    private long lastMovementTime;
    private float initialYaw;
    private float initialPitch;
    private double initialDistance;
    private PersistentCameraEntityGoal goal;

    private PersistentCameraEntity(Minecraft client, ClientLevel world, LocalPlayer player) {
        super(client, world, player.connection, player.getStats(), player.getRecipeBook(), false, false);
        this.moveTo(player.getX(), player.getY(), player.getZ(), player.getYRot(), player.getXRot());
        this.setYRot(player.getYRot());
        this.setXRot(player.getXRot());
        this.updateLastTickValues();
        this.noPhysics = true;
    }

    @Override
    public boolean isSpectator() {
        return true;
    }

    @Override
    public void aiStep() {
        PersistentCameraEntityGoal goal = this.goal;
        long currentTime = System.currentTimeMillis();
        if (goal == null || this.lastMovementTime < 0) {
            this.lastMovementTime = currentTime;
            return;
        }
        if (this.lastMovementTime > currentTime) {
            return;
        }
        this.updateLastTickValues();

        Vec3 currentPos = this.position();
        Vec3 currentVelocity = this.getDeltaMovement();
        Vec3 newPos = currentPos.add(currentVelocity.scale(currentTime - this.lastMovementTime));
        Vec3 currentDiff = goal.pos.subtract(currentPos);
        Vec3 newDiff = goal.pos.subtract(newPos);

        if (Math.signum(currentDiff.x) != Math.signum(newDiff.x)) {
            newPos = new Vec3(goal.pos.x, newPos.y, newPos.z);
            currentVelocity = new Vec3(0, currentVelocity.y, currentVelocity.z);
        }
        if (Math.signum(currentDiff.y) != Math.signum(newDiff.y)) {
            newPos = new Vec3(newPos.x, goal.pos.y, newPos.z);
            currentVelocity = new Vec3(currentVelocity.x, 0, currentVelocity.z);
        }
        if (Math.signum(currentDiff.z) != Math.signum(newDiff.z)) {
            newPos = new Vec3(newPos.x, newPos.y, goal.pos.z);
            currentVelocity = new Vec3(currentVelocity.x, currentVelocity.y, 0);
        }

        this.setPos(newPos);
        this.setDeltaMovement(currentVelocity);

        float factor = 1F - (float)(goal.pos.distanceTo(newPos) / this.initialDistance);
        float newYaw = this.initialYaw + (goal.yaw - this.initialYaw) * factor;
        float newPitch = this.initialPitch + (goal.pitch - this.initialPitch) * factor;
        this.moveTo(newPos.x, newPos.y, newPos.z, newYaw, newPitch);
        this.setYRot(newYaw);
        this.setXRot(newPitch);
        this.setYHeadRot(newYaw);
        this.setYBodyRot(newYaw);
        this.yRotO = this.yRotO + (newYaw - this.yRotO) * 0.5F;
        this.xRotO = this.xRotO + (newPitch - this.xRotO) * 0.5F;

        this.lastMovementTime = currentTime;
        if (this.position().equals(goal.pos)) {
            this.updateLastTickValues();
            this.setGoal(null);
            goal.finish(this);
        }
    }

    private void updateLastTickValues() {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();

        this.xOld = this.getX();
        this.yOld = this.getY();
        this.zOld = this.getZ();

        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();

        this.yHeadRotO = this.yHeadRot;
    }

    public void setGoal(PersistentCameraEntityGoal goal) {
        this.goal = goal;
        this.initialYaw = this.getYRot();
        this.initialPitch = this.getXRot();
        this.lastMovementTime = -1;

        double dX = 0;
        double dY = 0;
        double dZ = 0;
        double duration = 0;
        if (goal != null) {
            Vec3 pos = this.position();
            dX = goal.pos.x - pos.x;
            dY = goal.pos.y - pos.y;
            dZ = goal.pos.z - pos.z;
            duration = goal.duration;
            if (goal.delay > 0) {
                this.lastMovementTime = System.currentTimeMillis() + goal.delay;
            }
        }

        this.initialDistance = Math.sqrt(dX * dX + dY * dY + dZ * dZ);
        this.setDeltaMovement(new Vec3(dX, dY, dZ).scale(1.0 / Math.max(1, duration)));
    }

    public PersistentCameraEntityGoal getGoal() {
        return this.goal;
    }

    public static void setup(Minecraft client, PersistentCameraEntityGoal goal) {
        LocalPlayer player = client.player;
        if (player == null || player.level() == null) {
            return;
        }

        if (!(client.getCameraEntity() instanceof PersistentCameraEntity)) {
            client.setCameraEntity(new PersistentCameraEntity(client, player.clientLevel, player));
        }

        PersistentCameraEntity camera = (PersistentCameraEntity) Objects.requireNonNull(client.getCameraEntity());
        camera.setGoal(goal);
    }

    public static void unset(Minecraft client) {
        if (client.getCameraEntity() instanceof PersistentCameraEntity camera) {
            camera.setGoal(null);
            client.setCameraEntity(client.player);
        }
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft client = Minecraft.getInstance();
        if (!(client.getCameraEntity() instanceof PersistentCameraEntity camera) || camera.goal == null) {
            return;
        }

        camera.aiStep();
    }

    @SubscribeEvent
    public static void onPlayerLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        unset(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onPlayerLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        unset(Minecraft.getInstance());
    }

    @SubscribeEvent
    public static void onPlayerClone(ClientPlayerNetworkEvent.Clone event) {
        unset(Minecraft.getInstance());
    }
}