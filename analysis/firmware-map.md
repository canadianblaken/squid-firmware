# SQUID v1.13 — updater and initial runtime map

Latest continuation (2026-10-12): [note-output-path.md](note-output-path.md) connects gate expiry through note masks and queues to MIDI formatting and CV/Gate register writes. Earlier unresolved-output statements below describe the preceding pass.

This pass inspected the updater and firmware as data. No updater, firmware code, USB request or device operation was executed. The findings below come from local LIEF/Capstone/Ghidra analysis and byte checks; they are not new REA Evidence records. Earlier REA extraction findings remain in initial-findings.md.

## Findings

The image contains big-endian SH-2A instructions, including MOVMU, MOVI20, JSR/N and RTS/N. Startup explicitly writes FPSCR, supporting SH2A-FPU as the working processor profile. This is substantially stronger than the previous generic SuperH guess. The exact Renesas chip is still unknown.

The Mac updater preserves 6,602 symbols. Product-specific symbols include MainComponent::ParseUpdFile, CalcCRC, CheckUpdFile, run, SendDataAndWaitResponse, and HidUtil::deviceOutput. These provide anchors without executing the application.

## File validation recovered from the updater

ParseUpdFile at Mach-O address 0x10000F110:

- Receives expected size 0x200000 from CheckUpdFile at 0x10000EBB2.
- Reads a four-byte big-endian metadata word at file offset 0x1DFFFC. Its bytes are `20 18 12 20`. The individual byte checks resemble a packed calendar date, but its exact purpose is unconfirmed. It must not be used as the firmware release date.
- Reads the displayed version from the four ASCII bytes at 0x1D0000: `1.13`.
- Calls CalcCRC over all but the final two bytes and compares the result with a little-endian 16-bit trailer.

CalcCRC at 0x10000F4B0 uses a 0x1021-family polynomial. Independently computing CRC-16 with polynomial 0x1021, initial zero, no reflection and no final XOR produces **0x73FA**, exactly matching trailer bytes `FA 73`. The original image is therefore consistent with the updater's observed size/version/CRC format; this is not a cryptographic authenticity check or proof it will run correctly.

The metadata block also contains `Info1=[ 2019/06141406 nishikawa ]`, `Info2=[ UPD01 ]` and `Info3=[ UPD01 ]`. These are literal embedded strings; their relationship to the 2024 release is unknown.

## Update transfer layout

CheckUpdFile at 0x10000EC69 sets up three descriptors; constants at Mach-O addresses 0x100123C50 and 0x100123C60 supply the second and third sizes/offsets.

| Update kind byte | File offset | Length | Device offset field |
|---|---:|---:|---:|
| 0xF1 | 0x000000 | 0x090000 | 0x000000 |
| 0xF2 | 0x090000 | 0x140000 | 0x090000 |
| 0xF7 | 0x1D0000 | 0x010000 | 0x1D0000 |

The observed path reads up to 0x20000 bytes from the file at a time and copies payload bytes into 64-byte HID messages, typically 62 payload bytes following two protocol bytes. Address fields are serialized little endian. The firmware's machine code itself is big endian.

In the inspected file-to-send path, the payload is copied directly, with no decompression or decryption step. This is a bounded static finding about this updater path. It does not establish what the device does after receiving data or exhaust every updater function.

## Initial runtime map

The bootstrap at file offset zero loads stack pointer 0xFFF90000 and jumps to 0xFFF80010. At file 0x8DC, it selects either offset 0x20000 or 0x90000 and loads that loader into 0x1C04D000. The loader then supplies the mappings below.

Lengths are the effective whole-word DMA lengths inferred from the helper's right shift by two. In particular, arguments 0xFFF, 0x4CFFF and 0xEF become 0xFFC, 0x4CFFC and 0xEC bytes. Treat these as initial static transfer boundaries, not a full hardware memory map.

| Path | Source file offset | Runtime address | Derived length |
|---|---:|---:|---:|
| Alternate loader | 0x020000 | 0x1C04D000 | 0xFFC |
| Alternate fast code | 0x030000 | 0xFFF82000 | 0x189C |
| Alternate main code/data | 0x040000 | 0x1C000000 | 0x4CFFC |
| Application loader | 0x090000 | 0x1C04D000 | 0xFFC |
| Application fast code | 0x0A0000 | 0xFFF82000 | 0xB754 |
| Application main code/data | 0x0B0000 | 0x1C000000 | 0x4CFFC |
| Version metadata, both paths | 0x1D0000 | 0xFFF81F00 | 0xEC |
| Additional word, alternate path | 0x1DFFFC | 0xFFF81FFC | 4 |

