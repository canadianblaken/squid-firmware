# SQUID v1.13 — code-cave safety (file 0xF5608-0xFCFFC / CPU 0x1C045608-0x1C04CFFC)

Static/offline only. Original image untouched (sha256 `55fadd87...d866f`). Firmware read as
data; loader/updater instructions read read-only from saved disassembly. "Obs" = read from
bytes/asm; "Inf" = inferred. No device, updater, or firmware code executed.

## Verdict
**The cave is SAFE to use for added code/data.** It is the 0xFF tail of the application
code/rodata image, copied verbatim into on-chip RAM at boot and executed from there; nothing
references it and no BSS/heap/stack overlaps it. Recommended usable subrange
**0x1C045610-0x1C04CFFC (file 0xF5610-0xFCFFC, 0x79EC/31,212 bytes, 16-aligned start).**
The per-chunk byte-sum layout in `patch_image.py` is **CONFIRMED correct** (no fix needed).

## Evidence table
| # | Finding | Evidence | Conf |
|---|---|---|---|
| 1 | **App loader copies flash file 0xB0000 -> RAM 0x1C000000, dest range 0x1C000000..0x1C04CFFF** (len 0x4CFFC). Then `jmp @r2` to entry 0x1C016CEE, which is **inside** that RAM window. | `startup-selected.asm` file 0x90044 `r6=1c04cfff`(end), 0x90048 `r5=1c000000`(dest), 0x9006c `jmp` to literal `1c016cee`; mask `r7=1fffffff`. build_ghidra_app_elf.py regions. | H (Obs) |
| 2 | **Cave is the end of the copied window.** 0xF5608-0xB0000 = 0x45608 -> 0x1C045608; 0xFCFFC-0xB0000 = 0x4CFFC = copy end. So the cave IS copied to RAM and loaded as 0xFF, not zero-filled BSS. | arithmetic vs item 1; cave fill verified all-0xFF 0xF5608..0xFCFFF, 0 non-FF bytes. | H (Obs) |
| 3 | **Execution is from the RAM copy, not XIP from flash.** Entry 0x1C016CEE and all app code live in the 0x1C00xxxx on-chip RAM image the loader just wrote. Fast code is a *separate* RAM copy: file 0xA0000 -> 0xFFF82000..0xFFF8D754 (0xB754). | item 1; `startup-selected.asm` 0x9001c `r6=fff8d754`(end)/`r5=fff82000`(dest); firmware-map. | H (Obs) |
| 4 | **Regions executed from RAM:** main code/data 0x1C000000-0x1C04CFFF *and* fast code 0xFFF82000-0xFFF8D754. **Nothing runs XIP from memory-mapped flash in the app run image** — loader stages everything to RAM first. | items 1,3. | H (Obs) |
| 5 | **No runtime array/heap/BSS overlaps the cave.** Lowest aligned RAM data pointer referenced anywhere in the loaded image is **0x1C04E000** (file 0xd4524) — *above* the loader (0x1C04D000-0x1C04DFFC). Dense runtime data/BSS begins at **0x1C050000+**; trace arrays (1C066F60, 1C067060, 1C068360, 1C06C6A4, 1C06CBxx) are all >= 0x1C066xxx. Stack = 0xFFF90000. **No referenced RAM address falls in [0x1C045608, 0x1C04CFFF).** | aligned 32-bit scan of loader+fastcode+main windows; `scan.py`/`scan2.py`. | H (Obs) |
| 6 | **No code or data pointer targets the cave.** Only sub-cave rodata table ends at 0x1C045604. The single apparent in-cave hit (0x1C04AC84) is at an **odd file offset (0xea429) inside SH-2A code** — two instruction halfwords misread as a word, not a literal; its would-be target file 0xFAC84 is 0xFF. | scan2.py; 0xea429 context `...2460 [1c04ac84] 9244...`; cpu 0x1C03A429 is code. | H (Obs) |
| 7 | **Cave fill is clean 0xFF.** FF run begins exactly at file 0xF5608 (0x1C045608), unbroken to 0xFCFFF. 8 zero bytes at 0xF5601-0xF5608 are the rodata-table tail; start at 0xF5610 to clear them. | ff.py. | H (Obs) |
| 8 | **Updater chunk = min(remaining, 0x20000), restart per region** (region length table at obj+0x488, indexed by region). | `updater-selected.asm` run 0x10000d8e1 `cmp rax,0x20001` / `mov esi,0x20000` / `cmovge rax,rsi`. | H (Obs) |
| 9 | **op4 sum = plain unsigned byte-sum mod 2^32.** SSE loop zero-extends each byte (`punpcklbw`/`punpcklwd` against zeroed xmm4) then `paddd`; byte tail loop adds the rest; stored at obj+0x520, later sent LE. Device accumulates + compares (0x1C00589A, LE at 0x1C0570B4). | `updater-selected.asm` 0x10000e320-e37e; device-update-handler.md items 7-8. | H (Obs) |
| 10 | **patch_image.py layout matches items 8-9.** REGIONS F1(0,0x90000)/F2(0x90000,0x140000)/F7(0x1D0000,0x10000), CHUNK 0x20000 restart per region, `sum(img[a:b])&0xFFFFFFFF`. F2 = 10x0x20000 exact; F1 = 4x0x20000 + 0x10000; F7 = 1x0x10000. No change required; self-check passes. | patch_image.py chunk_sums/apply vs items 8-9. | H |

