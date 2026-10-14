// @category SQUID
// Create + decompile functions around loads of feature UI strings that Ghidra left outside functions.
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.mem.*;
import java.io.*;

public class TraceFeatureFuncs extends GhidraScript {
  Address findEntry(Address ref) throws Exception {
    Memory m=currentProgram.getMemory();
    for(long d=2; d<0x1000; d+=2){
      Address a=ref.subtract(d); short w=m.getShort(a);
      if(w==0x000B) return a.add(4);   // rts + delay slot
      if(w==0x006B) return a.add(2);   // rts/n
    }
    return null;
  }
  @Override public void run() throws Exception {
    PrintWriter out=new PrintWriter(new FileWriter("./analysis/feature-funcs-trace2.txt"));
    DecompInterface dec=new DecompInterface(); dec.openProgram(currentProgram);
    AddressSpace sp=currentProgram.getAddressFactory().getDefaultAddressSpace();
    String[][] q={{"1c00d14a","Trigger Probability popup caller"}};
    for(String[] e:q){
      Address ref=sp.getAddress(e[0]);
      Function f=getFunctionContaining(ref);
      if(f==null){ Address en=findEntry(ref); out.println("guessed entry "+en);
        if(en!=null){ disassemble(en); f=createFunction(en,null); if(f==null) f=getFunctionContaining(ref);} }
      out.println("\n==== "+e[1]+" ref "+ref+" IN "+(f==null?"none":f.getName()+"@"+f.getEntryPoint()));
      if(f!=null){ DecompileResults d=dec.decompileFunction(f,120,monitor);
        out.println(d.decompileCompleted()&&d.getDecompiledFunction()!=null?d.getDecompiledFunction().getC():"DECOMPILER: unavailable"); }
    }
    dec.dispose(); out.close(); println("Wrote feature-funcs-trace2.txt");
  }
}
