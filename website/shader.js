/* ════════════════════════════════════════════════════════════════════
   Hero shader — WebGL2 fragment shader.

   Two layered fields drift in opposite directions through warped 3-octave
   fBm domain. A directional "chrome streak" sweeps diagonally on a slow
   loop. Pointer light follows the cursor with smoothing. Tactile film
   grain on top. Aspect-correct UVs (no stretch). Caps DPR at 1.5 so it
   runs cool on retina laptops. Pauses via IntersectionObserver when off
   screen. Falls back to a flat dark canvas if WebGL2 isn't available.
   ════════════════════════════════════════════════════════════════════ */

(function () {
  const canvas = document.getElementById("hero-shader");
  if (!canvas) return;

  const gl = canvas.getContext("webgl2", { antialias: false, alpha: true, premultipliedAlpha: false });
  if (!gl) {
    canvas.style.background = "#07070a";
    return;
  }

  const vert = `#version 300 es
in vec2 a_pos;
void main() {
  gl_Position = vec4(a_pos, 0.0, 1.0);
}`;

  const frag = `#version 300 es
precision highp float;
out vec4 fragColor;

uniform vec2 iResolution;
uniform float iTime;
uniform vec2 iMouse;

float hash(vec2 p) {
  p = fract(p * vec2(123.34, 345.45));
  p += dot(p, p + 34.345);
  return fract(p.x * p.y);
}

float grain(vec2 p, float t) {
  return fract(sin(dot(p + t, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
  // Aspect-correct UVs centred at (0,0). y points up.
  vec2 uv = (2.0 * gl_FragCoord.xy - iResolution.xy) / iResolution.y;

  // Base background: deep slate-charcoal (very premium dark)
  vec3 bgCol = vec3(0.043, 0.043, 0.047);

  // Technical blueprint grid lines (spaced at 40px)
  vec2 gridUV = gl_FragCoord.xy / 40.0;
  vec2 grid = abs(fract(gridUV - 0.5) - 0.5) / fwidth(gridUV);
  float line = 1.0 - min(grid.x, grid.y);
  float gridPattern = smoothstep(0.0, 1.0, line) * 0.035;

  // Intersecting dots
  vec2 dotUV = fract(gridUV);
  float dotPattern = smoothstep(0.12, 0.0, length(dotUV - 0.5)) * 0.07;

  // Pointer position in aspect UV space
  vec2 m = (iMouse / iResolution.xy) * 2.0 - 1.0;
  m.x *= iResolution.x / iResolution.y;

  // Radio signal ripples radiating from pointer
  float d = distance(uv, m);
  float waveSpeed = 2.4;
  float waveFreq = 22.0;
  float waveIntensity = sin(d * waveFreq - iTime * waveSpeed);
  waveIntensity = smoothstep(0.72, 1.0, waveIntensity);
  // Fade out ripples as they travel
  waveIntensity *= exp(-d * 0.75) * 0.16;

  // Constant background pulse from the center representing network heartbeat
  float dCenter = length(uv);
  float centerWave = sin(dCenter * 10.0 - iTime * 1.2);
  centerWave = smoothstep(0.85, 1.0, centerWave) * exp(-dCenter * 0.5) * 0.04;

  // Soft lime glow under the pointer
  float glow = exp(-d * 1.4) * 0.35;

  // OffPay Lime accent color (#c5f542)
  vec3 lime = vec3(0.772, 0.960, 0.258);

  // Combine background, grid pattern, and dot matrix
  vec3 col = bgCol + lime * gridPattern + lime * dotPattern;

  // Add interactive elements (ripples, glow, heartbeat)
  col += lime * glow;
  col += lime * (waveIntensity + centerWave);

  // Subtle horizontal CRT scanlines
  float scanline = sin(gl_FragCoord.y * 0.8) * 0.012;
  col -= vec3(scanline);

  // Film grain for analog texture
  float g = grain(gl_FragCoord.xy, fract(iTime));
  col += (g - 0.5) * 0.018;

  // Smooth vignette blend into page margins
  float v = 1.0 - smoothstep(0.6, 1.4, length(uv));
  col *= mix(0.40, 1.0, v);

  fragColor = vec4(col, 1.0);
}`;

  function compile(type, src) {
    const s = gl.createShader(type);
    gl.shaderSource(s, src);
    gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) {
      console.warn("shader compile error:", gl.getShaderInfoLog(s));
      gl.deleteShader(s);
      return null;
    }
    return s;
  }

  const vs = compile(gl.VERTEX_SHADER, vert);
  const fs = compile(gl.FRAGMENT_SHADER, frag);
  if (!vs || !fs) return;

  const prog = gl.createProgram();
  gl.attachShader(prog, vs);
  gl.attachShader(prog, fs);
  gl.linkProgram(prog);
  if (!gl.getProgramParameter(prog, gl.LINK_STATUS)) {
    console.warn("program link error:", gl.getProgramInfoLog(prog));
    return;
  }
  gl.useProgram(prog);

  const vbo = gl.createBuffer();
  gl.bindBuffer(gl.ARRAY_BUFFER, vbo);
  gl.bufferData(gl.ARRAY_BUFFER, new Float32Array([-1, -1, 3, -1, -1, 3]), gl.STATIC_DRAW);
  const aPos = gl.getAttribLocation(prog, "a_pos");
  gl.enableVertexAttribArray(aPos);
  gl.vertexAttribPointer(aPos, 2, gl.FLOAT, false, 0, 0);

  const uRes = gl.getUniformLocation(prog, "iResolution");
  const uTime = gl.getUniformLocation(prog, "iTime");
  const uMouse = gl.getUniformLocation(prog, "iMouse");

  // Cap DPR so the shader stays cheap on hi-DPI laptops.
  const dpr = Math.min(window.devicePixelRatio || 1, 1.5);
  let width = 0, height = 0;

  function resize() {
    const r = canvas.getBoundingClientRect();
    width = Math.max(1, Math.floor(r.width * dpr));
    height = Math.max(1, Math.floor(r.height * dpr));
    canvas.width = width;
    canvas.height = height;
    gl.viewport(0, 0, width, height);
  }
  resize();
  const ro = new ResizeObserver(resize);
  ro.observe(canvas);

  // Smoothed pointer follow.
  const mouse = { x: 0, y: 0, tx: 0, ty: 0 };
  function onPointer(e) {
    const r = canvas.getBoundingClientRect();
    const x = (e.touches ? e.touches[0].clientX : e.clientX) - r.left;
    const y = (e.touches ? e.touches[0].clientY : e.clientY) - r.top;
    mouse.tx = x * dpr;
    mouse.ty = (r.height - y) * dpr;
  }
  canvas.addEventListener("pointermove", onPointer, { passive: true });
  canvas.addEventListener("touchmove", onPointer, { passive: true });
  // Light parked at upper-right by default so the canvas reads warm even
  // before the user moves the cursor.
  mouse.tx = width * 0.78;
  mouse.ty = height * 0.78;
  mouse.x = mouse.tx;
  mouse.y = mouse.ty;

  let raf = 0;
  let running = true;
  const start = performance.now();

  const io = new IntersectionObserver(
    (entries) => {
      for (const ent of entries) {
        const wasRunning = running;
        running = ent.isIntersecting;
        if (running && !wasRunning) raf = requestAnimationFrame(loop);
      }
    },
    { threshold: 0 }
  );
  io.observe(canvas);

  function loop() {
    if (!running) return;
    const t = (performance.now() - start) / 1000;

    mouse.x += (mouse.tx - mouse.x) * 0.06;
    mouse.y += (mouse.ty - mouse.y) * 0.06;

    gl.uniform2f(uRes, width, height);
    gl.uniform1f(uTime, t);
    gl.uniform2f(uMouse, mouse.x, mouse.y);
    gl.drawArrays(gl.TRIANGLES, 0, 3);

    raf = requestAnimationFrame(loop);
  }
  raf = requestAnimationFrame(loop);

  window.addEventListener("pagehide", () => {
    cancelAnimationFrame(raf);
    running = false;
    io.disconnect();
    ro.disconnect();
  }, { once: true });
})();
