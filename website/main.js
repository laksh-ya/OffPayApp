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
    ".bento-card, .card, .mode, .how-step, .screen-card, .install-card, .carrier-row, .maker, .about-prose h2, .about-prose .pull"
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

  // ── 4. Mobile Simulator State Machine & Interactions ───────────────────
  const simTime = document.getElementById("sim-time");
  if (simTime) {
    function updateTime() {
      const now = new Date();
      let h = now.getHours();
      let m = now.getMinutes();
      h = h < 10 ? '0' + h : h;
      m = m < 10 ? '0' + m : m;
      simTime.textContent = `${h}:${m}`;
    }
    updateTime();
    setInterval(updateTime, 15000);
  }

  // Element Selectors
  const views = {
    home: document.getElementById("view-home"),
    pay: document.getElementById("view-pay"),
    scan: document.getElementById("view-scan")
  };

  const overlays = {
    ussd: document.getElementById("sim-overlay-ussd"),
    pin: document.getElementById("sim-overlay-pin"),
    success: document.getElementById("sim-overlay-success")
  };

  // Navigations
  const btnGotoPay = document.getElementById("btn-goto-pay");
  const btnGotoScan = document.getElementById("btn-goto-scan");
  const btnGotoBalance = document.getElementById("btn-goto-balance");
  const btnPayBack = document.getElementById("btn-pay-back");
  const btnScanBack = document.getElementById("btn-scan-back");

  function switchSimView(targetKey) {
    Object.keys(views).forEach(k => {
      const v = views[k];
      if (!v) return;
      if (k === targetKey) {
        v.classList.add("active");
        v.classList.remove("slide-left");
      } else {
        v.classList.remove("active");
        if (targetKey === 'home') {
          v.classList.remove("slide-left");
        } else if (k === 'home') {
          v.classList.add("slide-left");
        }
      }
    });
  }

  if (btnGotoPay) {
    btnGotoPay.addEventListener("click", () => {
      // Pre-fill default
      document.getElementById("sim-pay-vpa").value = "harsh@upi";
      document.getElementById("sim-pay-amount").value = "500";
      switchSimView("pay");
    });
  }
  if (btnGotoScan) {
    btnGotoScan.addEventListener("click", () => switchSimView("scan"));
  }
  if (btnPayBack) {
    btnPayBack.addEventListener("click", () => switchSimView("home"));
  }
  if (btnScanBack) {
    btnScanBack.addEventListener("click", () => switchSimView("home"));
  }

  // Quick amounts selection
  const quickBtns = document.querySelectorAll(".sim-quick-amount-btn");
  const amountInput = document.getElementById("sim-pay-amount");
  quickBtns.forEach(btn => {
    btn.addEventListener("click", () => {
      quickBtns.forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      if (amountInput) {
        amountInput.value = btn.getAttribute("data-val");
      }
    });
  });

  // USSD Log & Flow management
  const ussdLog = document.getElementById("sim-ussd-log");
  const ussdText = document.getElementById("sim-ussd-text");
  const ussdInput = document.getElementById("sim-ussd-input");
  const btnUssdCancel = document.getElementById("btn-ussd-cancel");
  const btnUssdSend = document.getElementById("btn-ussd-send");
  let currentUssdFlow = null; // 'pay' | 'balance'
  let ussdStep = 0;
  let tempVpa = "harsh@upi";
  let tempAmount = "500";

  function appendUssdLog(text, type) {
    if (!ussdLog) return;
    const line = document.createElement("div");
    line.className = type === 'sim' ? 'sim-log-sim' : 'sim-log-carrier';
    line.textContent = text;
    ussdLog.appendChild(line);
    ussdLog.scrollTop = ussdLog.scrollHeight;
  }

  function startUssdFlow(flow) {
    currentUssdFlow = flow;
    ussdStep = 0;
    if (ussdLog) ussdLog.innerHTML = "";
    if (overlays.ussd) overlays.ussd.classList.add("active");
    runUssdStep();
  }

  function runUssdStep() {
    if (!ussdText || !ussdInput || !btnUssdSend || !btnUssdCancel) return;
    
    if (currentUssdFlow === 'pay') {
      if (ussdStep === 0) {
        appendUssdLog(">> Dialing *99#...", "sim");
        ussdText.textContent = "Connecting to NUUP Gateway...";
        ussdInput.style.display = "none";
        btnUssdSend.disabled = true;
        btnUssdCancel.disabled = true;

        setTimeout(() => {
          appendUssdLog("<< Carrier: Welcome to *99# NUUP.\n1. Send Money\n2. Check Balance\n3. My Profile", "carrier");
          ussdText.innerHTML = "Welcome to *99# NUUP.<br/>1. Send Money<br/>2. Check Balance";
          ussdInput.style.display = "block";
          ussdInput.value = "1";
          btnUssdSend.disabled = false;
          btnUssdCancel.disabled = false;
          ussdStep = 1;
        }, 1200);
      } else if (ussdStep === 2) {
        appendUssdLog(">> Sending choice: 1", "sim");
        ussdText.textContent = "Requesting routing details...";
        ussdInput.style.display = "none";
        btnUssdSend.disabled = true;

        setTimeout(() => {
          appendUssdLog("<< Carrier: Enter recipient UPI VPA", "carrier");
          ussdText.textContent = "Enter UPI VPA of the recipient:";
          ussdInput.style.display = "block";
          ussdInput.value = tempVpa;
          btnUssdSend.disabled = false;
          ussdStep = 3;
        }, 1000);
      } else if (ussdStep === 4) {
        appendUssdLog(`>> Sending VPA: ${tempVpa}`, "sim");
        ussdText.textContent = "Resolving payment address...";
        ussdInput.style.display = "none";
        btnUssdSend.disabled = true;

        setTimeout(() => {
          appendUssdLog("<< Carrier: Enter Amount (INR)", "carrier");
          ussdText.textContent = `Enter Transfer Amount (Max 2,000 INR):`;
          ussdInput.style.display = "block";
          ussdInput.value = tempAmount;
          btnUssdSend.disabled = false;
          ussdStep = 5;
        }, 1000);
      } else if (ussdStep === 6) {
        appendUssdLog(`>> Sending Amount: ₹${tempAmount}`, "sim");
        ussdText.textContent = "Preparing security context...";
        ussdInput.style.display = "none";
        btnUssdSend.disabled = true;

        setTimeout(() => {
          appendUssdLog("<< Carrier: Please enter UPI PIN to authorize transaction", "carrier");
          if (overlays.ussd) overlays.ussd.classList.remove("active");
          startPinFlow();
        }, 1000);
      }
    } else if (currentUssdFlow === 'balance') {
      if (ussdStep === 0) {
        appendUssdLog(">> Dialing *99#...", "sim");
        ussdText.textContent = "Connecting to NUUP Gateway...";
        ussdInput.style.display = "none";
        btnUssdSend.disabled = true;
        btnUssdCancel.disabled = true;

        setTimeout(() => {
          appendUssdLog("<< Carrier: Welcome to *99# NUUP.\n1. Send Money\n2. Check Balance\n3. My Profile", "carrier");
          ussdText.innerHTML = "Welcome to *99# NUUP.<br/>1. Send Money<br/>2. Check Balance";
          ussdInput.style.display = "block";
          ussdInput.value = "2";
          btnUssdSend.disabled = false;
          btnUssdCancel.disabled = false;
          ussdStep = 1;
        }, 1200);
      } else if (ussdStep === 2) {
        appendUssdLog(">> Sending choice: 2", "sim");
        ussdText.textContent = "Requesting balance profile...";
        ussdInput.style.display = "none";
        btnUssdSend.disabled = true;

        setTimeout(() => {
          appendUssdLog("<< Carrier: Please enter UPI PIN to check balance", "carrier");
          if (overlays.ussd) overlays.ussd.classList.remove("active");
          startPinFlow();
        }, 1000);
      }
    }
  }

  if (btnUssdSend) {
    btnUssdSend.addEventListener("click", () => {
      if (btnUssdSend.disabled) return;
      ussdStep++;
      runUssdStep();
    });
  }

  if (btnUssdCancel) {
    btnUssdCancel.addEventListener("click", () => {
      if (overlays.ussd) overlays.ussd.classList.remove("active");
      currentUssdFlow = null;
    });
  }

  // Start Pay submit trigger
  const btnPaySubmit = document.getElementById("btn-pay-submit");
  if (btnPaySubmit) {
    btnPaySubmit.addEventListener("click", () => {
      tempVpa = document.getElementById("sim-pay-vpa").value || "harsh@upi";
      tempAmount = document.getElementById("sim-pay-amount").value || "500";
      startUssdFlow('pay');
    });
  }

  // Start Balance check trigger
  if (btnGotoBalance) {
    btnGotoBalance.addEventListener("click", () => {
      startUssdFlow('balance');
    });
  }

  // Scan trigger simulation
  const btnSimulateScan = document.getElementById("btn-simulate-scan");
  if (btnSimulateScan) {
    btnSimulateScan.addEventListener("click", () => {
      // Scan complete animation
      btnSimulateScan.style.transform = "scale(0.95)";
      setTimeout(() => {
        btnSimulateScan.style.transform = "translateY(-10px) scale(1)";
        // Go to pay view and pre-fill merchant
        const payVpa = document.getElementById("sim-pay-vpa");
        const payAmount = document.getElementById("sim-pay-amount");
        if (payVpa) payVpa.value = "chaipoint@upi";
        if (payAmount) payAmount.value = "20";
        switchSimView("pay");
      }, 500);
    });
  }

  // PIN Flow controller
  let pinDigits = "";
  const pinDots = document.querySelectorAll(".pin-dot");
  const keys = document.querySelectorAll(".pin-key");
  const keyBack = document.querySelector(".pin-key-back");
  const keyClear = document.querySelector(".pin-key-clear");
  const successTitle = document.getElementById("sim-success-title");
  const successDesc = document.getElementById("sim-success-desc");
  const successRef = document.getElementById("sim-success-ref");

  function startPinFlow() {
    pinDigits = "";
    updatePinDots();
    if (overlays.pin) overlays.pin.classList.add("active");
  }

  function updatePinDots() {
    pinDots.forEach((dot, index) => {
      if (index < pinDigits.length) {
        dot.classList.add("active");
      } else {
        dot.classList.remove("active");
      }
    });
  }

  keys.forEach(k => {
    k.addEventListener("click", () => {
      if (pinDigits.length < 6) {
        pinDigits += k.getAttribute("data-val");
        updatePinDots();
        if (pinDigits.length === 6) {
          setTimeout(completePinFlow, 300);
        }
      }
    });
  });

  if (keyBack) {
    keyBack.addEventListener("click", () => {
      if (pinDigits.length > 0) {
        pinDigits = pinDigits.slice(0, -1);
        updatePinDots();
      }
    });
  }

  if (keyClear) {
    keyClear.addEventListener("click", () => {
      pinDigits = "";
      updatePinDots();
    });
  }

  function completePinFlow() {
    if (overlays.pin) overlays.pin.classList.remove("active");
    
    // Simulate RAM wiping immediately on submission (Bento connection!)
    const bentoRamVal = document.getElementById("bento-ram-val");
    const bentoRamTimer = document.getElementById("bento-ram-timer");
    if (bentoRamVal && bentoRamTimer) {
      bentoRamVal.textContent = "197346"; // Mock user PIN entered
      bentoRamVal.classList.remove("wiped");
      let countdown = 500;
      bentoRamTimer.textContent = `${countdown}ms`;
      
      const interval = setInterval(() => {
        countdown -= 100;
        bentoRamTimer.textContent = `${countdown}ms`;
        if (countdown <= 0) {
          clearInterval(interval);
          bentoRamVal.textContent = "******";
          bentoRamVal.classList.add("wiped");
          bentoRamTimer.textContent = "WIPED!";
        }
      }, 100);
    }

    // Return to USSD for final logs
    if (overlays.ussd) overlays.ussd.classList.add("active");
    appendUssdLog(">> Authorized with Local PIN", "sim");
    appendUssdLog(">> Wiping PIN from buffer...", "sim");
    
    setTimeout(() => {
      appendUssdLog("<< Carrier: Transmitting message over GSM voice...", "carrier");
      appendUssdLog("<< Carrier: NPCI authorized.", "carrier");
      
      setTimeout(() => {
        if (overlays.ussd) overlays.ussd.classList.remove("active");
        if (overlays.success) overlays.success.classList.add("active");
        
        if (currentUssdFlow === 'pay') {
          if (successTitle) successTitle.textContent = "Payment Success";
          if (successDesc) successDesc.textContent = `Transferred ₹${tempAmount} to ${tempVpa}`;
          if (successRef) successRef.textContent = Math.floor(100000000000 + Math.random() * 900000000000).toString();
          
          // Also dynamically add a transaction to the recent list in homescreen
          const recentList = document.querySelector(".sim-recent-list");
          if (recentList) {
            const newItem = document.createElement("div");
            newItem.className = "sim-recent-item";
            newItem.innerHTML = `
              <div>
                <div class="sim-recent-title">${tempVpa}</div>
                <div class="sim-recent-time">Just Now · offline</div>
              </div>
              <div class="sim-recent-amount lime">₹${parseFloat(tempAmount).toFixed(2)}</div>
            `;
            recentList.insertBefore(newItem, recentList.firstChild);
            if (recentList.children.length > 3) {
              recentList.removeChild(recentList.lastChild);
            }
          }
        } else if (currentUssdFlow === 'balance') {
          if (successTitle) successTitle.textContent = "SBI Balance Checked";
          if (successDesc) successDesc.textContent = "Primary Bank: State Bank of India";
          if (successRef) successRef.textContent = Math.floor(100000000000 + Math.random() * 900000000000).toString();
          
          const balanceVal = document.getElementById("sim-home-balance");
          if (balanceVal) {
            balanceVal.textContent = "Balance: ₹14,204.50";
          }
        }
      }, 1200);
    }, 1000);
  }

  const btnSuccessClose = document.getElementById("btn-success-close");
  if (btnSuccessClose) {
    btnSuccessClose.addEventListener("click", () => {
      if (overlays.success) overlays.success.classList.remove("active");
      switchSimView("home");
    });
  }

  // ── 5. RAM Wipe loop on Bento Card ─────────────────────────────────────
  const bentoRamVal = document.getElementById("bento-ram-val");
  const bentoRamTimer = document.getElementById("bento-ram-timer");
  if (bentoRamVal && bentoRamTimer) {
    function demoRamWipeLoop() {
      setTimeout(() => {
        bentoRamVal.textContent = Math.floor(100000 + Math.random() * 900000).toString();
        bentoRamVal.classList.remove("wiped");
        bentoRamTimer.textContent = "500ms";
        
        setTimeout(() => {
          bentoRamVal.textContent = "******";
          bentoRamVal.classList.add("wiped");
          bentoRamTimer.textContent = "WIPED!";
          setTimeout(demoRamWipeLoop, 6000);
        }, 500);
      }, 3000);
    }
    demoRamWipeLoop();
  }

  // ── 6. Animated stats counter ─────────────────────────────────────────
  const statNums = document.querySelectorAll(".stat-num[data-target]");
  if (statNums.length) {
    const statsIo = new IntersectionObserver((entries) => {
      entries.forEach(e => {
        if (!e.isIntersecting) return;
        const el = e.target;
        const target = parseInt(el.getAttribute("data-target"), 10);
        statsIo.unobserve(el);
        el.classList.add("animated");
        if (target === 0) { el.textContent = "0"; return; }
        const dur = 900;
        const start = performance.now();
        function step(now) {
          const t = Math.min((now - start) / dur, 1);
          // Ease out cubic
          const eased = 1 - Math.pow(1 - t, 3);
          el.textContent = Math.round(eased * target);
          if (t < 1) requestAnimationFrame(step);
          else el.textContent = target;
        }
        requestAnimationFrame(step);
      });
    }, { threshold: 0.5 });
    statNums.forEach(el => statsIo.observe(el));
  }

  // ── 7. FAQ haptic clicks ───────────────────────────────────────────────
  document.querySelectorAll(".faq-question").forEach(q => {
    q.addEventListener("click", () => {
      if (window.playClickSound) window.playClickSound("light");
    });
  });

})();
