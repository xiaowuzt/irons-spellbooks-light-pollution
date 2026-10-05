package com.gang.lightpollution.registry;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.item.BoundSpellScrollItem;
import net.minecraft.resources.ResourceLocation;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ExampleMod.MODID);

    public static final RegistryObject<CreativeModeTab> LIGHT_POLLUTION = TABS.register(
            "light_pollution",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.irons_spellbooks_light_pollution.light_pollution"))
                    .icon(() -> new ItemStack(ModItems.CHROMATIC_ACCRETION_SCROLL.get()))
                    .displayItems((parameters, output) -> ModItems.ITEMS.getEntries().stream()
                            .map(RegistryObject::get)
                            .filter(BoundSpellScrollItem.class::isInstance)
                            .map(BoundSpellScrollItem.class::cast)
                            .sorted(Comparator.comparingInt((BoundSpellScrollItem item) -> schoolRank(school(item)))
                                    .thenComparing(item -> school(item).toString())
                                    .thenComparing(item -> item.getBoundSpell().getSpellResource().toString()))
                            .forEach(item -> output.accept(item.getDefaultInstance())))
                    .build());

    // Iron's built-in school order. Empty schools produce no placeholder items.
    private static final List<ResourceLocation> SCHOOL_ORDER = List.of("fire", "ice", "lightning", "holy",
            "ender", "blood", "evocation", "nature", "eldritch").stream()
            .map(id -> new ResourceLocation("irons_spellbooks", id)).toList();

    private static ResourceLocation school(BoundSpellScrollItem item) {
        var spell = item.getBoundSpell();
        // Before world/server config sync Iron's manager returns EVOCATION for every spell.
        // In-world, respect the server's actual school overrides; at menus use declared defaults.
        return SpellConfig.SPEC.isLoaded() ? spell.getSchoolType().getId() : spell.getDefaultConfig().schoolResource;
    }

    private static int schoolRank(ResourceLocation school) {
        int index = SCHOOL_ORDER.indexOf(school);
        return index < 0 ? SCHOOL_ORDER.size() : index;
    }

    private ModCreativeTabs() {
    }

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
