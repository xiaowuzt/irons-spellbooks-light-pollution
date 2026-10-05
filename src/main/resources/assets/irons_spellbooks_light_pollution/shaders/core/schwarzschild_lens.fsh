#version 150
#moj_import <irons_spellbooks_light_pollution:bh_common.glsl>
#moj_import <irons_spellbooks_light_pollution:bh_r2_geodesic.glsl>
#moj_import <irons_spellbooks_light_pollution:bh_disk_material.glsl>
#moj_import <irons_spellbooks_light_pollution:bh_schwarzschild_gas.glsl>
uniform sampler2D bhDeflectionSampler;
uniform sampler2D bhInverseRadiusSampler;
// R2/R7 static Schwarzschild observer and two accretion-disk intersections.
// Sampling Minecraft colour is a SCREEN-SPACE approximation, not R7's all-sky cube maps.
vec3 diskLayer(vec3 background,vec3 ex,vec3 ey,float u,float phi,float coverage,float e2,float radialSign) {
    mat3 inverseBasis=transpose(bhDiskBasis());
    vec3 radial=ex*cos(phi)+ey*sin(phi),angular=-ex*sin(phi)+ey*cos(phi);
    vec3 p=inverseBasis*(radial/max(u,1e-6));
    // Evaluate derivatives before the per-fragment crossing/visibility branches.
    float footprint=max(length(dFdx(p)),length(dFdy(p)));
    if(u<=0.0 || coverage<=0.0) return background;
    // Static-observer tangent reconstructed from R2's conserved energy at this crossing.
    vec3 eyeTangent=bhSafeNormal(angular*u*sqrt(max(1.0-u,.001))
        -radial*radialSign*sqrt(max(e2-u*u*(1.0-u),0.0)));
    vec3 tangent=inverseBasis*eyeTangent;
    float halfLength=min(2.0,bhR2Gas.x/max(abs(tangent.y),.04));
    // bhBegin only rejects surfaces before the large support sphere. A wall can be
    // inside that sphere and still obscure this crossing, or cut through its gas.
    // Match the fragment's scene Z depth, not radial distance or LUT transport time.
    float sceneDepth=-bhRay(texCoord).z*bhDepthDistance(texCoord)/bhCenterRadius.w;
    vec2 visible=bhR2VisibleSpan(halfLength,-bhCenterRadius.z/bhCenterRadius.w-radial.z/u,
        -eyeTangent.z,sceneDepth);
    if(visible.y<=visible.x) return background;
    p+=tangent*(visible.x+visible.y)*.5;
    vec4 gas=bhR2Integrate(p,tangent,(visible.y-visible.x)*.5,footprint);
    float opacity=clamp(gas.a*coverage,0.0,1.0);
    return background*(1.0-opacity)+gas.rgb*coverage;
}
void main() {
    vec3 rd,base;vec2 span;
    if (!bhBegin(rd,base,span)) {fragColor=vec4(0);return;}
    float distance=length(bhCenterRadius.xyz)/max(bhCenterRadius.w,.005);
    if (distance<=1.001) {bhFinish(vec3(.002),rd,1.0,1.0);return;} // R7 excludes observers inside horizon
    float u=1.0/distance;
    vec3 ex=bhSafeNormal(-bhCenterRadius.xyz);
    // Orthonormal static observer -> Schwarzschild spatial coordinate direction.
    vec3 d=bhSafeNormal(rd+(sqrt(max(1.0-u,1e-5))-1.0)*dot(rd,ex)*ex);
    vec3 ez=cross(ex,d);
    if (dot(ez,ez)<1e-10) ez=cross(ex,abs(ex.y)<.9?vec3(0,1,0):vec3(1,0,0));
    ez=bhSafeNormal(ez);vec3 ey=bhSafeNormal(cross(ez,ex));
    vec3 t=cross(bhDiskNormal.xyz,ez);
    if (dot(t,t)<1e-10) t=ey;
    t=bhSafeNormal(t);if (dot(t,ey)<0.0) t=-t;
    float alpha=acos(clamp(dot(ex,t),-1.0,1.0));
    float delta=clamp(acos(clamp(dot(ex,d),-1.0,1.0)),1e-5,bhPI-1e-5);
    float ud=-u/tan(delta),e2=clamp(ud*ud+u*u*(1.0-u),1e-8,1e6);
    // Keep the photon-orbit singularity finite at LUT resolution, rather than log(0).
    if (abs(e2-kMu)<1e-7) e2=kMu+(e2<kMu?-1e-7:1e-7);
    float u0,phi0,t0,a0,u1,phi1,t1,a1;
    float deflection=TraceRay(bhDeflectionSampler,bhInverseRadiusSampler,u,ud,e2,delta,alpha,
            1.0/3.0,1.0/10.0,u0,phi0,t0,a0,u1,phi1,t1,a1);
    vec3 outDirection=cos(delta+max(deflection,0.0))*ex+sin(delta+max(deflection,0.0))*ey;
    vec3 color=deflection<0.0?vec3(.001):bhSceneInDirection(outDirection,span.x,base);
    if (int(bhQuality.w+.5)==4 && deflection>=0.0) color=bhCalibrationGrid(outDirection);
    // Far intersection first, then near: the emitting disk also absorbs background.
    color=diskLayer(color,ex,ey,u1,phi1,a1,e2,-1.0);
    color=diskLayer(color,ex,ey,u0,phi0,a0,e2,sign(ud));
    // A thin-disk LUT alone has no crossing for an exactly coplanar observer.
    // Resolve its finite, near-side gas thickness without seeing through the shadow.
    mat3 invBasis=transpose(bhDiskBasis());
    vec3 directOrigin=invBasis*(-bhCenterRadius.xyz)/bhCenterRadius.w;
    vec3 directRay=invBasis*rd;
    float grazing=1.0-smoothstep(.025,.10,abs(directRay.y));
    // Supplement only missing LUT coverage, never a third absorbing layer over
    // an already-covered crossing. Keep complementary weights at AA boundaries.
    grazing*=bhR2MissingLutCoverage(u0,a0,u1,a1);
    if(grazing>0.0) {
        float limit=bhDepthDistance(texCoord)/bhCenterRadius.w;
        vec2 core=bhSphere(rd,bhCenterRadius.w*2.6);
        if(core.y>core.x)limit=min(limit,core.x/bhCenterRadius.w);
        vec2 slab=bhDiskSlab(directOrigin,directRay,10.0,bhR2Gas.x,limit);
        if(slab.y>slab.x) {
            vec3 mid=directOrigin+directRay*(slab.x+slab.y)*.5;
            vec4 gas=bhR2Integrate(mid,directRay,(slab.y-slab.x)*.5,.02);
            color=color*(1.0-gas.a*grazing)+gas.rgb*grazing;
        }
    }
    bhFinish(color,rd,1.0,clamp(max(deflection,0.0)/(2.0*bhPI),0.0,1.0));
}
