package com.gang.lightpollution.item;

import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.function.Supplier;

/** A scroll item whose default stack is bound to one registered spell. */
public class BoundSpellScrollItem extends Scroll {
    private final Supplier<? extends AbstractSpell> spell;
    private final int spellLevel;

    public BoundSpellScrollItem(
            Properties properties,
            Supplier<? extends AbstractSpell> spell,
            int spellLevel) {
        super(properties);
        this.spell = spell;
        this.spellLevel = spellLevel;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack stack = super.getDefaultInstance();
        ensureBound(stack);
        return stack;
    }

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        ensureBound(stack);
        super.inventoryTick(stack, level, entity, slot, selected);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ensureBound(player.getItemInHand(hand));
        return super.use(level, player, hand);
    }

    @Override
    public Component getName(ItemStack stack) {
        ensureBound(stack);
        return super.getName(stack);
    }

    @Override
    public void appendHoverText(
            ItemStack stack,
            Level level,
            List<Component> tooltip,
            TooltipFlag flag) {
        ensureBound(stack);
        super.appendHoverText(stack, level, tooltip, flag);
    }

    private void ensureBound(ItemStack stack) {
        if (!stack.isEmpty() && !ISpellContainer.isSpellContainer(stack)) {
            ISpellContainer.createScrollContainer(spell.get(), spellLevel, stack);
        }
    }
}
