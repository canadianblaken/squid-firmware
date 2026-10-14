# Gate Hold control trace — 2026-10-13

## Question

Which button event implements Gate Hold, and how does it affect release timing? The panel-to-speed-state path and a per-track gate-countdown connection are now traced. Coverage of every note source remains open.

## Current result

The three scanned indexes have distinct half-speed, zero-advance, and double-speed effects. Together with the supplied manual's Speed Control descriptions, this identifies the middle index as Gate Hold with high static confidence. The label assignment is an inference from behavior, not a physical button capture.

| Control inferred from behavior | Button index | State byte address | Group selection | Speed state |
|---|---|---|---|---|
| 1/2X | `2E` | `1C06CB60` | 0 | 1 |
| Gate Hold | `2F` | `1C06CB61` | 1 | 2 |
| 2X | `30` | `1C06CB62` | 2 | 3 |

### Press, pending application, and release

1. Scanner `1C03821A` reads **three** entries from map `1C06CBF1`, indexing button state base `1C06CB32`. It writes selection `1C06CC29` and group edge history `1C06CC31`. It tracks press order: releasing one button can select another still-held button; selection 3 means none held.
2. In the ordinary middle-button branch, UI handler `1C03A418` chooses event `-0xA27` and argument 2 (`1C03A830–1C03A840`), then writes the event and argument at `1C051240/+4` (`1C03A8A2–1C03A8A6`). The release branch selects argument 0. UI context conditions apply; shifted outer buttons also have a separate event path.
3. Consumer `FFF83B26` dispatches this event to `FFF8C114(track,value)`. With transport running (`FFF8E440 == 1`), it sets the track bit in pending mask `FFF8EB62` and stores the value at `FFF8EB64`; otherwise it calls `FFF8C134` directly.
4. `FFF8C134` writes active speed state `FFF8EB40+track`. During playback, `FFF8C3D4` applies pending state 2 and `FFF8C360` applies other values. `FFF842D0` invokes them under different timing flags (`FFF8EB1C` and `FFF8EB1E` respectively); no exact activation latency is established.
5. Changing a nonzero state to zero sets restoration mask `FFF8EB60` and writes `FF` to `FFF8EB78+track`. `FFF842D0` then copies the separate baseline timing record `FFF8E768+12*track` into `FFF8E828+12*track`, clears the restoration bit, and calls `FFF89C0C`. The copy is explicit at `FFF843B2–FFF843C4`. This supports the documented return to background playback position.

### Timing and gate countdown

`FFF8C460` selects active state `FFF8EB40+track`, falling back to recorded state `FFF8EB50+track` when active state is zero during playback. It produces timing at `FFF8E828+12*track` from baseline `FFF8E768+12*track`:

- State 1: multiply timing advance by 0.5 (`FFF8C620 = 3F000000`).
- State 2: write floating-point zero to timing advance (`FFF8C492–FFF8C49A`, `FFF8C5F0–FFF8C5F2`), preserving the other timing fields.
- State 3: multiply advance by 2 (`FFF8C526–FFF8C536`).

`FFF8BA50` reads that advance (`FFF8BA90–FFF8BA98`: base `FFF8EB10 - 2E8 = FFF8E828`), scales it, and writes the downstream record at `FFF8E8E8+12*track` (`FFF8BB38`). A zero input gives zero normal advancement.

The per-track branch of Arpeggiator gate timer `1C00091A` reads this downstream advance (`1C000962–1C000972`) and subtracts it from the active gate countdown (`1C000A3C`). Gate Hold therefore stalls this countdown while its timing state is active. Expiry would otherwise clear the active mask and feed the previously traced note-mask/release chain. This establishes an upstream timing mechanism; the examined output comparison `FFF88E28` still releases removed notes.

**Scope limit:** separate active-note timer `FFF8CC18` subtracts `FFF8F318`, which `FFF8CB80` derives from global timing `FFF8E450 * 8`. It runs before per-track timing updates. The zero per-track advance does not itself freeze that global-clock route. Other reset, mask, and scheduling paths can also change notes; universal suppression of note-offs is not established.

### Evidence and continuation

