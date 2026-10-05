// Adapted from baopinsui, "GR BH with volume accretion disk", Shadertoy 4XcfR2.
// Original supplied by the project owner in docs/black-hole/references/R1-original.txt.
// Attribution / permission status: THIRD_PARTY_NOTICES.md. Not a newly licensed work.
// The original scalar Perlin, multiplicative cloud octaves, thickness, inner dust,
// piecewise radial profile, colour and front-to-back transport are kept here.
// Adaptations: local Rs -> original light-year units; explicit game time/camera;
// finite guards only for undefined inputs; independent, default-neutral controls.
uniform vec4 bhTiming; // game seconds, age ticks, envelope, local gameplay rate
uniform vec4 bhDiskSettings; // time multiplier, brightness (trace), temperature, Doppler power
uniform vec4 bhSourcePhysics; // c/Rs, diskA, peak T^4, Rs in light years (CPU double)
uniform vec4 bhSourceDisk; // inner Rs, outer Rs, half-thickness Rs, noise contrast
uniform vec4 bhSourceOptions; // source time rate, maximum blue shift, reserved, reserved
const float kPi              = 3.141592653589;
const float kGravityConstant = 6.673e-11;
const float kSpeedOfLight    = 299792458.0;
const float kSigma           = 5.670373e-8;
const float kLightYear       = 9460730472580800.0;
const float kSolarMass       = 1.9884e30;

float RandomStep(vec2 Input, float Seed)
{
    return fract(sin(dot(Input + fract(11.4514 * sin(Seed)), vec2(12.9898, 78.233))) * 43758.5453);
}

float CubicInterpolate(float x)
{
    return 3.0 * x * x - 2.0 * x * x * x;
}

