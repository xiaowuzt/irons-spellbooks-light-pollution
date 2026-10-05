// Finite-thickness, absorbing gas around R2's exact LUT mid-plane intersections.
// This material is mod-authored; it does not replace/approximate the R2 geodesic LUT.
uniform vec4 bhR2Gas; // half-thickness in Rs, density contrast, flow speed, brightness
// Clip the local straight segment against the camera and scene Z-depth planes.
// R2's t0/t1 are transport times, NOT Minecraft eye-space depth distances.
vec2 bhR2VisibleSpan(float halfLength,float centerDepth,float depthSlope,float sceneDepth) {
    vec2 span=vec2(-halfLength,halfLength);
    if(abs(depthSlope)<1e-5)
        return centerDepth>0.0 && centerDepth<sceneDepth?span:vec2(1,-1);
    vec2 planes=(vec2(0.0,sceneDepth)-centerDepth)/depthSlope;
    return vec2(max(span.x,min(planes.x,planes.y)),min(span.y,max(planes.x,planes.y)));
}
float bhR2MissingLutCoverage(float u0,float coverage0,float u1,float coverage1) {
    return 1.0-clamp(max(u0>0.0?coverage0:0.0,u1>0.0?coverage1:0.0),0.0,1.0);
}
vec4 bhR2Cloud(vec3 p,vec3 ray,float footprint) {
    float r=length(p.xz),halfHeight=bhR2Gas.x;
    if(r<=3.0 || r>=10.0 || abs(p.y)>=halfHeight) return vec4(0);
    float theta=atan(p.z,p.x)-bhTiming.x*bhR2Gas.z*.7*pow(3.0/r,1.5);
    // Periodic embedding + radius-dependent shear produces cloudy filaments, not spokes
    // and not a smooth, opaque painted annulus. Position is fixed to the WORLD disk.
    vec3 q=vec3(cos(theta),sin(theta),r);
    float broad=bhGasNoise(q*vec3(5,5,1.7)+vec3(0,0,p.y*3.0));
    float billow=bhGasNoise(q*vec3(12,12,4.5)+vec3(4,8,p.y*8.0));
    float fine=bhGasNoise(q*vec3(28,28,11)+vec3(p.y*16.0,3,9));
    // Fade unresolved octaves instead of sparkling grains on the far photon image.
    fine=mix(fine,.5,smoothstep(.025,.10,footprint));
    float thickness=halfHeight*(.45+.55*broad);
    float vertical=max(0.0,1.0-abs(p.y)/max(thickness,.003));
    float edge=smoothstep(3.0,3.3,r)*(1.0-smoothstep(8.5,10.0,r));
    float cloud=pow(max(0.0,(broad*.7+billow*.3-.18)*1.8),bhR2Gas.y);
    float density=cloud*(.3+1.2*billow)*(.55+.9*fine)*pow(vertical,1.35)*edge*3.2;
    vec3 velocity=vec3(-p.z,0,p.x)/max(r,.001)*sqrt(1.0/max(2.0*r-3.0,1.0));
    float relative=clamp(dot(-ray,velocity),-.8,.8);
    float doppler=sqrt((1.0+relative)/(1.0-relative));
    float heat=clamp(exp(-.28*(r-3.0))*doppler,.0,1.0);
    vec3 tint=mix(vec3(1.0,.20,.035),vec3(1.0,.92,.73),heat);
    tint=mix(tint,vec3(.68,.83,1.0),clamp((doppler-1.1)*.5,0.0,.55));
    vec3 emission=tint*(.55+2.4*exp(-.5*(r-3.0)))*(.6+.8*billow)
        *min(1.8,doppler*doppler)*bhR2Gas.w*(bhQuality.z/.85);
    return vec4(emission,density);
}
vec4 bhR2Integrate(vec3 center,vec3 tangent,float halfLength,float footprint) {
    vec3 light=vec3(0);float alpha=0.0;
    const int SAMPLES=12;
    float ds=2.0*halfLength/float(SAMPLES);
    for(int j=0;j<SAMPLES;j++) {
        vec3 p=center+tangent*(-halfLength+(float(j)+.5)*ds);
        vec4 gas=bhR2Cloud(p,tangent,footprint);
        float opacity=1.0-exp(-gas.a*ds);
        light+=(1.0-alpha)*opacity*(1.0-exp(-gas.rgb));
        alpha+=(1.0-alpha)*opacity;
    }
    return vec4(light,alpha);
}
