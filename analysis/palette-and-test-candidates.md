# Pad palette and easy "did my modification take effect" candidates (v1.13, static only)

2026-10-14, Claude. Read-only analysis. Nothing was built, patched or run, and no device was touched. Address math for the app bank: file = CPU - 0x1C000000 + 0xB0000. Evidence comes from the SH disassembly `/tmp/claude-1000/led/main.dis`/`fast.dis` (temporary), the Ghidra trace `led-composers.txt` (pad composer `1C03BE6C`, decompile lines 1701-2312), `led-frame-builders.txt`, and byte reads of `firmware-v113/windows/trzis1__v113.bin`. Helper scripts (not part of the repo) are in `/tmp/claude-1000/pal/`.

## A. Pad palette

### A1. The palette table exists in the image, in the panel-A MCU firmware (observed)

The main CPU expands nothing. The panel A frame builder `1C013DFE` copies `FFF8DED1[0..15]` byte for byte into frame bytes 8-0x17 (`1C014028-1C014076`: `mov.b @(n,r5),r0` / `mov.b r0,@(8+n,r14)`). There is no lookup or bit-field step on the SH side.

The table lives in a separate little-endian image at file 0x110000-0x1184FA. That image starts with a table of 4-byte words that looks like a vector table (`1F150C00 ...`, which is 24-bit addresses 0x0Cxxxx). The table is not SH code. It sits inside the F2 update range (0x90000-0x1CFFFF), so the updater sends it. It contains **10 palette blocks of 128 RGB triples each (0x180 bytes)** at file `0x110100, 0x110280, 0x110400, 0x110580, 0x110700, 0x110880, 0x110A00, 0x110B80, 0x110D00, 0x110E80`. Each block is one brightness level: the full-half maximum is 2/4/5/7/14/9/26/33/40/48 and the dim-half maximum is 2/2/2/2/2/3/3/6/9/16. Inferred: the panel picks a block from frame byte 0x36 (PadsFull+1, PadsDim+1).

The block layout is indexed by the full 7-bit pad byte (observed):
- `0x00` and `0x3F` = (0,0,0), off.
- `0x01-0x3E` = colors at the full level.
- `0x40` = (W,W,W), **white**. This is a real entry, not "dim of index 0".
- `0x41-0x7E` = the same hues at the dim level. `0x7F` = **dim white**. Exceptions: `0x73/0x74/0x75` are (1,0,0)/(0,1,0)/(0,0,1) in every block, so they are barely-lit red, green and blue.

The byte order R,G,B is inferred, with high confidence. It is the only order that fits the factory test and all of the manual correlations below.

Image roles (inferred): 0x110000 holds the pad palette, so it is very likely panel A (the pads). 0x150000 and 0x170000 hold font bitmaps, and 0x155C1A has `COMPLETE/UPDATING/PLEASE  UPDATE`, so they are likely the display panel (B). 0x130000 is an image of the same kind with an unknown role. The CPU type is not identified (24-bit vector addresses fit a Renesas R8C/M16C/RL78 class part; unconfirmed). **How the main CPU loads these images into the panel MCUs was not traced** (`01AA`/`02AA` init headers? a separate boot path?).

### A2. Independent firmware-side checks (observed in code, matched to the manual)

