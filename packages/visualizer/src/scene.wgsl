struct Frame {
  viewport: vec4f, // width, height, time, scene scale
  artwork: vec4f, // scale, offset, background intensity, impulse
  typography: vec4f, // offset, opacity, effect intensity, texture scale
  music: vec4f, // melodic height, voiced, harmonic phase, harmony active
  voicing: vec4f, // third, fifth, extension in octaves; actual media position
}
@group(0) @binding(0) var<uniform> frame: Frame;
@group(0) @binding(1) var image: texture_2d<f32>;
@group(0) @binding(2) var imageSampler: sampler;
override representative: bool = false;
override composition: bool = false;
struct Vertex { @builtin(position) position: vec4f, @location(0) uv: vec2f, }
@vertex fn vertex(@builtin(vertex_index) index: u32) -> Vertex {
  let p = array<vec2f, 3>(vec2f(-1, -1), vec2f(3, -1), vec2f(-1, 3));
  var out: Vertex;
  out.position = vec4f(p[index], 0, 1);
  out.uv = p[index];
  return out;
}
@fragment fn fragment(in: Vertex) -> @location(0) vec4f {
  let aspect = frame.viewport.x / frame.viewport.y;
  let p = vec2f(in.uv.x * aspect, -in.uv.y) / frame.viewport.w;
  let t = frame.viewport.z;
  let grain = 0.5 + 0.5 * sin((p.x + p.y) * 18 * frame.typography.w + t * 0.2);
  var color = vec3f(0.025, 0.035, 0.07) + vec3f(0.035, 0.09, 0.12) * frame.artwork.z * grain;
  let landscape = aspect > 1.0;
  let artCenter = select(vec2f(0, -0.35), vec2f(-aspect * 0.48, -0.3), landscape);
  let baseSize = select(min(0.36, aspect * 0.40), min(0.46, aspect * 0.34), landscape);
  let artSize = select(0.40, baseSize, representative) * frame.artwork.x + 0.012 * frame.artwork.w;
  let artPosition = select(vec2f(0, -0.13), artCenter, representative) + vec2f(frame.artwork.y, 0);
  let artUV = (p - artPosition) / (2 * artSize) + 0.5;
  // Three closed, interlaced curves. Pigment travels along the curves rather
  // than moving the artwork or drawing amplitude bars, beams or glowing waves.
  if (composition) {
    let q = vec2f(in.uv.x * aspect, -in.uv.y);
    let paper = vec3f(0.9765, 0.9608, 0.9216);
    let activity = clamp((frame.artwork.z - 0.15) / 0.4, 0.0, 1.0);
    let flowing = activity > 0.002 || frame.music.y > 0.0 || frame.music.w > 0.0;
    let flow = select(0.0, t * 0.35, flowing);
    let pitch = frame.music.x * frame.music.y;
    let harmony = frame.music.z * frame.music.w;
    let warmth = clamp(0.2 + harmony * 0.35 +
      (frame.music.x + 1.0) * frame.music.y * 0.15 + frame.artwork.w * 0.15, 0.0, 1.0);
    let ink = mix(vec3f(48, 43, 38), vec3f(116, 53, 36), warmth) / 255.0;
    // Pixel-sized antialiasing only: no Gaussian/exponential glow around a line.
    let pixel = 2.0 / frame.viewport.y;
    let width = pixel * 0.65;
    var distances: array<f32, 3>;
    var pigments: array<f32, 3>;
    let intervals = array<f32, 3>(0.0, frame.voicing.x, frame.voicing.y);
    for (var i = 0u; i < 3u; i++) {
      let angle = f32(i) * 2.0943951 - 1.5707963 + harmony * 0.22;
      let axis = vec2f(cos(angle), sin(angle));
      let local = q - vec2f(aspect * 0.10, 0.05);
      let v = vec2f(dot(local, axis), dot(local, vec2f(-axis.y, axis.x))) - vec2f(0.32, 0);
      let radii = vec2f(0.91 + harmony * 0.12,
        0.43 + pitch * 0.045 + intervals[i] * frame.music.w * 0.10 + frame.voicing.z * frame.music.w * 0.025);
      let normalized = v / radii;
      let radius = max(length(normalized), 0.0001);
      // First-order signed distance to an ellipse, accurate at the drawn edge.
      distances[i] = abs(radius - 1.0) / max(length(v / (radii * radii)) / radius, 0.0001);
      let along = atan2(normalized.y, normalized.x);
      let current = 0.5 + 0.5 * cos(along - flow - f32(i) * 2.0943951 + pitch * 0.6);
      pigments[i] = 0.25 + 0.30 * current + activity * 0.12 + frame.artwork.w * 0.25;
    }
    var coverage = 0.0;
    for (var i = 0u; i < 3u; i++) {
      let line = 1.0 - smoothstep(width - pixel * 0.5, width + pixel * 0.5, distances[i]);
      // Cyclic over/under ordering cuts a narrow gap only in the underpassing line.
      let under = smoothstep(pixel * 2.5, pixel * 3.5, distances[(i + 1u) % 3u]);
      coverage = max(coverage, line * under * pigments[i]);
    }
    return vec4f(mix(paper, ink, coverage), 1);
  }
  // One background surface, with a second atlas sample distributed beyond the artwork rectangle.
  if (representative) {
    let ambientUV = clamp(vec2f(in.uv.x, -in.uv.y) * 0.35 + 0.5 + vec2f(sin(t * 0.07) * 0.05, 0), vec2f(0), vec2f(1));
    let ambient = textureSample(image, imageSampler, ambientUV * vec2f(1, 0.5));
    color += ambient.rgb * (0.12 + frame.artwork.z * 0.22) * grain;
  }
  let art = textureSample(image, imageSampler, vec2f(clamp(artUV.x, 0, 1), clamp(artUV.y, 0, 1) * 0.5));
  let artMask = select(0.0, 1.0, all(artUV >= vec2f(0)) && all(artUV <= vec2f(1)));
  color = mix(color, art.rgb, artMask);
  // Match the display aspect to the text crop instead of compressing the square atlas half.
  let textOrigin = vec2f(0, 704);
  let textSize = vec2f(512, 128);
  let textWidth = select(min(1.25, aspect * 1.6), min(1.35, aspect * 0.75), landscape);
  let textDisplay = select(1.2, textWidth, representative) * textSize / textSize.x;
  let textCenter = select(vec2f(0, 0.2), vec2f(aspect * 0.43, -0.3), landscape);
  let textPosition = select(vec2f(0, 0.48), textCenter, representative) + vec2f(0, frame.typography.x);
  let textUV = (p - textPosition) / textDisplay + 0.5;
  let text = textureSample(image, imageSampler, (textOrigin + clamp(textUV, vec2f(0), vec2f(1)) * textSize) / vec2f(textureDimensions(image)));
  let textMask = select(0.0, text.a, all(textUV >= vec2f(0)) && all(textUV <= vec2f(1)));
  color = mix(color, text.rgb, textMask * frame.typography.y);
  let cell = fract((p + vec2f(t * 0.008, -t * 0.015)) * vec2f(9, 7)) - 0.5;
  let spark = 1 - smoothstep(0.008, 0.022, length(cell));
  color += vec3f(0.2, 0.65, 0.8) * spark * frame.typography.z * (1 - artMask);
  return vec4f(color, 1);
}

