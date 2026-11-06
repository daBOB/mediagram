/**
 * The WebGL half of the deinterlace switch: one comb filter, drawn per frame.
 *
 * The same maths the Android effect runs. For each output pixel (the canvas is
 * one pixel per source line), take its source line `b` and the lines `a`, `c`
 * above and below, sampled at line centres. Where `b` sticks out from both
 * neighbours the same way in luma it is combing, and takes their mean;
 * elsewhere it stays as it was, so static detail keeps full resolution.
 *
 * Kept apart from `deinterlace.js` so that module's logic runs without WebGL.
 */

const THRESHOLD = 0.0025;

const VERTEX = `
attribute vec2 corner;
varying vec2 uv;
void main() {
  uv = vec2(corner.x * 0.5 + 0.5, 0.5 - corner.y * 0.5);
  gl_Position = vec4(corner, 0.0, 1.0);
}`;

const FRAGMENT = `
precision highp float;
uniform sampler2D picture;
uniform float sourceLines;
varying vec2 uv;
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
vec3 line(float l) {
  return texture2D(picture, vec2(uv.x, (clamp(l, 0.0, sourceLines - 1.0) + 0.5) / sourceLines)).rgb;
}
void main() {
  float l = clamp(floor(uv.y * sourceLines), 0.0, sourceLines - 1.0);
  vec3 a = line(l - 1.0);
  vec3 b = line(l);
  vec3 c = line(l + 1.0);
  float comb = (luma(b) - luma(a)) * (luma(b) - luma(c));
  float t = ${THRESHOLD.toFixed(4)};
  gl_FragColor = vec4(mix(b, (a + c) * 0.5, smoothstep(t * 0.5, t * 2.0, comb)), 1.0);
}`;

/**
 * A renderer for `canvas`, or `null` when the browser has no WebGL.
 * @returns {{draw: (video: HTMLVideoElement) => void, clear: () => void}|null}
 */
export function createGlRenderer(canvas) {
  const gl = canvas.getContext?.("webgl", { alpha: true, antialias: false, premultipliedAlpha: true });
  if (!gl) return null;
  let program = null;
  let texture = null;
  let lines = null;

  function compile(type, source) {
    const shader = gl.createShader(type);
    gl.shaderSource(shader, source);
    gl.compileShader(shader);
    if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(shader) ?? "shader");
    return shader;
  }

  /** Everything the context owns; called again after a lost context comes back. */
  function build() {
    program = gl.createProgram();
    gl.attachShader(program, compile(gl.VERTEX_SHADER, VERTEX));
    gl.attachShader(program, compile(gl.FRAGMENT_SHADER, FRAGMENT));
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) throw new Error(gl.getProgramInfoLog(program) ?? "link");
    gl.useProgram(program);
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer());
    gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]), gl.STATIC_DRAW);
    const corner = gl.getAttribLocation(program, "corner");
    gl.enableVertexAttribArray(corner);
    gl.vertexAttribPointer(corner, 2, gl.FLOAT, false, 0, 0);
    texture = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, texture);
    // NEAREST and clamped: a line's centre must return exactly that line.
    for (const [name, value] of [[gl.TEXTURE_MIN_FILTER, gl.NEAREST], [gl.TEXTURE_MAG_FILTER, gl.NEAREST],
      [gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE], [gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE]]) gl.texParameteri(gl.TEXTURE_2D, name, value);
    lines = gl.getUniformLocation(program, "sourceLines");
  }

  let broken = false;
  try {
    build();
  } catch {
    return null;
  }
  canvas.addEventListener("webglcontextlost", (event) => event.preventDefault());
  canvas.addEventListener("webglcontextrestored", () => {
    try { build(); broken = false; } catch { broken = true; }
  });

  return {
    draw(video) {
      if (broken || gl.isContextLost()) return;
      try {
        gl.viewport(0, 0, canvas.width, canvas.height);
        gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, video);
        gl.uniform1f(lines, canvas.height);
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
      } catch {
        // A frame that cannot be uploaded (tainted, mid-reset) is skipped; the next one tries again.
      }
    },
    clear() {
      if (broken || gl.isContextLost()) return;
      gl.clearColor(0, 0, 0, 0);
      gl.clear(gl.COLOR_BUFFER_BIT);
    },
  };
}
