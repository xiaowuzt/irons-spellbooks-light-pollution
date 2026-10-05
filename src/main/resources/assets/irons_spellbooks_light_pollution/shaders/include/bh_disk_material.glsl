// Mod-authored gas shading and Euclidean volume helpers, not a GR ray integrator.
// Coordinates are fixed to the entity's WORLD disk, including when the camera rolls.
mat3 bhDiskBasis() {
    mat3 view = mat3(bhModelViewMatrix);
    vec3 n = bhSafeNormal(transpose(view) * bhDiskNormal.xyz);
    vec3 x = bhSafeNormal(cross(n, abs(n.z) < .9 ? vec3(0,0,1) : vec3(1,0,0)));
    return view * mat3(x, n, cross(x,n));
}
float bhGasHash(vec3 p) {
    p = fract(p * .1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}
float bhGasNoise(vec3 p) {
    vec3 i=floor(p),f=fract(p); f=f*f*(3.0-2.0*f);
    return mix(mix(mix(bhGasHash(i),bhGasHash(i+vec3(1,0,0)),f.x),
                   mix(bhGasHash(i+vec3(0,1,0)),bhGasHash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(bhGasHash(i+vec3(0,0,1)),bhGasHash(i+vec3(1,0,1)),f.x),
                   mix(bhGasHash(i+vec3(0,1,1)),bhGasHash(i+vec3(1)),f.x),f.y),f.z);
}
float bhGasCloud(vec3 p,float time) {
    float r=max(length(p.xz),.01), a=atan(p.z,p.x);
    // Differential rotation; periodic Cartesian embedding has no atan seam.
    a -= time * .7 * pow(3.0/max(r,1.0),1.5);
    vec3 q=vec3(cos(a),sin(a),r*.8);
    float broad=bhGasNoise(q*vec3(2.4,2.4,1.3)+vec3(0,0,p.y));
    float detail=bhGasNoise(q*vec3(6,6,3)+vec3(4,9,time*.035));
    return .22+.95*broad+.35*detail;
}
// Opaque core is independent of emission. A ray cannot see rear gas/path through it.
vec3 bhVisualHorizon(vec3 rd,vec3 base,float radius,out float limit) {
    limit=bhDepthDistance(texCoord);
    vec2 hit=bhSphere(rd,radius);
    if (hit.y>hit.x && hit.x<limit) { limit=hit.x; return vec3(.001); }
    return base;
}
vec2 bhDiskSlab(vec3 origin,vec3 direction,float outer,float thickness,float limit) {
    float b=dot(origin,direction),h=b*b-dot(origin,origin)+outer*outer;
    if(h<=0.0) return vec2(1,-1);
    vec2 span=vec2(max(0.0,-b-sqrt(h)),min(limit,-b+sqrt(h)));
    if(abs(direction.y)<1e-6) {
        if(abs(origin.y)>thickness) return vec2(1,-1);
    } else {
        vec2 slab=(vec2(-thickness,thickness)-origin.y)/direction.y;
        span=vec2(max(span.x,min(slab.x,slab.y)),min(span.y,max(slab.x,slab.y)));
    }
    return span;
}
