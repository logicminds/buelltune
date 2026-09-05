# Residual Review Findings — `feat/byte-link-refactor`

Stage 1 (U1–U3) of `docs/plans/2026-09-05-001-refactor-kmp-core-extraction-plan.md`.
Review run: three parallel reviewers (concurrency, protocol fidelity, test quality)
against commit `f9f2bcd`.

P1 findings were fixed in `dc9ab26` and are not listed here. The items below were
filed as beads rather than fixed in this PR.

## Filed

- **P2 — `PduFraming.kt:89`** — Header validity check uses `&&` where `||` is
  intended. **Pre-existing**, byte-identical in the pre-refactor file, so out of
  scope for this refactor. A frame with a corrupt SOH but intact EOH/SOT passes
  the early gate and the reader over-consumes before `PDU.validate()` rejects it.
  No bad PDU is accepted. → `buelltune-8ob`
- **P3 — `PduFraming.kt:86`** — `Carry` is per-call, so a delivery spanning the end
  of a frame loses its tail. Unreachable today (one outstanding PDU, enforced by
  each transport's `Mutex`); becomes live if any unsolicited or streaming frame is
  introduced. → `buelltune-167`
- **P3 — `PduFraming.kt:~140`** — `drain()` only sees chunks the pump already
  forwarded, not bytes still in the OS receive buffer as the old `available()`
  loop did. Bounded because every transport tears down on `IOException`. → `buelltune-6pp`

## Noted, not filed

- **Unbounded channel removes link-layer backpressure.** The old design left unread
  bytes in the kernel receive buffer, so TCP/RFCOMM flow control throttled a fast
  peer. The pump now reads eagerly into `Channel.UNLIMITED`. Bounded in practice by
  the strict request/response protocol — the ECM is silent between transactions.
  Recorded as a design property, not a defect.
- **Timeout/EOF messages lost some byte-progress context.** Partially restored in
  `dc9ab26` (the timeout message again reports bytes-read-of-expected and offset).
  The EOF path still reports less than the old `"EOF while reading $toRead/$len
  bytes at offset ..."`.
