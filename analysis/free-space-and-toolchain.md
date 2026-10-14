# SQUID v1.13: free space, toolchain, patch prototype

Offline only (2026-10-13). Original image sha256 `55fadd87...d866f` untouched. "Obs" = read from bytes/tools, "Inf" = inferred.

## 1. Free space in the application bank (F2 = file 0x90000-0x1D0000, F7 = 0x1D0000-0x1E0000)
Map (Obs, from `build_ghidra_app_elf.py` + firmware-map.md): app loader 0x90000 -> 0x1C04D000 (0xFFC); fast code 0xA0000 -> 0xFFF82000 (0xB754); main code/data 0xB0000 -> 0x1C000000 (0x4CFFC, i.e. file 0xB0000-0xFCFFC); metadata 0x1D0000 -> 0xFFF81F00 (0xEC). Code is copied into RAM at boot (Inf: 0x1C000000 is on-chip RAM, so patched bytes in the copied window just load).

| File range | Len | Fill | CPU addr | Usable? |
|---|---:|---|---|---|
| **0xF5608-0xFCFFC** | 0x79F4 (31,220) | 0xFF | 0x1C045608-0x1C04CFFC | **YES - best slot** (inside copied window). Suggest start 0xF5610 (0x1C045610, 16-aligned), 0x79E0 bytes. |
| 0x90180-0x9FFFF | 0xFE80 | 0xFF | none (loader copies only 0xFFC) | No, never loaded |
| 0xAB754-0xAFFFF | 0x48AC | 0xFF | would be 0xFFF8D754+ | No: past fast-code copy, overlaps RAM data (FFF8D7AC etc.) |
| 0xFD000-0xFD17F, 0xFD180-0xFFFFF | 0x2E80 | 0xFF | none (beyond copy end) | No, not loaded |
| 0x100000-0x10FFFF, 0x1184FA.. etc. | many | 0x00/0xFF | none | Resource data (fonts/strings/demo project), not mapped; do not use |
| 0x1D004C-0x1D0FFB, 0x1D1000-0x1DFFFB | 0xFB0 / 0xEFFC | 0xFF | metadata copy only to 0xEC | Transmitted (F7) but never loaded; unused |
| 0x1C0000-0x1CFFFF | 0x10000 | 0xFF | none | Inside F2 but unmapped; no loader copies it |

Reference check (Obs): scanned all 4-byte windows in 0x90000-0x1E0000 for words in 0x1C045200-0x1C04D010. No word >= 0x1C045608 and < 0x1C04D000 exists except 0x1C04CFFF at 0x9016C/0xFD16C (loader length arg 0x4CFFF, not an address). Nearest pointers: 0x1C0455F6, 0x1C0455F8, 0x1C045604 (0xE08C8, 0xE8A18, 0xEDBDC) = ends of the preceding table, all below the slot. Data before the slot ends `...ff 01 01 01 01 ff ff` at 0xF5600. Not checked: Ghidra xrefs (headless not on PATH), computed base+index addressing, whether the C runtime uses 0x1C0456xx-0x1C04CFFF as heap/BSS (likely not: RAM vars seen at 0x1C06xxxx). Unknown, so verify with a Ghidra xref pass before relying on it.

Do not touch (Obs): 0x0-0x90000 (F1: bootstrap/vectors 0x0-0x104, alt bank); loader 0x90000-0x90FFC; fast code 0xA0000-0xAB754 (Inf: likely holds vector/ISR code at FFF82000); 0x1D0000-0x1D00EC (version "1.13" + Info strings, copied to FFF81F00); the 0x1D0FC0 bank-select read window (bootstrap reads 4 longwords there; 0x1D0FC0-0x1D0FFB is FF, 0x1D0FFC-0x1D0FFF is "1.13" = 0x312E3133, NOT all FF; see independent-review.md); **0x1DFFFC-0x1DFFFF date word `20 18 12 20`** (keep equal so updater skips F1); 0x1FFFFE CRC-16 trailer (host-only, must be recomputed after any change; original 0x73FA). Where the SH vector table (VBR) lives is not established.

## 2. Toolchain
Installed at start: none (no sh-elf-*, sh4-linux-gnu-*, clang, llvm-mc).
- **Binutils (done, user dir):** `dnf download binutils-sh-linux-gnu` (Fedora 44 updates, 2.46.1) then `rpm2cpio *.rpm | cpio -idm` into `~/squid/build/tc/` (nothing installed system-wide). Use with `LD_LIBRARY_PATH=~/squid/build/tc/usr/lib64 ~/squid/build/tc/usr/bin/sh-linux-gnu-as --isa=sh2a --big`. Test: `mov #1,r0; rts; nop` -> `e0 01 00 0b 00 09`, disassembles back with `objdump -m sh2a`.
- **Encoding match (Obs):** disassembled firmware ranges 0xB00D8+0x600, 0xB1000+0x1000, 0xC6CEE+0x800 with `objdump -b binary -m sh2a -EB`, reassembled each non-pc-relative instruction: 2714 identical, 0 differing. The only failures were pc-relative `mov.l/mov.w @(disp,pc)` when assembled in isolation (expected; branches/mova skipped, 556 total). Includes SH-2A forms (movi20 etc. appeared in ranges; not individually listed).
- **GCC (verified, throwaway container):** `podman run --rm -v DIR:/w:Z docker.io/library/debian:trixie-slim sh -c 'apt-get update && apt-get install -y gcc-sh-elf && cd /w && sh-elf-gcc -m2a -mb -Os -ffreestanding -nostdlib -c t.c'` (Debian gcc-sh-elf 13.3.0, binutils-sh-elf 2.44). Produced big-endian SH-2A code. Image `debian:trixie-slim` now cached in podman (remove with `podman rmi`). Note: firmware writes FPSCR (SH2A-FPU), so match the ABI of existing code before calling C from patches (-m2a vs -m2a-single / -m2a-nofpu; unverified which firmware uses). Fedora has no gcc for sh.

## 3. Patch prototype
`build/patch_image.py ORIG OUT [patches.json]` (stdlib, run with `-I`); `patch_image.py ORIG` runs the self-check. Patches are `[["0xF5610","0009"]]`. Refuses: outside 0x90000-0x1D0000, anything touching 0x1DFFFC, OUT==ORIG. Recomputes CRC-16 (0x1021, init 0, LE at 0x1FFFFE). Prints per-chunk 32-bit byte-sums for F1/F2/F7. Self-check verified: empty patch list is byte-identical, original CRC reproduces 0x73FA, guards fire.
Unknowns: chunk boundaries for sums are Inf (restart at each region start, 0x20000 max; matches op2 doc but not confirmed against updater emitted values, and sum is a plain unsigned byte-sum mod 2^32, Inf); F7 is cut to 0x1000 when dates match. Patches only reach F2, so F7 sums never change. To change the date or F7 would need guard changes plus loses the F1-skip.
Next step for added features: pick hook points (replace a `jsr`/literal in existing code to branch into the slot at 0x1C045610), write SH-2A asm/C, assemble with the toolchain above, `objcopy -O binary`, feed to patch_image.py.
