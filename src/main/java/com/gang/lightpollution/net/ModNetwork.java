package com.gang.lightpollution.net;

import com.gang.lightpollution.ExampleMod;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * The mod's one network channel.
 *
 * <p>Added because every spell here resolves its damage inside a server-only entity tick, so the
 * client has no way to know a hit happened. Everything else in this mod is drawn from synced entity
 * data as a pure function of a seed and a start tick, which needs no packets at all — this is the
 * first thing that genuinely does.</p>
 *
 * <p>Both payloads go out with {@link PacketDistributor#NEAR}, so a hit is only sent to players who
 * could see it. {@link #TEXT_RANGE} is deliberately shorter than render distance: floating text
 * hundreds of blocks away is unreadable, and sending it would be traffic for nothing.</p>
 */
public final class ModNetwork {
    private static final String VERSION = "1";
    /** How far a floating text is sent, in blocks. */
    private static final double TEXT_RANGE = 64.0D;

    public static final SimpleChannel CHANNEL = NetworkRegistry.ChannelBuilder
            .named(new ResourceLocation(ExampleMod.MODID, "main"))
            .networkProtocolVersion(() -> VERSION)
            .clientAcceptedVersions(VERSION::equals)
            .serverAcceptedVersions(VERSION::equals)
            .simpleChannel();

    private ModNetwork() {
    }

    public static void register() {
        int id = 0;
        CHANNEL.messageBuilder(SpellDamageTextPacket.class, id++)
                .encoder(SpellDamageTextPacket::encode)
                .decoder(SpellDamageTextPacket::decode)
                .consumerMainThread(SpellDamageTextPacket::handle)
                .add();
        CHANNEL.messageBuilder(SpellCaptionPacket.class, id++)
                .encoder(SpellCaptionPacket::encode)
                .decoder(SpellCaptionPacket::decode)
                .consumerMainThread(SpellCaptionPacket::handle)
                .add();
    }

    /** Tell nearby clients that a target took damage from one of this mod's spells. */
    public static void sendDamageText(ServerLevel level, Vec3 at, int entityId,
                                      float amount, int colour) {
        if (!com.gang.lightpollution.performance.ServerPerformanceBudget.allowDamageText()) {
            return;
        }
        CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        at.x, at.y, at.z, TEXT_RANGE, level.dimension())),
                new SpellDamageTextPacket(entityId, amount, colour));
    }

    /** Tell nearby clients to show a caption — a spell reaching its own dramatic beat. */
    public static void sendCaption(ServerLevel level, Vec3 at, String translationKey, int colour,
                                   float scale) {
        CHANNEL.send(
                PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(
                        at.x, at.y, at.z, TEXT_RANGE, level.dimension())),
                new SpellCaptionPacket(at, translationKey, colour, scale));
    }
}
