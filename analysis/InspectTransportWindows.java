// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.Instruction;
import ghidra.program.model.symbol.Reference;
import ghidra.program.model.symbol.ReferenceIterator;
import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import java.io.FileWriter;
import java.io.PrintWriter;

public class InspectTransportWindows extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out = new PrintWriter(new FileWriter("./analysis/transport-window-inspection.txt"));
  out.println("PROGRAM=" + currentProgram.getName() + " MODE=readOnly-headless");
  disassemble(toAddr("fff87d3e"));
  disassemble(toAddr("fff87e68"));
  disassemble(toAddr("fff87fe4"));
  disassemble(toAddr("fff87718"));
  disassemble(toAddr("fff878b8"));
  disassemble(toAddr("fff878e4"));
  disassemble(toAddr("fff878ea"));
  for (String entry : new String[]{"fff87d3e", "fff87e68", "fff87fe4", "fff87718", "fff878b8", "fff879fc", "fff87a0c", "1c02d032", "1c0243d6", "1c02440e", "1c024348", "1c0243e6", "1c02444c"}) {
   Address a=toAddr(entry);
   createFunction(a,null);
   Function f=getFunctionAt(a);
   out.println("\nFUNCTION "+entry+" "+(f==null?"<none>":f.getName()));
   if(f!=null){
    DecompInterface d=new DecompInterface();d.openProgram(currentProgram);
    DecompileResults rr=d.decompileFunction(f,45,monitor);
    out.println(rr.decompileCompleted()?rr.getDecompiledFunction().getC():"<decompile unavailable>");
    d.dispose();
   }
  }
  String[][] windows = {
   {"1c020000", "1c020250"},
   {"1c02f790", "1c02f800"},
   {"1c02f5c0", "1c02f680"},
   {"1c042320", "1c042390"},
   {"1c01c600", "1c01c690"},
   {"1c02d110", "1c02d220"},
   {"1c024348", "1c024560"},
   {"fff87d3e", "fff87e70"},
   {"fff878a0", "fff87970"},
   {"fff87850", "fff87890"},
   {"fff87fe4", "fff880d0"},
   {"fff880d0", "fff88130"}
  };
  for (String[] w : windows) {
   out.println("\nWINDOW " + w[0] + ".." + w[1]);
   for (Address a=toAddr(w[0]), end=toAddr(w[1]); a.compareTo(end)<0; a=a.add(2)) {
    Instruction i=currentProgram.getListing().getInstructionAt(a);
    int word=Short.toUnsignedInt(currentProgram.getMemory().getShort(a));
    Function f=getFunctionContaining(a);
    out.printf("%s %04x %s FN=%s%n",a,word,i==null?"<undefined>":i.toString(),f==null?"none":f.getEntryPoint().toString());
    ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(a);
    while(ri.hasNext()) {Reference r=ri.next();out.println("  XREF_FROM "+r.getFromAddress()+" "+r.getReferenceType());}
    if(i!=null)for(Reference r:i.getReferencesFrom())out.println("  XREF_TO "+r.getToAddress()+" "+r.getReferenceType());
   }
  }
  out.close();
 }
}
