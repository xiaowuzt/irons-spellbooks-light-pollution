package com.gang.lightpollution.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One target took spell damage: show a number over it.
 *
 * <p>Carries the entity id rather than a position, so the client can look the target up and put the
 * number where it is <em>now</em>. It also gives the client a stable key to accumulate against —
 * these spells tick damage, and a position key would split one creature's total across several piles
 * as it walked.</p>
 */
public record SpellDamageTextPacket(int entityId, float amount, int colour) {
    public static void encode(SpellDamageTextPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId());
        buffer.writeFloat(packet.amount());
        buffer.writeInt(packet.colour());
    }

    public static SpellDamageTextPacket decode(FriendlyByteBuf buffer) {
        return new SpellDamageTextPacket(
                buffer.readVarInt(), buffer.readFloat(), buffer.readInt());
    }

    public static void handle(SpellDamageTextPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            // Gated here rather than at the send: the preference is a client config, and the server
            // has no way to know it. That does mean the packet travels even when nobody will draw
            // it. Twenty-five bytes to players within sixty-four blocks is cheap enough not to be
            // worth a whole preference-sync round trip, but it is a real cost and worth naming.
            if (com.gang.lightpollution.SpellLightConfig.floatingDamage) {
                com.gang.lightpollution.text.world.WorldTextManager.reportDamage(
                        packet.entityId(), packet.amount(), packet.colour());
            }
        }));
        ctx.setPacketHandled(true);
    }
}
