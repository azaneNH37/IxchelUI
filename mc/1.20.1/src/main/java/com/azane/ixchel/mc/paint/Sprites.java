package com.azane.ixchel.mc.paint;

import com.azane.ixchel.core.css.property.Syntaxes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;

/**
 * Image sources naming a sprite of the block atlas (blocks and items in 1.20.1), as
 * {@code sprite:minecraft:block/water_still}; animated sprites animate.
 */
final class Sprites {
    static final String PREFIX = Syntaxes.SPRITE;

    private Sprites() {
    }

    /** The sprite id of a {@link #PREFIX} source, or {@code null} when it is not a valid id. */
    static ResourceLocation id(String src) {
        return ResourceLocation.tryParse(src.substring(PREFIX.length()));
    }

    /** The block atlas sprite {@code id}, or the missing-texture sprite; look it up every time, a resource reload replaces the atlas's sprites. */
    static TextureAtlasSprite sprite(ResourceLocation id) {
        return Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(id);
    }
}
