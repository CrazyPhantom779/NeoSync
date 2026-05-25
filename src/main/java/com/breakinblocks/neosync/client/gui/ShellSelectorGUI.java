package com.breakinblocks.neosync.client.gui;

import com.breakinblocks.neosync.api.shell.ClientShell;
import com.breakinblocks.neosync.api.shell.ShellState;
import com.breakinblocks.neosync.client.gl.MSAAFramebuffer;
import com.breakinblocks.neosync.client.gui.hud.HudController;
import com.breakinblocks.neosync.client.gui.widget.ArrowButtonWidget;
import com.breakinblocks.neosync.client.gui.widget.CrossButtonWidget;
import com.breakinblocks.neosync.client.gui.widget.PageDisplayWidget;
import com.breakinblocks.neosync.client.gui.widget.ShellSelectorButtonWidget;
import com.breakinblocks.neosync.client.utils.render.ColorUtil;
import com.breakinblocks.neosync.common.utils.IdentifierUtil;
import com.breakinblocks.neosync.common.utils.NeoSyncDebug;
import com.breakinblocks.neosync.common.utils.math.Radians;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("FieldCanBeLocal")
@OnlyIn(Dist.CLIENT)
public class ShellSelectorGUI extends Screen {
    private static final int MAX_SLOTS = 8;
    private static final int EMPTY_REFRESH_RETRIES = 20;
    private static final double MENU_RADIUS = 0.3F;
    private static final int BACKGROUND_COLOR = ColorUtil.fromDyeColor(DyeColor.BLACK, 0.3F);
    private static final Component TITLE = Component.translatable("gui.neosync.default.cross_button.title");
    private static final Collection<Component> ARROW_TITLES = List.of(
        Component.translatable("gui.neosync.shell_selector.up.title"),
        Component.translatable("gui.neosync.shell_selector.right.title"),
        Component.translatable("gui.neosync.shell_selector.down.title"),
        Component.translatable("gui.neosync.shell_selector.left.title")
    );

    private final Runnable onCloseCallback;
    private final Runnable onRemovedCallback;
    @Nullable
    private final BlockPos currentContainerPos;

    private boolean wasClosed;
    private int emptyRefreshAttempts;
    private int lastVisibleShellCount = -1;
    private List<ShellSelectorButtonWidget> shellButtons;
    private List<ArrowButtonWidget> arrowButtons;
    private CrossButtonWidget crossButton;
    private PageDisplayWidget<ResourceLocation, ShellState> pageDisplay;

    public ShellSelectorGUI(Runnable onCloseCallback, Runnable onRemovedCallback) {
        this(null, onCloseCallback, onRemovedCallback);
    }

    public ShellSelectorGUI(
        @Nullable BlockPos currentContainerPos,
        Runnable onCloseCallback,
        Runnable onRemovedCallback
    ) {
        super(TITLE);
        this.currentContainerPos = currentContainerPos;
        this.onCloseCallback = onCloseCallback;
        this.onRemovedCallback = onRemovedCallback;
    }

