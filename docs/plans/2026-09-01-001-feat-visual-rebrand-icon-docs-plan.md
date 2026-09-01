---
title: BuellTune Visual Rebrand - App Icon & Docs Naming Pass - Plan
type: feat
date: 2026-09-01
topic: visual-rebrand-icon-docs
artifact_contract: ce-unified-plan/v1
artifact_readiness: implementation-ready
product_contract_source: ce-plan-bootstrap
execution: code
origin: docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md
---

# BuellTune Visual Rebrand - App Icon & Docs Naming Pass - Plan

## Goal Capsule

- **Objective:** finish the two branding items the Kotlin-foundation plan deferred and that the display-name/About-screen slice (`buelltune-xzm`) did not cover: replace the app's launcher icon and About-screen logo — still the original EcmDroid artwork — with new, original BuellTune artwork delivered as a modern Android Adaptive Icon, and complete the "EcmDroid" → "BuellTune" product-name rename pass across the remaining docs (`docs/USER_GUIDE.md`, `docs/DEVELOPER_GUIDE.md`, `README.md`, `AGENTS.md`).
- **Product authority:** this session's direct request, grounded in the Kotlin-foundation plan's Scope Boundaries ("Visual rebrand: app icon... docs/USER_GUIDE.md's product naming, and other BuellTune branding/UI-copy work beyond the applicationId/package rename") and confirmed by `bd`/git evidence that the foundation work (all 14 units) and the display-name slice are closed.
- **Open blockers:** none. The two design/scope forks (icon origin, adaptive-icon modernization) were resolved this session — see Key Technical Decisions.

---

## Product Contract

### Summary

The Kotlin-foundation plan's rebrand covered the package/applicationId and (via a follow-up slice, `buelltune-xzm`) the display name and About-screen attribution text. Two branding items from that plan's deferred "Visual rebrand" bucket remain: the launcher icon and About-screen logo image are still Lutz Gebhardt's original EcmDroid artwork, and `docs/USER_GUIDE.md`, `docs/DEVELOPER_GUIDE.md`, `README.md`, and `AGENTS.md` still open with "EcmDroid" product-name prose. This plan closes both, as two independent, small implementation units. Play Store distribution, app-theme color changes, and any Java/Kotlin class-name rename remain explicitly out of scope (see Scope Boundaries).

### Problem Frame

`app/src/main/assets/about.html` still credits "EcmDroid Launcher Icon Copyright (C) 2012 by Lutz Gebhardt," and the same artwork ships as every `mipmap-*/ic_launcher.png` density and as `drawable/ecmdroid.png` (the About-screen logo). The app has no Adaptive Icon (`mipmap-anydpi-v26/`) at all today — only legacy flat PNGs — despite `minSdk` 26 fully supporting Adaptive Icons since the Kotlin-foundation plan's API 36 compliance work. Separately, `docs/USER_GUIDE.md` (title, 12-entry Table of Contents, ~20 inline mentions), `docs/DEVELOPER_GUIDE.md` (title + intro), `README.md` (title + intro + one inline mention), and `AGENTS.md` (Project Overview opening line) all still introduce the product as "EcmDroid," inconsistent with the rest of the rebrand.

### Key Decisions

- KD1. **New artwork is original, not derived from the existing EcmDroid icon.** *(session-settled: user-approved — chosen over modifying/recoloring the existing Gebhardt artwork: matches the hard-fork identity already established on the About screen, and removes the Gebhardt icon-copyright attribution entirely rather than carrying it forward for a derivative work. Governs R1.)*
- KD2. **The icon ships as a full Adaptive Icon (foreground/background/monochrome), not a flat-PNG swap.** *(session-settled: user-approved — chosen over replacing only the 5 legacy mipmap PNGs at their current resolutions: `minSdk` 26 already supports Adaptive Icons, and this project's own precedent (the Kotlin-foundation plan) treats "restore compliance now, don't defer" as the default posture. Governs R1.)*
- KD3. **Play Store distribution stays a future milestone, not this plan's concern.** *(session-settled: user-directed, this session — "once we reach a milestone and ready for playstore we will distribute there." Reaffirms the Kotlin-foundation plan's KD5. No listing graphics, screenshots, or store-description copy are part of this plan.)*

### Requirements

