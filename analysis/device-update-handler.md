# SQUID v1.13 — device-side USB-HID firmware-update handler

Static analysis only. No device touched, no updater/firmware executed. The firmware
`.bin` was read as data; all code was examined read-only in Ghidra.

**Where it lives (answer to Q1):** The F0–F7 update command handler is **NOT in the
application bank** (our `squid-v113-application.elf`, file 0x90000–0x1D0000). It is in
the **alternate / bootloader bank** (file 0x20000–0x90000), a separate full firmware
with its own USB stack that our application ELF does not map. The application bank only
contains general flash primitives used for *project/pattern* saves, not the updater.

To analyze the alternate bank I built a second ELF with the alt-bank mapping
(file 0x30000→0xFFF82000, **0x40000→0x1C000000**, 0x20000→0x1C04D000) at
`/tmp/claude-1000/alt/squid-v113-alt.elf` (Ghidra project `/tmp/claude-1000/alt ALT`).
**All `0x1C00xxxx` addresses below are alt-bank addresses** valid only under that
mapping; they overlap the application mapping and must not be confused with app-bank code.

## How Q1 was proven (observed)
- Entire 2 MB image: **no `cmp/eq #0xF1/#0xF2/#0xF7`** anywhere; the only `cmp/eq #0xF0`
  hits are inside font-bitmap data (file 0x152xxx/0x177xxx). The kind byte is tested as
  `report[0] >> 4 == 0xF` (alt `0x1C005242`), not an exact compare — that is why no F1/F2/F7
  constant exists.
- The F2-region tail (file 0xFD000–0x1D0000) the app loader never copies to RAM is pure
  resource data (fonts + strings "UPDATING"/"PLEASE  UPDATE"/"COMPLETE" at 0x1779C0),
  SH-2A opcode density ~0.6%/page vs ~7% for real code. No updater code there.
- The update **opcode** dispatch (cmp/eq against 2,3,4,5,7,8,0xA with 0x20/0x40 flag bits)
  clusters only in the alt bank at file 0x45000–0x46000 (runtime ~0x1C005250).

## Evidence table (alt-bank runtime addresses; Observed unless noted)
| # | Finding | Evidence addr | Conf |
|---|---|---|---|
| 1 | Command state machine; kind = report[0] (0xF0–0xF6, tested `>>4==0xF`), sub-op = report[1], internal state byte at 0x1C0570AC | dispatcher 0x1C005226–0x1C0059B2; kind test 0x1C005242 | H |
| 2 | Packet-count watchdog: wraps at 0, if >~0x639C → error state 6; abort if a status byte==2 → error 7 | 0x1C005288–0x1C0052BE | M-H |
| 3 | **op8 region-prepare**: stores host **address** (report[2..5], big-endian) and **length** (report[7..10], BE); no range rejection | 0x1C005500–0x1C005556 | H |
| 4 | **op2 begin-chunk**: stores address → 0x1C0570A4, length → 0x1C0570A8 (both BE from packet); no range rejection | 0x1C0055DC–0x1C005632 | H |
| 5 | **op3 data** (62 bytes/packet) copied into RAM staging buffer **0x1C0F0000**; only bound is `(counter+0x3e) > 0x20000` → reject code 3. No address check | 0x1C00566E–0x1C0056B6 | H |
| 6 | **op7 final-short**: length byte → 0x1C0570BE | 0x1C0056DE–0x1C005700 | M-H |
| 7 | **op4 byte-sum submit**: host 32-bit sum (report[2..5], **little-endian**) → 0x1C0570B4 | 0x1C00583C–0x1C005874 | H |
| 8 | **Byte-sum VERIFY**: device-accumulated sum (0x1C0570B8) `cmp/eq` host sum (0x1C0570B4). Equal → reply 0x80. **Mismatch → reply 0x81 subcode 2, abort** | 0x1C00589A–0x1C0058C2 | H |
| 9 | **op5 commit**: sets write-request flag 0x1C0570AF, polls, replies 0x83 (busy) / 0x84 / 0x85 (done) | 0x1C0058F8–0x1C0059A8 | M-H |
| 10 | **Flash program+verify**: driver programs buffer 0x1C0F0000 → flash at host address, length, via primitive at literal 0x1C005D40, arg flag=1. Result read-back `exts.b < 0` → **error 4 (reply 0x81 subcode 4)**; else status 0x85 | 0x1C005C28–0x1C005CD4 | H |
| 11 | **Address check is only vs 0x1C0000 / 0x1D0000** (handling of the app/metadata tail split). **No lower-bound guard: a chunk at offset 0 takes the normal program path and IS written.** Boot/vector area is not protected | `cmp/hs 0x1C0000` 0x1C005C30; `0x1D0000` 0x1C005C3E; normal path 0x1C005C90 | H |
| 12 | **Flash erase**: region-prepare erases 16 × 4 KB = 64 KB blocks (loop r14=0x10, step 0x1000) via primitive at 0x1C005D38 | 0x1C005BEA–0x1C005C10 | M-H |
| 13 | Version/date reply source: reads flash **0x1D0000** (version "1.13") and **0x1DFFFC** (date word) via FUN_1C004B5E; date is only *reported*, not enforced on device | 0x1C005D74–0x1C005D94 | H |
| 14 | Session init clears staging buffer 0x1C0F0000 (0x20000 bytes) and zeroes all state vars (addr/len/counter/sums/flags) | 0x1C005DAE–0x1C005E16 | M-H |
| 15 | No CRC-16, no hash, no signature, no model/product check, no version/date gate anywhere in the handler. CRC-16 is host-only (never sent). Verification = per-chunk 32-bit byte-sum (host-computed) + post-program read-back verify | whole handler; see updater-load-path.md items 8–11 | H |

