# TORAIZ SQUID — owner feature requests vs. shipped firmware

Compiled 2026-10-11 from community threads (Gearspace, ModWiggler, Pioneer DJ forum,
Sound On Sound review), cross-checked against the official change history
(`TORAIZ_SQUID-Firmware-Change-History-Ver113-EN.pdf`, this folder).

Firmware history: 1.0 (launch) → 1.10 (2019-04-24, the only feature release) →
1.11 / 1.12 (2019-06/07, bug fixes) → 1.13 (2024-08-22, EU power-management only).

Caveat: many requests come from 2019 threads that may predate 1.10.

## Reported requests — implementation status requires item-by-item verification

### Arrangement / song structure
- Song mode — automatic pattern playlist (SP-16 has one)
- Pattern chaining past the 4-bar pattern limit; native 8-bar pattern length (current workaround: change step count + quantize) [YT]
- More than 64 patterns per track, so a full live set fits in one project [YT]
- Switch projects without stopping the sequencer [YT]
- Pattern Set chains — 16 chain memories via button combo, each a list of sets + repeat counts
- More than 16 pattern sets (one owner needs 48+)
- Pattern set change that leaves track mutes untouched (patterns only)
- Switch pattern set without restarting from the beginning / without the unit locking while a set is queued

### MIDI input / CC
- CC control of SQUID's own parameters from incoming MIDI (only pitch/gate/velocity accepted)
- Record incoming CCs (e.g. filter sweeps) for external gear
- More CC destinations, incl. Groove Bend slider
- Chord and transpose that follow incoming MIDI, not just scale
- Change patterns / pattern sets from an external controller without page switching
- Remote program change in
- Dedicated performance page where CC control stays live while changing patterns / muting tracks [YT]
- "Selected Track" option in global MIDI track setting (currently All / None only)

### Sequencing / modulation
- Microtiming as a randomizer destination, with error-margin control
- Per-step mute / probability
- Linear vs. exponential curve for CV interpolation
- Polyphonic overdub on a track (adding a note currently replaces the step)
- No need to tap every pad manually for CC data to play back

### Workflow
- Global swing / groove across all tracks (e.g. hold GLOBAL + adjust) instead of per track [YT]
- Save / load track presets or definitions
- Quick pad copy (Roland SP style)
- Drum-track playing / recording mode

### Sync
- Under external clock, pattern changes wait for the current pattern to finish
- Stopping master clock doesn't reset the pattern
- Clock issues when routed through a DAW
- No MIDI clock sync with rekordbox

## Partially addressed (v1.10)

| Request | What v1.10 shipped | Gap |
|---|---|---|
| Live recording from external MIDI | Step note programming from external MIDI; improved Rec from MIDI | Notes only — no CC recording |
| Program change per pattern | Program Change / Bank Select settings | Per track, not per pattern |
| Clock division slower than 1/4 | Pattern Speed setting (Pattern mode) | Range not documented — verify on device |
| Pattern set workflow | Pattern Set edit mode | No chaining, no extra sets, mute behaviour unchanged |
| Sync / transport | Start/Stop/Continue + Nudge regardless of Sync Source; Restart to DIN SYNC/CLOCK OUT fixed; TR-808 DIN sync fixed | Items in "Sync" above remain |

## Sources
[YT] = YouTube owner review (pasted summary 2026-10-11, video URL not recorded)
- https://downloads.support.alphatheta.com/firmwares/music-production/TORAIZ_SQUID/TORAIZ_SQUID-Firmware-Change-History-Ver113-EN.pdf
- https://www.pioneerdj.com/en/news/2019/toraiz-squid-firmware-update-ver110/
- https://files.kraftmusic.com/media/ownersmanual/Pioneer_DJ_Toraiz_Squid_v110_Addendum.pdf
- https://gearspace.com/threads/pioneer-toraiz-squid-sequencer.1262422/page-27
- https://gearspace.com/board/electronic-music-instruments-and-electronic-music-production/1262422-pioneer-toraiz-squid-sequencer-6.html
- https://gearspace.com/board/electronic-music-instruments-and-electronic-music-production/1262422-pioneer-toraiz-squid-sequencer-30.html
- https://gearspace.com/board/electronic-music-instruments-and-electronic-music-production/1262422-pioneer-toraiz-squid-sequencer-34.html
- https://community.pioneerdj.com/hc/en-us/community/posts/22979132284953-Toraiz-Squid-Feature-Request-Pattern-Set-Chains
- https://community.pioneerdj.com/hc/en-us/community/posts/22980234635545-Toraiz-Are-you-still-working-on-Firmware-Improvements-SP-16-SQUID
- https://forums.pioneerdj.com/hc/en-us/community/posts/4403720478105-About-squid-new-firmware
- https://www.modwiggler.com/forum/viewtopic.php?t=216515&start=100
- https://www.modwiggler.com/forum/viewtopic.php?t=217666
- https://www.soundonsound.com/reviews/pioneer-dj-toraiz-squid?page=2

## Firmware check (v1.13 static analysis)
See `analysis/feature-request-firmware-check.md` and `analysis/review-and-next-steps.md`. The search did not establish absent or dormant implementations. Trigger Probability Rate and Step Preview are documented existing controls; earlier uncertainty about those labels is resolved.