- R1. The app's launcher icon and About-screen logo are replaced with new, original BuellTune artwork. The launcher icon ships as an Adaptive Icon (`mipmap-anydpi-v26/ic_launcher.xml` with foreground, background, and monochrome layers) plus a regenerated legacy flat-PNG fallback at all 5 existing densities; the About screen's logo `<img>` and its now-inapplicable Gebhardt icon-copyright line are updated to match.
- R2. Every remaining "EcmDroid" product-name mention in `docs/USER_GUIDE.md` (title, Table of Contents, all in-body headers and inline text), `docs/DEVELOPER_GUIDE.md` (title, intro paragraph, the two USB device-filter sentences), `README.md` (title, intro, one inline mention), and `AGENTS.md` (Project Overview opening sentence) reads "BuellTune." Every literal Java/Kotlin class name (`EcmDroidService`, `EcmDroidApp`, `DBHelper`, etc.), the `ecmsim` sibling-project name/link, and the `Bin2MslConverter` `.msl` header-string documentation are left untouched, since none of those are branding.

---

## Key Technical Decisions

- KTD1. **The legacy mipmap PNG set is regenerated from the new artwork, not deleted.** `minSdk` 26 is exactly the API level Adaptive Icons were introduced in, so the XML definition alone would be sufficient — but Android Studio's Image Asset Studio (or an equivalent tool) generates the full adaptive-layer set and the legacy PNG fallback from one source image in a single pass, and several non-launcher surfaces (older launchers, some app-shortcut/widget contexts) still read the flat PNG. Regenerating costs nothing extra and avoids a second follow-up. Governs R1.
- KTD2. **No `android:roundIcon` is added.** The manifest declares only `android:icon="@mipmap/ic_launcher"` today, with no `roundIcon` attribute. Adding one is a separate, optional enhancement this plan doesn't need — the Adaptive Icon XML alone is sufficient for every API 26+ launcher to apply its own icon-shape mask. Governs R1.
- KTD3. **The `.msl` header string (`"EcmDroid/Bin2Msl <ECM_TYPE>"`, `Bin2MslConverter.kt:253`) is left unchanged.** It is a real code-emitted literal, not documentation prose, and the Kotlin-foundation plan's R5 requires `Bin2MslConverter`'s output to stay byte-identical to the committed `BUE2D_log.msl` fixture. Changing it is a source-code + fixture-regeneration decision with MegaLogViewer-interop consequences, not a docs edit — explicitly out of scope here (see Scope Boundaries). Governs R2.
- KTD4. **Class names (`EcmDroidService`, `EcmDroidApp`, `DBHelper`, and their references across `AGENTS.md`/`docs/DEVELOPER_GUIDE.md`) are left unchanged.** The Kotlin-foundation plan's KTD8 already established that ported classes keep their exact JVM-visible names; renaming any of them now would be a source-level refactor touching every call site and test, not a docs/branding change. Governs R2.

---

## Implementation Units

### U1. New original BuellTune app icon (Adaptive Icon) and About-screen logo

**Goal:** replace the legacy EcmDroid launcher icon and About-screen logo with new, original BuellTune artwork, shipped as a compliant Android Adaptive Icon, and update the About screen's attribution text to match.

**Requirements:** R1. Instantiates KD1, KD2, KTD1, KTD2.

**Dependencies:** none.

**Files:**
- New `app/src/main/res/drawable/ic_launcher_foreground.xml` (or equivalent vector/raster foreground layer)
- New `app/src/main/res/drawable/ic_launcher_background.xml` (or a `colors.xml` entry, if the background is a flat color)
- New `app/src/main/res/drawable/ic_launcher_monochrome.xml` (Android 13+ themed-icon layer)
- New `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` referencing the three layers above
- Updated `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` — legacy flat-icon fallback regenerated from the new artwork
- `app/src/main/res/drawable/ecmdroid.png` — removed, replaced by a new drawable (e.g. `drawable/buelltune_logo.png`) used by the About screen
- `app/src/main/assets/about.html` — `<img src="file:///android_res/drawable/ecmdroid.png"/>` re-pointed at the new drawable; the "EcmDroid Launcher Icon Copyright (C) 2012 by Lutz Gebhardt" line removed

