package com.gang.lightpollution.net;

import com.gang.lightpollution.text.world.WorldTextConfig;
import com.gang.lightpollution.text.world.WorldTextManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * A spell reaching its own dramatic beat: Gargantua going critical, the magnetar's magnetosphere
 * letting go, a tidal disruption's fallback peaking.
 *
 * <p>These moments were entirely silent before. The spells already compute them — the beat is a
 * named tick in each entity's timeline — so this is about surfacing something that exists rather
 * than inventing a new mechanic.</p>
 *
 * <p>Position rather than an entity id, unlike the damage packet: a caption belongs to the event, not
 * to a creature, and the anchor entity it came from may well be about to be discarded.</p>
 */
public record SpellCaptionPacket(Vec3 at, String translationKey, int colour, float scale) {
    /** Guard on the key length, since it comes off the wire and is used to build a component. */
    private static final int MAX_KEY_LENGTH = 128;

    public static void encode(SpellCaptionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.at().x);
        buffer.writeDouble(packet.at().y);
        buffer.writeDouble(packet.at().z);
        buffer.writeUtf(packet.translationKey(), MAX_KEY_LENGTH);
        buffer.writeInt(packet.colour());
        buffer.writeFloat(packet.scale());
    }

    public static SpellCaptionPacket decode(FriendlyByteBuf buffer) {
        Vec3 at = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        return new SpellCaptionPacket(at, buffer.readUtf(MAX_KEY_LENGTH),
                buffer.readInt(), buffer.readFloat());
    }

    public static void handle(SpellCaptionPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> show(packet)));
        ctx.setPacketHandled(true);
    }

    private static void show(SpellCaptionPacket packet) {
        Component text = Component.translatable(packet.translationKey())
                .withStyle(style -> style.withColor(packet.colour()).withBold(true));
        WorldTextManager.spawn(text, packet.at(), new WorldTextConfig()
                .setScale(packet.scale())
                .setEnterAnimation(WorldTextConfig.AnimationType.ROTATE_IN)
                .setExitAnimation(WorldTextConfig.AnimationType.ZOOM_OUT)
                .setEnterDuration(12L)
                .setStayDuration(30L)
                .setExitDuration(16L)
                .setRotationAnimated(6.0F, 10L)
                .setRotationRandomDirection(true)
                .setShadow(true));
    }
}