| Source (SH) | Values | Meaning | Table RGB |
|---|---|---|---|
| Factory COLORLED CHECK `1C01CE0C-32`: the encoder steps -3..3 and selects `#42`,`#21`,`#2`,`#64` | 42, 21, 2, 0x40 | classic R/G/B/W LED test | (48,0,0) (0,48,0) (0,5,48) (48,48,48) |
| Track Mute (pad mode 2, `1C03C13A-150`) | solo 0x2A, muted 0, normal 0x15, other track soloed 0x55 | manual: solo "lights up in red", mute = off | red / green / dim green |
| Scale (mode 5) | keynote 0x2A, member 0x6A, sounding 0x7F or 0x40 | manual: "brightens in red, dims in red", tapped pad "white" | red, dim red, dim white / white |
| Note Repeat pads (mode 5) | 0x24/0x64, pad 4 0x11/0x51 | manual: "dimly in orange or green" | orange; spring green |
| Chord (mode 6) | 0x2D / 0x6D | manual: "pink" | rose-pink |
| Trigger (mode 8) | 0x27/0x67, Tie +10 = 0x31/0x71, CV slide toggles to 0 on `F4BE` | manual: orange / dim orange, Tie "purple", flashing | red-orange / magenta-pink |
| CC mode (mode 9, `1C03C750-7D0`, knob 0..4) | 0x0B, 0x11, 0x15, 0x18, 0x1F (dim +0x40) | manual: Blue, Light blue, Green, Yellow green, Yellow | cyan, spring green, green, green-chartreuse, yellow. Hue order matches. The manual's names for CC1/CC2 are looser than the RGB |
| Copy/paste sub-modes (0x1000x/0x2000x -> `1C03C812`) | 0x02 | manual: copied and pasted pads "blue" | blue |
| Pattern (mode 3) | current 0x18, saved 0x58, none 0. Pending switch flashes 0x19/0x59 on the beat (`1C03C834/838`) | manual: bright/dim/off; pending "flashes in white" | 0x19 = (37,48,32), a pale whitish green. This explains "white" |
| Pattern Set (mode 4) | 0x1B / 0x5B. Pending flash 0x1C/0x5C | manual: same, flashes white | 0x1C = (42,48,32), a pale whitish yellow-green |
| Track (mode 1) | track table, dim = \|0x40. A triggered pad is shifted by +/-1 | manual: track color; "white" on trigger | +/-1 lands on a pale or light neighbour (e.g. 42 -> 41/43) |
| Time Warp (`1C03C018-05E`; state fn `1C034A66` returns 0 = in preview range, 1 = has data, 2 = none) | 0x40, 0x7F, 0. Cursor 0x3E/0x7E on the beat | manual: in range bright, selectable dim, otherwise off | white / dim white / pale violet |
| Step playhead (default branch, `1C00DAA8 != 0`) | 0x40 | manual: "pad being played lights up in white" | white |
| Mode 0xB (callback `1C00D5E0`), probably the Harmonizer chord display | 0x40 / 0x7F | manual: "chord is shown with white lit" | white / dim white |
| Fixed patterns `1C013CD4` (0x2A/0x40) and `1C013D0C` (0x27/0x31/0x40) | | startup or info screens (inferred) | red/white; red-orange/magenta/white |

### A3. Index to color (full level from block 0x110E80, max 48; dim = index\|0x40)

Confidence: **high** for every entry. The values are read directly from the table, and the table itself is confirmed by the R/G/B/W factory test plus about 12 manual correlations. The names are approximate; real LED rendering is untested.

