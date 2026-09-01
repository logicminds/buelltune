---
title: BuellTune Visual Rebrand - Theme Palette & Screen Consistency Pass - Plan
type: feat
date: 2026-09-01
topic: visual-rebrand-theme-screens
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
product_contract_source: ce-plan-bootstrap
execution: code
origin: docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md
---

# BuellTune Visual Rebrand - Theme Palette & Screen Consistency Pass - Plan

## Goal Capsule

- **Objective:** replace the app's default Android scaffold Material palette (`colors.xml`'s Indigo/Red `colorPrimary`/`colorPrimaryDark`/`colorAccent`) with a new BuellTune brand palette derived from/matched to the new adaptive-icon artwork, and do a full screen-by-screen pass across every legacy Activity/Fragment layout, drawable, and the debug-only Compose `NavHost` shell to remove hardcoded literal colors that would visually clash with the new brand.
- **Product authority:** this session's direct follow-up request, made immediately after reviewing `docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md`, whose Scope Boundaries explicitly deferred "App theme/Material color changes (`colorPrimary`/`colorAccent` in `colors.xml`) — a deliberate palette decision independent of the icon artwork, not requested here." That plan's Deferred to Implementation section also notes the new icon's palette "may reuse today's default scaffold values... or diverge from them" — this plan resolves that fork by tying the new theme palette to the new icon artwork once it exists.
- **Open blockers:** this plan's R1 (and, transitively, R2/R3, which consume the new palette) cannot be finalized until `docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md`'s U1 (new original BuellTune adaptive-icon artwork) has landed — see Key Decisions and Dependencies / Assumptions. The plan itself, its file-level scope, and every non-palette-value decision are settled now; only the literal hex values are gated.

---

## Product Contract

### Summary

The app ships today with the unmodified default Android Studio scaffold palette (`colorPrimary #3F51B5` Indigo, `colorPrimaryDark #303F9F`, `colorAccent #FF0000` Red) plus a hardcoded blue-navy nav-drawer header gradient, several hardcoded-black/white vector icon fills, and a couple of hardcoded literal text/background colors in `troublecodes.xml` — none of it BuellTune-branded, all of it predating both the applicationId rename and the display-name/About-screen rebrand slices. Separately, the new debug-only Compose `NavHost` shell (`BuellTuneNavHost`/`ConnectionStatusScreen`) renders with the unmodified Compose Material 3 default color scheme — it has no `MaterialTheme` color customization at all. This plan replaces the default palette with a BuellTune brand palette matched to the new icon artwork, re-points every hardcoded decorative literal at that palette (or an appropriate theme attribute) instead, and gives the Compose shell an explicit brand `ColorScheme` so it stops rendering with generic Material defaults. It leaves the app's underlying theming architecture (AppCompat, not Material3; no dark/night variant) and the `ColorMap.kt` gauge-value heatmap (a functional data-legibility encoding, not a decorative brand element) untouched.

### Problem Frame

`app/src/main/res/values/colors.xml` defines exactly three colors — `colorPrimary #3F51B5`, `colorPrimaryDark #303F9F`, `colorAccent #FF0000` — the unmodified Android Studio "Empty Activity" scaffold defaults, referenced by `styles.xml`'s single `AppTheme` (`Theme.AppCompat.Light.DarkActionBar`). Nothing in the app's five years of EcmDroid/BuellTune history has ever replaced them. Beyond the theme attributes, several screens and drawables hardcode literal colors that bypass the theme entirely and would clash with any new brand palette: `drawable/side_nav_bar.xml`'s three-stop cyan-to-navy gradient (`#0065B4`→`#005AA1`→`#00335B`) behind the nav-drawer header; `drawable/roundedrectangle.xml`'s `#EEE` fill / `#CCCCCC` stroke; `layout/troublecodes.xml`'s hardcoded `android:textColor="#000"` on two `EditText` fields; and eight nav-drawer-menu vector icons (`ic_cog`, `ic_datachannels`, `ic_ecminfo`, `ic_eeprom`, `ic_pulse`, `ic_torque`, `ic_troublecodes`, plus the two-tone `ic_log`) with hardcoded `#FF000000`/black fills and no `tint`/`iconTint` attribute, so they never picked up theme color even before this plan. The FAB's `ic_connected`/`ic_disconnected` icons hardcode white (`#FFF`), which is correct against the FAB's themed background and is a state-affordance color, not a brand color. Separately, the debug-only Compose shell added by the Kotlin-foundation plan's U12 (`BuellTuneNavHost`, `ConnectionStatusScreen`) calls `MaterialTheme.typography`/`Surface` without ever wrapping its content in a `MaterialTheme { colorScheme = ... }` block, so it renders with the stock M3 light/dark default scheme — also unbranded.

