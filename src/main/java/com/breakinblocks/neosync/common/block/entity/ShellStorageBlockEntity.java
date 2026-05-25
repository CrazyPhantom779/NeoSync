package com.breakinblocks.neosync.common.block.entity;

import com.breakinblocks.neosync.api.event.PlayerSyncEvents;
import com.breakinblocks.neosync.common.block.ShellStorageBlock;
import com.breakinblocks.neosync.common.config.SyncConfig;
import com.breakinblocks.neosync.common.utils.BlockPosUtil;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.breakinblocks.neosync.integration.sable.NeoSyncSableCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.DyeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;

public class ShellStorageBlockEntity extends AbstractShellContainerBlockEntity implements IEnergyStorage {
    private EntityState entityState;
    private int ticksWithoutPower;
    private int storedEnergy;
    private final BooleanAnimator connectorAnimator;

    public ShellStorageBlockEntity(BlockPos pos, BlockState state) {
        super(SyncBlockEntities.SHELL_STORAGE.get(), pos, state);
        this.entityState = EntityState.NONE;
        this.connectorAnimator = new BooleanAnimator(false);
    }

    public DyeColor getIndicatorColor() {
        if (this.level != null && ShellStorageBlock.isPowered(this.getBlockState())) {
            return this.color == null ? DyeColor.LIME : this.color;
        }

        return DyeColor.RED;
    }

    @OnlyIn(Dist.CLIENT)
    public float getConnectorProgress(float tickDelta) {
        return this.getBottomPart().map(x -> ((ShellStorageBlockEntity) x).connectorAnimator.getProgress(tickDelta)).orElse(0f);
    }

    @Override
    public void onServerTick(Level world, BlockPos pos, BlockState state) {
        super.onServerTick(world, pos, state);

        BlockState liveState = world.getBlockState(pos);

        if (!(liveState.getBlock() instanceof ShellStorageBlock) || !liveState.hasProperty(ShellStorageBlock.HALF)) {
            NeoSyncDebug.warn("storage", "server tick skipped because live state is not storage at {} live={}", NeoSyncDebug.describe(world, pos), liveState);
            return;
        }

        if (!ShellStorageBlock.isBottom(liveState)) {
            NeoSyncDebug.warn("storage", "server tick on upper half ignored at {}", NeoSyncDebug.describe(world, pos));
            return;
        }

        state = liveState;

        SyncConfig config = SyncConfig.getInstance();
        boolean infinitePower = config.shellStorageConsumption() == 0;
        boolean isReceivingRedstonePower = !infinitePower
                && config.shellStorageAcceptsRedstone()
                && ShellStorageBlock.isEnabled(state);
        boolean hasEnergy = infinitePower || this.storedEnergy > 0;
        boolean isPowered = infinitePower || isReceivingRedstonePower || hasEnergy;
        boolean shouldBeOpen = isPowered && this.getBottomPart().map(x -> x.shell == null).orElse(true);

        NeoSyncDebug.info(
                "storage",
                "server tick at {} shell={} energy={} infinitePower={} redstone={} hasEnergy={} powered={} shouldOpen={}",
                NeoSyncDebug.describe(world, pos),
                NeoSyncDebug.describeShell(this.shell),
                this.storedEnergy,
                infinitePower,
                isReceivingRedstonePower,
                hasEnergy,
                isPowered,
                shouldBeOpen
        );

        ShellStorageBlock.setPowered(state, world, pos, isPowered);
        ShellStorageBlock.setOpen(state, world, pos, shouldBeOpen);

        if (!infinitePower) {
            if (this.shell != null && !isPowered) {
                ++this.ticksWithoutPower;
                NeoSyncDebug.warn("storage", "unpowered shell ticking at {} ticksWithoutPower={}/{}", NeoSyncDebug.describe(world, pos), this.ticksWithoutPower, config.shellStorageMaxUnpoweredLifespan());

                if (this.ticksWithoutPower >= config.shellStorageMaxUnpoweredLifespan()) {
                    this.destroyShell((ServerLevel) world, pos);
                }
            } else {
                this.ticksWithoutPower = 0;
            }
        }

        if (!infinitePower && !isReceivingRedstonePower && hasEnergy && this.shell != null) {
            int oldEnergy = this.storedEnergy;
            this.storedEnergy = (int) Mth.clamp(this.storedEnergy - config.shellStorageConsumption(), 0, config.shellStorageCapacity());

            if (oldEnergy != this.storedEnergy) {
                this.setChanged();
                this.sync("storage-energy-drain");
            }
        }
    }

