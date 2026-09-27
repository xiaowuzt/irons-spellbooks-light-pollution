# Using the API from another mod

Seventeen of this pack's effects, its animated text and its tooltip frames are available to other
mods. Visuals only — no damage, no entity, no spell.

## Dependency

Every project on Modrinth is automatically served from Modrinth's Maven, so no extra repository is
needed on this side.

```gradle
repositories {
    maven { url = "https://api.modrinth.com/maven" }
}

dependencies {
    // compileOnly, not implementation: this is a soft dependency, and the jar should not be
    // bundled into yours.
    compileOnly fg.deobf("maven.modrinth:irons-spellbooks-light-pollution:1.7.1")
}
```

The artifact id is this project's Modrinth slug. Check it against the project URL — the slug is the
last path segment of `modrinth.com/mod/<slug>` — since a slug can be changed after a project is
created.

Also declare it in `mods.toml`, so Forge tells the player what is missing rather than crashing:

```toml
[[dependencies.yourmodid]]
    modId = "irons_spellbooks_light_pollution"
    mandatory = false
    versionRange = "[1.7.1,)"
    ordering = "NONE"
    side = "CLIENT"
```

If Modrinth's Maven is unavailable to you, the release jar works as a plain file dependency:
`compileOnly fg.deobf(files("libs/irons_spellbooks_light_pollution-1.7.1.jar"))`.

## Guarding the calls

Every API method here is client-side. With `mandatory = false` the classes may be absent at runtime,
so calls must sit behind a check *and* in a separate class — the JVM resolves a class's references
when the enclosing method is first verified, which can happen before your `if` runs.

```java
public final class LightPollutionCompat {
    private static final boolean PRESENT =
            ModList.get().isLoaded("irons_spellbooks_light_pollution");

    public static void magnetarAt(Vec3 pos, float yaw) {
        if (PRESENT) {
            Impl.magnetar(pos, yaw);
        }
    }

    /** Separate class, so it is only loaded when PRESENT is true. */
    private static final class Impl {
        static void magnetar(Vec3 pos, float yaw) {
            LightPollutionFx.magnetar(pos, yaw);
        }
    }
}
```

## Effects

```java
FxHandle handle = LightPollutionFx.magnetar(pos, 143.0F);
if (handle == null) {
    return;  // Refused: 32 of that kind are already live. Cope, do not assume.
}

handle.setPosition(newPos);  // Cheap; safe every frame
handle.isAlive();
handle.remove();             // Idempotent
```

Each effect has two overloads: one taking a full params record, one taking the least it needs and
using the spell's own proportions.

| | |
|---|---|
| Structures that hold | `magnetar` `microquasar` `pinwheel` `quasarJet` `crabNebula` `constellation` `stellarConvergence` `worldTree` |
| Events that run and finish | `tidalDisruption` `helixNebula` `secondSun` `singularity` `skyCollapse` `starfall` `leviathan` `eclipseSeverance` `funeralNova` |

Params records carry an azimuth or bearing, a seed, a `scale` and a `lifetimeTicks`, with `scale(…)`
and `lifetime(…)` to change them. Pass `lifetime(0)` to draw until removed by hand.

```java
LightPollutionFx.worldTree(pos,
        WorldTreeParams.of(seed, groundY).scale(1.5F).lifetime(0));
```

### Two effects that do not fit the pattern

`eclipseSeverance` and `funeralNova` were ported from another mod with their timelines intact, so
their durations are fixed and there is no `lifetimeTicks`. `eclipseSeverance.setPosition` **throws**
rather than doing nothing: its particles are integrated in world space from the moment it starts, so
moving the origin would leave them behind. Remove it and make a new one.

### Ground level

`skyCollapse`, `starfall` and `worldTree` drop pieces onto the ground. The spell samples the world's
heightmap so its slabs sit on hillsides; an API instance uses the flat `groundY` in its params
instead, because sampling terrain would restrict these to positions in loaded chunks and fail
silently elsewhere.

## Animated text

```java
Component title = LightPollutionText.styled("Legendary", LightPollutionText.Style.RAINBOW);
```

The returned Component animates wherever Minecraft draws it — a tooltip, a chat line, a book. Ten
styles: `RAINBOW` `GLITCH` `CYBER` `MAGICAL_NEON` `HOLOGRAPHIC` `ENERGY_BAR` `LAVA` `PARCHMENT`
`RED_SPEED_NEON` `SYNTHWAVE_NEON`. The player can switch any of them off in this mod's client
config, so do not depend on one being visible.

```java
LightPollutionText.inWorld(title, pos);  // Floats at a world position, then ages out
```

## Tooltip frames

```java
LightPollutionFx.registerTooltipAccent(MyItems.RELIC.get(), 0x7FB2FF);
LightPollutionFx.registerTooltipAccent(MyTags.MY_RELICS, 0xFF6A3D);
```

Safe during mod setup. You choose the colour; the *style* stays the player's, set in the config or
with `/lightpollution frame`. A caller who could force a style would be overriding a preference, and
two mods each forcing their own would leave the player with no way to get a consistent inventory.

These frames take the tooltip over rather than layering onto it, so they compete with mods that do
the same. With ModernUI installed most styles win and the one background-only style stands down; a
registered item inherits that behaviour.

## Multiplayer

Nothing here is synced. These effects are drawn, not simulated, so there is nothing on the server to
call. To show one to other players, send your own packet and call this in its client handler. There
is deliberately no convenience wrapper for that: one would work in single player and quietly fail in
multiplayer, which is worse than none.

## What is not open

Cosmic Horseshoe, Silhouette and Starless are full-screen passes with no geometry and no position, so
a handle with `setPosition` on it would be a lie. Gargantua's lens copies the framebuffer twice per
instance. Celestial Judgment, Chromatic Accretion and Stargrave Singularity draw through Minecraft's
own entity renderers and key their look off entity UUIDs. These need a different interface, not a
worse version of this one.

## Stability

`com.gang.lightpollution.api` is the contract. Everything under `fx` and below is not, and will
change. One exception, named because it would otherwise be a trap: `WorldTextConfig` appears in
`LightPollutionText`'s signatures despite living outside that package — treat it as part of the
contract too.

## Trying it

Every effect has a command that goes through the same public entry point a caller would use, which is
the only way to know the seam works end to end:

```
/lightpollution fx <name> [scale]
/lightpollution fx clear
/lightpollution text <style> [message]
```