| Index | R,G,B | Color | Used by (firmware) |
|---|---|---|---|
| 0x00 | 0,0,0 | off | many |
| 0x01 | 16,20,48 | light blue | |
| 0x02 | 0,5,48 | blue | Track #11, copy/paste, factory test |
| 0x03 | 32,34,48 | pale blue | |
| 0x04 | 0,16,48 | blue | |
| 0x05 | 16,34,48 | light azure | |
| 0x06 | 0,26,48 | azure | Track #10, mode-5 global value pads |
| 0x07 | 32,41,48 | pale azure | |
| 0x08 | 0,37,48 | sky | |
| 0x09 | 0,42,48 | cyan | |
| 0x0A | 16,48,45 | light cyan | |
| 0x0B | 0,48,44 | cyan | Track #09, CC1 |
| 0x0C | 32,48,47 | pale cyan | |
| 0x0D-0x0F | 0,48,35 / 31 / 26 | teal | |
| 0x10 | 16,48,28 | light spring green | |
| 0x11 | 0,48,18 | spring green | Track #08, CC2, Note Repeat pad |
| 0x12 | 32,48,38 | pale spring green | |
| 0x13 | 0,48,9 | spring green | |
| 0x14 | 16,48,16 | light green | |
| 0x15 | 0,48,0 | green | Track #07, CC3, mute normal, factory test |
| 0x16 | 32,48,32 | pale green | |
| 0x17 | 26,48,16 | light chartreuse | |
| 0x18 | 14,48,0 | green-chartreuse | Track #06, CC4, Pattern current |
| 0x19 | 37,48,32 | pale green (whitish) | Pattern pending flash |
| 0x1A | 36,48,16 | light yellow-green | |
| 0x1B | 29,48,0 | chartreuse | Track #05, Pattern Set |
| 0x1C | 42,48,32 | pale yellow-green (whitish) | Pattern Set pending flash |
| 0x1D | 38,48,0 | yellow-green | |
| 0x1E | 48,48,16 | light yellow | |
| 0x1F | 48,48,0 | yellow | Track #04, CC5 |
| 0x20 | 48,48,32 | pale yellow | |
| 0x21 | 48,39,0 | yellow-amber | |
| 0x22 | 48,35,0 | amber | |
| 0x23 | 48,34,16 | light amber | |
| 0x24 | 48,26,0 | orange | Track #03, Note Repeat |
| 0x25 | 48,41,32 | pale amber | |
| 0x26 | 48,25,16 | light red-orange | |
| 0x27 | 48,13,0 | red-orange | Track #02, Trigger |
| 0x28 | 48,36,32 | pale red-orange | |
| 0x29 | 48,16,16 | light red | other step-edit modes (default `#41`) |
| 0x2A | 48,0,0 | red | Track #01, solo, scale key, not-ready flash, factory test |
| 0x2B | 48,32,32 | pale red | |
| 0x2C | 48,16,25 | light rose | |
| 0x2D | 48,0,13 | rose/pink | Track #16, Chord |
| 0x2E | 48,32,36 | pale rose | |
| 0x2F | 48,0,22 | pink | |
| 0x30 | 48,16,36 | light magenta-pink | |
| 0x31 | 48,0,30 | magenta-pink | Track #15, Tie |
| 0x32 | 48,32,42 | pale magenta | |
| 0x33 | 48,0,39 | magenta-pink | (dim 0x73 is overridden to faint red, used in a Scale layout) |
| 0x34-0x35 | 48,0,43 / 48,0,48 | magenta | (dim 0x74/0x75 overridden to faint G/B) |
| 0x36 | 42,16,48 | light purple | |
| 0x37 | 38,0,48 | purple | Track #14 |
| 0x38 | 45,32,48 | pale purple | |
| 0x39 | 32,16,48 | light violet | |
| 0x3A | 24,0,48 | violet | Track #13, Transpose (mode 7) |
| 0x3B | 36,32,48 | pale violet | |
| 0x3C | 23,16,48 | light blue-violet | |
| 0x3D | 10,0,48 | blue-violet | Track #12, mode 10 |
| 0x3E | 36,32,48 | pale violet | Time Warp cursor |
| 0x3F | 0,0,0 | off | (0x7F = dim white, special) |
| 0x40 | 48,48,48 | white | playhead, Time Warp range, factory test |

Unknown or unresolved:
- How the colors actually look on the LEDs (PWM mapping and gamma).
- Which of the 10 blocks belongs to which Pads Full/Dim setting.
- Why dim 0x73-0x75 are special.
- The role of the 0x130000 image.
- How the panel images get loaded into the panel MCUs.

The user's HTML `TCOLORS` (#15 white, #16 grey) does **not** match the firmware: #15 = 0x31 magenta-pink and #16 = 0x2D rose.

### A4. Community/service docs offline
None in the repo. There is no service manual, and the manual's Track Color table is an image that was lost in the .txt. The palette above therefore comes from the image itself.

## B. Other low-risk "modification took effect" tests

