package com.gang.lightpollution.fx;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.api.ConstellationParams;
import com.gang.lightpollution.api.CrabNebulaParams;
import com.gang.lightpollution.api.EclipseSeveranceParams;
import com.gang.lightpollution.api.FuneralNovaParams;
import com.gang.lightpollution.api.FxHandle;
import com.gang.lightpollution.api.HelixNebulaParams;
import com.gang.lightpollution.api.LeviathanParams;
import com.gang.lightpollution.api.MagnetarParams;
import com.gang.lightpollution.api.MicroquasarParams;
import com.gang.lightpollution.api.PinwheelParams;
import com.gang.lightpollution.api.QuasarJetParams;
import com.gang.lightpollution.api.SecondSunParams;
import com.gang.lightpollution.api.SingularityParams;
import com.gang.lightpollution.api.SkyCollapseParams;
import com.gang.lightpollution.api.StarfallParams;
import com.gang.lightpollution.api.StellarConvergenceParams;
import com.gang.lightpollution.api.TidalDisruptionParams;
import com.gang.lightpollution.api.WorldTreeParams;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The effects other mods have asked this one to draw.
 *
 * <p>Client only, and deliberately so: these are drawn, not simulated. Nothing here is synced, so a
 * mod that wants other players to see an effect has to send its own packet and call this on each
 * client. Pretending otherwise would produce something that works in single player and quietly fails
 * in multiplayer.</p>
 *
 * <p>Copy-on-write, because the list is walked every frame on the render thread and written from
 * whichever thread a caller happens to be on. Frames vastly outnumber spawns, which is exactly the
 * shape copy-on-write is for.</p>
 *
 * <p>Cleared when a level unloads. The positions are in that level's coordinates and would otherwise
 * reappear somewhere meaningless in the next one.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class FxRegistry {
    /**
     * Ceiling on how many of one effect can be live at once.
     *
     * <p>These are heavy: one tidal disruption is a few thousand vertices of tube. A caller with a
     * loop bug should get a visual ceiling rather than a frozen game, and refusing the spawn is more
     * honest than accepting it and dropping frames.</p>
     */
    private static final int MAX_PER_KIND = 32;

    private static final List<TidalDisruptionInstance> TIDAL_DISRUPTIONS =
            new CopyOnWriteArrayList<>();
    private static final List<HelixNebulaInstance> HELIX_NEBULAE =
            new CopyOnWriteArrayList<>();
    private static final List<CrabNebulaInstance> CRAB_NEBULAE =
            new CopyOnWriteArrayList<>();
    private static final List<MagnetarInstance> MAGNETARS =
            new CopyOnWriteArrayList<>();
    private static final List<MicroquasarInstance> MICROQUASARS =
            new CopyOnWriteArrayList<>();
    private static final List<PinwheelInstance> PINWHEELS =
            new CopyOnWriteArrayList<>();
    private static final List<QuasarJetInstance> QUASARJETS =
            new CopyOnWriteArrayList<>();
    private static final List<SecondSunInstance> SECOND_SUNS =
            new CopyOnWriteArrayList<>();
    private static final List<SingularityInstance> SINGULARITIES =
            new CopyOnWriteArrayList<>();
    private static final List<SkyCollapseInstance> SKY_COLLAPSES =
            new CopyOnWriteArrayList<>();
    private static final List<StarfallInstance> STARFALLS =
            new CopyOnWriteArrayList<>();
    private static final List<ConstellationInstance> CONSTELLATIONS =
            new CopyOnWriteArrayList<>();
    private static final List<StellarConvergenceInstance> STELLAR_CONVERGENCES =
            new CopyOnWriteArrayList<>();
    private static final List<LeviathanInstance> LEVIATHANS =
            new CopyOnWriteArrayList<>();
    private static final List<WorldTreeInstance> WORLD_TREES =
            new CopyOnWriteArrayList<>();
    private static final List<EclipseSeveranceInstance> ECLIPSE_SEVERANCES =
            new CopyOnWriteArrayList<>();
    private static final List<FuneralNovaInstance> FUNERAL_NOVAS =
            new CopyOnWriteArrayList<>();

    /** Every list above, so ticking and clearing do not have to name each one. */
    private static final List<List<? extends Advanceable>> ALL_KINDS =
            List.of(TIDAL_DISRUPTIONS, HELIX_NEBULAE, CRAB_NEBULAE,
                    MAGNETARS, MICROQUASARS, PINWHEELS, QUASARJETS,
                    SECOND_SUNS, SINGULARITIES, SKY_COLLAPSES, STARFALLS,
                    CONSTELLATIONS, STELLAR_CONVERGENCES, LEVIATHANS, WORLD_TREES,
                    FUNERAL_NOVAS);

    private FxRegistry() {
    }

    /** Add a tidal disruption, or return null if the ceiling is reached. */
    public static FxHandle addTidalDisruption(Vec3 at, TidalDisruptionParams params) {
        if (at == null || params == null || TIDAL_DISRUPTIONS.size() >= MAX_PER_KIND) {
            return null;
        }
        TidalDisruptionInstance instance = new TidalDisruptionInstance(at, params);
        TIDAL_DISRUPTIONS.add(instance);
        return instance;
    }

    /** Add a planetary nebula, or return null if the ceiling is reached. */
    public static FxHandle addHelixNebula(Vec3 at, HelixNebulaParams params) {
        if (at == null || params == null || HELIX_NEBULAE.size() >= MAX_PER_KIND) {
            return null;
        }
        HelixNebulaInstance instance = new HelixNebulaInstance(at, params);
        HELIX_NEBULAE.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<TidalDisruptionSource> tidalDisruptions() {
        return TIDAL_DISRUPTIONS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(TIDAL_DISRUPTIONS);
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<HelixNebulaSource> helixNebulae() {
        return HELIX_NEBULAE.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(HELIX_NEBULAE);
    }

    /** Add a supernova remnant, or return null if the ceiling is reached. */
    public static FxHandle addCrabNebula(Vec3 at, CrabNebulaParams params) {
        if (at == null || params == null || CRAB_NEBULAE.size() >= MAX_PER_KIND) {
            return null;
        }
        CrabNebulaInstance instance = new CrabNebulaInstance(at, params);
        CRAB_NEBULAE.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<CrabNebulaSource> crabNebulae() {
        return CRAB_NEBULAE.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(CRAB_NEBULAE);
    }

    /** Add a magnetar, or return null if the ceiling is reached. */
    public static FxHandle addMagnetar(Vec3 at, MagnetarParams params) {
        if (at == null || params == null || MAGNETARS.size() >= MAX_PER_KIND) {
            return null;
        }
        MagnetarInstance instance = new MagnetarInstance(at, params);
        MAGNETARS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<MagnetarSource> magnetars() {
        return MAGNETARS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(MAGNETARS);
    }

    /** Add a microquasar, or return null if the ceiling is reached. */
    public static FxHandle addMicroquasar(Vec3 at, MicroquasarParams params) {
        if (at == null || params == null || MICROQUASARS.size() >= MAX_PER_KIND) {
            return null;
        }
        MicroquasarInstance instance = new MicroquasarInstance(at, params);
        MICROQUASARS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<MicroquasarSource> microquasars() {
        return MICROQUASARS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(MICROQUASARS);
    }

    /** Add a pinwheel, or return null if the ceiling is reached. */
    public static FxHandle addPinwheel(Vec3 at, PinwheelParams params) {
        if (at == null || params == null || PINWHEELS.size() >= MAX_PER_KIND) {
            return null;
        }
        PinwheelInstance instance = new PinwheelInstance(at, params);
        PINWHEELS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<PinwheelSource> pinwheels() {
        return PINWHEELS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(PINWHEELS);
    }

    /** Add a quasarjet, or return null if the ceiling is reached. */
    public static FxHandle addQuasarJet(Vec3 at, QuasarJetParams params) {
        if (at == null || params == null || QUASARJETS.size() >= MAX_PER_KIND) {
            return null;
        }
        QuasarJetInstance instance = new QuasarJetInstance(at, params);
        QUASARJETS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<QuasarJetSource> quasarJets() {
        return QUASARJETS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(QUASARJETS);
    }


    /** Add a second sun, or return null if the ceiling is reached. */
    public static FxHandle addSecondSun(Vec3 at, SecondSunParams params) {
        if (at == null || params == null || SECOND_SUNS.size() >= MAX_PER_KIND) {
            return null;
        }
        SecondSunInstance instance = new SecondSunInstance(at, params);
        SECOND_SUNS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<SecondSunSource> secondSuns() {
        return SECOND_SUNS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(SECOND_SUNS);
    }

    /** Add a singularity, or return null if the ceiling is reached. */
    public static FxHandle addSingularity(Vec3 at, SingularityParams params) {
        if (at == null || params == null || SINGULARITIES.size() >= MAX_PER_KIND) {
            return null;
        }
        SingularityInstance instance = new SingularityInstance(at, params);
        SINGULARITIES.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<SingularitySource> singularities() {
        return SINGULARITIES.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(SINGULARITIES);
    }


    /** Add a sky collapse, or return null if the ceiling is reached. */
    public static FxHandle addSkyCollapse(Vec3 at, SkyCollapseParams params) {
        if (at == null || params == null || SKY_COLLAPSES.size() >= MAX_PER_KIND) {
            return null;
        }
        SkyCollapseInstance instance = new SkyCollapseInstance(at, params);
        SKY_COLLAPSES.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<SkyCollapseSource> skyCollapses() {
        return SKY_COLLAPSES.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(SKY_COLLAPSES);
    }

    /** Add a meteor shower, or return null if the ceiling is reached. */
    public static FxHandle addStarfall(Vec3 at, StarfallParams params) {
        if (at == null || params == null || STARFALLS.size() >= MAX_PER_KIND) {
            return null;
        }
        StarfallInstance instance = new StarfallInstance(at, params);
        STARFALLS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<StarfallSource> starfalls() {
        return STARFALLS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(STARFALLS);
    }

    /** Add a constellation, or return null if the ceiling is reached. */
    public static FxHandle addConstellation(Vec3 at, ConstellationParams params) {
        if (at == null || params == null || CONSTELLATIONS.size() >= MAX_PER_KIND) {
            return null;
        }
        ConstellationInstance instance = new ConstellationInstance(at, params);
        CONSTELLATIONS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<ConstellationSource> constellations() {
        return CONSTELLATIONS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(CONSTELLATIONS);
    }

    /** Add a stellar convergence, or return null if the ceiling is reached. */
    public static FxHandle addStellarConvergence(Vec3 at, StellarConvergenceParams params) {
        if (at == null || params == null || STELLAR_CONVERGENCES.size() >= MAX_PER_KIND) {
            return null;
        }
        StellarConvergenceInstance instance = new StellarConvergenceInstance(at, params);
        STELLAR_CONVERGENCES.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<StellarConvergenceSource> stellarConvergences() {
        return STELLAR_CONVERGENCES.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(STELLAR_CONVERGENCES);
    }

    /** Add a leviathan, or return null if the ceiling is reached. */
    public static FxHandle addLeviathan(Vec3 at, LeviathanParams params) {
        if (at == null || params == null || LEVIATHANS.size() >= MAX_PER_KIND) {
            return null;
        }
        LeviathanInstance instance = new LeviathanInstance(at, params);
        LEVIATHANS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<LeviathanSource> leviathans() {
        return LEVIATHANS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(LEVIATHANS);
    }

    /** Add a world tree, or return null if the ceiling is reached. */
    public static FxHandle addWorldTree(Vec3 at, WorldTreeParams params) {
        if (at == null || params == null || WORLD_TREES.size() >= MAX_PER_KIND) {
            return null;
        }
        WorldTreeInstance instance = new WorldTreeInstance(at, params);
        WORLD_TREES.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<WorldTreeSource> worldTrees() {
        return WORLD_TREES.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(WORLD_TREES);
    }

    /** Add an eclipse severance, or return null if the ceiling is reached. */
    public static FxHandle addEclipseSeverance(Vec3 at, EclipseSeveranceParams params) {
        if (at == null || params == null || ECLIPSE_SEVERANCES.size() >= MAX_PER_KIND) {
            return null;
        }
        EclipseSeveranceInstance instance = new EclipseSeveranceInstance(at, params);
        ECLIPSE_SEVERANCES.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<EclipseSeveranceSource> eclipseSeverances() {
        return ECLIPSE_SEVERANCES.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(ECLIPSE_SEVERANCES);
    }

    /** Add a funeral nova, or return null if the ceiling is reached. */
    public static FxHandle addFuneralNova(Vec3 at, FuneralNovaParams params) {
        if (at == null || params == null || FUNERAL_NOVAS.size() >= MAX_PER_KIND) {
            return null;
        }
        FuneralNovaInstance instance = new FuneralNovaInstance(at, params);
        FUNERAL_NOVAS.add(instance);
        return instance;
    }

    /** Everything of this kind the renderer should draw, on top of the spell's own anchors. */
    public static List<FuneralNovaSource> funeralNovas() {
        return FUNERAL_NOVAS.isEmpty()
                ? Collections.emptyList()
                : new ArrayList<>(FUNERAL_NOVAS);
    }

    /**
     * How old an API-requested funeral nova is, in seconds.
     *
     * <p>On the registry rather than the source interface because the spell's own path gets this from
     * its anchor entity's tick age — putting it on the interface would mean the entity implementing a
     * method whose answer it already has in a different form.</p>
     */
    public static float funeralNovaAge(FuneralNovaSource source) {
        return source instanceof FuneralNovaInstance instance ? instance.ageSeconds() : 0.0F;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        // Paused single player: hold everything where it is rather than ageing it out behind the
        // pause screen, which is what the player would expect from a paused game.
        if (Minecraft.getInstance().isPaused()) {
            return;
        }
        for (List<? extends Advanceable> kind : ALL_KINDS) {
            kind.removeIf(instance -> !instance.advance());
        }
        // Not in ALL_KINDS: the sweep runs off a monotonic clock it started itself, so there is
        // nothing to step — only to notice when it has finished.
        ECLIPSE_SEVERANCES.removeIf(instance -> !instance.isAlive());
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide()) {
            ALL_KINDS.forEach(List::clear);
            ECLIPSE_SEVERANCES.clear();
        }
    }

    /**
     * One tick of ageing, so the tick loop does not need a branch per effect kind.
     *
     * <p>Package-private and not part of the API: {@code FxHandle} is what a caller gets, and it has
     * no business advancing anything.</p>
     */
    interface Advanceable {
        /** Advance one tick. Returns false once it should be dropped. */
        boolean advance();
    }
}
