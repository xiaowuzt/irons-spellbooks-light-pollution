package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.MicroquasarEntity;
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
 * Commands for standing a Microquasar up and taking it down again.
 *
 * <p>The corkscrew's pitch depends on the ratio between jet speed and precession rate,
 * which has to be judged by eye — too tight and it reads as a rope, too loose and it
 * reads as a beam that wobbles. Casting the spell to check means a six-minute cooldown
 * between looks. A display one keeps precessing rather than freezing, because the sweep
 * is the thing being inspected, and stands until it is cleared.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MicroquasarDisplayCommands {
    /** How far ahead of the player it is placed, in blocks. */
    private static final double PLACE_DISTANCE = 40.0D;
    /** How far around the player {@code clear} looks, in blocks. */
    private static final double CLEAR_RADIUS = 512.0D;

    private MicroquasarDisplayCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("quasar_show")
                .requires(source -> source.hasPermission(2))
                .executes(context -> show(context.getSource())));

        dispatcher.register(Commands.literal("quasar_clear")
                .requires(source -> source.hasPermission(2))
                .executes(context -> clear(context.getSource())));
    }

    private static int show(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 eye = source.getPosition();
        float yawRadians = (float) Math.toRadians(source.getRotation().y);
        Vec3 ahead = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3 centre = eye.add(ahead.scale(PLACE_DISTANCE));

        MicroquasarEntity effect = new MicroquasarEntity(
                ModEntities.MICROQUASAR.get(), level);
        // Axis across the viewer's line of sight, so the corkscrew is seen side-on. A
        // helix viewed down its own axis is just a circle; side-on is the only angle the
        // coil is legible from.
        effect.configureDisplay(centre, source.getRotation().y + 90.0F,
                level.random.nextInt());
        level.addFreshEntity(effect);
        source.sendSuccess(() -> Component.literal(
                "Microquasar standing " + (int) PLACE_DISTANCE
                        + " blocks ahead. /quasar_clear to remove."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        List<MicroquasarEntity> found = level.getEntitiesOfClass(
                MicroquasarEntity.class,
                new AABB(at.x - CLEAR_RADIUS, at.y - CLEAR_RADIUS, at.z - CLEAR_RADIUS,
                        at.x + CLEAR_RADIUS, at.y + CLEAR_RADIUS, at.z + CLEAR_RADIUS),
                // Only the display ones. A cast spell in flight is not litter.
                MicroquasarEntity::isDisplay);
        for (Entity entity : found) {
            entity.discard();
        }
        int removed = found.size();
        source.sendSuccess(() -> Component.literal("Removed " + removed
                + (removed == 1 ? " microquasar." : " microquasars.")), false);
        return removed;
    }
}
