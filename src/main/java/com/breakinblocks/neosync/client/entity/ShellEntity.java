package com.breakinblocks.neosync.client.entity;

import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@OnlyIn(Dist.CLIENT)
public class ShellEntity extends RemotePlayer {
    private static final Cache<UUID, PlayerInfo> PLAYER_ENTRY_CACHE = CacheBuilder.newBuilder()
            .initialCapacity(20)
            .maximumSize(40)
            .expireAfterAccess(20, TimeUnit.MINUTES)
            .build();

    public boolean isActive;
    public float pitchProgress;
    private final ShellState state;
    private Runnable onInitialized;
    private final PlayerInfo playerEntry;

    public ShellEntity(ShellState state) {
        this(Minecraft.getInstance().level, state);
    }

    public ShellEntity(ClientLevel world, ShellState state) {
        super(world, new GameProfile(state.getOwnerUuid(), state.getOwnerName()));
        this.isActive = false;
        this.pitchProgress = 0;
        this.state = state;
        this.refreshFromState();
        this.playerEntry = getPlayerEntry(state);
        this.moveTo(state.getPos().getX() + 0.5, state.getPos().getY(), state.getPos().getZ() + 0.5, 0, 0);
        this.xCloakO = this.xCloak = getX();
        this.yCloakO = this.yCloak = getY();
        this.zCloakO = this.zCloak = getZ();
        NeoSyncDebug.info("shell-entity", "created shell entity state={} playerEntry={}", state.getUuid(), this.playerEntry != null);

        if (this.onInitialized != null) {
            this.onInitialized.run();
            this.onInitialized = null;
        }
    }

    public void refreshFromState() {
        this.state.getInventory().copyTo(this.getInventory());
        NeoSyncDebug.info("shell-entity", "refreshFromState state={} progress={} pos={}", this.state.getUuid(), this.state.getProgress(), this.state.getPos());
    }

    public void onInitialized(Runnable runnable) {
        if (this.state == null) {
            this.onInitialized = runnable;
        } else if (runnable != null) {
            runnable.run();
        }
    }

    public ShellState getState() {
        return this.state;
    }

    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource damageSource) {
        // Don't drop items when shell is destroyed.
    }

    @Override
    public boolean isCreative() {
        return true;
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean shouldShowName() {
        return false;
    }

    @Override
    public boolean isModelPartShown(PlayerModelPart modelPart) {
        return true;
    }

    @Override
    public PlayerInfo getPlayerInfo() {
        return this.playerEntry;
    }

    private static PlayerInfo getPlayerEntry(ShellState state) {
        PlayerInfo entry = PLAYER_ENTRY_CACHE.getIfPresent(state.getOwnerUuid());

        if (entry == null) {
            Minecraft client = Minecraft.getInstance();
            ClientPacketListener networkHandler = client.getConnection();

            if (networkHandler != null) {
                entry = networkHandler.getPlayerInfo(state.getOwnerUuid());

                if (entry == null) {
                    entry = networkHandler.getPlayerInfo(state.getOwnerName());
                }
            }

            if (entry != null) {
                PLAYER_ENTRY_CACHE.put(state.getOwnerUuid(), entry);
            }
        }

        return entry;
    }
}
