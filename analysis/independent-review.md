# SQUID v1.13 — independent falsification review

Reviewer re-derived every load-bearing claim from raw firmware bytes
(`firmware-v113/windows/trzis1__v113.bin`, sha256 `55fadd87…d866f`, size 0x200000)
and the Mach-O updater, **not** from the notes. Static/offline only; nothing executed;
originals untouched; work under `/tmp/claude-1000`, python `-I`.

Tooling: `sh-linux-gnu-objdump -m sh2a -EB` (binutils in `~/squid/build/tc`) on raw
firmware bytes, disassembled with **VMA = file offset** (and addresses translated by hand —
app bank file→CPU: `+0x1BF50000`→`0x1C000000` i.e. CPU = file−0xB0000+0x1C000000; alt bank
file 0x40000→CPU 0x1C000000 i.e. CPU = file−0x40000+0x1C000000). `objdump -b binary -m
i386:x86-64` on the Mach-O `__TEXT` (vmaddr 0x100000000 @ fileoff 0). **Caveat found:**
`objdump -b binary` mis-reads bytes when `--adjust-vma` and `--start-address` are combined;
all results below use file-offset VMA with correct alignment.

## Verdict table
| # | Claim | Result | Key independent evidence |
|---|-------|--------|--------------------------|
| 1 | Device verifies ONLY per-chunk 32-bit byte-sum + post-program read-back; no CRC/hash/sig/model/version/date; no lower-bound address guard | **VERIFIED** | byte-sum `cmp/eq r6,r5` at file 0x4589E (CPU 0x1C00589E), eq→0x80 / ne→0x81 sub-2; readback `jsr` program-primitive (pool→0x1C004D36) then `exts.b;cmp/pz`, fail→sub-4 at file 0x45C74/0x45CA2; addr bounds compared ONLY vs 0x1C0000 (`cmp/hs`, `r5=28<<16`) and 0x1D0000 (`r4=29<<16`) at file 0x45C30/0x45C3E, no low bound → addr 0 (r2=0) falls to normal program path 0x45C94; kind = `movu.b;shad #-4;cmp/eq #15` at 0x45242; whole-image scan: **0 `cmp/eq #F1/#F2/#F7`** (all `cmp/eq #F0` hits are in font data 0x152xxx/0x177xxx) |
| 2 | Updater sends F1 only on date-word mismatch; unchanged date → only F2/F7 | **VERIFIED** (with refinement) | CheckDeviceVersion @0x10000F7DA `cmp eax,[rbx+0x508]` (device date vs file date) `jne 0xf85d`: EQUAL→`[rbx+0x4a0]=3` (F1 skip) + `[rbx+0x4d8]=0x1000` (F7 truncated); MISMATCH→`[rbx+0x4a0]=2` (F1 send). ParseUpdFile (0xf110) default-sets all three region states (0x4a0/0x4c8/0x4f0)=2 (send). run() sends a region iff `cmp [rbx+rdx*8+0x4a0],2` (0xd7e6/0xd868/0xd8b6). Only writers of F1 state: init(0), ParseUpdFile(2), CheckDeviceVersion(3/2). **Refinement:** F1 also stays =2 (sent) if the version query never returns 0x82 (error 0xE41/0xE36 path) — i.e. F1 is sent unless device reports a date word *equal* to the file's. |
| 3 | Loader copies flash 0xB0000→RAM 0x1C000000 len 0x4CFFC, jumps 0x1C016CEE; nothing runs in place; file 0xF5610-0xFCFFC all 0xFF & unreferenced; BSS/heap ≥0x1C050000 not overlapping cave | **VERIFIED** (BSS bound corroborated, not instruction-traced) | loader @file 0x90040: `r7=0x1FFFFFFF` mask, `r6=0x1C04CFFF` end, `r5=0x1C000000` dest, `r4=11<<16=0xB0000` src, `bsr` copy, then 2nd copy `r4=29<<16=0x1D0000`→`r5=0xFFF81F00` (len ~0xEC), then @0x90068 `mov.l→r2=0x1C016CEE; jmp @r2`; fast-code pool `0xA0000→0xFFF82000..0xFFF8D754` (len 0xB754). Cave 0xF5610-0xFCFFC: **0 non-FF bytes**. Pointer scan of whole copied image+loader+fastcode: **no word in [0x1C045610,0x1C04CFFF]** except (a) 0x1C04CFFF = loader length arg at 0x9016C and (b) 0x1C04AC84 at **odd** file 0xEA429 (two code halfwords misread). Lowest data word in [0x1C04D000,0x1C060000)=0x1C04E000 (one pointer above loader; dense data at 0x1C050000+). Entry 0x1C016CEE (file 0xC6CEE) is a thin init calling sub-inits then a spin loop — BSS-clear is inside a callee, not read instruction-by-instruction. |
| 4 | Do-not-touch list is complete | **PARTIALLY** | Boot-read fields confirmed; two issues (below). The list is sufficient for an **F2-cave patch** (which touches none of 0x1D0000-0x1E0000), but contains a factual error and an imprecise read range. |
| 5 | Interrupted F2/F7-only update is recoverable via bootloader | **VERIFIED** | Bootstrap @file 0xFFF80824/0xFFF80854 reads 4 longwords at flash 0xB0000 and 0x1D0000 and tests `cmp/eq #-1` (all-0xFF ⇒ erased/invalid) → on invalid selects the alternate bank default (`movi20 #0xE001<<16|0xA000`). The F0–F7 update handler lives in the **alt bank** (handler code at file 0x45xxx ∈ [0x20000,0x90000]); F2/F7 write only 0x90000-0x1E0000, so the bootstrap (file 0) and alt/bootloader bank (0x20000-0x90000) survive an interrupted F2/F7 flash → unit boots the surviving bootloader and re-accepts an update. The brick window is F1 (version-mismatch full update), which erases bootstrap+alt bank. |

