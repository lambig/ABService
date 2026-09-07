/* eslint-disable functional/immutable-data -- Canvas/WebGPU resource adapters require imperative DOM and pixel buffer updates. */
import type { PresentationFrame } from "./index";
import shader from "./scene.wgsl?raw";

/** Rendererの音響に依存しない描画・破棄境界。 */
export type Renderer = Readonly<{
  render: (frame: PresentationFrame) => void;
  dispose: () => void;
  lost: Promise<GPUDeviceLostInfo>;
}>;
const unavailable = (message: string): never => {
  throw new Error(message);
};

const atlas = (): HTMLCanvasElement => {
  const image = document.createElement("canvas");
  image.width = 512;
  image.height = 1024;
  const context =
    image.getContext("2d") ?? unavailable("Artwork canvas unavailable");
  const gradient = context.createLinearGradient(0, 0, 512, 512);
  gradient.addColorStop(0, "#174e62");
  gradient.addColorStop(1, "#e39966");
  context.fillStyle = gradient;
  context.fillRect(0, 0, 512, 512);
  context.strokeStyle = "#f6e5c3";
  context.lineWidth = 2;
  Array.from({ length: 12 }, (_, index) => index).forEach((index) => {
    context.beginPath();
    context.ellipse(
      256,
      256,
      55 + index * 14,
      150,
      index * 0.16,
      0,
      Math.PI * 2,
    );
    context.stroke();
  });
  context.fillStyle = "#eef3f5";
  context.font = "bold 54px sans-serif";
  context.textAlign = "center";
  context.fillText("AB / RESONANCE", 256, 755, 490);
  context.font = "24px sans-serif";
  context.fillText("AUDIO FEATURES · STUDY 01", 256, 820, 480);
  return image;
};

/** 一枚のatlasとuniform bufferを使うWebGPU候補。初期化失敗は呼び出し元へ返す。 */
export const createRenderer = async (
  canvas: HTMLCanvasElement,
  signal: AbortSignal,
): Promise<Renderer> => {
  const gpu =
    "gpu" in navigator ? navigator.gpu : unavailable("WebGPU unavailable");
  const adapter =
    (await gpu.requestAdapter()) ?? unavailable("WebGPU adapter unavailable");
  const device = await adapter.requestDevice();
  try {
    signal.throwIfAborted();
    const context =
      canvas.getContext("webgpu") ?? unavailable("WebGPU canvas unavailable");
    const format = gpu.getPreferredCanvasFormat();
    const module = device.createShaderModule({ code: shader });
    const pipeline = await device.createRenderPipelineAsync({
      layout: "auto",
      vertex: { module, entryPoint: "vertex" },
      fragment: { module, entryPoint: "fragment", targets: [{ format }] },
      primitive: { topology: "triangle-list" },
    });
    signal.throwIfAborted();
    const buffer = device.createBuffer({
      size: 48,
      usage: GPUBufferUsage.UNIFORM | GPUBufferUsage.COPY_DST,
    });
    const texture = device.createTexture({
      size: [512, 1024],
      format: "rgba8unorm",
      usage:
        GPUTextureUsage.TEXTURE_BINDING |
        GPUTextureUsage.COPY_DST |
        GPUTextureUsage.RENDER_ATTACHMENT,
    });
    device.queue.copyExternalImageToTexture(
      { source: atlas() },
      { texture },
      [512, 1024],
    );
    const group = device.createBindGroup({
      layout: pipeline.getBindGroupLayout(0),
      entries: [
        { binding: 0, resource: { buffer } },
        { binding: 1, resource: texture.createView() },
        {
          binding: 2,
          resource: device.createSampler({
            magFilter: "linear",
            minFilter: "linear",
          }),
        },
      ],
    });
    context.configure({ device, format, alphaMode: "opaque" });
    return Object.freeze({
      lost: device.lost,
      render: (frame: PresentationFrame): void => {
        const ratio = Math.min(window.devicePixelRatio, 2);
        const width = Math.max(
          1,
          Math.min(
            device.limits.maxTextureDimension2D,
            Math.floor(canvas.clientWidth * ratio),
          ),
        );
        const height = Math.max(
          1,
          Math.min(
            device.limits.maxTextureDimension2D,
            Math.floor(canvas.clientHeight * ratio),
          ),
        );
        const resizeWidth =
          canvas.width === width
            ? () => undefined
            : () => {
                canvas.width = width;
              };
        resizeWidth();
        const resizeHeight =
          canvas.height === height
            ? () => undefined
            : () => {
                canvas.height = height;
              };
        resizeHeight();
        device.queue.writeBuffer(
          buffer,
          0,
          new Float32Array([
            width,
            height,
            frame.timeSeconds,
            frame.sceneScale,
            frame.artworkScale,
            frame.artworkOffset,
            frame.backgroundIntensity,
            frame.impulse,
            frame.textOffset,
            frame.textOpacity,
            frame.effectIntensity,
            frame.textureScale,
          ]),
        );
        const encoder = device.createCommandEncoder();
        const pass = encoder.beginRenderPass({
          colorAttachments: [
            {
              view: context.getCurrentTexture().createView(),
              loadOp: "clear",
              storeOp: "store",
              clearValue: [0, 0, 0, 1],
            },
          ],
        });
        pass.setPipeline(pipeline);
        pass.setBindGroup(0, group);
        pass.draw(3);
        pass.end();
        device.queue.submit([encoder.finish()]);
      },
      dispose: (): void => {
        buffer.destroy();
        texture.destroy();
        context.unconfigure();
        device.destroy();
      },
    });
  } catch (error) {
    device.destroy();
    throw error;
  }
};
