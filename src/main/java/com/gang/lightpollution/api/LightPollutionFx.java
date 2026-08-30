package com.gang.lightpollution.api;

import com.gang.lightpollution.fx.FxRegistry;
import com.gang.lightpollution.fx.TooltipAccentRegistry;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Ask this mod to draw one of its effects for you.
 *
 * <p>The effects here are the visuals only — no damage, no entity, no spell. What a caller gets is the
 * effect hanging in the air at a position they choose, and a {@link FxHandle} to move it or take it
 * away.</p>
 *
 * <h2>Client only</h2>
 *
 * <p>Every method here must be called on the client. These effects are drawn rather than simulated,
 * so there is nothing on the server to call and nothing is synced. To show an effect to other
 * players, send your own packet and call this in its client handler. This deliberately does not do
 * that for you: a convenience wrapper that silently only worked in single player would be worse than
 * no wrapper.</p>
 *
 * <h2>What stays on this side</h2>
 *
 * <p>Which render stage to draw at, how to survive a shader pack compositing over the effect, which
 * shader and vertex format each piece needs, how to restore GL state afterwards. Those took several
 * attempts to get right and are not worth re-deriving per caller.</p>
 *
 * <h2>Stability</h2>
 *
 * <p>This package is the contract; everything under {@code fx} and below is not, and will change. If
 * a method returns null it means the request was refused — usually because too many of that effect
 * are already live — and a caller should cope rather than assume.</p>
 *
 * <p>One exception, named because it would otherwise be a trap: {@code WorldTextConfig} appears in
 * {@link LightPollutionText}'s signatures despite living outside this package. It is a settled
 * builder rather than something being reworked, and copying thirty setters into a parallel class
 * would have been worse than the inconsistency. Treat that one class as part of the contract too.</p>
 */
public final class LightPollutionFx {
    private LightPollutionFx() {
    }

