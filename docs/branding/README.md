# BuellTune Brand Assets

Source-of-truth artwork for the "Digital Wing" identity: a Pegasus neck/crest
with four equalizer-style wing feathers (low-range through peak-RPM power
bands) plus a telemetry circuit overlay (data-signal line, nodes, connection
vectors).

- `icon.svg` — square app-icon crop (`viewBox="60 30 230 180"`), used to derive
  the Android Adaptive Icon layers.
- `buelltune-banner.svg` — full wordmark banner (mark + "BUELLTUNE" lockup +
  tagline), source of truth for the artwork.
- `buelltune-banner.png` (2048x614) — raster render of `buelltune-banner.svg`
  at 2x, tiled/stitched from two 1024-wide captures for resolution beyond
  this renderer's single-shot cap. Embedded at the top of the repo
  `README.md` instead of the `.svg` directly, for renderers/viewers (e.g.
  some Markdown previewers, non-browser Git clients) that don't render
  inline SVG reliably. Regenerate by re-running the same tile/stitch
  process if the source SVG changes.

## Brand palette

| Token          | Hex       | Name                             |
|----------------|-----------|-----------------------------------|
| Primary Dark   | `#0F1115` | Midnight Asphalt                  |
| Accent Flame   | `#FF4500` | Buell Racing Orange                |
| Data Blue      | `#00D2FF` | Telemetry / ECU Active State       |
| Text Primary   | `#F4F5F7` | Pure Aluminum                     |
| Secondary Gray | `#2A2E37` | Cast Iron / Frame                  |

Mirrored in the app as `app/src/main/res/values/colors.xml`'s
`brand_*` tokens, which `colorPrimary`/`colorPrimaryDark`/`colorAccent` and
the nav-drawer header gradient (`nav_header_gradient_*`) derive from.

## Where the Android assets come from

Android's Adaptive Icon spec has no equivalent to SVG filters, so the
in-app assets are hand-derived from these SVGs rather than a 1:1 vector
import:

- `app/src/main/res/drawable/ic_launcher_foreground.xml` — the mark redrawn
  as an Android `<vector>` (gradients preserved via `<aapt:attr>`; the
  `cyan-glow` blur filter is dropped, since `VectorDrawable` has no filter
  primitives). A `<clip-path>` reproduces `icon.svg`'s own viewBox crop, and
  the group's `scale`/`translate` fit the mark's actual painted-content
  bounding box (after that crop) into a centered 66dp safe zone.
- `app/src/main/res/drawable/ic_launcher_monochrome.xml` — the same geometry
  collapsed to a single silhouette fill (Android 13+ themed-icon layer).
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`
  and `app/src/main/res/drawable/buelltune_logo.png` (About screen, 384x384)
  — flat raster renders of `icon.svg` (background + mark, full fidelity
  including the cyan glow) at each required size, since these are one-shot
  assets rather than something the app re-renders live.
- `app/src/main/res/drawable-nodpi/splash_banner.png` (`SplashActivity`,
  1080x2400) — a full-screen composition, not a 1:1 render of
  `buelltune-banner.svg`: the banner's own `bg-grad` diagonal gradient
  extended edge-to-edge across the whole canvas, a sparse 6-column/12-row
  grid-line overlay (same 5%-opacity white lines, re-spaced for a tall
  screen instead of the banner's dense 800x240 spacing), and the mark +
  "BUELLTUNE" wordmark + tagline (verbatim `defs`/`logo-mark`/`logo-text`
  from the banner, no changes) centered at ~85% width. Pasting the banner's
  own small self-contained card (with its own background/gradient/grid)
  onto `activity_splash.xml`'s plain background reads as a floating
  rectangle; rendering one continuous background+grid+mark composition
  sized for the full screen removes that seam entirely.
  `activity_splash.xml`'s `ImageView` uses `scaleType="centerCrop"` so it
  fills any device aspect ratio without letterboxing. The README's embedded
  banner keeps the original small card treatment (background rect + tight
  grid), since it's viewed inline on an unpredictable page background there.

If the master artwork changes, regenerate the mipmap/logo PNGs by rendering
`icon.svg` (composited over a `#0F1115` background rect) to a high-resolution
master (e.g. 960x960) and downsampling with a high-quality filter (Lanczos)
to 48/72/96/144/192px (legacy launcher) and 384px (About logo); hand-update
the two vector drawables to match any geometry/color change.

## Known source-SVG fix applied

Both SVGs originally drew the telemetry horizon line
(`stroke="url(#cyan-grad)"` on a perfectly horizontal `<path>`) using a
percentage-based (`objectBoundingBox`) gradient. A horizontal line has a
zero-height bounding box, and per the SVG spec a gradient (or pattern) with a
zero-width/height bounding box must not be painted — so that line silently
failed to render in spec-compliant engines (confirmed in Chromium). Fixed by
switching `cyan-grad` to `gradientUnits="userSpaceOnUse"` with explicit
`x1/y1/x2/y2` coordinates in both files.
