package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.CosmicHorseshoeEntity;
import com.gang.lightpollution.registry.ModEntities;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * Commands for standing a Cosmic Horseshoe up and taking it down again.
 *
 * <p>The arc's shape depends on constants that have to be judged by eye against the
 * real thing — the arc should span about 300 degrees and leave a gap of about 60 — and
 * casting the spell to check means a five-and-a-half minute cooldown between looks. A
 * display lens opens and then simply stays, doing no damage, until it is cleared.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CosmicHorseshoeDisplayCommands {
    /** How far ahead of the player it is placed, in blocks. */
    private static final double PLACE_DISTANCE = 40.0D;
    /** How far around the player {@code clear} looks, in blocks. */
    private static final double CLEAR_RADIUS = 512.0D;

    private CosmicHorseshoeDisplayCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("horseshoe_show")
                .requires(source -> source.hasPermission(2))
                .executes(context -> show(context.getSource())));

        dispatcher.register(Commands.literal("horseshoe_clear")
                .requires(source -> source.hasPermission(2))
                .executes(context -> clear(context.getSource())));
    }

    private static int show(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 eye = source.getPosition();
        float yawRadians = (float) Math.toRadians(source.getRotation().y);
        Vec3 ahead = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3 centre = eye.add(ahead.scale(PLACE_DISTANCE));

        CosmicHorseshoeEntity effect = new CosmicHorseshoeEntity(
                ModEntities.COSMIC_HORSESHOE.get(), level);
        // Gap facing back toward whoever ran the command, so the opening is visible
        // without having to walk round it first.
        effect.configureDisplay(centre, ahead.reverse(), level.random.nextInt());
        level.addFreshEntity(effect);
        source.sendSuccess(() -> Component.literal(
                "Cosmic Horseshoe standing " + (int) PLACE_DISTANCE
                        + " blocks ahead. /horseshoe_clear to remove."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        List<CosmicHorseshoeEntity> found = level.getEntitiesOfClass(
                CosmicHorseshoeEntity.class,
                new AABB(at.x - CLEAR_RADIUS, at.y - CLEAR_RADIUS, at.z - CLEAR_RADIUS,
                        at.x + CLEAR_RADIUS, at.y + CLEAR_RADIUS, at.z + CLEAR_RADIUS),
                // Only the display ones. A cast spell in flight is not litter.
                CosmicHorseshoeEntity::isDisplay);
        for (Entity entity : found) {
            entity.discard();
        }
        int removed = found.size();
        source.sendSuccess(() -> Component.literal("Removed " + removed
                + (removed == 1 ? " horseshoe." : " horseshoes.")), false);
        return removed;
    }
}
