# What was done and what was corrected

## Hardware-tested (one unit, Mac updater, F1 bootloader region untouched)
| Image | Change | Result |
|---|---|---|
| `ver_test` | `Ver.%s` -> `Mod.%s` (0xF460C) | Update completed, no visible change; the string is on a System Info page the owner could not find. Inconclusive. |
| `ver119` | App version string "1.13" -> "1.19" (0xF219F) | Boot screen shows "Version 1.19". Data edits in the app bank take effect. |
| `hook` | No-op jump from the arpeggiator gate timer (0x1C00091A) through the code cave (0x1C045610) and back | Boots, arpeggiator normal. Modified code runs. |
| `hook_120_quarter` | Hook + "1.20" + 1/2X speed factor 0.5 -> 0.25 (0xAA620, fast-RAM literal read once at FFF8C4B4) | 1/2X plays slower. First behavior change confirmed. |

## Findings (static unless stated)
- Update path: USB HID, regions F1 (bootstrap + bootloader bank), F2 (app), F7; device verifies only a per-chunk 32-bit byte-sum and
  read-back; the CRC-16 is host-only; no signature or encryption. F1 is skipped when the unit and file date words match.
- Application bank loads from file 0xB0000 to 0x1C000000; fast code from 0xA0000 to 0xFFF82000. A 0xFF code cave (file 0xF5610-0xFCFFC) is unreferenced.
- Speed Control: scanner index 2F = Gate Hold; per-track state at FFF8EB40+track maps 1/2/3 to half/zero/double timing advance.
- Boot screen draws "Version" as a bitmap from the panel firmware; the digits come from the string at 0xF219C.

## Corrections made along the way
- 0x1D0FFC actually holds "1.13" (not the date); the date word is 0x1DFFFC. Found by an independent review.
- The `Ver.%s` string was assumed to be on the boot screen. It is not (see `ver_test` above); the boot screen reads 0xF219C.
- Time Warp identification, saved-data location and the panel palette chip remain inferred, not proven.
