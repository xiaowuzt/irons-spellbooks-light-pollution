package com.gang.lightpollution.client.perf;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.Locale;

/** Client-only visual budgets; never passed to shared shape or server gameplay configuration. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, value = Dist.CLIENT)
public final class AdaptiveVisualQuality {
    private static final AdaptiveQualityController CONTROL = new AdaptiveQualityController();
    private static ClientLevel level;
    private static long lastFrame, warmUntil;
    private static boolean validFrame;
    private static double seconds;
    private static float cpuScale = 1, gpuScale = 1;
    private static int targetFps = 60;

    private AdaptiveVisualQuality() {}

    public static void beginFrame() {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        if (level != mc.level) {
            reset();
            level = mc.level;
            warmUntil = now + 5_000_000_000L;
        }
        seconds = lastFrame == 0 ? 0 : (now - lastFrame) / 1_000_000_000.0;
        lastFrame = now;
        validFrame = level != null && !mc.isPaused() && mc.isWindowActive() && mc.getOverlay() == null;
        if (!validFrame) {
            CONTROL.suspend();
            warmUntil = now + 2_000_000_000L;
        }
        targetFps = Math.min(SpellLightConfig.adaptiveTargetFps,
                Math.max(1, mc.getWindow().getFramerateLimit()));
        if (mc.options.enableVsync().get()) {
            var monitor = mc.getWindow().findBestMonitor();
            if (monitor != null) targetFps = Math.min(targetFps, monitor.getCurrentMode().getRefreshRate());
        }
        targetFps = Math.max(1, targetFps);
        PerfTracker.beginFrame(validFrame);
    }

    /** Called before Window.updateDisplay: excludes buffer-swap/VSync and explicit frame limiting. */
    public static void endFrame() {
        PerfTracker.endFrame();
        var mode = SpellLightConfig.adaptiveMode;
        if (mode == AdaptiveQualityController.Mode.FIXED) {
            CONTROL.reset();
            cpuScale = gpuScale = 1;
            return;
        }
        if (!validFrame || System.nanoTime() < warmUntil) return;
        int limit = Math.max(0, SpellLightConfig.qualityPreset.ordinal() - SpellLightConfig.adaptiveMinimumQuality.ordinal());
        CONTROL.observe(seconds, PerfTracker.frameCpuMs(), PerfTracker.frameGpuMs(),
                PerfTracker.modCpuMs(), PerfTracker.modGpuMs(), 1000.0 / targetFps, mode, limit);
        float blend = (float) Math.min(1, Math.max(0, seconds) / 0.4);
        cpuScale += (scale(CONTROL.cpuReduction()) - cpuScale) * blend;
        gpuScale += (scale(CONTROL.gpuReduction()) - gpuScale) * blend;
    }

    private static float scale(int reduction) {
        return switch (reduction) { case 1 -> .78F; case 2 -> .55F; case 3 -> .35F; default -> 1; };
    }

    public static int curveSegments(int full, double distance, double radius) {
        if (SpellLightConfig.adaptiveMode == AdaptiveQualityController.Mode.FIXED) return full;
        if (full <= 16 || !Double.isFinite(distance) || !Double.isFinite(radius)) return full;
        double angular = Math.max(0.001, radius) / Math.max(0.001, distance);
        double projected = angular >= .25 ? 1 : angular >= .10 ? .75 : angular >= .04 ? .5 : .35;
        // Stable discrete segment counts prevent topology changing on every animation frame.
        double quality = scale(CONTROL.cpuReduction());
        int selected = (int) Math.ceil(full * Math.max(.25, projected * quality) / 4.0) * 4;
        return Math.min(full, Math.max(16, selected));
    }

    public static float volumeResolutionScale() {
        return switch (CONTROL.gpuReduction()) { case 1 -> .75F; case 2, 3 -> .5F; default -> 1; };
    }
    public static int volumeSteps(int full) { return Math.min(full, Math.max(Math.min(full, 32), Math.round(full * gpuScale))); }
    public static int lightLimit(int configured) { return Math.max(1, Math.min(configured, Math.round(configured * gpuScale))); }
    public static int shadowSteps(int configured) { return Math.min(configured, Math.max(8, Math.round(configured * gpuScale))); }
    public static int spatialPasses() { return CONTROL.gpuReduction() >= 3 ? 1 : 2; }
    public static int voxelScrubSlabs() { return Math.max(1, 4 - CONTROL.cpuReduction()); }
    public static float decorationScale() { return Math.min(cpuScale, gpuScale); }
    public static void invalidate() {
        CONTROL.suspend();
        warmUntil = System.nanoTime() + 5_000_000_000L;
    }
    public static void reset() {
        CONTROL.reset();
        PerfTracker.release();
        cpuScale = gpuScale = 1;
        lastFrame = 0;
        level = null;
    }
    @SubscribeEvent public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    public static String status() {
        return String.format(Locale.ROOT, "%s | target %d FPS | CPU -%d / GPU -%d | %s",
                SpellLightConfig.adaptiveMode, targetFps, CONTROL.cpuReduction(), CONTROL.gpuReduction(), CONTROL.reason());
    }
}
