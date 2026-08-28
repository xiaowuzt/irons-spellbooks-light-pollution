"""Generates the display command pair for a spell, from the shape every one of them shares.

Written after deriving these by sed three times and leaving a wrong ModEntities constant
behind twice. The constant is SCREAMING_CASE, so a rename keyed on the class name misses
it, and the result compiles and spawns the wrong entity — which no compiler catches.

Usage:
    python make_display_command.py <ClassName> <snake_id> <cmd_prefix> <label> <note>
"""
import io
import sys

DEST = 'src/main/java/com/gang/lightpollution/event/'


def main():
    if len(sys.argv) != 6:
        print(__doc__)
        return 1
    cls, snake, prefix, label, note = sys.argv[1:]
    upper = snake.upper()
    io.open(f'{DEST}{cls}DisplayCommands.java', 'w', encoding='utf-8').write(
        f'''package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.{cls}Entity;
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
 * Commands for standing {label} up and taking it down again.
 *
 * <p>{note} Casting the spell to check means minutes of cooldown between looks, so a
 * display one stands with no caster and no damage until it is cleared.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class {cls}DisplayCommands {{
    /** How far ahead of the player it is placed, in blocks. */
    private static final double PLACE_DISTANCE = 42.0D;
    /** How far around the player {{@code clear}} looks, in blocks. */
    private static final double CLEAR_RADIUS = 512.0D;

    private {cls}DisplayCommands() {{
    }}

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {{
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(Commands.literal("{prefix}_show")
                .requires(source -> source.hasPermission(2))
                .executes(context -> show(context.getSource())));

        dispatcher.register(Commands.literal("{prefix}_clear")
                .requires(source -> source.hasPermission(2))
                .executes(context -> clear(context.getSource())));
    }}

    private static int show(CommandSourceStack source) {{
        ServerLevel level = source.getLevel();
        Vec3 eye = source.getPosition();
        float yawRadians = (float) Math.toRadians(source.getRotation().y);
        Vec3 ahead = new Vec3(-Math.sin(yawRadians), 0.0D, Math.cos(yawRadians));
        Vec3 centre = eye.add(ahead.scale(PLACE_DISTANCE));

        {cls}Entity effect = new {cls}Entity(ModEntities.{upper}.get(), level);
        // Leaning across the viewer, which is the angle the structure is legible from.
        effect.configureDisplay(centre, source.getRotation().y + 90.0F,
                level.random.nextInt());
        level.addFreshEntity(effect);
        source.sendSuccess(() -> Component.literal(
                "{label} standing " + (int) PLACE_DISTANCE
                        + " blocks ahead. /{prefix}_clear to remove."), false);
        return 1;
    }}

    private static int clear(CommandSourceStack source) {{
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        List<{cls}Entity> found = level.getEntitiesOfClass(
                {cls}Entity.class,
                new AABB(at.x - CLEAR_RADIUS, at.y - CLEAR_RADIUS, at.z - CLEAR_RADIUS,
                        at.x + CLEAR_RADIUS, at.y + CLEAR_RADIUS, at.z + CLEAR_RADIUS),
                // Only the display ones. A cast spell in flight is not litter.
                {cls}Entity::isDisplay);
        for (Entity entity : found) {{
            entity.discard();
        }}
        int removed = found.size();
        source.sendSuccess(() -> Component.literal("Removed " + removed
                + (removed == 1 ? " effect." : " effects.")), false);
        return removed;
    }}
}}
''')
    print(f'  ok {cls}DisplayCommands.java  (/{prefix}_show, /{prefix}_clear)')
    return 0


sys.exit(main())
