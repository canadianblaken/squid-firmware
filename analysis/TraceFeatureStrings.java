// @category SQUID
// Decompile functions that load pointers to feature-related UI strings (feature-requests.md check).
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class TraceFeatureStrings extends GhidraScript {
  @Override public void run() throws Exception {
    PrintWriter out=new PrintWriter(new FileWriter("./analysis/feature-strings-trace.txt"));
    DecompInterface dec=new DecompInterface(); dec.openProgram(currentProgram);
    AddressSpace sp=currentProgram.getAddressFactory().getDefaultAddressSpace();
    Set<Function> done=new HashSet<>();
    // literal-pool slots (runtime) holding pointers to: Trigger Probability Rate, GLOBAL..SWING row,
    // Disable/Enable/Ena(Mute..) option table, Set No., Swing, Paste x2, Shift+RSPush:Dup, Fixed Length, Length/bars, Pattern Set
    String[][] q={{"1c0303b8","Trigger Probability Rate"},{"1c01d734","GLOBAL..SWING row"},{"1c023120","Disable/Enable/Ena(Mute Cur Tr) table"},
      {"1c0092a8","Set No."},{"1c034438","Swing"},{"1c00c468","Paste"},{"1c00c6d8","Paste(2)"},{"1c0368a4","Shift+RSPush:Dup"},
      {"1c033e24","Fixed Length"},{"1c0092f0","Length"},{"1c007e3c","Pattern Set"}};
    for(String[] e:q){
      Address a=sp.getAddress(e[0]);
      out.println("\n==== "+e[1]+" slot "+a);
      ReferenceIterator it=currentProgram.getReferenceManager().getReferencesTo(a);
      while(it.hasNext()){
        Reference r=it.next(); Function f=currentProgram.getFunctionManager().getFunctionContaining(r.getFromAddress());
        out.println("REF "+r.getFromAddress()+" "+r.getReferenceType()+" IN "+(f==null?"none":f.getName()+"@"+f.getEntryPoint()));
        if(f!=null && done.add(f)){
          DecompileResults d=dec.decompileFunction(f,90,monitor);
          out.println(d.decompileCompleted()&&d.getDecompiledFunction()!=null?d.getDecompiledFunction().getC():"DECOMPILER: unavailable");
        }
      }
    }
    dec.dispose(); out.close(); println("Wrote feature-strings-trace.txt");
  }
}
