/* ════════════════════════════════════════════════════════════════════
   Page interactions — kept tiny on purpose.
     1. Subtle pointer parallax on the hero phone strip.
     2. IntersectionObserver scroll-reveal for cards / mode panels.
     3. Sticky-nav active-state highlight.
   ════════════════════════════════════════════════════════════════════ */

(function () {
  // ── 1. Hero phones — subtle pointer drift ───────────────────────────
  const heroStrip = document.querySelector(".hero-phones");
  if (
    heroStrip &&
    !window.matchMedia("(prefers-reduced-motion: reduce)").matches &&
    window.matchMedia("(min-width: 721px)").matches
  ) {
    const phones = heroStrip.querySelectorAll(".hp");
    let frame = 0;

    function onMove(e) {
      const r = heroStrip.getBoundingClientRect();
      const cx = r.left + r.width / 2;
      const cy = r.top + r.height / 2;
      const dx = (e.clientX - cx) / r.width;
      const dy = (e.clientY - cy) / r.height;
      cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        phones.forEach((p, i) => {
          const baseRot = i === 0 ? -3 : i === 2 ? 3 : 0;
          const baseY   = i === 1 ? -12 : 0;
          const depth   = (i + 1) * 3;
          p.style.transform =
            `translate(${dx * depth}px, ${baseY + dy * depth}px) rotate(${baseRot}deg)`;
        });
      });
    }
    window.addEventListener("pointermove", onMove, { passive: true });
  }

  // ── 2. Scroll-reveal ────────────────────────────────────────────────
  const reveal = document.querySelectorAll(
    ".card, .mode, .how-step, .screen-card, .install-card, .carrier-row, .maker, .about-prose h2, .about-prose .pull"
  );
  reveal.forEach((el, i) => {
    el.style.opacity = "0";
    el.style.transform = "translateY(14px)";
    el.style.transition = `opacity 480ms cubic-bezier(.2,.8,.2,1) ${Math.min(i, 6) * 40}ms, transform 480ms cubic-bezier(.2,.8,.2,1) ${Math.min(i, 6) * 40}ms`;
  });
  const io = new IntersectionObserver(
    (entries) => {
      entries.forEach((e) => {
        if (e.isIntersecting) {
          e.target.style.opacity = "1";
          e.target.style.transform = "translateY(0)";
          io.unobserve(e.target);
        }
      });
    },
    { threshold: 0.1, rootMargin: "0px 0px -50px 0px" }
  );
  reveal.forEach((el) => io.observe(el));

  // ── 3. Nav active state ─────────────────────────────────────────────
  const navLinks = Array.from(document.querySelectorAll(".nav-links a[href^='#']"));
  const sections = navLinks
    .map((a) => document.querySelector(a.getAttribute("href")))
    .filter(Boolean);
  if (sections.length) {
    const navIo = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            navLinks.forEach((a) =>
              a.classList.toggle(
                "active",
                a.getAttribute("href") === `#${e.target.id}`
              )
            );
          }
        });
      },
      { rootMargin: "-40% 0px -55% 0px" }
    );
    sections.forEach((s) => navIo.observe(s));
  }
})();
