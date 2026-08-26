package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;

/** Client-only commands for validating the real-light pass in a live world. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class SpellLightClientCommands {
    private SpellLightClientCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("lightpollution_test_light")
                .executes(context -> createTestLight(context.getSource(), SpellLightEmitter.TestColor.PURPLE))
                .then(Commands.argument("color", StringArgumentType.word())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                SpellLightEmitter.testColorNames(), builder))
                        .executes(context -> {
                            String colorName = StringArgumentType.getString(context, "color");
                            SpellLightEmitter.TestColor color = SpellLightEmitter.testColor(colorName);
                            if (color == null) {
                                context.getSource().sendFailure(Component.literal(
                                        "Unknown test-light color. Use: red, orange, yellow, green, cyan, blue, purple."));
                                return 0;
                            }
                            return createTestLight(context.getSource(), color);
                        })));

        dispatcher.register(Commands.literal("lightpollution_clear_test_light")
                .executes(context -> {
                    SpellLightEmitter.clearTestLight();
                    context.getSource().sendSuccess(
                            () -> Component.literal("Light Pollution test light removed."), false);
                    return 1;
                }));

        // Prints the GPU occupancy bitfield for the block under the crosshair so
        // a missing cutout hole can be attributed to the voxelizer or to the
        // denoising that runs after it.
        dispatcher.register(Commands.literal("lightpollution_dump_voxel")
                .executes(context -> dumpVoxel(context.getSource())));

        dispatcher.register(Commands.literal("lightpollution_toggle_filter")
                .executes(context -> {
                    boolean enabled = SpellLightPostProcessor.toggleFilter();
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Light Pollution denoise (temporal + spatial): "
                                    + (enabled ? "ON" : "OFF -- raw mask")), false);
                    return 1;
                }));

        // Shows one term of the lighting pass unfiltered. This is what tells a
        // missing shadow apart from a shadow the denoiser smeared, and a light
        // that never reached a surface from one attenuated away.
        // Dumps the framebuffer state at every render stage for one frame. Run it
        // with and without a shader pack: the diff is what says whether this mod's
        // screen-space passes can work under Oculus/Iris at all.
        dispatcher.register(Commands.literal("lightpollution_probe_pipeline")
                .executes(context -> {
                    ShaderPipelineProbe.arm();
                    context.getSource().sendSuccess(() -> Component.literal(
                            "Probing the render pipeline for one frame -- see the log. "
                                    + "Shader pack: " + ShaderPackState.describe()), false);
                    return 1;
                }));

        dispatcher.register(Commands.literal("lightpollution_debug")
                .then(Commands.argument("mode", IntegerArgumentType.integer(0, 4))
                        .executes(context -> {
                            int mode = SpellLightPostProcessor.cycleDebugMode(
                                    IntegerArgumentType.getInteger(context, "mode"));
                            String name = switch (mode) {
                                case 1 -> "shadow visibility (white = lit, black = occluded)";
                                case 2 -> "distance attenuation";
                                case 3 -> "reconstructed normal";
                                case 4 -> "voxel occupancy under the surface (red = solid)";
                                default -> "off";
                            };
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Spell light debug view: " + name), false);
                            return 1;
                        })));
    }

    private static int dumpVoxel(CommandSourceStack source) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.hitResult instanceof BlockHitResult blockHit)) {
            source.sendFailure(Component.literal("Look at a block first."));
            return 0;
        }
        BlockPos target = blockHit.getBlockPos();
        for (String line : SpellLightPostProcessor.dumpVoxelBlock(target)) {
            ExampleMod.LOGGER.info("[voxel-dump] {}", line);
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return 1;
    }

    private static int createTestLight(CommandSourceStack source, SpellLightEmitter.TestColor color) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            source.sendFailure(Component.literal("No client player is available."));
            return 0;
        }
        SpellLightEmitter.setTestLight(player.position().add(0.0D, 1.0D, 0.0D), color);
        source.sendSuccess(() -> Component.literal(
                "Light Pollution test light created (" + color.name().toLowerCase(java.util.Locale.ROOT) + ")."), false);
        return 1;
    }
}
