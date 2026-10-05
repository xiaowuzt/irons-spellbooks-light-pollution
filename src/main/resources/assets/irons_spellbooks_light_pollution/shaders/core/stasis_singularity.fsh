#version 150
#moj_import <irons_spellbooks_light_pollution:bh_common.glsl>
// From R5 / shadertoy代码.txt A (WltSDM): p+=v*dt, then Newtonian GM/r^2 acceleration.
// MC replaces the moving demo centre/background with authoritative world centre/scene buffers.
// Rs maps to original radius=.3. This is NOT a Schwarzschild solution.
void main() {
    vec3 rd,base;vec2 support;
    if (!bhBegin(rd,base,support)) {fragColor=vec4(0);return;}
    vec2 span=bhSphere(rd,bhCenterRadius.w*4.0);
    if (span.y<=span.x || bhDepthDistance(texCoord)<span.x) {fragColor=vec4(0);return;}
    float unit=.3/max(bhCenterRadius.w,.005);
    vec3 p=(rd*span.x-bhCenterRadius.xyz)*unit,v=rd;
    int count=int(clamp(bhQuality.x,80.0,400.0));
    float dt=4.0/float(count),steps=0.0;bool captured=false,escaped=false;
    for (int i=0;i<400;i++) {
        if (i>=count) break;
        vec3 before=p;p+=v*dt;
        vec3 segment=p-before;
        vec3 closest=before+segment*clamp(-dot(before,segment)/max(dot(segment,segment),1e-8),0.0,1.0);
        if (dot(closest,closest)<=.09) {captured=true;steps=float(i+1);break;}
        float r2=max(dot(p,p),.0004);
        v+=.8/r2*bhSafeNormal(-p)*dt;
        steps=float(i+1);
        if (r2>1.44 && dot(p,v)>0.0) {escaped=true;break;}
    }
    // Budget exhaustion is not classified as a horizon hit; it degrades to the original scene.
    vec3 color=captured?vec3(.001,.001,.003):escaped?bhSceneInDirection(bhSafeNormal(v),span.x,base):base;
    float impact=length(cross(rd,bhCenterRadius.xyz))/bhCenterRadius.w;
    color+=vec3(.05,.035,.12)*exp(-pow((impact-1.7)*4.0,2.0))*bhQuality.z;
    // The integration volume has an artificial boundary; fade its optical contribution there.
    bhFinish(color,rd,1.0-smoothstep(3.4,4.0,impact),steps/float(count));
}
