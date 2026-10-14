// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;

public class InspectPanelDispatch extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out = new PrintWriter(new FileWriter("./analysis/panel-dispatch-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  for(String s:new String[]{"1c038e84","1c03a4bc","1c03cb50","1c03d440"}){
   Address a=toAddr(s);
   out.printf("LITERAL %s VALUE=%08x%n",s,currentProgram.getMemory().getInt(a));
  }
  for(String s:new String[]{"1c03ca00","1c03cfc0","1c03d1d0","1c03d2b0","1c03d400"})disassemble(toAddr(s));
  for(String[] w:new String[][]{{"1c03c9f0","1c03cbc0"},{"1c03cfc0","1c03d1d0"},{"1c03d1d0","1c03d2b0"},{"1c03d2b0","1c03d400"},{"1c03d400","1c03d480"}}){
   out.println("\nWINDOW "+w[0]+".."+w[1]);
   for(Address a=toAddr(w[0]),end=toAddr(w[1]);a.compareTo(end)<0;a=a.add(2)){
    Instruction i=currentProgram.getListing().getInstructionAt(a);
    int word=Short.toUnsignedInt(currentProgram.getMemory().getShort(a));
    Function f=getFunctionContaining(a);
    out.printf("%s %04x %s FN=%s%n",a,word,i==null?"<undefined>":i.toString(),f==null?"none":f.getEntryPoint());
    if(i!=null)for(Reference r:i.getReferencesFrom())if(r.getReferenceType().isRead())try{out.printf("  READ %s VALUE=%08x%n",r.getToAddress(),currentProgram.getMemory().getInt(r.getToAddress()));}catch(Exception e){}
   }
  }
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  for(String s:new String[]{"1c03ca00","1c03d400","1c03c9f0"}){
   Function f=getFunctionContaining(toAddr(s));out.println("\nFUNCTION "+s+" ENTRY="+(f==null?"none":f.getEntryPoint()));
   if(f!=null){DecompileResults rr=d.decompileFunction(f,45,monitor);if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());}
  }
  d.dispose();out.println("TRACE_COMPLETE");out.close();
 }
}
