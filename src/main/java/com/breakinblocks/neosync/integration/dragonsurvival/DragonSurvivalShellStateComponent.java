package com.breakinblocks.neosync.integration.dragonsurvival;

import com.breakinblocks.neosync.api.shell.ShellStateComponent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

public final class DragonSurvivalShellStateComponent extends ShellStateComponent {
    public static final String ID = "neosync:dragonsurvival";

    @Nullable
    private final ServerPlayer targetPlayer;

    private CompoundTag dragonData = new CompoundTag();

    public DragonSurvivalShellStateComponent() {
        this.targetPlayer = null;
    }

    public DragonSurvivalShellStateComponent(@Nullable ServerPlayer targetPlayer) {
        this.targetPlayer = targetPlayer;

        if (targetPlayer != null) {
            this.dragonData = NeoSyncDragonSurvivalCompat.captureDragonData(targetPlayer);
        }
    }

    public static DragonSurvivalShellStateComponent empty() {
        return new DragonSurvivalShellStateComponent();
    }

    public static DragonSurvivalShellStateComponent of(ServerPlayer player) {
        return new DragonSurvivalShellStateComponent(player);
    }

    public boolean hasDragonData() {
        return !dragonData.isEmpty();
    }

    public CompoundTag getDragonData() {
        return dragonData.copy();
    }

    @Override
    public String getId() {
        return ID;
    }

    @Override
    public void clone(ShellStateComponent component) {
        DragonSurvivalShellStateComponent other = component.as(DragonSurvivalShellStateComponent.class);
        dragonData = other == null ? new CompoundTag() : other.getDragonData();

        if (targetPlayer != null) {
            NeoSyncDragonSurvivalCompat.applyDragonData(targetPlayer, dragonData);
        }
    }

    @Override
    protected void readComponentNbt(CompoundTag nbt) {
        dragonData = nbt.getCompound("dragonData").copy();
    }

    @Override
    protected CompoundTag writeComponentNbt(CompoundTag nbt) {
        if (!dragonData.isEmpty()) {
            nbt.put("dragonData", dragonData.copy());
        }

        return nbt;
    }
}