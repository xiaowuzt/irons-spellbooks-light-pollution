package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
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
            event.setEffectiveCooldown(CelestialJudgmentSpell.FIXED_COOLDOWN_TICKS);
        } else if (event.getSpell().getSpellResource().equals(StargraveSingularitySpell.ID)) {
            event.setEffectiveCooldown(StargraveSingularitySpell.FIXED_COOLDOWN_TICKS);
        } else if (event.getSpell().getSpellResource().equals(EclipseSeveranceSpell.ID)) {
            event.setEffectiveCooldown(EclipseSeveranceSpell.FIXED_COOLDOWN_TICKS);
        }
    }
}
