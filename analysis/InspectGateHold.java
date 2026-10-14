// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.Reference;
import java.io.*;

public class InspectGateHold extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/gate-hold-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  for(String s:new String[]{"fff894da","fff8940c","fff8942c","fff8b1b4"}){
   Address a=toAddr(s);disassemble(a);createFunction(a,null);
   Function f=getFunctionContaining(a);
   out.println("\nFUNCTION "+s+" "+f);
   if(f==null)continue;
   DecompileResults rr=d.decompileFunction(f,45,monitor);
   out.println(rr.decompileCompleted()?rr.getDecompiledFunction().getC():"<decompile unavailable>");
   InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);
   while(it.hasNext()){
    Instruction i=it.next();out.println(i.getAddress()+" "+i);
    for(Reference r:i.getReferencesFrom()){
     out.println("  TO "+r.getToAddress()+" "+r.getReferenceType());
     if(r.getReferenceType().isRead())try{out.println("  VALUE="+Integer.toUnsignedString(currentProgram.getMemory().getInt(r.getToAddress()),16));}catch(Exception e){}
    }
   }
  }
  d.dispose();out.println("TRACE_COMPLETE");out.close();
 }
}
