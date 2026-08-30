package com.gang.lightpollution.text;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext).
 *
 * Renamed from ModernUiDynamicTextRenderer, which was misleading: despite the name this is the
 * core per-character renderer and touches nothing from ModernUI. It was named for the layout
 * engine it also fed, and that compat layer was left behind in the port.
 */

import com.gang.lightpollution.text.anim.AnimSpec;
import com.gang.lightpollution.text.anim.GlyphState;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import com.gang.lightpollution.mixin.FontInvoker;
import com.gang.lightpollution.text.EffectStyle;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;

/**
 * 原版与 ModernUI 共用的动态文字桥接器。
 *
 * <p>控制码在进入这里前已经编码进 Style.font。渲染器逐个可见字形计算动画，
 * 然后以清理过效果元数据的样式递归交还当前 Font，因而能够保留 ModernUI 的中文字体回退，
 * 同时让物品标题、tooltip、Lore、FTB 文本与普通 Component 使用完全一致的效果。</p>
 */
public final class DynamicTextRenderer {
    private static final int[] MAGIC_PALETTE = {0x8F39FF, 0xD45CFF, 0x15E6FF, 0x5033D8};
    private static final int[] HOLOGRAPHIC_PALETTE = {0xFF91E8, 0x8DEBFF, 0xFFF2A8, 0xA7FFCF, 0xC9A8FF};
    private static final int[] LAVA_PALETTE = {0xFF3B0A, 0xFF7A0A, 0xFFD43B, 0xFF5A12};
    private static final int[] PARCHMENT_PALETTE = {0xB45CFF, 0x4B9BFF, 0xFFE15A, 0x64E879};
    private static final int SYNTHWAVE_CYAN = 0x00EFFF;
    private static final int SYNTHWAVE_MAGENTA = 0xFF2BD6;

    private DynamicTextRenderer() {
    }

