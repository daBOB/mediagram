/** Block widths per step on a 200 px wide canvas; the last step is the sharp image. */
const BLOCKS = [40, 28, 20, 14, 9, 6, 3, 1];

export function blockWidth(step: number, canvasWidth: number): number {
  const i = Math.min(Math.max(step, 0), BLOCKS.length - 1);
  return Math.max(1, BLOCKS[i] * (canvasWidth / 200));
}

const off = document.createElement("canvas");

/** Shrink the image with smoothing, then scale it back up without: square blocks. */
export function drawPixelated(ctx: CanvasRenderingContext2D, img: CanvasImageSource, block: number) {
  const { width, height } = ctx.canvas;
  const w = Math.max(1, Math.ceil(width / block));
  const h = Math.max(1, Math.ceil(height / block));
  off.width = w;
  off.height = h;
  const octx = off.getContext("2d")!;
  octx.imageSmoothingEnabled = true;
  octx.drawImage(img, 0, 0, w, h);
  ctx.imageSmoothingEnabled = false;
  ctx.clearRect(0, 0, width, height);
  ctx.drawImage(off, 0, 0, w, h, 0, 0, width, height);
}
