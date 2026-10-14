# SQUID firmware investigation — resume note

Updated 2026-10-13. Read this file first after clearing chat context. Latest focused transport trace includes 165 functions and is in `analysis/midi-transport-trace.txt`.

Code-cave safety (2026-10-13, Claude): `code-cave-safety.md`. SAFE. Cave 0x1C045608-0x1C04CFFC is the 0xFF tail of the flash->RAM code copy (app loader file 0x90000 copies 0xB0000->0x1C000000 len 0x4CFFC, then jumps into it at 0x1C016CEE); executed from on-chip RAM, not XIP. No references into [0x1C045608,0x1C04CFFF); BSS/heap starts at 0x1C050000+ (lowest ptr 0x1C04E000). Use 0x1C045610-0x1C04CFFC (16-aligned, ~0x79E0 B); hook via mov.l pc-literal + jmp @rN (bra too short), mind delay slot + pc-relative literals. patch_image.py byte-sum layout CONFIRMED correct (updater run 0xd8e1 cmovge 0x20000 per-region; sum loop 0xe320-e37e = plain byte-sum); no fix made, self-check passes.

Gate Hold mapping is now supported by static behavior: scanner index `2F` (state byte `1C06CB61`) is the middle of three speed controls. UI event `-0xA27`, argument 2, reaches per-track speed state `FFF8EB40+track` through `FFF8C114/FFF8C134`. `FFF8C460` gives states 1/2/3 half/zero/double timing advance. Release to state 0 requests restoration from the separate baseline timing record. See `gate-hold-control.md` for addresses, conditions, evidence, and confidence.

## Immediate handoff after model/context change

The normal sequence timer is now traced: `FFF8BC14` schedules into the shared active-note/timer arrays; `FFF8BF3A` uses per-track advance `FFF8E8E8+12*track` when its third argument is nonzero. Gate Hold's zero advance stalls normal sequence and Arpeggiator countdowns. Explicit release events in `FFF8378C` and `FFF89340` still call `FFF88D1C` directly; final output depends on downstream mask processing.

The global timer `FFF8CC18` belongs to an alternate service activated through `FFF8EBCC`. Mode word `FFF8EBC8` skips normal sequence/timer processing, and the `FFF8CB80` return value selects a direct-mask branch in `FFF8942C`, bypassing the normal transform/Arpeggiator chain. Its user-facing feature and compatibility with Gate Hold remain open. See the note-source boundary section of `gate-hold-control.md` for conditions and SH evidence.

Next: identify that alternate mode from setter `1C034944`, UI callers `1C03943C/1C03946C`, and dispatcher `FFF83D28`; determine whether Hold and this mode can coexist. Reuse saved traces to minimize token cost. The previous model recommendation remains recorded as a workflow suggestion; no configuration was changed. No tests or device operations were run. Physical USB/DIN completion remains unverified.

Alternate-mode continuation (2026-10-13, Claude): saved in `hold-alt-mode.txt` (InspectFocused, TRACE_COMPLETE). `FFF8EBC8` is set by `1C034944(0/1/2)` (0 also writes 0xFF to `1C06C6A5`) and directly at `1C034E74`. `FFF8EBCC` is the separate activation flag, written by `1C034A1C(0/1)` (gated by `*DAT_1C034AB4`; arg 0 restores a 12-byte state copy and sets 0xFF markers) and `1C03444C`. UI `1C038C78` toggles `EBC8` 1->2 (at `1C03943C`) and 2->1 (at `1C03946C`) on press events, skipping codes `10000001`/`40000001`. `1C034954` accepts a 0..15 index (a 16-pad selection) only when `EBC8==1`, stores it at `1C06C6A4/A5`. `FFF83D28` reads selector `FFF8EBD8` (0/1/2; written by UI `1C03A418` at `1C03B0E4`=1 and `1C03B12A`=2): state 1 calls `1C034944(1)`, state 2 calls `1C034A1C(0)` then `1C034944(0)`. Feature name still unidentified (a 16-pad selection mode such as Pattern Set fits the shape but is unproven). No examined branch in these paths tests Hold state `FFF8EB40`, so coexistence is not excluded and not shown. Next: identify which button codes drive `1C03A418` writes to `EBD8` (needs the panel index map), then decide.

