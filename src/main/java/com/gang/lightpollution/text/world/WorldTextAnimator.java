package com.gang.lightpollution.text.world;

import com.gang.lightpollution.text.world.WorldTextConfig.AnimationType;
import net.minecraft.world.phys.Vec3;

/**
 * Advances one floating text's animation by a tick.
 *
 * <p>Ported from ArcaneVortex's {@code WorldTextAnimator}, credited in {@code CREDITS.txt}. The
 * easing curves are the standard set from easings.net, reproduced with the same constants.</p>
 *
 * <p>Each animation reports a scale, an alpha and a positional offset. The same curve is asked
 * about both the enter and the exit phase and told which it is being used for, so a pair like
 * {@code SCALE_IN}/{@code FADE_OUT} composes without either knowing about the other.</p>
 */
public final class WorldTextAnimator {
    /** What one curve contributes at a point in time. */
    private record Frame(float scale, float alpha, Vec3 offset) {
        private static final Frame NEUTRAL = new Frame(1.0F, 1.0F, Vec3.ZERO);
    }

    private WorldTextAnimator() {
    }

    public static void update(WorldTextPopup popup, long currentTick) {
        WorldTextPopup.Phase phase = popup.phase(currentTick);
        if (phase == WorldTextPopup.Phase.FINISHED) {
            popup.setFinished(true);
            return;
        }

        WorldTextConfig config = popup.getConfig();
        float progress = popup.phaseProgress(currentTick);
        Frame frame = switch (phase) {
            case ENTER -> curve(config.getEnterAnimation(), progress, config, false);
            case EXIT -> curve(config.getExitAnimation(), progress, config, true);
            // Holding: whatever the enter curve settled on, which for every curve here is its
            // finished state. Asking the exit curve would snap to its start instead.
            default -> curve(config.getEnterAnimation(), 1.0F, config, false);
        };

        popup.setScale(frame.scale() * config.getScale());
        popup.setAlpha(frame.alpha());
        popup.setOffset(frame.offset());

        if (config.isRotationAnimated()) {
            long duration = Math.max(1L, config.getRotationDuration());
            float turned = Math.min(1.0F, popup.age(currentTick) / (float) duration);
            popup.setRotation(popup.getTargetRotation() * easeOutCubic(turned));
        } else {
            popup.setRotation(popup.getTargetRotation());
        }
    }

