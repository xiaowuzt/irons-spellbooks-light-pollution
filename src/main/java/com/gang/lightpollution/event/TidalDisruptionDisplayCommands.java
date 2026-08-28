package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.TidalDisruptionEntity;
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
 * Commands for standing A tidal disruption up and taking it down again.
 *
 * <p>How the debris stream reads depends on how tightly it wraps and how fast it is drawn out. Casting the spell to check means minutes of cooldown between looks, so a
 * display one stands with no caster and no damage until it is cleared.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TidalDisruptionDisplayCommands {
    /** How far ahead of the player it is placed, in blocks. */
    private static final double PLACE_DISTANCE = 42.0D;
    /** How far around the player {@code clear} looks, in blocks. */
    private static final double CLEAR_RADIUS = 512.0D;

    private TidalDisruptionDisplayCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("tde_show")
                .requires(source -> source.hasPermission(2))
                .executes(context -> show(context.getSource())));

        dispatcher.register(Commands.literal("tde_clear")
                .requires(source -> source.hasPermission(2))
                .executes(context -> clear(context.getSource())));
    }

    private static int show(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 eye = source.getPosition();
        float yawRadians = (float) Math.toRadians(source.getRotation().y);
        Vec3 ahead = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3 centre = eye.add(ahead.scale(PLACE_DISTANCE));

        TidalDisruptionEntity effect = new TidalDisruptionEntity(ModEntities.TIDAL_DISRUPTION.get(), level);
        // Leaning across the viewer, which is the angle the structure is legible from.
        effect.configureDisplay(centre, source.getRotation().y + 90.0F,
                level.random.nextInt());
        level.addFreshEntity(effect);
        source.sendSuccess(() -> Component.literal(
                "A tidal disruption standing " + (int) PLACE_DISTANCE
                        + " blocks ahead. /tde_clear to remove."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        List<TidalDisruptionEntity> found = level.getEntitiesOfClass(
                TidalDisruptionEntity.class,
                new AABB(at.x - CLEAR_RADIUS, at.y - CLEAR_RADIUS, at.z - CLEAR_RADIUS,
                        at.x + CLEAR_RADIUS, at.y + CLEAR_RADIUS, at.z + CLEAR_RADIUS),
                // Only the display ones. A cast spell in flight is not litter.
                TidalDisruptionEntity::isDisplay);
        for (Entity entity : found) {
            entity.discard();
        }
        int removed = found.size();
        source.sendSuccess(() -> Component.literal("Removed " + removed
                + (removed == 1 ? " effect." : " effects.")), false);
        return removed;
    }
}