Time Warp identification (2026-10-13, Claude): alternate mode `FFF8EBC8/FFF8EBCC` = **TIME WARP** (medium-high, inferred from behavior+manual 3302-3350). `EBD8` is a UI-mode transition latch in `1C03A418` (`1C03B09C-1C03B12A`): 1 on entry into mode family {1,10001,10000001}, 2 on leaving (incl. to pending-exit 40000001). Scanner index `00` press requests mode 1 (`1C038F2A`); index `0C` toggles 1<->10001 / EBC8 1<->2 (PATTERN save sub-mode, inferred). Gate Hold: speed-button `-0xA27` dispatch is gated off in mode-1 family (`1C03A516-1C03A51C` clears r8; `1C03A7FA` requires it) and normal per-track timing is skipped while EBC8!=0, so they do not functionally coexist; post-exit Hold state unresolved. Details: `gate-hold-control.md` last section.

Time Warp vs Hold state (2026-10-13, Claude): only `FFF8C134` and boot init `1C033E40` write `FFF8EB40`; TW entry/exit (`EBD8`, `1C034944`, `1C034A1C`, `FFF83D28`) does not (high). `-0xA27` is produced only in `1C03A418` (args 1/2/3 at `1C03A828/83A/84E`, release 0 at `1C03A886`); a second clear event `-0xA26` (arg 0 -> `FFF8C134`) comes from `1C03720C`, called by pad/track-select handler `1C0372DE`; whether a TW pad press reaches it is unresolved (low-medium). Hold state after exit is preserved, not reset (medium-high); `FFF842D0` baseline restore runs during TW; `1C034A1C(0)` restores only global `FFF8E450`. Details: `gate-hold-control.md` last section; traces `hold-timewarp-reset.txt`.

Model routing (cost): do mechanical work (headless Ghidra runs via InspectFocused.java, grep/sed of saved traces, appending notes here) on a cheap model (Haiku/Sonnet-class, low reasoning). Switch up (Opus/Fable-class or gpt-6-astra high) only for branch-by-branch panel-handler tracing, i.e. mapping button codes to `1C03A418` writes of `FFF8EBD8` and the `0xE` scan/UI index offset. Drop back down once the finding is saved.

## Gate Hold trace: status (2026-10-13) — CLOSED statically

Summary of the static result (all from saved/read-only traces; no device or firmware operation; hardware behavior untested):
- Gate Hold = scanner index `2F`, middle of the 1/2X, Gate Hold, 2X group. UI event `-0xA27` arg 2 sets per-track speed state `FFF8EB40+track`; `FFF8C460` maps states 1/2/3 to half/zero/double timing advance. Release restores the baseline timing record.
- Hold stalls elapsed-time expiry of normal sequence and Arpeggiator notes (per-track clock). Explicit release events still clear source note bits via `FFF88D1C`.
- Alternate mode `FFF8EBC8/FFF8EBCC` is very likely TIME WARP (index `00` button; index `0C` likely PATTERN sub-mode). Medium-high confidence; labels inferred from the manual (lines 3302-3350) and code shape.
- Coexistence: Hold presses are blocked while in Time Warp (flag cleared at `1C03A516`, checked at `1C03A7FA`). A Hold set before entry is preserved and inert; no entry/exit path clears it. Pad presses in Time Warp almost certainly cannot emit `-0xA26` (only exact mode `0x10001` reaches `1C0372DE`, and it drops new presses then).
- Remaining low-probability edges: `1C06CBB8` track-offset path, pad-function-4 release, a transition branch in `1C0372DE`; whether `FFF8E8E8` is restored after Time Warp exit; physical USB/DIN/CV completion timing.
- Main-session spot-check (SH listing in `note-output-trace.txt`): `1C03A47C-48A` reaches the pad handler only for mode word exactly `0x10001`; `1C03A516-51C` clears `r8` when the mode low half is 1 (modes `1`, `0x10001`, `0x10000001`); `1C03A7FA` skips the speed-button block when `r8==0`; no `r8` write between them (coarse grep). Hold presses are therefore blocked in every low-half-1 mode, including the PATTERN sub-mode. Not re-checked: `r8` before `1C03A516`, `FFF8EBD8` writes, the Time Warp identification (still inferred).

Next candidates if work continues: device-side confirmation of Time Warp vs Hold interaction (needs a specific measurement plan), or move to another firmware area.

