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

// ── hash + value noise ─────────────────────────────────────────────
float hash(vec2 p) {
  p = fract(p * vec2(123.34, 345.45));
  p += dot(p, p + 34.345);
  return fract(p.x * p.y);
}

float noise(vec2 p) {
  vec2 i = floor(p);
  vec2 f = fract(p);
  vec2 u = f * f * (3.0 - 2.0 * f);
  float a = hash(i);
  float b = hash(i + vec2(1.0, 0.0));
  float c = hash(i + vec2(0.0, 1.0));
  float d = hash(i + vec2(1.0, 1.0));
  return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// 4-octave fBm with domain warping for that liquid-chrome feel.
float fbm(vec2 p) {
  float v = 0.0;
  float a = 0.5;
  for (int i = 0; i < 4; i++) {
    v += a * noise(p);
    p = p * 2.07 + vec2(11.3, 7.7);
    a *= 0.52;
  }
  return v;
}

float warpedFbm(vec2 p, float t) {
  // Domain warp: feed one fbm into another.
  vec2 q = vec2(fbm(p + t * 0.10), fbm(p - vec2(5.2, 1.3) + t * 0.15));
  return fbm(p + 2.5 * q);
}

float grain(vec2 p, float t) {
  return fract(sin(dot(p + t, vec2(12.9898, 78.233))) * 43758.5453);
}

void main() {
  // Aspect-correct UVs centred at (0,0). y points up.
  vec2 uv = (2.0 * gl_FragCoord.xy - iResolution.xy) / iResolution.y;

  float t = iTime * 0.10;

  // Two warped fields drifting opposite directions for visible motion.
  float n1 = warpedFbm(uv * 1.20 + vec2(t, -t * 0.55), iTime * 0.30);
  float n2 = warpedFbm(uv * 1.85 - vec2(t * 0.70, t * 1.10), iTime * 0.22);

  float field = smoothstep(0.10, 0.95, n1 * 0.55 + n2 * 0.55);

  // Diagonal chrome streak — narrow band of brightness sweeping the canvas.
  // Tied to time on a long period so it feels organic.
  float streakAxis = uv.x * 0.6 + uv.y * 0.6;
  float streakPhase = mod(iTime * 0.35, 6.0) - 3.0;
  float streak = exp(-pow((streakAxis - streakPhase) * 1.6, 2.0));

  // Pointer light — soft lime glow that smoothly follows the cursor.
  vec2 m = (iMouse / iResolution.xy) * 2.0 - 1.0;
  m.x *= iResolution.x / iResolution.y;
  float lightDist = distance(uv, m);
  float light = exp(-lightDist * 1.4) * 0.55;

  // Palette: deep near-black → cool slate → warm muted lime accent.
  vec3 deep = vec3(0.018, 0.020, 0.030);          // near-black background
  vec3 mid  = vec3(0.045, 0.060, 0.090);          // slate blue
  vec3 hot  = vec3(0.560, 0.780, 0.180) * 0.55;   // muted lime

  vec3 col = mix(deep, mid, field);
  col = mix(col, hot, light * (0.55 + 0.35 * field));

  // Streak adds warmth where the band passes — never overwhelming.
  col += hot * streak * 0.18 * field;

  // Tactile film grain in shadows so flat regions read alive.
  float g = grain(gl_FragCoord.xy, fract(iTime));
  col += (g - 0.5) * 0.022;

  // Edge vignette so the canvas blends into the page background.
  float v = 1.0 - smoothstep(0.7, 1.6, length(uv));
  col *= mix(0.50, 1.0, v);

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
