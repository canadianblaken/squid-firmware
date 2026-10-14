// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.address.Address;
import java.io.*;
public class InspectAlt extends GhidraScript {
 public void run() throws Exception {
  String[] a=getScriptArgs();
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/"+a[0]));
  DecompInterface d=new DecompInterface(); d.openProgram(currentProgram);
  for(int n=1;n<a.length;n++){
   char mode=a[n].charAt(0);
   Address ad=toAddr(a[n].substring(1));
   if(mode=='c'){ // containing function -> decompile + asm
    Function f=getFunctionContaining(ad);
    out.println("ADDR "+ad+" FN="+(f==null?"none":f.getEntryPoint()));
    if(f==null) continue;
    DecompileResults r=d.decompileFunction(f,60,monitor);
    if(r.decompileCompleted()) out.println(r.getDecompiledFunction().getC());
    for(InstructionIterator it=currentProgram.getListing().getInstructions(f.getBody(),true);it.hasNext();){
     Instruction i=it.next(); out.println(i.getAddress()+" "+i);
     for(var rf:i.getReferencesFrom()) if(rf.getReferenceType().isRead()){ try{out.printf("  READ %s=%08x%n",rf.getToAddress(),currentProgram.getMemory().getInt(rf.getToAddress()));}catch(Exception e){} }
    }
   } else if(mode=='x'){ // xrefs to
    out.println("XREFTO "+ad);
    for(var ri=currentProgram.getReferenceManager().getReferencesTo(ad);ri.hasNext();){var r=ri.next();Function f=getFunctionContaining(r.getFromAddress());out.println(" "+r+" FN="+(f==null?"none":f.getEntryPoint()));}
   }
  }
  d.dispose(); out.println("TRACE_COMPLETE"); out.close();
 }
}