## Goal and current state

Reverse engineer the TORAIZ SQUID v1.13 firmware under `.` to understand it and identify further possible work. Firmware payloads have matching hashes and validated CRC. REA extracted the firmware but its Ghidra provider rejected the SuperH-derived ELF as an unsupported architecture. Local Ghidra 12.1.4 headless works; no device or firmware was modified.

Gate Length is a 1–100% Arpeggiator setting, stored at channel/track object offset `+0xE16`. Runtime code scales the time to the next Rate boundary by `GateLength/100`. The traced chain now reaches output formatting and CV/Gate hardware register writes:

```
1C000C5A schedules gate countdown
1C00091A expires it and clears channel state
1C000740 / FFF8A808 / FFF8D0EC / FFF88AB8 rebuild note masks
1C035484 publishes desired notes
FFF88E28 finds removed notes
FFF891BC queues release at velocity 0; also enqueues a timestamped event
FFF8901A drains the delay queue
FFF87324 dispatches by output type
1C0177FA / 1C017930 format MIDI 1.0 / USB-MIDI events
1C0179D8 -> FFF8678E clears CV/Gate source state
  FFF861EA combines gate sources/polarity and writes FFFE3876 bit 4,
  FFFE3877 bit 7 (address matches Port D data register PDDR0)
```

Gate expiry clears this track's contribution. A second gate source and output polarity affect whether the physical gate edge changes immediately. The register address is PDDR0 on documented SH7264 and SH7269 maps; that common address does not identify the exact chip. If this mapping applies, the big-endian byte writes select PD12 and PD7. SQUID connector wiring and exact physical output timing remain open. USB output has a static queue handoff, endpoint submission, and a configured runtime vector/status dispatch to `FFF87AB6`. DIN output has a static ring reader and hardware data-register feed. Whether the observed status transitions mark actual bus/wire completion requires runtime evidence.

The release helper also puts a timestamped copy into a separate 128-entry, 8-byte ring at `FFF8ECDC`, gated by global `FFF8E440 == 1`; a full ring drops the event. `FFF8CA64` copies a current timestamp and event fields from shared state `FFF8F308` into each record, and advances a 7-bit producer index. Consumer `1C034C30` drains it and dispatches records to `1C034724` (flag byte +6 is zero) or `1C034602` (nonzero). Release records use the first handler. `1C034724` matches a track/note slot using a bitmap, examines per-step timing state at track-object offset `+0x28e`, and may update it through `1C032FA4`. The directly examined handlers do not call the MIDI/CV/Gate routing service; whether this shared track-object state later feeds that service remains unresolved.

The timer-active-note → release → output question is now statically closed. `FFF88C30` sets per-track active-note bits in `1C067060` and timer state in `1C068360`; `FFF8CC18` checks timers and calls `FFF88D1C` on expiry. `FFF88D1C` clears the bit in `1C067060` and associated mask `1C067160`. The loop at `FFF83288` calls `FFF8CC18` at `FFF8335C`, then `FFF8942C`, which compares current note masks with snapshot `1C066F60`, runs `1C000740 → FFF8A808 → FFF8D0EC → FFF88AB8`, and publishes desired notes with `1C035484`. It then calls `FFF88E28` to diff output notes, followed by `FFF8901A` to drain velocity-zero releases into output records routed by `FFF87324`. CV/Gate then follows `FFF8678E → FFF861EA` to update source state and hardware register bits. The loop also calls `FFF8897A`; that helper's separate event queue populates record offset `+0x23`, which `1C017844` formats as MIDI Control Change, so it is not the note-off route. The loop's caller is vector-`0xB0` handler `FFF87718` under its state/counter branch; real-time frequency remains to be established.

## User supplied control reference

`./squid-interactive-manual.html` is the user's recreation of the manual. It is a useful control/feature index: it describes Gate Hold as sustaining the currently sounding notes while sequence position continues, and identifies CV/Gate routing, gate mode/polarity, and Arpeggiator Gate Length controls. Its own scope notes say MIDI/CV output is not implemented and Arpeggiator is display-only, so treat it as manual-derived UI behavior, not firmware/runtime evidence. `FFF8E440` is a 0/1/2 transport-mode state changed by `FFF8B1B4` and consulted by note/arp routines; transition helpers format MIDI Start, Stop, and Continue under routing conditions. No physical button is mapped to this state, and it is not identified as Gate Hold. The manual's Gate Hold behavior remains a behavioral reference.

