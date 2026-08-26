package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Unattended visual regression check for the spell light pass.
 *
 * <p>Several of this renderer's defects only appear at a particular
 * camera-to-light distance or view pitch — a lit pool clipped by a straight line
 * on flat ground, for instance, came from the sky-depth threshold and was
 * invisible up close. Those cannot be caught by a log assertion, and asking a
 * human to reproduce a specific viewpoint every time is slow and unreliable.
 *
 * <p>This drives the camera and the test light itself and writes screenshots, so
 * the same two viewpoints can be compared across builds. It runs only when
 * {@code lightpollution-autotest.marker} exists in the game directory, and it
 * deletes that marker immediately, so a normal launch never enters this path.
 * Pair it with {@code --quickPlaySingleplayer <level>} to reach a world without
 * touching the menus.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class SpellLightAutoTest {
    private static final String MARKER = "lightpollution-autotest.marker";
    /** Blocks between camera and light for the far shot. */
    /**
     * The distance the reported "light disappears when far away" defect showed
     * at. 60 blocks is past the point where the quadratic falloff leaves
     * anything to judge -- the pool is a few pixels either way -- so the far
     * case has to be near enough that an intact pool and a clipped one look
     * obviously different.
     */
    private static final double FAR_DISTANCE = 26.0D;
    private static final double NEAR_DISTANCE = 10.0D;

    private static Boolean armed;
    private static int tick;
    /** Where the camera stood when the light was placed, for the retreat step. */
    private static Vec3 anchor;

    private SpellLightAutoTest() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (armed == null) {
            armed = consumeMarker(minecraft);
        }
        if (!armed || minecraft.level == null || minecraft.player == null) {
            return;
        }

        tick++;
        switch (tick) {
            // Place the light once and leave it there. Moving the light instead
            // of the camera is not a controlled comparison: the ground under it
            // changes, so a dimmer pool could just be different terrain. Only a
            // fixed light with a moving camera isolates camera distance, which
            // is the variable the reported defect depends on.
            case 40 -> {
                forceMidnight(minecraft);
                aimAt(minecraft.player, NEAR_DISTANCE);
                anchor = minecraft.player.position();
                ExampleMod.LOGGER.info("[autotest] light fixed {} blocks out from {}",
                        NEAR_DISTANCE, anchor);
            }
            // Temporal accumulation needs a few dozen frames to converge, so
            // leave a wide gap before capturing.
            case 110 -> capture(minecraft, "islp-autotest-near");
            case 130 -> retreat(minecraft, FAR_DISTANCE - NEAR_DISTANCE);
            case 200 -> capture(minecraft, "islp-autotest-far");
            // Same viewpoint, same light, but each term of the lighting pass on
            // its own and unfiltered. This is what separates "the light never
            // reached this pixel" from "it reached it and something downstream
            // removed it" -- the two are indistinguishable in the final image.
            case 210 -> SpellLightPostProcessor.cycleDebugMode(1);
            case 245 -> capture(minecraft, "islp-autotest-far-shadow");
            case 255 -> SpellLightPostProcessor.cycleDebugMode(2);
            case 290 -> capture(minecraft, "islp-autotest-far-atten");
            case 300 -> SpellLightPostProcessor.cycleDebugMode(0);
            case 320 -> aimAtCutout(minecraft);
            case 390 -> capture(minecraft, "islp-autotest-cutout");
            // The reported "light stretches when I raise the crosshair" case.
            // Pitching up makes the ground grazing, which is where depth-derived
            // reconstruction is least accurate.
            case 400 -> {
                aimAt(minecraft.player, FAR_DISTANCE);
                minecraft.player.setXRot(-12.0F);
            }
            case 470 -> capture(minecraft, "islp-autotest-pitchup");
            case 480 -> ExampleMod.LOGGER.info("[autotest] DONE");
            default -> {
            }
        }
    }

    /**
     * Walks the camera straight back along its own view axis, leaving the light
     * where it is, and keeps it aimed at the light.
     */
    private static void retreat(Minecraft minecraft, double distance) {
        LocalPlayer player = minecraft.player;
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (player == null || server == null || anchor == null) {
            return;
        }
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 destination = anchor.subtract(forward.scale(distance));
        String name = player.getGameProfile().getName();
        server.execute(() -> server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withSuppressedOutput(),
                String.format("tp %s %.2f %.2f %.2f", name,
                        destination.x, destination.y, destination.z)));
        ExampleMod.LOGGER.info("[autotest] camera retreated to {} blocks from the light",
                NEAR_DISTANCE + distance);
    }

    /** True when the marker was present; removes it so this runs at most once. */
    private static boolean consumeMarker(Minecraft minecraft) {
        Path marker = minecraft.gameDirectory.toPath().resolve(MARKER);
        if (!Files.exists(marker)) {
            return false;
        }
        try {
            Files.delete(marker);
        } catch (IOException ignored) {
            // A marker that cannot be deleted would re-arm next launch; the log
            // line below makes that obvious rather than silent.
        }
        ExampleMod.LOGGER.info("[autotest] marker found; driving the light pass");
        return true;
    }

    /**
     * Frames the nearest cutout block with the light placed behind it, which is
     * the arrangement the goal is actually about: the shadow then falls toward
     * the camera and a working mask shows the model's holes as bright patches on
     * the ground in front of the block.
     */
    private static void aimAtCutout(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null) {
            return;
        }
        // Trapdoors first: their four texture cutouts are the sharpest test of
        // the baked mask, where a fence only has gaps between posts.
        for (String wanted : new String[] {"trapdoor", "bars", "fence"}) {
            net.minecraft.core.BlockPos found = findVisible(minecraft, player, wanted);
            if (found == null) {
                continue;
            }
            Vec3 block = new Vec3(found.getX() + 0.5D, found.getY() + 0.5D,
                    found.getZ() + 0.5D);
            Vec3 toward = player.position().subtract(block).normalize();
            // Light behind the block, camera on the ground patch in front of it:
            // that patch is where the cutout pattern lands.
            SpellLightEmitter.setTestLight(block.subtract(toward.scale(3.0D)));
            look(player, player.getEyePosition(),
                    block.add(toward.scale(2.0D)).add(0.0D, -0.4D, 0.0D));
            ExampleMod.LOGGER.info("[autotest] cutout {} at {}; light 3 blocks behind",
                    wanted, found.toShortString());
            return;
        }
        ExampleMod.LOGGER.warn("[autotest] no visible trapdoor/bars/fence within 24 blocks");
    }

    /**
     * Nearest block whose id contains {@code wanted} and which the camera can
     * actually see. Without the visibility test the search happily picks a fence
     * inside a building and photographs its wall.
     */
    private static net.minecraft.core.BlockPos findVisible(Minecraft minecraft,
                                                           LocalPlayer player, String wanted) {
        net.minecraft.core.BlockPos.MutableBlockPos cursor =
                new net.minecraft.core.BlockPos.MutableBlockPos();
        int baseX = net.minecraft.util.Mth.floor(player.getX());
        int baseY = net.minecraft.util.Mth.floor(player.getY());
        int baseZ = net.minecraft.util.Mth.floor(player.getZ());
        for (int radius = 1; radius <= 24; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dy = -4; dy <= 4; dy++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        cursor.set(baseX + dx, baseY + dy, baseZ + dz);
                        if (!minecraft.level.getBlockState(cursor).getBlock()
                                .getDescriptionId().contains(wanted)) {
                            continue;
                        }
                        if (hasLineOfSight(minecraft, player, cursor)) {
                            return cursor.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasLineOfSight(Minecraft minecraft, LocalPlayer player,
                                          net.minecraft.core.BlockPos target) {
        Vec3 eye = player.getEyePosition();
        Vec3 centre = new Vec3(target.getX() + 0.5D, target.getY() + 0.5D,
                target.getZ() + 0.5D);
        net.minecraft.world.phys.BlockHitResult hit = minecraft.level.clip(
                new net.minecraft.world.level.ClipContext(eye, centre,
                        net.minecraft.world.level.ClipContext.Block.COLLIDER,
                        net.minecraft.world.level.ClipContext.Fluid.NONE, player));
        return hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS
                || hit.getBlockPos().equals(target);
    }

    /**
     * Places the test light that many blocks away along the player's current
     * facing and points the camera at it. Using the player's own Y keeps the
     * light just above whatever surface they are standing on.
     */
    private static void aimAt(LocalPlayer player, double distance) {
        double yaw = Math.toRadians(player.getYRot());
        Vec3 forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 target = new Vec3(player.getX(), player.getY() + 1.0D, player.getZ())
                .add(forward.scale(distance));
        SpellLightEmitter.setTestLight(target);
        look(player, player.getEyePosition(), target);
    }

    private static void look(LocalPlayer player, Vec3 eye, Vec3 target) {
        double dx = target.x - eye.x;
        double dy = target.y - eye.y;
        double dz = target.z - eye.z;
        float lookYaw = (float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0D);
        float lookPitch = (float) -Math.toDegrees(
                Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        player.setYRot(lookYaw);
        player.setXRot(lookPitch);
        // Match the previous-frame rotation too, or the renderer interpolates
        // from the old angles and the first frames are aimed somewhere else.
        player.yRotO = lookYaw;
        player.xRotO = lookPitch;
    }

    /** Night removes the ambient term, so the lit pool's extent is unambiguous. */
    private static void forceMidnight(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server == null) {
            return;
        }
        // Rain both darkens the scene and draws a translucent layer over it,
        // which makes a screenshot useless for judging whether the light pool is
        // intact. Clear it in the same breath as forcing night.
        server.execute(() -> {
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "time set midnight");
            server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack().withSuppressedOutput(),
                    "weather clear 1000000");
        });
    }

    private static void capture(Minecraft minecraft, String name) {
        Screenshot.grab(minecraft.gameDirectory, name + ".png",
                minecraft.getMainRenderTarget(),
                message -> ExampleMod.LOGGER.info("[autotest] {}", message.getString()));
        ExampleMod.LOGGER.info("[autotest] captured {}", name);
    }
}