    /**
     * Draw a tidal disruption: a star stretched into a long thin stream that wraps back around and
     * lashes, with a flare when the bound debris returns.
     *
     * @param at     where the hole is, in world coordinates
     * @param params see {@link TidalDisruptionParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle tidalDisruption(Vec3 at, TidalDisruptionParams params) {
        return FxRegistry.addTidalDisruption(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle tidalDisruption(Vec3 at, float azimuthDegrees) {
        return tidalDisruption(at, TidalDisruptionParams.of(azimuthDegrees));
    }

    /**
     * Draw a planetary nebula: an expanding shell of cometary knots in two nested rings, each knot
     * with its tail streaming away from the white dwarf at the centre.
     *
     * <p>The shell grows over roughly the first three hundred ticks and then holds, so this reads as
     * an event rather than as scenery. For a static one, pass an age-independent position and a scale
     * rather than expecting it to sit still.</p>
     *
     * @param at     where the white dwarf is, in world coordinates
     * @param params see {@link HelixNebulaParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle helixNebula(Vec3 at, HelixNebulaParams params) {
        return FxRegistry.addHelixNebula(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle helixNebula(Vec3 at, float azimuthDegrees, int seed) {
        return helixNebula(at, HelixNebulaParams.of(azimuthDegrees, seed));
    }

    /**
     * Draw a supernova remnant: a cage of tangled filament loops around a pulsar, with the pulsar's
     * wind nebula glowing inside it and pulsing on its rhythm.
     *
     * <p>Barely expands, so unlike the planetary nebula this one is usable as something that sits
     * there. Pass {@code params.lifetime(0)} to keep it until removed by hand.</p>
     *
     * @param at     where the pulsar is, in world coordinates
     * @param params see {@link CrabNebulaParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle crabNebula(Vec3 at, CrabNebulaParams params) {
        return FxRegistry.addCrabNebula(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle crabNebula(Vec3 at, int seed) {
        return crabNebula(at, CrabNebulaParams.of(seed));
    }


    /**
     * Draw a magnetar: closed dipole field loops leaving one magnetic pole and arriving at the
     * other, winding as the field is stressed and letting go in a flare.
     *
     * @param at     where the neutron star is, in world coordinates
     * @param params see {@link MagnetarParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle magnetar(Vec3 at, MagnetarParams params) {
        return FxRegistry.addMagnetar(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle magnetar(Vec3 at, float azimuthDegrees) {
        return magnetar(at, MagnetarParams.of(azimuthDegrees));
    }

    /**
     * Draw a microquasar: two opposed jets sweeping out a cone as the disc precesses, so the blobs
     * already launched trace a helix — each keeps the direction the jet had when it left.
     *
     * @param at     where the black hole is, in world coordinates
     * @param params see {@link MicroquasarParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle microquasar(Vec3 at, MicroquasarParams params) {
        return FxRegistry.addMicroquasar(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle microquasar(Vec3 at, float azimuthDegrees) {
        return microquasar(at, MicroquasarParams.of(azimuthDegrees));
    }

    /**
     * Draw a pinwheel galaxy: two logarithmic spiral arms in a tilted plane, turning as a whole.
     *
     * @param at     where the galactic core is, in world coordinates
     * @param params see {@link PinwheelParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle pinwheel(Vec3 at, PinwheelParams params) {
        return FxRegistry.addPinwheel(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle pinwheel(Vec3 at, float azimuthDegrees) {
        return pinwheel(at, PinwheelParams.of(azimuthDegrees));
    }

    /**
     * Draw a relativistic jet: a narrow beam with bright knots travelling along it and a lobe where
     * it terminates. The knots are spaced by the apparent superluminal speed rather than the real one,
     * which is what a viewer near the jet's axis would actually see.
     *
     * @param at     where the nucleus is, in world coordinates
     * @param params see {@link QuasarJetParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle quasarJet(Vec3 at, QuasarJetParams params) {
        return FxRegistry.addQuasarJet(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle quasarJet(Vec3 at, float azimuthDegrees) {
        return quasarJet(at, QuasarJetParams.of(azimuthDegrees));
    }


    /**
     * Draw a second sun: a disc that climbs from the horizon, swells and cools, then detonates and
     * leaves a collapsing remnant.
     *
     * <p>Drawn as sky rather than as an object, so the position given is only used to decide which
     * players are near enough to see it — the disc itself is placed along the bearing at sky distance.
     * That also means {@link FxHandle#setPosition} moves who can see it, not where it appears.</p>
     *
     * @param at     roughly where the effect belongs, in world coordinates
     * @param params see {@link SecondSunParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle secondSun(Vec3 at, SecondSunParams params) {
        return FxRegistry.addSecondSun(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle secondSun(Vec3 at, float bearingRadians) {
        return secondSun(at, SecondSunParams.of(bearingRadians));
    }

    /**
     * Draw a collapsing singularity: a core that opens, contracts and lashes lightning, then
     * collapses into a blast with three decelerating shockwave fronts.
     *
     * @param at     where the core is, in world coordinates
     * @param params see {@link SingularityParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle singularity(Vec3 at, SingularityParams params) {
        return FxRegistry.addSingularity(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle singularity(Vec3 at, int seed) {
        return singularity(at, SingularityParams.of(seed));
    }


    /**
     * Draw a sky collapse: the firmament fractures and sheds slabs one after another onto the ground,
     * with a keystone last.
     *
     * <p>The slabs land on the flat height named in the params, not on the terrain. The spell samples
     * the world's heightmap so its slabs sit on hillsides; doing that here would mean this method could
     * only be called where chunks are loaded, and failing silently otherwise. Pass the height you want,
     * or place the effect where the ground is level.</p>
     *
     * @param at     the centre the slabs scatter around, in world coordinates
     * @param params see {@link SkyCollapseParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle skyCollapse(Vec3 at, SkyCollapseParams params) {
        return FxRegistry.addSkyCollapse(at, params);
    }

    /** With the spell's own proportions and timing, landing on the height given. */
    @Nullable
    public static FxHandle skyCollapse(Vec3 at, int seed, double groundY) {
        return skyCollapse(at, SkyCollapseParams.of(seed, groundY));
    }

    /**
     * Draw a meteor shower: meteors entering at a slant one after another, with one larger arrival at
     * the end.
     *
     * <p>Lands on the flat height named in the params, for the same reason as
     * {@link #skyCollapse}.</p>
     *
     * @param at     the centre the meteors scatter around, in world coordinates
     * @param params see {@link StarfallParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle starfall(Vec3 at, StarfallParams params) {
        return FxRegistry.addStarfall(at, params);
    }

    /** With the spell's own proportions and timing, landing on the height given. */
    @Nullable
    public static FxHandle starfall(Vec3 at, int seed, double groundY) {
        return starfall(at, StarfallParams.of(seed, groundY));
    }


