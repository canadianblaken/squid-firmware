from pathlib import Path
import json,hashlib,binascii,capstone
root=Path('.'); out=root/'analysis';p=root/'firmware-v113/windows/trzis1__v113.bin';b=p.read_bytes()
# Lengths below follow the loader's byte-count argument shifted right by two
# for DMA word transfers. Labels for the two operating paths remain inferred.
regions=[
 ('bootstrap_start',0,0x104,0xfff80000,'common','Only the inspected startup span; whole bootstrap length remains unknown.'),
 ('alternate_loader',0x20000,0xffc,0x1c04d000,'alternate','Boot selector at file 0x8DC; argument 0xFFF reduced to words.'),
 ('alternate_fast_code',0x30000,0x189c,0xfff82000,'alternate','Loader at file 0x20000; endpoint literal 0xFFF8389C.'),
 ('alternate_main_code_data',0x40000,0x4cffc,0x1c000000,'alternate','Loader argument 0x4CFFF reduced to words.'),
 ('application_loader',0x90000,0xffc,0x1c04d000,'application','Boot selector at file 0x8DC; argument 0xFFF reduced to words.'),
 ('application_fast_code',0xa0000,0xb754,0xfff82000,'application','Loader at file 0x90000; endpoint literal 0xFFF8D754.'),
 ('application_main_code_data',0xb0000,0x4cffc,0x1c000000,'application','Loader at file 0x90040; argument 0x4CFFF reduced to words.'),
 ('version_metadata',0x1d0000,0xec,0xfff81f00,'both','Loader argument 0xEF reduced to words; contains ASCII version.'),
 ('alternate_date_word',0x1dfffc,4,0xfff81ffc,'alternate','Explicit copy in loader at file 0x20024.')]
record={'source':str(p),'sha256':hashlib.sha256(b).hexdigest(),'architecture':'SuperH SH-2A with FPU, big endian (static inference)','status':'initial static map; paths overlap at runtime; exact chip unconfirmed','regions':[]}
for name,off,n,addr,path,note in regions:
 record['regions'].append({'name':name,'file_offset':off,'length':n,'runtime_start':addr,'runtime_end_exclusive':addr+n,'path':path,'evidence':note,'slice_sha256':hashlib.sha256(b[off:off+n]).hexdigest()})
record['updater_transfer_ranges']=[{'kind':hex(k),'file_offset':s,'device_offset':s,'length':n} for k,s,n in [(0xf1,0,0x90000),(0xf2,0x90000,0x140000),(0xf7,0x1d0000,0x10000)]]
(out/'memory-map.json').write_text(json.dumps(record,indent=2)+'\n')
checksum={'algorithm':'CRC-16 polynomial 0x1021, initial 0, non-reflected, no final XOR','covered_offset':0,'covered_length':len(b)-2,'computed':hex(binascii.crc_hqx(b[:-2],0)),'stored_offset':len(b)-2,'stored_little_endian':hex(int.from_bytes(b[-2:],'little')),'matches':binascii.crc_hqx(b[:-2],0)==int.from_bytes(b[-2:],'little'),'version_ascii':b[0x1d0000:0x1d0004].decode(),'metadata_word_at_1dfffc':b[0x1dfffc:0x1e0000].hex()}
(out/'image-validation.json').write_text(json.dumps(checksum,indent=2)+'\n')
md=capstone.Cs(capstone.CS_ARCH_SH,capstone.CS_MODE_SH2A|capstone.CS_MODE_SHFPU|capstone.CS_MODE_BIG_ENDIAN)
snips=[('Startup entry',0,8,0xfff80000),('Startup body',0x10,0x98,0xfff80010),('Boot path selection',0x8dc,0x8fe,0xfff808dc),('Alternate loader',0x20000,0x2005e,0x1c04d000),('Application loader',0x90000,0x90070,0x1c04d000),('Application entry candidate',0xc6cee,0xc6d40,0x1c016cee),('Gate Length UI branch',0xb11b8,0xb11f2,0x1c0011b8)]
lines=['Capstone '+capstone.__version__+'; static decode, not target execution.','No fallthrough disassembly into known literal pools. MOVI20 sign extension annotated separately.']
for title,start,end,va in snips:
 lines.append(f'\n{title}: file 0x{start:X}, mapped address 0x{va:X}')
 for i in md.disasm(b[start:end],va):
  extra=[]
  if i.mnemonic=='mov.l' and i.op_str.startswith('0x'):
   target=int(i.op_str.split(',')[0],16); off=target-va+start
   if 0<=off<len(b)-4:extra.append(f'literal 0x{int.from_bytes(b[off:off+4],"big"):08X} at file 0x{off:X}')
  if i.mnemonic=='movi20' and len(i.bytes)==4:
   w1=int.from_bytes(i.bytes[:2],'big');w2=int.from_bytes(i.bytes[2:],'big');imm=((w1&0xf0)<<12)|w2
   if imm&0x80000:extra.append(f'sign-extended immediate 0x{imm|0xfff00000:08X}')
  lines.append(f'{i.address:08x} {i.bytes.hex():8} {i.mnemonic:9} {i.op_str:30} ; '+', '.join(extra))
(out/'mapped-disassembly.asm').write_text('\n'.join(lines)+'\n')
print(json.dumps(checksum))
print('Saved memory-map.json and mapped-disassembly.asm')