- `speed-control-inspection.txt`: three-entry scanner, speed setters, alternate global speed handler.
- `speed-state-xrefs.txt`, `speed-state-consumers.txt`: speed-state readers, pending application, half/zero/double behavior.
- `hold-timing-inspection.txt`: downstream per-track timing and resynchronization helper.
- `hold-timer-bridge.txt`: separate global-clock timer source and loop caller.
- `note-output-trace.txt`: UI event, consumer, per-track service/restoration, Arpeggiator countdown.
- `InspectFocused.java`: reusable bounded read-only decompiler/SH listing script; arguments are output filename followed by function entries or `@address` xref queries. All five new trace files end in `TRACE_COMPLETE`.

Critical timing, setter, and restoration instructions were checked against SH assembly because the decompiler has zero-shift and signature errors. No tests, firmware execution, or device operations were performed.

## Note-source boundary — continuation

The normal sequence countdown is now traced as well as the Arpeggiator countdown. Gate Hold stalls elapsed-time expiry in these per-track branches; it does not lock the active-note mask against explicit events.

| Route | Clock or action | Established Hold boundary |
|---|---|---|
| Normal sequence: `FFF8BC14 -> FFF88C30 -> FFF8BF3A` | Notes use active bits `1C067060` and timers `1C068360`; `FFF8BF3A` selects advance `FFF8E8E8+12*track` when its third argument is nonzero | Zero advance prevents a nonnegative timer from crossing below zero through this subtraction |
| Arpeggiator: `1C00091A` | Same per-track advance in its nonzero second-argument branch | Gate countdown likewise stalls, subject to other scheduling/reset paths |
| Alternate playback: `FFF8CB80 -> FFF8CC18` | Global advance `FFF8E450 * 8 -> FFF8F318`; reads pattern pointers from `FFF8EBDC` | Per-track zero advance does not freeze this route; its user-facing feature name is not yet established |
| Explicit input events: `FFF8378C`, `FFF89340` | Direct calls to `FFF88D1C(track,note)` on release | Source bits can be cleared without waiting for either countdown; later Arpeggiator/other mask processing determines final output |

### Sequence timer evidence

`FFF8BC14` reads the current pattern through `FFF8E1F4`, computes note duration from the note record, and calls `FFF88C30` at `FFF8BEC2`. Thus `1C067060` is shared source state, not exclusively a separate live-note source.

`FFF8BF3A` loads per-track advance at `FFF8BF8E–FFF8BF94`, negates it at `FFF8C00E`, and adds it to the timer at `FFF8C094–FFF8C09C`. A negative result either receives a one-unit extension when the relevant step bit `0x40` is set, or calls `FFF88D1C` at `FFF8C0C6`. A timer already negative can still expire with zero advance; the freeze claim concerns elapsed-time advancement of nonnegative timers. With third argument zero, this function instead uses `FFF8E45C * 8` (`FFF8BFF0–FFF8BFF8`).

The loop's `FFF833D4–FFF833DA` checks mode word `FFF8EBC8`; a nonzero value skips the normal timer/sequence block. Otherwise the per-track path calls `FFF8BF3A` at `FFF83414` with its third argument supplied in `r6`, followed by `FFF8BC14` at `FFF8341E`. This narrows the earlier global-timer caveat: the ordinary sequence timer has its own Hold-aware clock path.

### Alternate mode and mask processing

`FFF8CB80` only activates its alternate service when `FFF8EBCC` is nonzero. Its return value gates `FFF8CC18` and is passed as the second argument to `FFF8942C` (`FFF835A4–FFF835AA`). A nonzero argument takes the direct-copy branch `FFF8945A–FFF89488`: active mask `1C067060` goes to `1C067360`, and newly active bits go to `FFF8E1A0`, bypassing the usual transform/Arpeggiator chain before publication. This is a distinct operating path, not evidence that normal Gate Hold always leaks global-clock note-offs.

The normal branch runs `FFF8D2DE` (copies/transposes source masks), `FFF830A4` (copies into `FFF8E110/FFF8E120` and applies further processing), `1C000740` (Arpeggiator), `FFF8A808`, `FFF8D0EC`, and `FFF88AB8`, then publishes through `1C035484`. Explicit source removal need not become an immediate output release when another transformation or contribution retains that note.

