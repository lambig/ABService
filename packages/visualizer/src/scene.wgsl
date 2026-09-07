struct Frame {
  viewport: vec4f, // width, height, time, scene scale
  artwork: vec4f, // scale, offset, background intensity, impulse
  typography: vec4f, // offset, opacity, effect intensity, texture scale
}
@group(0) @binding(0) var<uniform> frame: Frame;
@group(0) @binding(1) var image: texture_2d<f32>;
@group(0) @binding(2) var imageSampler: sampler;
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
  let artSize = 0.40 * frame.artwork.x + 0.012 * frame.artwork.w;
  let artUV = (p - vec2f(frame.artwork.y, -0.13)) / (2 * artSize) + 0.5;
  let art = textureSample(image, imageSampler, vec2f(clamp(artUV.x, 0, 1), clamp(artUV.y, 0, 1) * 0.5));
  let artMask = select(0.0, 1.0, all(artUV >= vec2f(0)) && all(artUV <= vec2f(1)));
  color = mix(color, art.rgb, artMask);
  let textUV = (p - vec2f(0, 0.48 + frame.typography.x)) / vec2f(1.2, 0.30) + 0.5;
  let text = textureSample(image, imageSampler, vec2f(clamp(textUV.x, 0, 1), 0.5 + clamp(textUV.y, 0, 1) * 0.5));
  let textMask = select(0.0, text.a, all(textUV >= vec2f(0)) && all(textUV <= vec2f(1)));
  color = mix(color, text.rgb, textMask * frame.typography.y);
  let cell = fract((p + vec2f(t * 0.008, -t * 0.015)) * vec2f(9, 7)) - 0.5;
  let spark = 1 - smoothstep(0.008, 0.022, length(cell));
  color += vec3f(0.2, 0.65, 0.8) * spark * frame.typography.z * (1 - artMask);
  return vec4f(color, 1);
}