## Key files

- `note-output-path.md` — current finding, evidence table, caveats and reproduction command.
- `note-output-trace.txt` — Ghidra decompiler and instructions for current default note/transport targets; ends in `TRACE_COMPLETE`.
- `midi-transport-trace.txt` — focused ring initialization, DIN enqueue wrapper, USB-MIDI staging/consumer, endpoint submission and callback registration/release trace; static evidence only.
- `transport-window-inspection.txt` / `InspectTransportWindows.java` — bounded read-only Ghidra listing and decompilation of USB callback readers, vector registration and the interrupt handler that calls the DIN consumer.
- `ring-reader-inspection.txt` / `InspectRingReaders.java` — bounded read-only Ghidra decompilation and assembly of the DIN read-index and hardware writer path.
- `callback-loop-inspection.txt` / `InspectCallbackLoop.java` — corrected `FFF83288` note-processing loop entry, function body, and call evidence.
- `gate-hold-control.md` — current panel-to-speed-state mapping, timing/release behavior, and remaining note-source boundary.
- `gate-hold-inspection.txt` / `InspectGateHold.java` — read-only inspection of the previously untraced mask accumulator `FFF894DA` and active-note snapshot `FFF8940C`; neither identifies the button state.
- `mode-caller-inspection.txt` / `InspectModeCallers.java`, `mode-caller-window.txt` / `InspectModeCallerWindow.java` — transport state transition helpers and their callers, including MIDI Start/Stop/Continue formatting.
- `loop-gate-inspection.txt` / `InspectLoopGate.java`, `control-event-inspection.txt` / `InspectControlEvents.java` — bounded candidate-state and event-record reference inspections.
- `panel-state-inspection.txt`, `panel-array-inspection.txt`, `panel-scan-inspection.txt`, `panel-map-inspection.txt`, `panel-dispatch-inspection.txt` and matching `InspectPanel*.java` scripts — read-only button-state base, scanner, call-order, and map-initialization evidence; the later speed traces establish the behavior-backed Gate Hold mapping.
- `InspectFocused.java` / `speed-control-inspection.txt`, `speed-state-xrefs.txt`, `speed-state-consumers.txt`, `hold-timing-inspection.txt`, `hold-timer-bridge.txt` — focused speed state, timer, and release-restoration evidence.
- `hold-source-inspection.txt`, `hold-note-timers.txt`, `hold-route-selectors.txt` — normal/alternate mask processing, explicit note releases, and mode selectors.
- `TraceNoteOutputs.java` — read-only headless tracing script with default queries.
- `firmware-map.md` — image memory map and broader investigation ledger; a few earlier narrative paragraphs preserve the previous pass, so its latest status is in `note-output-path.md`.
- `review-and-next-steps.md` — online/manual and prior-work review.
- `../squid-interactive-manual.html` — user-supplied manual recreation; useful control index, not device-output evidence.
- `~/toraiz-squid-firmware-analysis-guide.html` — user’s walkthrough artifact, updated to link the output-path report.
- `squid-v113-application.elf` and `build_ghidra_app_elf.py` — derived analysis input; not official firmware.

## Ghidra state

Installed at `~/.local/share/ghidra/ghidra_12.1.4_PUBLIC`. Current temporary project at `/tmp/squid-ghidra/SQUIDv113.gpr` (about 7.8 MB). The last `analyzeHeadless` run opened it successfully in `-readOnly` mode and discarded project changes afterward. If `/tmp` has been cleared, rebuild the ELF if needed and create/import the project before replaying the trace. The analysis script and output under `analysis/` are persistent.

Last successful command:

```sh
~/.local/share/ghidra/ghidra_12.1.4_PUBLIC/support/analyzeHeadless \
  /tmp/squid-ghidra SQUIDv113 -process squid-v113-application.elf \
  -noanalysis -readOnly -scriptPath ./analysis \
  -postScript InspectLoopGate.java
```

## Next work, in order

