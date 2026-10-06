/* eslint-disable functional/immutable-data -- Canvas/WebGPU resource adapters require imperative DOM and pixel buffer updates. */
import { defaultBudget, drawSize } from "./budget";
import type { RendererBudget } from "./budget";
import type { PresentationFrame } from "./index";
import type { FrameSample } from "./probe";
import type { ListeningAlbum } from "abservice-listening-presentation";
import shader from "./scene.wgsl?raw";

/** 描画の予算と、フレームごとの観測の受け取り先。観測は指定したときだけ取る。 */
export type RendererOptions = Readonly<{
  budget?: RendererBudget;
  onSample?: (sample: FrameSample) => void;
  representative?: boolean;
  content?: ListeningAlbum;
}>;

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
  /* Text occupies the 512 × 128 crop at (0, 704), sampled at the same aspect ratio in scene.wgsl. */
  context.fillStyle = "#eef3f5";
  context.font = "bold 54px sans-serif";
  context.textAlign = "center";
  context.fillText("AB / RESONANCE", 256, 755, 490);
  context.font = "24px sans-serif";
  context.fillText("AUDIO FEATURES · STUDY 01", 256, 820, 480);
  return image;
};

/* The same texture path carries verified presentation bytes; no network or album lookup in the renderer. */
const contentAtlas = async (
  content: ListeningAlbum | undefined,
  signal: AbortSignal,
): Promise<HTMLCanvasElement> => {
  const image = atlas();
  const context =
    image.getContext("2d") ?? unavailable("Artwork canvas unavailable");
  const bitmap =
    content?.artwork === undefined
      ? undefined
      : await createImageBitmap(content.artwork, {
          resizeWidth: 512,
          resizeHeight: 512,
          resizeQuality: "high",
        });
  try {
    signal.throwIfAborted();
    /* Missing artwork retains the neutral generated surface, never the previous album. */
    (bitmap === undefined
      ? () => undefined
      : () => {
          context.fillStyle = "#07111b";
          context.fillRect(0, 0, 512, 512);
          context.drawImage(bitmap, 0, 0, 512, 512);
        })();
  } finally {
    bitmap?.close();
  }
  const title = content?.title ?? "";
  const artist = content?.artistDisplayName ?? "";
  await Promise.all([
    document.fonts.load('600 48px "Klee One"', title),
    document.fonts.load('400 28px "Klee One"', artist),
  ]);
  signal.throwIfAborted();
  context.clearRect(0, 512, 512, 512);
  context.fillStyle = "#eef3f5";
  context.font = '600 48px "Klee One", sans-serif';
  context.fillText(title, 256, 760, 490);
  context.font = '400 28px "Klee One", sans-serif';
  context.fillText(artist, 256, 812, 490);
  return image;
};

/** 一枚のatlasとuniform bufferを使うWebGPU候補。初期化失敗は呼び出し元へ返す。 */
export const createRenderer = async (
  canvas: HTMLCanvasElement,
  signal: AbortSignal,
  options: RendererOptions = {},
): Promise<Renderer> => {
  const budget = options.budget ?? defaultBudget;
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
      fragment: {
        module,
        entryPoint: "fragment",
        targets: [{ format }],
        constants: { representative: options.representative === true ? 1 : 0 },
      },
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
    const pixels =
      options.representative === true
        ? await contentAtlas(options.content, signal)
        : atlas();
    signal.throwIfAborted();
    device.queue.copyExternalImageToTexture(
      { source: pixels },
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
        const begin = performance.now();
        /* Drawn at the budgeted size and stretched to the canvas' CSS size: fewer pixels to fill. */
        const { width, height } = drawSize(
          canvas.clientWidth,
          canvas.clientHeight,
          window.devicePixelRatio,
          budget,
          device.limits.maxTextureDimension2D,
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
            frame.effectIntensity * budget.effectDensity,
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
        const submitted = performance.now();
        const sample = options.onSample;
        /* GPU completion is measured from submit; it includes queue waits, so it is an upper bound. */
        (sample === undefined
          ? () => undefined
          : () => {
              const rendererCpuMs = submitted - begin;
              void device.queue.onSubmittedWorkDone().then(
                () => {
                  sample({
                    rendererCpuMs,
                    gpuMs: performance.now() - submitted,
                    pixels: width * height,
                  });
                },
                () => undefined,
              );
            })();
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
