# BuellTune — Deferred / Unscoped Work

Tracks items explicitly deferred out of landed plans that are **not yet ready for `bd`
tracking** — no plan, no concrete acceptance criteria, ownership/scope still undecided.
Once any item below gets scoped into a plan, move it to `bd` (see
`docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md` for the `bd`
issue shape/conventions to follow) and delete it from this file.

Mechanical follow-up cleanup with clear acceptance criteria is tracked in `bd` under the
`kotlin-foundation-followup` label (`bd list --label kotlin-foundation-followup`), not here.

## Screen migration & legacy removal

- **Per-screen Compose migration** — Setup, DataChannels, EEPROM editor, TroubleCodes,
  ActiveTests, Log, TorqueValues. Each can proceed independently once the Kotlin
  foundation plan's transport/domain/Room layer and Compose shell exist (they do — all
  landed). No plan exists yet for any individual screen.
  Source: `docs/plans/2026-08-30-001-refactor-kotlin-foundation-compliance-plan.md` §
  "How This Work Fits Together" and Scope Boundaries.
- **Legacy Fragment/Preference/AsyncTask removal** — deleting `android.app.Fragment`,
  `android.app.ListFragment`, `android.preference.*`, and the remaining `AsyncTask`
  subclasses (`ProgressDialogTask` + descendants once `FetchTask`/`BurnTask` migrate,
  tracked separately as `buelltune-02d`). Depends on every screen above being migrated
  first — unsafe to remove while any screen still depends on it.
  Source: same plan, Scope Boundaries.

## Distribution

- **Play Store relisting** under the new `biz.logicminds.buelltune` applicationId — a
  fresh listing, not a relisting of the delisted `org.ecmdroid` app. Depends on this
  repo's compliance work (already landed: API 36 target, real foreground service), but
  is a distribution-channel decision, not engineering scope. Still to decide: who owns
  Play Console access.
- **F-Droid publishing** (upstream issue #20). Same ownership gap.
  Source: same plan, Key Decisions (KD5) and "How This Work Fits Together".

## Untriaged upstream issues

Never scoped into any landed plan — out of scope for compatibility/compliance work,
not yet assessed for relevance to the BuellTune fork:

- [ecmdroid/ecmdroid#13](https://github.com/ecmdroid/ecmdroid/issues/13)
- [ecmdroid/ecmdroid#4](https://github.com/ecmdroid/ecmdroid/issues/4)
- [ecmdroid/ecmdroid#3](https://github.com/ecmdroid/ecmdroid/issues/3)
- [ecmdroid/ecmdroid#24](https://github.com/ecmdroid/ecmdroid/issues/24)

## Explicitly out of scope (not deferred — decided against)

Listed here only so they aren't re-proposed without a fresh product decision:

- `values-night/` dark theme — none exists today, none requested
  (`docs/plans/2026-09-01-002-feat-visual-rebrand-theme-screens-plan.md` KTD2).
- Recoloring `ColorMap.kt`'s procedural gauge/EEPROM heatmap — a data-legibility
  encoding, not decorative branding (same plan, KD3).
- AppCompat → Material3 theming migration — separate initiative, unrelated to the
  palette/color-literal pass (same plan, Scope Boundaries).