`FFF8378C` handles zero-valued release events with direct `FFF88D1C` calls at `FFF838C2` and `FFF83A16`. Queue consumer `FFF89340` similarly calls it at `FFF893BC` for zero velocity under its routing conditions. These examined release branches do not test speed state `FFF8EB40`. Naming their exact physical/external input producers remains separate work.

New evidence: `hold-source-inspection.txt`, `hold-note-timers.txt`, and `hold-route-selectors.txt`, all produced with `InspectFocused.java` in read-only mode and ending in `TRACE_COMPLETE`; existing `note-output-trace.txt` and `callback-loop-inspection.txt` supply the normal timer and loop assembly. Critical timer subtraction, call arguments, mode skip, direct-copy branch, and explicit release calls were checked against SH instructions. No tests or device operations were performed.

Next focused question: map `FFF8EBC8/FFF8EBCC` to its user-facing mode through setter `1C034944`, UI callers `1C03943C/1C03946C`, and dispatcher `FFF83D28`. Then determine whether that mode can coexist with Gate Hold. Exact input-producer identities and exhaustive reset/mute behavior remain untraced.


## Earlier candidate and panel observations

The following records the search before the speed-state path above was identified; unresolved statements here describe that earlier pass.

## Established behavior and bounded observations

- The supplied operating-instructions text (`manual/Pioneer_DJ_Toraiz_Squid_Operating_Instructions.txt`, lines 3113–3131) says holding **GATE HOLD** holds triggered note output while maintaining the previous playback position in the background; releasing it cancels the hold and resumes playback from that position. It also says BPM is unchanged and connected equipment may not sustain the sound. This is documentation, not an observation of device behavior.
- The mapped firmware's extracted ASCII strings (`analysis/ascii-strings.txt`) contain no `Gate Hold` or `GATE HOLD` label. This bounds a string search only; a button can be handled numerically without a label.
- `FFF8B1B4` writes the 0/1/2 state at `FFF8E440` (`note-output-trace.txt`, `FUNCTION fff8b1b4`, instruction `FFF8B222`). Its branches invoke broad reset/start helpers. `1C01093A` invokes it with 0 or 2; `1C00FE58` invokes it with 1 or 2; `1C0105A6` invokes it with 1 under a branch. These are code-level callers, but none is tied to the physical Gate Hold button by the present trace.
- The transition helpers establish the narrower meaning of this state: `FFF8B1B4` calls `1C00F3DE` when moving 0→1 and that helper formats MIDI realtime byte `0xFA` (Start); it calls `1C00F456` when moving 2→1 and that helper formats `0xFB` (Continue). Moving 1→0 calls `1C00F4CE`, which conditionally formats `0xFC` (Stop); moving 1→2 calls `1C00F5B8`, which also has a Stop-formatting path. These are transport-mode transitions, with output conditional on routing/state. The code does not identify which physical button requests them. Evidence: `mode-caller-inspection.txt` and `gate-hold-inspection.txt`.
- The two located literal callers of `1C01093A` belong to a transport-control cluster: recovered function `1C00FBFE` calls it with 0 or 2, while `1C00FC96` calls it with 2 and `1C00FD54` calls it with 0 (`mode-caller-window.txt`, assembly). This narrows the path but still does not map a panel button code. The same cluster reads `FFF8DE74/FFF8DE76`, which are also read by many non-panel routines (`loop-gate-inspection.txt`); that breadth makes them poor Gate Hold identifiers without an input trace.
- The note-processing loop at `FFF83288` eventually calls `FFF8942C`, then `FFF88E28`. The latter compares desired-note masks with previous output state and queues a velocity-zero release when a note disappears (`note-output-path.md`). A hold could act before mask publication, at this comparison, or in a different state path. The current evidence does not choose among them.
- In the saved `FFF88E28` decompilation (`note-output-trace.txt`, `FUNCTION fff88e28`), the release branch still calls `FFF891BC` when a note is removed. The per-track flag read through `DAT_fff89088` changes a branch's release helper, but the trace does not identify it as Gate Hold. This makes the button event and upstream desired-mask generation the useful next search points; it does not rule out a hold effect in this comparison.
- New read-only inspection of the loop's previously unexamined `FFF894DA` call shows that it ORs the selected track's `1C067260` and `1C067360` note masks into shared accumulators `FFF8E0F0` and `FFF8E190` (`gate-hold-inspection.txt`, `FFF894DA–FFF89530`). It has no branch on a button state in this function. The subsequent `FFF8940C` copies active-note mask `1C067060` to snapshot `1C066F60` (`FFF8940C–FFF8942A`). Neither function identifies the Gate Hold control.
- A separate candidate at `FFF8F372` is a per-track enable mask: `FFF8D0AC(track,1)` sets that track's bit; `1C035484` uses the bit to choose whether to publish note masks or zero masks (`loop-gate-inspection.txt`). The examined code does not link it to Gate Hold.

