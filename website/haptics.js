/* ════════════════════════════════════════════════════════════════════
   Web haptics — bind tactile feedback to interactive elements.
   Uses the Vibration API where available (Android Chrome / Edge); silent
   no-op on iOS Safari (which doesn't expose vibration to the web). The
   intent is enhancement, never a hard dependency.
   Also implements a synthesised mechanical audio click using the Web Audio API
   to mimic a mechanical physical key.
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

  // Audio click synthesizer
  let audioCtx = null;
  function playClickSound(type = 'light') {
    try {
      if (!audioCtx) {
        audioCtx = new (window.AudioContext || window.webkitAudioContext)();
      }
      if (audioCtx.state === 'suspended') {
        audioCtx.resume();
      }
      
      const osc = audioCtx.createOscillator();
      const gainNode = audioCtx.createGain();
      osc.connect(gainNode);
      gainNode.connect(audioCtx.destination);
      
      const now = audioCtx.currentTime;
      if (type === 'strong') {
        // Double tick / double mechanical click sound
        osc.type = 'sine';
        osc.frequency.setValueAtTime(800, now);
        osc.frequency.exponentialRampToValueAtTime(150, now + 0.04);
        gainNode.gain.setValueAtTime(0.08, now);
        gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.04);
        osc.start(now);
        osc.stop(now + 0.05);
      } else if (type === 'medium') {
        // Medium mechanical snap
        osc.type = 'triangle';
        osc.frequency.setValueAtTime(600, now);
        osc.frequency.exponentialRampToValueAtTime(100, now + 0.03);
        gainNode.gain.setValueAtTime(0.06, now);
        gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.03);
        osc.start(now);
        osc.stop(now + 0.04);
      } else {
        // Light high-pitch tap
        osc.type = 'sine';
        osc.frequency.setValueAtTime(1000, now);
        osc.frequency.exponentialRampToValueAtTime(200, now + 0.02);
        gainNode.gain.setValueAtTime(0.04, now);
        gainNode.gain.exponentialRampToValueAtTime(0.001, now + 0.02);
        osc.start(now);
        osc.stop(now + 0.03);
      }
    } catch (e) {
      console.warn("Web Audio click failed:", e);
    }
  }

  // Expose playClickSound globally for use in other scripts
  window.playClickSound = playClickSound;

  function bind() {
    document.querySelectorAll("[data-haptic]").forEach((el) => {
      if (el.dataset.hapticBound) return;
      el.dataset.hapticBound = "1";
      el.addEventListener("pointerdown", () => {
        const level = el.dataset.haptic || "light";
        vibrate(PATTERNS[level] ?? PATTERNS.light);
        playClickSound(level);
      }, { passive: true });
    });
  }

  // Run on load and again after fonts settle (in case content shifts).
  document.addEventListener("DOMContentLoaded", bind);
  window.addEventListener("load", bind);
  
  // Re-run periodically to catch dynamically added DOM elements (like in the simulator)
  setInterval(bind, 1000);
})();
