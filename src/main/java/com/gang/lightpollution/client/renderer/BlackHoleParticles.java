package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.ExampleMod;
import com.gang.lightpollution.BlackHoleVisualConfig;
import com.gang.lightpollution.entity.BlackHoleEntity;
import com.gang.lightpollution.entity.RadiantCollapseEntity;
import com.gang.lightpollution.fx.BlackHoleInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;
import java.util.ArrayList;
import java.util.Comparator;

/** Functional fallback on vanilla particles, not dependent on Iris/OptiFine or our custom GLSL. */
@Mod.EventBusSubscriber(modid=ExampleMod.MODID,bus=Mod.EventBusSubscriber.Bus.FORGE,value=Dist.CLIENT)
public final class BlackHoleParticles {
    private BlackHoleParticles() {}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc=Minecraft.getInstance();
        if(event.phase!=TickEvent.Phase.END || mc.level==null || mc.player==null || mc.isPaused() || mc.level.getGameTime()%2!=0) return;
        var candidates=new ArrayList<Entity>();
        for(Entity entity:mc.level.entitiesForRendering()) if(entity instanceof BlackHoleInstance
                && BlackHoleRenderer.particleFallback(entity) && entity.distanceToSqr(mc.player)<128*128
                && (!(entity instanceof BlackHoleEntity bh) || bh.bhReady())) candidates.add(entity);
        candidates.sort(Comparator.comparingDouble(e->e.distanceToSqr(mc.player)));
        for(int n=0;n<Math.min(BlackHoleVisualConfig.maxVisible,candidates.size());n++) {
            Entity entity=candidates.get(n);var p=BlackHoleUniforms.opticalParameters(entity,0);
            if(p.bhEnvelope()<.01) continue;
            var color=new Vector3f(1F,.52F,.18F);
            var particle=new DustParticleOptions(color,1.1F);
            Vec3 normal=p.bhDiskNormal();Vec3 x=normal.cross(Math.abs(normal.z)<.9?new Vec3(0,0,1):new Vec3(1,0,0)).normalize();Vec3 y=x.cross(normal).normalize();
            float radius=entity instanceof RadiantCollapseEntity ring?ring.shellRadius(0):p.bhHorizonRadius()*2.6F;
            for(int i=0;i<16;i++) {
                double angle=i*Math.PI/8+p.bhTimeSeconds()*.6;
                Vec3 point=p.bhCenter().add(x.scale(Math.cos(angle)*radius)).add(y.scale(Math.sin(angle)*radius));
                mc.level.addParticle(particle,point.x,point.y,point.z,0,0,0);
            }
        }
    }
}
