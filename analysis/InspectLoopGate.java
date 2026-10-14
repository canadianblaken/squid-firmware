// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class InspectLoopGate extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/loop-gate-inspection.txt"));
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  Set<Function> fs=new LinkedHashSet<>();
  for(String s:new String[]{"fff8de74","fff8de76","fff8de7a","fff8f372"}){
   out.println("TARGET "+s);
   for(ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(toAddr(s));ri.hasNext();){
    Reference r=ri.next();Function f=getFunctionContaining(r.getFromAddress());
    out.println("REF "+r.getFromAddress()+" "+r.getReferenceType()+" FN="+(f==null?"none":f.getEntryPoint()));
    if(f!=null && (s.equals("fff8de76")||s.equals("fff8f372")))fs.add(f);
   }
  }
  for(Function f:fs){
   out.println("\nFUNCTION "+f.getEntryPoint());
   DecompileResults rr=d.decompileFunction(f,45,monitor);
   if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());
   InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);
   while(it.hasNext()){
    Instruction i=it.next();out.println(i.getAddress()+" "+i);
    for(Reference r:i.getReferencesFrom())if(r.getReferenceType().isRead())try{out.println("  READ "+r.getToAddress()+" VALUE="+Integer.toUnsignedString(currentProgram.getMemory().getInt(r.getToAddress()),16));}catch(Exception e){}
   }
  }
  d.dispose();out.println("TRACE_COMPLETE functions="+fs.size());out.close();
 }
}