    @Override
    public void onClientTick(Level world, BlockPos pos, BlockState state) {
        super.onClientTick(world, pos, state);
        this.connectorAnimator.setValue(this.getShellState() != null);
        this.connectorAnimator.step();

        Vec3 shellCenter = NeoSyncSableCompat.projectBlockCenter(world, pos);

        if (this.entityState == EntityState.LEAVING || this.entityState == EntityState.CHILLING) {
            this.entityState = BlockPosUtil.hasPlayerInside(shellCenter, world) ? this.entityState : EntityState.NONE;
        }

        Entity localPlayer = getLocalClientPlayer();

        if (localPlayer != null
                && this.entityState != EntityState.LEAVING
                && canAcceptPlayerClient(state)
                && isNearStorageEntry(world, pos, localPlayer)) {
            NeoSyncDebug.info("storage", "client tick fallback collision at {} player={} state={}", NeoSyncDebug.describe(world, pos), localPlayer.getName().getString(), this.entityState);
            this.onEntityCollisionClient(localPlayer, state);
        }
    }

    @OnlyIn(Dist.CLIENT)
    public void onEntityCollisionClient(Entity entity, BlockState state) {
        if (!(entity instanceof Player player)) {
            return;
        }

        if (!canAcceptPlayerClient(state)) {
            if (this.entityState != EntityState.NONE) {
                NeoSyncDebug.info("storage", "client collision reset because storage cannot accept player at {} oldState={}", NeoSyncDebug.describe(entity.level(), this.worldPosition), this.entityState);
            }

            this.entityState = EntityState.NONE;
            return;
        }

        boolean isLocalPlayer = isLocalClientPlayer(entity);
        boolean hasNoScreen = hasNoClientScreen();

        Vec3 shellCenter = NeoSyncSableCompat.projectBlockCenter(entity.level(), this.worldPosition);
        Vec3 shellOutside = NeoSyncSableCompat.projectNeighborCenter(
                entity.level(),
                this.worldPosition,
                state.getValue(ShellStorageBlock.FACING).getOpposite()
        );

        boolean isInside = BlockPosUtil.isEntityInside(entity, shellCenter);
        NeoSyncDebug.info(
                "storage",
                "client collision at {} player={} local={} screenClear={} entityState={} inside={} center={} outside={}",
                NeoSyncDebug.describe(entity.level(), this.worldPosition),
                player.getName().getString(),
                isLocalPlayer,
                hasNoScreen,
                this.entityState,
                isInside,
                shellCenter,
                shellOutside
        );

        if (this.entityState == EntityState.NONE) {
            if (!isInside) {
                return;
            }

            PlayerSyncEvents.ShellSelectionFailureReason failureReason = isLocalPlayer
                    ? PlayerSyncEvents.ALLOW_SHELL_SELECTION.invoker().allowShellSelection(player, this)
                    : null;

            this.entityState = failureReason != null ? EntityState.CHILLING : EntityState.ENTERING;

            if (failureReason != null) {
                NeoSyncDebug.warn("storage", "client shell selection denied at {} reason={}", NeoSyncDebug.describe(entity.level(), this.worldPosition), failureReason.toText().getString());
                player.displayClientMessage(failureReason.toText(), true);
            }
        } else if (this.entityState != EntityState.CHILLING && hasNoScreen) {
            BlockPosUtil.moveEntity(
                    entity,
                    shellCenter,
                    shellOutside,
                    this.entityState == EntityState.ENTERING
            );
        }

        if (this.entityState == EntityState.ENTERING
                && isLocalPlayer
                && hasNoClientScreen()
                && BlockPosUtil.isEntityInside(entity, shellCenter)) {
            NeoSyncDebug.info("storage", "opening shell selector for storage at {}", NeoSyncDebug.describe(entity.level(), this.worldPosition));
            openShellSelectorClient(
                    this.worldPosition,
                    () -> this.entityState = EntityState.LEAVING,
                    () -> this.entityState = EntityState.CHILLING
            );
        }
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean isLocalClientPlayer(Entity entity) {
        Object result = invokeClientHook("isLocalPlayer", new Class[]{Entity.class}, entity);
        return result instanceof Boolean value && value;
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean hasNoClientScreen() {
        Object result = invokeClientHook("hasNoScreen", new Class[0]);
        return result instanceof Boolean value && value;
    }

    @OnlyIn(Dist.CLIENT)
    @Nullable
    private static Entity getLocalClientPlayer() {
        Object result = invokeClientHook("getLocalPlayer", new Class[0]);
        return result instanceof Entity entity ? entity : null;
    }

    @OnlyIn(Dist.CLIENT)
    private static void openShellSelectorClient(BlockPos currentContainerPos, Runnable onCloseCallback, Runnable onRemovedCallback) {
        invokeClientHook(
                "openShellSelector",
                new Class[]{BlockPos.class, Runnable.class, Runnable.class},
                currentContainerPos,
                onCloseCallback,
                onRemovedCallback
        );
    }

    @OnlyIn(Dist.CLIENT)
    private static Object invokeClientHook(String methodName, Class[] parameterTypes, Object... args) {
        try {
            Class<?> hooksClass = Class.forName("com.breakinblocks.neosync.client.block.entity.ShellStorageClientHooks");
            Method method = hooksClass.getMethod(methodName, parameterTypes);
            return method.invoke(null, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException("Failed to invoke NeoSync client shell storage hook " + methodName, e);
        }
    }

    @Override
    public InteractionResult onUse(Level world, BlockPos pos, Player player, InteractionHand hand) {
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        ItemStack stack = player.getItemInHand(hand);
        Item item = stack.getItem();

        if (stack.getCount() > 0 && item instanceof DyeItem dye) {
            NeoSyncDebug.info("storage", "dyed storage at {} player={} color={}", NeoSyncDebug.describe(world, pos), player.getName().getString(), dye.getDyeColor());
            stack.shrink(1);
            this.color = dye.getDyeColor();
            this.forceShellStateUpdate("storage-dye");
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    public int receiveEnergy(int maxReceive, boolean simulate) {
        if (SyncConfig.getInstance().shellStorageConsumption() == 0) {
            return 0;
        }

        ShellStorageBlockEntity bottom = (ShellStorageBlockEntity) this.getBottomPart().orElse(null);

        if (bottom == null) {
            return 0;
        }

        int capacity = bottom.getMaxEnergyStored();
        int maxEnergy = Mth.clamp(capacity - bottom.storedEnergy, 0, capacity);
        int inserted = Mth.clamp(maxReceive, 0, maxEnergy);

        if (!simulate && inserted > 0) {
            int oldEnergy = bottom.storedEnergy;
            bottom.storedEnergy += inserted;
            NeoSyncDebug.info("storage", "receiveEnergy at {} inserted={} energy={} -> {}", NeoSyncDebug.describe(bottom.level, bottom.worldPosition), inserted, oldEnergy, bottom.storedEnergy);
            bottom.setChanged();
            bottom.sync("storage-energy-receive");
        }

        return inserted;
    }

    @OnlyIn(Dist.CLIENT)
    private static boolean isNearStorageEntry(Level world, BlockPos pos, Entity entity) {
        Vec3 shellCenter = NeoSyncSableCompat.projectBlockCenter(world, pos);
        return NeoSyncSableCompat.distanceSquared(world, entity.position(), shellCenter) < 0.25D * 0.25D;
    }

    @OnlyIn(Dist.CLIENT)
    private boolean canAcceptPlayerClient(BlockState state) {
        boolean empty = this.getBottomPart()
                .map(part -> part.getShellState() == null)
                .orElse(this.getShellState() == null);

        if (!empty) {
            return false;
        }

        if (!ShellStorageBlock.isOpen(state)) {
            NeoSyncDebug.warn("storage", "client storage accepts because empty, but local visual state is closed/stale at {} state={}", NeoSyncDebug.describe(this.level, this.worldPosition), state);
        }

        return true;
    }

    @Override
    public int extractEnergy(int maxExtract, boolean simulate) {
        return 0;
    }

    @Override
    public int getEnergyStored() {
        return this.getBottomPart().map(x -> ((ShellStorageBlockEntity) x).storedEnergy).orElse(0);
    }

    @Override
    public int getMaxEnergyStored() {
        return Math.toIntExact(SyncConfig.getInstance().shellStorageConsumption() == 0 ? 0 : SyncConfig.getInstance().shellStorageCapacity());
    }

    @Override
    public boolean canExtract() {
        return false;
    }

    @Override
    public boolean canReceive() {
        return SyncConfig.getInstance().shellStorageConsumption() != 0;
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        nbt.putInt("storedEnergy", this.storedEnergy);
        nbt.putInt("ticksWithoutPower", this.ticksWithoutPower);
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        this.storedEnergy = nbt.getInt("storedEnergy");
        this.ticksWithoutPower = nbt.getInt("ticksWithoutPower");
    }

    private enum EntityState {
        NONE,
        ENTERING,
        CHILLING,
        LEAVING
    }
}
