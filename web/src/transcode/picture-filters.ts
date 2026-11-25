/** What an encode for the player does to the picture before encoding it. */

/**
 * The `scale` options that bring a picture larger than UHD down to fit inside
 * it, keeping its shape; anything already smaller passes through untouched.
 *
 * Screen recordings arrive at sizes like 4784x2464. VAAPI refuses to encode
 * anything over 4096 wide, and a frame that size is past every H.264 level,
 * so a browser could refuse it even from libx264. The quotes keep the commas
 * in `min()` from being read as the next filter in the chain.
 */
export const FIT_UHD = "w='min(iw,3840)':h='min(ih,2160)':force_original_aspect_ratio=decrease:force_divisible_by=2";

/**
 * HDR10, HLG and Dolby Vision to SDR BT.709, on the GPU through libplacebo.
 *
 * An H.264 stream for the browser is SDR, so an HDR picture encoded as it is
 * plays grey and washed out. Dolby Vision profile 5 is worse: it has no HDR10
 * base layer, and its reshaped colours read as plain video come out purple
 * and green. libplacebo applies the Dolby Vision metadata and reads the HDR10
 * and HLG tags itself, so one chain covers all three.
 */
export const TONE_MAP_TO_SDR =
  "libplacebo=tonemapping=bt.2390:colorspace=bt709:color_primaries=bt709:color_trc=bt709:range=tv:format=yuv420p";
