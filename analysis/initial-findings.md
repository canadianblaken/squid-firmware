# SQUID v1.13: initial firmware findings

Source: ../firmware-v113/windows/trzis1__v113.bin
SHA-256: 55fadd87edaa9157b8cfd1055103b0862ec1774b91f6df2b753e96f217cd866f
Length: 2,097,152 bytes. Windows and Mac payload hashes previously matched.

## What the REA runs established

Binwalk 3.1.0 reported a zlib signature at file offset 971656 (0xED388), reported size 32 bytes.
Evidence: ev_eaaa8a3b355845baf34793a435895e7394caf5f77c93e81a27b9dccb03a5516b

Unblob 26.6.4 finished with no child files, chunks, derivations or diagnostics in its normalized report.
Evidence: ev_69ab537b1f3dd56c5dd123a9bf710b29271b24730b90148d4404429bc5c5deb9
Its "complete" status means that configured extraction finished; it does not mean the firmware was understood.

## Additional observations from local Python and strings, not REA Evidence

- Image contains 61.91% 0xFF bytes and 10.18% zero bytes, with distinct populated regions.
- NORTi(c)MiSPO occurs at 0x46A90 and 0xD3EE0.
- Readable menu labels include Gate Length (0xF0CE4), Note Repeat (0xF16A8), DIN-MIDI (0xF1BC4), USB-MIDI (0xF1BCD), and CLOCK Settings (0xF28A2).
- SQUID demo project occurs at 0x190004.
- Normal zlib decompression starting at the reported 0xED388 offset failed with "invalid distance too far back". This does not validate an independently decompressible zlib stream. A false positive or a stream requiring additional context remains possible.

## Architecture hypothesis

The first bytes are DF02 D001 402B 0009 FFF80010 FFF90000, interpreted as big-endian 16-bit words followed by 32-bit literals.
Under the SuperH instruction set, the first four words decode coherently as:

    0x0000  DF02   MOV.L @(8,PC),R15  ; literal at file offset 0xC: 0xFFF90000
    0x0002  D001   MOV.L @(4,PC),R0   ; literal at file offset 0x8: 0xFFF80010
    0x0004  402B   JMP @R0
    0x0006  0009   NOP               ; delay slot

This is an analyst inference consistent with a startup stub that initializes the stack and jumps to an entry point. It supports big-endian SuperH as a candidate architecture. Exact processor, SH variant, and runtime mappings for the whole image remain unknown. This is a manual interpretation, not a completed disassembler analysis.

The NORTi strings suggest MiSPO NORTi RTOS code is included; strings alone do not prove an active runtime or explain why two copies exist.

## Next work

1. Confirm the SuperH variant and memory mappings using the updater and additional startup/literal references.
2. Import selected code with an appropriate raw SuperH loader and disassembler. The installed REA firmware workflow does not itself establish raw firmware load profiles; do not assume REA native admission supports this image.
3. Trace cross-references to Gate Length or another selected feature after mapping code and data.
4. Keep any carved region tied to its source offset and hash; no archive extraction is required merely to study raw code.

## References

- Renesas SH-2E software manual: https://www.renesas.com/en/document/mah/sh-2e-software-manual
- MiSPO NORTi supported cores: https://mispo.co.jp/pamphlet/NORTiProList.pdf
- REA firmware workflow and admission limits: https://github.com/morluto/rea/blob/main/docs/firmware-analysis.md

The firmware and updater have not been executed by this inspection.
