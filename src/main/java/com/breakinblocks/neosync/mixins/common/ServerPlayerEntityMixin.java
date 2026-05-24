package com.breakinblocks.neosync.mixins.common;

import com.mojang.authlib.GameProfile;
import com.mojang.datafixers.util.Either;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.Mth;
import net.minecraft.util.Tuple;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.scores.Team;
import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.breakinblocks.neosync.api.networking.PlayerIsAlivePacket;
import com.breakinblocks.neosync.api.networking.ShellStateUpdatePacket;
import com.breakinblocks.neosync.api.networking.ShellUpdatePacket;
import com.breakinblocks.neosync.api.shell.*;
import com.breakinblocks.neosync.common.entity.KillableEntity;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import com.breakinblocks.neosync.common.utils.WorldUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerEntityMixin extends Player implements ServerShell, KillableEntity {
    @Shadow
    private int lastSentExp;

    @Shadow
    private float lastSentHealth;

    @Shadow
    private int lastSentFood;

    @Shadow @Final
    public MinecraftServer server;

    @Shadow
    public ServerGamePacketListenerImpl connection;

    @Unique
    private boolean isArtificial = false;

    @Unique
    private boolean shellDirty = false;

    @Unique
    private boolean undead = false;

    @Unique
    private ConcurrentMap<UUID, ShellState> shellsById = new ConcurrentHashMap<>();

    @Unique
    private Map<UUID, Tuple<ShellStateUpdateType, ShellState>> shellStateChanges = new ConcurrentHashMap<>();

    private ServerPlayerEntityMixin(Level world, BlockPos pos, float yaw, GameProfile profile) {
        super(world, pos, yaw, profile);
    }

    @Override
    public UUID getShellOwnerUuid() {
        return this.getGameProfile().getId();
    }

    @Override
    public boolean isArtificial() {
        return this.isArtificial;
    }

    @Override
    public void changeArtificialStatus(boolean isArtificial) {
        if (this.isArtificial != isArtificial) {
            this.isArtificial = isArtificial;
            this.shellDirty = true;
        }
    }

    @Override
    public Either sync(ShellState state, @Nullable BlockPos currentContainerPos) {
        ServerPlayer player = (ServerPlayer)(Object)this;
        Level currentWorld = player.level();

        if (currentContainerPos != null) {
            double currentContainerDistance = NeoSyncSableCompat.distanceSquared(
                    currentWorld,
                    player.position(),
                    Vec3.atCenterOf(currentContainerPos)
            );

            if (currentContainerDistance > 4.0D) {
                return Either.right(PlayerSyncEvents.SyncFailureReason.INVALID_CURRENT_LOCATION);
            }
        }

        BlockPos currentPos = currentContainerPos == null
                ? this.blockPosition()
                : ShellStateContainer.getContainerBottomPos(currentWorld, currentContainerPos);

        if (!this.canBeApplied(state) || state.getProgress() < ShellState.PROGRESS_DONE) {
            return Either.right(PlayerSyncEvents.SyncFailureReason.INVALID_SHELL);
        }

        boolean isDead = this.isDeadOrDying();
        ShellStateContainer currentShellContainer = isDead ? null : ShellStateContainer.find(currentWorld, currentPos);
        if (!isDead && (currentShellContainer == null || currentShellContainer.getShellState() != null)) {
            return Either.right(PlayerSyncEvents.SyncFailureReason.INVALID_CURRENT_LOCATION);
        }

        PlayerSyncEvents.ShellSelectionFailureReason selectionFailureReason = PlayerSyncEvents.ALLOW_SHELL_SELECTION.invoker().allowShellSelection(player, currentShellContainer);
        if (selectionFailureReason != null) {
            PlayerSyncEvents.SyncFailureReason syncFailureReason = selectionFailureReason::toText;
            return Either.right(syncFailureReason);
        }

        ResourceLocation targetWorldId = state.getWorld();
        ServerLevel targetWorld = WorldUtil.findWorld(this.server.getAllLevels(), targetWorldId).orElse(null);
        if (targetWorld == null) {
            return Either.right(PlayerSyncEvents.SyncFailureReason.INVALID_TARGET_LOCATION);
        }

        BlockPos targetPos = ShellStateContainer.getContainerBottomPos(targetWorld, state.getPos());
        LevelChunk targetChunk = targetWorld.getChunk(targetPos.getX() >> 4, targetPos.getZ() >> 4);
        ShellStateContainer targetShellContainer = targetChunk == null ? null : ShellStateContainer.find(targetWorld, targetPos);
        if (targetShellContainer == null) {
            return Either.right(PlayerSyncEvents.SyncFailureReason.INVALID_TARGET_LOCATION);
        }

        state = targetShellContainer.getShellState();
        PlayerSyncEvents.SyncFailureReason finalFailureReason = this.canBeApplied(state) ? PlayerSyncEvents.ALLOW_SYNCING.invoker().allowSync(this, state) : PlayerSyncEvents.SyncFailureReason.INVALID_SHELL;
        if (finalFailureReason != null) {
            return Either.right(finalFailureReason);
        }

        PlayerSyncEvents.START_SYNCING.invoker().onStartSyncing(this, state);

        ShellState storedState = null;
        if (currentShellContainer != null) {
            storedState = ShellState.of(player, currentPos, currentShellContainer.getColor());
            currentShellContainer.setShellState(storedState);
            if (currentShellContainer.isRemotelyAccessible()) {
                this.add(storedState);
            }
        }

        targetShellContainer.setShellState(null);
        this.remove(state);
        this.apply(state);

        PlayerSyncEvents.STOP_SYNCING.invoker().onStopSyncing(player, currentPos, storedState);
        return Either.left(storedState);
    }

    @Override
    public void apply(ShellState state) {
        Objects.requireNonNull(state);

        ServerPlayer serverPlayer = (ServerPlayer)(Object)this;
        MinecraftServer server = Objects.requireNonNull(this.getServer());
        ServerLevel targetWorld = WorldUtil.findWorld(server.getAllLevels(), state.getWorld()).orElse(null);
        if (targetWorld == null) {
            return;
        }

        this.stopRiding();
        this.removeEntitiesOnShoulder();
        this.clearFire();
        this.setTicksFrozen(0);
        this.setRemainingFireTicks(0);
        this.removeAllEffects();

        new PlayerIsAlivePacket(serverPlayer).sendToAll(server);
        BlockPos teleportPos = NeoSyncSableCompat.projectOut(targetWorld, state.getPos());
        this.teleport(targetWorld, teleportPos);
        this.isArtificial = state.isArtificial();

        Inventory inventory = this.getInventory();
        int selectedSlot = inventory.selected;
        state.getInventory().copyTo(inventory);
        inventory.selected = selectedSlot;

        ShellStateComponent playerComponent = ShellStateComponent.of(serverPlayer);
        playerComponent.clone(state.getComponent());

        serverPlayer.setGameMode(GameType.byId(state.getGameMode()));
        this.setHealth(state.getHealth());
        this.experienceLevel = state.getExperienceLevel();
        this.experienceProgress = state.getExperienceProgress();
        this.totalExperience = state.getTotalExperience();
        this.getFoodData().setFoodLevel(state.getFoodLevel());
        this.getFoodData().setSaturation(state.getSaturationLevel());
        this.getFoodData().setExhaustion(state.getExhaustion());

        this.undead = false;
        this.dead = false;
        this.deathTime = 0;
        this.fallDistance = 0;
        this.lastSentExp = -1;
        this.lastSentHealth = -1;
        this.lastSentFood = -1;
        this.shellDirty = true;
    }

    @Override
    public Stream<ShellState> getAvailableShellStates() {
        return this.shellsById.values().stream();
    }

    @Override
    public void setAvailableShellStates(Stream<ShellState> states) {
        this.shellsById = states.collect(Collectors.toConcurrentMap(ShellState::getUuid, x -> x));
        this.shellDirty = true;
    }

    @Override
    public ShellState getShellStateByUuid(UUID uuid) {
        return uuid == null ? null : this.shellsById.get(uuid);
    }

    @Override
    public void add(ShellState state) {
        if (!this.canBeApplied(state)) {
            return;
        }

        this.shellsById.put(state.getUuid(), state);
        this.shellStateChanges.put(state.getUuid(), new Tuple<>(ShellStateUpdateType.ADD, state));
    }

    @Override
    public void remove(ShellState state) {
        if (state == null) {
            return;
        }

        if (this.shellsById.remove(state.getUuid()) != null) {
            this.shellStateChanges.put(state.getUuid(), new Tuple<>(ShellStateUpdateType.REMOVE, state));
        }
    }

    @Override
    public void update(ShellState state) {
        if (state == null) {
            return;
        }

        boolean updated;
        if (this.canBeApplied(state)) {
            updated = this.shellsById.put(state.getUuid(), state) != null;
        } else {
            updated = this.shellsById.computeIfPresent(state.getUuid(), (a, b) -> state) != null;
        }
        this.shellStateChanges.put(state.getUuid(), new Tuple<>(updated ? ShellStateUpdateType.UPDATE : ShellStateUpdateType.ADD, state));
    }

    @Inject(method = "doTick", at = @At("HEAD"))
    private void playerTick(CallbackInfo ci) {
        ServerPlayer player = (ServerPlayer)(Object)this;

        if (this.shellDirty) {
            this.shellDirty = false;
            this.shellStateChanges.clear();
            new ShellUpdatePacket(WorldUtil.getId(this.level()), this.isArtificial, this.shellsById.values()).send(player);
        }

        for (Tuple<ShellStateUpdateType, ShellState> upd : this.shellStateChanges.values()) {
            new ShellStateUpdatePacket(upd.getA(), upd.getB()).send(player);
        }
        this.shellStateChanges.clear();
    }

    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void onDeath(DamageSource source, CallbackInfo ci) {
        if (!this.isArtificial) {
            return;
        }

        ShellState respawnShell = this.shellsById.values().stream().filter(x -> this.canBeApplied(x) && x.getProgress() >= ShellState.PROGRESS_DONE).findAny().orElse(null);
        if (respawnShell == null) {
            return;
        }

        if (this.level().getGameRules().getBoolean(GameRules.RULE_SHOWDEATHMESSAGES)) {
            this.sendDeathMessageInChat();
        } else {
            this.sendEmptyDeathMessageInChat();
        }

        this.removeEntitiesOnShoulder();
        if (this.level().getGameRules().getBoolean(GameRules.RULE_FORGIVE_DEAD_PLAYERS)) {
            this.tellNeutralMobsThatIDied();
        }

        if (!this.isSpectator() && this.level() instanceof ServerLevel serverLevel) {
            this.dropAllDeathLoot(serverLevel, source);
        }

        this.undead = true;
        ci.cancel();
    }

    @Override
    public boolean updateKillableEntityPostDeath() {
        this.deathTime = Mth.clamp(++this.deathTime, 0, 20);
        if (this.isArtificial && this.shellsById.values().stream().anyMatch(x -> this.canBeApplied(x) && x.getProgress() >= ShellState.PROGRESS_DONE)) {
            return true;
        }

        if (this.undead) {
            this.die(level().damageSources().magic());
            this.undead = false;
        }

        if (this.deathTime == 20) {
            this.level().broadcastEntityEvent(this, (byte)60);
            this.remove(RemovalReason.KILLED);
        }
        return true;
    }

    @Unique
    private void sendDeathMessageInChat() {
        Component text = this.getCombatTracker().getDeathMessage();
        this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), text));
        Team team = this.getTeam();
        if (team != null && team.getDeathMessageVisibility() != Team.Visibility.ALWAYS) {
            if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OTHER_TEAMS) {
                this.server.getPlayerList().broadcastSystemToTeam(this, text);
            } else if (team.getDeathMessageVisibility() == Team.Visibility.HIDE_FOR_OWN_TEAM) {
                this.server.getPlayerList().broadcastSystemToAllExceptTeam(this, text);
            }
        } else {
            this.server.getPlayerList().broadcastSystemMessage(text, false);
        }
    }

    @Unique
    private void sendEmptyDeathMessageInChat() {
        this.connection.send(new ClientboundPlayerCombatKillPacket(this.getId(), Component.empty()));
    }

    @Shadow
    protected abstract void tellNeutralMobsThatIDied();

    @Shadow
    protected abstract void triggerDimensionChangeTriggers(ServerLevel serverLevel);

    @Shadow public abstract ServerLevel serverLevel();

    @Shadow public abstract boolean isChangingDimension();

    @Shadow private boolean isChangingDimension;

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void writeCustomDataToNbt(CompoundTag nbt, CallbackInfo ci) {
        ListTag shellList = new ListTag();
        this.shellsById
                .values()
                .stream()
                .map(x -> x.writeNbt(new CompoundTag()))
                .forEach(shellList::add);

        nbt.putBoolean("IsArtificial", this.isArtificial);
        nbt.put("Shells", shellList);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void readCustomDataFromNbt(CompoundTag nbt, CallbackInfo ci) {
        this.isArtificial = nbt.getBoolean("IsArtificial");
        this.shellsById = nbt.getList("Shells", Tag.TAG_COMPOUND)
                .stream()
                .map(x -> ShellState.fromNbt((CompoundTag)x))
                .collect(Collectors.toConcurrentMap(ShellState::getUuid, x -> x));

        Collection<Tuple<ShellStateUpdateType, ShellState>> updates = ((ShellStateManager)this.server).popPendingUpdates(this.uuid);
        for (Tuple<ShellStateUpdateType, ShellState> update : updates) {
            ShellState state = update.getB();
            switch (update.getA()) {
                case ADD, UPDATE -> {
                    if (this.uuid.equals(state.getOwnerUuid())) {
                        this.shellsById.put(state.getUuid(), state);
                    }
                }
                case REMOVE -> this.shellsById.remove(state.getUuid());
            }
        }

        this.shellStateChanges = new HashMap<>();
        this.shellDirty = true;
    }

    @Inject(method = "restoreFrom", at = @At("HEAD"))
    private void copyFrom(ServerPlayer oldPlayer, boolean alive, CallbackInfo ci) {
        Shell shell = (Shell)oldPlayer;
        this.isArtificial = alive && shell.isArtificial();
        this.shellsById = shell.getAvailableShellStates().collect(Collectors.toConcurrentMap(ShellState::getUuid, x -> x));
        this.shellStateChanges = new HashMap<>();
        this.shellDirty = true;
    }

    @Inject(method = "setServerLevel", at = @At("HEAD"))
    private void setWorld(ServerLevel world, CallbackInfo ci) {
        if (world != this.level()) {
            this.shellDirty = true;
        }
    }

    @Unique
    private void teleport(ServerLevel targetWorld, BlockPos pos) {
        this.isChangingDimension = true;
        LevelChunk chunk = targetWorld.getChunk(pos.getX() >> 4, pos.getZ() >> 4);
        double x = pos.getX() + 0.5;
        double y = pos.getY();
        double z = pos.getZ() + 0.5;
        float yaw = BlockPosUtil.getHorizontalFacing(pos, chunk).map(d -> d.getOpposite().toYRot()).orElse(0F);
        float pitch = 0;

        if (this.level() == targetWorld) {
            this.connection.teleport(x, y, z, yaw, pitch);
            return;
        }

        ServerLevel serverWorld = this.serverLevel();
        ServerPlayer serverPlayer = (ServerPlayer)(Object)this;

        CommonPlayerSpawnInfo spawnInfo = new CommonPlayerSpawnInfo(
                targetWorld.dimensionTypeRegistration(),
                targetWorld.dimension(),
                BiomeManager.obfuscateSeed(targetWorld.getSeed()),
                serverPlayer.gameMode.getGameModeForPlayer(),
                serverPlayer.gameMode.getPreviousGameModeForPlayer(),
                targetWorld.isDebug(),
                targetWorld.isFlat(),
                this.getLastDeathLocation(),
                3
        );
        serverPlayer.connection.send(new ClientboundRespawnPacket(spawnInfo, (byte) 1));
        serverPlayer.connection.send(new ClientboundChangeDifficultyPacket(targetWorld.getDifficulty(), targetWorld.getLevelData().isDifficultyLocked()));
        PlayerList playerManager = Objects.requireNonNull(this.level().getServer()).getPlayerList();
        playerManager.sendPlayerPermissionLevel(serverPlayer);
        serverWorld.removePlayerImmediately(serverPlayer, RemovalReason.CHANGED_DIMENSION);
        this.unsetRemoved();
        serverPlayer.setServerLevel(targetWorld);
        targetWorld.addDuringTeleport(serverPlayer);
        this.connection.teleport(x, y, z, yaw, pitch);
        this.triggerDimensionChangeTriggers(targetWorld);
        serverPlayer.connection.send(new ClientboundPlayerAbilitiesPacket(serverPlayer.getAbilities()));
        playerManager.sendLevelInfo(serverPlayer, targetWorld);
        playerManager.sendAllPlayerInfo(serverPlayer);
        for (MobEffectInstance effectInstance : this.getActiveEffects()) {
            this.connection.send(new ClientboundUpdateMobEffectPacket(this.getId(), effectInstance, false));
        }
        this.triggerDimensionChangeTriggers(targetWorld);
    }
}
