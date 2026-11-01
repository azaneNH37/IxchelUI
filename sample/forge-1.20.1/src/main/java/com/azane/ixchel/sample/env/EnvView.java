package com.azane.ixchel.sample.env;

import com.azane.ixchel.core.binding.Cell;
import com.azane.ixchel.core.binding.Derived;
import com.azane.ixchel.core.binding.UiTemplate;
import com.azane.ixchel.core.binding.Value;
import com.azane.ixchel.core.binding.ViewModel;
import com.azane.ixchel.mc.source.ClientSources;
import com.azane.ixchel.sample.waypoints.Waypoints;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.fml.ModList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The environment HUD: a compass bar with the session's waypoints, what the crosshair is on (block or
 * entity), where the player is (biome, time, weather, light, coordinates, facing) and the vitals
 * (hearts, armor, food, experience). Every section is one source read per frame into a record, so a
 * frame where nothing changed compares equal and does no layout.
 */
@UiTemplate("ixchel_sample:ui/env/hud.html")
final class EnvView extends ViewModel {
    /** One icon of a row of ten (hearts, armor, food), each standing for two points. */
    record Icon(int index, boolean full, boolean half) {
    }

    record Prop(String name, String value) {
    }

    /** The block under the crosshair; {@code tool} is the harvest hint, red unless {@code harvestable}. */
    record BlockInfo(Component name, String mod, Item item, Component tool, boolean harvestable, List<Prop> properties) {
        static final BlockInfo NONE = new BlockInfo(Component.empty(), "", Items.AIR, Component.empty(), true, List.of());
    }

    /** The entity under the crosshair; health and armor only when {@code living}. */
    record EntityInfo(Component name, String mod, boolean living, float health, float maxHealth, int armor) {
        static final EntityInfo NONE = new EntityInfo(Component.empty(), "", false, 0, 0, 0);
    }

    record World(Component biome, Component time, Component weather, Component light, String coords, String facing) {
        static final World NONE = new World(Component.empty(), Component.empty(), Component.empty(), Component.empty(), "", "");
    }

    /** A strip tick every 15°; {@code left} (pixels) places its box so the tick sits on the angle. */
    record Mark(int id, int left, String label, boolean major) {
    }

    /**
     * A waypoint on the compass: {@code near} within 10° of the heading (its label shows), {@code edge}
     * more than 90° off, held at the end of the bar.
     */
    record Marker(int id, String label, int left, boolean near, boolean edge) {
    }

    private static final String[] POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
    /** Degrees shown each side of the heading, one pixel per degree. */
    private static final int HALF = 90;
    private static final int MARK_WIDTH = 20;
    private static final int MARKER_WIDTH = 80;

    final Cell<BlockInfo> block = source(ClientSources.player(EnvView::block, BlockInfo.NONE));
    final Cell<EntityInfo> entity = source(ClientSources.player(EnvView::entity, EntityInfo.NONE));
    final Cell<World> world = source(ClientSources.player(EnvView::world, World.NONE));
    final Cell<Float> health = source(ClientSources.player(LocalPlayer::getHealth, 0f));
    final Cell<Float> maxHealth = source(ClientSources.player(LocalPlayer::getMaxHealth, 20f));
    final Cell<Integer> armor = source(ClientSources.player(LocalPlayer::getArmorValue, 0));
    final Cell<Integer> food = source(ClientSources.player(p -> p.getFoodData().getFoodLevel(), 0));
    final Cell<Integer> level = source(ClientSources.player(p -> p.experienceLevel, 0));
    final Cell<Float> progress = source(ClientSources.player(p -> p.experienceProgress, 0f));
    final Cell<Integer> heading = source(ClientSources.player(EnvView::heading, 0));
    final Cell<List<Marker>> markers = source(ClientSources.player(EnvView::markers, List.of()));
    final Value<List<Mark>> marks = state(marks());

