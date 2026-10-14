from pathlib import Path
import lief,capstone,json,hashlib
from capstone.x86 import X86_OP_IMM,X86_OP_MEM,X86_REG_RIP
root=Path('.'); out=root/'analysis'
p=root/'firmware-v113/mac/SQUID_ver113.app/Contents/MacOS/TORAIZSQUIDUpdateProgram'
fat=lief.MachO.parse(str(p)); b=fat.at(0)
rawfile=p.read_bytes()
def content(va,n):
 for seg in b.segments:
  if seg.virtual_address<=va and va+n<=seg.virtual_address+seg.file_size:
   off=seg.file_offset+va-seg.virtual_address
   return rawfile[off:off+n]
 return b""
syms={s.value:s.name for s in b.symbols if s.value}
starts=sorted(syms)
md=capstone.Cs(capstone.CS_ARCH_X86,capstone.CS_MODE_64);md.detail=True
wanted=['MainComponentC2','ParseUpdFile','CheckUpdFile','CalcCRC','CheckSysVer','CheckDeviceMode','SendDataAndWaitResponse','MainComponent3run','deviceOutput','toraizis11Matching']
lines=[]
for start in starts:
 name=syms[start]
 if not any(k in name for k in wanted):continue
 end=next((x for x in starts if x>start),start+512)
 lines.append(f'\nFUNCTION {start:x} {name}')
 for i in md.disasm(content(start,end-start),start):
  comments=[]
  for op in i.operands:
   if op.type==X86_OP_IMM and op.imm in syms: comments.append(syms[op.imm])
   if op.type==X86_OP_MEM and op.mem.base==X86_REG_RIP:
    va=i.address+i.size+op.mem.disp
    comments.append(f'RIP target {va:x} '+syms.get(va,''))
    try:
     raw=content(va,80).split(b'\0')[0]
     if raw and all(32<=x<127 for x in raw):comments.append(repr(raw.decode()))
    except Exception:pass
  lines.append(f'{i.address:010x} {i.mnemonic:8} {i.op_str:45} ; '+', '.join(comments))
(out/'updater-selected.asm').write_text('\n'.join(lines)+'\n')
fw=(root/'firmware-v113/windows/trzis1__v113.bin').read_bytes()
sh=capstone.Cs(capstone.CS_ARCH_SH,capstone.CS_MODE_SH2A|capstone.CS_MODE_SHFPU|capstone.CS_MODE_BIG_ENDIAN)
lines=[]
for start,end,base in [(0,8,0xfff80000),(0x10,0x104,0xfff80000),(0x20000,0x20130,0),(0x30000,0x30140,0),(0x90000,0x90190,0),(0x40000,0x40060,0),(0x7cc,0x988,0xfff80000),(0xb1200,0xb1400,0x1bf50000)]:
 lines.append(f'\nREGION file {start:x} to {end:x}; address delta {base:x}; SH2A decode hypothesis')
 for i in sh.disasm(fw[start:end],start+base):
  comment=''
  if i.mnemonic=='mov.l' and i.op_str.startswith('0x'):
   target=int(i.op_str.split(',')[0],16)-base
   if 0<=target<len(fw)-3:comment=f' ; literal {int.from_bytes(fw[target:target+4],"big"):08x} at file {target:x}'
  lines.append(f'{i.address:08x} {i.bytes.hex():8} {i.mnemonic:8} {i.op_str}'+comment)
(out/'startup-selected.asm').write_text('\n'.join(lines)+'\n')
(out/'updater-symbols.json').write_text(json.dumps({'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'symbols':{hex(k):v for k,v in syms.items()}},indent=2)+'\n')
print('Saved updater-selected.asm, startup-selected.asm, updater-symbols.json')
