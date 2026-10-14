// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import java.io.*;

public class InspectRingReaders extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/ring-reader-inspection.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" MODE=readOnly-headless");
  for(String entry:new String[]{"fff85220","fff85238","fff8526c","fff8542c","fff854f0","fff85554"}){
   Address a=toAddr(entry);disassemble(a);createFunction(a,null);
   out.println("ENTRY "+entry+" FUNCTION="+getFunctionAt(a)+" CONTAINING="+getFunctionContaining(a));
  }
  DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
  FunctionIterator it=currentProgram.getFunctionManager().getFunctions(toAddr("fff84c00"),true);
  while(it.hasNext()){
   Function f=it.next();Address a=f.getEntryPoint();
   if(a.compareTo(toAddr("fff85600"))>=0)break;
   out.println("\nFUNCTION "+a+" "+f.getName()+" SIZE="+f.getBody().getNumAddresses());
   DecompileResults rr=d.decompileFunction(f,40,monitor);
   if(rr.decompileCompleted())out.println(rr.getDecompiledFunction().getC());
   InstructionIterator ins=currentProgram.getListing().getInstructions(f.getBody(),true);
   while(ins.hasNext()) {Instruction i=ins.next();out.println(i.getAddress()+" "+i);}
  }
  d.dispose();out.close();
 }
}
