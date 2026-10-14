# SQUID v1.13 - C-runtime init trace and user-data location

Static/offline only (objdump SH-2A big-endian on raw firmware, VMA=file offset; Ghidra headless on a /tmp copy of the project). Originals untouched. Obs = read from bytes/asm; Inf = inferred. App-bank CPU = file - 0xB0000 + 0x1C000000. Trace: `init-userdata-trace.txt`.

## 1. Init / BSS (entry 0x1C016CEE, file 0xC6CEE)

| # | Finding | Evidence | Tag |
|---|---|---|---|
| 1 | Entry is a thin start: `r4,r5` args, optional write 0xA000 to FFF8DE74, calls 1C023F04 (arg r5=1C04212C), 1C024E94, 1C01C64E, 1C025310, `stc/ldc sr` (clear IMASK), 1C0244C6, 1C024ABA, then spin `bra`. No inline clear loop. | file 0xC6CEE-0xC6D3E | Obs |
| 2 | **No BSS/data clear loop exists in the startup path.** The app image is only copied (loader DMA: flash 0xB0000 -> 0x1C000000 len 0x4CFFC; fast code -> FFF82000..FFF8D754; 0x1D0000 -> FFF81F00). Runtime statics are initialised by explicit stores (kernel init). | 1C023F04 -> 1C024378, 1C024750; 1C024750 body d4750-d4ab4 | Obs |
| 3 | 1C024378 (RTOS vector setup): VBR = **0x1C04E000**; fills 960 longs (0xF00 B) from 0x1C04E100 with default handler 1C024348, i.e. 0x1C04E100-0x1C04F000. Memset-like loop, range above the cave. | d4378-d43a6 (`movi20 #960`, `dt`, `mov.l r0,@r2+`, `ldc r4,vbr`) | Obs |
| 4 | 1C024750 (kernel init, config table at 0x1C0420BC): clears **0x200 bytes backwards** from (sp_arg - 0x160) (stack area, FFF8xxxx; sp arg = r15+4 from 1C023F04); hands FFF8F800..sp to pool init 1C02DC34; pools: 0x1C04EF00 (len 0x100), 0x1C05567C (len 0xF34), 0x1C0565B4. Kernel statics 0x1C0551xx-0x1C0556xx set by single stores. | d4750-d4ab4; config words at file 0xF20BC/0xF2118 | Obs |
| 5 | Stack: top region FFF8Fxxx-FFF90000 (SRAM, not 0x1C04xxxx). Heap/pools: 0x1C04EF00, 0x1C05567C. All >= 0x1C04E000. | items 3-4; prior notes for stack top | Obs (pool) / Inf (exact SP) |
| 6 | **Nothing in startup touches 0x1C045610-0x1C04CFFC.** Highest RW variable below the cave: **0x1C045604-07 (rand() LCG seed, 41C64E6D/12345)**, also 0x1C0455E8 (12-byte array cleared by a `dt` loop at 0xE06AA), 0x1C0455F6 (byte). RW data therefore ends at 0x1C045608; the file zeros at 0xF5601-0xF5607 are that seed's initial value. | edbc0-edbd8; e06a4-e06ae; e08c4/e08c8 pools | Obs |
| 7 | Literal scan of 0x90000-0x1D0000 for words in 0x1C043000-0x1C050000: none in [0x1C045608,0x1C04D000) except loader length 0x1C04CFFF (0x9016C/0xFD16C); first word above the cave is 0x1C04E000. Memset/copy loops found by `dt`+store scan (230 hits, all use pool pointers outside the cave or are small fixed-size). | scan this session | Obs |
| 8 | Fast-code (FFF82000..) loops: 8 `dt`+store hits, all FFF8xxxx. | scan | Obs |

Not proven: indexed accesses (base+computed offset) from tables below the cave cannot be fully excluded by a literal scan; only 5 startup callees were skimmed for pool addresses (1C024E94, 1C01C64E, 1C025310, 1C0244C6, 1C024ABA: all pointers 0x1C0420xx-0x1C0565xx or FFF81F00, none in cave). Verdict: cave remains safe; start at 0x1C045610 is 8+ bytes clear of the seed (0x1C045604-07).

## 2. User data in non-volatile memory

Hardware (Obs): SPI flash driver at file 0xCBEF6-0xCC3B6 (CPU 0x1C01BEF6+): channel table at 0x1C045270 = {0xFFFF8000, 0xFFFF8800}; read cmd 0x0B (1C01BFDA(buf,addr,len,?,dev)), 4 KB sector erase 1C01BFA8(addr,dev), page program 256 B via 1C01C1B2->1C01C1F4(buf,addr,len,eraseflag,dev=stack arg). The boot loader uses channel 0 (0xFFFF8000) for the program flash. Device index 0 = channel 0, index 1 = channel 1.