**Approach:**
1. Source one new master artwork concept for BuellTune — original, not derived from the existing Gebhardt icon (KD1). Exact motif/palette is a creative decision left to implementation (see Deferred to Implementation); reusing today's default `colorPrimary`/`colorAccent` scaffold values as a palette anchor is a reasonable default but not required.
2. Run Android Studio's Image Asset Studio (or an equivalent generator) against that master image to produce the adaptive foreground/background/monochrome layers, the `mipmap-anydpi-v26/ic_launcher.xml` definition, and the full legacy mipmap PNG set at all 5 densities in one pass (KTD1) — the standard, AGP-recognized path; `android:icon="@mipmap/ic_launcher"` in the manifest needs no edit. The foreground and background layers must respect the Adaptive Icon spec's 108dp full viewport with a 66dp safe zone (content outside the safe zone may be cropped by OEM icon-shape masks); the monochrome layer must contain only the icon silhouette with no background, per Android 13+ themed-icon convention. If Image Asset Studio is unavailable or unsuitable for the sourced artwork, hand-author `mipmap-anydpi-v26/ic_launcher.xml` and the three drawables directly, then regenerate the 5 legacy PNG densities with an alternative rasterizer.
3. Swap the About-screen logo drawable and update `about.html`'s `<img>` reference.
4. Remove the Gebhardt icon-copyright line from `about.html` — it no longer applies once that artwork is replaced with original work. Every other attribution line (Michel Marti's code copyright, the ecmspy.com database credit, the BLE/USB-serial library credits) stays exactly as-is; this unit touches icon/logo attribution only.

**Execution note:** sourcing the actual artwork is a creative step outside this plan's authority — the approach above governs how the sourced art becomes a compliant Android icon set, not what the art depicts.

**Test scenarios:**
- Covers R1. Install the build on an API 26+ device/emulator: the launcher icon renders as the new BuellTune artwork, not the legacy EcmDroid icon.
- Covers R1, KTD1. With system themed icons enabled (Android 13+), the launcher icon's monochrome layer tints correctly with the system palette; with themed icons off, the full-color adaptive icon renders with no foreground clipping or background bleed under at least one non-default icon-shape mask (circle/squircle).
- Covers R1. Open the About screen: the new logo drawable renders in place of the old EcmDroid image, the "EcmDroid Launcher Icon Copyright... Gebhardt" line is gone, and every other attribution line is unchanged from before this unit.
- Covers R1, KTD1. Install on emulators/devices spanning at least three DPI buckets (e.g. mdpi, xxhdpi, xxxhdpi) and visually confirm the launcher icon is crisp, correctly scaled, and free of rasterization artifacts on each — a bad regeneration only surfaces on specific-density devices.

**Verification:** `./gradlew assembleDebug` succeeds with no missing-resource lint errors; before/after screenshots of the launcher icon and About screen confirm the swap.

---

### U2. BuellTune product-naming pass across remaining docs

**Goal:** replace the remaining stale "EcmDroid" product-name prose in `docs/USER_GUIDE.md`, `docs/DEVELOPER_GUIDE.md`, `README.md`, and `AGENTS.md` with "BuellTune," without touching code identifiers or the `.msl` fixture string.

**Requirements:** R2. Instantiates KTD3, KTD4.

**Dependencies:** none (independent of U1).

**Files:**
- `docs/USER_GUIDE.md` — title (line 1); all 12 Table-of-Contents entries and their anchor targets; in-body headers `## 1. What EcmDroid Can Do` and `## 4. Installing EcmDroid`; inline mentions at lines 3, 47, 80, 91, 96, 109, 114-115, 122, 137, 161, 256, 290, 309, 312, 317
- `README.md` — title (line 1), intro sentence (line 4), inline mention (line 53)
- `docs/DEVELOPER_GUIDE.md` — title (line 1), intro sentence (line 3), USB device-filter sentences (lines 311, 318)
- `AGENTS.md` — Project Overview opening sentence (line 5)

**Approach:**
1. Do a literal "EcmDroid" → "BuellTune" pass restricted to product-name prose (titles, descriptions, "the app" narration) in the four files above.
2. In `docs/USER_GUIDE.md`, the two headers that change (`§1`, `§4`) also change their Markdown auto-generated anchor slugs (e.g. `#1-what-ecmdroid-can-do` → `#1-what-buelltune-can-do`) — update every Table-of-Contents link to the new slug, not just the visible header text, or the TOC silently breaks.
3. Leave every reference to the sibling `ecmsim` project — its name and the `github.com/ecmdroid/ecmsim` link — untouched; that is a different, still-EcmDroid-named upstream project this repo depends on (KTD-independent: it is simply not this app).
4. Leave every literal class name (`EcmDroidService`, `EcmDroidApp`, `DBHelper`, `EcmDroidService.ReaderThread`, etc.) and the `Bin2MslConverter` `.msl` header-string documentation (`docs/DEVELOPER_GUIDE.md:626`) untouched (KTD3, KTD4).
5. Re-check each file's Table of Contents / internal anchor links after editing.

**Patterns to follow:** none — plain-text Markdown editing, no code involved.

**Test scenarios:**
- Covers R2. Every Table-of-Contents link in `docs/USER_GUIDE.md` resolves to its target heading after the rename.
- Covers R2, KTD3, KTD4. A diff review confirms every changed line is product-name prose, and every `EcmDroidService`/`EcmDroidApp`/`DBHelper` class-name reference, every `ecmsim` project mention, and the `Bin2Msl` header-string documentation line are byte-identical to before this unit.
- Test expectation: none beyond doc review — a text-only edit with no runtime behavior.

**Verification:** rendered Markdown preview of all four files shows "BuellTune" throughout product-name prose, all Table-of-Contents links resolve, and no excluded reference (class names, `ecmsim`, `.msl` header string) was altered.

---

## Scope Boundaries

**Out of scope (non-goals):**

- Renaming the Java/Kotlin class names `EcmDroidService`, `EcmDroidApp`, `DBHelper` (or any other `EcmDroid*`-named class) — a source-level rename touching every call site and test, not a docs/branding change (KTD4).
- Changing the `.msl` log-header literal in `Bin2MslConverter.kt` — would break the Kotlin-foundation plan's byte-identical-output contract (R5) and the committed `BUE2D_log.msl` fixture; a MegaLogViewer-format-identity decision, not branding (KTD3).
- Play Store / F-Droid listing graphics (feature graphic, screenshots, store-description copy) — distribution remains a later milestone (KD3).
- App theme/Material color changes (`colorPrimary`/`colorAccent` in `colors.xml`) — a deliberate palette decision independent of the icon artwork, not requested here.
- Per-screen Compose UX migration, legacy Fragment/`AsyncTask` removal, and the Kotlin-foundation plan's other follow-up items (`suspend` conversion, singleton-facade deletion, SQL parameterization, preference-key consolidation) — unrelated to branding; tracked in that plan's own "Deferred to follow-up work."
- `third_party/ecmsim` (vendored submodule) and its own `README.md` — external project, not touched.

---

## Dependencies / Assumptions

- Foundation work is complete: all 14 Kotlin-foundation implementation units (`buelltune-0y8` through `buelltune-izj`) and the display-name/About-screen slice (`buelltune-xzm`) are closed in `bd`, confirmed against `git log`.
- No new build-tooling dependency is required — Android Studio's Image Asset Studio (or an equivalent icon generator) is a design-time tool, not a Gradle dependency.
- The new artwork's actual visual design (motif, palette) is not decided by this plan — see Deferred to Implementation.

## Deferred to Implementation

- The new icon's exact visual motif and color palette — a creative decision made when the artwork is actually produced, not at planning time.
- Whether the new palette reuses today's default `colorPrimary`/`colorAccent` scaffold values or diverges from them (U1 approach step 1 notes reuse as a safe default, not a requirement).

## Sources / Research

- `docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md` — origin plan; its Scope Boundaries deferred "Visual rebrand: app icon... docs/USER_GUIDE.md's product naming, and other BuellTune branding/UI-copy work" and its "How This Work Fits Together" section frames visual rebrand as a track independent of that plan.
- `bd list --all` — confirms all 14 Kotlin-foundation units plus `buelltune-xzm` (display name + About-screen attribution) are closed; `bd ready` returns no open issues.
- `app/src/main/assets/about.html` — read directly: still credits "EcmDroid Launcher Icon Copyright (C) 2012 by Lutz Gebhardt"; `app_name` and the nav-drawer header already read "BuellTune" (confirms `buelltune-xzm` scope was display name + About text only, not the icon).
- `app/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png`, `app/src/main/res/drawable/ecmdroid.png` — read directly: no `mipmap-anydpi-v26/` exists, confirming no Adaptive Icon today.
- `app/src/main/AndroidManifest.xml` — `android:icon="@mipmap/ic_launcher"`, no `android:roundIcon` declared (KTD2).
- `docs/USER_GUIDE.md`, `docs/DEVELOPER_GUIDE.md`, `README.md`, `AGENTS.md` — read directly for exact line references; `app/src/main/java/biz/logicminds/buelltune/util/Bin2MslConverter.kt:253` and `app/src/androidTest/resources/BUE2D_log.msl:1` read directly to confirm the `.msl` header literal is real code output, not documentation prose (KTD3).
- `app/src/main/res/values/colors.xml` — `colorPrimary #3F51B5`, `colorPrimaryDark #303F9F`, `colorAccent #FF0000` — the current (default Material scaffold) palette, noted as a non-binding reuse option, not a new requirement.
