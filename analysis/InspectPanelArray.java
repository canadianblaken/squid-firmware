// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;

public class InspectPanelArray extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/panel-array-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  Address base=toAddr("1c06cb24");
  for(int offset=0;offset<0x90;offset++){
   Address a=base.add(offset); ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(a);
   boolean header=false;
   while(ri.hasNext()){
    if(!header){out.printf("\nBYTE +%02x %s%n",offset,a);header=true;}
    Reference r=ri.next();Function f=getFunctionContaining(r.getFromAddress());
    out.println("REF "+r.getFromAddress()+" "+r.getReferenceType()+" FN="+(f==null?"none":f.getEntryPoint()));
   }
  }
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  for(String s:new String[]{"1c038a1c","1c036fea","1c037f0e"}){
   Function f=getFunctionContaining(toAddr(s));out.println("\nFUNCTION "+s+" ENTRY="+(f==null?"none":f.getEntryPoint()));
   if(f!=null){DecompileResults rr=d.decompileFunction(f,45,monitor);if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());}
  }
  d.dispose();out.println("TRACE_COMPLETE");out.close();
 }
}
