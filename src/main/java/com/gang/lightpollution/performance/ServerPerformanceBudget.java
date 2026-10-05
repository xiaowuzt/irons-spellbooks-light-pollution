package com.gang.lightpollution.performance;

import com.gang.lightpollution.ExampleMod;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A shared per-server-tick budget for optional messages and absorption sparkles.
 * Damage, charge counters, targeting, motion packets and spell phase captions never use it.
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ServerPerformanceBudget {
    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.IntValue PARTICLE_BURSTS;
    private static final ForgeConfigSpec.IntValue DAMAGE_TEXTS;
    private static final ForgeConfigSpec.IntValue DIAGNOSTICS;
    private static final ServerDecorationBudget LOAD = new ServerDecorationBudget();
    private static long tickStarted;
    private static int particleBursts;
    private static int damageTexts;
    private static int diagnostics;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        ENABLED = builder.comment("Automatically reduce optional server presentation under sustained tick load.",
                        "Never changes damage, targets, attraction, charge counters or spell timing.")
                .define("adaptiveDecorationBudget", true);
        PARTICLE_BURSTS = builder.comment("Maximum decorative projectile-absorption particle broadcasts per tick.")
                .defineInRange("particleBurstsPerTick", 128, 0, 4096);
        DAMAGE_TEXTS = builder.comment("Maximum floating damage-number broadcasts per tick; health changes are unaffected.")
                .defineInRange("damageTextsPerTick", 128, 0, 4096);
        DIAGNOSTICS = builder.comment("Maximum optional diagnostic messages per tick.")
                .defineInRange("diagnosticsPerTick", 8, 0, 256);
        SPEC = builder.build();
    }

    private ServerPerformanceBudget() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beginTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.START) {
            tickStarted = System.nanoTime();
            particleBursts = 0;
            damageTexts = 0;
            diagnostics = 0;
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void endTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && tickStarted != 0L) {
            LOAD.recordTick((System.nanoTime() - tickStarted) / 1_000_000.0D);
            tickStarted = 0L;
        }
    }

    public static boolean allowAbsorptionParticles() {
        if (!safe(ENABLED, true)) {
            return true;
        }
        if (particleBursts >= LOAD.limit(safe(PARTICLE_BURSTS, 128))) {
            return false;
        }
        particleBursts++;
        return true;
    }

    public static boolean allowDamageText() {
        if (!safe(ENABLED, true)) {
            return true;
        }
        if (damageTexts >= LOAD.limit(safe(DAMAGE_TEXTS, 128))) {
            return false;
        }
        damageTexts++;
        return true;
    }

    public static boolean allowDiagnostic() {
        if (!safe(ENABLED, true)) {
            return true;
        }
        if (diagnostics >= LOAD.limit(safe(DIAGNOSTICS, 8))) {
            return false;
        }
        diagnostics++;
        return true;
    }

    /** A read-only view; reporting never consumes a decoration allowance or resets feedback. */
    public static Snapshot snapshot() {
        boolean enabled = safe(ENABLED, true);
        return new Snapshot(enabled, LOAD.averageMillis(), enabled ? LOAD.limit(100) : 100,
                particleBursts, LOAD.limit(safe(PARTICLE_BURSTS, 128)),
                damageTexts, LOAD.limit(safe(DAMAGE_TEXTS, 128)),
                diagnostics, LOAD.limit(safe(DIAGNOSTICS, 8)));
    }

    public record Snapshot(boolean enabled, double averageTickMillis, int budgetPercent,
                           int particleBurstsUsed, int particleBurstsLimit,
                           int damageTextsUsed, int damageTextsLimit,
                           int diagnosticsUsed, int diagnosticsLimit) {
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        LOAD.reset();
        tickStarted = 0L;
        particleBursts = damageTexts = diagnostics = 0;
    }

    private static <T> T safe(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T configured = value.get();
            return configured == null ? fallback : configured;
        } catch (IllegalStateException | NullPointerException notLoaded) {
            return fallback;
        }
    }
}
