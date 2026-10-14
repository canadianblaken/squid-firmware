// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;

public class InspectModeCallers extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/mode-caller-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  for(String s:new String[]{"1c00fd28","1c00fdb4","1c01093a","1c00f3de","1c00f456"}){
   Address a=toAddr(s);Function f=getFunctionContaining(a);
   out.println("\nQUERY "+s+" FUNCTION "+f);
   if(f==null)continue;
   DecompileResults rr=d.decompileFunction(f,45,monitor);
   out.println(rr.decompileCompleted()?rr.getDecompiledFunction().getC():"<decompile unavailable>");
   InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);
   while(it.hasNext()){
    Instruction i=it.next();out.println(i.getAddress()+" "+i);
    for(Reference r:i.getReferencesFrom())if(r.getReferenceType().isRead())try{out.println("  READ "+r.getToAddress()+" VALUE="+Integer.toUnsignedString(currentProgram.getMemory().getInt(r.getToAddress()),16));}catch(Exception e){}
   }
   for(ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(f.getEntryPoint());ri.hasNext();) {
    Reference r=ri.next();out.println("  CALLER_REF "+r.getFromAddress()+" "+r.getReferenceType());
   }
  }
  d.dispose();out.println("TRACE_COMPLETE");out.close();
 }
}
