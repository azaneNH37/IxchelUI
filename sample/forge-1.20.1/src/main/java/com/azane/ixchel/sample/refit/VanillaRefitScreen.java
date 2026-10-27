package com.azane.ixchel.sample.refit;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Supplier;

/**
 * The refit screen of {@link RefitView} written the usual vanilla way, as the M3 baseline: same
 * look, requests and failure handling. The screen keeps copies of what it shows, compares them with
 * the live inventory every tick and rebuilds its widgets when they differ or its own state changes.
 * V opens it.
 */
final class VanillaRefitScreen extends Screen {
    private static final int PANEL_WIDTH = 330;
    private static final int PAD = 6;
    private static final int COLUMN = 106;
    private static final int COLUMN_GAP = 4;
    private static final int LABEL = 12;
    private static final int ROW = 18;
    private static final int ROW_STEP = 19;
    private static final int BUTTON = 13;
    private static final int BUTTON_STEP = 16;
    private static final int TEXT_WIDTH = PANEL_WIDTH - 2 * PAD;

    private final int slot;
    private ItemStack gun = ItemStack.EMPTY;
    private int[] owned = new int[0];
    private String selectedType;
    private String draftLaser;
    private final Request install = new Request();
    private final Request remove = new Request();
    private final Request applyLaser = new Request();
    private boolean dirty;
    private boolean closed;
    private int left;
    private int top;
    private int bodyHeight;
    private List<FormattedCharSequence> goneLines = List.of();
    private List<FormattedCharSequence> errorLines = List.of();

    VanillaRefitScreen(int slot) {
        super(Component.empty());
        this.slot = slot;
    }

    /** Whether the gun is still in the slot the screen was opened on, as of the last rebuild. */
    boolean valid() {
        return Attachments.isGun(gun);
    }

    void selectType(String type) {
        selectedType = type;
        dirty = true;
        minecraft.gui.setOverlayMessage(Component.literal("Inspecting ").append(Attachments.type(type).name()), false);
    }

    @Override
    protected void init() {
        dirty = false;
        gun = liveGun().copy();
        owned = ownedCounts();
        String error = install.error != null ? install.error : remove.error != null ? remove.error : applyLaser.error;
        errorLines = error == null ? List.of() : font.split(Component.literal(error), TEXT_WIDTH);
        goneLines = valid() ? List.of()
                : font.split(Component.literal("The crossbow left the slot this screen was opened on. Put it back, or press Esc."), TEXT_WIDTH);

        Attachments.Type type = selectedType == null ? null : Attachments.type(selectedType);
        String installed = type == null ? "" : Attachments.installed(gun, type.id());
        String laser = Attachments.laser(gun);
        boolean hasDraft = draftLaser != null && !draftLaser.equals(laser);
        int attachments = LABEL + Attachments.TYPES.size() * ROW_STEP;
        int options = type == null ? 0 : LABEL + type.options().size() * ROW_STEP + (installed.isEmpty() ? 0 : BUTTON_STEP);
        int lasers = LABEL + Attachments.LASERS.size() * ROW_STEP + (hasDraft ? BUTTON_STEP : 0);
        bodyHeight = valid() ? Math.max(attachments, Math.max(options, lasers)) : 4 + goneLines.size() * 9;
        int height = PAD + 9 + PAD + bodyHeight + (errorLines.isEmpty() ? 0 : 4 + errorLines.size() * 9) + PAD;
        left = (width - PANEL_WIDTH) / 2;
        top = (this.height - height) / 2;
        if (!valid()) {
            return;
        }

        int x = left + PAD;
        int y = top + PAD + 9 + PAD + LABEL;
        for (Attachments.Type t : Attachments.TYPES) {
            Attachments.Option o = Attachments.option(t.options(), Attachments.installed(gun, t.id()));
            addRenderableWidget(new Row(x, y, o == null ? t.name() : o.name(), icon(o), 0xFFFFFFFF,
                    t.id().equals(selectedType), () -> selectType(t.id())));
            y += ROW_STEP;
        }

        x += COLUMN + COLUMN_GAP;
        if (type != null) {
            y = top + PAD + 9 + PAD + LABEL;
            for (Attachments.Option o : type.options()) {
                boolean on = o.id().equals(installed);
                int count = owned[optionIndex(o)];
                Component label = on ? o.name() : Component.literal(o.name().getString() + " x" + count);
                int color = on ? 0xFF55FF55 : count == 0 ? 0xFF777777 : 0xFFFFFFFF;
                addRenderableWidget(new Row(x, y, label, icon(o), color, false,
                        () -> {
                            if (!on) {
                                install.run(() -> RefitNetwork.install(slot, selectedType, o.id()));
                            }
                        }));
                y += ROW_STEP;
            }
            if (!installed.isEmpty()) {
                addRenderableWidget(new Button(x, y + 3, "Remove", remove,
                        () -> remove.run(() -> RefitNetwork.install(slot, selectedType, ""))));
            }
        }

        x += COLUMN + COLUMN_GAP;
        y = top + PAD + 9 + PAD + LABEL;
        for (Attachments.Option o : Attachments.LASERS) {
            addRenderableWidget(new Row(x, y, o.name(), icon(o), o.id().equals(laser) ? 0xFF55FF55 : 0xFFFFFFFF,
                    o.id().equals(draftLaser), () -> {
                draftLaser = o.id();
                dirty = true;
            }));
            y += ROW_STEP;
        }
        if (hasDraft) {
            addRenderableWidget(new Button(x, y + 3, "Apply", applyLaser,
                    () -> applyLaser.run(() -> RefitNetwork.laser(slot, draftLaser))));
        }
    }