## Panel scan lead from the next read-only pass

- `1C03CA54` calls the UI handler `1C03A418` through literal `1C03CB50`, after the same routine calls `1C038A1C` and `1C038C78` (`panel-dispatch-inspection.txt`, SH listing). `1C038A1C` calls the bounded scan helpers `1C037F90`, `1C038104`, `1C03821A`, and `1C03835A`; their decompilation shows two-bit button history, with low bits `01` used for a new press and `10` for release (`panel-array-inspection.txt`, `panel-scan-inspection.txt`). The physical button-to-index mapping is still open.
- `1C038C78` reads button state from base `1C06CB32` (literal `1C038E84`); `1C03A418` uses base `1C06CB24` (literal `1C03A4BC`). These point into the same RAM block with a `0xE` offset. For example, `1C03A418` offset `+0x2D` is `1C038C78` offset `+0x1F`, both address `1C06CB51` (`panel-state-inspection.txt`, `panel-array-inspection.txt`). Do not compare their apparent offsets without this translation.
- The scan helpers index the shared state array through RAM mapping tables. `1C037F90` reads a six-entry map at `1C06CBFA`; `1C038104` reads a two-entry map at `1C06CC00`; `1C03821A` and `1C03835A` use adjacent map entries (`panel-scan-inspection.txt`). The SH writes at `1C03D1CA–1C03D208` initialize these index bytes (`panel-dispatch-inspection.txt`):

  | Map address | Initialized bytes | Reader |
  |---|---|---|
  | `1C06CBF1–F9` | `2E 2F 30 31 32 33 34 35 36` | `1C03821A` uses the first three; `1C03835A` uses `31–34` |
  | `1C06CBFA–FF` | `12 13 14 15 16 17` | `1C037F90` |
  | `1C06CC00–01` | `0F 11` | `1C038104` |

  The writes at `1C03D212–1C03D298` fill two companion 16-byte tables at `1C06CC04` and `1C06CC14`; their byte values differ by `0x40` at each index. These numeric tables establish scanner indexes, not panel labels. `1C06CBF1` is loaded through the literal at `1C03D340`; the first write advances the pointer before subsequent offset writes, which is why the table begins at `2E` and continues at `2F` (`panel-dispatch-inspection.txt`).
- The new pass has not identified which array index is Gate Hold, nor a press/release handler or hold-state writer. No conclusion about `FFF8E440` changes. These are bounded static observations from the derived ELF, with no device runtime evidence.

The current continuation and evidence are listed above. Physical output completion remains outside this static trace.

## Alternate mode identified: TIME WARP (2026-10-13 continuation)

Sources: saved `note-output-trace.txt` SH listings of `1C038C78` (lines ~13675–14625) and `1C03A418` (lines ~17110–17470, 18529–18610); manual lines 255–256, 3302–3350. No new Ghidra run; no device or firmware operation.

**Observed (high confidence).** `FFF8EBD8` is not written per button. `1C03A418` compares the current UI mode word `*(*1C06CB98)+0` with the previous mode word at `+0x10` (`1C03B09C–1C03B12A`; literals `1C03B1CC=40000001`, `1C03B1D0=10000001`, `1C03B1D4=FFF8EBD8`). It writes 1 (`1C03B0E4`) on entry into the mode family {`1`, `10001`, `10000001`} from outside it or from `40000001`. It writes 2 (`1C03B12A`) when the current mode is outside that family, or is `40000001`, and the previous mode was inside it. `10000001 -> 1` writes nothing.

