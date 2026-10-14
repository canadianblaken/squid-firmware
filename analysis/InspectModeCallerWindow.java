// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.Reference;
import java.io.*;

public class InspectModeCallerWindow extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/mode-caller-window.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  for(String s:new String[]{"1c00fbe0","1c00fc00","1c00fc80","1c00fd00","1c00fd40","1c00fd80"}) disassemble(toAddr(s));
  for(Address a=toAddr("1c00fbe0"),end=toAddr("1c00fdd0");a.compareTo(end)<0;a=a.add(2)) {
   int word=Short.toUnsignedInt(currentProgram.getMemory().getShort(a));
   Instruction i=currentProgram.getListing().getInstructionAt(a);
   Function f=getFunctionContaining(a);
   out.printf("%s %04x %s FN=%s%n",a,word,i==null?"<undefined>":i.toString(),f==null?"none":f.getEntryPoint());
   if(i!=null)for(Reference r:i.getReferencesFrom()){
    out.println("  TO "+r.getToAddress()+" "+r.getReferenceType());
    if(r.getReferenceType().isRead())try{out.println("  VALUE "+Integer.toUnsignedString(currentProgram.getMemory().getInt(r.getToAddress()),16));}catch(Exception e){}
   }
  }
  out.println("TRACE_COMPLETE");out.close();
 }
}
