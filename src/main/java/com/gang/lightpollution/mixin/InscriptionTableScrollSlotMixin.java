package com.gang.lightpollution.mixin;

import com.gang.lightpollution.ExampleMod;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.item.Scroll;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets the Inscription Table's scroll slot accept this mod's own scrolls.
 *
 * <p>That slot filters with {@code stack.is(ItemRegistry.SCROLL.get())} — an exact item identity
 * check rather than {@code instanceof Scroll} — so a bespoke scroll item is refused however correct
 * its spell container is. Everything past the slot already works: both {@code doInscription} and
 * {@code clickMenuButton} test only {@code instanceof Scroll}, and the container's own
 * {@code addSpell} gates only on a free slot and the spell not already being present. The slot was
 * the single obstacle.</p>
 *
 * <p><b>The target is an anonymous inner class, and its number was verified rather than guessed.</b>
 * {@code InscriptionTableMenu} compiles to five of them; {@code $3} is the spell book slot
 * ({@code instanceof SpellBook}), {@code $4} is the scroll slot, {@code $5} is the result slot
 * ({@code return false}). Reading the bytecode was the only way to know that — source order would
 * have suggested {@code $2}.</p>
 *
 * <p>Because that number is not part of any API, this mixin lives in a config with
 * {@code defaultRequire: 0}: if Iron's Spells ever reshuffles those classes, this is skipped with a
 * warning instead of crashing the game, and the crafting recipe that converts one of our scrolls to
 * a plain one remains as a working route. A hard requirement on another mod's inner-class numbering
 * would be a crash waiting for their next release.</p>
 */
@Mixin(targets = "io.redspace.ironsspellbooks.gui.inscription_table.InscriptionTableMenu$4")
public class InscriptionTableScrollSlotMixin {
    @Inject(method = "mayPlace", at = @At("HEAD"), cancellable = true)
    private void lightPollution$acceptOurScrolls(ItemStack stack,
                                                 CallbackInfoReturnable<Boolean> callback) {
        if (stack.isEmpty() || !(stack.getItem() instanceof Scroll)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !ExampleMod.MODID.equals(id.getNamespace())) {
            return;
        }
        // Only if it actually carries a spell. An unbound scroll would sit in the slot and offer
        // nothing to inscribe, which reads as the table being broken rather than as an empty scroll.
        if (ISpellContainer.isSpellContainer(stack)) {
            callback.setReturnValue(true);
        }
    }
}