**Observed (high confidence): button to mode.** In `1C038C78` the button base is `1C06CB32`, so offset = scanner index; `1C03A418` offsets are +0xE.
- Index `00` (`1C06CB32`) new press with no other modifier bits (`r13 & 0xFFFB == 0`) requests mode 1 (`1C038F2A–1C038F3E`). Request 1 becomes `10000001` (pending entry) when not already in the family. It becomes `40000001` (pending exit) from `1`/`10001` (`1C0391F2–1C03921C`).
- Index `0C` (`1C06CB3E`) new press while in the family toggles mode `1 -> 10001` with `1C034944(2)`, so `EBC8=2` (`1C039420–1C03943C`). It also toggles `10001 -> 1` with `1C034944(1)` (`1C039458–1C03946C`).
- The other one-shot mode requests in the same block are: index `22` -> 2/3 (shift), `1F` -> 6, `25` -> 7, `04` -> 9, `37` -> 8, and `0A` -> A (`1C038F58–1C038FC2`).

**Inferred (medium-high): user-facing feature is TIME WARP.**
- The manual has one top-level button that toggles the mode and waits for the next bar's downbeat while flashing. Exit is also a button press. This matches `10000001`/`40000001` pending states around steady mode 1.
- Time Warp pads 1–16 each select a 1-bar preview start. This matches `1C034954`, which accepts pad index 0..15 only when `EBC8==1`.
- Pressing PATTERN enters a save sub-mode and pressing it again returns. This matches the index-`0C` toggle `EBC8 1<->2`, so index `0C` is PATTERN (inferred). Index `00` is the TIME WARP button (inferred).
- The preview loops the auto-recorded history rather than the live sequence. This matches `EBC8!=0` skipping the normal sequence/timer block (`FFF833D4`) and the `EBCC` global-clock service `FFF8CB80/FFF8CC18`, which reads `FFF8EBDC` pattern pointers and takes the direct-mask branch in `FFF8942C`.
- [STOP] stops the preview. This is consistent with `1C034A1C(0)`, which clears `EBCC` and restores its 12-byte timing copy. The `1C034A1C(0)` call made when `EBD8=2` on exit is likewise consistent.
- `FFF8E440==1`-gated 128x8-byte ring `FFF8ECDC`, drained by `1C034C30`, is a candidate for the "auto record" buffer (unverified).

**Coexistence with Gate Hold.**
- Observed: `1C03A418` clears `r8` when the current mode's low half is 1 (`1C03A516–1C03A51C`). The speed-button branch that emits `-0xA27` with argument 1/2/3 requires `r8 != 0` (`1C03A7FA`). New 1/2X, Gate Hold, and 2X presses are therefore not dispatched in Time Warp-family modes (medium-high; other `r10`/`r12` sub-paths not fully resolved). Whether an argument-0 release event is emitted on that path (`1C03A882–1C03A88A`) is unresolved.
- Observed: no write to `FFF8EB40` was found in `FFF83D28`, `1C034944`, `1C034A1C`, or the `EBD8` writer. A Hold engaged before entry is not shown to be cleared.
- Inferred (medium-high): even if `FFF8EB40=2` persists, it has no timing effect on Time Warp playback. The per-track advance path (`FFF8BF3A/FFF8BC14`) is skipped while `EBC8!=0`. Preview timers use global `FFF8E450*8`, and the direct-mask branch bypasses the Arpeggiator.
- Conclusion: the two do not functionally coexist. Gate Hold is locked out of UI dispatch during Time Warp, and any held state is inert there. The Hold state after exit (stale state 2 vs. reset) is open; next step is to trace `FFF8EB40` writers reached from `FFF83D28` state 2 or from the `40000001 -> 0` resolution.


## Time Warp entry/exit vs Hold state (2026-10-13, Claude)

Sources: `speed-state-xrefs.txt`, `speed-state-consumers.txt`, `control-event-inspection.txt`, `callback-loop-inspection.txt`; new `hold-timewarp-reset.txt`, `hold-eb3c-xrefs.txt`, `hold-ebc8-xrefs.txt` (InspectFocused, read-only, TRACE_COMPLETE) plus a byte scan of the ELF for the `movi20 #-0xA27 / -0xA26` encodings. No device or firmware operation.

