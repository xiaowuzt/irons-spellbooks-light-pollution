package com.gang.lightpollution.client.renderer;

import com.gang.lightpollution.BlackHoleVisualConfig;
import com.gang.lightpollution.SpellLightConfig;
import com.gang.lightpollution.entity.*;
import com.gang.lightpollution.fx.BlackHoleInstance;
import net.minecraft.world.entity.Entity;
import com.gang.lightpollution.fx.BlackHoleParameters;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** The single bh* uniform bridge. All positions are camera-relative before conversion to float. */
public final class BlackHoleUniforms {
    private BlackHoleUniforms() {}
    /** Client optical copy only. R1's historical visual scale also applies to its attached
     * lens/stasis skin. The R4 damage shell MUST keep its authoritative world size. */
    public static BlackHoleParameters opticalParameters(Entity entity, float partial) {
        BlackHoleParameters p = ((BlackHoleInstance) entity).bhParameters(partial);
        Entity root = entity instanceof BlackHoleEntity bh && !bh.bhIsRoot() ? bh.rootEntity() : entity;
        if (root instanceof RedshiftAbyssEntity && !(entity instanceof RadiantCollapseEntity)) {
            return p.at(p.bhCenter(), p.bhHorizonRadius() * SpellLightConfig.redshiftAbyssVisualScale, p.bhInfluenceRadius());
        }
        return p;
    }
    public static Vector3f eye(Matrix4f rotation, Vec3 value) {
        return rotation.transformDirection(new Vector3f((float)value.x,(float)value.y,(float)value.z));
    }
    public static void upload(ShaderInstance shader, BlackHoleParameters p, Matrix4f projection,
            Matrix4f inverse, Matrix4f rotation, Vec3 camera, int width, int height, float support, int steps) {
        Vector3f center=eye(rotation,p.bhCenter().subtract(camera)),normal=eye(rotation,p.bhDiskNormal()).normalize();
        matrix(shader,"bhProjectionMatrix",projection);matrix(shader,"bhInverseProjectionMatrix",inverse);
        matrix(shader,"bhModelViewMatrix",rotation);
        vec4(shader,"bhCenterRadius",center.x,center.y,center.z,p.bhHorizonRadius());
        vec4(shader,"bhRadii",p.bhInfluenceRadius(),support,p.bhGeometricMass(),0);
        vec4(shader,"bhTiming",p.bhTimeSeconds(),p.bhAgeTicks(),p.bhEnvelope(),p.bhTimeScale());
        vec4(shader,"bhScreenSize",width,height,1F/width,1F/height);
        vec4(shader,"bhQuality",steps,BlackHoleVisualConfig.dopplerStrength,BlackHoleVisualConfig.exposure,BlackHoleVisualConfig.debugMode);
        vec4(shader,"bhDiskNormal",normal.x,normal.y,normal.z,p.bhSeed()&65535);
    }
    public static void matrix(ShaderInstance shader,String key,Matrix4f value) {var u=shader.getUniform(key);if(u!=null)u.set(value);}
    public static void vec4(ShaderInstance shader,String key,float a,float b,float c,float d) {var u=shader.getUniform(key);if(u!=null)u.set(a,b,c,d);}
}
