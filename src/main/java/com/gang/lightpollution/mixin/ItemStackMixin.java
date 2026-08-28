package com.gang.lightpollution.mixin;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import com.gang.lightpollution.text.DynamicTextParser;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** 明确覆盖物品名称和完整悬浮提示（含 Lore 与其他模组追加文本）。 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {
    @Inject(method = "getHoverName", at = @At("RETURN"), cancellable = true)
    private void dynamicTextEffects$parseHoverName(CallbackInfoReturnable<Component> callback) {
        Component original = callback.getReturnValue();
        if (original != null && DynamicTextParser.containsCodes(original.getString())) {
            callback.setReturnValue(DynamicTextParser.parse(original));
        }
    }

    @Inject(
            method = "getTooltipLines(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/TooltipFlag;)Ljava/util/List;",
            at = @At("RETURN"),
            cancellable = true
    )
    private void dynamicTextEffects$parseTooltip(
            Player player,
            TooltipFlag flag,
            CallbackInfoReturnable<List<Component>> callback
    ) {
        List<Component> original = callback.getReturnValue();
        if (original == null || original.stream().noneMatch(line -> DynamicTextParser.containsCodes(line.getString()))) {
            return;
        }

        List<Component> parsed = new ArrayList<>(original.size());
        for (Component line : original) {
            parsed.add(DynamicTextParser.parseIfNeeded(line));
        }
        callback.setReturnValue(parsed);
    }
}
