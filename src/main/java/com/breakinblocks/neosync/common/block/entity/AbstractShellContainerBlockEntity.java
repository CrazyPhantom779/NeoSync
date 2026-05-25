package com.breakinblocks.neosync.common.block.entity;

import com.breakinblocks.neosync.api.networking.ShellContainerStatePacket;
import com.breakinblocks.neosync.api.networking.ShellDestroyedPacket;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.api.shell.ShellStateContainer;
import com.breakinblocks.neosync.api.shell.ShellStateManager;
import com.breakinblocks.neosync.common.block.AbstractShellContainerBlock;
import com.breakinblocks.neosync.common.item.SimpleInventory;
import com.breakinblocks.neosync.common.utils.ItemUtil;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.breakinblocks.neosync.common.utils.nbt.SyncRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.Optional;

public abstract class AbstractShellContainerBlockEntity extends BlockEntity
    implements ShellStateContainer, DoubleBlockEntity, TickableBlockEntity, Container {
    protected final BooleanAnimator doorAnimator;
    protected ShellState shell;
    protected DyeColor color;
    protected int progressComparatorOutput;
    protected int inventoryComparatorOutput;

    private AbstractShellContainerBlockEntity bottomPart;
    private ShellState syncedShell;
    private BlockPos syncedShellPos;
    private DyeColor syncedShellColor;
    private float syncedShellProgress;
    private DyeColor syncedColor;
    private boolean inventoryDirty;
    private boolean visibleInventoryDirty;

    public AbstractShellContainerBlockEntity(@NotNull BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.doorAnimator = new BooleanAnimator(AbstractShellContainerBlock.isOpen(state));
        NeoSyncDebug.info(
            "container-be",
            "created {} at {} state={}",
            this.getClass().getSimpleName(),
            pos.toShortString(),
            state
        );
    }

    @Override
    public void setShellState(@Nullable ShellState shell) {
        AbstractShellContainerBlockEntity target = this.getBottomPart().orElse(this);
        if (target != this) {
            NeoSyncDebug.info(
                "container-be",
                "redirecting setShellState from {} to bottom {} incoming={}",
                NeoSyncDebug.describe(this.level, this.worldPosition),
                NeoSyncDebug.describe(target.level, target.worldPosition),
                NeoSyncDebug.describeShell(shell)
            );
            target.setShellState(shell);
            return;
        }

        NeoSyncDebug.info(
            "container-be",
            "setShellState at {} old={} new={}",
            NeoSyncDebug.describe(this.level, this.worldPosition),
            NeoSyncDebug.describeShell(this.shell),
            NeoSyncDebug.describeShell(shell)
        );
        this.shell = shell;
        if (shell != null && this.worldPosition != null) {
            shell.setPos(this.worldPosition);
        }
        this.forceShellStateUpdate("setShellState");
    }

    public void applyClientVisualState(@Nullable ShellState shell, @Nullable DyeColor color, String reason) {
        AbstractShellContainerBlockEntity target = this.getBottomPart().orElse(this);
        if (target != this) {
            target.applyClientVisualState(shell, color, reason);
            return;
        }

        target.shell = shell;
        if (target.shell != null && target.worldPosition != null) {
            target.shell.setPos(target.worldPosition);
        }
        target.color = color;
        target.setChanged();
        NeoSyncDebug.info(
            "container-be",
            "applyClientVisualState reason={} at {} shell={} color={}",
            reason,
            NeoSyncDebug.describe(target.level, target.worldPosition),
            NeoSyncDebug.describeShell(target.shell),
            target.color
        );
    }

    @Override
    @Nullable
    public ShellState getShellState() {
        AbstractShellContainerBlockEntity bottom = this.getBottomPart().orElse(this);
        if (bottom != this) {
            return bottom.getShellState();
        }
        return this.shell;
    }

    @Override
    @Nullable
    public DyeColor getColor() {
        return this.color;
    }

    public int getProgressComparatorOutput() {
        return this.getBottomPart().map(x -> x.progressComparatorOutput).orElse(0);
    }

    public int getInventoryComparatorOutput() {
        return this.getBottomPart().map(x -> x.inventoryComparatorOutput).orElse(0);
    }

    protected ShellStateManager getShellStateManager() {
        return (ShellStateManager) Objects.requireNonNull(this.level).getServer();
    }

    protected Optional<AbstractShellContainerBlockEntity> getBottomPart() {
        if (this.level == null || this.worldPosition == null) {
            return Optional.ofNullable(this.bottomPart);
        }

        BlockState liveState = this.level.getBlockState(this.worldPosition);
        boolean liveIsBottom = liveState.hasProperty(AbstractShellContainerBlock.HALF)
            && AbstractShellContainerBlock.isBottom(liveState);
        if (liveIsBottom) {
            this.bottomPart = this;
            return Optional.of(this);
        }

        if (this.bottomPart == null || this.bottomPart.isRemoved()) {
            BlockEntity maybeBottom = this.level.getBlockEntity(this.worldPosition.below());
            this.bottomPart = maybeBottom instanceof AbstractShellContainerBlockEntity container ? container : null;
        }
        return Optional.ofNullable(this.bottomPart);
    }

    @Override
    public void onServerTick(Level world, BlockPos pos, BlockState state) {
        this.checkShellState(world, pos, state, "serverTick");
    }

    protected void forceShellStateUpdate(String reason) {
        if (this.level == null || this.level.isClientSide || this.worldPosition == null) {
            return;
        }
        BlockState liveState = this.level.getBlockState(this.worldPosition);
        NeoSyncDebug.info(
            "container-be",
            "forceShellStateUpdate reason={} at {} liveState={} shell={}",
            reason,
            NeoSyncDebug.describe(this.level, this.worldPosition),
            liveState,
            NeoSyncDebug.describeShell(this.shell)
        );
        this.checkShellState(this.level, this.worldPosition, liveState, reason);
        this.setChanged();
        this.sync(reason);
    }

    private void checkShellState(Level world, BlockPos pos, BlockState state, String reason) {
        if (world == null || pos == null) {
            return;
        }

        if (this.shell != null && this.shell.getColor() != this.color) {
            NeoSyncDebug.info(
                "container-be",
                "{} color sync at {} shell={} oldColor={} newColor={}",
                reason,
                NeoSyncDebug.describe(world, pos),
                NeoSyncDebug.describeShell(this.shell),
                this.shell.getColor(),
                this.color
            );
            this.shell.setColor(this.color);
        }

        if (this.requiresSync()) {
            NeoSyncDebug.info(
                "container-be",
                "{} requires sync at {} shell={} syncedShell={} visibleInventoryDirty={} inventoryDirty={}",
                reason,
                NeoSyncDebug.describe(world, pos),
                NeoSyncDebug.describeShell(this.shell),
                NeoSyncDebug.describeShell(this.syncedShell),
                this.visibleInventoryDirty,
                this.inventoryDirty
            );
            this.updateShell(this.shell != this.syncedShell, !this.visibleInventoryDirty, reason);
            this.updateComparatorOutput(world, pos, state);
            this.syncedShellPos = this.shell == null ? null : this.shell.getPos();
            this.syncedShellColor = this.shell == null ? null : this.shell.getColor();
            this.syncedShellProgress = this.shell == null ? -1 : this.shell.getProgress();
            this.syncedShell = this.shell;
            this.syncedColor = this.color;
            this.inventoryDirty = false;
            this.visibleInventoryDirty = false;
            this.sync(reason + ":requiresSync");
            this.setChanged();
        }

        if (this.inventoryDirty) {
            NeoSyncDebug.info(
                "container-be",
                "{} inventory dirty update at {} shell={}",
                reason,
                NeoSyncDebug.describe(world, pos),
                NeoSyncDebug.describeShell(this.shell)
            );
            this.updateComparatorOutput(world, pos, state);
            this.inventoryDirty = false;
            this.setChanged();
            this.sync(reason + ":inventoryDirty");
        }
    }

    private boolean requiresSync() {
        return this.visibleInventoryDirty
            || this.syncedShell != this.shell
            || this.syncedColor != this.color
            || this.shell != null && (
            !Objects.equals(this.shell.getPos(), this.syncedShellPos)
                || !Objects.equals(this.shell.getColor(), this.syncedShellColor)
                || this.shell.getProgress() != this.syncedShellProgress
        );
    }

    private void updateShell(boolean isNew, boolean partialUpdate, String reason) {
        if (this.level == null || this.level.getServer() == null) {
            return;
        }

        ShellStateManager shellManager = this.getShellStateManager();
        NeoSyncDebug.info(
            "container-be",
            "updateShell reason={} isNew={} partialUpdate={} old={} new={}",
            reason,
            isNew,
            partialUpdate,
            NeoSyncDebug.describeShell(this.syncedShell),
            NeoSyncDebug.describeShell(this.shell)
        );

        if (isNew) {
            if (this.syncedShell != null) {
                shellManager.remove(this.syncedShell);
            }
            if (this.shell != null) {
                shellManager.add(this.shell);
            }
        } else if (this.shell != null) {
            if (partialUpdate) {
                shellManager.update(this.shell);
            } else {
                shellManager.add(this.shell);
            }
        }
    }

    private void updateComparatorOutput(Level world, BlockPos pos, BlockState state) {
        int currentProgressOutput = this.shell == null ? 0 : Mth.clamp((int) (this.shell.getProgress() * 15), 1, 15);
        int currentInventoryOutput =
            this.shell == null ? 0 : AbstractContainerMenu.getRedstoneSignalFromContainer(this.shell.getInventory());

        if (!state.hasProperty(AbstractShellContainerBlock.OUTPUT)
            || !state.hasProperty(AbstractShellContainerBlock.HALF)) {
            NeoSyncDebug.warn(
                "container-be",
                "updateComparatorOutput skipped at {} because state lacks properties: {}",
                NeoSyncDebug.describe(world, pos),
                state
            );
            return;
        }

        BlockPos topPartPos = pos.relative(AbstractShellContainerBlock.getDirectionTowardsAnotherPart(state));
        BlockState topPartState = world.getBlockState(topPartPos);

        if (this.progressComparatorOutput != currentProgressOutput) {
            this.progressComparatorOutput = currentProgressOutput;
            if (state.getValue(AbstractShellContainerBlock.OUTPUT)
                == AbstractShellContainerBlock.ComparatorOutputType.PROGRESS) {
                world.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
            if (topPartState.hasProperty(AbstractShellContainerBlock.OUTPUT)
                && topPartState.getValue(AbstractShellContainerBlock.OUTPUT)
                == AbstractShellContainerBlock.ComparatorOutputType.PROGRESS) {
                world.updateNeighbourForOutputSignal(topPartPos, topPartState.getBlock());
            }
        }

        if (this.inventoryComparatorOutput != currentInventoryOutput) {
            this.inventoryComparatorOutput = currentInventoryOutput;
            if (state.getValue(AbstractShellContainerBlock.OUTPUT)
                == AbstractShellContainerBlock.ComparatorOutputType.INVENTORY) {
                world.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
            if (topPartState.hasProperty(AbstractShellContainerBlock.OUTPUT)
                && topPartState.getValue(AbstractShellContainerBlock.OUTPUT)
                == AbstractShellContainerBlock.ComparatorOutputType.INVENTORY) {
                world.updateNeighbourForOutputSignal(topPartPos, topPartState.getBlock());
            }
        }
    }

    @Override
    public void onClientTick(Level world, BlockPos pos, BlockState state) {
        this.doorAnimator.setValue(AbstractShellContainerBlock.isOpen(state));
        this.doorAnimator.step();
    }

    public void onBreak(Level world, BlockPos pos) {
        NeoSyncDebug.info(
            "container-be",
            "onBreak at {} shell={}",
            NeoSyncDebug.describe(world, pos),
            NeoSyncDebug.describeShell(this.shell)
        );
        if (this.shell != null && world instanceof ServerLevel serverWorld) {
            this.getShellStateManager().remove(this.shell);
            this.destroyShell(serverWorld, pos);
        }
    }

    protected void destroyShell(ServerLevel world, BlockPos pos) {
        if (this.shell != null) {
            NeoSyncDebug.info(
                "container-be",
                "destroyShell at {} shell={}",
                NeoSyncDebug.describe(world, pos),
                NeoSyncDebug.describeShell(this.shell)
            );
            this.shell.drop(world, pos);
            new ShellDestroyedPacket(pos).send(world, pos, 32);
            this.setShellState(null);
            this.setChanged();
            this.sync("destroyShell");
        }
    }

    public abstract InteractionResult onUse(Level world, BlockPos pos, Player player, InteractionHand hand);

    @OnlyIn(Dist.CLIENT)
    public float getDoorOpenProgress(float tickDelta) {
        return this.getBottomPart().map(x -> x.doorAnimator.getProgress(tickDelta)).orElse(0f);
    }

    @Override
    public DoubleBlockHalf getBlockType(BlockState state) {
        return AbstractShellContainerBlock.getShellContainerHalf(state);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag nbt = super.getUpdateTag(registries);
        this.saveAdditional(nbt, registries);
        return nbt;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void saveAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.saveAdditional(nbt, registries);
        SyncRegistries.push(registries);
        try {
            if (this.shell != null) {
                nbt.put("shell", this.shell.writeNbt(new CompoundTag()));
            }
        } finally {
            SyncRegistries.pop();
        }
        nbt.putInt("color", this.color == null ? -1 : this.color.getId());
        NeoSyncDebug.info(
            "container-be",
            "saveAdditional at {} shell={} color={}",
            NeoSyncDebug.describe(this.level, this.worldPosition),
            NeoSyncDebug.describeShell(this.shell),
            this.color
        );
    }

    @Override
    protected void loadAdditional(CompoundTag nbt, HolderLookup.Provider registries) {
        super.loadAdditional(nbt, registries);
        SyncRegistries.push(registries);
        try {
            this.shell = nbt.contains("shell") ? ShellState.fromNbt(nbt.getCompound("shell")) : null;
        } finally {
            SyncRegistries.pop();
        }

        if (this.shell != null && this.worldPosition != null) {
            this.shell.setPos(this.worldPosition);
        }

        int colorId = nbt.contains("color", Tag.TAG_INT) ? nbt.getInt("color") : -1;
        this.color = colorId == -1 ? null : DyeColor.byId(colorId);
        NeoSyncDebug.info(
            "container-be",
            "loadAdditional at {} shell={} color={}",
            NeoSyncDebug.describe(this.level, this.worldPosition),
            NeoSyncDebug.describeShell(this.shell),
            this.color
        );
    }

    private static int reorderSlotIndex(int slot, SimpleInventory inventory) {
        int mainSize = inventory.main.size();
        int armorSize = inventory.armor.size();
        int offHandSize = inventory.offHand.size();
        if (slot >= 0 && slot < armorSize) {
            return slot + mainSize;
        }
        if (slot >= armorSize && slot < (armorSize + offHandSize)) {
            return slot + mainSize;
        }
        return slot - armorSize - offHandSize;
    }

    private static boolean isVisibleSlot(int slot, SimpleInventory inventory) {
        int armorSize = inventory.armor.size();
        int offHandSize = inventory.offHand.size();
        return slot >= 0 && slot <= (armorSize + offHandSize);
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack stack) {
        AbstractShellContainerBlockEntity bottom = this.getBottomPart().orElse(null);
        if (bottom == null || bottom.shell == null) {
            return false;
        }

        SimpleInventory inventory = bottom.shell.getInventory();
        int armorSize = inventory.armor.size();
        boolean isArmorSlot = slot >= 0 && slot < armorSize;
        if (isArmorSlot) {
            EquipmentSlot equipmentSlot = ItemUtil.getPreferredEquipmentSlot(stack);
            return ItemUtil.isArmor(stack)
                && equipmentSlot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                && slot == equipmentSlot.getIndex();
        }

        boolean isOffHandSlot = slot >= armorSize && slot < (armorSize + inventory.offHand.size());
        if (isOffHandSlot) {
            return ItemUtil.getPreferredEquipmentSlot(stack) == EquipmentSlot.OFFHAND
                || inventory.main.stream().noneMatch(x ->
                x.isEmpty()
                    || (x.getCount() + stack.getCount()) <= x.getMaxStackSize()
                    && ItemStack.isSameItemSameComponents(x, stack)
            );
        }

        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return this.getBottomPart()
            .filter(x -> x.shell != null)
            .map(x -> x.shell.getInventory().getItem(reorderSlotIndex(slot, x.shell.getInventory())))
            .orElse(ItemStack.EMPTY);
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        AbstractShellContainerBlockEntity bottom = this.getBottomPart().orElse(null);
        if (bottom == null || bottom.shell == null || bottom.shell.getProgress() < ShellState.PROGRESS_DONE) {
            return;
        }

        SimpleInventory inventory = bottom.shell.getInventory();
        inventory.setItem(reorderSlotIndex(slot, inventory), stack);
        bottom.inventoryDirty = true;
        bottom.visibleInventoryDirty |= isVisibleSlot(slot, inventory);
        bottom.forceShellStateUpdate("setItem");
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        AbstractShellContainerBlockEntity bottom = this.getBottomPart().orElse(null);
        if (bottom == null || bottom.shell == null) {
            return ItemStack.EMPTY;
        }

        SimpleInventory inventory = bottom.shell.getInventory();
        ItemStack removed = inventory.removeItem(reorderSlotIndex(slot, inventory), amount);
        bottom.inventoryDirty = true;
        bottom.visibleInventoryDirty |= !removed.isEmpty() && isVisibleSlot(slot, inventory);
        bottom.forceShellStateUpdate("removeItem");
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        AbstractShellContainerBlockEntity bottom = this.getBottomPart().orElse(null);
        if (bottom == null || bottom.shell == null) {
            return ItemStack.EMPTY;
        }

        SimpleInventory inventory = bottom.shell.getInventory();
        ItemStack removed = inventory.removeItemNoUpdate(reorderSlotIndex(slot, inventory));
        bottom.inventoryDirty = true;
        bottom.visibleInventoryDirty |= !removed.isEmpty() && isVisibleSlot(slot, inventory);
        bottom.forceShellStateUpdate("removeItemNoUpdate");
        return removed;
    }

    @Override
    public void clearContent() {
        AbstractShellContainerBlockEntity bottom = this.getBottomPart().orElse(null);
        if (bottom == null || bottom.shell == null) {
            return;
        }

        bottom.shell.getInventory().clearContent();
        bottom.inventoryDirty = true;
        bottom.visibleInventoryDirty = true;
        bottom.forceShellStateUpdate("clearContent");
    }

    @Override
    public int getContainerSize() {
        return this.getBottomPart()
            .map(x -> x.shell == null || x.shell.getProgress() < ShellState.PROGRESS_DONE
                ? 0
                : x.shell.getInventory().getContainerSize())
            .orElse(0);
    }

    @Override
    public boolean isEmpty() {
        return this.getBottomPart().map(x -> x.shell == null || x.shell.getInventory().isEmpty()).orElse(true);
    }

    @Override
    public boolean stillValid(Player player) {
        return false;
    }

    protected void sync(String reason) {
        if (!(this.level instanceof ServerLevel serverWorld)) {
            return;
        }

        NeoSyncDebug.info(
            "container-be",
            "sync reason={} at {} shell={} color={}",
            reason,
            NeoSyncDebug.describe(serverWorld, this.worldPosition),
            NeoSyncDebug.describeShell(this.shell),
            this.color
        );
        syncBlockEntity(serverWorld, this.worldPosition, reason + ":self");

        BlockState state = this.getBlockState();
        if (state.hasProperty(AbstractShellContainerBlock.HALF)) {
            BlockPos otherPartPos = this.worldPosition.relative(AbstractShellContainerBlock.getDirectionTowardsAnotherPart(state));
            syncBlockEntity(serverWorld, otherPartPos, reason + ":other");
        } else {
            NeoSyncDebug.warn(
                "container-be",
                "sync reason={} at {} could not sync other half because state lacks HALF: {}",
                reason,
                NeoSyncDebug.describe(serverWorld, this.worldPosition),
                state
            );
        }

        new ShellContainerStatePacket(this.worldPosition, this.shell, this.color).send(serverWorld, this.worldPosition, 96.0D);
    }

    protected void sync() {
        this.sync("legacy-call");
    }

    private static void syncBlockEntity(ServerLevel world, BlockPos pos, String reason) {
        BlockState state = world.getBlockState(pos);
        BlockEntity blockEntity = world.getBlockEntity(pos);
        NeoSyncDebug.info(
            "container-be",
            "syncBlockEntity reason={} target={} state={} be={}",
            reason,
            NeoSyncDebug.describe(world, pos),
            state,
            blockEntity == null ? "null" : blockEntity.getClass().getSimpleName()
        );
        if (blockEntity != null) {
            blockEntity.setChanged();
        }
        world.getChunkSource().blockChanged(pos);
        world.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
    }
}