## Answers to the five questions
1. **Copy routine:** application loader (file 0x90000, runtime 0x1C04D000) copies file 0xB0000 ->
   RAM 0x1C000000 for 0x4CFFC bytes (dest end 0x1C04CFFF), then jumps to entry 0x1C016CEE. It
   **does** copy the cave (the cave is the window's 0xFF tail). Execution is from the **RAM copy**
   (on-chip RAM at 0x1C0xxxxx), not XIP from flash. RAM-executed regions: main code/data
   0x1C000000-0x1C04CFFF and fast code 0xFFF82000-0xFFF8D754 (file 0xA0000). No app region runs
   in place from flash.
2. **Runtime use of the destination:** none inside the cave. Heap/BSS/data begins at 0x1C050000+
   (lowest pointer 0x1C04E000, above the 0x1C04D000 loader); trace arrays 1C067060 / 1C06C6A4 /
   1C06CBxx and the FFF8xxxx region are all outside and above. The copied cave window is a pure
   flash mirror (0xFF) that overwrites nothing else.
3. **Executable in place:** yes — after the boot copy the cave is on-chip RAM at 0x1C045610 and is
   fetched as instructions like the rest of the app image (loader masks to the 0x1FFFFFFF
   cacheable P0 region). For a **static flash patch there is no runtime cache-flush or permission
   concern**: the loader repopulates RAM every boot and normal execution fills I-cache; we are not
   doing runtime self-modifying code. Alignment: SH-2A code is 2-byte aligned, `mov.l/mov.w
   @(disp,pc)` literals must sit at 4-byte-aligned targets — a 16-aligned cave start satisfies both.
   (Caveat: any *future* code that writes instructions to RAM at runtime would need SH-2A cache
   handling; not applicable here.)
4. **Recommended subrange + hook:** use **0x1C045610-0x1C04CFFC (file 0xF5610-0xFCFFC, ~0x79E0
   bytes)**, 16-aligned. Hook by overwriting a short sequence at a known RAM-resident function with
   a register-indirect jump (bra/bsr only reach +/-4KB, too short): place the 4-byte literal
   `0x1C045610` in a nearby 4-aligned pool, `mov.l @(disp,pc),rN; jmp @rN` (or `jsr @rN`), and
   **relocate the single branch-delay-slot instruction** that executes after the jump. In the cave
   trampoline, re-execute the displaced original instruction(s) and return; any PC-relative literal
   load (`mov.l/mov.w @(disp,pc)`, `mova`) among the displaced bytes must be re-pointed or have its
   literal copied into the cave's own pool, since PC changed. Cleanest target is an existing
   `jsr @rN`/`bsr` call site redirected to a cave trampoline that does the original call plus the
   added work, preserving PR and caller-saved regs.
5. **Byte-sum layout:** CONFIRMED correct (items 8-10). Chunk = min(remaining, 0x20000) restarting
   at each region start; sum = plain unsigned byte-sum mod 2^32. `patch_image.py` is right as-is;
   **no change made**. Self-check still passes.

## Explicit unknowns
- Correction (independent-review.md): do-not-touch also includes the bank-select read window at 0x1D0FC0 (0x1D0FFC holds "1.13" = 0x312E3133, not 0xFF). An F2 cave patch touches none of 0x1D0000-0x1E0000.
- BSS/init now traced: see init-and-userdata.md (no clear/copy loop reaches the cave).
- The C-runtime BSS *clear loop* bounds were not read instruction-by-instruction; safety rests on
  (a) the cave being part of the flash->RAM code copy (loaded, not zeroed) and (b) zero referenced
  addresses in [0x1C045608, 0x1C04CFFF). A direct Ghidra read of the init at entry 0x1C016CEE could
  make this H+ instead of H.
- Whether 0x1C000000 on-chip RAM is write-through/copy-back cached on this exact (unidentified)
  Renesas part — irrelevant to a static flash patch, relevant only if runtime SMC is ever added.
- Physical flash-sector/erase granularity vs the 0x20000 sum chunk (device erases 0x10000 blocks);
  does not affect the host-side byte-sum layout.
- F7 is truncated to 0x1000 on a date match (updater); patch_image sums the full 0x10000. Harmless
  for app-bank (F2) patches since F1/F7 sums are reference-only and unchanged by F2 edits; already
  documented in the script's notes.
