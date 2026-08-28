package com.gang.lightpollution.registry;

import com.gang.lightpollution.ExampleMod;
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
                    .displayItems((parameters, output) -> {
                        output.accept(ModItems.CELESTIAL_JUDGMENT_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.STARGRAVE_SINGULARITY_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.ECLIPSE_SEVERANCE_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.FUNERAL_NOVA_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.CHROMATIC_ACCRETION_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.STARLESS_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.CONSTELLATION_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.SILHOUETTE_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.STARFALL_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.SKY_COLLAPSE_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.STELLAR_CONVERGENCE_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.SECOND_SUN_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.SINGULARITY_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.LEVIATHAN_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.WORLD_TREE_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.GARGANTUA_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.COSMIC_HORSESHOE_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.MICROQUASAR_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.HELIX_NEBULA_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.MAGNETAR_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.TIDAL_DISRUPTION_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.QUASAR_JET_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.PINWHEEL_SCROLL.get().getDefaultInstance());
                        output.accept(ModItems.CRAB_NEBULA_SCROLL.get().getDefaultInstance());
                    })
                    .build());

    private ModCreativeTabs() {
    }

    public static void register(IEventBus eventBus) {
        TABS.register(eventBus);
    }
}
