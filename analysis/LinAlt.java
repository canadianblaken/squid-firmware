// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Instruction;
import java.io.*;
public class LinAlt extends GhidraScript {
 public void run() throws Exception {
  String[] a=getScriptArgs();
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/"+a[0]));
  Address ad=toAddr(a[1]); int cnt=Integer.parseInt(a[2]);
  for(int i=0;i<cnt;i++){
   disassemble(ad);
   Instruction ins=getInstructionAt(ad);
   if(ins==null){ out.println(ad+" <nodata>"); ad=ad.add(2); continue; }
   StringBuilder sb=new StringBuilder(ad+"  "+ins);
   for(var rf:ins.getReferencesFrom()) if(rf.getReferenceType().isRead()){ try{sb.append(String.format("  [%s=%08x]",rf.getToAddress(),currentProgram.getMemory().getInt(rf.getToAddress())));}catch(Exception e){} }
   out.println(sb);
   ad=ad.add(ins.getLength());
  }
  out.println("TRACE_COMPLETE"); out.close();
 }
}