    final Derived<Boolean> hasBlock = derive(block, b -> !b.equals(BlockInfo.NONE));
    final Derived<ItemStack> blockIcon = derive(block, b -> new ItemStack(b.item()));
    final Derived<Boolean> hasEntity = derive(entity, e -> !e.equals(EntityInfo.NONE));
    /** Rounded like the vanilla health display; the bar never overfills. */
    final Derived<String> entityHealth = derive(entity, e -> Math.round(e.health()) + " / " + Math.round(e.maxHealth()));
    final Derived<String> entityHealthWidth = derive(entity,
            e -> e.maxHealth() <= 0 ? "0%" : Math.min(100, e.health() / e.maxHealth() * 100) + "%");

    final Derived<List<Icon>> hearts = derive(health, maxHealth, (h, max) -> icons(Math.round(h), (int) Math.ceil(max)));
    final Derived<List<Icon>> armorIcons = derive(armor, a -> icons(a, 20));
    final Derived<List<Icon>> foodIcons = derive(food, f -> icons(f, 20));

    /** {@code points} out of {@code max} as icons of two points each. */
    private static List<Icon> icons(int points, int max) {
        List<Icon> out = new ArrayList<>();
        for (int i = 0; i < (max + 1) / 2; i++) {
            int left = points - i * 2;
            out.add(new Icon(i, left >= 2, left == 1));
        }
        return out;
    }

    /** Compass heading in whole degrees, 0 north, clockwise; whole pixels on the strip. */
    private static int heading(LocalPlayer p) {
        return Math.floorMod(Math.round(p.getYRot()) + 180, 360);
    }

    /** Ticks from -90° to 450°, so any heading has 90° of strip on each side. */
    private static List<Mark> marks() {
        List<Mark> out = new ArrayList<>();
        for (int a = -HALF; a <= 360 + HALF; a += 15) {
            int deg = Math.floorMod(a, 360);
            String label = deg % 45 == 0 ? POINTS[deg / 45] : "";
            out.add(new Mark(a, a + HALF - MARK_WIDTH / 2, label, deg % 90 == 0));
        }
        return out;
    }

    private static List<Marker> markers(LocalPlayer p) {
        List<Waypoints.Waypoint> all = Waypoints.all();
        if (all.isEmpty()) {
            return List.of();
        }
        int h = heading(p);
        List<Marker> out = new ArrayList<>(all.size());
        for (Waypoints.Waypoint w : all) {
            double dx = w.pos().getX() + 0.5 - p.getX();
            double dz = w.pos().getZ() + 0.5 - p.getZ();
            // North is -z, east +x.
            double bearing = Math.toDegrees(Math.atan2(dx, -dz));
            int delta = Math.floorMod((int) Math.round(bearing) - h + 180, 360) - 180;
            int at = Math.max(-HALF, Math.min(HALF, delta));
            String label = w.name() + " " + Math.round(Math.sqrt(w.pos().distSqr(p.blockPosition()))) + " m";
            out.add(new Marker(w.id(), label, at + HALF - MARKER_WIDTH / 2, Math.abs(delta) <= 10,
                    Math.abs(delta) > HALF));
        }
        return out;
    }

