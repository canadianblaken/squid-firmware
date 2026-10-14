# v1.13 firmware vs. feature-requests.md

Static only: string sweep of the full 2 MiB image (`trzis1__v113.bin`), pointer/literal-pool
mapping (runtime = file − 0xB0000 + 0x1C000000), and headless Ghidra 12.1.4 decompilation of
the application image (`TraceFeatureStrings.java`, `TraceFeatureFuncs.java` →
`feature-strings-trace.txt`, `feature-funcs-trace*.txt`). Nothing executed. Not REA Evidence
(REA's Ghidra provider rejects the SuperH ELF).

Method limit: this is a bounded string and function search. Features can use button combinations, shared labels or no dedicated label. Missing strings do not establish that a feature is absent, exposed or dormant. See review-and-next-steps.md for documentation corrections.

## No matching dedicated labels identified in this search

Whole-image case-insensitive search found no label for any of these:

- Song mode / playlist / arrangement / pattern chain (`song`, `chain`, `playlist`, `arrang`: 0 hits)
- Pattern Set chains, or more pattern sets (Pattern Set UI shows `Set No.`, `(Entering)`, `(Exiting)` only)
- Performance page, CC learn/assign, remote CC→parameter mapping (`perform`, `learn`, `assign`, `macro`: 0 hits).
  MIDI IN labels cover sync, Start/Stop, Rec Channel and THRU only.
- Global swing/groove. The `GLOBAL PITCH GATE VELO STEP DIVIDE SWING MODULATI SCALE SHAPE` row
  (file 0xF2384) is used only by FUN_1c01d47a, which also loads `ENCODER CHECK`. It's the
  factory test's names for the 10 encoders, not a feature.
- Exponential CV interpolation. `Interpolation` labels exist per modulation lane (CV SLIDE,
  Pitch, Velocity …) but there's no curve option. `Linear` belongs to the Velocity Curve table
  (Soft 1–3 / Linear …, pool at 0x1c0230c0).
- Track presets/templates, drum mode, microtiming randomization, polyphonic overdub, rekordbox sync
- Seamless project switching. The project manager only has `Loading` / `Unsaved progress will be lost.` strings.

## Already present (partially answers a request)

- Copy/paste exists: `Paste` / `Delete` / `History` in the recorded-parameter editor
  (FUN_1c00c2a4) and `Shift+RSPush:Dup` (FUN_1c036458, track-settings list). No pad-level quick copy label.
- `Fixed Length` record popup (FUN_1c033d0a) plus `%2d.%02d Steps` / `bars` length display. The
  4-bar maximum wasn't confirmed in code.
- Capacity strings: `Projects : %d/32`, `Patterns : %d/1600` (system info). The per-track
  pattern cap from the YouTube review wasn't verified.

## Documentation resolves two former leads

- `Trigger Probability Rate` is documented playback trigger suppression, accessed with SHIFT + TRIG PROB (manual pages 103 and 144). It is not evidence of a hidden feature or of independently editable probability values for individual steps. The popup code connection is still a separate static tracing question.
- `Disable / Enable / Ena(Mute Cur Tr) / Ena(Mute All Tr) / Ena(Stop/Pause)` is the documented Step Preview setting (manual page 138). The earlier transport-output interpretation was incorrect; its firmware builder remains incompletely decoded.

Source: [Pioneer DJ Operating Instructions, manufacturer-authored PDF mirrored by Kraft Music](https://files.kraftmusic.com/media/ownersmanual/Pioneer_DJ_Toraiz_Squid_Operating_Instructions.pdf).
