/* ════════════════════════════════════════════════════════════════════
   Web haptics — bind tactile feedback to interactive elements.
   Uses the Vibration API where available (Android Chrome / Edge); silent
   no-op on iOS Safari (which doesn't expose vibration to the web). The
   intent is enhancement, never a hard dependency.
   ════════════════════════════════════════════════════════════════════ */

(function () {
  const vibrate = (pattern) => {
    if (typeof navigator !== "undefined" && navigator.vibrate) {
      try { navigator.vibrate(pattern); } catch (_) {}
    }
  };

  // Map data-haptic levels → vibration patterns (ms).
  const PATTERNS = {
    light: 8,
    medium: [12],
    strong: [18, 30, 12],
  };

  function bind() {
    document.querySelectorAll("[data-haptic]").forEach((el) => {
      if (el.dataset.hapticBound) return;
      el.dataset.hapticBound = "1";
      el.addEventListener("pointerdown", () => {
        const level = el.dataset.haptic || "light";
        vibrate(PATTERNS[level] ?? PATTERNS.light);
      }, { passive: true });
    });
  }

  // Run on load and again after fonts settle (in case content shifts).
  document.addEventListener("DOMContentLoaded", bind);
  window.addEventListener("load", bind);
})();
