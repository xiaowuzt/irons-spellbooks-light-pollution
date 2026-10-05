#version 150
// Adapted from sonicether, "Gargantua With HDR Bloom", Shadertoy lstSRS (2016-04-07).
// Source supplied by the project owner. See THIRD_PARTY_NOTICES.md before distribution.
// World camera/depth, stable sampling, HDR pyramid and compositing are mod-specific.
uniform sampler2D DepthSampler;
uniform sampler2D NoiseSampler;
uniform mat4 InverseProjectionMat;
uniform vec4 HoleCentre;
uniform vec4 DiskX;
uniform vec4 DiskY;
uniform vec4 DiskZ;
uniform vec4 State; // time, envelope, step count, integration sphere
uniform vec4 DiskSettings; // rotation multiplier, emission brightness, reserved, reserved
in vec2 texCoord;
out vec4 fragColor;
float MarchStep;
const vec3 MainColor = vec3(1.0);

// Periodic cylindrical adaptation of iq's correlated 3-D value noise.
// Keep angle independent of radius: this stretches outer gas tangentially instead
// of shrinking it into small Cartesian speckles. Wrap lattice rows, not just the
// angle, so interpolation is continuous across the -pi/+pi boundary at every scale.
const float GasAngularSpan = 6.28318530718 * 1.5 * 0.95;
float gasNoise(vec3 coordinates, vec3 frequency)
{
    float period = max(1.0, floor(GasAngularSpan * frequency.y + 0.5));
    vec3 x = coordinates * frequency;
    x.y = coordinates.y * (period / GasAngularSpan);
    vec3 p = floor(x);
    vec3 f = fract(x);
    f = f*f*(3.0-2.0*f);
    vec2 uv = vec2(p.x + f.x, mod(p.y, period)) + vec2(37.0, 17.0) * p.z;
    vec2 lower = textureLod(NoiseSampler, (uv + 0.5) / 256.0, 0.0).yx;
    uv.y += mod(p.y + 1.0, period) - mod(p.y, period);
    vec2 upper = textureLod(NoiseSampler, (uv + 0.5) / 256.0, 0.0).yx;
    vec2 rg = mix(lower, upper, f.y);
    return -1.0 + 2.0 * mix(rg.x, rg.y, f.z);
}

float saturate(float x)
{
    return clamp(x, 0.0, 1.0);
}

vec3 saturate(vec3 x)
{
    return clamp(x, vec3(0.0), vec3(1.0));
}

float rand(vec2 coord)
{
    return saturate(fract(sin(dot(coord, vec2(12.9898, 78.223))) * 43758.5453));
}

float pcurve( float x, float a, float b )
{
    float k = pow(a+b,a+b) / (pow(a,a)*pow(b,b));
    return k * pow( x, a ) * pow( 1.0-x, b );
}

const float pi = 3.14159265;

float atan2(float y, float x)
{
    if (x > 0.0)
    {
        return atan(y / x);
    }
    else if (x == 0.0)
    {
        if (y > 0.0)
        {
            return pi / 2.0;
        }
        else if (y < 0.0)
        {
            return -(pi / 2.0);
        }
        else
        {
            return 0.0;
        }
    }
    else //(x < 0.0)
    {
        if (y >= 0.0)
        {
            return atan(y / x) + pi;
        }
        else
        {
            return atan(y / x) - pi;
        }
    }
}

float sdTorus(vec3 p, vec2 t)
{
    vec2 q = vec2(length(p.xz) - t.x, p.y);
    return length(q)-t.y;
}

float sdSphere(vec3 p, float r)
{
  return length(p)-r;
}