**Observed (high).** The only code that writes `FFF8EB40+track` is `FFF8C134` (store `*(EB40+track)=arg`, `FFF8C14A–C15A`) and the boot initializer `1C033E40` (zeroes `EB40/EB50/EB68/EB78`, sets `EB60/62/64/66` at `1C033E54–6E`; reached only via the init-call list in the function at `1C00E0FC`, `jsr` at `1C00E11C`; no other reference). `FFF8C134` is reached from: UI event `-0xA27` via `FFF83B26` (`FFF83CBC` -> `FFF8C114`), event `-0xA26` via `FFF83B26` (`FFF83CC8` -> `FFF8C134` directly), and the pending-apply helpers `FFF8C360/FFF8C3D4`. Nothing in `1C034944`, `1C034A1C`, `FFF83D28`, or the `EBD8` writer writes it. So TW entry/exit does not itself reset `EB40`.

**Observed (high).** `-0xA27` is produced only in `1C03A418` (movi20 sites `1C03A828/83A/84E` = args 1/2/3, `1C03A886` = arg 0 release). Arg 0 is set by `bVar6`, derived from the speed-button history `==2` (release edge) or a `-0x3e5` flag, evaluated outside the `r8` gate (`1C03A868`). No mode-transition code emits it.

**Observed (high): second clear path.** `-0xA26` is produced only by `1C03720C` (`1C03725E`), always with arg 0 (`DAT_1c0373e8[1]=0`), so it calls `FFF8C134(track,0)`: Hold cleared, restoration bit set, pending bit cleared (`FFF8EB62` mask update). It fires when `EB40[track]!=0` or its pending bit is set (or a helper `PTR_1c0373e4` returns 0). `1C03720C` is called from `1C0372DE` (`1C037460/538/972`, and from `1C03A418` at `1C03A5DA`), a mode-request/pad handler taking the requested mode (`param_3` = 1, 3, 4) and a pad/track index. It is invoked on the current track (`*DAT_1c03740c`) when a valid pad/track index is selected (via `1C0370C6`/`1C0370EA`). Reading: selecting another track/pad clears the current track's Hold.

**Inferred (low-medium).** If request 1 is the TW button (index `00`, see above) and the TW pad/track path is this one, then pressing a TW pad would clear Hold on the current track. Unresolved: whether `param_3==1` here is the same mode-1 family, what `1C0370EA` validates, and whether the bare TW-button press (no pad) reaches `1C03720C`. Stopped here (multi-branch in `1C0372DE`).

**Answers.**
1. Entry/exit: no code observed that fires a release or resets `EB40` on the mode transition itself (high for the listed functions; the `FFF8EB40` reference set is exact-address only, so base-offset aliases are not excluded). Possible indirect clear via the `1C0372DE -> 1C03720C -> -0xA26` pad/track-select path (low-medium, unresolved). A physical Gate Hold release still sends arg 0 even in TW (release branch not gated by `r8`; medium), so a Hold held across entry would clear when the button is released.
2. After exit: `EB40` is preserved (a stale state 2/1/3 stays until a release event, `-0xA26`, or reboot) (medium-high). Since Hold is momentary, a stale value needs the release edge to have been lost. Per-track timing: `FFF842D0` (restore `FFF8E768 -> FFF8E828`, gated only by `EB60`) is called at `FFF83366` before the `EBC8` test at `FFF833D4`, so it runs during TW too; `1C034A1C(0)` on exit restores only global `FFF8D6B0 -> FFF8E450` (12 bytes) and clears `EB16`, not per-track timing (observed). `FFF8E8E8` is derived each tick by `FFF8BA50` from `FFF8E828`, not separately restored (inferred from earlier analysis; not re-traced).


## Can a Time Warp pad press emit `-0xA26`? (2026-10-13, Claude)

Sources: `control-event-inspection.txt` (decompile of `1C0372DE`, `1C03720C`, `FFF83B26`), `note-output-trace.txt` (`1C03A418` SH `1C03A46E-1C03A5B4`), `callback-loop-inspection.txt`, `hold-ebc8-xrefs.txt` (`FFF8C134`); new `hold-padselect-a26.txt` (InspectFocused, read-only, TRACE_COMPLETE: xrefs `@1C0372DE`, `@FFF8D8E8`; functions `1C0372DE`, `1C0370EA`, `1C0370C6`). No device or firmware operation.