1. The transmit-consumer questions are now statically resolved to hardware handoff. USB: `FFF88266` stages packets at state `0x10`; `FFF8812C` moves eligible slots to `0x20`; `FFF87A20` submits payload via `FFF82104`. Registration `1C01F964 -> 1C01F830` stores endpoint-4 submitter `FFF87A20` at `FFF8DFC4` and callback `FFF87AB6` at `FFF8DFEC`. The direction-0 status dispatcher `FFF87D3E` reads the callback base at `FFF87D94`, indexes endpoint 4 at `FFF87D9A`, and calls the registered callback at `FFF87DAA` on a matching status bit. Runtime initialization `1C024378` moves VBR to `1C04E000`; record `1C04235C` associates vector `0x80` with trampoline `FFF878E4 -> FFF87D3E` through `1C01C64E -> 1C02D032`. The separate readiness/submission routine starts at `1C02014A`; `1C0201FC` is only its call to `FFF8812C`. DIN: `FFF8526C` reads descriptors `FFF8D9A0/FFF8D9AC`, `FFF8515C` tests status and writes the serial data register, and `FFF85220` advances the read index after accepted writes. Handler `FFF87718` calls the DIN consumer for both channels at `FFF8787A–FFF87882`; record `1C042334` associates it with vector `0xB0`. On-wire timing and independent UART wire-completion signaling remain unknown. Evidence: `note-output-path.md`, `midi-transport-trace.txt`, `transport-window-inspection.txt`, and `ring-reader-inspection.txt`.
2. The note-processing loop's true entry is `FFF83288`; its body spans `FFF83288–FFF83425` and `FFF83494–FFF83635` around a literal pool. The older `FFF83386` label was interior. Vector-`0xB0` handler `FFF87718` calls it through literal `FFF87920` on a state/counter branch, and later calls the DIN consumer on another branch. `callback-loop-inspection.txt` records the assembly and read-only decompilation. The counter's real-time period and other task scheduling remain open.
3. Identify the exact MCU by comparing additional peripheral addresses with official Renesas hardware manuals. Treat SH-2A/FPU as the supported ISA; PDDR0 address alone is shared by at least SH7264 and SH7269.
4. Gate Hold now reaches both normal sequence and Arpeggiator countdowns. Explicit releases can still clear source bits. Identify the alternate global-clock mode (`FFF8EBC8/FFF8EBCC`) and whether it can coexist with Hold; see the focused entry points above.
5. If needed, map demo project at firmware file offset `0x190004` and remaining uninitialized RAM separately from the current ELF code/data slices.

## Review priorities and alternate avenues

- The original bootstrap vector table at file `0x1800`, runtime VBR switch at `1C02439A`, USB callback reader, and DIN consumer are now traced. The next static transport work is to identify exactly which USB status bits in `FFF8DFAC/FFF8DFB0` denote completion and whether any SCI wire-completion interrupt follows the data-register write. Keep hardware acceptance distinct from on-wire completion.
- For the requested recreation, model the already established Gate Length → desired-note mask → release queue → MIDI/CV/Gate route as explicit state transitions. Scheduling and physical connector behavior remain separate unknowns; the static route is enough to implement a behavioral prototype once output requirements are chosen.
- The callback loop's entry and interrupt-service caller are now identified. Next identify the alternate global-clock playback mode and its compatibility with Gate Hold. Exact MCU and connector pins are lower priority until output behavior needs hardware validation.

## Analysis cautions

- Decompiler pseudocode has some zero-shift errors; validate important branches and arguments against SH assembly.
- Gate Length is +`0xE16`; +`0xE18` is Rate; +`0xE1C` is Style; +`0xE15` is octave Range.
- Static output path is derived evidence, not a runtime capture. Do not say the physical output always falls exactly at timer expiry.
- Use `cat ~/knowledge/index.md` at the start of a new session and keep updates brief. Do not run tests unless asked.

## Prompt to continue after clearing context

```text
Continue TORAIZ SQUID v1.13 firmware reverse engineering in .. Read analysis/RESUME.md, analysis/note-output-path.md, and analysis/gate-hold-control.md first. Reuse the existing read-only Ghidra project and saved traces. The static note-release route and USB/DIN hardware handoff are traced; physical transfer completion remains unknown. Gate Hold is mapped to scanner index 2F and per-track speed state 2; its zero advance reaches the Arpeggiator countdown, and release restores baseline timing. Normal sequence timers also use the frozen per-track clock; explicit release events still clear source bits. Next identify alternate mode FFF8EBC8/FFF8EBCC through setter 1C034944 and its UI/dispatcher callers, and determine whether it can coexist with Hold. Minimize token use and reuse saved traces. Do not equate transport state FFF8E440 with Gate Hold without a button mapping. Keep observations, inferences, and unknowns separate. Do not modify or flash firmware or use the device; no tests unless requested. Goal: understand/recreate behavior so functionality can be added.
```

