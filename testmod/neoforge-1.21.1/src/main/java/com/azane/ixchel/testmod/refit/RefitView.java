package com.azane.ixchel.testmod.refit;

import com.azane.ixchel.core.binding.Action;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Z-key refit screen, Ixchel version. The target is locked to the inventory slot that held the gun
 * at open; if the gun leaves it, {@link #valid} turns false and the template says so. The laser
 * color is a draft until applied; closing the screen drops an unapplied draft.
 */
final class RefitView extends ViewModel {
    record SlotRow(String type, Component name, ItemStack icon, boolean selected) {
    }

    record OptionRow(String id, Component label, ItemStack icon, boolean installed, boolean missing) {
    }

    record Swatch(String id, Component name, ItemStack icon, boolean draft, boolean current) {
    }

    final Value<ItemStack> target;
    final Value<List<ItemStack>> inventory = source(ClientSources.inventory());
    final Derived<Boolean> valid;
    final Derived<Component> gunName;
    final Value<String> selectedType = state(null);
    final Derived<Boolean> hasSelection = derive(selectedType, Objects::nonNull);
    final Derived<Component> selectedName = derive(selectedType, t -> t == null ? null : Attachments.type(t).name());
    final Derived<List<SlotRow>> slots;
    final Derived<List<OptionRow>> options;
    final Derived<Boolean> hasInstalled;
    final Value<String> draftLaser = state(null);
    final Derived<List<Swatch>> swatches;
    final Derived<Boolean> hasDraft;

    final Action<SlotRow> select = action(row -> selectedType.set(row.type()));
    final Action<OptionRow> install;
    final Action<Void> remove;
    final Action<Swatch> pickLaser = action(s -> draftLaser.set(s.id()));
    final Action<Void> applyLaser;
    final Derived<String> errorText;
    final Derived<Boolean> hasError;

    RefitView(int slot) {
        target = source(ClientSources.inventorySlot(slot));
        valid = derive(target, Attachments::isGun);
        gunName = derive(target, ItemStack::getHoverName);
        slots = derive(target, selectedType, RefitView::slotRows);
        options = derive(target, inventory, selectedType, RefitView::optionRows);
        hasInstalled = derive(target, selectedType, (gun, t) -> t != null && !Attachments.installed(gun, t).isEmpty());
        swatches = derive(target, draftLaser, RefitView::swatches);
        hasDraft = derive(target, draftLaser, (gun, d) -> d != null && !d.equals(Attachments.laser(gun)));
        install = task(o -> o.installed() ? CompletableFuture.completedFuture(null)
                : RefitNetwork.install(slot, selectedType.get(), o.id()));
        remove = task(() -> RefitNetwork.install(slot, selectedType.get(), ""));
        applyLaser = task(() -> RefitNetwork.laser(slot, draftLaser.get()));
        errorText = derive(install.error(), remove.error(), applyLaser.error(), (a, b, c) -> {
            Throwable t = a != null ? a : b != null ? b : c;
            return t == null ? null : t.getMessage();
        });
        hasError = derive(errorText, Objects::nonNull);
    }

    private static List<SlotRow> slotRows(ItemStack gun, String selected) {
        List<SlotRow> out = new ArrayList<>();
        for (Attachments.Type t : Attachments.TYPES) {
            Attachments.Option o = Attachments.option(t.options(), Attachments.installed(gun, t.id()));
            out.add(new SlotRow(t.id(), o == null ? t.name() : o.name(), icon(o == null ? null : o.item()),
                    t.id().equals(selected)));
        }
        return out;
    }

    private static List<OptionRow> optionRows(ItemStack gun, List<ItemStack> inventory, String selected) {
        Attachments.Type t = selected == null ? null : Attachments.type(selected);
        if (t == null) {
            return List.of();
        }
        String installed = Attachments.installed(gun, t.id());
        List<OptionRow> out = new ArrayList<>();
        for (Attachments.Option o : t.options()) {
            int owned = 0;
            for (ItemStack s : inventory) {
                if (s.is(o.item())) {
                    owned += s.getCount();
                }
            }
            boolean on = o.id().equals(installed);
            out.add(new OptionRow(o.id(), on ? o.name() : Component.literal(o.name().getString() + " x" + owned),
                    icon(o.item()), on, !on && owned == 0));
        }
        return out;
    }

    private static List<Swatch> swatches(ItemStack gun, String draft) {
        String current = Attachments.laser(gun);
        List<Swatch> out = new ArrayList<>();
        for (Attachments.Option o : Attachments.LASERS) {
            out.add(new Swatch(o.id(), o.name(), icon(o.item()), o.id().equals(draft), o.id().equals(current)));
        }
        return out;
    }

    private static ItemStack icon(Item item) {
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }
}