**Observed (high): call site and arguments.** The only caller of `1C0372DE` is `1C03A418` (`1C03A5DA`; `1C03A6B0` is its literal-pool pointer). Arguments: `param_2` = UI mode word `(*1C06CB98)[0]`, `param_3` = word `[1]` (pad-function sub-state: 1 selects a track, emitting `-0xBB6`; 2 = per-pad `-0x10CB/-0x10CA`; 4/`10000004`, 5-10 = other pad functions), `param_7` = pad masks (high half = new presses `uVar21`, low half = held). `1C0372DE` exits early unless all 16 per-pad event slots `1C0510EC` are idle (`-0x10CC`) and UI event slot `1C051260` is idle (`-3000`).

**Observed (high): gate in `1C03A418`.** `1C0372DE` is called via `1C03A5B4` either (a) when `r14`/`bVar6` is set or (b) from the transition branch `1C03A546...` (`word2==1` or `word1&0x30000` in {`0x10000`,`0x20000`}, then only if previous `word8` is 5/6 (or 5/6/7 with `bVar3`)). `bVar6` is set at `1C03A47C-1C03A48A`: when the mode word's low half is 1, only an exact `0x10001` sets it. It also requires `*1C06CBBA==0`, `(*(1C06CD70+0x10)&8)==0`, and `1C03709C(word1)==0`. So in steady TW mode `1` and pending-entry `10000001`, a plain pad press does **not** call `1C0372DE`. TW pads go to `1C034954` (preview start) instead.

**Observed (high): branch conditions inside `1C0372DE` with TW mode words.**
- Path A (`1C037460`): requires `param_3==3`, new press, and `(short)param_2 != 1`. All TW-family words have low half 1, so this path is blocked.
- Path B (`1C037538`): requires `param_3` in {1,3,4,`10000004`} or `param_2==0x10001`.
  - With `param_2==0x10001` and `*1C06CBB8==0`, the code sets `uVar21=0` (new-press mask discarded), so no pad-press call.
  - With `*1C06CBB8!=0` (pending relative track offset), it calls `1C03720C(cur, cur+offset clamped 0..15)` after `1C0370EA` validates, then sets `*1C06CB9C=10`. This is an offset-driven move, not a pad.
  - With `param_3==1` and mode `1`/`10000001`, a new press would reach `1C0370EA` -> `1C03720C`, but path (b) above is the only way in.
- Path C (`1C037972`): requires `param_3` in {4,`10000004`}, entered only when `uVar20=1`. On a pad release (bit in `1C06CC24` set, pad no longer held), and if `1C03001C(pad)!=0` and `1C037146(pad)!=0`, it calls `1C03720C`. Reachable in `0x10001` only if word1 is 4 there (not shown).

**Observed (high): what `-0xA26` does.** `1C03720C(cur,new)` emits `{-0xA26, 0}` into slot `1C051240` only when Hold is engaged or pending on the current track: (`*1C06CC3A!=0` or `EB40[cur]!=0` or `EB62` bit) and (`1C02E6B0()==0` or `EB40[cur]!=0` or `EB62` bit). It also clears `EB62` bit `cur` (and sets `EB64=0xF0` if none remain). `FFF83B26` (track argument `*FFF8D8E8`, which is the same byte `1C0372DE` reads as `cur` at `1C0372E8`) dispatches it to `FFF8C134(track,0)`. That call sets restore bit `EB60|=bit(track)` and `EB78[track]=0xFF` if `EB40[track]!=0`, then writes `EB40[track]=0`. Scope: **one track (the track current before the switch), value 0**. Inferred (medium): `-0xBB6` updates `FFF8D8E8` later, so the old track is cleared.

**Answer.**
- Steady Time Warp (mode `1`, `EBC8=1`): a pad press cannot reach `1C03720C` via the normal gate, so `-0xA26` is not emitted (medium-high).
- TW PATTERN sub-mode (`0x10001`, `EBC8=2`): `1C0372DE` is entered, but the new-press mask is zeroed, so pad presses still do not emit (high for that branch). Exceptions: a pending `1C06CBB8` offset (high that it would clear; trigger unidentified), and the pad-function-4 release path (low; requires `word1==4` in TW).
- Residual edge (low): the transition branch (b) with `word1==1` while in mode 1.
- Net: Time Warp pad presses do not clear Gate Hold. Combined with earlier findings, `EB40` survives TW unless Hold is physically released, or a track change via `1C06CBB8`/non-TW pad select occurs after exit.
