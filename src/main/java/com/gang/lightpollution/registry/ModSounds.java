package com.gang.lightpollution.registry;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Sound events owned by the DLC.
 *
 * <p>The audio is from Some of FX (github.com/YangMao-Minister/some_of_fx, MIT,
 * (c) 2026 Pizuka), used unchanged. See
 * {@code assets/.../textures/effect/SOME_OF_FX_LICENSE.txt}.</p>
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, ExampleMod.MODID);

    /** The charge starting: a rising tone under the visual wind-up. */
    public static final RegistryObject<SoundEvent> SINGULARITY_CHARGE =
            register("singularity.charge");
    /** The charge holding, looped over the wind-up by the entity's timeline. */
    public static final RegistryObject<SoundEvent> SINGULARITY_ACTIVE =
            register("singularity.active");
    /** The collapse. */
    public static final RegistryObject<SoundEvent> SINGULARITY_EXPLODE =
            register("singularity.explode");
    /**
     * Ringing ears. Played per nearby player rather than at the world position:
     * it is meant to be the listener's own reaction, not a sound in the scene.
     */
    public static final RegistryObject<SoundEvent> TINNITUS = register("tinnitus");
    /** One lightning arc. */
    public static final RegistryObject<SoundEvent> LIGHTNING_ARC =
            register("lightning.arc");

    private ModSounds() {
    }

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(ExampleMod.MODID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUNDS.register(eventBus);
    }
}
