package com.azane.ixchel.testmod.refit;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.ClientSources;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

/**
 * Backpack refit, Ixchel version: the Z-key refit's laser and name bindings unchanged, plus real
 * attachment slots and the inventory. Slots carry no item data here; vanilla draws and moves the
 * items, the view only places them and labels the attachment slots.
 */
final class BackpackRefitView extends ViewModel {
    record AttachmentRow(int slot, Component name, boolean empty) {
    }

    record Swatch(String id, Component name, ItemStack icon, boolean draft, boolean current) {
    }

    final Value<ItemStack> target;
    final Derived<Component> gunName;
    final Derived<List<AttachmentRow>> attachments;
    final Value<List<Integer>> inventory = state(range(BackpackRefitMenu.INVENTORY_START, BackpackRefitMenu.HOTBAR_START));
    final Value<List<Integer>> hotbar = state(range(BackpackRefitMenu.HOTBAR_START, BackpackRefitMenu.SLOT_COUNT));
    final Value<String> draftLaser = state(null);
    final Derived<List<Swatch>> swatches;
    final Derived<Boolean> hasDraft;
    final Action<Swatch> pickLaser = action(s -> draftLaser.set(s.id()));
    final Action<Void> applyLaser;
    final Derived<String> errorText;
    final Derived<Boolean> hasError;

    BackpackRefitView(BackpackRefitMenu menu) {
        int slot = menu.gunSlot();
        target = source(ClientSources.inventorySlot(slot));
        gunName = derive(target, ItemStack::getHoverName);
        attachments = derive(target, BackpackRefitView::attachmentRows);
        swatches = derive(target, draftLaser, BackpackRefitView::swatches);
        hasDraft = derive(target, draftLaser, (gun, d) -> d != null && !d.equals(Attachments.laser(gun)));
        applyLaser = task(() -> RefitNetwork.laser(slot, draftLaser.get()));
        errorText = derive(applyLaser.error(), t -> t == null ? null : t.getMessage());
        hasError = derive(errorText, Objects::nonNull);
    }

    private static List<AttachmentRow> attachmentRows(ItemStack gun) {
        List<AttachmentRow> out = new ArrayList<>();
        for (int i = 0; i < Attachments.TYPES.size(); i++) {
            Attachments.Type t = Attachments.TYPES.get(i);
            out.add(new AttachmentRow(i, t.name(), Attachments.installed(gun, t.id()).isEmpty()));
        }
        return out;
    }

    private static List<Swatch> swatches(ItemStack gun, String draft) {
        String current = Attachments.laser(gun);
        List<Swatch> out = new ArrayList<>();
        for (Attachments.Option o : Attachments.LASERS) {
            out.add(new Swatch(o.id(), o.name(), new ItemStack(o.item()), o.id().equals(draft), o.id().equals(current)));
        }
        return out;
    }

    private static List<Integer> range(int from, int to) {
        return IntStream.range(from, to).boxed().toList();
    }
}