State vars (alt RAM): addr `0x1C0570A4`, len `0x1C0570A8`, counter `0x1C0570A0`,
kind `0x1C0570AC`, status `0x1C0570AE`, commit-flag `0x1C0570AF`,
host-sum `0x1C0570B4`, dev-sum `0x1C0570B8`; staging buffer `0x1C0F0000`;
version/date scratch `0x1C056E90`/`0x1C056E94`.

## Application-bank shared flash primitives (for added-code work, app ELF)
- `FUN_1C01BFA8(addr,1)` erase wrapper (4 KB sectors; sector = (addr>>16)*0x10 + ((addr&0xFFFF)>>12))
- `FUN_1C01C1B2(buf,addr,len,0,1)` program + read-back verify (returns −1 on fail)
- `FUN_1C01A826` streaming 512-byte-page writer (256-page / 0x20000 ring, pre-erases 4 KB on 8-page boundaries)
- low level: `FUN_1C01BCC0(1/0)` write-enable gate, `FUN_1C01BCD0` erase, `FUN_1C01C1F4` program
- Hidden test menu (`FUN_1C01E634`, strings at 0x1C042544 "FACTORY RESET"/0x1C042774 "ERASE"/0x1C042788 "WRITE 0x000000") exposes ERASE/WRITE directly; entry combo not found.

## Answers
2. **Per chunk**: 32-bit byte-sum compared to the host op4 value (0x1C00589A). **Overall**:
   each programmed chunk is read-back verified by the flash primitive (0x1C005C74/0x1C005CA2,
   result<0 → reject). **No** CRC/hash/signature/model/version-date enforcement on the device.
   Rejections: byte-sum mismatch → 0x81/2; program-verify fail → 0x81/4; chunk > 0x20000 → code 3.
3. **Erase/write**: erase 64 KB blocks at the host-supplied address, then program the
   staged 0x1C0F0000 buffer to that same address with read-back verify. Order and region
   selection are **host-driven** per chunk (kind F1/F2/F7 → file 0x0/0x90000/0x1D0000). There
   is **no backup-bank copy, no atomic commit flag, and no rollback** in the handler — each
   chunk is erased+written in place. Offset 0 / vector table is written if the host sends it
   (updater sends F1 first in a full update; F1 is skipped when device date == file date).
   Active vs alternate bank is chosen at boot by the 0x1D0FFC word (not re-examined here).
4. **Brick/recovery**: The update handler itself lives in the **alternate (bootloader) bank**,
   which the updater normally does **not** rewrite (F1 is skipped on a version match), so an
   interrupted app-only update (F2/F7) is recoverable by re-running the updater from the
   surviving bootloader. The brick window is a **full update (version mismatch) that rewrites
   F1** — F1 covers the bootstrap (flash 0) *and* the alternate/bootloader bank (0x20000–0x90000);
   an interruption while erasing/writing those leaves no recovery firmware. Minimal state that
   must stay valid: bootstrap/reset vector at flash 0, the alternate bootloader bank
   (0x20000–0x90000), and the bank-select word at 0x1D0FFC. No ROM bootloader was evidenced.
5. **A modified image is accepted by the device.** It must keep size 0x200000, the version
   pattern at 0x1D0000, a plausible date at 0x1DFFFC, the recomputed CRC-16 trailer
   (host-only gate), and correct per-chunk byte-sums — all of which the official tool
   recomputes from the file. The device verifies only the byte-sum + read-back; it writes the
   payload verbatim. Free space: file 0x1E0000–0x1FFFFF is 0xFF (except the 2-byte CRC at
   0x1FFFFE) and is never transmitted — not usable for added code. Added code must go in a
   transmitted region (F1 0x0–0x90000, F2 0x90000–0x1D0000, F7 0x1D0000–0x1E0000); look for
   0xFF padding inside the app bank (F2) for app-only patches that keep the device date equal
   (so F1/bootloader are untouched → lowest brick risk).

## Explicit unknowns
- How the device enters the alternate/bootloader bank to accept an update (reboot trigger
  from the app, the "Push OK" step, or a button combo) — not traced. Whether the app bank
  has a minimal F0 responder that reboots-to-bootloader is unverified.
- Exact `0x83/0x84/0x85` poll sequencing and all 0x81 subcode numbering vs updater error ids.
- The byte-sum accumulation site (where 0x1C0570B8 is incremented during op3) was not pinned;
  only the compare was read. Assumed to sum received data bytes.
- Alt-bank flash primitives (erase at 0x1C005D38, program+verify at literal 0x1C005D40) were
  identified by role, not decompiled to the SPI/serial-flash register level.
- Exact meaning of the 0x1C0000/0x1D0000 split in the program path (likely app-tail vs
  version-metadata region), and the 0x1D0FFC bank-select semantics.
- Physical USB transfer/flash-timing (runtime-only).

## Reproduce
```
python3 -I /tmp/claude-1000/altbuild.py   # builds alt-bank ELF
analyzeHeadless /tmp/claude-1000/alt ALT -import .../squid-v113-alt.elf -processor "SuperH:BE:32:SH-2A"
analyzeHeadless /tmp/claude-1000/alt ALT -process squid-v113-alt.elf -noanalysis -readOnly \
  -scriptPath ~/squid/analysis -postScript LinAlt.java OUT.txt 0x1C005226 300
```
Saved traces: `alt-lin.txt`..`alt-lin4.txt`, `alt-wr.txt`, `alt-prog.txt` (dispatcher + program driver);
`hid-fn1.txt`, `hid-flashcallers.txt`, `hid-dispatch.txt`, `hid-handlers.txt`, `hid-disp2.txt`,
`hid-callers2.txt` (app-bank primitives + project-save callers). Scripts: `InspectAlt.java`, `LinAlt.java`.