    public static int draw(
            Font font,
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int backgroundColor,
            int packedLight
    ) {
        int normalizedColor = normalizeColor(color);
        float[] redSpeedBounds = effectBounds(font, text, x, EffectStyle.RED_SPEED_NEON);
        float[] synthwaveBounds = effectBounds(font, text, x, EffectStyle.SYNTHWAVE_NEON);
        if (hasBounds(redSpeedBounds)) {
            drawRedSpeedBackdrop(
                    font, redSpeedBounds[0], redSpeedBounds[1], y, normalizedColor,
                    pose, buffers, mode, packedLight
            );
        }
        if (hasBounds(synthwaveBounds)) {
            drawSynthwaveBackdrop(
                    font, synthwaveBounds[0], synthwaveBounds[1], y, normalizedColor,
                    pose, buffers, mode, packedLight
            );
        }

        float[] cursor = {x};
        int[] visibleIndex = {0};
        // Identifies this run for effects that need progress across frames. The visible text is the
        // right key: the same string drawn twice is one reveal, which is what a typewriter means, and
        // a different string is a different reveal. Built lazily — only the typewriter reads it, and
        // building it walks the whole sequence.
        String[] runKey = {null};
        boolean[] runKeyBuilt = {false};

        text.accept((stringIndex, encodedStyle, codePoint) -> {
            Style baseStyle = EffectStyle.clean(encodedStyle);
            FormattedCharSequence measuredGlyph = FormattedCharSequence.codepoint(codePoint, baseStyle);
            float advance = font.getSplitter().stringWidth(measuredGlyph);

            int requestedMask = EffectStyle.mask(encodedStyle);
            int mask = requestedMask == 0 ? 0 : DynamicTextRuntime.effectiveMask(requestedMask);
            int glyphIndex = visibleIndex[0]++;
            float drawX = cursor[0];
            float drawY = y;
            int primaryColor = normalizedColor;
            Style primaryStyle = baseStyle;

            // The animated effects, which move and recolour the glyph rather than drawing extra
            // copies of it. Applied before the ten shading effects below, so those still decide the
            // final look of anything they touch.
            AnimSpec anim = AnimSpec.byId(EffectStyle.animId(encodedStyle));
            Matrix4f glyphPose = pose;
            if (anim != null) {
                GlyphState glyph = ANIM_STATE.get();
                // False, not the caller's flag. This means "we are drawing the shadow pass", and we
                // never draw one: each glyph goes to drawInBatch with the flag and vanilla draws both
                // passes itself, dimming the shadow with its own factor. Passing the flag through made
                // it read "this text has a shadow", so every colour effect bailed out on shadowed text
                // — which is most text.
                glyph.reset(codePoint, glyphIndex, false,
                        ((normalizedColor >> 16) & 0xFF) / 255.0F,
                        ((normalizedColor >> 8) & 0xFF) / 255.0F,
                        (normalizedColor & 0xFF) / 255.0F,
                        ((normalizedColor >>> 24) & 0xFF) / 255.0F,
                        anim.needsRunKey() ? runKeyOf(text, runKey, runKeyBuilt) : null);
                anim.apply(glyph, Util.getMillis());
                // Fully transparent means hidden, which is how the typewriter conceals what it has
                // not yet revealed. Returning here rather than drawing with alpha 0 because
                // Font.adjustColor turns an alpha under 4 back into opaque, so the glyph would show.
                if (glyph.a <= 0.0F) {
                    cursor[0] += advance;
                    return true;
                }
                drawX += glyph.x;
                drawY += glyph.y;
                primaryColor = glyph.packedColour();
                // Written onto the Style as well as passed as the argument. Font's renderer reads the
                // colour off the Style whenever it has one and only falls back to the argument when it
                // does not — so on text with any colour code, passing it as the argument alone means
                // every recolouring effect is computed and then thrown away.
                primaryStyle = primaryStyle.withColor(primaryColor & 0xFFFFFF);
                // Two different pivots, as upstream has them: a swing turns about the glyph's centre,
                // a pendulum hangs from its top. Using the centre for both makes the pendulum spin.
                if (glyph.rotation != 0.0F) {
                    glyphPose = turned(pose, drawX + advance * 0.5F, drawY + ANIM_LINE_HEIGHT * 0.5F,
                            glyph.rotation);
                } else if (glyph.pendulum != 0.0F) {
                    glyphPose = turned(pose, drawX + advance * 0.5F, drawY,
                            glyph.pendulum * ANIM_RAD_TO_DEG);
                }
                if (glyph.glowPasses > 0 && glyph.glowRadius > 0.0F) {
                    // Saturated, not the glyph's own colour: white text glowing white shows nothing.
                    int glowColour = multiplyAlpha(replaceRgb(primaryColor, saturate(primaryColor)),
                            glyph.glowAlpha);
                    for (int pass = 0; pass < glyph.glowPasses; pass++) {
                        double around = Math.PI * 2.0 * pass / glyph.glowPasses;
                        drawGlyph(font, codePoint, primaryStyle,
                                drawX + (float) Math.cos(around) * glyph.glowRadius,
                                drawY + (float) Math.sin(around) * glyph.glowRadius,
                                glowColour, false, glyphPose, buffers, mode, 0, packedLight);
                    }
                }
            }

            if (mask != 0) {
                long frame = DynamicTextRuntime.animationFrame();
                long primaryHash = DynamicTextRuntime.hash(frame, glyphIndex, codePoint, mask);
                float pulse = DynamicTextRuntime.pulse(glyphIndex, 1.25D);

                if ((mask & EffectStyle.CYBER) != 0) {
                    drawBackdrop(
                            font, baseStyle, drawX - 0.75F, drawY - 0.65F,
                            drawX + advance + 0.75F, drawY + 8.75F,
                            0x04070D, 0.54F, pose, buffers, mode, packedLight
                    );
                    int fragmentAlpha = multiplyAlpha(normalizedColor, 0.46F);
                    drawGlyph(font, codePoint, baseStyle.withColor(0xFF1738), drawX - 0.90F, drawY + 0.25F,
                            replaceRgb(fragmentAlpha, 0xFF1738), false, pose, buffers, mode, 0, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0x00E8FF), drawX + 0.90F, drawY - 0.20F,
                            replaceRgb(fragmentAlpha, 0x00E8FF), false, pose, buffers, mode, 0, packedLight);
                    primaryColor = replaceRgb(primaryColor, 0xE8FBFF);
                    primaryStyle = primaryStyle.withColor(0xE8FBFF).withBold(true);

                    if (DynamicTextRuntime.glitchActive(primaryHash)) {
                        float fragmentY = DynamicTextRuntime.unit(
                                DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 317)
                        ) < 0.5F ? -0.65F : 0.65F;
                        drawGlyph(font, codePoint, baseStyle.withColor(0xFFFFFF), drawX + 1.55F, drawY + fragmentY,
                                multiplyAlpha(replaceRgb(normalizedColor, 0xFFFFFF), 0.22F), false,
                                pose, buffers, mode, 0, packedLight);
                    }
                }

                if ((mask & EffectStyle.RAINBOW) != 0) {
                    int rainbow = DynamicTextRuntime.rainbowColor(glyphIndex);
                    primaryStyle = primaryStyle.withColor(rainbow);
                    primaryColor = replaceRgb(primaryColor, rainbow);
                }

                if ((mask & EffectStyle.MAGIC) != 0) {
                    int magic = DynamicTextRuntime.paletteColor(glyphIndex, 0.38D, MAGIC_PALETTE);
                    int aura = DynamicTextRuntime.mixColor(0x722DFF, 0x11E9FF, pulse);
                    drawGlow(font, codePoint, baseStyle, drawX, drawY, aura,
                            multiplyAlpha(normalizedColor, 0.20F), 0.85F, pose, buffers, mode, packedLight);
                    primaryStyle = primaryStyle.withColor(magic).withBold(true);
                    primaryColor = replaceRgb(primaryColor, magic);
                }

                if ((mask & EffectStyle.HOLOGRAPHIC) != 0) {
                    int holographic = DynamicTextRuntime.paletteColor(glyphIndex, 0.56D, HOLOGRAPHIC_PALETTE);
                    drawOutline(font, codePoint, baseStyle, drawX, drawY, 0x202342,
                            multiplyAlpha(normalizedColor, 0.72F), 0.55F, pose, buffers, mode, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0xFFFFFF), drawX - 0.20F, drawY - 0.55F,
                            multiplyAlpha(replaceRgb(normalizedColor, 0xFFFFFF), 0.48F), false,
                            pose, buffers, mode, 0, packedLight);
                    primaryStyle = primaryStyle.withColor(holographic).withBold(true);
                    primaryColor = replaceRgb(primaryColor, holographic);
                }

                if ((mask & EffectStyle.ENERGY_BAR) != 0) {
                    float tilt = (glyphIndex % 9 - 4) * 0.075F;
                    drawBackdrop(
                            font, baseStyle, drawX - 1.05F, drawY - 1.15F + tilt,
                            drawX + advance + 1.20F, drawY + 9.15F + tilt,
                            0x00A9C8, 0.34F, pose, buffers, mode, packedLight
                    );
                    drawBackdrop(
                            font, baseStyle, drawX - 0.75F, drawY - 0.35F + tilt,
                            drawX + advance + 0.95F, drawY + 0.20F + tilt,
                            0x73F4FF, 0.78F, pose, buffers, mode, packedLight
                    );
                    drawOutline(font, codePoint, baseStyle, drawX, drawY, 0x003B52,
                            multiplyAlpha(normalizedColor, 0.94F), 0.65F, pose, buffers, mode, packedLight);
                    primaryStyle = primaryStyle.withColor(0xE7FFFF).withBold(true);
                    primaryColor = replaceRgb(primaryColor, 0xE7FFFF);
                }

                if ((mask & EffectStyle.LAVA) != 0) {
                    int lava = DynamicTextRuntime.paletteColor(glyphIndex, 0.44D, LAVA_PALETTE);
                    drawGlow(font, codePoint, baseStyle, drawX, drawY, 0xFF2C00,
                            multiplyAlpha(normalizedColor, 0.22F + pulse * 0.10F), 0.85F,
                            pose, buffers, mode, packedLight);
                    drawOutline(font, codePoint, baseStyle, drawX, drawY, 0x4A0800,
                            multiplyAlpha(normalizedColor, 0.88F), 0.62F, pose, buffers, mode, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0xFFF09A), drawX - 0.15F, drawY - 0.45F,
                            multiplyAlpha(replaceRgb(normalizedColor, 0xFFF09A), 0.42F), false,
                            pose, buffers, mode, 0, packedLight);
                    primaryStyle = primaryStyle.withColor(lava).withBold(true);
                    primaryColor = replaceRgb(primaryColor, lava);
                }

