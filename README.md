# SQUID firmware research (v1.13)

Static analysis and offline patch tooling for the Pioneer TORAIZ SQUID (product sunset). Goal: add features to the firmware.
Start with `analysis/RESUME.md`. Results so far are in `CHANGELOG.md`.

**No vendor firmware, installers or manuals are in this repo.** Put your own copy of the original `trzis1__v113.bin`
(from the official updater) in `firmware-v113/windows/` and run:

    python3 -I build/patch_image.py ORIG OUT build/patches/<set>.json

The patcher works on a copy, refuses to touch the bootloader regions and the date word, and recomputes the host-side CRC.

**Warning.** Flashing a modified image can brick the unit. The device checks only a per-chunk byte-sum, there is no signature,
and the updater sends the bootloader region too if the unit's version query fails. Keep the original backup, do not use a
unit you cannot afford to lose, and flash at your own risk. Nothing here comes from or is endorsed by Pioneer DJ / AlphaTheta.

Confidence markers in the notes: verified (read in disassembly), inferred (reasoned, not proven), tested (observed on hardware).
