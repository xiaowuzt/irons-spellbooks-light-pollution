package com.gang.lightpollution.event;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.entity.BlackHoleEntity;
import com.gang.lightpollution.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import java.util.function.Supplier;

/** Operator-only server-tracked previews. No damage, block changes, pull or inventory effects. */
@Mod.EventBusSubscriber(modid = ExampleMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BlackHoleDisplayCommands {
    private BlackHoleDisplayCommands() {}
    @SubscribeEvent public static void register(RegisterCommandsEvent event) {
        register(event, "schwarzschild_lens", ModEntities.SCHWARZSCHILD_LENS);
        register(event, "radiant_collapse", ModEntities.RADIANT_COLLAPSE);
        register(event, "stasis_singularity", ModEntities.STASIS_SINGULARITY);
    }
    private static void register(RegisterCommandsEvent event, String id,
            Supplier<? extends EntityType<? extends BlackHoleEntity>> type) {
        event.getDispatcher().register(Commands.literal(id + "_show")
                .requires(source -> source.hasPermission(2))
                .executes(context -> show(context.getSource(), type.get(), id)));
        event.getDispatcher().register(Commands.literal(id + "_clear")
                .requires(source -> source.hasPermission(2))
                .executes(context -> clear(context.getSource(), id)));
    }
    private static int show(CommandSourceStack source, EntityType<? extends BlackHoleEntity> type, String id) {
        var level = source.getLevel();
        double yaw = Math.toRadians(source.getRotation().y);
        Vec3 center = source.getPosition().add(-Math.sin(yaw) * 36, 2, Math.cos(yaw) * 36);
        center = new Vec3(center.x, Math.max(level.getMinBuildHeight() + 2,
                Math.min(level.getMaxBuildHeight() - 2, center.y)), center.z);
        BlackHoleEntity effect = type.create(level);
        if (effect == null) return 0;
        effect.configureDisplay(center, source.getRotation().y, level.random.nextInt());
        if (!level.addFreshEntity(effect)) {
            source.sendFailure(Component.literal("Preview refused by the existing spell entity budget."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(id + " harmless preview: 36 blocks ahead. /" + id + "_clear to remove."), false);
        return 1;
    }
    private static int clear(CommandSourceStack source, String id) {
        var entities = source.getLevel().getEntitiesOfClass(BlackHoleEntity.class,
                new AABB(source.getPosition(), source.getPosition()).inflate(512),
                entity -> entity.bhIsDisplay() && entity.effectId().equals(id));
        entities.forEach(BlackHoleEntity::discard);
        source.sendSuccess(() -> Component.literal("Removed " + entities.size() + " " + id + " preview(s)."), false);
        return entities.size();
    }
}