    private static Frame curve(AnimationType type, float t, WorldTextConfig config,
                               boolean exiting) {
        return switch (type) {
            case NONE -> Frame.NEUTRAL;
            case FADE_IN -> new Frame(1.0F, exiting ? 1.0F : t, Vec3.ZERO);
            case FADE_OUT -> new Frame(1.0F, exiting ? 1.0F - t : 1.0F, Vec3.ZERO);
            case SCALE_IN -> new Frame(exiting ? 1.0F : easeOutBack(t), exiting ? 1.0F : t,
                    Vec3.ZERO);
            case SCALE_OUT -> new Frame(exiting ? 1.0F - easeInBack(t) : 1.0F,
                    exiting ? 1.0F - t : 1.0F, Vec3.ZERO);
            case SLIDE_UP -> new Frame(1.0F, exiting ? 1.0F : t,
                    new Vec3(0.0D, exiting ? 0.0D : (1.0F - easeOutCubic(t)) * -2.0F, 0.0D));
            case SLIDE_DOWN -> new Frame(1.0F, exiting ? 1.0F - t : 1.0F,
                    new Vec3(0.0D, exiting ? easeInCubic(t) * -2.0F : 0.0D, 0.0D));
            case SLIDE_LEFT -> new Frame(1.0F, exiting ? 1.0F : t,
                    new Vec3(exiting ? 0.0D : (1.0F - easeOutCubic(t)) * 2.0F, 0.0D, 0.0D));
            case SLIDE_RIGHT -> new Frame(1.0F, exiting ? 1.0F - t : 1.0F,
                    new Vec3(exiting ? easeInCubic(t) * 2.0F : 0.0D, 0.0D, 0.0D));
            case BOUNCE_IN -> exiting ? Frame.NEUTRAL
                    : new Frame(easeOutBounce(t), t, new Vec3(0.0D, (1.0F - t) * 1.5F, 0.0D));
            case BOUNCE_OUT -> exiting
                    ? new Frame(1.0F - easeInBounce(t), 1.0F - t, new Vec3(0.0D, t * 1.5F, 0.0D))
                    : Frame.NEUTRAL;
            case ELASTIC_IN -> exiting ? Frame.NEUTRAL
                    : new Frame(easeOutElastic(t), t, Vec3.ZERO);
            case ELASTIC_OUT -> exiting
                    ? new Frame(1.0F + easeInElastic(t) * 0.5F, 1.0F - t, Vec3.ZERO)
                    : Frame.NEUTRAL;
            case ZOOM_IN -> exiting ? Frame.NEUTRAL
                    : new Frame(easeOutCubic(t) * 1.2F, t, Vec3.ZERO);
            case ZOOM_OUT -> exiting
                    ? new Frame(1.0F + easeInCubic(t) * 2.0F, 1.0F - easeInQuad(t), Vec3.ZERO)
                    : Frame.NEUTRAL;
            // The scale part of ROTATE is the same overshoot as SCALE; the turn itself comes from
            // the config's rotation, applied in update rather than here.
            case ROTATE_IN -> exiting ? Frame.NEUTRAL : new Frame(easeOutBack(t), t, Vec3.ZERO);
            case ROTATE_OUT -> exiting
                    ? new Frame(1.0F - easeInBack(t), 1.0F - t, Vec3.ZERO)
                    : Frame.NEUTRAL;
            // Both FLIP curves were inverted in the original: the enter used
            // abs(cos(t * PI/2)), which runs 1 down to 0, so the text shrank to nothing over
            // exactly the phase where it should have been arriving. Swapped to sin for the enter
            // and cos for the exit, which is the squash the name promises.
            case FLIP_IN -> exiting ? Frame.NEUTRAL
                    : new Frame(Math.abs((float) Math.sin(t * Math.PI * 0.5)), t, Vec3.ZERO);
            case FLIP_OUT -> exiting
                    ? new Frame(Math.abs((float) Math.cos(t * Math.PI * 0.5)), 1.0F - t, Vec3.ZERO)
                    : Frame.NEUTRAL;
        };
    }

    private static float easeInQuad(float t) {
        return t * t;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float easeOutCubic(float t) {
        float inverse = 1.0F - t;
        return 1.0F - inverse * inverse * inverse;
    }

    private static float easeOutBack(float t) {
        float c1 = 1.70158F;
        float shifted = t - 1.0F;
        return 1.0F + (c1 + 1.0F) * shifted * shifted * shifted + c1 * shifted * shifted;
    }

    private static float easeInBack(float t) {
        float c1 = 1.70158F;
        return (c1 + 1.0F) * t * t * t - c1 * t * t;
    }

    private static float easeOutBounce(float t) {
        float n1 = 7.5625F;
        float d1 = 2.75F;
        if (t < 1.0F / d1) {
            return n1 * t * t;
        }
        if (t < 2.0F / d1) {
            float shifted = t - 1.5F / d1;
            return n1 * shifted * shifted + 0.75F;
        }
        if (t < 2.5F / d1) {
            float shifted = t - 2.25F / d1;
            return n1 * shifted * shifted + 0.9375F;
        }
        float shifted = t - 2.625F / d1;
        return n1 * shifted * shifted + 0.984375F;
    }

    private static float easeInBounce(float t) {
        return 1.0F - easeOutBounce(1.0F - t);
    }

    private static float easeOutElastic(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        float c4 = (float) (2.0 * Math.PI / 3.0);
        return (float) (Math.pow(2.0, -10.0 * t) * Math.sin((t * 10.0 - 0.75) * c4) + 1.0);
    }

    private static float easeInElastic(float t) {
        if (t <= 0.0F) {
            return 0.0F;
        }
        if (t >= 1.0F) {
            return 1.0F;
        }
        float c4 = (float) (2.0 * Math.PI / 3.0);
        return (float) (-Math.pow(2.0, 10.0 * t - 10.0) * Math.sin((t * 10.0 - 10.75) * c4));
    }
}
