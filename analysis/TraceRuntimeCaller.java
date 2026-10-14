// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class TraceRuntimeCaller extends GhidraScript {
  private PrintWriter out;
  private DecompInterface dec;
  private Set<Function> printed = new HashSet<>();
  private Address addr(String s) throws Exception { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(s); }
  private Function containing(Address a) { return currentProgram.getFunctionManager().getFunctionContaining(a); }
  private void dump(Function f) throws Exception {
    if (f == null || !printed.add(f)) return;
    out.println("\nFUNCTION " + f.getName() + " @ " + f.getEntryPoint() + " BODY=" + f.getBody());
    DecompileResults r=dec.decompileFunction(f,60,monitor);
    out.println(r.decompileCompleted() && r.getDecompiledFunction()!=null ? r.getDecompiledFunction().getC() : "DECOMPILER: unavailable");
    if (f.getEntryPoint().equals(addr("1c000b7e")) || f.getEntryPoint().equals(addr("fff8b71a")) || f.getEntryPoint().equals(addr("fff8942c"))) {
      InstructionIterator ins=currentProgram.getListing().getInstructions(f.getBody(),true);
      while(ins.hasNext()) out.println("INSN " + ins.next());
    }
    ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(f.getEntryPoint());
    while(it.hasNext()) { Reference x=it.next(); out.println("REF_FROM " + x.getFromAddress() + " TYPE=" + x.getReferenceType()); }
  }
  @Override public void run() throws Exception {
    out=new PrintWriter(new FileWriter("./analysis/runtime-path.txt"));
    dec=new DecompInterface(); dec.openProgram(currentProgram);
    out.println("PROGRAM="+currentProgram.getName()+" LANGUAGE="+currentProgram.getLanguageID());
    for(String s : new String[]{"1c000740","1c000c5a","1c00091a","1c00014c","1c00011a","1c000ac8","1c000b7e","fff8b71a","fff83386","fff89498","fff8cc18","fff8ad40","fff84450","fff8adf4","fff8cfa8","fff88d1c","fff88c30","fff88770","fff83d28","fff83d8c","fff8d2de","fff830a4","fff8a808","fff8d0ec","fff88ab8","fff82fee","1c0235ac","1c02379e","1c023948","1c035484","1c0127e6","1c031208","1c000c30","1c000e6c","1c0181cc","1c01824c","1c00d0f8","1c00c65e"}) {
      Address a=addr(s); Function f=currentProgram.getFunctionManager().getFunctionContaining(a);
      out.println("\nQUERY "+a+" CONTAINING="+(f==null?"none":f.getName()+"@"+f.getEntryPoint())); dump(f);
    }
    dec.dispose(); out.close(); println("Wrote runtime-path.txt");
  }
}
