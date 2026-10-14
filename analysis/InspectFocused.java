// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
public class InspectFocused extends GhidraScript {
 public void run() throws Exception {
  String[] args=getScriptArgs();
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/"+args[0]));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  for(int n=1;n<args.length;n++){
   String s=args[n];
   if(s.startsWith("@")){
    s=s.substring(1);out.println("TARGET "+s);
    for(ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(toAddr(s));ri.hasNext();){Reference r=ri.next();Function f=getFunctionContaining(r.getFromAddress());out.println("REF "+r+" FN="+(f==null?"none":f.getEntryPoint()));}
    continue;
   }
   disassemble(toAddr(s)); if(getFunctionAt(toAddr(s))==null)createFunction(toAddr(s),null);
   Function f=getFunctionAt(toAddr(s));out.println("FUNCTION "+s);if(f==null)continue;
   DecompileResults rr=d.decompileFunction(f,45,monitor);if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());
   InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);
   while(it.hasNext()){Instruction i=it.next();out.println(i.getAddress()+" "+i);for(Reference r:i.getReferencesFrom())if(r.getReferenceType().isRead())try{out.printf("  READ %s VALUE=%08x%n",r.getToAddress(),currentProgram.getMemory().getInt(r.getToAddress()));}catch(Exception e){}}
  }
  d.dispose();out.println("TRACE_COMPLETE");out.close();
 }
}
