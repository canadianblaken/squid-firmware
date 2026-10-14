"""Package the mapped application-path regions as ELF32 big-endian for Ghidra."""
from pathlib import Path
import hashlib, json, struct
root = Path('.')
source = root / 'firmware-v113/windows/trzis1__v113.bin'
out = root / 'analysis/squid-v113-application.elf'
data = source.read_bytes()
# name, file offset, mapped address, bytes, ELF permissions
regions = [
    ('bootstrap', 0x0, 0xFFF80000, 0x104, 5),
    ('version_metadata', 0x1D0000, 0xFFF81F00, 0xEC, 4),
    ('application_fast_code', 0xA0000, 0xFFF82000, 0xB754, 5),
    ('application_main_code_data', 0xB0000, 0x1C000000, 0x4CFFC, 5),
    ('application_loader', 0x90000, 0x1C04D000, 0xFFC, 5),
]
ident = b'\x7fELF' + bytes([1, 2, 1, 0, 0]) + bytes(7)  # ELF32, big-endian
phoff = 52
phentsize = 32
header_size = phoff + phentsize * len(regions)
blob = bytearray(header_size)
phdrs = []
for name, fileoff, vaddr, size, flags in regions:
    offset = len(blob)
    chunk = data[fileoff:fileoff + size]
    blob.extend(chunk)
    phdrs.append((1, offset, vaddr, vaddr, size, size, flags, 1))
    assert len(chunk) == size
for idx, ph in enumerate(phdrs):
    start = phoff + idx * phentsize
    blob[start:start + phentsize] = struct.pack('>IIIIIIII', *ph)
ehdr = struct.pack('>16sHHIIIIIHHHHHH', ident, 2, 42, 1, 0x1C016CEE,
                   phoff, 0, 0, 52, phentsize, len(regions), 40, 0, 0)
blob[:52] = ehdr
out.write_bytes(blob)
record = {
    'output': str(out),
    'sha256': hashlib.sha256(blob).hexdigest(),
    'source': str(source),
    'source_sha256': hashlib.sha256(data).hexdigest(),
    'entry': '0x1C016CEE',
    'purpose': 'derived multi-segment ELF container for Ghidra; not an official firmware file',
    'regions': [{'name': n, 'file_offset': f'{f:#x}', 'runtime_address': f'{v:#x}', 'length': f'{l:#x}'} for n,f,v,l,_ in regions],
}
(root / 'analysis/ghidra-app-image.json').write_text(json.dumps(record, indent=2) + '\n')
print(json.dumps(record, indent=2))
