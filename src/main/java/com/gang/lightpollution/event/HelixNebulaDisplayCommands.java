package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.HelixNebulaEntity;
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
 * Commands for standing a Helix Nebula up and taking it down again.
 *
 * <p>Whether the two rings read as an eye depends on their spacing and tilt, which has to
 * be judged by eye. Casting the spell to check means a five-and-a-half minute cooldown
 * between looks. A display one holds at full extent rather than looping, because the shell
 * only ever grows — restarting the expansion would make it harder to look at, not easier —
 * and stands until it is cleared.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HelixNebulaDisplayCommands {
    /** How far ahead of the player it is placed, in blocks. */
    private static final double PLACE_DISTANCE = 40.0D;
    /** How far around the player {@code clear} looks, in blocks. */
    private static final double CLEAR_RADIUS = 512.0D;

    private HelixNebulaDisplayCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("helix_show")
                .requires(source -> source.hasPermission(2))
                .executes(context -> show(context.getSource())));

        dispatcher.register(Commands.literal("helix_clear")
                .requires(source -> source.hasPermission(2))
                .executes(context -> clear(context.getSource())));
    }

    private static int show(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 eye = source.getPosition();
        float yawRadians = (float) Math.toRadians(source.getRotation().y);
        Vec3 ahead = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3 centre = eye.add(ahead.scale(PLACE_DISTANCE));

        HelixNebulaEntity effect = new HelixNebulaEntity(
                ModEntities.HELIX_NEBULA.get(), level);
        // Rings leaning across the viewer's line of sight, which is the angle the nested
        // pair looks like an eye from. Face-on they are concentric circles.
        effect.configureDisplay(centre, source.getRotation().y + 90.0F,
                level.random.nextInt());
        level.addFreshEntity(effect);
        source.sendSuccess(() -> Component.literal(
                "Helix Nebula standing " + (int) PLACE_DISTANCE
                        + " blocks ahead. /helix_clear to remove."), false);
        return 1;
    }

    private static int clear(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        List<HelixNebulaEntity> found = level.getEntitiesOfClass(
                HelixNebulaEntity.class,
                new AABB(at.x - CLEAR_RADIUS, at.y - CLEAR_RADIUS, at.z - CLEAR_RADIUS,
                        at.x + CLEAR_RADIUS, at.y + CLEAR_RADIUS, at.z + CLEAR_RADIUS),
                // Only the display ones. A cast spell in flight is not litter.
                HelixNebulaEntity::isDisplay);
        for (Entity entity : found) {
            entity.discard();
        }
        int removed = found.size();
        source.sendSuccess(() -> Component.literal("Removed " + removed
                + (removed == 1 ? " nebula." : " nebulae.")), false);
        return removed;
    }
}