| # | Store | Device | Range | Evidence | Tag |
|---|---|---|---|---|---|
| A | Settings-like block: erase 4 KB then write 0x1000 B from RAM buffer 0x1C04F000; loaded at boot by 0xBE9C0 (reads 0x1000 B at same addr into 0x1C04F000; parses header 2 shorts + 16 x 12-byte records, FFFF = empty) | 0 (boot flash) | **file 0x1C0000-0x1C0FFF** (rest of 0x1C0000-0x1CFFFF all FF in image, presumably reserved) | 0xBE96C-0xBE98E (erase r4=28<<16, write r5=28<<16, len 0x1000, stack dev 0), 0xBE9C0 | Obs (addresses) / Inf (that it is user settings) |
| B | Block store with wear-levelling map: 512 B logical sectors (`sector<<9`), 256-entry map at 0x1C0605A4, erase groups of 4 KB, 128 KB total (0x0-0x1FFFF; format loop at 0xCBAE4 erases 0..0x1FFFF step 0x1000) | **1 (channel 1, 0xFFFF8800)** | dev1 0x00000-0x1FFFF | FUN_1C01A826 (calls write with dev=1), 1C01A96C, CBAE4, CE6BC/CE72C/CE83C/CE862 (all dev=1) | Obs (dev index and extent) / Inf (that this holds projects/patterns) |
| C | Version/date reads and in-app flash-update path (chunk buffer 0x1C0F0000; 0x1C0000/0x1D0000 split; erase/write of 0x1F0000 and 0x1F8000 sectors at 0xD32AC-0xD3526) | 0 | 0x1F0000, 0x1F8000 (outside any transmitted update range) and update chunk addresses | 0xCF4E2-0xCF53A, 0xD3354-0xD3526 | Obs; purpose Inf |

Update-handler interaction (alt-bank handler, file 0x45C28-0x45CBE, Obs): `r6 = (0x1C0000 <= addr < 0x1D0000)`. If a chunk **starts** inside 0x1C0000-0x1CFFFF it is ACKed (status 0x85) with **no program/erase**. If it straddles (starts below, ends in (0x1C0000,0x1D0000]) it programs with len = end-0x1C0000 from addr. The standard F2 last chunk (0x1B0000, len 0x20000) takes this path with len 0x10000, i.e. it writes 0x1B0000-0x1BFFFF and **never writes 0x1C0000-0x1CFFFF** (Inf: the primitive 1C004D36 args are (buf=0x1C0F0000, addr, len, erase=1, dev=0), same shape as 1C01C1B2; not re-verified). This is consistent with 0x1C0000-0x1CFFFF being a deliberately preserved user area.

### Overlap with 0x90000-0x1E0000
- Dev1 store (B): no overlap (different SPI channel). Inf: separate chip; not proven that ch1 is a physically distinct device.
- Dev0 store (A) 0x1C0000-0x1C0FFF lies inside the F2 range (0x90000-0x1D0000) **in file-offset terms**, but the handler skips programming 0x1C0000-0x1CFFFF (above). Image bytes there are all 0xFF.
- 0x1F0000/0x1F8000 (C): outside F1/F2/F7.

### What an F2/F7-only update does to saved data
- Projects/patterns (dev1, Inf): untouched.
- Dev0 settings block 0x1C0000: untouched under the straddle logic above (Inf, from static reading; the 0x1B0000 chunk programs only 0x1B0000-0x1BFFFF). F7 (0x1D0000+) does not reach it. Risk: if a hand-built update uses non-standard chunk addresses, the 0x1C0000 split decides; keep chunks as in patch_image.py.
- F1 (full) rewrites 0x0-0x90000 only; no user-data overlap found.

## Unknowns
- Exact semantics of settings block A and what B stores (names, formats); only addresses/devices are certain.
- Whether primitive 1C004D36 erase granularity (sector 4 KB vs 64 KB block) expands the 0x1B0000-0x1BFFFF erase into 0x1C0000 (file notes say 0x10000 blocks; len 0x10000 at 0x1B0000 is block-aligned, so no spill expected).
- Physical chip count (ch0 vs ch1), and the 0x1F0000/0x1F8000 role.
- Exact initial SP (bootstrap), not re-read here.
