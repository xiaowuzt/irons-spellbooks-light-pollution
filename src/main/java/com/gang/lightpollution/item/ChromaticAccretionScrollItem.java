package com.gang.lightpollution.item;

import com.gang.lightpollution.spell.ModSpells;

/** A dedicated scroll that always contains Chromatic Accretion at level one. */
public final class ChromaticAccretionScrollItem extends BoundSpellScrollItem {
    public ChromaticAccretionScrollItem(Properties properties) {
        super(properties, ModSpells.CHROMATIC_ACCRETION::get, 1);
    }
}