void Haze(inout vec3 color, vec3 pos, float alpha)
{
    vec2 t = vec2(1.0, 0.01);

    float torusDist = length(sdTorus(pos + vec3(0.0, -0.05, 0.0), t));

    float bloomDisc = 1.0 / (pow(torusDist, 2.0) + 0.001);
    vec3 col = MainColor;
    bloomDisc *= length(pos) < 0.5 ? 0.0 : 1.0;

    bloomDisc *= 1.0 - smoothstep(State.w - 1.0, State.w, length(pos));
    color += col * bloomDisc * (2.9 / (15.0 / MarchStep)) * (1.0 - alpha * 1.0);
}

void GasDisc(inout vec3 color, inout float alpha, vec3 pos)
{
    float discRadius = 3.2;
    float discWidth = 5.3;
    float discInner = discRadius - discWidth * 0.5;
    float discOuter = discRadius + discWidth * 0.5;
    
    vec3 origin = vec3(0.0, 0.0, 0.0);
    vec3 discNormal = normalize(vec3(0.0, 1.0, 0.0));
    float discThickness = 0.1;

    float distFromCenter = distance(pos, origin);
    float distFromDisc = dot(discNormal, pos - origin);
    
    float radialGradient = 1.0 - saturate((distFromCenter - discInner) / discWidth * 0.5);

    float coverage = pcurve(radialGradient, 4.0, 0.9);

    discThickness = max(0.035, discThickness * radialGradient);
    coverage *= saturate(1.0 - abs(distFromDisc) / discThickness);

    vec3 dustColorLit = MainColor;
    vec3 dustColorDark = vec3(0.0, 0.0, 0.0);

    float dustGlow = 1.0 / (pow(1.0 - radialGradient, 2.0) * 290.0 + 0.002);
    vec3 dustColor = dustColorLit * dustGlow * 8.2;

    coverage = saturate(coverage * 0.7);


    float fade = pow((abs(distFromCenter - discInner) + 0.4), 4.0) * 0.04;
    float bloomFactor = 1.0 / (pow(distFromDisc, 2.0) * 40.0 + fade + 0.00002);
    vec3 b = dustColorLit * pow(bloomFactor, 1.5);
    
    b *= mix(vec3(1.7, 1.1, 1.0), vec3(0.5, 0.6, 1.0), vec3(pow(radialGradient, 2.0)));
    b *= mix(vec3(1.7, 0.5, 0.1), vec3(1.0), vec3(pow(radialGradient, 0.5)));

    dustColor = mix(dustColor, b * 150.0, saturate(1.0 - coverage * 1.0));
    coverage = saturate(coverage + bloomFactor * bloomFactor * 0.1);
    
    if (coverage < 0.01)
    {
        return;   
    }
    
    
    // Restore the original cylindrical gas field. A Cartesian field scaled by radius
    // made the outer disk's angular features too small; explicit spiral masks further
    // cut the soft cloud coverage into bright, rigid filaments.
    vec3 radialCoords;
    radialCoords.x = distFromCenter * 1.5 + 0.55;
    radialCoords.y = atan2(-pos.x, -pos.z) * 1.5;
    radialCoords.z = distFromDisc * 1.5;
    radialCoords *= 0.95;

    // Preserve the configurable acceleration, but let different noise scales shear
    // past each other again instead of rotating a single rigid material pattern.
    float flowTime = State.x * DiskSettings.x;
    float speed = 0.06;
    float noise1 = 1.0;
    vec3 rc = radialCoords;
    rc.y += flowTime * speed;
    noise1 *= gasNoise(rc, vec3(3.0)) * 0.5 + 0.5;     rc.y -= flowTime * speed;
    noise1 *= gasNoise(rc, vec3(6.0)) * 0.5 + 0.5;     rc.y += flowTime * speed;
    noise1 *= gasNoise(rc, vec3(12.0)) * 0.5 + 0.5;    rc.y -= flowTime * speed;
    noise1 *= gasNoise(rc, vec3(24.0)) * 0.5 + 0.5;

    float noise2 = 2.0;
    rc = radialCoords + 30.0;
    noise2 *= gasNoise(rc, vec3(3.0)) * 0.5 + 0.5;     rc.y += flowTime * speed;
    noise2 *= gasNoise(rc, vec3(6.0)) * 0.5 + 0.5;     rc.y -= flowTime * speed;
    noise2 *= gasNoise(rc, vec3(12.0)) * 0.5 + 0.5;    rc.y += flowTime * speed;
    noise2 *= gasNoise(rc, vec3(24.0)) * 0.5 + 0.5;    rc.y -= flowTime * speed;
    noise2 *= gasNoise(rc, vec3(48.0)) * 0.5 + 0.5;    rc.y += flowTime * speed;
    noise2 *= gasNoise(rc, vec3(92.0)) * 0.5 + 0.5;

    dustColor *= noise1 * 0.998 + 0.002;
    coverage *= noise2;
    radialCoords.y += flowTime * speed * 0.5;
    dustColor *= pow(vec3(1.0, 0.90, 0.76)
            * (0.55 + 0.45 * gasNoise(vec3(radialCoords.xy, 0.5), vec3(6.0, 9.0, 6.0))), vec3(2.0)) * 4.0;

    coverage = saturate(coverage * 1200.0 / (15.0 / MarchStep));
    dustColor = max(vec3(0.0), dustColor);

    coverage *= pcurve(radialGradient, 4.0, 0.9);
    coverage *= 1.0 - smoothstep(State.w - 1.0, State.w, distFromCenter);

    color = (1.0 - alpha) * dustColor * coverage + color;

    alpha = (1.0 - alpha) * coverage + alpha;
}




