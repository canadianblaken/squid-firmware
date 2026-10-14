# Front-panel LED path and candidate "test" changes (v1.13, static only)

2026-10-14, Claude. Read-only. No image was built or patched, nothing was run on a device. Address math: file = CPU - 0x1C000000 + 0xB0000. Evidence: Ghidra InspectFocused traces `led-panel-uart.txt`, `led-frame-builders.txt`, `led-state-refs.txt`, `led-ui-entry.txt`, `led-composers.txt` (all in analysis/), plus a full sh2a objdump of the app slices at `/tmp/claude-1000/led/main.dis` and `fast.dis` (temporary). Branch and shift semantics below were checked against SH instructions. Note that Ghidra mis-renders the `shad` idiom when the shift is 0: it shows 0 where the CPU produces 3.

## 1. How the LEDs are driven (observed)

The main CPU does not drive LEDs directly. It sends 64-byte frames to two panel MCUs. Version strings `Main` / `PnlA` / `PnlB` are at 1C042434..5C, and a factory menu has `COLORLED CHECK`, `LED&SW CHECK` and `DIMMER`.

| Layer | Address | Role |
|---|---|---|
| UART link init | `1C015040` | SCIF4 `FFFEA000` = panel A (ch0) and SCIF1 `FFFE8800` = panel B (ch1), SCBRR=8. DMA TX goes from buffers `1C05444C` / `1C05454C` (4 x 0x40 each) to SCFTDR. DMA RX goes from SCFRDR to `FFF8DB9C` / `FFF8DC9C`. DMAC regs `FFFE10A0..10FF`. |
| Link task | `1C014BF6(ch)` | Loops with an RTOS delay of 1 (`1C02C18C`). When a TX slot is free it calls the frame builder at `1C01513A` (A) and `1C01515C` (B). DMA completion handlers are `1C0149EA` / `1C014AD0`. |
| Panel A frame | `1C013DFE` | Header `05AA` (or `01AA` init). Bytes 3-7 hold lamp[0x00..0x12] as 2-bit fields, 4 per byte, in permuted order. Bytes 8-0x17 are a raw copy of pad[0..15]. Byte 0x36 = (PadsFull+1)<<4 \| (PadsDim+1), sent only if `FFF8DE74[0]==0 && [1]==0`. Byte 0x3B low nibble = ButtonsFull. Bytes 0x3C-3D = `5AA5`. Bytes 0x3E-3F = checksum `1C013DD4`, a sum of even and odd bytes. |
| Panel B frame | `1C01436C` | Header `07AA` (or `02AA`). Bytes 3-8 hold lamp[0x13..0x2A] as 2-bit fields. Byte 9 = Display brightness code (setting 0/1/2/3 -> 5/9/0xC/0xE). Bytes 0x0A+ carry display text/commands (`1C0141E6`). Byte 0x3B low nibble = ButtonsFull. |
| Brightness getters | `1C021474` (Display), `1C021532` (Buttons Full), `1C021576` (Pads Full), `1C0215BA` (Pads Dim) | Read settings bytes 0..3. While the matching menu row is being edited they return the live edit value, so you see a preview. |
| LED RAM | `FFF8DE98[57]` lamps, `FFF8DED1[16]` pads (= DE98+0x39) | Fill helpers: `1C013CB8` (all lamps = r4), `1C013CC6` (all pads = r4). Fixed patterns: `1C013CD4` (0x2A/0x40 checker, called from `1C017414`, startup/info screen) and `1C013D0C` (0x27/0x31/0x40, called from `1C01C55E` when `*1C045278==1`). |
| UI wiring | `1C0169CE..F4` -> `1C03CFD8` | The main loop passes &DE98 and &DED1, which are stored at `1C06CB8C` (lamp ptr) and `1C06CB90` (pad ptr). |
| Composers | LED task `1C03CAA6` (called from `1C016406`) -> lamp composer `1C03B674(track,..)` at `1C03CAE4` and pad composer `1C03BE6C` at `1C03CAEC` | They rebuild all 57 lamps and 16 pads from mode words (`*1C06CB98`), track state, transport `FFF8E440` and the blink phases. |
| Blink clocks | `1C03CB78`, called from loop `FFF83288` at `FFF832CA` | `FFF8F4BC` (s16) counts from -320 up to 1600 then wraps (period 1921 ticks; init at `1C03D014`). `FFF8F4BE` counts from -960 up to 960. The beat clock `FFF8F478` (u8) is zeroed on a beat at `FFF83EC6` and incremented with saturation at 0xFA (`FFF83F50`). Real tick rate is unknown. |

Encodings (observed in code; physical meaning partly inferred):
- **Lamp byte:** 0 = off, 1 = dim, 3 = full. The usual idiom is `tst; movt; neg; mov #3; shad`, so the lamp is 3 if the condition holds and 1 otherwise. No composer path writing 2 was seen, so its meaning is unknown.
- **Pad byte:** bit 6 = dim. In the track table every dim entry = bright \| 0x40. Bits 0-5 are a color index sent raw to panel A. The index-to-RGB palette is in panel-MCU firmware, not in this image (inferred), so the rendered color of any index is unverified. A value of 0 most likely means off. The track-color sequence decreases and wraps (42..2, then 61..45), which suggests a 64-step hue wheel (inference).
- **Flashing is done in main firmware**, by toggling between full and dim:
  - Mode-button lamps 0x19-0x1F: full when in the state, blinking (dim while `F4BC<0`) in state+6.
  - Lamp 7: full in mode word 1, blinking in 0x10001 (`1C03B83C-84A`).
  - Lamp 0x23: full/off at `F4BC<960` (`1C03BDF0`).
  - Beat-synced: lamp 0x15 is full when `F478<0xF0`, otherwise dim (`1C03B936-950`). Manual line 233 says BPM flashes, so this is likely the BPM lamp (label inferred).
  - Pads: the "no project/not ready" state flashes all pads 0x2A/0 on `F4BC<960` (`1C03BEE6`). Pending pad-change requests flash bright/dim on the beat (below).