    private static BlockInfo block(LocalPlayer p) {
        if (!(Minecraft.getInstance().hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return BlockInfo.NONE;
        }
        BlockState s = p.level().getBlockState(hit.getBlockPos());
        if (s.isAir()) {
            return BlockInfo.NONE;
        }
        List<Prop> props = new ArrayList<>();
        for (Map.Entry<Property<?>, Comparable<?>> e : s.getValues().entrySet()) {
            props.add(new Prop(e.getKey().getName(), valueName(e.getKey(), e.getValue())));
        }
        return new BlockInfo(s.getBlock().getName(), modName(BuiltInRegistries.BLOCK.getKey(s.getBlock()).getNamespace()),
                s.getBlock().asItem(), tool(s), p.hasCorrectToolForDrops(s), List.copyOf(props));
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property property, Comparable value) {
        return property.getName(value);
    }

    /** The tool tag and the lowest tier from the vanilla mining tags. */
    private static Component tool(BlockState s) {
        String kind = s.is(BlockTags.MINEABLE_WITH_PICKAXE) ? "pickaxe"
                : s.is(BlockTags.MINEABLE_WITH_AXE) ? "axe"
                : s.is(BlockTags.MINEABLE_WITH_SHOVEL) ? "shovel"
                : s.is(BlockTags.MINEABLE_WITH_HOE) ? "hoe" : null;
        String tier = s.is(BlockTags.NEEDS_DIAMOND_TOOL) ? "diamond"
                : s.is(BlockTags.NEEDS_IRON_TOOL) ? "iron"
                : s.is(BlockTags.NEEDS_STONE_TOOL) ? "stone" : null;
        if (kind == null) {
            return Component.translatable(s.requiresCorrectToolForDrops() ? "ixchel_sample.env.tool.needed" : "ixchel_sample.env.tool.any");
        }
        Component tool = Component.translatable("ixchel_sample.env.tool." + kind);
        return tier == null ? tool
                : Component.translatable("ixchel_sample.env.tool.tiered", tool, Component.translatable("ixchel_sample.env.tier." + tier));
    }

    private static EntityInfo entity(LocalPlayer p) {
        if (!(Minecraft.getInstance().hitResult instanceof EntityHitResult hit) || hit.getType() != HitResult.Type.ENTITY) {
            return EntityInfo.NONE;
        }
        Entity e = hit.getEntity();
        String mod = modName(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()).getNamespace());
        if (e instanceof LivingEntity l) {
            return new EntityInfo(e.getDisplayName(), mod, true, l.getHealth(), l.getMaxHealth(), l.getArmorValue());
        }
        return new EntityInfo(e.getDisplayName(), mod, false, 0, 0, 0);
    }

    private static String modName(String namespace) {
        return ModList.get().getModContainerById(namespace).map(c -> c.getModInfo().getDisplayName()).orElse(namespace);
    }

    private static World world(LocalPlayer p) {
        Level level = p.level();
        BlockPos pos = p.blockPosition();
        Component biome = level.getBiome(pos).unwrapKey()
                .map(k -> (Component) Component.translatable(Util.makeDescriptionId("biome", k.location())))
                .orElse(Component.empty());
        long dayTime = level.getDayTime();
        // Day time 0 is sunrise, drawn as 06:00.
        long t = (dayTime + 6000) % 24000;
        Component time = Component.translatable("ixchel_sample.env.time", dayTime / 24000 + 1,
                String.format("%02d:%02d", t / 1000, t % 1000 * 60 / 1000));
        Component light = Component.translatable("ixchel_sample.env.light", level.getBrightness(LightLayer.BLOCK, pos),
                level.getBrightness(LightLayer.SKY, pos));
        String coords = pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
        int heading = heading(p);
        String facing = POINTS[Math.round(heading / 45f) % 8] + " " + heading + "\u00b0";
        return new World(biome, time, weather(level, pos), light, coords, facing);
    }

    private static Component weather(Level level, BlockPos pos) {
        if (!level.isRaining()) {
            return Component.translatable("ixchel_sample.env.weather.clear");
        }
        Biome.Precipitation here = level.getBiome(pos).value().getPrecipitationAt(pos);
        if (here == Biome.Precipitation.NONE) {
            return Component.translatable("ixchel_sample.env.weather.dry");
        }
        Component kind = Component.translatable(here == Biome.Precipitation.SNOW ? "ixchel_sample.env.weather.snow" : "ixchel_sample.env.weather.rain");
        return level.isThundering() ? Component.translatable("ixchel_sample.env.weather.thunder", kind) : kind;
    }
}