    /**
     * Draw a constellation: a star that falls in from high and far, eases into a ring orbit, and
     * completes one turn over its life.
     *
     * @param at     where the orbit is centred, in world coordinates
     * @param params see {@link ConstellationParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle constellation(Vec3 at, ConstellationParams params) {
        return FxRegistry.addConstellation(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle constellation(Vec3 at, float spinOffsetRadians) {
        return constellation(at, ConstellationParams.of(spinOffsetRadians));
    }

    /**
     * Draw a stellar convergence: nine stars lighting one after another across a shell, contracting
     * into a net, then pouring themselves into a column of light.
     *
     * @param at     where the shell is centred, in world coordinates
     * @param params see {@link StellarConvergenceParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle stellarConvergence(Vec3 at, StellarConvergenceParams params) {
        return FxRegistry.addStellarConvergence(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle stellarConvergence(Vec3 at, int seed) {
        return stellarConvergence(at, StellarConvergenceParams.of(seed));
    }

    /**
     * Draw a leviathan: a long body swimming in with an anguilliform undulation, rearing its front
     * third, then plunging its jaws onto the anchor point and unravelling.
     *
     * <p>The body arrives from behind the anchor along the bearing and travels forward past it, so
     * give it room — it is ninety blocks long at scale 1.</p>
     *
     * @param at     the point the jaws close on, in world coordinates
     * @param params see {@link LeviathanParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle leviathan(Vec3 at, LeviathanParams params) {
        return FxRegistry.addLeviathan(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle leviathan(Vec3 at, float bearingRadians, int seed) {
        return leviathan(at, LeviathanParams.of(bearingRadians, seed));
    }

    /**
     * Draw a world tree: a trunk rising from a point, roots gripping the ground, then branches
     * unfolding order by order with a leaf crown opening behind them.
     *
     * <p>The branch recursion is walked once when the effect is created, not per frame. Roots lie on
     * the flat height named in the params rather than following terrain, for the same reason as
     * {@link #skyCollapse}.</p>
     *
     * @param at     where the trunk rises from, in world coordinates
     * @param params see {@link WorldTreeParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle worldTree(Vec3 at, WorldTreeParams params) {
        return FxRegistry.addWorldTree(at, params);
    }

    /** With the spell's own proportions and timing, roots lying on the height given. */
    @Nullable
    public static FxHandle worldTree(Vec3 at, int seed, double groundY) {
        return worldTree(at, WorldTreeParams.of(seed, groundY));
    }


    /**
     * Draw an eclipse severance: a wide arc swept through the air with speed lines, particles and
     * lightning behind it, then a ring and a burst where it lands.
     *
     * <h3>Fixed duration, and it cannot be moved</h3>
     *
     * <p>Runs for about 1.45 seconds off its own monotonic clock, so there is no lifetime to set. Its
     * particles are integrated in world space from the moment it starts, which means
     * {@link FxHandle#setPosition} throws rather than silently leaving them behind — remove it and make
     * a new one instead.</p>
     *
     * @param at     where the sweep is centred, in world coordinates
     * @param params see {@link EclipseSeveranceParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle eclipseSeverance(Vec3 at, EclipseSeveranceParams params) {
        return FxRegistry.addEclipseSeverance(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle eclipseSeverance(Vec3 at, float facingYawDegrees, int seed) {
        return eclipseSeverance(at, EclipseSeveranceParams.of(facingYawDegrees, seed));
    }

    /**
     * Draw a funeral nova: a ten-stage collapse running just over seventeen seconds — a magic circle,
     * a tower, a black hole, an accretion disc, a collapse, a void, a flash, a hypernova, an afterglow,
     * a fade.
     *
     * <p>The stage boundaries are fixed, so there is no lifetime to set. This one can be moved while it
     * runs. It also drives a full-screen post pass, so only a few can be visible at once; extra ones
     * are drawn without it rather than refused.</p>
     *
     * @param at     where the collapse is centred, in world coordinates
     * @param params see {@link FuneralNovaParams#of}
     * @return a handle, or null if the request was refused
     */
    @Nullable
    public static FxHandle funeralNova(Vec3 at, FuneralNovaParams params) {
        return FxRegistry.addFuneralNova(at, params);
    }

    /** With the spell's own proportions and timing. */
    @Nullable
    public static FxHandle funeralNova(Vec3 at, int seed) {
        return funeralNova(at, FuneralNovaParams.of(seed));
    }

    /**
     * Give one of your items this mod's tooltip frame, in a colour you choose.
     *
     * <p>You pick the colour; the <em>style</em> is the player's, set in this mod's client config or
     * with {@code /lightpollution frame}. That is deliberate — a caller who could force a style would
     * be overriding a preference the player set, and two mods each forcing their own would leave the
     * player with no way to get a consistent inventory.</p>
     *
     * <h3>Other tooltip mods</h3>
     *
     * <p>These frames take the tooltip over rather than layering onto it, so they compete with mods
     * that do the same. With ModernUI installed, most styles win and the one background-only style
     * stands down. Register an item here and it inherits that behaviour; this cannot make two takeover
     * renderers coexist, because only one of them can draw the box.</p>
     *
     * <p>Safe to call during mod setup. Registering the same item twice keeps the later colour.</p>
     *
     * @param item      the item to frame
     * @param accentRgb packed 0xRRGGBB; the alpha byte is ignored
     */
    public static void registerTooltipAccent(Item item, int accentRgb) {
        TooltipAccentRegistry.register(item, accentRgb);
    }

    /**
     * Give every item in a tag this mod's tooltip frame.
     *
     * <p>For when the set is decided by the pack rather than by you. An exact item registration beats
     * a tag; between two tags that both match, the one registered first wins.</p>
     *
     * @param tag       the tag whose items should be framed
     * @param accentRgb packed 0xRRGGBB; the alpha byte is ignored
     */
    public static void registerTooltipAccent(TagKey<Item> tag, int accentRgb) {
        TooltipAccentRegistry.register(tag, accentRgb);
    }
}
