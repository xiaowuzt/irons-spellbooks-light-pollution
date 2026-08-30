package com.gang.lightpollution.client;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.SpellLightConfig.TooltipStyle;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.Font;
import com.gang.lightpollution.client.tooltip.SpellIcons;
import com.gang.lightpollution.client.tooltip.TextPinwheel;
import com.gang.lightpollution.client.tooltip.TooltipElements;
import com.gang.lightpollution.client.tooltip.TooltipShapes;
import com.gang.lightpollution.client.tooltip.TooltipText;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector2ic;

import java.util.List;

/**
 * Draws this mod's tooltips in one of several custom styles, after ArcaneVortex's own frames.
 *
 * <p>All off by default, chosen by {@link TooltipStyle} in the config or by
 * {@code /lightpollution frame}. PANEL puts a rounded panel behind the vanilla layout and cancels
 * nothing, so vanilla still places the text. Every other style cancels the event and lays the box
 * out here, differing only in what is drawn behind and over the panel — which is why nine separate
 * renderers in the source mod collapse to one method and a switch.</p>
 *
 * <p><b>The position is not the event's x and y.</b> Those are the <em>mouse</em> position — Forge
 * says so outright: "the Y position of the tooltip box. By default, this is the mouse Y position."
 * The real box position is produced after this event by the tooltip positioner, which offsets from
 * the cursor and pulls the box back inside the screen. Drawing at the raw x and y puts the frame at
 * the cursor while vanilla's text goes somewhere else, which is exactly how it failed: the title sat
 * above the panel and the long stat line ran out of the right edge. So the positioner is run here
 * too, with the same size vanilla will use, and everything is placed from its answer.</p>
 *
 * <p>Layout numbers are ArcaneVortex's: forty pixels of horizontal slack, sixteen vertical, ten to
 * a line and six to the separator, a thirty-two pixel corner over sixteen segments, and thirteen
 * rays. The rays scale off the <em>shorter</em> side of the box, which matters much more here than
 * it does there — these tooltips carry one very long stat line, so a box can be fifteen times wider
 * than it is tall, and scaling off the longer side threw rays clean across the screen.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SpellTooltipFrame {
    /** Horizontal slack each side, half of ArcaneVortex's forty. */
    private static final int PAD_X = 20;
    /** Vertical slack top and bottom, half of its sixteen. */
    private static final int PAD_Y = 8;
    /** Height of the rule under the title. */
    private static final int SEPARATOR_H = 6;
    /** Corner radius, clamped to half the shorter side. */
    private static final float RADIUS = 32.0F;
    /**
     * Corner radius for PANEL, which is much smaller than ARCANE's and has to be.
     *
     * <p>A rounded corner cuts a bite out of each end of the box, and the text's own corner has to
     * stay inside it. For text inset by {@code p} the corner point sits {@code sqrt(2)(r - p)} from
     * the arc centre, so it survives only while {@code r <= p * sqrt(2) / (sqrt(2) - 1)}, about
     * {@code 3.4p}. PANEL hugs vanilla's layout with {@link #PANEL_PAD} of slack and cannot move the
     * text, so 32 overshot by more than double and clipped the title and the last line — which is
     * exactly what it looked like. ARCANE gets away with 32 because its padding is 20 by 8.</p>
     */
    private static final float PANEL_RADIUS = 10.0F;
    /** Segments per corner arc. */
    private static final int ARC_SEGMENTS = 16;
    /**
     * How fast the border highlight travels, in cycles per ten seconds.
     *
     * <p>Slow. A highlight that laps the box quickly reads as a loading spinner.</p>
     */
    private static final float BORDER_SPEED = 3.0F;
    /** How far the panel sits outside vanilla's box in PANEL, in pixels. */
    private static final int PANEL_PAD = 4;

    private static final int RAYS = 13;
    /** Ray reach, as a fraction of the glow scale. */
    private static final float RAY_LENGTH = 0.8F;
    /** Ray half-width at the tip, as a fraction of the glow scale. */
    private static final float RAY_TIP_WIDTH = 0.405F;
    /** The glow's own scale is this times the shorter side of the box. */
    private static final float GLOW_SCALE = 6.0F;
    /** Turns per second of the whole fan. */
    private static final float RAY_SPIN = 0.10F;
    /** How far off the screen edge a clamped box is kept, in pixels. */
    private static final int EDGE_MARGIN = 10;

    private SpellTooltipFrame() {
    }

    /**
     * HIGHEST, not HIGH, and that is load-bearing for ModernUI compatibility.
     *
     * <p>ModernUI listens for this event at HIGH, draws a complete tooltip — frame, border, title
     * rule and all the text — and only cancels at LOW. Forge checks cancellation per listener at
     * invoke time rather than breaking out of the loop, so a cancel suppresses listeners that run
     * later and nothing that has already run. At equal priority the order is registration order, and
     * ModernUI registers during mod construction, so it drew first and this drew second: two frames,
     * each with its own text, and an apparent offset because our box is deliberately wider than
     * vanilla's. Cancelling from HIGHEST suppresses their draw instead, since neither of their
     * handlers sets receiveCanceled.</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onTooltipPre(RenderTooltipEvent.Pre event) {
        TooltipStyle style = SpellLightConfig.tooltipStyle;
        if (style == TooltipStyle.VANILLA) {
            return;
        }
        Integer boxed = SpellTooltipStyle.accentFor(event.getItemStack());
        if (boxed == null) {
            return;
        }
        int accent = boxed;
        Font font = event.getFont();
        List<ClientTooltipComponent> components = event.getComponents();
        if (components.isEmpty()) {
            return;
        }

        // Vanilla's own measurement, down to the -2 on a single line, because the positioner's
        // answer depends on the size it is given and PANEL has to land on vanilla's box exactly.
        int vanillaWidth = 0;
        int vanillaHeight = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            vanillaWidth = Math.max(vanillaWidth, component.getWidth(font));
            vanillaHeight += component.getHeight();
        }
        if (vanillaWidth <= 0 || vanillaHeight <= 0) {
            return;
        }
        Vector2ic at = event.getTooltipPositioner().positionTooltip(
                event.getScreenWidth(), event.getScreenHeight(),
                event.getX(), event.getY(), vanillaWidth, vanillaHeight);

        float seconds = (System.currentTimeMillis() % 600_000L) / 1000.0F;
        GuiGraphics graphics = event.getGraphics();

        if (style == TooltipStyle.PANEL) {
            // PANEL is the one style that does not cancel: it draws behind whatever lays the
            // tooltip out. When ModernUI has replaced that layout there is nothing to sit behind —
            // its box is a different size and in a different place — so this stands down and lets
            // ModernUI's own frame be the frame. The alternative would be a panel that cannot
            // align with the text in front of it.
            if (net.minecraftforge.fml.ModList.get().isLoaded("modernui")) {
                return;
            }
            graphics.pose().pushPose();
            // Under vanilla's 400, so vanilla's text lands on top of this.
            graphics.pose().translate(0.0F, 0.0F, 380.0F);
            try {
                panel(graphics, at.x() - PANEL_PAD, at.y() - PANEL_PAD,
                        at.x() + vanillaWidth + PANEL_PAD, at.y() + vanillaHeight + PANEL_PAD,
                        accent);
            } finally {
                graphics.pose().popPose();
            }
            return;
        }

        takeover(graphics, event, components, font, at, accent, seconds, style);
        // Everything is drawn, so stop vanilla drawing its own box over the top.
        event.setCanceled(true);
    }

    /** PANEL: a rounded panel behind vanilla's layout, and nothing else. */
    private static void panel(GuiGraphics graphics, int left, int top, int right, int bottom,
                              int accent) {
        RoundedRect.Corners corners = RoundedRect.Corners.uniform(PANEL_RADIUS);
        RoundedRect.fill(graphics, left, top, right, bottom, corners,
                RoundedRect.Ramp.of(RoundedRect.Gradient.VERTICAL, 0.0F,
                        0xE8000000 | shade(accent, 0.10F),
                        0xE8000000 | shade(accent, 0.22F)),
                ARC_SEGMENTS);
        RoundedRect.border(graphics, left, top, right, bottom, corners, 1.0F,
                RoundedRect.Ramp.of(RoundedRect.Gradient.BORDER_CIRCULAR, BORDER_SPEED,
                        0xFF000000 | shade(accent, 0.40F),
                        0xFF000000 | shade(accent, 1.00F),
                        0xFF000000 | shade(accent, 0.40F)),
                ARC_SEGMENTS);
    }

    /**
     * The styles that take rendering over. Every one of them lays the box out here.
     *
     * <p>They share the measuring, the clamping and the text pass, and differ only in what is drawn
     * behind and over the panel. That split is the whole reason the nine renderers this came from
     * collapse to one method plus a switch.</p>
     */
    private static void takeover(GuiGraphics graphics, RenderTooltipEvent.Pre event,
                                 List<ClientTooltipComponent> components, Font font,
                                 Vector2ic at, int accent, float seconds, TooltipStyle style) {
        int lineWidth = 0;
        int content = 0;
        for (ClientTooltipComponent component : components) {
            lineWidth = Math.max(lineWidth, component.getWidth(font));
            content += component.getHeight();
        }
        boolean ruled = components.size() > 1;
        int width = lineWidth + PAD_X * 2;
        int height = content + PAD_Y * 2 + (ruled ? SEPARATOR_H : 0);

        // The frame is wider and taller than vanilla's box, so the positioner's answer can leave it
        // hanging off an edge. Same clamp ArcaneVortex uses.
        int left = at.x() - PAD_X;
        int top = at.y() - PAD_Y;
        if (left + width > event.getScreenWidth()) {
            left = event.getScreenWidth() - width - EDGE_MARGIN;
        }
        if (top + height > event.getScreenHeight()) {
            top = event.getScreenHeight() - height - EDGE_MARGIN;
        }
        left = Math.max(EDGE_MARGIN, left);
        top = Math.max(EDGE_MARGIN, top);
        int right = left + width;
        int bottom = top + height;

        if (style == TooltipStyle.PINWHEEL) {
            pinwheel(graphics, event, components, font, left, top, right, bottom, seconds);
            return;
        }

        // Behind the panel, before the depth translate: the rays blend additively and the backdrop
        // blits, and neither belongs in the batch the panel goes into.
        if (style == TooltipStyle.ARCANE) {
            rays(graphics, left, top, right, bottom, accent, seconds);
        } else if (style == TooltipStyle.ORBIT) {
            ResourceLocation icon = SpellIcons.forStack(event.getItemStack());
            if (icon != null) {
                TooltipElements.backdrop(graphics, icon, (left + right) / 2, (top + bottom) / 2,
                        Math.max(64, Math.min(160, height * 3)), 0.55F, seconds);
            }
        }

        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 380.0F);
        try {
            // The ring and the sigil go under the panel, inside this pose so they share its depth.
            // The ring specifically has to be split across the panel draw, which is the whole point
            // of it: far half, panel, near half.
            float centreX = (left + right) * 0.5F;
            float centreY = (top + bottom) * 0.5F;
            // Sized off the panel's HEIGHT, not its width, and clamped. These tooltips carry one very
            // long stat line, so a box is routinely fifteen times wider than it is tall — deriving
            // the radius from the width gave a ring some thirteen hundred pixels across, most of it
            // off screen. It is the same trap the rays hit, and the fix is the same.
            //
            // Height is the right driver anyway. The ring is squashed to a fifth of its width, so
            // what decides whether it threads through the box is how far it reaches above and below
            // it, and that is a vertical question. The clamp keeps a one-line tooltip from getting a
            // ring too small to emerge and a tall one from getting a ring off the top of the screen.
            float ringOuter = Mth.clamp(height * 3.0F, 110.0F, 240.0F);
            float ringInner = ringOuter - Mth.clamp(ringOuter * 0.17F, 8.0F, 40.0F);
            if (style == TooltipStyle.RING) {
                TooltipShapes.ringHalf(graphics, centreX, centreY, ringOuter, ringInner,
                        TooltipShapes.Half.BEHIND, accent, 0.85F, seconds);
            } else if (style == TooltipStyle.SIGIL) {
                TooltipShapes.sigil(graphics, left, top, right, bottom, accent, 0.9F, seconds);
            }

            TooltipElements.panel(graphics, left, top, right, bottom, RADIUS, accent, BORDER_SPEED);

            if (style == TooltipStyle.RING) {
                TooltipShapes.ringHalf(graphics, centreX, centreY, ringOuter, ringInner,
                        TooltipShapes.Half.IN_FRONT, accent, 0.85F, seconds);
            }

            int row = top + PAD_Y;
            if (ruled) {
                int titleHeight = components.get(0).getHeight();
                if (style == TooltipStyle.ASTRAL) {
                    // A band of light where the rule would go, rather than a rule.
                    TooltipElements.flowingBand(graphics, left + PAD_X, row + titleHeight,
                            right - PAD_X, row + titleHeight + 2, accent, 1.0F, 4.0F, 0.9F,
                            seconds);
                } else {
                    TooltipElements.separator(graphics, left + PAD_X, row + titleHeight,
                            right - PAD_X, accent);
                }
            }

            if (style == TooltipStyle.ASTRAL) {
                TooltipElements.particles(graphics, left, top, right, bottom, accent, seconds);
                TooltipElements.cornerMarks(graphics, left, top, right, bottom, accent, seconds);
            }

            text(graphics, components, font, left, width, top + PAD_Y, ruled);
        } finally {
            graphics.pose().popPose();
        }
    }

    /**
     * The text pass, shared by every takeover style.
     *
     * <p>The title is centred and the body is not, which stops a one-word name sitting alone at the
     * far left of a very wide box.</p>
     */
    private static void text(GuiGraphics graphics, List<ClientTooltipComponent> components,
                             Font font, int left, int width, int firstRow, boolean ruled) {
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 4.0F);
        Matrix4f pose = graphics.pose().last().pose();
        int row = firstRow;
        for (int index = 0; index < components.size(); ++index) {
            ClientTooltipComponent component = components.get(index);
            int x = index == 0
                    ? left + (width - component.getWidth(font)) / 2
                    : left + PAD_X;
            component.renderText(font, x, row, pose, graphics.bufferSource());
            row += component.getHeight() + (index == 0 && ruled ? SEPARATOR_H : 0);
        }
        // Text is batched rather than drawn, so it has to be flushed while this pose is current.
        graphics.flush();
        graphics.pose().popPose();

        row = firstRow;
        for (int index = 0; index < components.size(); ++index) {
            ClientTooltipComponent component = components.get(index);
            component.renderImage(font, left + PAD_X, row, graphics);
            row += component.getHeight() + (index == 0 && ruled ? SEPARATOR_H : 0);
        }
    }

    /**
     * PINWHEEL: no panel, no border, no background — the lines orbit as spokes.
     *
     * <p>Centred on the screen rather than on the cursor, unlike every other style. The spokes reach
     * sixty pixels out and swing right round, so anchoring that to the pointer would throw half of it
     * off the edge whenever the cursor was near one.</p>
     */
    private static void pinwheel(GuiGraphics graphics, RenderTooltipEvent.Pre event,
                                 List<ClientTooltipComponent> components, Font font,
                                 int left, int top, int right, int bottom, float seconds) {
        List<Component> lines = TooltipText.forStack(event.getItemStack());
        if (lines.isEmpty()) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0.0F, 0.0F, 380.0F);
        try {
            TextPinwheel.render(graphics, font, lines,
                    event.getScreenWidth() * 0.5F, event.getScreenHeight() * 0.5F, seconds);
        } finally {
            graphics.pose().popPose();
        }
    }

    /**
     * The radial glow behind the box: thirteen wedges, bright at the hub and transparent at the tip.
     *
     * <p>Drawn immediately with additive blending rather than into the GUI batch, because additive
     * is what makes it a glow and the batched GUI type blends normally. The flush first is what
     * keeps the order right — anything already queued for this frame is put on screen before these
     * go down, so the glow cannot end up over the inventory it should be behind.</p>
     */
    private static void rays(GuiGraphics graphics, int left, int top, int right, int bottom,
                            int accent, float seconds) {
        float centreX = (left + right) * 0.5F;
        float centreY = (top + bottom) * 0.5F;
        // The shorter side. On a box fifteen times wider than it is tall, the longer side throws
        // the rays across the whole screen.
        float scale = Math.min(right - left, bottom - top) * GLOW_SCALE;
        float reach = RAY_LENGTH * scale;
        float tipHalf = RAY_TIP_WIDTH * scale * 0.5F;
        if (reach < 1.0F) {
            return;
        }

        graphics.flush();
        Matrix4f pose = graphics.pose().last().pose();
        RenderSystem.enableBlend();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE,
                GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        RenderSystem.disableDepthTest();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);

        for (int ray = 0; ray < RAYS; ++ray) {
            double angle = seconds * RAY_SPIN * Math.PI * 2.0
                    + ray * Math.PI * 2.0 / RAYS;
            float dx = (float) Math.cos(angle);
            float dy = (float) Math.sin(angle);
            // Two colours per ray, offset in time so the fan is not one flat hue. ArcaneVortex
            // walks a palette; there is one accent per spell here, so the walk is in brightness.
            float phase = (float) (Math.sin(seconds * 1.1 + ray * 0.97) * 0.5 + 0.5);
            int hub = 0x66000000 | shade(accent, 0.75F + phase * 0.55F);
            int tip = shade(accent, 0.45F + phase * 0.35F);

            vertex(builder, pose, centreX, centreY, hub);
            vertex(builder, pose, centreX, centreY, hub);
            vertex(builder, pose, centreX + dx * reach - dy * tipHalf,
                    centreY + dy * reach + dx * tipHalf, tip);
            vertex(builder, pose, centreX + dx * reach + dy * tipHalf,
                    centreY + dy * reach - dx * tipHalf, tip);
        }
        BufferUploader.drawWithShader(builder.end());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, int argb) {
        consumer.vertex(pose, x, y, 0.0F)
                .color((argb >> 16) & 0xFF, (argb >> 8) & 0xFF, argb & 0xFF, (argb >>> 24) & 0xFF)
                .endVertex();
    }

    /** Scale a packed RGB, clamped. */
    private static int shade(int rgb, float factor) {
        int r = Math.max(0, Math.min(255, (int) (((rgb >> 16) & 0xFF) * factor)));
        int g = Math.max(0, Math.min(255, (int) (((rgb >> 8) & 0xFF) * factor)));
        int b = Math.max(0, Math.min(255, (int) ((rgb & 0xFF) * factor)));
        return (r << 16) | (g << 8) | b;
    }
}
