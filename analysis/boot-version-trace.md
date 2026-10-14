# Boot-screen "Version 1.13" trace (v1.13, static only)

2026-10-14, Claude. This was a read-only pass. Nothing was patched, built or run, and no device was touched. The work used the SH disassembly `/tmp/claude-1000/led/main.dis` (temporary; app main code at CPU 0x1C000000) and byte reads of `firmware-v113/windows/trzis1__v113.bin`. For the app bank, file = CPU - 0x1C000000 + 0xB0000.

Tags: **[V]** = verified in disassembly or bytes. **[I]** = inferred.

## 1. Who draws the boot screen, and where the text and glyphs come from

- **[V] The app bank draws it. The bootloader does not.** The routine is `1C007AA8` (file 0xB7AA8). Its only caller is the wrapper `1C00C652`, which bumps the counter `1C05138C` and tail-jumps to it. That wrapper is reached from the screen dispatcher `1C00D0EA` when its 3rd argument is not zero; when it is zero, the normal UI renderer `1C00C65E` runs. `1C00D0EA` is called only from `1C0181CA` (UI update `1C018168`), which is called from the UI task loop `1C016440-1C016494`.
- **[V] Splash timer.** The flag is true while countdown `1C058DC4` is positive. Boot init `1C0162C4` (called at `1C016660`) sets it to 6000, which is about 6 s if the unit is ms **[I]**.
- **[V] What `1C007AA8` emits.** It builds display lists, not pixels:
  - `1C0061DC(1C0514BC, 0)` and `1C0061DC(1C05163C, 1)` reset list 0 and list 1. Each list holds 64 entries of 6 bytes, with state structs at `1C051350 + 28*n`.
  - `1C006A3C(0, x=30, y=15, id=26)` and `1C006A3C(1, x=9, y=19, id=27)` queue **pre-rendered images** as entry `{seq, x, y, 0x0800|id}`.
  - Then it reads the version pointer `@1C0513E0`, which holds FFF8DE84, and builds a local string `{v[0], '.', v[2], v[3], 0}`. **The '.' is forced, so v[1] is never shown here.**
  - `1C006358(1, x=68, y=36, str, max=16, font=0x200)` queues **one entry per character**: `{seq, x, y, 0x200|ASCII}`, with per-font widths from `1C041336/13C0/132C/144A` and A-Z kerning from `1C0512A3/12BE`.
  - `1C007436(1, +2)` shifts list 1 two pixels right. `1C007A94`/`1C007A80` then add the progress-bar commands (bar = `[1C0517D4]*192/1000`).
- **[V] Where the lists go.** `1C00D100(n)` returns the list base, which is stored in `1C058DD8`/`1C058DDC`. `1C0141E6` packs 4 entries from each list into the **panel-B UART frame** (`1C01436C`, SCIF1 `FFFE8800`, see `led-driver-candidates.md`). The SH never rasterizes text. **The OLED glyphs and bitmaps live in the panel-B MCU firmware.**
- **[V/I] Where "Version" comes from.** No string "Version" is sent, so "Version" must be the bitmap of image **26 or 27**. Image 27 at x=9 followed by the digits at x=68 fits "Version 1.13" on one line **[I]**. Image 26 is probably the logo **[I]**.
- **[V/I] Which panel image holds the bitmaps.** The panel MCU images are at file 0x150000 (MCU 0xC0000) and 0x170000 (MCU 0xE0000). Both contain glyph data in OLED page format (column bytes; for example `fe 02 04 08 10 20 40 80 fe` at 0x15300B is an 'N') and the `COMPLETE/UPDATING` strings **[V]**. **The exact "Version" bitmap was not located** **[I]**.
- **[V]** Neither the bootloader (file 0x0-0x90000) nor the alternate bank is involved in this screen.

## 2. Where the displayed "1.13" comes from

- **[V] It comes from file 0xF219C (CPU 0x1C04219C)**:
  - `1C0166AE` copies 16 bytes to the stack, then only 4 of them to `FFF8DE84`.
  - `1C016418` passes `FFF8DE84` as r6 to `1C00D10C`, which stores it at `1C0513B0+0x30 = 1C0513E0`.
  - `1C0513E0` has exactly one reader, the boot screen at `1C007AE0`.
- **[V] It does not come from flash 0x1D0000.** The loader maps 0x1D0000 to `FFF81F00`. That copy has only two readers:
  - the factory/test-menu line "Main x.yy" (`1C01C6A6`, template "Main ." at `1C042434`)
  - a 4-byte copier `1C023568` with no static caller found
- **[V]** The 16-byte copy reads 0xF219C-0xF21AB. Bytes 4-15 are 0x00 and are not stored. The NORTi task descriptor `1C042180` ends at 0x1C04219B, so it does not overlap.

## 3. Safe data-only edit

**Recommendation:** change file **0xF219F** from `33` ('3') to `39` ('9'). The 4 bytes at 0xF219C go from `31 2E 31 33` ("1.13") to `31 2E 31 39` ("1.19"). This is the same length, NUL-terminated, and digits only.

Expected result: the boot screen shows "Version 1.19". The digit glyph is already used by the stock screen, so it will exist.

Every consumer of `FFF8DE84` (all direct literal readers found: `1C01641A`, `1C0166BA`, `1C01670A`, `1C01C52A`):

| Consumer | Effect of "1.19" | Tag |
|---|---|---|
| Boot screen `1C007AA8` (via `1C0513E0`) | "Version 1.19". Uses v[0], v[2], v[3] | V |
| GLOBAL > System Info: getter `1C01C52A` <- `1C021AD6`, `Ver.%s` | "Ver.1.19" | V (path), I (screen) |
| **USB bcdDevice**: `1C02E8CC`, called at `1C01670E`, writes `((v2-'0')<<4 \| (v3-'0'), v0-'0')` into the device descriptor at `1C045334` (file 0xF5334, `00 00` in the file, patched at runtime). The descriptor is VID 2B73 / PID 0025 at 0xF5328 and is served by `1C02EFF6` | bcdDevice changes from 0x0113 to 0x0119. Windows' hardware ID REV_xxxx changes, so it may re-enumerate or re-bind the class driver. Port names are unaffected **[I]** | V (code), I (host) |
| Mac updater `HidCtrl::initialize/matchingCallBack` (via `HidUtil::deviceVersion`, IOHIDDevice VersionNumber) | Stored in HidCtrl+0x1C. No reader was found in MainComponent 0x100003000-0x100012000. The version gate uses F0/01 (flash 0x1D0000) instead | V (store), I (unused) |
| SysEx identity, project header, update check | No path from `FFF8DE84` or `1C045334` was found | I (not exhaustive: base+offset or indirect access not fully excluded) |

Notes:
- Avoid non-digits. The BCD math ORs the raw (char-'0') values, and the font-0x200 glyphs for other characters are unverified.
- Byte 1 ('.') is invisible on the boot screen but visible in System Info.
- The image still needs the CRC-16 trailer and the per-chunk byte-sums recomputed, as in the earlier notes.
- Alternatives are code-immediate edits rather than data, so they are not recommended as the first test. For example, image IDs `mov #26`/`#27` at file 0xB7AC0/0xB7AD4 (`e7 1a`/`e7 1b`), or the text X position `mov #68` at 0xB7B08 (`e5 44`).

## Open
- Locate image 26/27 in the panel-B image, and decide which of 0x150000/0x170000 is the OLED panel.
- Find out how the panel images are loaded.
- Check the Windows `toraizsquidhid.dll` for a bcdDevice check.
