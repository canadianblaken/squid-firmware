// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.Address;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class InspectPanelState extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out = new PrintWriter(new FileWriter("./analysis/panel-state-inspection.txt"));
  out.println("PROGRAM=" + currentProgram.getName() + " MODE=readOnly-headless");
  for (String s : new String[]{"1c06cb24", "1c06cb51", "1c06cb52", "1c06cb53", "1c06cbd0", "1c06cd4b", "1c03a418"}) {
   Address a = toAddr(s);
   out.println("\nTARGET " + s);
   for (ReferenceIterator ri = currentProgram.getReferenceManager().getReferencesTo(a); ri.hasNext();) {
    Reference r = ri.next();
    Function f = getFunctionContaining(r.getFromAddress());
    out.println("REF " + r.getFromAddress() + " " + r.getReferenceType() + " FN=" + (f == null ? "none" : f.getEntryPoint()));
   }
  }
  DecompInterface d = new DecompInterface(); d.openProgram(currentProgram);
  for (String s : new String[]{"1c03cd20", "1c0386e2", "1c03709c", "1c03a418"}) {
   Function f = getFunctionContaining(toAddr(s));
   out.println("\nFUNCTION " + s + " ENTRY=" + (f == null ? "none" : f.getEntryPoint()));
   if (f == null) continue;
   DecompileResults rr = d.decompileFunction(f, 45, monitor);
   if (rr.decompileCompleted()) out.println(rr.getDecompiledFunction().getC());
  }
  d.dispose(); out.println("TRACE_COMPLETE"); out.close();
 }
}