The application loader jumps to 0x1C016CEE (file offset 0xC6CEE). The alternate loader jumps to 0x1C00474E (file offset 0x4474E). A recovery/update role for the alternate path is plausible, but not yet established.

These paths overlap at runtime and should be analyzed separately. Loading the entire 2 MiB file at one address would give incorrect cross-references. The two NORTi strings occur in the two different main-code images; that is consistent with each path containing its own RTOS code, without proving its runtime role.

## First feature anchor: Gate Length

Review correction: the published manual already describes this control (page 143). See [review-and-next-steps.md](review-and-next-steps.md) for the documentation cross-check and corrections to the earlier stopping rationale.

The application mapping predicts:

- `Gate Length` at file 0xF0CE4 → runtime 0x1C040CE4.
- Pointer to that address at file 0xB13F4 → runtime 0x1C0013F4.
- Instruction at file 0xB11C0 → runtime 0x1C0011C0 loads that pointer.
- The following instructions copy the 12 bytes including the terminator into a buffer.

This validates a useful cross-reference under the proposed mapping. A closer bounded decode shows the containing UI routine branches on a menu field and case 4 selects the Gate Length label from the Arpeggiator Settings choices, then copies it into a display-row buffer. The shared code after the label cases updates UI row state and formats menu data. This is strong evidence for the menu identity, but it does not establish how the selected Gate Length value is stored or used by the sequencer.

Saved as `arp-setting-ui.asm`. Ghidra analysis of the mapped application image resolves the setting path further:

- The Arpeggiator Settings builder at 0x1C000D2C selects the type-4 getter and encoder handler for the Gate Length row.
- The type-4 render branch at 0x1C0013AE formats the selected unsigned byte using `%d%%` at file offset 0xF0CF4.
- The edit handler at 0x1C001012 passes bounds 100 and 1 to the shared value-edit helper. The helper clamps to those bounds and calls the setter when the value changes.
- Setter 0x1C0000D8 checks channel index < 16 and value 1–100, then writes the byte to field offset 0xE16 in that channel's object. Getter 0x1C000100 reads the same field. The channel pointer table begins at 0xFFF8E1F4, reached through the pointer stored at 0x1C00023C.

The timing path now connects Gate Length to the Arpeggiator engine. Per-channel update routine 0x1C000740 passes the stored 0xE16 byte to 0x1C000C5A when it schedules an arp event. That routine divides the value by the observed float constant 100.0, asks helper 0xFFF8B71A for the next rate-dependent timing boundary, and stores `((boundary - current_phase) * (GateLength / 100.0))` in a per-channel float timer. The boundary helper uses fractional timing constants 0.25, 1/3, 0.5, 2/3 and 0.75. The timer's base is 0xFFF8D7B0: 0x1C000E74 points there, and 0x1C0009E4 points to 0xFFF8D754, whose +0x5C offset is the same timer array. Routine 0x1C00091A subtracts a timing delta selected from global or per-channel timing state. Its pseudocode clamps the timer and clears an active mask when the result is strictly below zero. Separate later scheduling checks can dispatch 0x1C000B7E; they are not shown to be conditional on timer expiry. This supports the interpretation that Gate Length scales the interval to the next Arpeggiator timing boundary. It does not yet identify the final MIDI note-off or CV/Gate output operation; those callbacks and state flags remain unresolved.

The UI getter/setter and runtime routine 0x1C000740 access the Gate Length field. Earlier direct-access searches were incomplete, so they cannot establish an exhaustive list of users. The UI builder and getter offsets support this corrected field map: +0xE15 is octave Range, +0xE16 is Gate Length, +0xE18 is Rate, and +0xE1C is Style. Earlier notes calling +0xE18 Style were incorrect.

The output path is unfinished, not blocked by a demonstrated need for hardware. In 0x1C00091A, the timer branch clears the per-channel mask at 0xFFF8D7AC. Routine 0x1C000740 reads that same mask before constructing output note masks at 0xFFF8E130 and 0xFFF8E140. This provides a concrete static continuation: follow consumers of those masks through the callback pipeline in 0xFFF8942C. Separately, 0x1C000B7E dispatches through literal pointers 0x1C000C30 and 0x1C000E6C; both resolve to timing helper 0xFFF8B71A. Those particular indirect targets are resolved and are not hardware-output callbacks.

