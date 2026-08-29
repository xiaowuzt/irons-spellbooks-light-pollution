package com.gang.lightpollution.recipe;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** The mod's recipe serializers. */
public final class ModRecipes {
    public static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, ExampleMod.MODID);

    /** Our scroll to a plain Iron's Spells scroll, carrying the spell across. */
    public static final RegistryObject<RecipeSerializer<TranscribeScrollRecipe>>
            TRANSCRIBE_SCROLL = SERIALIZERS.register("transcribe_scroll",
                    TranscribeScrollRecipe.Serializer::new);

    private ModRecipes() {
    }

    public static void register(IEventBus bus) {
        SERIALIZERS.register(bus);
    }
}
