package com.gang.lightpollution.client.renderer;

import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Bounded top-k selection with temporal preference; moving light records need not retain identity. */
public final class StableLightSelector {
    private final SpellLightEmitter.Light[] selected = new SpellLightEmitter.Light[512];
    private final SpellLightEmitter.Light[] previous = new SpellLightEmitter.Light[512];
    private final double[] scores = new double[512];
    private int previousCount;

    public List<SpellLightEmitter.Light> select(List<SpellLightEmitter.Light> candidates, Vec3 camera, int limit) {
        int cap = Math.max(1, Math.min(selected.length, limit));
        int count = 0;
        for (var light : candidates) {
            double r2 = light.radius() * (double) light.radius();
            double distance2 = camera.distanceToSqr(light.position());
            double score = light.intensity() * Math.min(1, r2 / Math.max(1, distance2));
            if (!Double.isFinite(score) || score <= 0 || light.radius() <= 0) continue;
            for (int i = 0; i < previousCount; i++) {
                var old = previous[i];
                double tolerance = Math.max(1, light.radius() * .08);
                if (Math.abs(old.radius() - light.radius()) <= Math.max(1, light.radius() * .15)
                        && Math.abs(old.red() - light.red()) + Math.abs(old.green() - light.green()) + Math.abs(old.blue() - light.blue()) < .15
                        && old.position().distanceToSqr(light.position()) <= tolerance * tolerance) {
                    score *= 1.20;
                    break;
                }
            }
            int at = count;
            while (at > 0 && score > scores[at - 1]) at--;
            if (at >= cap) continue;
            int moved = Math.min(count, cap - 1) - at;
            if (moved > 0) {
                System.arraycopy(selected, at, selected, at + 1, moved);
                System.arraycopy(scores, at, scores, at + 1, moved);
            }
            selected[at] = light;
            scores[at] = score;
            count = Math.min(cap, count + 1);
        }
        List<SpellLightEmitter.Light> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) { result.add(selected[i]); previous[i] = selected[i]; }
        Arrays.fill(previous, count, previous.length, null);
        Arrays.fill(selected, null);
        previousCount = count;
        return result;
    }

    public void clear() {
        Arrays.fill(selected, null);
        Arrays.fill(previous, null);
        previousCount = 0;
    }
}