TW pad vs `-0xA26` (2026-10-13, Claude): resolved (medium-high). `1C0372DE` is called only from `1C03A418` (`1C03A5DA`). In mode `1`/`10000001`, `bVar6` is set only for exact `0x10001` (`1C03A47C-48A`), so TW pad presses never enter it; they go to `1C034954`. In `0x10001`, `1C0372DE` zeroes the new-press mask (`(short)mode==1`), and its path A requires `(short)mode!=1`. `-0xA26` arg 0 -> `FFF8C134(*FFF8D8E8,0)`: one track (current/old), `EB40=0`, restore bit `EB60` + `EB78=0xFF`; only emitted if Hold is engaged or pending there. Residual edges: `1C06CBB8` relative-offset path, pad-function-4 release path, transition branch (low). Trace `hold-padselect-a26.txt`; details in `gate-hold-control.md` last section.

## Updater / load path (2026-10-13)
`analysis/updater-load-path.md`: USB HID (VID 2B73 PID 0025, 64-byte reports, kinds F1/F2/F7), host-only CRC-16 + per-chunk byte-sum, no crypto seen, equality-only version gate.
Verdict: modified image plausible host-side; device-side HID handler unchecked, so acceptance is unknown. Brick risk (overwrites offset 0).
Next: Ghidra the firmware's HID report handler (descriptors at .bin 0xF5330/0xF552C app, 0x52398 alt); see Unknowns list there.

## Device-side update handler (2026-10-13, Claude) — `device-update-handler.md`
The F0–F7 handler is in the ALTERNATE/bootloader bank (file 0x20000–0x90000), NOT the application bank; analyzed via new alt-bank ELF `/tmp/claude-1000/alt/squid-v113-alt.elf` (proj `/tmp/claude-1000/alt ALT`), dispatcher ~0x1C005226 (file 0x40000→0x1C000000 mapping). Kind = report[0]>>4==0xF; ops 2/3/4/5/7/8/0xA. Device verifies ONLY the per-chunk 32-bit byte-sum (0x1C00589A, mismatch→0x81/2) + post-program read-back verify (0x1C005C74, fail→0x81/4). No CRC/hash/signature/model/version-date on device; address is host-supplied with NO lower-bound guard (offset 0 writable). No backup bank / commit flag / rollback in the handler.
Verdict: a modified image IS accepted (byte-sums auto-computed by the tool). Brick window = full update (version mismatch) rewriting F1 = bootstrap(0)+bootloader bank; app-only F2 patch keeping device-date equal skips F1 → lowest risk. Scripts: InspectAlt.java, LinAlt.java; traces alt-lin*.txt, alt-wr/prog.txt.

## Free space / toolchain / patch workflow (2026-10-13)
See `free-space-and-toolchain.md`. Best code cave: file 0xF5608-0xFCFFC (0x79F4 bytes, CPU 0x1C045608-0x1C04CFFC, inside the boot-copied window; no literal references found, Ghidra xref check still to do).
SH-2A BE binutils extracted to `~/squid/build/tc` (encoding verified vs firmware); gcc via Debian `gcc-sh-elf` podman container. Patcher: `~/squid/build/patch_image.py` (guards date word and bank, recomputes CRC and chunk sums).

## Independent falsification review (2026-10-13) — `independent-review.md`
Re-derived all 5 load-bearing claims from raw .bin bytes (sh-objdump) + Mach-O (x86 objdump), not the notes. Claims 1/2/3/5 VERIFIED against assembly (byte-sum cmp @file 0x4589E, readback @0x45C74, addr bounds only vs 0x1C0000/0x1D0000 no-low-guard @0x45C30, loader copy consts @0x90040 + jmp 0x1C016CEE, version cmp/jne @0x10000F7DA, bootstrap all-FF bank-validity test @0xFFF80824). Claim 4 PARTIALLY: notes' "0x1D0FFC = all FF" is WRONG (actually "1.13"=0x312E3133); bank-select read is 0x1D0FC0 window, not a single 0x1D0FFC word; user-data flash location unpinned. Residual: BSS-clear bounds corroborated (pointer scan, floor 0x1C04E000) not instruction-traced.
Recommendation: GO for first F2-only cave flash on a SPARE unit (keep date word unchanged → F1/bootloader untouched → recoverable; patch_image.py CRC+byte-sum layout independently confirmed correct).

