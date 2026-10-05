#version 150
#moj_import <irons_spellbooks_light_pollution:bh_common.glsl>
// Xor / owner-supplied R4 field, preserved as a field, NOT a texture on a different disk.
// Only the source coordinate/time inputs, finite guards and world-depth compositing differ.
uniform vec4 bhR4Options; // time multiplier, visual scale, emission multiplier, reserved
vec4 bhR4Field(vec2 p,float time) {
    vec2 d=vec2(-1,1);
    float denominator=max(dot(5.0*p-d,5.0*p-d),1e-12);
    vec2 c=p*mat2(1.0,1.0,d/(0.1+5.0/denominator)),v=c;
    v*=mat2(cos(log(max(length(v),1e-7))+time*0.2+vec4(0,33,11,0)))*5.0;
    vec4 color=vec4(0.0);
    // The supplied loop leaves i uninitialized. Explicit 1..9 is its intended sequence.
    for(int n=1;n<=9;n++) {
        float i=float(n);
        v+=0.7*sin(v.yx*i+time)/i+0.5;
        color+=sin(v.xyyx)+1.0;
    }
    color=1.0-exp(-exp(clamp(c.x*vec4(0.6,-0.4,-1,0),vec4(-80),vec4(80)))
        /max(color,vec4(1e-8))
        /(0.1+0.1*pow(length(sin(v/0.3)*0.2+c*vec2(1,2))-1.0,2.0))
        /(1.0+7.0*exp(clamp(0.3*c.y-dot(c,c),-80.0,80.0)))
        /(0.03+abs(length(p)-0.7))*0.2);
    return color;
}
void main() {
    vec3 rd,base;vec2 span;
    if(!bhBegin(rd,base,span) || bhCenterRadius.z>=-0.001 || rd.z>=-1e-7) {fragColor=vec4(0);return;}
    // A perspective-sized, camera-facing field at the WORLD anchor. Applying the source
    // to a tilted 3-D disk destroys its silhouette and stretches/crops its flowing arms.
    float planeDistance=bhCenterRadius.z/rd.z;
    if(planeDistance<=0.0 || planeDistance>=bhDepthDistance(texCoord)) {fragColor=vec4(0);return;}
    float radius=max(bhEffect.x*bhR4Options.y,0.001);
    vec2 p=(rd*planeDistance-bhCenterRadius.xyz).xy*(0.7/radius);
    vec3 emission=bhR4Field(p,bhTiming.x*bhR4Options.x).rgb
        *bhR4Options.z*(bhQuality.z/0.85);
    // Preserve the original RGB exactly over black. Alpha only supplies its dark central
    // negative space and transparent outer glow for Minecraft; it is not a new core mesh.
    float glow=max(emission.r,max(emission.g,emission.b));
    float coverage=max(1.0-smoothstep(0.66,0.74,length(p)),clamp(glow,0.0,1.0));
    bhFinish(emission/max(coverage,1e-7),rd,coverage,1.0);
}