All CPU addresses are in the app bank 0x1C000000 (file 0xB0000-0xFCFFC), so none fall in the do-not-touch list unless a row says so. All are data or immediate bytes; no new code is needed. Any of them still needs a rebuilt image with the CRC and the per-region byte-sum fixed.

| Rank | Change | CPU / file | What the user sees | Side effects | Conf. |
|---|---|---|---|---|---|
| 1 | Format string `Ver.%s` -> e.g. `Mod.%s` (same length, keep `%s`) | `1C04460C` / **0xF460C** | GLOBAL > System Info shows "Mod.1.13" | Only one literal reference (`1C021C58`, used by the sprintf at `1C021B0E`). Display only | high (code), med (exact screen) |
| 2 | App version string `1.13` -> `1.1X` (4 chars) | `1C04219C` / **0xF219C** | System Info "Ver.1.1X". Copied at boot (`1C0166AE`) to `FFF8DE84`, read by getter `1C01C52A` <- `1C021AD6` | `FFF8DE84` is also passed to init `1C00D10C` (stored at struct+0x30, consumer not traced: possibly SysEx identity or project header). The updater's version comes from flash 0x1D0000 (do-not-touch), not from this string (`device-update-handler.md` item 13) | high (path), med (side effects) |
| 3 | Fixed red/white pad pattern `#42`/`#64` | imm `1C013CD7` / 0xC3CD7, `1C013CD9` / 0xC3CD9 | the startup/info pad pattern (caller `1C017414`) changes color | Only this helper. Use a value from table A3 | high (code), med (when it shows) |
| 4 | Pattern-pending flash `#89`/`#25` | `1C03C835` / 0xEC835, `1C03C839` / 0xEC839 | the flashing pad during a queued pattern change, e.g. `#106`/`#42` = red flash | That branch only | high |
| 5 | Track Color #02 `#39`/`#103` | `1C03D211` / 0xED211, `1C03D219` / 0xED219 | Track mode color for tracks set to #02 | see `led-driver-candidates.md` (avoid entries 0/6/7/14) | high |
| 6 | Factory COLORLED CHECK colors `#21/#2/#64/#42` | imm bytes `1C01CE19`, `1C01CE25`, `1C01CE31`, `1C01CE33` (file 0xCCE19...) | only in the factory menu | entry method into the factory menu is unknown | high (code), low (usable) |
| 7 | Blink phase limits / beat-flash thresholds | see `led-driver-candidates.md` rows 3-4 | every flashing button or pad changes timing | global to all blink users | high (code), med |
| 8 | USB strings, app bank: `TORAIZ SQUID` (iProduct), `Multitrack Sequencer`, `Pioneer DJ Corporation`, serial `000000000001` | `1C0454BC` / **0xF54BC**, `0xF54F0`, `0xF548E`, `0xF54D6` (descriptor table pointers at `1C045518-28`) | host OS / DAW device or port name | **The same strings are also in the alternate bank at file 0x524F6-0x52540, which is inside do-not-touch 0x0-0x90000. Change only the app copy.** Keep the same length (bLength). The host caches names and DAW port mappings may break. Whether the HID updater matches by name is unverified | high (location), med-low (risk) |
| 9 | Panel palette RGB (file 0x110E80...) | panel-A image | colors | Needs the unknown panel-MCU load path. **Not recommended** | — |

Not recommended: `1.13` at 0x1D0000 and the 0x1DFFFC word (do-not-touch, and the updater uses them); the demo project name at file 0x190004 (the demo data format and checks are untraced).

## Can we learn whether the unit accepts modifications without loading a modified image?

**No.** Static evidence shows the device-side update handler does no CRC, hash, signature, model or version check. It checks only the host-supplied per-chunk byte-sum and reads the data back after programming (`device-update-handler.md` items 13-15, high confidence). So it is expected to *store* a correctly byte-summed image. Whether that image boots and behaves can only be shown by loading one. No dry-run or "verify-only" command was found. The only safe rehearsal is to load the unmodified original image.
