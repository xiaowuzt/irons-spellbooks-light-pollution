// Original Minecraft integration only; no author's ray integrator is shared here.
uniform sampler2D bhSceneSampler;
uniform sampler2D bhDepthSampler;
uniform mat4 bhProjectionMatrix;
uniform mat4 bhInverseProjectionMatrix;
uniform mat4 bhModelViewMatrix;
uniform vec4 bhCenterRadius; // eye-space centre (camera-relative), Schwarzschild/horizon radius, metres
uniform vec4 bhRadii;        // influence, visual support, geometric GM/c^2 = Rs/2, reserved
uniform vec4 bhTiming;       // seconds, age ticks, envelope, gameplay time scale
uniform vec4 bhScreenSize;   // full framebuffer width/height, reciprocal dimensions
uniform vec4 bhQuality;      // bounded steps, Doppler multiplier, exposure, debug mode
uniform vec4 bhDiskNormal;   // world-fixed disk normal transformed to eye space; seed in w
uniform vec4 bhEffect;       // per-effect data; w: 0 lens, 1 ring, 2 stasis, 3 path, 4 R1 fallback
in vec2 texCoord;
out vec4 fragColor;
const float bhPI = 3.14159265359;
vec3 bhSafeNormal(vec3 v) { return v * inversesqrt(max(dot(v,v), 1e-12)); }
vec3 bhRay(vec2 uv) {
    vec4 p = bhInverseProjectionMatrix * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    return bhSafeNormal(p.xyz / max(abs(p.w), 1e-7)); // OpenGL eye forward is -Z, Y is up
}
float bhDepthDistance(vec2 uv) {
    float z = texture(bhDepthSampler, uv).r;
    if (z >= 0.999999) return 1e8;
    vec4 p = bhInverseProjectionMatrix * vec4(uv * 2.0 - 1.0, z * 2.0 - 1.0, 1.0);
    return length(p.xyz) / max(abs(p.w), 1e-7);
}
vec2 bhSphere(vec3 rd, float radius) {
    float b = dot(rd, bhCenterRadius.xyz);
    float h = b*b - dot(bhCenterRadius.xyz,bhCenterRadius.xyz) + radius*radius;
    if (h < 0.0) return vec2(1e8,-1.0);
    h = sqrt(max(h,0.0)); return vec2(max(0.0,b-h),b+h);
}
float bhSupport(vec3 rd) {
    float impact = length(cross(rd,bhCenterRadius.xyz));
    return 1.0-smoothstep(bhRadii.y*.86,bhRadii.y,impact);
}
vec3 bhSceneInDirection(vec3 direction, float nearestEffect, vec3 original) {
    vec4 clip = bhProjectionMatrix * vec4(direction,0.0);
    if (clip.w <= 1e-5) return original; // no invented behind-camera or off-screen geometry
    vec2 uv = clip.xy / clip.w * .5 + .5;
    vec2 edge = min(uv,1.0-uv);
    float fade = smoothstep(0.0,.035,min(edge.x,edge.y));
    if (fade <= 0.0 || bhDepthDistance(clamp(uv,bhScreenSize.zw*.5,1.0-bhScreenSize.zw*.5)) < nearestEffect) return original;
    return mix(original,texture(bhSceneSampler,uv).rgb,fade);
}
vec3 bhCalibrationGrid(vec3 direction) {
    vec3 world = transpose(mat3(bhModelViewMatrix))*bhSafeNormal(direction);
    float u = atan(world.z,world.x), v = asin(clamp(world.y,-1.0,1.0));
    vec2 q = vec2(u,v)*8.0/bhPI;
    vec2 line = abs(fract(q+.5)-.5)/max(fwidth(q),vec2(.01));
    float grid = 1.0-smoothstep(.6,1.5,min(line.x,line.y));
    return mix(vec3(.018,.025,.05),vec3(.7,.82,1.0),grid);
}
vec3 bhDebugColor(vec3 color, vec3 rd, float steps) {
    int mode = int(bhQuality.w+.5);
    if (mode == 1) return bhSafeNormal(bhDiskNormal.xyz)*.5+.5;
    if (mode == 2) return vec3(clamp(steps,0.0,1.0),.15,1.0-clamp(steps,0.0,1.0));
    if (mode == 3) {
        vec2 span = bhSphere(rd,bhRadii.y);
        return vec3(bhDepthDistance(texCoord)<span.x?1.0:0.0,bhSupport(rd),min(span.x/128.0,1.0));
    }
    return color;
}
bool bhBegin(out vec3 rd,out vec3 scene,out vec2 span) {
    rd=bhRay(texCoord); scene=texture(bhSceneSampler,texCoord).rgb;
    span=bhSphere(rd,bhRadii.y);
    return span.y>span.x && bhDepthDistance(texCoord)>span.x && bhCenterRadius.w>.005;
}
void bhFinish(vec3 color,vec3 rd,float coverage,float steps) {
    color=bhDebugColor(color,rd,steps);
    float a=clamp(coverage*bhSupport(rd)*bhTiming.z,0.0,1.0);
    fragColor=vec4(clamp(color,vec3(0),vec3(32)),a);
}
