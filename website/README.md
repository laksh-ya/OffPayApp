# OffPay landing page

Static, zero-dependency website for OffPay. Drop the contents of this
folder onto any static host and you're done.

## Files

- `index.html` — the page
- `styles.css` — NeoPOP/CRED dark theme
- `shader.js` — WebGL2 fragment shader powering the hero background
  (multi-octave fBm + grain + pointer light). Falls back to a flat
  black canvas on older browsers.
- `haptics.js` — light vibration feedback bound to `data-haptic`
  attributes. Silent on iOS.
- `main.js` — pointer parallax on the hero phone stack, IO-based scroll
  reveal, sticky-nav active-link highlight.
- `assets/` — icon, logo variants, and 13 phone screenshots.

## Run locally

```sh
# from the website/ folder
python3 -m http.server 5500
# → http://localhost:5500
```

Anything that serves static files (Vercel, Netlify, GitHub Pages,
`vercel deploy`, `npx serve`, `caddy file-server`) works.

## Deploy to Vercel

```sh
npx vercel --cwd website
```

## Deploy to GitHub Pages

Commit `website/` to your repo, then in Settings → Pages set source to
the `website/` folder on `main`. Done.

## Design notes

- Palette: black canvas, lime `#C5F542` accent, no purple (matches the
  Android app's NeoPOP theme).
- Type: **Space Grotesk** for display, **Inter** for body, **JetBrains
  Mono** for kickers/code/USSD-codes.
- Buttons use real NeoPOP geometry — primary lime button has separate
  side and bottom faces drawn via `box-shadow` parallelograms; on
  press the front face translates `+depth` so the box visibly collapses
  into the page.
- The hero shader caps DPR at 1.5 so high-DPI laptops don't melt their
  fans; pauses entirely via IntersectionObserver when the hero scrolls
  out of view.
- All transitions respect `prefers-reduced-motion: reduce`.

## Updating screenshots

Drop replacements into `assets/screenshots/` keeping the same numeric
filenames (`01.jpeg` … `13.jpeg`) and the page just picks them up.