## Discrepancies found in the notes
1. **0x1D0FFC is NOT "all 0xFF".** `updater-load-path.md` item 12 and `free-space-and-toolchain.md`
   §Do-not-touch both state 0x1D0FFC is all-FF. Raw bytes at file 0x1D0FFC = `31 2E 31 33`
   = **"1.13"** (same version string as 0x1D0000). The bank-select / version compare is real,
   but the characterization of that word is wrong.
2. **Bank-select read range is 0x1D0FC0, not a single word at 0x1D0FFC.** Bootstrap loads
   `movi20s #0x1D0FC0` (file 0xFFF80884) and reads 4 longwords; the notes pinned it as 0x1D0FFC.
   0x1D0FC0-0x1D0FFB is FF and 0x1D0FFC-0x1D0FFF is "1.13" — so the real compare window spans
   0x1D0FC0-0x1D0FD0+. Not load-bearing for an F2 patch, but the do-not-touch entry should read
   "version/metadata block near 0x1D0000 and the 0x1D0FC0 compare window," not one FF word.
3. **Minor wording:** `device-update-handler.md` item 10 cites "primitive at literal 0x1C005D40";
   0x1C005D40 is a *pool slot* whose value 0x1C004D36 is the actual program primitive. Confirmed, just imprecise.
4. All other cited addresses/branches (byte-sum verify, readback, address bounds, kind test,
   loader copy constants, jump target, version-compare) reproduced **exactly** from raw bytes.

## Residual risks
- **BSS-clear bounds not traced to the instruction.** Cave safety rests on (a) the cave being
  part of the boot flash→RAM copy (verified, loaded as 0xFF) and (b) the independent pointer
  scan finding nothing referencing [0x1C045610,0x1C04CFFF] (verified). The zero/clear loop inside
  the 0x1C016CEE callees was not read; a clear with an end ≥0x1C045610 would zero the cave. The
  lowest referenced data address (0x1C04E000) sits *above* the cave, which strongly implies the
  clear starts ≥0x1C04E000, but this is corroboration, not proof. Low risk.
- **User project/pattern flash location not pinned.** Runtime save primitives
  (FUN_1C01A826 / FUN_1C01C1B2) write to flash, but their live target addresses were not
  resolved. No user-data region was identified inside the transmitted image ranges
  (F2 0x90000-0x1D0000 = loader/fastcode/code/resources; F7 0x1D0000-0x1E0000 = metadata), so an
  F2/F7 flash of an otherwise-identical image should not clobber user data — but this is
  absence-evidence, not a positive bound on where saves land. Verify before a destructive test.
- **"No crypto on device" is a negative.** Fully verified for the *verification path* (byte-sum +
  readback only) and no F1/F2/F7/CRC constants exist in the handler; a hidden check elsewhere
  cannot be excluded statically with certainty. Low risk given the compact, fully-read dispatcher.

## Go / No-Go: first F2-only (code-cave) flash on a SPARE unit
**GO**, conditioned:
1. Patch only inside the cave **0x1C045610-0x1C04CFFC** (file 0xF5610-0xFCFFC); touch nothing in
   0x90000 (loader), 0xA0000-0xAB754 (fast code), or 0x1D0000-0x1E0000.
2. Keep the date word 0x1DFFFC (`20 18 12 20`) **unchanged** so the updater skips F1 (bootloader
   stays intact — this is what keeps an interruption recoverable).
3. Recompute the CRC-16 (0x1021/init0, LE at 0x1FFFFE) and the per-chunk byte-sums — `patch_image.py`
   does both; its layout matches the updater (chunk = min(rem,0x20000) per region) and the device
   byte-sum (plain unsigned sum mod 2^32), independently confirmed.
4. Spare unit only, because the two residual risks (BSS-clear bounds, user-data location) are
   corroborated but not proven. An interrupted F2 flash is recoverable; an interrupted F1 (full)
   update is not, so never let the date word change on the first attempt.
