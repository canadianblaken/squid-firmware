# SQUID investigation review — 2026-10-11

Update 2026-10-12: the proposed continuation succeeded. See [note-output-path.md](note-output-path.md) for the recovered MIDI formatting and CV/Gate register-write paths. This review is retained as the preceding checkpoint.

Scope: reviewed the saved extraction notes, updater report, derived ELF builder, Ghidra traces, HTML guide and feature-request reports. Cross-checked manufacturer documentation online and independently rechecked the local payload bytes. This was a review of existing analysis, not a complete re-disassembly or hardware execution.

## Published behavior

The manufacturer’s [Operating Instructions](https://files.kraftmusic.com/media/ownersmanual/Pioneer_DJ_Toraiz_Squid_Operating_Instructions.pdf) (original edition, mirrored by Kraft Music) already answers several questions:

- Page 48: ordinary note GATE spans 0.125–64 steps.
- Page 143: Arpeggiator Gate Length spans 1–100% of the Rate interval. Rate, pitch-order Style and octave Range are separate settings.
- Pages 103 and 144: TRIG PROB suppresses playback triggers probabilistically; SHIFT + TRIG PROB edits its rate. This does not establish independently editable probability values per step.
- Page 138: the Enable/Mute/Stop-Pause option table is Step Preview.

These documented behaviors do not require a new device experiment to understand. A connected synthesizer’s release envelope can extend the audible sound beyond a note’s gate duration; the firmware timer alone does not describe that sound tail.

The [official change history](https://downloads.support.alphatheta.com/firmwares/music-production/TORAIZ_SQUID/TORAIZ_SQUID-Firmware-Change-History-Ver113-EN.pdf) dates v1.13 to August 22, 2024 and describes a power-management change. It also records v1.11 fixes for Arpeggiator CV/Gate and Mono behavior. The embedded 2018/2019 metadata is not the v1.13 release date. This history alone does not establish an official end of support.

## What holds up

- Windows and Mac firmware payloads match: SHA-256 `55fadd87edaa9157b8cfd1055103b0862ec1774b91f6df2b753e96f217cd866f`.
- Recomputed size is 2,097,152 bytes, version is `1.13`, and CRC-16/0x1021 with initial zero is `0x73FA`, matching the stored little-endian trailer. This checks consistency, not publisher authenticity.
- The mapped application image and instruction evidence support big-endian SH-2A/FPU. The exact chip remains unconfirmed.
- The saved getter/setter trace supports Gate Length at object offset +0xE16 and bounds 1–100. The timer calculation appears at 0x1C000C5A. Independently reread literal bytes confirm divisor 100.0 and timer base 0xFFF8D7B0.
- Local Ghidra has already produced SuperH disassembly and pseudocode. REA rejected the derived ELF, but that is not evidence that another disassembler or hardware access is required.

## Corrections to the analysis

1. **The stopping rationale was premature.** An unresolved output path is unfinished analysis. The saved state references provide additional offline leads.
2. **Rate was mislabeled Style.** UI rows/getters in `ghidra-gate-trace.txt` map +0xE18 to Rate, +0xE1C to Style, and +0xE15 to Range. The eight-way timing dispatch agrees with the Rate menu.
3. **Expiry and next-event scheduling were conflated.** In `runtime-path.txt`, 0x1C00091A clamps a negative countdown and clears a mask. Later boundary/rate checks are separate conditions. The branch is `< 0`, not `<= 0`, in the recovered pseudocode. The second argument selects timing state; it is not itself the elapsed float.
4. **The timing helper’s indirect targets are resolved.** Literal pointers at 0x1C000C30 and 0x1C000E6C both name 0xFFF8B71A. Calling them unresolved hardware callbacks was misleading.
5. **Pseudocode is imperfect.** The recovered 0x1C000B7E signature omits a floating-point argument visible at its caller; suspicious shift expressions also appear. Critical branches and register arguments need instruction-level confirmation before exact behavior is asserted.
6. **Missing strings do not prove missing features.** The feature reports overstated what a label sweep could establish. Documentation resolves the Trigger Probability and Step Preview labels without proving a hidden implementation.
7. **RAM coverage is incomplete.** `build_ghidra_app_elf.py` maps populated slices but omits uninitialized runtime objects and arrays. Missing RAM definitions can obstruct useful references and type recovery. Their bounds must be supported before adding them.

## Concrete offline continuation

1. Work in a preserved copy of the local Ghidra project; keep the original firmware unchanged.
2. Validate the timer branch, SH shift semantics and floating-point call arguments against instructions. Define referenced RAM blocks with documented provenance and provisional boundaries.
3. Follow the active mask at 0xFFF8D7AC: 0x1C00091A clears it and 0x1C000740 reads it before generating note masks at 0xFFF8E130 and 0xFFF8E140.
4. Trace consumers of those output masks through the callback sequence in 0xFFF8942C toward MIDI/CV emission. This shared-state path is a more concrete lead than expecting a direct timer-to-note-off call.
5. Use the manuals to classify further feature questions first. Reserve device capture for a specific undocumented behavior or for corroborating a recovered output path.

No new hardware blocker was established. The exact physical output functions and exact MCU remain open questions.

## Corrections saved

- `firmware-map.md`: corrected Rate/Style, timer control flow, tool limitation and continuation.
- `../feature-requests.md` and `feature-request-firmware-check.md`: narrowed absence claims and resolved documented labels.
- `~/toraiz-squid-firmware-analysis-guide.html`: replaced the misleading stop/expiry description and linked this review.

Raw traces were retained as evidence, including their decompiler errors. No firmware, updater or device operation was executed.