float PerlinNoise(vec3 Position)
{
    vec3 PosInt   = floor(Position);
    vec3 PosFloat = fract(Position);

    float v000 = 2.0 * fract(sin(dot(vec3(PosInt.x,       PosInt.y,       PosInt.z),       vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v100 = 2.0 * fract(sin(dot(vec3(PosInt.x + 1.0, PosInt.y,       PosInt.z),       vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v010 = 2.0 * fract(sin(dot(vec3(PosInt.x,       PosInt.y + 1.0, PosInt.z),       vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v110 = 2.0 * fract(sin(dot(vec3(PosInt.x + 1.0, PosInt.y + 1.0, PosInt.z),       vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v001 = 2.0 * fract(sin(dot(vec3(PosInt.x,       PosInt.y,       PosInt.z + 1.0), vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v101 = 2.0 * fract(sin(dot(vec3(PosInt.x + 1.0, PosInt.y,       PosInt.z + 1.0), vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v011 = 2.0 * fract(sin(dot(vec3(PosInt.x,       PosInt.y + 1.0, PosInt.z + 1.0), vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;
    float v111 = 2.0 * fract(sin(dot(vec3(PosInt.x + 1.0, PosInt.y + 1.0, PosInt.z + 1.0), vec3(12.9898, 78.233, 213.765))) * 43758.5453) - 1.0;

    float v00 = v001 * CubicInterpolate(PosFloat.z) + v000 * CubicInterpolate(1.0 - PosFloat.z);
    float v10 = v101 * CubicInterpolate(PosFloat.z) + v100 * CubicInterpolate(1.0 - PosFloat.z);
    float v01 = v011 * CubicInterpolate(PosFloat.z) + v010 * CubicInterpolate(1.0 - PosFloat.z);
    float v11 = v111 * CubicInterpolate(PosFloat.z) + v110 * CubicInterpolate(1.0 - PosFloat.z);
    float v0  = v01  * CubicInterpolate(PosFloat.y) + v00  * CubicInterpolate(1.0 - PosFloat.y);
    float v1  = v11  * CubicInterpolate(PosFloat.y) + v10  * CubicInterpolate(1.0 - PosFloat.y);

    return v1 * CubicInterpolate(PosFloat.x) + v0 * CubicInterpolate(1.0 - PosFloat.x);
}

float SoftSaturate(float x)
{
    return 1.0 - 1.0 / (max(x, 0.0) + 1.0);
}

float GenerateAccretionDiskNoise(vec3 Position, int NoiseStartLevel, int NoiseEndLevel, float ContrastLevel)
{
    float NoiseAccumulator = 10.0;
    float NoiseFrequency   = 1.0;
    
    for (int Level = NoiseStartLevel; Level < NoiseEndLevel; ++Level)
    {
        NoiseFrequency = pow(3.0, float(Level));
        vec3 ScaledPosition = vec3(NoiseFrequency * Position.x, NoiseFrequency * Position.y, NoiseFrequency * Position.z);

        NoiseAccumulator *= (1.0 + 0.1 * PerlinNoise(ScaledPosition));
    }
    
    return log(1.0 + pow(0.1 * NoiseAccumulator, ContrastLevel));
}

float Vec2ToTheta(vec2 v1, vec2 v2)
{
    if (dot(v1, v2) > 0.0)
    {
        return asin(0.999999 * (v1.x * v2.y - v1.y * v2.x) / length(v1) / length(v2));
    }
    else if (dot(v1, v2) < 0.0 && (-v1.x * v2.y + v1.y * v2.x) < 0.0)
    {
        return kPi - asin(0.999999 * (v1.x * v2.y - v1.y * v2.x) / length(v1) / length(v2));
    }
    else if (dot(v1, v2) < 0.0 && (-v1.x * v2.y + v1.y * v2.x) > 0.0)
    {
        return -kPi - asin(0.999999 * (v1.x * v2.y - v1.y * v2.x) / length(v1) / length(v2));
    }
    // The source omitted a return for exactly perpendicular/zero vectors.
    return atan(v1.x * v2.y - v1.y * v2.x, dot(v1, v2) + 1e-30);
}

vec3 KelvinToRgb(float Kelvin)
{
    if (Kelvin < 400.01)
    {
        return vec3(0.0);
    }

    float Teff     = (Kelvin - 6500.0) / (6500.0 * Kelvin * 2.2);
    vec3  RgbColor = vec3(0.0);
    
    RgbColor.r = exp(2.05539304e4 * Teff);
    RgbColor.g = exp(2.63463675e4 * Teff);
    RgbColor.b = exp(3.30145739e4 * Teff);

    float BrightnessScale = 1.0 / max(max(RgbColor.r, RgbColor.g), RgbColor.b);
    
    if (Kelvin < 1000.0)
    {
        BrightnessScale *= (Kelvin - 400.0) / 600.0;
    }
    
    RgbColor *= BrightnessScale;
    return RgbColor;
}

float GetKeplerianAngularVelocity(float Radius, float Rs)
{
    return sqrt(kSpeedOfLight / kLightYear * kSpeedOfLight * Rs / kLightYear / ((2.0 * Radius - 3.0 * Rs) * Radius * Radius));
}

float Shape(float x, float Alpha, float Beta)
{
    float k = pow(Alpha + Beta, Alpha + Beta) / (pow(Alpha, Alpha) * pow(Beta, Beta));
    return k * pow(x, Alpha) * pow(1.0 - x, Beta);
}

vec4 sourceDiskStep(vec4 BaseColor, float stepRs, vec3 rayPosRs, vec3 lastPosRs,
                    vec3 DirOnDisk, float cameraRadiusRs)
{
    float Rs = bhSourcePhysics.w;
    float InterRadius = bhSourceDisk.x * Rs;
    float OuterRadius = bhSourceDisk.y * Rs;
    float Thin = bhSourceDisk.z * Rs;
    float DiskTemperatureArgument = bhSourcePhysics.y;
    float QuadraticedPeakTemperature = bhSourcePhysics.z;
    float ShiftMax = bhSourceOptions.y;
    float simulationTime = bhTiming.x * bhSourceOptions.x * bhDiskSettings.x;
    float StepLength = stepRs * Rs;
    vec3 CameraPos = vec3(0.0, 0.0, cameraRadiusRs * Rs);
    vec3 PosOnDisk = rayPosRs * Rs;
    vec3 LastPosOnDisk = lastPosRs * Rs;
    float PosR = length(PosOnDisk.zx);
    float PosY = PosOnDisk.y;
    float LPosY = LastPosOnDisk.y;
    if( LPosY*PosY<0.0)
    {
    
    vec3 CPoint=(-PosOnDisk*LPosY+LastPosOnDisk*PosY)/(PosY-LPosY);
    PosOnDisk=CPoint+min(Thin,length(CPoint-LastPosOnDisk))*DirOnDisk*(-1.0+2.0*RandomStep(10000000000.0*PosOnDisk.zx, fract(bhTiming.x + 0.5)));
    
    StepLength=length(PosOnDisk-LastPosOnDisk);
    PosR = length(PosOnDisk.zx);
    PosY = PosOnDisk.y;
    }
    
    vec4 Color = vec4(0.0);
    if (abs(PosY) < Thin && PosR < OuterRadius && PosR > InterRadius)
    {
        
        float EffectiveRadius = 1.0 - ((PosR - InterRadius) / (OuterRadius - InterRadius) * 0.5);
        if ((OuterRadius - InterRadius) > 9.0 * Rs)
        {
            if (PosR < 5.0 * Rs + InterRadius)
            {
                EffectiveRadius = 1.0 - ((PosR - InterRadius) / (9.0 * Rs) * 0.5);
            }
            else
            {
                EffectiveRadius = 1.0 - (0.5 / 0.9 * 0.5 + ((PosR - InterRadius) / (OuterRadius - InterRadius) -
                                  5.0 * Rs / (OuterRadius - InterRadius)) / (1.0 - 5.0 * Rs / (OuterRadius - InterRadius)) * 0.5);
            }
        }

        if ((abs(PosY) < Thin * Shape(EffectiveRadius, 4.0, 0.9)) || (PosY < Thin * (1.0 - 5.0 * pow(2.0 * (1.0 - EffectiveRadius), 2.0))))
        {
            float AngularVelocity  = GetKeplerianAngularVelocity(PosR, Rs);
            float HalfPiTimeInside = kPi / GetKeplerianAngularVelocity(3.0 * Rs, Rs);

            float SpiralTheta=12.0*2.0/sqrt(3.0)*(atan(sqrt(max(0.6666666*(PosR/Rs)-1.0, 0.0))));
            float InnerTheta= kPi / HalfPiTimeInside *simulationTime ;
            float PosThetaForInnerCloud = Vec2ToTheta(PosOnDisk.zx, vec2(cos(0.666666*InnerTheta),sin(0.666666*InnerTheta)));
            float PosTheta            = Vec2ToTheta(PosOnDisk.zx, vec2(cos(-SpiralTheta), sin(-SpiralTheta)));

            // 计算盘温度
            float DiskTemperature = pow(DiskTemperatureArgument * pow(max(Rs/PosR,0.10),3.0) * max(1.0 - sqrt(InterRadius / PosR), 0.000001), 0.25);
            // 计算云相对速度
            vec3  CloudVelocity    = kLightYear / kSpeedOfLight * AngularVelocity * cross(vec3(0., 1., 0.), PosOnDisk);
            float RelativeVelocity = clamp(dot(-DirOnDisk, CloudVelocity), -0.999, 0.999);
            // 计算多普勒因子
            float Dopler = pow(sqrt((1.0 + RelativeVelocity) / (1.0 - RelativeVelocity)), bhDiskSettings.w);
            // 总红移量，含多普勒因子和引力红移和
            float RedShift = Dopler * sqrt(max(1.0 - Rs / PosR, 0.000001)) / sqrt(max(1.0 - Rs / length(CameraPos), 0.000001));

            float Density           = 0.0;
            float Thick             = 0.0;
            float VerticalMixFactor = 0.0;
            float DustColor         = 0.0;
            
            float RotPosR=PosR/Rs+0.3*sqrt(3.0)*kSpeedOfLight/kLightYear /3.0/sqrt(3.0)/Rs*simulationTime;
            
            vec4  Color0            = vec4(0.0);
            
            Density = Shape(EffectiveRadius, 4.0, 0.9);
            if (abs(PosY) < Thin * Density)
            {
                Thick = Thin * Density * (0.4 + 0.6 * SoftSaturate(GenerateAccretionDiskNoise(vec3(1.5 * PosTheta,RotPosR, 1.0), 1, 3, bhSourceDisk.w))); // 盘厚
                VerticalMixFactor = max(0.0, (1.0 - abs(PosY) / Thick));
                Density    *= 0.7 * VerticalMixFactor * Density;
                Color0      = vec4(GenerateAccretionDiskNoise(vec3(1.0 * RotPosR, 1.0 * PosY / min(Rs,Thin/0.1), 0.5 * PosTheta), 3, 6, bhSourceDisk.w)); // 云本体
                if(PosTheta+kPi<0.1*kPi)
                {
                    Color0*=(PosTheta+kPi)/(0.1*kPi);
                    Color0+=(1.0-((PosTheta+kPi)/(0.1*kPi)))*vec4(GenerateAccretionDiskNoise(vec3(1.0 * RotPosR, 1.0 * PosY / min(Rs,Thin/0.1), 0.5 * (2.0*kPi+PosTheta)), 3, 6, bhSourceDisk.w));
                }
                Color0.xyz *= Density * 1.4 * (0.2 + 0.8 * VerticalMixFactor + (0.8 - 0.8 * VerticalMixFactor) *
                              GenerateAccretionDiskNoise(vec3(RotPosR, 1.5 * PosTheta, PosY / min(Rs,Thin/0.1)), 1, 3, bhSourceDisk.w));
                Color0.a   *= (Density); // * (1.0 + VerticalMixFactor);
            }
            if (abs(PosY) < Thin* (1.0 - 5.0 * pow(2.0 * (1.0 - EffectiveRadius), 2.0)))
            {
                DustColor = max(1.0 - pow(PosY / (Thin * max(1.0 - 5.0 * pow(2.0 * (1.0 - EffectiveRadius), 2.0), 0.0001)), 2.0), 0.0) * GenerateAccretionDiskNoise(vec3(1.5 * fract((1.5 *  PosThetaForInnerCloud + kPi / HalfPiTimeInside *simulationTime) / 2.0 / kPi) * 2.0 * kPi, PosR / Rs, PosY /  min(Rs,Thin/0.1)), 0, 6, bhSourceDisk.w);
                Color0 += 0.02 * vec4(vec3(DustColor), 0.2 * DustColor) * sqrt(1.0001 - DirOnDisk.y * DirOnDisk.y) * min(1.0, Dopler * Dopler);
            }
           
            Color =  Color0;
            Color *= 1.0 + 20.0 * exp(-10.0 * (PosR - InterRadius) / (OuterRadius - InterRadius)); // 内侧增加密度

            float BrightWithoutRedshift = 4.5 * DiskTemperature * DiskTemperature * DiskTemperature * DiskTemperature / QuadraticedPeakTemperature;  // 原亮度
            if (DiskTemperature > 1000.0)
            {
                DiskTemperature = max(1000.0, DiskTemperature * RedShift * Dopler * Dopler);
            }

            DiskTemperature = min(100000.0, DiskTemperature);
            // Default 1 is exactly the owner source. Non-default values are a
            // local presentation control applied after the source's redshift.
            DiskTemperature = clamp(DiskTemperature * bhDiskSettings.z, 400.0, 100000.0);

            Color.xyz *= BrightWithoutRedshift * min(1.0, 1.8 * (OuterRadius - PosR) / (OuterRadius - InterRadius)) *
                         KelvinToRgb(DiskTemperature / exp((PosR - InterRadius) / (0.6 * (OuterRadius - InterRadius))));
            Color.xyz *= min(ShiftMax, RedShift) * min(ShiftMax, Dopler);

            RedShift=min(RedShift,ShiftMax);
            Color.xyz *= pow((1.0 - (1.0 - min(1., RedShift)) * (PosR - InterRadius) / (OuterRadius - InterRadius)), 9.0);
            Color.xyz *= min(1.0, 1.0 + 0.5 * ((PosR - InterRadius) / InterRadius + InterRadius / (PosR - InterRadius)) - max(1.0, RedShift));

            Color *= StepLength / Rs;
        }
    }

    return BaseColor + Color * (1.0 - BaseColor.a);
}


// Inverse encoding from the end of Buffer A. Clamp only log's undefined domain
// and guard the source's zero denominator; black must stay exactly black.
vec3 sourceInverseHdr(vec3 color) {
    color = max(color, vec3(0.0));
    vec3 factors = 3.0 * color / max(color.g + color.g + color.b, 1e-8);
    vec3 encoded = -4.0 * log(max(vec3(1e-7), 1.0 - pow(min(color, vec3(1.0)), vec3(2.2))));
    return min(encoded, 12.0 * factors);
}