Init and user-data (2026-10-13, Claude): `init-and-userdata.md`. No BSS-clear loop in startup; RW data ends at 0x1C045607 (rand seed 0x1C045604); vector table 0x1C04E000, pools >=0x1C04EF00; cave 0x1C045610+ stays SAFE.
User data: settings-like 4 KB at boot-flash 0x1C0000 (update handler skips 0x1C0000-0x1CFFFF), block store (128 KB, 512 B sectors) on SPI channel/device 1. F2/F7-only update should not touch either (static inference). Fixed 0x1D0FFC "1.13" note in free-space-and-toolchain.md / code-cave-safety.md.

LED path (2026-10-14, Claude): `led-driver-candidates.md`. The LEDs are on panel MCUs A and B over SCIF4/SCIF1 with DMA, 64-byte frames (`1C013DFE`/`1C01436C`). Lamps are `FFF8DE98[57]` (0 off, 1 dim, 3 full) and pads are `FFF8DED1[16]` (bit6 dim + 6-bit color index; the palette is in the panel MCU). Composers `1C03B674`/`1C03BE6C` run from LED task `1C03CAA6`. Blink clock `FFF8F4BC` runs -320..1600 (`1C03CB78`), beat clock `FFF8F478`.
Candidates: GLOBAL Brightness / Track Color need no patch. Simplest code-immediate edits are the pending-pad flash color `#25/#89` at file `0xEC839/0xEC835` and the track-color table immediates `1C03D210+` (avoid entries 6/7/14, which share registers with the scanner map). Nothing was patched or built.

Palette + test candidates (2026-10-14, Claude): `palette-and-test-candidates.md`. Pad palette FOUND in panel-A image (file 0x110000): 10 brightness blocks x 128 RGB triples at 0x110100..0x110E80; byte = 7-bit index (0x40 white, 0x7F dim white, 0x41-0x7E dim). Confirmed by factory R/G/B/W test (42/21/2/0x40) + manual correlations.
Easiest data-only test: `Ver.%s` at file 0xF460C (GLOBAL > System Info), or app "1.13" at 0xF219C. USB strings at 0xF548C.. (app bank only; alt copy at 0x524F6 is do-not-touch).
No way found to test acceptance without loading an image (device does no CRC/signature check). Open: panel-MCU load path, which block = which brightness setting.

## 2026-10-14 flash results (user flashed on own unit, Mac updater)
- Ver.->Mod. string (0xF460C): flashed, no visible change (string is on a System Info page not found). Inconclusive.
- 1.19 (0xF219F='9'): boot screen "Version 1.19" -> app-bank data edits take effect.
- No-op hook at 0x1C00091A -> cave 0x1C045610 (+1.19): booted, arp ok.
- 1.20 + hook + 0.5->0.25 at 0xAA620 (fast code literal, FFF8C620): 1/2X is slower. First behavior change confirmed on hardware.
- Images: Drive SQUID_firmware_test/MODIFIED_HOOK_120_QUARTER. Patch sets: build/patches/.
- Next: 2X (code computes 2.0 via fldi1/fadd at FFF8C526-C528, not a constant) needs a code edit; other ideas in feature-requests.md.

Stacking speed design (2026-10-14, Claude): `stacking-speed-design.md` + `build/patches/stacking.s` (assembled, 276 B at cave 1C045630; LVL[16] per-track level there). Patches: FFF8C49C stub (0xAA49C), nop FFF8C4BC (0xAA4BC), handler ptrs FFF83ED0/ED4 (file 0xA1ED0/4), producer filter 1C03A8A4 + literal 1C03A8F4 (0xEA8A4/0xEA8F4). Staged S1 (stock-identical) -> S1b -> S2 -> S3. Nothing built/flashed.
