#version 150
#moj_import <irons_spellbooks_light_pollution:bh_common.glsl>
// Original depth-guided upsample: untouched scene remains full resolution outside coverage.
uniform sampler2D bhEffectSampler;
void main() {
    vec3 rd,base;vec2 span;
    if (!bhBegin(rd,base,span)) {fragColor=vec4(texture(bhSceneSampler,texCoord).rgb,1);return;}
    vec2 size=vec2(textureSize(bhEffectSampler,0)),pixel=texCoord*size-.5;
    vec2 origin=floor(pixel),f=fract(pixel);vec4 accum=vec4(0);float total=0.0;
    float depth=bhDepthDistance(texCoord);
    for (int y=0;y<2;y++) for (int x=0;x<2;x++) {
        vec2 uv=(origin+vec2(x,y)+.5)/size;
        float guide=bhDepthDistance(uv);
        float w=(x==0?1.0-f.x:f.x)*(y==0?1.0-f.y:f.y);
        if (abs(guide-depth)>max(.15,depth*.02)) w=0.0;
        // bhFinish writes straight alpha. Premultiply EACH tap before filtering;
        // filtering RGB first would attenuate thin gas twice and leak hidden RGB.
        vec4 tap=texture(bhEffectSampler,uv);
        float alpha=clamp(tap.a,0.0,1.0);
        accum+=vec4(tap.rgb*alpha,alpha)*w;total+=w;
    }
    vec4 effect=total>1e-5?accum/total:vec4(0);
    fragColor=vec4(base*(1.0-clamp(effect.a,0.0,1.0))+effect.rgb,1);
}