The candidate timed-note machinery at 0xFFF88C30 / 0xFFF8CC18 and bookkeeping at 0xFFF88D1C remain useful leads. Shared arrays alone do not prove the relationship to arp gate expiry. The inspected users at 0x1C0235AC, 0x1C02379E and 0x1C023948 manipulate note/state arrays; their higher-level roles remain inferred.

Decompiler limitations require care: 0x1C000B7E has an inconsistent recovered signature relative to its caller, and several functions contain suspicious zero-index shift expressions. Confirm affected control flow and register arguments against instructions before relying on exact pseudocode semantics. Ghidra also reported an out-of-mapped-space decompilation at 0x1C03D6A8. The derived ELF maps populated code/data slices but does not define the runtime RAM arrays above them; adding evidence-backed uninitialized RAM blocks could improve analysis.

REA's Ghidra provider is available, but rejected the derived SuperH ELF with `unsupported ELF architecture`. The follow-up used the locally installed Ghidra 12.1.4 headless analyzer directly. Focused function output is saved as `runtime-path.txt` and `arp-timing-xrefs.txt`; the query scripts are `TraceRuntimeCaller.java` and `TraceArpTiming.java`.

## Artifacts and reproduction

- `note-output-path.md`, `note-output-trace.txt`, `TraceNoteOutputs.java`: latest mask, queue, formatter and CV/Gate register trace; 121 functions examined in a read-only headless pass.

- `memory-map.json`: offsets, addresses, path membership and hashes of mapped slices. Slices remain in the original firmware; no extra binary copies were needed.
- `mapped-disassembly.asm`: focused instructions at mapped addresses, including the Gate Length reference.
- `arp-setting-ui.asm`: bounded decode of the Arpeggiator Settings display routine and shared row-state code.
- `arp-setting-value-render.asm`: bounded Capstone decode of the percentage formatting branch.
- `ghidra-gate-trace.txt`: Ghidra cross references and decompilation for the UI builder, value handler, getter, setter and candidate step routine.
- `runtime-path.txt` and `arp-timing-xrefs.txt`: Ghidra decompilation of the arp scheduling and timer-consumer routines, plus observed timing constants and RAM pointers.
- `squid-v113-application.elf` and `ghidra-app-image.json`: a derived ELF container that maps application-path firmware regions separately for Ghidra; it is not an official firmware file.
- `build_ghidra_app_elf.py`, `TraceGateRefs.java`, `TraceRuntimeCaller.java` and `TraceArpTiming.java`: reproduce the mapped image and focused Ghidra traces.
- `updater-selected.asm`: selected named updater functions disassembled as x86-64.
- `startup-selected.asm`: exploratory snippets; some linear snippets can enter literal pools and must not be treated as a complete code listing.
- `updater-symbols.json`: updater hash and symbol names/addresses.
- `image-validation.json`: version, computed checksum and stored checksum.
- `disassemble_selected.py` and `build_memory_map.py`: reproduce these files using LIEF and Capstone 5.0.9 in the isolated analysis environment.

Capstone displays some negative MOVI20 operands as unsigned 20-bit numbers. The focused mapped listing adds their sign-extended values; raw decoder output is retained elsewhere. Ghidra 12.1.4 is installed under `~/.local/share/ghidra/`; its downloaded archive matched the official release SHA-256. Ghidra was used headlessly on the derived application ELF. REA's Ghidra provider recognizes the provider but cannot load this SuperH target.

## Remaining work

1. Confirm the exact MCU from hardware evidence or sufficiently specific peripheral references.
2. The static timer-active-note → note-release → MIDI/CV/Gate route is recovered in `note-output-path.md`. Remaining output work: true callback-loop function entry and task/interrupt timing, UART/USB queue consumption and physical transmission, and exact CV/Gate register/pin identities. `FFF84F26` producer behavior is traced, but no consumer or send-completion path was found in this pass. Runtime capture is needed to verify actual timing.
3. Establish remaining data-region mappings, including the demo project at file 0x190004.

## Primary references

- [Renesas SH-2A / SH2A-FPU instruction manual](https://www.renesas.com/en/document/mah/sh-2a-sh2a-fpu-software-manual)
- [Ghidra 12.1.4 release](https://github.com/NationalSecurityAgency/ghidra/releases/tag/Ghidra_12.1.4_build)
- [REA firmware workflow and raw-image limitations](https://github.com/morluto/rea/blob/main/docs/firmware-analysis.md)