### Key Decisions

- KD1. **The new theme palette is derived from/matched to the new BuellTune icon artwork, not chosen independently.** *(session-settled: user-directed — chosen over picking a palette now and letting the icon (from the sibling plan's U1) diverge from it: keeps the launcher icon, About-screen logo, and in-app chrome visually coherent as one brand, avoids a second follow-up reconciliation pass once the icon artwork exists. Governs R1.)* This plan therefore has a hard build-order dependency on that sibling plan's U1 (see Dependencies / Assumptions) — the exact hex values are Deferred to Implementation, but every other decision in this plan is settled now.
- KD2. **The screen-consistency pass covers every legacy layout/drawable with a hardcoded literal color, plus the Compose debug shell — not a UI redesign, restructure, or dark-theme addition.** *(session-settled: user-directed — "palette + full screen pass" — chosen over a palette-only change that leaves the nav-header gradient, drawer-icon fills, and troublecodes literals stranded on the old palette. Explicitly excludes: adding a `values-night/` dark theme (none exists today, not requested), migrating AppCompat to Material3, or migrating any legacy Fragment screen to Compose — those are separate, already-tracked initiatives (Kotlin-foundation plan's deferred follow-ups). Governs R2, R3.)*
- KD3. **`ColorMap.kt`'s procedural gauge-value heatmap (blue→cyan→green→red→magenta over EEPROM byte values) is out of scope.** *(session-settled: inferred from the sibling plan's KTD3 precedent — a functional data-legibility encoding, not decorative branding; recoloring it changes what an EEPROM cell's color means to a tuner mid-diagnosis, a behavior-affecting decision this plan has no authority to make silently. Governs Scope Boundaries.)*

### Requirements

- R1. `app/src/main/res/values/colors.xml`'s `colorPrimary`, `colorPrimaryDark`, and `colorAccent` are replaced with a new BuellTune brand palette derived from/matched to the icon artwork produced by `docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md`'s U1; `styles.xml`'s `AppTheme` (and its `AppTheme.NoActionBar`/`AppTheme.AppBarOverlay`/`AppTheme.PopupOverlay` variants) continue consuming those same three attribute names — no new theme-architecture migration.
- R2. Every hardcoded literal color identified in the Problem Frame that functions as decorative/brand chrome — the nav-drawer header gradient (`drawable/side_nav_bar.xml`), the `roundedrectangle.xml` background/stroke used by `troublecodes.xml`'s two `EditText` fields (and `troublecodes.xml`'s own hardcoded `android:textColor="#000"`), and the eight untinted nav-drawer-menu vector icon fills — is re-pointed at the new brand palette (via `@color/...` references) or theme-driven tint attributes (e.g. `app:itemIconTint` on the `NavigationView`, or per-icon `android:tint`) instead of a hardcoded literal, while preserving legibility/contrast (the `troublecodes.xml` `#000`-on-`#EEE` pairing keeps working contrast against whatever the new palette resolves the background to). The FAB's `ic_connected`/`ic_disconnected` white fills and `ic_log`'s internal near-black/near-white two-tone waveform detail are state/legibility colors, not brand chrome, and are left as-is.
- R3. The debug-only Compose shell (`BuellTuneNavHost`, `ConnectionStatusScreen`) gains an explicit `MaterialTheme` wrapper with a `ColorScheme` built from the same new brand palette (R1), so it stops rendering with the stock Compose M3 default scheme.

---

## Key Technical Decisions

- KTD1. **The exact new hex values are Deferred to Implementation, resolved when the sibling plan's icon artwork exists.** The mechanism, not the values, is fixed now: the implementer picks `colorPrimary`/`colorPrimaryDark`/`colorAccent` (and any additional named brand colors R2/R3 need, e.g. a distinct nav-header-gradient pair) by sampling/matching the produced icon artwork's dominant colors — not via an automated palette-extraction tool, since the icon's foreground/background/monochrome layers (Adaptive Icon spec) already encode the intended brand colors deliberately. Governs R1, KD1.
- KTD2. **No `values-night/` directory is added.** The app has no dark-theme variant today and none is requested; this plan replaces the existing single-variant palette in place, it does not add theme-mode branching. Governs R1, KD2.
- KTD3. **The nav-drawer header keeps its gradient treatment, re-colored rather than flattened to a solid.** `side_nav_bar.xml`'s three-stop `<gradient>` is a decorative pattern already established in the app, not a rendering bug; replacing its three literal hex stops with three new `@color/nav_header_start`/`nav_header_center`/`nav_header_end`-style references derived from the brand palette preserves the existing visual pattern while removing the literal-hex clash. Flattening it to a solid color is a bigger visual change than "consistency with the new brand" calls for. Governs R2.
- KTD4. **Nav-drawer-menu icons pick up brand color via `NavigationView` tint attributes, not per-icon hand-edited `fillColor` literals.** Research confirms `main_drawer.xml`'s menu items carry no `android:tint`/`app:iconTint` today, so all eight icons render at their hardcoded black fill regardless of theme. Adding `app:itemIconTint="@color/..."` (or equivalent) to the `NavigationView` in `activity_main.xml` is the standard AppCompat/Material `NavigationView` mechanism for themed menu-icon coloring, is a one-line change per surface instead of eight hand-edited vector drawables, and keeps every icon visually consistent if the palette changes again later. `ic_connected`/`ic_disconnected` (FAB icons, not nav-drawer-menu icons) and `ic_log`'s internal two-tone detail are unaffected by this tint (R2). Governs R2.
- KTD5. **`troublecodes.xml`'s hardcoded `android:textColor="#000"` is replaced with a `@color/` reference resolving to the same near-black value** (not a theme-attribute lookup like `?android:attr/textColorPrimary`), so its contrast against `roundedrectangle.xml`'s re-colored background is verified once at implementation time against the actual new background color rather than inherited implicitly from an AppCompat text-color default that was never designed against this background. Governs R2.

---

## Implementation Units

### U1. New BuellTune brand palette (`colors.xml` / `styles.xml`)

**Goal:** replace the default Android-scaffold `colorPrimary`/`colorPrimaryDark`/`colorAccent` with a new BuellTune brand palette derived from/matched to the new adaptive-icon artwork.

**Requirements:** R1. Instantiates KD1, KTD1, KTD2.

**Dependencies:** blocked on `docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md`'s U1 (new icon artwork) landing first — the palette is derived from that artwork (KD1). Blocks U2 and U3 within this plan (both consume the new palette).

**Files:**
- `app/src/main/res/values/colors.xml` — `colorPrimary`, `colorPrimaryDark`, `colorAccent` values replaced; any additional named brand colors U2/U3 need (e.g. nav-header gradient stops) added here
- `app/src/main/res/values/styles.xml` — no structural change; confirm `AppTheme` and its `.NoActionBar`/`.AppBarOverlay`/`.PopupOverlay` variants still resolve correctly against the new values (dark-action-bar / popup-overlay contrast in particular)

**Approach:**
1. Once the sibling plan's U1 icon artwork exists, sample/match its dominant colors to derive `colorPrimary` (main brand color), `colorPrimaryDark` (status-bar / dark-variant color), and `colorAccent` (FAB/interactive-highlight color) — see KTD1.
2. Update `colors.xml` in place; add any additional named colors R2/R3 need (nav-header gradient stops, drawer-icon tint color) as new entries in the same file rather than inlining new literals elsewhere.
3. Visually confirm `AppTheme.AppBarOverlay` (`ThemeOverlay.AppCompat.Dark.ActionBar`) and `AppTheme.PopupOverlay` (`ThemeOverlay.AppCompat.Light`) still produce legible text/icon contrast against the new `colorPrimary` — these overlays assume specific light/dark contrast direction from the base color.

**Test scenarios:**
- Covers R1. Launch the app: the toolbar (`?attr/colorPrimary`), status bar (`colorPrimaryDark`, API 21+ edge-to-edge per `values-v21/styles.xml`), and FAB (`colorAccent`) all render the new brand colors, not the old Indigo/Red.
- Covers R1, KTD2. No `values-night/` directory exists after this unit — single-variant palette only, confirmed by directory listing.
- Covers R1. Open the overflow/popup menu and the action bar: text and icons remain legible (no white-on-white or black-on-black regressions) against the new `colorPrimary`/popup-overlay combination.

**Verification:** `./gradlew assembleDebug` succeeds; before/after screenshots of the main toolbar, status bar, and FAB confirm the new palette renders everywhere `?attr/colorPrimary`/`colorPrimaryDark`/`colorAccent` are referenced.

---

### U2. Screen consistency pass — remove hardcoded literal colors

**Goal:** re-point every decorative hardcoded literal color found in legacy layouts and drawables at the new brand palette (or a theme-driven tint attribute) so no screen visually clashes with the new brand.

**Requirements:** R2. Instantiates KD2, KD3, KTD3, KTD4, KTD5.

**Dependencies:** blocked on U1 (consumes the new palette values / new named colors it defines). Independent of U3.

**Files:**
- `app/src/main/res/drawable/side_nav_bar.xml` — three gradient stops re-pointed at new `@color/` references (KTD3)
- `app/src/main/res/drawable/roundedrectangle.xml` — fill/stroke re-pointed at new `@color/` references
- `app/src/main/res/layout/troublecodes.xml` — `android:textColor="#000"` (both `EditText`s) re-pointed at a new `@color/` reference (KTD5)
- `app/src/main/res/layout/activity_main.xml` — `NavigationView` gains `app:itemIconTint` (or equivalent) referencing the new brand palette (KTD4)
- `app/src/main/res/drawable/ic_cog.xml`, `ic_datachannels.xml`, `ic_ecminfo.xml`, `ic_eeprom.xml`, `ic_pulse.xml`, `ic_torque.xml`, `ic_troublecodes.xml` — left structurally as-is; their fill color is now overridden by the `NavigationView` tint (KTD4), not hand-edited per-file
- `app/src/main/res/drawable/ic_log.xml` — left as-is (internal two-tone waveform detail is legibility, not brand chrome; R2)
- `app/src/main/res/drawable/ic_connected.xml`, `ic_disconnected.xml` — left as-is (FAB state-affordance white, not brand chrome; R2)

**Approach:**
1. Add the `app:itemIconTint` (or `app:itemIconTintList`) attribute to `activity_main.xml`'s `NavigationView`, referencing a new brand color from `colors.xml` (added in U1 or here) — verifies against all eight menu icons simultaneously (KTD4).
2. Replace `side_nav_bar.xml`'s three literal gradient stops with `@color/` references to new nav-header-gradient colors (KTD3).
3. Replace `roundedrectangle.xml`'s literal `#EEE` fill / `#CCCCCC` stroke with `@color/` references.
4. Replace `troublecodes.xml`'s two hardcoded `android:textColor="#000"` instances with a `@color/` reference (KTD5), and manually verify contrast against the re-colored `roundedrectangle.xml` background from step 3.
5. Explicitly do not touch `ColorMap.kt` (KD3), `ic_log.xml`'s internal two-tone fills, or `ic_connected`/`ic_disconnected`'s white fills (R2) — confirm via diff review that these are untouched.

**Test scenarios:**
- Covers R2, KTD4. Open the nav drawer: all eight menu icons (ECM info, trouble codes, pulse/live data, data channels, cog/setup, log, EEPROM, torque) render in the new brand tint color, not black, and are visually consistent with each other.
- Covers R2, KTD3. Open the nav drawer: the header gradient behind the drawer's top section renders in new brand-derived colors, not the old cyan-to-navy gradient.
- Covers R2, KTD5. Open the Trouble Codes screen with at least one populated error field: text remains clearly legible against its background (no contrast regression versus the pre-change `#000`-on-`#EEE` pairing).
- Covers R2, KD3. Open the EEPROM screen: the grid-cell heatmap coloring is visually unchanged from before this unit (still the blue→cyan→green→red→magenta value gradient).
- Covers R2. Diff review confirms `ColorMap.kt`, `ic_log.xml`, `ic_connected.xml`, and `ic_disconnected.xml` are byte-identical to before this unit.

**Verification:** `./gradlew assembleDebug` succeeds with no missing-resource lint errors; before/after screenshots of the nav drawer (open) and Trouble Codes screen confirm the swap; diff review confirms the excluded files are untouched.

---

### U3. Compose debug shell brand theming

**Goal:** wrap the debug-only Compose `NavHost` shell in an explicit `MaterialTheme` using a `ColorScheme` built from the new brand palette, so it stops rendering with the stock Compose Material 3 default colors.

**Requirements:** R3. Instantiates KD2.

**Dependencies:** blocked on U1 (consumes the new palette values). Independent of U2.

**Files:**
- New `app/src/debug/java/biz/logicminds/buelltune/ui/BuellTuneTheme.kt` (or similarly named) — defines a `ColorScheme` (`lightColorScheme(primary = ..., ...)`) from the new brand palette and a `BuellTuneTheme` composable wrapper
- `app/src/debug/java/biz/logicminds/buelltune/ui/BuellTuneDebugActivity.kt` — `setContent { BuellTuneNavHost() }` wrapped as `setContent { BuellTuneTheme { BuellTuneNavHost() } }`

**Approach:**
1. Define a Compose `ColorScheme` sourcing its `primary`/`secondary`/`background`/etc. values from the same brand colors introduced in U1 (either by referencing `colorResource(R.color....)` at composition time, or by duplicating the resolved hex values as `Color(...)` constants — prefer `colorResource` so U1's `colors.xml` stays the single source of truth and a future palette change doesn't require a second edit here).
2. Wrap `BuellTuneDebugActivity`'s `setContent` body in the new `BuellTuneTheme` composable, matching the standard Compose app-theme-wrapper pattern (single wrapper at the activity's `setContent` root, not per-screen).
3. Confirm `ConnectionStatusScreen`'s existing `MaterialTheme.typography`/`Surface` calls now resolve colors from the new `ColorScheme` instead of the Compose default, with no code change needed inside `ConnectionStatusScreen` itself (it already reads from the ambient `MaterialTheme`).

**Test scenarios:**
- Covers R3. Launch `BuellTuneDebugActivity` (debug build variant): `ConnectionStatusScreen`'s `Surface` background and `Text` styling render using the new brand `ColorScheme`, not the stock Compose M3 default light/dark scheme.
- Covers R3, KD2. `ConnectionStatusScreen.kt`'s own source is unchanged — the theming is applied only at the `BuellTuneDebugActivity`/new theme-file layer, confirmed by diff review.

**Verification:** `./gradlew assembleDebug` succeeds; launch `BuellTuneDebugActivity` on a debug build and screenshot-compare against pre-change default M3 rendering.

---

## Scope Boundaries

**Out of scope (non-goals):**

- `ColorMap.kt`'s procedural EEPROM/gauge value-to-color heatmap — a functional data-legibility encoding a tuner reads mid-diagnosis, not decorative branding; recoloring it is a behavior-affecting decision outside this plan's authority (KD3).
- Adding a `values-night/` dark-theme variant — no dark theme exists today and none was requested; this plan replaces the existing single-variant palette in place (KTD2).
- Migrating the app's theming architecture from AppCompat to Material3, or migrating any additional legacy Fragment screen to Compose — separate, already-tracked initiatives from the Kotlin-foundation plan's deferred follow-up work; unrelated to a palette/color-literal pass (KD2).
- `ic_log.xml`'s internal near-black/near-white two-tone waveform detail and `ic_connected`/`ic_disconnected`'s white FAB-icon fills — legibility/state-affordance colors, not brand chrome (R2).
- Play Store / F-Droid listing graphics — distribution remains a later milestone, per the sibling plan's KD3.
- The exact hex values of the new brand palette — Deferred to Implementation, resolved only once the sibling plan's icon artwork exists (KTD1).

---

## Dependencies / Assumptions

- **Hard build-order dependency:** this plan's R1 (and therefore R2, R3) cannot be implemented with real values until `docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md`'s U1 (new original BuellTune adaptive-icon artwork) is complete — the palette is derived from that artwork (KD1). This plan's issue tracking should encode U1 (from the icon/docs plan) as a blocking dependency of this plan's U1.
- `docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md`'s U2 (docs product-naming pass) has no relationship to this plan — independent doc-text work.
- No new build-tooling dependency is required — Compose Material3 (`androidx.compose.material3`) is already a dependency via the debug-only Compose shell (Kotlin-foundation plan's U12).

## Deferred to Implementation

- The new palette's exact hex values (`colorPrimary`, `colorPrimaryDark`, `colorAccent`, nav-header-gradient stops, drawer-icon tint color) — derived from the icon artwork once it exists (KTD1).
- Whether the Compose `ColorScheme` in U3 is built as `lightColorScheme(...)` only or includes a parallel `darkColorScheme(...)` selected via `isSystemInDarkTheme()` — the legacy app has no dark theme (KTD2), but Compose's M3 default scheme is dark-mode-aware out of the box; deciding whether to preserve that or pin to light-only is a small implementation-time call, not a product decision.

## Sources / Research

- `docs/plans/2026-09-01-001-feat-visual-rebrand-icon-docs-plan.md` — sibling plan; its Scope Boundaries explicitly deferred "App theme/Material color changes" and its Deferred to Implementation section left the palette-reuse-vs-diverge question open, which this plan resolves via KD1.
- `app/src/main/res/values/colors.xml`, `app/src/main/res/values/styles.xml`, `app/src/main/res/values-v21/styles.xml` — read directly: confirms the exact current palette (`#3F51B5`/`#303F9F`/`#FF0000`), the single `AppTheme` definition and its three overlay variants, and the API 21+ edge-to-edge transparent-status-bar override.
- `app/src/main/res/layout/troublecodes.xml`, `app/src/main/res/drawable/side_nav_bar.xml`, `app/src/main/res/drawable/roundedrectangle.xml`, `app/src/main/res/drawable/ic_{cog,connected,disconnected,datachannels,ecminfo,eeprom,log,pulse,torque,troublecodes}.xml`, `app/src/main/res/menu/main_drawer.xml`, `app/src/main/res/layout/activity_main.xml`, `app/src/main/res/layout/nav_header_main.xml` — read directly: full inventory of hardcoded literal colors and confirmation that nav-drawer-menu icons carry no existing tint attribute.
- `app/src/main/java/biz/logicminds/buelltune/ColorMap.kt` — read directly: confirms the procedural blue→cyan→green→red→magenta gauge/EEPROM heatmap is unrelated to theme attributes.
- `app/src/debug/java/biz/logicminds/buelltune/ui/BuellTuneDebugActivity.kt`, `BuellTuneNavHost.kt`, `ConnectionStatusScreen.kt` — read directly: confirms the debug-only Compose shell (Kotlin-foundation plan's U12) has no `MaterialTheme` color customization today, relying entirely on Compose M3 defaults.
- 11 Activities/Fragments and their 12 layout files (`MainActivity`, `AboutActivity`, `PrefsActivity`, `MainFragment`, `EEPROMFragment`, `DataChannelFragment`, `LogFragment`, `SetupFragment`, `TroubleCodeFragment`, `ActiveTestsFragment`, `CellEditorDialogFragment`, `TorqueValuesFragment`) — reviewed via codebase scout for hardcoded-color hits; only `troublecodes.xml` and the drawables above carry any literal colors, every other screen is already fully theme-driven.
