struct Vertex {
  @builtin(position) position: vec4f,
  @location(0) color: vec4f,
}
@vertex fn vertex(@location(0) position: vec2f, @location(1) color: vec4f) -> Vertex {
  var out: Vertex;
  out.position = vec4f(position, 0, 1);
  out.color = color;
  return out;
}
@fragment fn fragment(in: Vertex) -> @location(0) vec4f {
  return in.color;
}