    @Override
    public void tick() {
        ItemStack live = liveGun();
        if (live.getCount() != gun.getCount() || !ItemStack.isSameItemSameTags(live, gun)
                || !Arrays.equals(owned, ownedCounts())) {
            dirty = true;
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        if (dirty) {
            rebuildWidgets();
        }
        renderBackground(g);
        int bottom = top + PAD + 9 + PAD + bodyHeight + (errorLines.isEmpty() ? 0 : 4 + errorLines.size() * 9) + PAD;
        g.fill(left, top, left + PANEL_WIDTH, bottom, 0xE0101418);
        int x = left + PAD;
        int y = top + PAD;
        g.drawString(font, gun.getHoverName(), x, y, 0xFFFFFF55, false);
        y += 9 + PAD;
        if (valid()) {
            g.drawString(font, "Attachments", x, y, 0xFFAAAAAA, false);
            if (selectedType != null) {
                Attachments.Type type = Attachments.type(selectedType);
                g.drawString(font, type.name(), x + COLUMN + COLUMN_GAP, y, 0xFFAAAAAA, false);
                if (install.pending) {
                    g.fill(x + COLUMN + COLUMN_GAP, y + LABEL, x + 2 * COLUMN + COLUMN_GAP,
                            y + LABEL + type.options().size() * ROW_STEP, 0x30FFFF55);
                }
            }
            g.drawString(font, "Laser", x + 2 * (COLUMN + COLUMN_GAP), y, 0xFFAAAAAA, false);
        } else {
            drawLines(g, goneLines, x, y + 4);
        }
        super.render(g, mouseX, mouseY, partialTick);
        drawLines(g, errorLines, x, y + bodyHeight + 4);
    }

    private void drawLines(GuiGraphics g, List<FormattedCharSequence> lines, int x, int y) {
        for (FormattedCharSequence line : lines) {
            g.drawString(font, line, x, y, 0xFFFF5555, false);
            y += 9;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void removed() {
        closed = true;
    }

    private ItemStack liveGun() {
        LocalPlayer p = minecraft.player;
        return p == null ? ItemStack.EMPTY : p.getInventory().getItem(slot);
    }

    /** Attachment items owned, per option in {@link Attachments#TYPES} order. */
    private int[] ownedCounts() {
        int[] out = new int[Attachments.TYPES.size() * 2];
        LocalPlayer p = minecraft.player;
        if (p == null) {
            return out;
        }
        Inventory inv = p.getInventory();
        for (int i = 0, n = inv.getContainerSize(); i < n; i++) {
            ItemStack s = inv.getItem(i);
            int k = 0;
            for (Attachments.Type t : Attachments.TYPES) {
                for (Attachments.Option o : t.options()) {
                    if (s.is(o.item())) {
                        out[k] += s.getCount();
                    }
                    k++;
                }
            }
        }
        return out;
    }

    private static int optionIndex(Attachments.Option option) {
        int k = 0;
        for (Attachments.Type t : Attachments.TYPES) {
            for (Attachments.Option o : t.options()) {
                if (o == option) {
                    return k;
                }
                k++;
            }
        }
        throw new IllegalArgumentException(option.id());
    }

    private static ItemStack icon(Attachments.Option o) {
        return o == null ? ItemStack.EMPTY : new ItemStack(o.item());
    }

    /** One request at a time; the reply lands on the client thread and is dropped once the screen closed. */
    private final class Request {
        boolean pending;
        String error;

        void run(Supplier<CompletableFuture<Void>> send) {
            if (pending) {
                return;
            }
            error = null;
            pending = true;
            dirty = true;
            send.get().whenComplete((result, failure) -> minecraft.execute(() -> {
                if (closed) {
                    return;
                }
                pending = false;
                Throwable cause = failure instanceof CompletionException c && c.getCause() != null ? c.getCause() : failure;
                error = cause == null ? null : cause.getMessage();
                dirty = true;
            }));
        }
    }

    /** Icon and label; selected rows are blue, hovered rows lighter. */
    private final class Row extends AbstractButton {
        private final ItemStack icon;
        private final int color;
        private final boolean selected;
        private final Runnable action;

        Row(int x, int y, Component label, ItemStack icon, int color, boolean selected, Runnable action) {
            super(x, y, COLUMN, ROW, label);
            this.icon = icon;
            this.color = color;
            this.selected = selected;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            if (selected || isHovered()) {
                g.fill(getX(), getY(), getX() + width, getY() + height, selected ? 0x604060FF : 0x40FFFFFF);
            }
            if (!icon.isEmpty()) {
                g.renderItem(icon, getX() + 1, getY() + 1);
            }
            g.drawString(font, getMessage(), getX() + 20, getY() + 5, color, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }

    /** Centered text; yellow while its request is pending. */
    private final class Button extends AbstractButton {
        private final Request request;
        private final Runnable action;

        Button(int x, int y, String label, Request request, Runnable action) {
            super(x, y, COLUMN, BUTTON, Component.literal(label));
            this.request = request;
            this.action = action;
        }

        @Override
        public void onPress() {
            action.run();
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            int background = request.pending ? 0x30FFFF55 : isHovered() ? 0x40FFFFFF : 0x30FFFFFF;
            g.fill(getX(), getY(), getX() + width, getY() + height, background);
            g.drawString(font, getMessage(), getX() + (width - font.width(getMessage())) / 2, getY() + 2, 0xFFFFFFFF, false);
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput output) {
            defaultButtonNarrationText(output);
        }
    }
}