                if ((mask & EffectStyle.PARCHMENT) != 0) {
                    int parchment = DynamicTextRuntime.paletteColor(glyphIndex, 0.22D, PARCHMENT_PALETTE);
                    drawGlyph(font, codePoint, baseStyle.withColor(0x3B1C0A), drawX + 0.85F, drawY + 0.85F,
                            multiplyAlpha(replaceRgb(normalizedColor, 0x3B1C0A), 0.86F), false,
                            pose, buffers, mode, 0, packedLight);
                    if (Math.floorMod(glyphIndex, 5) == 2) {
                        drawBackdrop(
                                font, baseStyle, drawX - 0.65F, drawY - 0.45F,
                                drawX + advance + 0.75F, drawY + 8.75F,
                                0x38DFF4, 0.30F, pose, buffers, mode, packedLight
                        );
                    }
                    primaryStyle = primaryStyle.withColor(parchment).withBold(true);
                    primaryColor = replaceRgb(primaryColor, parchment);
                }

                if ((mask & EffectStyle.RED_SPEED_NEON) != 0) {
                    int neonRed = DynamicTextRuntime.mixColor(0xFF062B, 0xFFF3F6, pulse * 0.34F);
                    drawGlyph(font, codePoint, baseStyle.withColor(0x090000), drawX + 1.35F, drawY + 0.50F,
                            multiplyAlpha(replaceRgb(normalizedColor, 0x090000), 0.94F), false,
                            pose, buffers, mode, 0, packedLight);
                    drawGlow(font, codePoint, baseStyle, drawX, drawY, 0xFF001F,
                            multiplyAlpha(normalizedColor, 0.34F + pulse * 0.08F), 0.90F,
                            pose, buffers, mode, packedLight);
                    drawGlow(font, codePoint, baseStyle, drawX, drawY, 0xC40018,
                            multiplyAlpha(normalizedColor, 0.16F), 1.55F,
                            pose, buffers, mode, packedLight);
                    drawOutline(font, codePoint, baseStyle, drawX, drawY, 0x3A0007,
                            multiplyAlpha(normalizedColor, 0.96F), 0.58F,
                            pose, buffers, mode, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0xFF1433), drawX - 2.05F, drawY + 0.15F,
                            multiplyAlpha(replaceRgb(normalizedColor, 0xFF1433), 0.16F), false,
                            pose, buffers, mode, 0, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0x8A0011), drawX + 2.35F, drawY + 0.15F,
                            multiplyAlpha(replaceRgb(normalizedColor, 0x8A0011), 0.12F), false,
                            pose, buffers, mode, 0, packedLight);

                    if (DynamicTextRuntime.glitchActive(
                            DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 887)
                    )) {
                        float splitY = DynamicTextRuntime.unit(
                                DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 883)
                        ) < 0.5F ? -0.70F : 0.70F;
                        float splitX = 1.25F + DynamicTextRuntime.unit(
                                DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 881)
                        ) * 1.35F;
                        drawGlyph(font, codePoint, baseStyle.withColor(0xFF0030), drawX - splitX, drawY + splitY,
                                multiplyAlpha(replaceRgb(normalizedColor, 0xFF0030), 0.35F), false,
                                pose, buffers, mode, 0, packedLight);
                        drawGlyph(font, codePoint, baseStyle.withColor(0x120003), drawX + splitX, drawY - splitY,
                                multiplyAlpha(replaceRgb(normalizedColor, 0x120003), 0.68F), false,
                                pose, buffers, mode, 0, packedLight);
                    }

                    primaryStyle = primaryStyle.withColor(neonRed).withBold(true);
                    primaryColor = replaceRgb(primaryColor, neonRed);
                }

                if ((mask & EffectStyle.SYNTHWAVE_NEON) != 0) {
                    float synthProgress = gradientProgress(drawX, synthwaveBounds);
                    float animatedShift = (DynamicTextRuntime.pulse(glyphIndex, 0.32D) - 0.5F) * 0.12F;
                    int synthColor = DynamicTextRuntime.mixColor(
                            SYNTHWAVE_CYAN,
                            SYNTHWAVE_MAGENTA,
                            clamp01(synthProgress + animatedShift)
                    );
                    int bloomColor = DynamicTextRuntime.mixColor(0x00BFFF, 0xD300FF, pulse);

                    drawGlyph(font, codePoint, baseStyle.withColor(0x11001E), drawX + 1.45F, drawY + 1.35F,
                            multiplyAlpha(replaceRgb(normalizedColor, 0x11001E), 0.96F), false,
                            pose, buffers, mode, 0, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0x40105F), drawX + 0.85F, drawY + 0.80F,
                            multiplyAlpha(replaceRgb(normalizedColor, 0x40105F), 0.84F), false,
                            pose, buffers, mode, 0, packedLight);
                    drawGlow(font, codePoint, baseStyle, drawX, drawY, bloomColor,
                            multiplyAlpha(normalizedColor, 0.24F + pulse * 0.08F), 0.90F,
                            pose, buffers, mode, packedLight);
                    drawGlow(font, codePoint, baseStyle, drawX, drawY, bloomColor,
                            multiplyAlpha(normalizedColor, 0.11F), 1.55F,
                            pose, buffers, mode, packedLight);
                    drawOutline(font, codePoint, baseStyle, drawX, drawY, 0x25073D,
                            multiplyAlpha(normalizedColor, 0.92F), 0.55F,
                            pose, buffers, mode, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0x00EFFF), drawX - 0.80F, drawY,
                            multiplyAlpha(replaceRgb(normalizedColor, 0x00EFFF), 0.34F), false,
                            pose, buffers, mode, 0, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0xFF2BD6), drawX + 0.80F, drawY,
                            multiplyAlpha(replaceRgb(normalizedColor, 0xFF2BD6), 0.34F), false,
                            pose, buffers, mode, 0, packedLight);

                    if (DynamicTextRuntime.glitchActive(
                            DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 991)
                    )) {
                        float vhsY = DynamicTextRuntime.unit(
                                DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 997)
                        ) < 0.5F ? -0.65F : 0.65F;
                        float vhsX = 1.15F + DynamicTextRuntime.unit(
                                DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 1009)
                        ) * 1.20F;
                        drawGlyph(font, codePoint, baseStyle.withColor(SYNTHWAVE_CYAN), drawX - vhsX, drawY + vhsY,
                                multiplyAlpha(replaceRgb(normalizedColor, SYNTHWAVE_CYAN), 0.28F), false,
                                pose, buffers, mode, 0, packedLight);
                        drawGlyph(font, codePoint, baseStyle.withColor(SYNTHWAVE_MAGENTA), drawX + vhsX, drawY - vhsY,
                                multiplyAlpha(replaceRgb(normalizedColor, SYNTHWAVE_MAGENTA), 0.28F), false,
                                pose, buffers, mode, 0, packedLight);
                        drawBackdrop(
                                font, baseStyle, drawX - 0.85F, drawY + 3.35F + vhsY,
                                drawX + advance + 1.05F, drawY + 3.75F + vhsY,
                                synthColor, 0.42F, pose, buffers, mode, packedLight
                        );
                    }

                    primaryStyle = primaryStyle.withColor(synthColor).withBold(true);
                    primaryColor = replaceRgb(primaryColor, synthColor);
                }

                if ((mask & EffectStyle.GLITCH) != 0 && DynamicTextRuntime.glitchActive(primaryHash)) {
                    float flicker = DynamicTextRuntime.unit(
                            DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 239)
                    );
                    if (flicker < 0.13F) {
                        primaryColor = multiplyAlpha(primaryColor, 0.12F);
                    } else if (flicker < 0.43F) {
                        primaryColor = multiplyAlpha(primaryColor, 0.48F);
                    }

                    int fragmentColor = multiplyAlpha(normalizedColor, 0.36F);
                    float slice = DynamicTextRuntime.unit(
                            DynamicTextRuntime.hash(frame, glyphIndex, codePoint, 197)
                    ) < 0.5F ? -0.55F : 0.55F;
                    drawGlyph(font, codePoint, baseStyle.withColor(0xFF142E), drawX - 1.25F, drawY + slice,
                            replaceRgb(fragmentColor, 0xFF142E), false, pose, buffers, mode, 0, packedLight);
                    drawGlyph(font, codePoint, baseStyle.withColor(0x0DEBFF), drawX + 1.10F, drawY - slice,
                            replaceRgb(fragmentColor, 0x0DEBFF), false, pose, buffers, mode, 0, packedLight);
                }
            }

            drawGlyph(
                    font, codePoint, primaryStyle, drawX, drawY, primaryColor, shadow,
                    glyphPose, buffers, mode, backgroundColor, packedLight
            );
            // Advances by the unanimated width, deliberately: the cursor is what the next glyph and
            // every width measurement agree on, so letting an offset feed into it would make an
            // animated string measure differently each frame and shake its own container.
            cursor[0] += advance;
            return true;
        });

        return (int) cursor[0] + (shadow ? 1 : 0);
    }

    /** Degrees per radian, for the pendulum's radians. */
    private static final float ANIM_RAD_TO_DEG = (float) (180.0 / Math.PI);

    /** Font line height, for the swing's pivot. Vanilla's, and not configurable. */
    private static final float ANIM_LINE_HEIGHT = 9.0F;

    /**
     * Reused per glyph rather than allocated.
     *
     * <p>Thread-local because text is drawn from the render thread but ModernUI lays out on its own
     * workers, and a shared instance would be torn between them.</p>
     */
    private static final ThreadLocal<GlyphState> ANIM_STATE = ThreadLocal.withInitial(GlyphState::new);

    /**
     * A copy of {@code pose} turned about a point, in screen space.
     *
     * <p>A copy rather than a mutation: the caller's matrix is the shared GUI pose and every later
     * glyph would inherit the rotation.</p>
     */
    private static Matrix4f turned(Matrix4f pose, float pivotX, float pivotY, float degrees) {
        return new Matrix4f(pose)
                .translate(pivotX, pivotY, 0.0F)
                .rotateZ(degrees * Mth.DEG_TO_RAD)
                .translate(-pivotX, -pivotY, 0.0F);
    }

    /**
     * The most saturated version of a colour, for a glow that reads against the glyph.
     *
     * <p>A glow in the glyph's own colour is invisible on bright text, which is most text. Pushing the
     * dominant channel up and the others down keeps the hue recognisable while giving the halo
     * something to be.</p>
     */
    private static int saturate(int argb) {
        float r = ((argb >> 16) & 0xFF) / 255.0F;
        float g = ((argb >> 8) & 0xFF) / 255.0F;
        float b = (argb & 0xFF) / 255.0F;
        float max = Math.max(r, Math.max(g, b));
        if (max <= 0.0F) {
            return 0x4488FF;
        }
        float min = Math.min(r, Math.min(g, b));
        // Grey text has no hue to preserve, so give it a cyan cast rather than a grey halo.
        if (max - min < 0.08F) {
            return 0x66CCFF;
        }
        float scale = 1.0F / max;
        return (glowChannel(r * scale) << 16) | (glowChannel(g * scale) << 8) | glowChannel(b * scale);
    }

    private static int glowChannel(float value) {
        return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
    }

    /** The run key, built on first use and reused for the rest of this draw. */
    private static String runKeyOf(FormattedCharSequence text, String[] cache, boolean[] built) {
        if (!built[0]) {
            cache[0] = plainTextOf(text);
            built[0] = true;
        }
        return cache[0];
    }

    /** The visible characters of a sequence, for use as a stable key. */
    private static String plainTextOf(FormattedCharSequence text) {
        StringBuilder plain = new StringBuilder();
        text.accept((position, style, codePoint) -> {
            plain.appendCodePoint(codePoint);
            return true;
        });
        return plain.toString();
    }

    private static float[] effectBounds(Font font, FormattedCharSequence text, float startX, int effect) {
        float[] cursor = {startX};
        float[] bounds = {Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
        text.accept((stringIndex, encodedStyle, codePoint) -> {
            Style baseStyle = EffectStyle.clean(encodedStyle);
            float advance = font.getSplitter().stringWidth(
                    FormattedCharSequence.codepoint(codePoint, baseStyle)
            );
            int mask = DynamicTextRuntime.effectiveMask(EffectStyle.mask(encodedStyle));
            if ((mask & effect) != 0) {
                bounds[0] = Math.min(bounds[0], cursor[0]);
                bounds[1] = Math.max(bounds[1], cursor[0] + advance);
            }
            cursor[0] += advance;
            return true;
        });
        return bounds;
    }

    private static boolean hasBounds(float[] bounds) {
        return bounds != null
                && bounds.length >= 2
                && Float.isFinite(bounds[0])
                && Float.isFinite(bounds[1])
                && bounds[1] > bounds[0];
    }

    private static float gradientProgress(float x, float[] bounds) {
        if (!hasBounds(bounds)) {
            return 0.5F;
        }
        return clamp01((x - bounds[0]) / Math.max(1.0F, bounds[1] - bounds[0]));
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    private static void drawRedSpeedBackdrop(
            Font font,
            float x0,
            float x1,
            float y,
            int color,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int packedLight
    ) {
        float alphaScale = ((color >>> 24) & 255) / 255.0F;
        long frame = DynamicTextRuntime.animationFrame();
        float shimmer = 0.78F + DynamicTextRuntime.unit(
                DynamicTextRuntime.hash(frame, Math.round(x0), Math.round(x1), 1201)
        ) * 0.22F;

        drawBackdrop(font, Style.EMPTY, x0 - 2.40F, y - 1.55F, x1 + 2.40F, y + 9.65F,
                0xFF001F, 0.10F * alphaScale, pose, buffers, mode, packedLight);
        drawBackdrop(font, Style.EMPTY, x0 - 1.35F, y - 0.90F, x1 + 1.35F, y + 9.10F,
                0x050001, 0.76F * alphaScale, pose, buffers, mode, packedLight);
        drawBackdrop(font, Style.EMPTY, x0 - 1.15F, y - 0.65F, x1 + 1.15F, y - 0.05F,
                0xFF1231, 0.68F * shimmer * alphaScale, pose, buffers, mode, packedLight);
        drawBackdrop(font, Style.EMPTY, x0 - 1.15F, y + 8.45F, x1 + 1.15F, y + 9.05F,
                0xD4001B, 0.74F * shimmer * alphaScale, pose, buffers, mode, packedLight);

        for (int line = 0; line < 5; line++) {
            long hash = DynamicTextRuntime.hash(frame, line, Math.round(x1 - x0), 1217);
            float phase = DynamicTextRuntime.unit(hash);
            float length = 4.5F + line * 2.35F + phase * 3.0F;
            float thickness = line % 2 == 0 ? 0.62F : 0.38F;
            float lineY = y + 0.45F + line * 1.85F
                    + DynamicTextRuntime.signed(DynamicTextRuntime.hash(frame, line, 0, 1223)) * 0.22F;
            float gap = 1.15F + phase * 1.45F;
            int streakColor = line == 2 ? 0x250005 : (line % 2 == 0 ? 0xFF0A2B : 0xA80018);
            float streakAlpha = (line == 2 ? 0.92F : 0.54F + phase * 0.25F) * alphaScale;

            drawBackdrop(font, Style.EMPTY, x0 - gap - length, lineY, x0 - gap, lineY + thickness,
                    streakColor, streakAlpha, pose, buffers, mode, packedLight);
            drawBackdrop(font, Style.EMPTY, x1 + gap, lineY, x1 + gap + length, lineY + thickness,
                    streakColor, streakAlpha, pose, buffers, mode, packedLight);
        }

        float sliceY = y + 2.0F + DynamicTextRuntime.unit(
                DynamicTextRuntime.hash(frame, Math.round(x0), Math.round(x1), 1231)
        ) * 4.75F;
        float sliceShift = DynamicTextRuntime.signed(
                DynamicTextRuntime.hash(frame, Math.round(x1), Math.round(x0), 1237)
        ) * 1.6F;
        drawBackdrop(font, Style.EMPTY, x0 - 1.35F + sliceShift, sliceY,
                x1 + 1.35F + sliceShift, sliceY + 0.42F,
                0xFF0026, 0.46F * alphaScale, pose, buffers, mode, packedLight);
        drawBackdrop(font, Style.EMPTY, x0 + 0.40F - sliceShift, sliceY + 0.55F,
                x1 - 0.40F - sliceShift, sliceY + 0.82F,
                0x160003, 0.88F * alphaScale, pose, buffers, mode, packedLight);
    }

    private static void drawSynthwaveBackdrop(
            Font font,
            float x0,
            float x1,
            float y,
            int color,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int packedLight
    ) {
        float alphaScale = ((color >>> 24) & 255) / 255.0F;
        long frame = DynamicTextRuntime.animationFrame();
        float pulse = DynamicTextRuntime.pulse(Math.round(x0 + x1), 0.32D);

        drawBackdrop(font, Style.EMPTY, x0 - 2.60F, y - 1.75F, x1 + 2.60F, y + 9.85F,
                0x7B16FF, (0.10F + pulse * 0.04F) * alphaScale, pose, buffers, mode, packedLight);
        drawBackdrop(font, Style.EMPTY, x0 - 1.70F, y - 1.05F, x1 + 1.70F, y + 9.20F,
                0x090014, 0.70F * alphaScale, pose, buffers, mode, packedLight);
        drawBackdrop(font, Style.EMPTY, x0 - 1.35F, y - 0.72F, x1 + 1.35F, y - 0.18F,
                SYNTHWAVE_CYAN, 0.48F * alphaScale, pose, buffers, mode, packedLight);
        drawBackdrop(font, Style.EMPTY, x0 - 1.35F, y + 8.55F, x1 + 1.35F, y + 9.12F,
                SYNTHWAVE_MAGENTA, 0.54F * alphaScale, pose, buffers, mode, packedLight);

        for (int scanline = 0; scanline < 4; scanline++) {
            float lineY = y + 1.15F + scanline * 2.05F;
            int scanColor = scanline % 2 == 0 ? 0x552075 : 0x073955;
            drawBackdrop(font, Style.EMPTY, x0 - 1.25F, lineY, x1 + 1.25F, lineY + 0.26F,
                    scanColor, 0.48F * alphaScale, pose, buffers, mode, packedLight);
        }

        float glitch = DynamicTextRuntime.signed(
                DynamicTextRuntime.hash(frame, Math.round(x0), Math.round(x1), 1301)
        );
        float vhsY = y + 1.25F + DynamicTextRuntime.unit(
                DynamicTextRuntime.hash(frame, Math.round(x1), Math.round(x0), 1303)
        ) * 6.0F;
        int vhsColor = glitch < 0.0F ? SYNTHWAVE_CYAN : SYNTHWAVE_MAGENTA;
        drawBackdrop(font, Style.EMPTY, x0 - 1.60F + glitch * 2.25F, vhsY,
                x1 + 1.60F + glitch * 2.25F, vhsY + 0.48F,
                vhsColor, 0.48F * alphaScale, pose, buffers, mode, packedLight);

        for (int pixel = 0; pixel < 4; pixel++) {
            long hash = DynamicTextRuntime.hash(frame, pixel, Math.round(x1 - x0), 1319);
            float pixelY = y + DynamicTextRuntime.unit(hash) * 7.75F;
            float pixelWidth = 0.70F + DynamicTextRuntime.unit(
                    DynamicTextRuntime.hash(frame, pixel, 0, 1321)
            ) * 1.35F;
            int pixelColor = pixel % 2 == 0 ? SYNTHWAVE_CYAN : SYNTHWAVE_MAGENTA;
            float sideX = pixel % 2 == 0 ? x0 - 1.75F : x1 + 0.35F;
            drawBackdrop(font, Style.EMPTY, sideX, pixelY, sideX + pixelWidth, pixelY + 0.72F,
                    pixelColor, 0.50F * alphaScale, pose, buffers, mode, packedLight);
        }
    }

    private static void drawGlow(
            Font font,
            int codePoint,
            Style style,
            float x,
            float y,
            int rgb,
            int color,
            float distance,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int packedLight
    ) {
        int tinted = replaceRgb(color, rgb);
        drawGlyph(font, codePoint, style.withColor(rgb), x - distance, y, tinted, false,
                pose, buffers, mode, 0, packedLight);
        drawGlyph(font, codePoint, style.withColor(rgb), x + distance, y, tinted, false,
                pose, buffers, mode, 0, packedLight);
        drawGlyph(font, codePoint, style.withColor(rgb), x, y - distance, tinted, false,
                pose, buffers, mode, 0, packedLight);
        drawGlyph(font, codePoint, style.withColor(rgb), x, y + distance, tinted, false,
                pose, buffers, mode, 0, packedLight);
    }

    private static void drawOutline(
            Font font,
            int codePoint,
            Style style,
            float x,
            float y,
            int rgb,
            int color,
            float distance,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int packedLight
    ) {
        int tinted = replaceRgb(color, rgb);
        drawGlyph(font, codePoint, style.withColor(rgb).withBold(true), x - distance, y, tinted, false,
                pose, buffers, mode, 0, packedLight);
        drawGlyph(font, codePoint, style.withColor(rgb).withBold(true), x + distance, y, tinted, false,
                pose, buffers, mode, 0, packedLight);
        drawGlyph(font, codePoint, style.withColor(rgb).withBold(true), x, y - distance, tinted, false,
                pose, buffers, mode, 0, packedLight);
        drawGlyph(font, codePoint, style.withColor(rgb).withBold(true), x, y + distance, tinted, false,
                pose, buffers, mode, 0, packedLight);
    }

    private static void drawBackdrop(
            Font font,
            Style style,
            float x0,
            float y0,
            float x1,
            float y1,
            int rgb,
            float alpha,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int packedLight
    ) {
        FontSet fontSet = ((FontInvoker) font).dynamicTextEffects$getFontSet(EffectStyle.baseFont(style.getFont()));
        BakedGlyph whiteGlyph = fontSet.whiteGlyph();
        VertexConsumer buffer = buffers.getBuffer(whiteGlyph.renderType(mode));
        float red = ((rgb >> 16) & 255) / 255.0F;
        float green = ((rgb >> 8) & 255) / 255.0F;
        float blue = (rgb & 255) / 255.0F;
        whiteGlyph.renderEffect(
                new BakedGlyph.Effect(x0, y0, x1, y1, 0.01F, red, green, blue, alpha),
                pose,
                buffer,
                packedLight
        );
    }

    private static void drawGlyph(
            Font font,
            int codePoint,
            Style style,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int backgroundColor,
            int packedLight
    ) {
        font.drawInBatch(
                FormattedCharSequence.codepoint(codePoint, style),
                x,
                y,
                color,
                shadow,
                pose,
                buffers,
                mode,
                backgroundColor,
                packedLight
        );
    }

    private static int normalizeColor(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    private static int replaceRgb(int color, int rgb) {
        return (color & 0xFF000000) | (rgb & 0x00FFFFFF);
    }

    private static int multiplyAlpha(int color, float factor) {
        int alpha = Math.round(((color >>> 24) & 255) * factor);
        if (factor > 0.0F && alpha < 4) {
            alpha = 4;
        }
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}
