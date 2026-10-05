// From R2 / Eric Bruneton, black_hole/functions.glsl. BSD-3-Clause.
// Copyright (c) 2020 Eric Bruneton. Full license: META-INF/licenses/black-hole-R2-BSD-3-Clause.txt.
// Only this file contains R2's tracing core; no R1/R4/R5/R6 core is included.
// MC changes: float/vec2 aliases, initialized out values, log/asin/UV/tan guards.
#define RAY_DEFLECTION_TEXTURE_WIDTH 512
#define RAY_DEFLECTION_TEXTURE_HEIGHT 512
#define RAY_INVERSE_RADIUS_TEXTURE_WIDTH 64
#define RAY_INVERSE_RADIUS_TEXTURE_HEIGHT 32
const float rad = 1.0;
const float pi = 3.141592653589793;
const float kMu = 4.0 / 27.0;

float GetRayDeflectionTextureUFromEsquare(const float e_square) {
  if (e_square < kMu) {
    return 0.5 - sqrt(-log(max(1.0 - e_square / kMu, 1e-7)) * (1.0 / 50.0));
  } else {
    return 0.5 + sqrt(-log(max(1.0 - kMu / e_square, 1e-7)) * (1.0 / 50.0));
  }
}

float GetUapsisFromEsquare(const float e_square) {
  float x = (2.0 / kMu) * e_square - 1.0;
  return 1.0 / 3.0 + (2.0 / 3.0) * sin(asin(clamp(x, -1.0, 1.0)) * (1.0 / 3.0));
}

float GetRayDeflectionTextureVFromEsquareAndU(const float e_square,
                                             const float u) {
  if (e_square > kMu) {
    float x = u < 2.0 / 3.0 ? -sqrt(2.0 / 3.0 - u) : sqrt(u - 2.0 / 3.0);
    return (sqrt(2.0 / 3.0) + x) / (sqrt(2.0 / 3.0) + sqrt(1.0 / 3.0));
  } else {
    return 1.0 - sqrt(max(1.0 - u / GetUapsisFromEsquare(e_square), 0.0));
  }
}

float GetTextureCoordFromUnitRange(const float x, const int texture_size) {
  return 0.5 / float(texture_size) + clamp(x, 0.0, 1.0) * (1.0 - 1.0 / float(texture_size));
}

vec2 LookupRayDeflection(sampler2D ray_deflection_texture,
                               const float e_square, const float u,
                               out vec2 deflection_apsis) {
  float tex_u = GetTextureCoordFromUnitRange(
      GetRayDeflectionTextureUFromEsquare(e_square),
      RAY_DEFLECTION_TEXTURE_WIDTH);
  float tex_v = GetTextureCoordFromUnitRange(
      GetRayDeflectionTextureVFromEsquareAndU(e_square, u),
      RAY_DEFLECTION_TEXTURE_HEIGHT);
  float tex_v_apsis =
      GetTextureCoordFromUnitRange(1.0, RAY_DEFLECTION_TEXTURE_HEIGHT);
  deflection_apsis =
      vec2(texture(ray_deflection_texture, vec2(tex_u, tex_v_apsis)));
  return vec2(texture(ray_deflection_texture, vec2(tex_u, tex_v)));
}

float GetPhiUbFromEsquare(const float e_square) {
  return (1.0 + e_square) / (1.0 / 3.0 + 2.0 * e_square * sqrt(e_square)) * rad;
}

float GetRayInverseRadiusTextureUFromEsquare(const float e_square) {
  return 1.0 / (1.0 + 6.0 * e_square);
}

vec2 LookupRayInverseRadius(sampler2D
                                                ray_inverse_radius_texture,
                                            const float e_square,
                                            const float phi) {
  float tex_u = GetTextureCoordFromUnitRange(
      GetRayInverseRadiusTextureUFromEsquare(e_square),
      RAY_INVERSE_RADIUS_TEXTURE_WIDTH);
  float tex_v = GetTextureCoordFromUnitRange(phi / GetPhiUbFromEsquare(e_square),
                                            RAY_INVERSE_RADIUS_TEXTURE_HEIGHT);
  return vec2(
      texture(ray_inverse_radius_texture, vec2(tex_u, tex_v)));
}

