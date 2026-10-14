// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;

public class InspectCallbackLoop extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/callback-loop-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  Function old=getFunctionAt(toAddr("fff83386"));
  if(old!=null){out.println("REMOVE_INTERIOR_FUNCTION "+old.getEntryPoint());currentProgram.getFunctionManager().removeFunction(old.getEntryPoint());}
  disassemble(toAddr("fff83288"));
  Function f=createFunction(toAddr("fff83288"),null);
  out.println("FUNCTION="+f);
  if(f!=null){
   DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
   DecompileResults rr=d.decompileFunction(f,45,monitor);
   if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());
   out.println("BODY="+f.getBody());
   d.dispose();
  }
  for(Address a=toAddr("fff83280"),end=toAddr("fff83600");a.compareTo(end)<0;a=a.add(2)){
   Instruction i=currentProgram.getListing().getInstructionAt(a);
   if(i==null)continue;
   out.println(a+" "+i+" FN="+getFunctionContaining(a));
   for(Reference r:i.getReferencesFrom())out.println("  TO "+r.getToAddress()+" "+r.getReferenceType());
   ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(a);
   while(it.hasNext()){Reference r=it.next();out.println("  FROM "+r.getFromAddress()+" "+r.getReferenceType());}
  }
  out.close();
 }
}
