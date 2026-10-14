// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;

public class InspectPanelMaps extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/panel-map-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  for(String s:new String[]{"1c06cbfa","1c06cc00","1c06cc02","1c06cc04","1c06cc06","1c06cc08","1c06cc0a","1c06cc0c","1c06cc0e","1c06cc10"}){
   Address a=toAddr(s);out.println("\nTARGET "+s);
   for(ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(a);ri.hasNext();){
    Reference r=ri.next();Function f=getFunctionContaining(r.getFromAddress());
    out.println("REF "+r.getFromAddress()+" "+r.getReferenceType()+" FN="+(f==null?"none":f.getEntryPoint()));
   }
  }
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  for(String s:new String[]{"1c03d0ae","1c03d200","1c03d300"}){
   Function f=getFunctionContaining(toAddr(s));out.println("\nFUNCTION "+s+" ENTRY="+(f==null?"none":f.getEntryPoint()));
   if(f!=null){DecompileResults rr=d.decompileFunction(f,45,monitor);if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());}
  }
  d.dispose();out.println("TRACE_COMPLETE");out.close();
 }
}
