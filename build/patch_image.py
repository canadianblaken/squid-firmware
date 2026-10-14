#!/usr/bin/python3
"""Offline patcher: apply (offset, bytes) patches to a COPY of the SQUID v1.13 image.
Usage: python3 -I patch_image.py ORIG OUT [patches.json]   (json: [["0xF5610","6243e610"], ...])
Never writes ORIG. Never talks to a device. Stdlib only."""
import sys, json, os
SIZE = 0x200000
BANK = (0x90000, 0x1D0000)            # F2 app bank; F1 (0..0x90000) and F7 (0x1D0000+) are untouched
DATE = (0x1DFFFC, 0x1E0000)           # date word: keep equal so updater skips F1
CRC = 0x1FFFFE                        # host-only CRC-16 trailer (recomputed, LE)
REGIONS = {'F1': (0, 0x90000), 'F2': (0x90000, 0x140000), 'F7': (0x1D0000, 0x10000)}
CHUNK = 0x20000

def crc16(b):  # poly 0x1021, init 0, MSB-first, no xorout (matches updater CalcCRC)
    c = 0
    for x in b:
        c ^= x << 8
        for _ in range(8):
            c = ((c << 1) ^ 0x1021) & 0xFFFF if c & 0x8000 else (c << 1) & 0xFFFF
    return c

def chunk_sums(img):  # inferred: chunks restart at each region start, <=0x20000, plain byte-sum mod 2^32
    out = {}
    for k, (o, n) in REGIONS.items():
        out[k] = [sum(img[a:min(a + CHUNK, o + n)]) & 0xFFFFFFFF for a in range(o, o + n, CHUNK)]
    return out

def apply(img, patches):
    img = bytearray(img)
    for off, data in patches:
        end = off + len(data)
        if off < BANK[0] or end > BANK[1]:
            raise ValueError(f'patch {off:#x}+{len(data):#x} outside app bank {BANK[0]:#x}-{BANK[1]:#x}')
        if off < DATE[1] and end > DATE[0]:
            raise ValueError('patch touches date word 0x1DFFFC')
        img[off:end] = data
    img[CRC:CRC + 2] = crc16(img[:CRC]).to_bytes(2, 'little')
    return bytes(img)

def main(argv):
    orig, out = argv[1], argv[2]
    if os.path.abspath(orig) == os.path.abspath(out): sys.exit('refusing: OUT == ORIG')
    src = open(orig, 'rb').read()
    assert len(src) == SIZE
    patches = [(int(o, 0), bytes.fromhex(h)) for o, h in json.load(open(argv[3]))] if len(argv) > 3 else []
    new = apply(src, patches)
    open(out, 'wb').write(new)
    n = sum(a != b for a, b in zip(src, new))
    print(f'{len(patches)} patches, {n} bytes differ (incl. CRC trailer), CRC16 {int.from_bytes(new[CRC:CRC+2],"little"):#06x}')
    for off, d in patches:
        print(f'  {off:#x} len {len(d):#x} overwrote non-FF bytes: {sum(x != 0xFF for x in src[off:off+len(d)])}')
    a, b = chunk_sums(src), chunk_sums(new)
    for k in a:
        for i, (x, y) in enumerate(zip(a[k], b[k])):
            if x != y or not patches: print(f'  {k} chunk{i}: {x:#010x} -> {y:#010x}')

def selfcheck(orig):
    src = open(orig, 'rb').read()
    assert crc16(src[:CRC]) == int.from_bytes(src[CRC:], 'little')   # orig CRC is 0x73FA
    assert apply(src, []) == src                                      # empty patch list: identical copy
    p = apply(src, [(0xF5610, b'\x00\x09')])
    assert p[:0xF5610] == src[:0xF5610] and p[0xF5612:CRC] == src[0xF5612:CRC] and p[0x1DFFFC:0x1E0000] == src[0x1DFFFC:0x1E0000]
    for bad in [(0x1DFFFC, b'\0'), (0x8FFFF, b'\0\0'), (0x1CFFFF, b'\0\0')]:
        try: apply(src, [bad]); raise SystemExit('guard failed')
        except ValueError: pass
    print('selfcheck ok')

if __name__ == '__main__':
    if len(sys.argv) == 2: selfcheck(sys.argv[1])
    else: main(sys.argv)
