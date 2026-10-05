#version 150
#moj_import <irons_spellbooks_light_pollution:bh_common.glsl>
// Original cheap MC fallback, not presented as a missing author's implementation.
void main() {
    vec3 rd,base;vec2 span;
    if (!bhBegin(rd,base,span)) {fragColor=vec4(0);return;}
    // Photon path still uses authoritative particles in LOW; retain its opaque core here.
    vec4 clip=bhProjectionMatrix*vec4(bhCenterRadius.xyz,1.0);
    if (clip.w<=0.0) {fragColor=vec4(0);return;}
    vec2 center=clip.xy/clip.w*.5+.5,offset=texCoord-center;
    float pathMode=clamp(step(2.5,bhEffect.w)-step(3.5,bhEffect.w),0.0,1.0);
    float impact=length(cross(rd,bhCenterRadius.xyz));
    float rs=max(bhCenterRadius.w,.01);
    float bend=clamp(rs*rs/max(impact*impact,.02),0.0,.65);
    vec3 shifted=bhSceneInDirection(bhRay(texCoord+offset*bend),span.x,base);
    float shadow=1.0-smoothstep(rs*.9,rs*1.05,impact);
    vec3 color=mix(shifted,vec3(.001),shadow);
    float ring=exp(-pow((impact-rs*(1.7+.25*pathMode))/max(.1,rs*.15),2.0));
    color+=vec3(.35,.18,.06)*ring*bhQuality.z;
    bhFinish(color,rd,1.0,0.05);
}
