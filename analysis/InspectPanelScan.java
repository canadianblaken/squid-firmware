// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;

public class InspectPanelScan extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/panel-scan-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  for(String s:new String[]{"1c037f90","1c038104","1c03821a","1c03835a","1c0386fc","1c037f26","1c037f42"}){
   Function f=getFunctionContaining(toAddr(s));out.println("\nFUNCTION "+s+" ENTRY="+(f==null?"none":f.getEntryPoint()));
   if(f==null)continue;
   DecompileResults rr=d.decompileFunction(f,45,monitor);if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());
   InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);
   while(it.hasNext()){Instruction i=it.next();for(Reference r:i.getReferencesFrom())if(r.getReferenceType().isRead())try{out.printf("LITERAL %s -> %s VALUE=%08x%n",i.getAddress(),r.getToAddress(),currentProgram.getMemory().getInt(r.getToAddress()));}catch(Exception e){}}
  }
  d.dispose();out.println("TRACE_COMPLETE");out.close();
 }
}
