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
  // A field extends beyond the cover. Continuous energy moves the ribbons;
  // the short impulse widens a separate wave. Text never passes through this shader.
  if (composition) {
    let q = vec2f(in.uv.x * aspect, -in.uv.y);
    let ambientUV = clamp(vec2f(in.uv.x, -in.uv.y) * 0.22 + 0.5, vec2f(0), vec2f(1));
    let pigment = textureSample(image, imageSampler, ambientUV * vec2f(1, 0.5)).rgb;
    // Resting intensity is a visible still, not a clock-driven response to silence.
    let activity = clamp((frame.artwork.z - 0.15) / 0.4, 0.0, 1.0);
    let drift = sin(q.x * 1.8 + t * 0.11) * activity * 0.37;
    let ribbon = exp(-abs(q.y - drift + 0.08) * (8.0 - frame.artwork.z * 4.0));
    let contour = 0.5 + 0.5 * cos((q.y - drift) * 30.0 + q.x * frame.typography.w);
    let radius = length(q - vec2f(aspect * 0.28, -0.08));
    let impulseWave = exp(-abs(radius - 0.5 - frame.artwork.w * 0.55) * 28.0) * frame.artwork.w;
    let edges = smoothstep(0.15, 1.35, abs(q.x));
    color = vec3f(0.025, 0.04, 0.065) + pigment * (0.10 + ribbon * frame.artwork.z * 0.30);
    color += vec3f(0.08, 0.22, 0.27) * ribbon * (0.2 + contour * 0.5);
    color += vec3f(0.4, 0.67, 0.62) * impulseWave * 0.32;
    color += vec3f(0.2, 0.4, 0.5) * edges * contour * frame.typography.z * 0.18;
    // The chord determines the field's center and pigment. Melody has its own
    // vertical trace, independent of loudness, so held notes remain visible.
    let angle = frame.music.z * 6.2831853;
    let harmonicCenter = vec2f(cos(angle) * aspect * 0.38, sin(angle) * 0.32);
    let chordPigment = 0.5 + 0.5 * cos(angle + vec3f(0.1, 2.2, 4.3));
    // Three translucent petals retain a chord's interval spacing, including
    // suspended, diminished and augmented voicings. No clock invents chord changes.
    let intervals = array<f32, 3>(0.0, frame.voicing.x, frame.voicing.y);
    for (var i = 0u; i < 3u; i++) {
      let direction = angle + intervals[i] * 6.2831853;
      let axis = vec2f(cos(direction), sin(direction));
      let local = q - harmonicCenter;
      let along = dot(local, axis);
      let across = dot(local, vec2f(-axis.y, axis.x));
      let ellipse = sqrt(pow((along - 0.22) / 0.85, 2.0) + pow(across / 0.32, 2.0));
      let veil = exp(-ellipse * ellipse * 1.6);
      let edge = exp(-abs(ellipse - 1.0) * 24.0);
      let tint = mix(pigment, 0.5 + 0.5*cos(direction + vec3f(0.1,2.2,4.3)), 0.4);
      color += tint * (veil * 0.18 + edge * 0.10) * frame.music.w;
    }
    let extensionRing = exp(-abs(length(q-harmonicCenter) - 0.46 - frame.voicing.z*0.25)*30.0);
    color += chordPigment * extensionRing * frame.music.w * select(0.0,0.10,frame.voicing.z > 0.0);
    // Melody follows a continuous media-time path; pitch controls elevation and
    // curvature. Its light disappears in unvoiced gaps, without inventing notes.
    let melodicHeight = -frame.music.x * 0.55;
    let curve = sin(q.x * 1.8 + frame.voicing.w * 0.24) * (0.06 + abs(frame.music.x)*0.08);
    let distance = abs(q.y - melodicHeight - curve);
    let line = exp(-distance * 85.0) + exp(-distance * 15.0)*0.25;
    let traveler = sin(frame.voicing.w * 0.24) * aspect * 0.8;
    let focus = exp(-pow((q.x-traveler)/0.28,2.0));
    let envelope = exp(-q.x*q.x/max(aspect*aspect,0.1));
    let melodyTint = mix(vec3f(0.64,0.92,0.90),chordPigment,0.15*frame.music.w);
    color += melodyTint * line * envelope * (0.23+focus*0.40) * frame.music.y;
    return vec4f(color, 1);
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

