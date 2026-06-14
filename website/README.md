# OffPay | Landing Page

**Live:** [offpayapp.vercel.app](https://offpayapp.vercel.app/)

Static, zero-dependency marketing site for OffPay. No build step, no framework: just HTML/CSS/JS served as-is.

## Structure

```
website/
├── index.html          Main landing page
├── about.html          About / story page
├── styles.css          Gilroy + NeoPOP dark theme
├── shader.js           WebGL2 hero background (fBm + chrome streak)
├── haptics.js          Vibration API on data-haptic elements
├── main.js             Scroll-reveal, pointer parallax, nav highlight
└── assets/
    ├── logos/          icon.png, logo1.png
    ├── screenshots/   13 named app screenshots
    ├── cat_aesthetic.png
    ├── pwa-mockup.png  iPhone 14 Pro gold frame mockup
    └── pwa-screen.png  Raw PWA screenshot
```

## Run locally

```sh
cd website
python3 -m http.server 5500
# → http://localhost:5500
```

## Deploy

Already live on Vercel at [offpayapp.vercel.app](https://offpayapp.vercel.app/).

To redeploy after changes:

```sh
cd website
npx vercel --prod
```

Or just push to `main`: Vercel auto-deploys from the `website/` root directory.

## Design

- **Type:** Gilroy (all weights, from `web-assets.cred.club`) + JetBrains Mono for kickers/code.
- **Palette:** `#0d0d0d` canvas, `#C5F542` lime accent, no purple, no serif.
- **Buttons:** Real NeoPOP plunk geometry: skewed parallelogram edges (`skewX/Y(45deg)`), front face translates `+6px` on press so the box collapses into the page.
- **Hero shader:** WebGL2 domain-warped fBm + diagonal chrome streak + pointer-following lime light + film grain. Caps DPR at 1.5, pauses via IntersectionObserver when off-screen.
- **Carousels:** Showcase + How-it-works phone strips use native CSS `scroll-snap-type: x mandatory` (no JS carousel library).
- **Reduced motion:** All animations respect `prefers-reduced-motion: reduce`.

## Updating screenshots

Drop replacements into `assets/screenshots/` keeping the same filenames:

```
pay-form.jpeg, pay-form-alt.jpeg, balance-form.jpeg,
balance-result.jpeg, qr-scanner.jpeg, history-list.jpeg,
history-detail.jpeg, payment-running.jpeg, payment-success.jpeg,
payment-success-alt.jpeg, payment-failed.jpeg, settings.jpeg, faq.jpeg
```

## Links

- **App landing:** [offpayapp.vercel.app](https://offpayapp.vercel.app/)
- **PWA:** [offpay.vercel.app](https://offpay.vercel.app/)
- **Lakshya:** [github.com/laksh-ya](https://github.com/laksh-ya/)
- **Harsh:** [github.com/harshtripathi272](https://github.com/harshtripathi272/)
