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
 * A single-frame comb filter for interlaced video flagged progressive, which
 * no decoder here deinterlaces. Each output pixel takes its source line and
 * the lines above and below; where the line sticks out from both neighbours
 * the same way (a comb), it becomes their average, elsewhere it is left
 * alone. Static areas keep full resolution and the frame rate is unchanged.
 * The web player's WebGL filter does the same maths.
 *
 * [sourceLines] is the decoded video's height in lines, read when a frame is
 * drawn. It is not the texture's height: media3 stretches the texture
 * vertically for a pixel aspect ratio below 1 (see
 * `DefaultVideoFrameProcessor.adjustForPixelWidthHeightRatio`), and the
 * fields interleave per decoded line, so lines are found at `(L + 0.5) / sourceLines`
 * whatever the texture's size. A value below 1 means "not known yet": the texture's own height is used.
 */
class DeinterlaceEffect(private val sourceLines: () -> Int) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram = DeinterlaceShaderProgram(sourceLines, useHdr)

    companion object {
        /** Frames the shader has drawn since the process started — read by [VideoFilterLog]. */
        val framesDrawn = AtomicLong()
    }
}

private class DeinterlaceShaderProgram(private val sourceLines: () -> Int, useHdr: Boolean) :
    BaseGlShaderProgram(/* useHighPrecisionColorComponents = */ useHdr, /* texturePoolCapacity = */ 1) {
    private val program = try {
        GlProgram(FULLSCREEN_VERTEX, FRAGMENT).apply {
            setBufferAttribute("aFramePosition", GlUtil.getNormalizedCoordinateBounds(), GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE)
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }
    private var textureHeight = 0

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        textureHeight = inputHeight
        Log.e(VIDEO_FILTER_TAG, "deinterlace shader configured ${inputWidth}x$inputHeight lines=${sourceLines()}")
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatUniform("uLines", (sourceLines().takeIf { it > 0 } ?: textureHeight).toFloat())
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GlUtil.checkGlError()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
        if (DeinterlaceEffect.framesDrawn.incrementAndGet() == 1L) Log.e(VIDEO_FILTER_TAG, "deinterlace shader drew its first frame")
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

// T = 0.0025 is about 13/255 of luma on each side of the line.
private const val FRAGMENT = """
precision highp float;
uniform sampler2D uTexSampler;
uniform float uLines;
varying vec2 vUv;
const float T = 0.0025;
vec4 row(float line) { return texture2D(uTexSampler, vec2(vUv.x, (line + 0.5) / uLines)); }
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
void main() {
  float line = min(floor(vUv.y * uLines), uLines - 1.0);
  vec4 b = row(line);
  vec4 a = row(max(line - 1.0, 0.0));
  vec4 c = row(min(line + 1.0, uLines - 1.0));
  float comb = (luma(b.rgb) - luma(a.rgb)) * (luma(b.rgb) - luma(c.rgb));
  gl_FragColor = vec4(mix(b.rgb, (a.rgb + c.rgb) * 0.5, smoothstep(T * 0.5, T * 2.0, comb)), b.a);
}
"""
