// media3's effect surface is @UnstableApi; see CacheProvider for why the
// version is pinned.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import android.opengl.GLES20
import android.util.Log
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import java.util.concurrent.atomic.AtomicLong

/**
 * AMD FidelityFX Contrast Adaptive Sharpening, the no-scaling path: one
 * pass over a 3x3 neighbourhood, output at the input's own size.
 *
 * Ported from `ffx_cas.h` (CasFilter, noScaling = true) in
 * https://github.com/GPUOpen-Effects/FidelityFX-CAS, Copyright (c) 2019
 * Advanced Micro Devices, Inc., released under the MIT licence.
 *
 * [sharpness] is CAS's one knob, 0 (least) to 1 (most); the shader's peak
 * weight is `-1 / mix(8, 5, sharpness)`, as `CasSetup` computes it.
 */
class CasSharpenEffect(private val sharpness: Float) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram =
        CasShaderProgram(peak = -1f / (8f + (5f - 8f) * sharpness), useHdr = useHdr)

    companion object {
        /** Frames the shader has drawn since the process started — proof the effect is in the path, read by [VideoFilterLog]. */
        val framesDrawn = AtomicLong()
    }
}

private class CasShaderProgram(private val peak: Float, useHdr: Boolean) :
    BaseGlShaderProgram(/* useHighPrecisionColorComponents = */ useHdr, /* texturePoolCapacity = */ 1) {
    private val program = try {
        GlProgram(VERTEX, FRAGMENT).apply {
            setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var texel = floatArrayOf(0f, 0f)

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texel = floatArrayOf(1f / inputWidth, 1f / inputHeight)
        Log.e(VIDEO_FILTER_TAG, "CAS shader configured ${inputWidth}x$inputHeight peak=$peak")
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexel", texel)
            program.setFloatUniform("uPeak", peak)
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
        if (CasSharpenEffect.framesDrawn.incrementAndGet() == 1L) Log.e(VIDEO_FILTER_TAG, "CAS shader drew its first frame")
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }
}

private const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vUv;
void main() {
  gl_Position = aFramePosition;
  vUv = aFramePosition.xy * 0.5 + 0.5;
}
"""

// a b c
// d e f
// g h i
private const val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform vec2 uTexel;
uniform float uPeak;
varying vec2 vUv;
vec3 at(float x, float y) { return texture2D(uTexSampler, vUv + vec2(x, y) * uTexel).rgb; }
void main() {
  vec3 a = at(-1.0, -1.0); vec3 b = at(0.0, -1.0); vec3 c = at(1.0, -1.0);
  vec3 d = at(-1.0, 0.0);  vec4 e4 = texture2D(uTexSampler, vUv); vec3 e = e4.rgb; vec3 f = at(1.0, 0.0);
  vec3 g = at(-1.0, 1.0);  vec3 h = at(0.0, 1.0);  vec3 i = at(1.0, 1.0);
  // Soft min and max: the cross, plus the cross and corners.
  vec3 mn = min(min(min(d, e), min(f, b)), h);
  mn += min(mn, min(min(a, c), min(g, i)));
  vec3 mx = max(max(max(d, e), max(f, b)), h);
  mx += max(mx, max(max(a, c), max(g, i)));
  // Less sharpening where the neighbourhood already nears black or white.
  vec3 amp = sqrt(clamp(min(mn, 2.0 - mx) / mx, 0.0, 1.0));
  vec3 w = amp * uPeak;
  vec3 outRgb = clamp(((b + d + f + h) * w + e) / (1.0 + 4.0 * w), 0.0, 1.0);
  gl_FragColor = vec4(outRgb, e4.a);
}
"""
