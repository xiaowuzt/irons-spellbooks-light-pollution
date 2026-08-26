package com.gang.lightpollution.spell;

import com.gang.lightpollution.ExampleMod;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Iron's Spells registry entries owned by the DLC. */
public final class ModSpells {
    public static final DeferredRegister<AbstractSpell> SPELLS =
            DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, ExampleMod.MODID);

    public static final RegistryObject<AbstractSpell> CELESTIAL_JUDGMENT = SPELLS.register(
            "celestial_judgment", CelestialJudgmentSpell::new);

    public static final RegistryObject<AbstractSpell> STARGRAVE_SINGULARITY = SPELLS.register(
            "stargrave_singularity", StargraveSingularitySpell::new);

    public static final RegistryObject<AbstractSpell> ECLIPSE_SEVERANCE = SPELLS.register(
            "eclipse_severance", EclipseSeveranceSpell::new);

    public static final RegistryObject<AbstractSpell> FUNERAL_NOVA = SPELLS.register(
            "funeral_nova", FuneralNovaSpell::new);

    public static final RegistryObject<AbstractSpell> CHROMATIC_ACCRETION = SPELLS.register(
            "chromatic_accretion", ChromaticAccretionSpell::new);

    public static final RegistryObject<AbstractSpell> STARLESS = SPELLS.register(
            "starless", StarlessSpell::new);

    public static final RegistryObject<AbstractSpell> CONSTELLATION = SPELLS.register(
            "constellation", ConstellationSpell::new);

    public static final RegistryObject<AbstractSpell> SILHOUETTE = SPELLS.register(
            "silhouette", SilhouetteSpell::new);

    public static final RegistryObject<AbstractSpell> STARFALL = SPELLS.register(
            "starfall", StarfallSpell::new);

    public static final RegistryObject<AbstractSpell> SKY_COLLAPSE = SPELLS.register(
            "sky_collapse", SkyCollapseSpell::new);

    public static final RegistryObject<AbstractSpell> STELLAR_CONVERGENCE = SPELLS.register(
            "stellar_convergence", StellarConvergenceSpell::new);

    public static final RegistryObject<AbstractSpell> SECOND_SUN = SPELLS.register(
            "second_sun", SecondSunSpell::new);

    public static final RegistryObject<AbstractSpell> SINGULARITY = SPELLS.register(
            "singularity", SingularitySpell::new);

    public static final RegistryObject<AbstractSpell> LEVIATHAN = SPELLS.register(
            "leviathan", LeviathanSpell::new);

    public static final RegistryObject<AbstractSpell> WORLD_TREE = SPELLS.register(
            "world_tree", WorldTreeSpell::new);

    private ModSpells() {
    }
}
