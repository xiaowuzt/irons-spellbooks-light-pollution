package com.gang.lightpollution.mixin;

/*
 * Ported from the author's own Dynamic Text Effects mod (cn.blockforge.dynamictext),
 * flattened into this mod's packages so the effects work without that mod installed.
 *
 * The FTB Quests and ModernUI compatibility layers were deliberately left behind: they need
 * those mods on the compile classpath, and this mod has no quest text to style.
 */

import com.gang.lightpollution.text.DynamicTextRenderer;
import com.gang.lightpollution.text.DynamicTextParser;
import com.gang.lightpollution.text.EffectStyle;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.MultiBufferSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/** 让原版字体使用真实字体资源，并让控制码不参与宽度、截断和换行计算。 */
@Mixin(value = Font.class, priority = 2)
public abstract class FontMixin {
    @ModifyVariable(method = "getFontSet", at = @At("HEAD"), argsOnly = true)
    private ResourceLocation dynamicTextEffects$decodeFont(ResourceLocation font) {
        return EffectStyle.baseFont(font);
    }

    @Inject(
            method = "drawInBatch(Ljava/lang/String;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;IIZ)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dynamicTextEffects$drawFormattedString(
            String text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int backgroundColor,
            int packedLight,
            boolean bidirectional,
            CallbackInfoReturnable<Integer> callback
    ) {
        if (!DynamicTextParser.containsCodes(text)) {
            return;
        }
        Component parsed = DynamicTextParser.parse(text);
        FormattedCharSequence sequence = parsed.getVisualOrderText();
        callback.setReturnValue(DynamicTextRenderer.draw(
                (Font) (Object) this,
                sequence,
                x,
                y,
                color,
                shadow,
                pose,
                buffers,
                mode,
                backgroundColor,
                packedLight
        ));
    }

    /** ModernUI 会覆盖这两个入口；含动态效果时改走原版逐字字形通道。 */
    @Inject(
            method = "drawInBatch(Lnet/minecraft/network/chat/Component;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dynamicTextEffects$drawComponent(
            Component text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int backgroundColor,
            int packedLight,
            CallbackInfoReturnable<Integer> callback
    ) {
        Component parsed = DynamicTextParser.parseIfNeeded(text);
        if (!EffectStyle.hasEffects(parsed)) {
            return;
        }
        FormattedCharSequence sequence = parsed.getVisualOrderText();
        callback.setReturnValue(DynamicTextRenderer.draw(
                (Font) (Object) this,
                sequence,
                x,
                y,
                color,
                shadow,
                pose,
                buffers,
                mode,
                backgroundColor,
                packedLight
        ));
    }

    @Inject(
            method = "drawInBatch(Lnet/minecraft/util/FormattedCharSequence;FFIZLorg/joml/Matrix4f;Lnet/minecraft/client/renderer/MultiBufferSource;Lnet/minecraft/client/gui/Font$DisplayMode;II)I",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dynamicTextEffects$drawSequence(
            FormattedCharSequence text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f pose,
            MultiBufferSource buffers,
            Font.DisplayMode mode,
            int backgroundColor,
            int packedLight,
            CallbackInfoReturnable<Integer> callback
    ) {
        if (!EffectStyle.hasEffects(text)) {
            return;
        }
        callback.setReturnValue(DynamicTextRenderer.draw(
                (Font) (Object) this,
                text,
                x,
                y,
                color,
                shadow,
                pose,
                buffers,
                mode,
                backgroundColor,
                packedLight
        ));
    }

    @Inject(method = "width(Ljava/lang/String;)I", at = @At("HEAD"), cancellable = true)
    private void dynamicTextEffects$widthString(String text, CallbackInfoReturnable<Integer> callback) {
        if (DynamicTextParser.containsCodes(text)) {
            callback.setReturnValue(((Font) (Object) this).width(DynamicTextParser.parse(text)));
        }
    }

    @Inject(method = "width(Lnet/minecraft/network/chat/FormattedText;)I", at = @At("HEAD"), cancellable = true)
    private void dynamicTextEffects$widthFormatted(FormattedText text, CallbackInfoReturnable<Integer> callback) {
        if (!DynamicTextParser.needsParsing(text)) {
            return;
        }
        Component parsed = DynamicTextParser.parse(text);
        callback.setReturnValue(((Font) (Object) this).width(parsed));
    }

    @Inject(
            method = "split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dynamicTextEffects$split(
            FormattedText text,
            int maxWidth,
            CallbackInfoReturnable<List<FormattedCharSequence>> callback
    ) {
        if (!DynamicTextParser.needsParsing(text)) {
            return;
        }
        Component parsed = DynamicTextParser.parse(text);
        callback.setReturnValue(((Font) (Object) this).split(parsed, maxWidth));
    }

    @Inject(
            method = "substrByWidth(Lnet/minecraft/network/chat/FormattedText;I)Lnet/minecraft/network/chat/FormattedText;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void dynamicTextEffects$substrByWidth(
            FormattedText text,
            int maxWidth,
            CallbackInfoReturnable<FormattedText> callback
    ) {
        if (!DynamicTextParser.needsParsing(text)) {
            return;
        }
        Component parsed = DynamicTextParser.parse(text);
        callback.setReturnValue(((Font) (Object) this).substrByWidth(parsed, maxWidth));
    }
}
