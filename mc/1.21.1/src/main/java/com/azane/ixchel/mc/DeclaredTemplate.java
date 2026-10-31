package com.azane.ixchel.mc;

import com.azane.ixchel.core.binding.UiTemplate;
import net.minecraft.resources.ResourceLocation;

/** The template a view model class declares, for the hosts mounted without a template id. */
final class DeclaredTemplate {
    private DeclaredTemplate() {
    }

    /**
     * The one concrete template {@code type}'s {@link UiTemplate} names.
     *
     * @throws IllegalArgumentException if {@code type} declares none, several or a pattern
     * @throws net.minecraft.ResourceLocationException if the declared id is malformed
     */
    static ResourceLocation of(Class<?> type) {
        return ResourceLocation.parse(UiTemplate.Ids.single(type));
    }
}