vec3 viewPosition(vec2 uv, float depth) {
    vec4 p = InverseProjectionMat * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}
vec3 local(vec3 v) { return vec3(dot(v, DiskX.xyz), dot(v, DiskY.xyz), dot(v, DiskZ.xyz)); }
void main() {
    fragColor = vec4(0.0);
    float unit = HoleCentre.w;
    if (unit < 0.005 || State.y <= 0.001) return;
    vec3 viewRay = normalize(viewPosition(texCoord, 1.0));
    vec3 origin = local(-HoleCentre.xyz) / unit;
    vec3 direction = local(viewRay);
    float b = dot(origin, direction), c = dot(origin, origin) - State.w * State.w;
    float discriminant = b * b - c;
    if (discriminant <= 0.0) return;
    float enter = max(0.0, -b - sqrt(discriminant));
    float exit = -b + sqrt(discriminant);
    float depth = texture(DepthSampler, texCoord).r;
    float sceneDistance = depth < 0.999999 ? length(viewPosition(texCoord, depth)) / unit : 1e6;
    exit = min(exit, sceneDistance);
    if (exit <= enter) return;
    // Stable screen-space dither: no stale temporal feedback when moving the player camera.
    int steps = int(clamp(State.z, 80.0, 240.0));
    MarchStep = min(0.12, (exit - enter) / float(steps));
    float jitter = rand(floor(gl_FragCoord.xy)) * 0.8 + 0.1;
    vec3 pos = origin + direction * (enter + jitter * MarchStep);
    vec3 color = vec3(0.0);
    float alpha = 0.0, distanceTravelled = enter;
    for (int i = 0; i < 240; i++) {
        if (i >= steps || distanceTravelled >= exit) break;
        float r = length(pos);
        if (r < 0.48) { alpha = 1.0; break; }
        // Same inverse-square bent-ray field as WarpSpace, rescaled by actual step length.
        direction = normalize(direction - pos / max(r, 0.001) * (MarchStep / 3.0) / max(r * r, 0.002));
        pos += direction * MarchStep;
        distanceTravelled += MarchStep;
        GasDisc(color, alpha, pos);
        Haze(color, pos, alpha);
        if (alpha > 0.999) break;
    }
    fragColor = vec4(max(color * 0.0001, vec3(0.0)) * State.y * DiskSettings.y, clamp(alpha * State.y, 0.0, 1.0));
}
