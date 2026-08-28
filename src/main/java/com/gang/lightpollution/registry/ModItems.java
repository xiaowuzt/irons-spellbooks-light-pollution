package com.gang.lightpollution.registry;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.item.BoundSpellScrollItem;
import com.gang.lightpollution.item.ChromaticAccretionScrollItem;
import com.gang.lightpollution.spell.ModSpells;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, ExampleMod.MODID);

    public static final RegistryObject<Item> CHROMATIC_ACCRETION_SCROLL = ITEMS.register(
            "chromatic_accretion_scroll",
            () -> new ChromaticAccretionScrollItem(new Item.Properties()
                    .stacksTo(1).fireResistant().rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> CELESTIAL_JUDGMENT_SCROLL = registerSpellScroll(
            "celestial_judgment_scroll", ModSpells.CELESTIAL_JUDGMENT);
    public static final RegistryObject<Item> STARGRAVE_SINGULARITY_SCROLL = registerSpellScroll(
            "stargrave_singularity_scroll", ModSpells.STARGRAVE_SINGULARITY);
    public static final RegistryObject<Item> ECLIPSE_SEVERANCE_SCROLL = registerSpellScroll(
            "eclipse_severance_scroll", ModSpells.ECLIPSE_SEVERANCE);
    public static final RegistryObject<Item> FUNERAL_NOVA_SCROLL = registerSpellScroll(
            "funeral_nova_scroll", ModSpells.FUNERAL_NOVA);
    public static final RegistryObject<Item> STARLESS_SCROLL = registerSpellScroll(
            "starless_scroll", ModSpells.STARLESS);
    public static final RegistryObject<Item> CONSTELLATION_SCROLL = registerSpellScroll(
            "constellation_scroll", ModSpells.CONSTELLATION);
    public static final RegistryObject<Item> SILHOUETTE_SCROLL = registerSpellScroll(
            "silhouette_scroll", ModSpells.SILHOUETTE);
    public static final RegistryObject<Item> STARFALL_SCROLL = registerSpellScroll(
            "starfall_scroll", ModSpells.STARFALL);
    public static final RegistryObject<Item> SKY_COLLAPSE_SCROLL = registerSpellScroll(
            "sky_collapse_scroll", ModSpells.SKY_COLLAPSE);
    public static final RegistryObject<Item> STELLAR_CONVERGENCE_SCROLL = registerSpellScroll(
            "stellar_convergence_scroll", ModSpells.STELLAR_CONVERGENCE);
    public static final RegistryObject<Item> SECOND_SUN_SCROLL = registerSpellScroll(
            "second_sun_scroll", ModSpells.SECOND_SUN);
    public static final RegistryObject<Item> SINGULARITY_SCROLL = registerSpellScroll(
            "singularity_scroll", ModSpells.SINGULARITY);
    public static final RegistryObject<Item> LEVIATHAN_SCROLL = registerSpellScroll(
            "leviathan_scroll", ModSpells.LEVIATHAN);
    public static final RegistryObject<Item> WORLD_TREE_SCROLL = registerSpellScroll(
            "world_tree_scroll", ModSpells.WORLD_TREE);

    public static final RegistryObject<Item> GARGANTUA_SCROLL = registerSpellScroll(
            "gargantua_scroll", ModSpells.GARGANTUA);

    public static final RegistryObject<Item> COSMIC_HORSESHOE_SCROLL = registerSpellScroll(
            "cosmic_horseshoe_scroll", ModSpells.COSMIC_HORSESHOE);

    public static final RegistryObject<Item> MICROQUASAR_SCROLL = registerSpellScroll(
            "microquasar_scroll", ModSpells.MICROQUASAR);

    public static final RegistryObject<Item> HELIX_NEBULA_SCROLL = registerSpellScroll(
            "helix_nebula_scroll", ModSpells.HELIX_NEBULA);

    public static final RegistryObject<Item> MAGNETAR_SCROLL = registerSpellScroll(
            "magnetar_scroll", ModSpells.MAGNETAR);

    public static final RegistryObject<Item> TIDAL_DISRUPTION_SCROLL = registerSpellScroll(
            "tidal_disruption_scroll", ModSpells.TIDAL_DISRUPTION);

    public static final RegistryObject<Item> QUASAR_JET_SCROLL = registerSpellScroll(
            "quasar_jet_scroll", ModSpells.QUASAR_JET);

    public static final RegistryObject<Item> PINWHEEL_SCROLL = registerSpellScroll(
            "pinwheel_scroll", ModSpells.PINWHEEL);

    public static final RegistryObject<Item> CRAB_NEBULA_SCROLL = registerSpellScroll(
            "crab_nebula_scroll", ModSpells.CRAB_NEBULA);

    private static RegistryObject<Item> registerSpellScroll(
            String id,
            RegistryObject<? extends io.redspace.ironsspellbooks.api.spells.AbstractSpell> spell) {
        return ITEMS.register(id, () -> new BoundSpellScrollItem(
                new Item.Properties().stacksTo(1).fireResistant().rarity(Rarity.EPIC),
                spell::get,
                1));
    }

    private ModItems() {
    }

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
