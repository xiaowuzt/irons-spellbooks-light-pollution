package com.gang.lightpollution.recipe;

import com.google.gson.JsonObject;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.api.spells.SpellData;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapelessRecipe;
import net.minecraft.world.level.Level;

/**
 * Turns one of this mod's own scrolls into a plain Iron's Spells scroll carrying the same spell.
 *
 * <p>This exists because the Inscription Table will not accept our scrolls. Its scroll slot filters
 * with {@code stack.is(ItemRegistry.SCROLL.get())} — an exact item identity check, not
 * {@code instanceof Scroll} — so a bespoke scroll item cannot be placed there however correct its
 * spell container is. The table's own inscription logic <em>does</em> only check
 * {@code instanceof Scroll}, which is what made this look like it already worked.</p>
 *
 * <p>A conversion recipe is the least invasive fix available. Mixing into that slot would mean
 * targeting an anonymous inner class by index, which changes whenever the surrounding code does.
 * Changing our items to be the plain scroll would give up the per-spell items entirely.</p>
 *
 * <p>It is a {@link ShapelessRecipe} subclass rather than a special recipe with no declared
 * ingredients, and that is deliberate: a recipe with real ingredients is one JEI and EMI can both
 * display, which is the whole point of the acquisition work this belongs to. Only
 * {@link #assemble} differs, because the result has to carry NBT copied from the input and a
 * datapack result cannot.</p>
 */
public class TranscribeScrollRecipe extends ShapelessRecipe {
    public TranscribeScrollRecipe(ResourceLocation id, String group, CraftingBookCategory category,
                                  ItemStack result, NonNullList<Ingredient> ingredients) {
        super(id, group, category, result, ingredients);
    }

    /**
     * Copy the spell off whichever of our scrolls was used.
     *
     * <p>Falls back to the declared result if the input somehow has no spell on it. That is not a
     * case that should happen — the items bind themselves — but an empty scroll is a better outcome
     * than a crash in the crafting grid.</p>
     */
    @Override
    public ItemStack assemble(CraftingContainer container, RegistryAccess registries) {
        ItemStack result = super.assemble(container, registries);
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack input = container.getItem(slot);
            if (input.isEmpty() || !ISpellContainer.isSpellContainer(input)) {
                continue;
            }
            SpellData spell = ISpellContainer.get(input).getSpellAtIndex(0);
            if (spell != SpellData.EMPTY) {
                ISpellContainer.createScrollContainer(spell.getSpell(), spell.getLevel(), result);
                break;
            }
        }
        return result;
    }

    @Override
    public boolean matches(CraftingContainer container, Level level) {
        // The parent already checks the ingredients. The extra condition is that the scroll actually
        // carries a spell, so a blank one cannot be laundered into a plain scroll.
        if (!super.matches(container, level)) {
            return false;
        }
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack input = container.getItem(slot);
            if (!input.isEmpty() && ISpellContainer.isSpellContainer(input)
                    && ISpellContainer.get(input).getSpellAtIndex(0) != SpellData.EMPTY) {
                return true;
            }
        }
        return false;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipes.TRANSCRIBE_SCROLL.get();
    }

    /**
     * Reads and writes the recipe exactly as a shapeless one, then rebuilds it as this type.
     *
     * <p>Delegating to the vanilla serializer rather than reimplementing it keeps the JSON shape
     * identical to any other shapeless recipe, so the datapack file needs no special knowledge and
     * the ingredient list JEI reads is the ordinary one.</p>
     */
    public static class Serializer implements RecipeSerializer<TranscribeScrollRecipe> {
        @Override
        public TranscribeScrollRecipe fromJson(ResourceLocation id, JsonObject json) {
            ShapelessRecipe base = RecipeSerializer.SHAPELESS_RECIPE.fromJson(id, json);
            return rebuild(base);
        }

        @Override
        public TranscribeScrollRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
            ShapelessRecipe base = RecipeSerializer.SHAPELESS_RECIPE.fromNetwork(id, buffer);
            return base == null ? null : rebuild(base);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, TranscribeScrollRecipe recipe) {
            RecipeSerializer.SHAPELESS_RECIPE.toNetwork(buffer, recipe);
        }

        private static TranscribeScrollRecipe rebuild(ShapelessRecipe base) {
            NonNullList<Ingredient> ingredients = NonNullList.create();
            ingredients.addAll(base.getIngredients());
            return new TranscribeScrollRecipe(base.getId(), base.getGroup(), base.category(),
                    // Empty registry access: a plain scroll's result stack is a fixed item with no
                    // registry lookup behind it, and the spell is stamped on in assemble anyway.
                    base.getResultItem(RegistryAccess.EMPTY), ingredients);
        }
    }
}