// Anti-aliased pulse function. See
// https://renderman.pixar.com/resources/RenderMan_20/basicAntialiasing.html.
float FilteredPulse(float edge0, float edge1, float x, float fw) {
  fw = max(fw, 1e-6);
  float x0 = x - fw * 0.5;
  float x1 = x0 + fw;
  return max(0.0, (min(x1, edge1) - max(x0, edge0)) / fw);
}

float TraceRay(sampler2D ray_deflection_texture,
               sampler2D ray_inverse_radius_texture,
               const float u, const float u_dot, const float e_square,
               const float delta, const float alpha, const float u_ic,
               const float u_oc, out float u0, out float phi0, out float t0,
               out float alpha0, out float u1, out float phi1, out float t1,
               out float alpha1) {
  // Compute the ray deflection.
  u0 = -1.0;
  u1 = -1.0;
  phi0 = 0.0; t0 = 0.0; alpha0 = 0.0; phi1 = 0.0; t1 = 0.0; alpha1 = 0.0;
  if (e_square < kMu && u > 2.0 / 3.0) {
    return -1.0 * rad;
  }
  vec2 deflection_apsis;
  vec2 deflection = LookupRayDeflection(ray_deflection_texture, e_square,
                                              u, deflection_apsis);
  float ray_deflection = deflection.x;
  if (u_dot > 0.0) {
    ray_deflection =
        e_square < kMu ? 2.0 * deflection_apsis.x - ray_deflection : -1.0 * rad;
  }
  // Compute the accretion disc intersections.
  float s = sign(u_dot);
  float phi = deflection.x + (s == 1.0 ? pi - delta : delta) + s * alpha;
  float phi_apsis = deflection_apsis.x + pi / 2.0;
  phi0 = mod(phi, pi);
  vec2 ui0 =
      LookupRayInverseRadius(ray_inverse_radius_texture, e_square, phi0);
  if (phi0 < phi_apsis) {
    float side = s * (ui0.x - u);
    if (side > 1e-3 || (side > -1e-3 && alpha < delta)) {
      u0 = ui0.x;
      phi0 = alpha + phi - phi0;
      t0 = s * (ui0.y - deflection.y);
    }
  }
  phi = 2.0 * phi_apsis - phi;
  phi1 = mod(phi, pi);
  vec2 ui1 =
      LookupRayInverseRadius(ray_inverse_radius_texture, e_square, phi1);
  if (e_square < kMu && s == 1.0 && phi1 < phi_apsis) {
    u1 = ui1.x;
    phi1 = alpha + phi - phi1;
    t1 = 2.0 * deflection_apsis.y - ui1.y - deflection.y;
  }
  // Compute the anti-aliasing opacity values.
  float fw0 = min(fwidth(ui0.x), fwidth(u0 == -1.0 ? u1 : u0));
  float fw1 = min(fwidth(ui1.x), fwidth(u1 == -1.0 ? u0 : u1));
  alpha0 = FilteredPulse(u_oc, u_ic, u0, fw0);
  alpha1 = FilteredPulse(u_oc, u_ic, u1, fw1);
  if (s == 1.0 && abs(e_square - kMu) < min(fwidth(e_square), kMu)) {
    if (alpha0 < 0.99) u0 = 2.0 / (1.0 / u_ic + 1.0 / u_oc);
    if (alpha1 < 0.99) u1 = 2.0 / (1.0 / u_ic + 1.0 / u_oc);
  }
  return ray_deflection;
}

float TraceRay(sampler2D ray_deflection_texture,
               sampler2D ray_inverse_radius_texture,
               const float p_r, const float delta, const float alpha,
               const float u_ic, const float u_oc, out float u0,
               out float phi0, out float t0, out float alpha0, out float u1,
               out float phi1, out float t1, out float alpha1) {
  float u = 1.0 / p_r;
  float tangent = tan(delta);
  float u_dot = -u / (abs(tangent) < 1e-6 ? (tangent < 0.0 ? -1e-6 : 1e-6) : tangent);
  float e_square = u_dot * u_dot + u * u * (1.0 - u);
  return TraceRay(ray_deflection_texture, ray_inverse_radius_texture, u,
                  u_dot, e_square, delta, alpha, u_ic, u_oc, u0, phi0, t0,
                  alpha0, u1, phi1, t1, alpha1);
}
