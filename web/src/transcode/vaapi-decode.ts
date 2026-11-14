/**
 * Decoding on the GPU as well as encoding there.
 *
 * Measured on funstation (Ryzen 3 4300U, Vega), ten seconds of 4K HEVC Main 10
 * into h264_vaapi: decoded on the CPU it took 10.1 s and 29 s of CPU, so the
 * conversion only just kept up with playback and the warm-up's 60 s buffer
 * never filled; decoded, fitted and encoded on the GPU it took 4.7 s and
 * under 1 s of CPU. Decoding on the GPU but fitting on the CPU gained almost
 * nothing (8.7 s): converting a 4K 10-bit frame is itself the cost.
 */

/**
 * Whether a source in this codec is decoded on the GPU.
 *
 * Every codec but AV1: with `-hwaccel vaapi` ffmpeg swaps libdav1d for its
 * own AV1 decoder, which has no software path, so a GPU without AV1 (Vega has
 * none) fails the whole conversion. Every other decoder falls back to
 * software on its own when the GPU cannot take a stream (H.264 High 10, a
 * picture wider than the GPU decodes), and the filter below uploads what it
 * produces. An unknown codec might be AV1, so it keeps the CPU path.
 */
export function gpuDecodes(vcodec: string | null | undefined): boolean {
  return vcodec != null && vcodec !== "" && vcodec.toLowerCase() !== "av1";
}

/** The device flags, before `-i`: one named device the decoder, filters and encoder share. */
export function vaapiInputArgs(device: string, gpuDecode: boolean): string[] {
  if (!gpuDecode) return ["-vaapi_device", device];
  return [
    "-init_hw_device",
    `vaapi=va:${device}`,
    "-filter_hw_device",
    "va",
    "-hwaccel",
    "vaapi",
    "-hwaccel_device",
    "va",
    "-hwaccel_output_format",
    "vaapi",
  ];
}

/**
 * The `-vf` for an h264_vaapi encode, fitting the picture with [fit]'s
 * `scale` options.
 *
 * GPU-decoded frames pass `hwupload` untouched; frames a decoder fell back to
 * software for are converted and uploaded first. Either way the GPU fits
 * them, so a picture over VAAPI's 4096 encode limit is shrunk before the
 * encoder sees it, as the CPU path does with `scale` before its upload.
 */
export function vaapiFilter(fit: string, gpuDecode: boolean): string {
  if (!gpuDecode) return `scale=${fit},format=nv12,hwupload`;
  return `format=nv12|p010|vaapi,hwupload,scale_vaapi=${fit}:format=nv12`;
}