- **Button labels:** most lamp-index-to-button labels are not established. Lamp 7 is lit in mode 1 and blinks in 0x10001. That fits TIME WARP (mode 1 per RESUME) or PATTERN. Unresolved, low confidence.

## 2/3. Candidates, ranked by simplicity and side-effect risk

| # | What the user sees | Control (CPU / file) | Data-only? | Shared with / side effects | Conf. |
|---|---|---|---|---|---|
| 0 | Brighter or dimmer buttons and pads, or a different pad color per track | GLOBAL > Brightness (Buttons Full Lit 1-5, Pads Full/Dim Lit 1-4), Track > Track Color #01-#16. Path: getters `1C021532/576/5BA` -> frame bytes 0x3B/0x36. Track color = track rec `1C06C6AC+0x40*t` byte+9 bits1-4 -> table lookup `1C03C0D4-108` | **No firmware change** | None. These are existing user settings | high (path), manual 3397-3609, 4500 |
| 1 | A pad blinking for a pending pattern change shows a different color (only that pad, only while pending, beat-synced) | `1C03C834 mov #89` (dim, 0x59) imm @ `1C03C835` / file `0xEC835`. `1C03C838 mov #25` (bright, 0x19) imm @ `1C03C839` / file `0xEC839`. Gate: request code `-0xBB0` (`1C03C81A`) + pad match, mode family low=3 or 0x10001. The twin for code `-0xB9C` (family 4): `#92` @ `1C03C21F` / `0xEC21F`, `#28` @ `1C03C227` / `0xEC227` | Code-immediate byte (no new logic) | Only that branch. Both are delay-slot `mov #imm,r2` into the shared store at `1C03C85A`. Which mode is Pattern vs Pattern Set is inferred, low | med-high (code), low (labels/colors) |
| 2 | Track Color #02 shows a different color in Track mode (bright on the current track, dim on the others) | bright `1C03D210 e0 27` imm @ `1C03D211` / `0xED211`; dim `1C03D218 e0 67` imm @ `1C03D219` / `0xED219`. The table is built at runtime by immediates into RAM `1C06CC04` (bright) / `1C06CC14` (dim): `2A 27 24 1F 1B 18 15 11 0B 06 02 3D 3A 37 31 2D` with dim = \|0x40 | Code immediates (two bytes; keep dim = bright\|0x40) | Every track set to #02, and only the readers `1C03C0D8/0FE`. **Avoid entries 6, 7, 14 (and treat 0 with care):** those values come from shared registers r5=21, r12=17, r8=49 that also initialize the scanner index map at `1C06CBF1+` (`1C03D1C8-1F4`). Changing them would remap buttons. Entry 0 uses `mov #42,r9` @ `1C03D20E` (no later read seen) and dim `mov #106,r4` @ `1C03D16A`. Safest choice is a value already in the table, so the color is known | high (code), med (visual) |
| 3 | Every blinking button blinks differently (for example a longer dim phase) | Phase limits: `movi20 #1600` @ `1C03CBCC` / `0xEDBCC`; wrap `movi20 #-320` @ `1C03CBDA` / `0xEDBDA`; init `#-320` @ `1C03D014` / `0xED014`. Thresholds `#960` @ `1C03BDF4` (lamp 0x23) and `1C03BEE8` (not-ready pad flash) | Code immediates (20-bit signed, layout `0n00 iiii`) | All F4BC users: mode-button flash states, lamp 7, lamp 0x23, the not-ready pad flash. The tick rate of the loop FFF83288 is unknown, so the period in seconds is unknown | high (code), med (effect) |
| 4 | Beat flash (BPM lamp 0x15, current pad 0x3E/0x7E, pending pads) has a different duty | `mov #-16` (0xF0) @ `1C03B93A` (lamp 0x15), `1C03BDBE`, `1C03C03A` (pad `#126` @ `1C03C040`, `#62` @ `1C03C048`), `1C03C218`, `1C03C82E`, `1C03C406`, `1C03C8D2` | Code immediates (one per site) | Per site. `F478` saturates at 0xFA, so thresholds above 0xFA never fire | med |
| 5 | Factory LED test patterns | Factory menu strings `1C0424A0` (`COLORLED CHECK`), `1C0424B4`; code `1C01C8C4`, `1C01CE40-1C01D5F0` writes all pads to `*1C0452AD` (init 0x2A) or 0/0x40 | Existing feature | Entry method into factory mode is not traced | low |

## Open / not done
- Which button sits at each lamp index. The permutation tables are the builders' index lists (A: 3,0,6,0xC,2,7,0xB,0xD,1,8,0xA,0xE,0xF,0x12,0x11,5,0x10,4,9).
- The palette index-to-RGB mapping (panel MCU), and whether 0x40 renders as off.
- The meaning of lamp value 2. The real tick rate of `FFF83288` / `1C03CB78`.
- Any byte change above still needs a rebuilt image with the CRC/byte-sum fixed. That was not done here.
