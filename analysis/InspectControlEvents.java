// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class InspectControlEvents extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/control-event-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  Set<Function> fs=new LinkedHashSet<>();
  for(String p:new String[]{"1c0511ec","1c051218","1c051240","fff8f390","fff8f3b4"}) {
   Address target=toAddr(p);out.println("\nTARGET "+p);
   for(ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(target);ri.hasNext();) {
    Reference r=ri.next();Function f=getFunctionContaining(r.getFromAddress());
    out.println("REF "+r.getFromAddress()+" "+r.getReferenceType()+" FN="+(f==null?"none":f.getEntryPoint()));
    if(f!=null)fs.add(f);
   }
  }
  for(Function f:fs){
   out.println("\nFUNCTION "+f.getEntryPoint());
   DecompileResults rr=d.decompileFunction(f,45,monitor);
   if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());
  }
  d.dispose();out.println("TRACE_COMPLETE functions="+fs.size());out.close();
 }
}
