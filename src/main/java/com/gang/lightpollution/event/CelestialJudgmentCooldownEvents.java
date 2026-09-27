package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.SpellConfig;
import com.gang.lightpollution.spell.CelestialJudgmentSpell;
import com.gang.lightpollution.spell.EclipseSeveranceSpell;
import com.gang.lightpollution.spell.StargraveSingularitySpell;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Keeps the DLC's designated long-cooldown spells at their literal cooldown. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CelestialJudgmentCooldownEvents {
    private CelestialJudgmentCooldownEvents() {
    }

    @SubscribeEvent
    public static void onSpellCooldownAdded(SpellCooldownAddedEvent.Pre event) {
        if (event.getSpell().getSpellResource().equals(CelestialJudgmentSpell.ID)) {
            event.setEffectiveCooldown(SpellConfig.celestialCooldownSeconds * 20);
        } else if (event.getSpell().getSpellResource().equals(StargraveSingularitySpell.ID)) {
            event.setEffectiveCooldown(SpellConfig.stargraveCooldownSeconds * 20);
        } else if (event.getSpell().getSpellResource().equals(EclipseSeveranceSpell.ID)) {
            event.setEffectiveCooldown(SpellConfig.eclipseCooldownSeconds * 20);
        }
    }
}