    @Override
    public void init() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            NeoSyncDebug.warn("selector", "init closed because local player was null");
            this.onClose();
            return;
        }

        ResourceLocation selectedWorld = player.level().dimension().location();
        List<ShellState> shellStates = collectVisibleShellStates(player);
        this.lastVisibleShellCount = shellStates.size();

        NeoSyncDebug.info(
            "selector",
            "init currentContainer={} visibleStates={} attempts={}",
            this.currentContainerPos,
            shellStates.size(),
            this.emptyRefreshAttempts
        );

        this.wasClosed = false;
        this.clearWidgets();

        this.arrowButtons = createArrowButtons(
            this.width,
            this.height,
            ARROW_TITLES,
            List.of(this::previousSection, this::nextPage, this::nextSection, this::previousPage)
        );
        this.crossButton = createCrossButton(this.width, this.height, this::onClose);
        this.pageDisplay = createPageDisplay(
            this.width,
            this.height,
            shellStates.stream(),
            selectedWorld,
            MAX_SLOTS,
            this::onPageChange
        );

        Stream.concat(this.arrowButtons.stream(), Stream.of(this.crossButton, this.pageDisplay))
            .forEach(this::addRenderableWidget);
        HudController.hide();
    }

    @Override
    public void tick() {
        super.tick();

        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }

        if (!player.isAlive()) {
            NeoSyncDebug.info("selector", "closing selector because local player is no longer alive");
            this.onClose();
            return;
        }

        List<ShellState> shellStates = collectVisibleShellStates(player);
        int visibleShellCount = shellStates.size();

        if (visibleShellCount != this.lastVisibleShellCount) {
            NeoSyncDebug.info(
                "selector",
                "shell count changed while open {} -> {}, refreshing",
                this.lastVisibleShellCount,
                visibleShellCount
            );
            this.emptyRefreshAttempts = 0;
            this.refreshFromNetwork();
            return;
        }

        if (visibleShellCount == 0 && this.emptyRefreshAttempts < EMPTY_REFRESH_RETRIES) {
            this.emptyRefreshAttempts++;
            if ((this.emptyRefreshAttempts % 2) == 0) {
                NeoSyncDebug.info(
                    "selector",
                    "refresh retry {} because selector opened empty currentContainer={}",
                    this.emptyRefreshAttempts,
                    this.currentContainerPos
                );
                this.refreshFromNetwork();
            }
        }
    }

    public void refreshFromNetwork() {
        NeoSyncDebug.info("selector", "refreshFromNetwork currentContainer={}", this.currentContainerPos);
        this.init();
    }

    private List<ShellState> collectVisibleShellStates(LocalPlayer player) {
        ClientShell clientShell = (ClientShell) player;
        UUID currentShellUuid = clientShell.neosync$getCurrentShellUuid();
        List<ShellState> allShellStates = clientShell.getAvailableShellStates().collect(Collectors.toList());
        List<ShellState> shellStates = allShellStates.stream()
            .filter(shellState -> !isCurrentContainerOption(currentShellUuid, shellState))
            .collect(Collectors.toList());

        NeoSyncDebug.info(
            "selector",
            "collectVisibleShellStates currentContainer={} currentShellUuid={} allStates={} visibleStates={}",
            this.currentContainerPos,
            currentShellUuid,
            allShellStates.size(),
            shellStates.size()
        );

        return shellStates;
    }

    private static List<ShellSelectorButtonWidget> createShellButtons(int screenWidth, int screenHeight, int count) {
        final double hollowR = MENU_RADIUS * 0.6;
        final double borderWidth = 0.0033;
        final double sectorSpacing = 0.01;

        double cX = screenWidth / 2.0;
        double cY = screenHeight / 2.0;
        double majorR = screenHeight * MENU_RADIUS;
        double minorR = screenHeight * hollowR;
        double spacing = count > 1 ? sectorSpacing : 0;
        double sector = Radians.R_2_PI / count - spacing;
        double strokeWidth = screenHeight * borderWidth;
        double pos = -sector / (2 << (count % 2));

        List<ShellSelectorButtonWidget> buttons = new ArrayList<>();
        for (int i = 0; i < count; ++i) {
            ShellSelectorButtonWidget button =
                new ShellSelectorButtonWidget(cX, cY, majorR, minorR, strokeWidth, pos, pos + sector);
            pos += sector + spacing;
            buttons.add(button);
        }
        return buttons;
    }

    private static PageDisplayWidget<ResourceLocation, ShellState> createPageDisplay(
        int screenWidth,
        int screenHeight,
        Stream<ShellState> data,
        ResourceLocation defaultPage,
        int entriesPerPage,
        BiConsumer<PageDisplayWidget<ResourceLocation, ShellState>, PageDisplayWidget<ResourceLocation, ShellState>.Page> onChange
    ) {
        final float fontHeight = 1 / 30F;
        float cX = screenWidth / 2F;
        float cY = screenHeight / 2F;
        float scale = screenHeight * fontHeight / Minecraft.getInstance().font.lineHeight;
        return new PageDisplayWidget<>(
            cX,
            cY,
            scale,
            data,
            ShellState::getWorld,
            IdentifierUtil::prettifyAsText,
            defaultPage,
            entriesPerPage,
            onChange
        );
    }

    private static List<ArrowButtonWidget> createArrowButtons(
        int screenWidth,
        int screenHeight,
        Iterable<Component> arrowTitles,
        Iterable<Runnable> arrowActions
    ) {
        final float arrowHeight = 2 / 75F;
        final float arrowWidth = 57 / 32F;
        final float arrowThickness = 1 / 240F;
        final float arrowSpacing = 1 / 14F;

        float cX = screenWidth / 2F;
        float cY = screenHeight / 2F;
        float r = screenHeight * (float) MENU_RADIUS * (1F + arrowSpacing);
        float height = screenHeight * arrowHeight;
        float width = height * arrowWidth;
        float thickness = screenHeight * arrowThickness;

        Iterator<Runnable> actions = arrowActions.iterator();
        Iterator<Component> descriptions = arrowTitles.iterator();
        List<ArrowButtonWidget> arrowButtons = new ArrayList<>();
        for (ArrowButtonWidget.ArrowType arrowType : ArrowButtonWidget.ArrowType.values()) {
            float x;
            float y;
            if (arrowType.isVertical()) {
                x = screenWidth / 2F - width / 2F;
                y = cY + r * (arrowType.isDown() ? 1 : -1) + (arrowType.isDown() ? 0 : -height);
            } else {
                x = cX + r * (arrowType.isRight() ? 1 : -1) + (arrowType.isRight() ? 0 : -height);
                y = screenHeight / 2F - width / 2F;
            }
            arrowButtons.add(
                new ArrowButtonWidget(x, y, width, height, arrowType, thickness, descriptions.next(), actions.next())
            );
        }
        return arrowButtons;
    }

    private static CrossButtonWidget createCrossButton(int screenWidth, int screenHeight, Runnable onClose) {
        final float crossMargin = 1 / 15F;
        final float crossWidth = 2 / 75F;
        final float crossThickness = 1 / 240F;

        float width = screenHeight * crossWidth;
        float y = screenHeight * crossMargin;
        float x = screenWidth - y - width;
        float thickness = screenHeight * crossThickness;
        return new CrossButtonWidget(x, y, width, width, thickness, onClose);
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (Objects.requireNonNull(this.minecraft).level != null) {
            guiGraphics.fillGradient(0, 0, this.width, this.height, BACKGROUND_COLOR, BACKGROUND_COLOR);
        } else {
            super.renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float delta) {
        this.renderBackground(guiGraphics, mouseX, mouseY, delta);
        MSAAFramebuffer.use(MSAAFramebuffer.MAX_SAMPLES, () -> super.render(guiGraphics, mouseX, mouseY, delta));
        this.renderTooltips(guiGraphics, mouseX, mouseY);
    }

    protected void renderTooltips(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        for (GuiEventListener child : this.children()) {
            if (child instanceof NarratableEntry narratableEntry
                && narratableEntry.narrationPriority() != NarratableEntry.NarrationPriority.NONE) {
                Component tooltipText = child instanceof TooltipProvider tooltipProvider ? tooltipProvider.getTooltip() : null;
                if (tooltipText != null) {
                    guiGraphics.renderTooltip(font, tooltipText, mouseX, mouseY);
                }
                return;
            }
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        for (GuiEventListener child : this.children()) {
            if (child.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void onPageChange(
        PageDisplayWidget<ResourceLocation, ShellState> pageDisplay,
        PageDisplayWidget<ResourceLocation, ShellState>.Page page
    ) {
        for (ArrowButtonWidget arrow : this.arrowButtons) {
            arrow.visible = arrow.type.isVertical() ? pageDisplay.hasMoreSections() : pageDisplay.hasMorePages();
        }

        if (this.shellButtons != null) {
            this.shellButtons.forEach(this::removeWidget);
        }

        List<ShellState> content = page.content;
        NeoSyncDebug.info(
            "selector",
            "page change contentSize={} currentContainer={}",
            content.size(),
            this.currentContainerPos
        );

        this.shellButtons = createShellButtons(this.width, this.height, Math.max(content.size(), 1));
        this.shellButtons.forEach(this::addRenderableWidget);

        for (int i = 0; i < content.size(); ++i) {
            ShellSelectorButtonWidget button = this.shellButtons.get(i);
            button.shell = content.get(i);
            button.currentContainerPos = this.currentContainerPos;
        }
    }

    private boolean isCurrentContainerOption(@Nullable UUID currentShellUuid, ShellState shellState) {
        if (this.currentContainerPos != null && isSameStorageBlock(shellState.getPos(), this.currentContainerPos)) {
            return true;
        }
        return currentShellUuid != null && shellState.getUuid().equals(currentShellUuid);
    }

    private static boolean isSameStorageBlock(BlockPos shellPos, BlockPos currentContainerPos) {
        return shellPos.equals(currentContainerPos)
            || shellPos.above().equals(currentContainerPos)
            || shellPos.below().equals(currentContainerPos)
            || currentContainerPos.above().equals(shellPos)
            || currentContainerPos.below().equals(shellPos);
    }

    private void nextSection() {
        this.pageDisplay.nextSection();
    }

    private void previousSection() {
        this.pageDisplay.previousSection();
    }

    private void nextPage() {
        this.pageDisplay.nextPage();
    }

    private void previousPage() {
        this.pageDisplay.previousPage();
    }

    @Override
    public void onClose() {
        NeoSyncDebug.info("selector", "onClose wasClosed={} currentContainer={}", this.wasClosed, this.currentContainerPos);
        HudController.restore();
        if (this.onCloseCallback != null) {
            this.onCloseCallback.run();
        }
        this.wasClosed = true;
        super.onClose();
    }

    @Override
    public void removed() {
        super.removed();
        NeoSyncDebug.info("selector", "removed wasClosed={} currentContainer={}", this.wasClosed, this.currentContainerPos);
        if (!this.wasClosed && this.onRemovedCallback != null) {
            this.onRemovedCallback.run();
        }
    }
}
