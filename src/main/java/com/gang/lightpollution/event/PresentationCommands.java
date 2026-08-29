package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.SpellLightConfig.TooltipStyle;
import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Locale;

/**
 * Client commands for the presentation toggles.
 *
 * <p>Registered as client commands rather than server ones, and that is not a detail: these control
 * how this client draws things. A server command would be sent to the server, which has no opinion
 * about tooltips and no field to change, so it would silently do nothing in multiplayer.</p>
 *
 * <p>No permission requirement either, for the same reason — nothing here affects anyone else, and
 * needing operator rights to change your own tooltip border would be absurd.</p>
 */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class PresentationCommands {
    private PresentationCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        var frame = Commands.literal("frame")
                .executes(context -> setStyle(context.getSource(), null));
        for (TooltipStyle style : TooltipStyle.values()) {
            frame = frame.then(Commands.literal(style.name().toLowerCase(Locale.ROOT))
                    .executes(context -> setStyle(context.getSource(), style)));
        }

        var damage = Commands.literal("damage")
                .executes(context -> setFloatingDamage(context.getSource(), null))
                .then(Commands.literal("on")
                        .executes(context -> setFloatingDamage(context.getSource(), true)))
                .then(Commands.literal("off")
                        .executes(context -> setFloatingDamage(context.getSource(), false)));

        dispatcher.register(Commands.literal("lightpollution").then(frame).then(damage));
    }

    /** Turn the floating damage numbers on, off, or to the other state. */
    private static int setFloatingDamage(CommandSourceStack source, Boolean explicit) {
        boolean next = explicit != null ? explicit : !SpellLightConfig.floatingDamage;
        SpellLightConfig.floatingDamage = next;
        source.sendSuccess(() -> Component.literal(
                "Floating damage numbers " + (next ? "on" : "off")
                        + ". This session only — set floatingDamage in the client config"
                        + " to keep it."), false);
        return 1;
    }

    /**
     * Pick a tooltip style, or step to the next one when none is named.
     *
     * <p>Writes the in-memory field only. Deliberately not persisted: reloading the config reads the
     * file again and would overwrite this, so the command is for trying a look and the config file
     * is for keeping it. Saying so in the reply is the only way that is discoverable.</p>
     */
    private static int setStyle(CommandSourceStack source, TooltipStyle explicit) {
        TooltipStyle[] all = TooltipStyle.values();
        TooltipStyle next = explicit != null
                ? explicit
                : all[(SpellLightConfig.tooltipStyle.ordinal() + 1) % all.length];
        SpellLightConfig.tooltipStyle = next;
        source.sendSuccess(() -> Component.literal(
                "Tooltip style: " + next.name().toLowerCase(Locale.ROOT)
                        + ". This session only — set tooltipStyle in the client config"
                        + " to keep it."), false);
        return 1;
    }
}
