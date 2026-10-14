// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import java.io.*;
import java.util.*;

public class TraceGateRefs extends GhidraScript {
  private PrintWriter out;
  private DecompInterface dec;
  private Set<Function> printed = new LinkedHashSet<>();
  private void say(String s) { out.println(s); }
  private Address addr(String s) throws Exception { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(s); }
  private Function containing(Address a) { return currentProgram.getFunctionManager().getFunctionContaining(a); }
  private void dumpFunction(Function f) throws Exception {
    if (f == null || !printed.add(f)) return;
    say("\nFUNCTION " + f.getName() + " @ " + f.getEntryPoint() + " BODY=" + f.getBody());
    DecompileResults r = dec.decompileFunction(f, 60, monitor);
    if (r.decompileCompleted() && r.getDecompiledFunction() != null) say(r.getDecompiledFunction().getC());
    else say("DECOMPILER: " + (r.getErrorMessage() == null ? "unavailable" : r.getErrorMessage()));
    ReferenceIterator refs = currentProgram.getReferenceManager().getReferencesTo(f.getEntryPoint());
    int n=0;
    while (refs.hasNext() && n++ < 50) say("CALL/REF " + refs.next().getFromAddress());
  }
  private void refsTo(String label, Address target) throws Exception {
    say("\nXREFS TO " + label + " @ " + target);
    ReferenceIterator refs = currentProgram.getReferenceManager().getReferencesTo(target);
    int n=0;
    while (refs.hasNext() && n < 100) {
      Reference ref=refs.next(); n++;
      say("REF " + ref.getFromAddress() + " type=" + ref.getReferenceType());
      dumpFunction(containing(ref.getFromAddress()));
    }
    if(n==0) say("(none recorded)");
  }
  @Override public void run() throws Exception {
    File f = new File("./analysis/ghidra-gate-trace.txt");
    out = new PrintWriter(new FileWriter(f));
    dec = new DecompInterface(); dec.openProgram(currentProgram);
    say("PROGRAM=" + currentProgram.getName() + " LANGUAGE=" + currentProgram.getLanguageID() + " IMAGEBASE=" + currentProgram.getImageBase());
    for (String a : new String[]{"1c040ce4", "1c040cf4", "1c050000", "1c00023c", "1c0013f4", "1c001410"})
      refsTo("address", addr(a));
    for (String[] item : new String[][]{{"1c000d2c","arp_setting_descriptor_builder"},{"1c00004a","arp_style_getter"},{"1c00007c","arp_range_getter"},{"1c0000be","arp_rate_getter"},{"1c000100","arp_setting4_getter"},{"1c0000d8","arp_gate_value_setter"},{"1c000ed2","arp_setting_value_helper"},{"1c001012","arp_gate_value_helper"},{"1c0010d0","arp_setting_row_handler"},{"1c00122e","arp_setting_value_renderer"},{"1c016cee","application_entry"}}) {
      Address x=addr(item[0]);
      Function fn=currentProgram.getFunctionManager().getFunctionAt(x);
      if(fn==null) fn=createFunction(x,item[1]);
      if(fn!=null && !fn.getName().equals(item[1])) fn.setName(item[1], ghidra.program.model.symbol.SourceType.USER_DEFINED);
      say("\nQUERY " + x + " BYTE=" + currentProgram.getMemory().getByte(x)); dumpFunction(fn);
    }
    Listing listing = currentProgram.getListing();
    say("\nREFERENCES TO LITERAL POOLS CONTAINING CHANNEL TABLE POINTER 0xFFF8E1F4");
    Address poolStart=addr("1c000000"), poolEnd=addr("1c04cff8");
    for(Address a=poolStart; a.compareTo(poolEnd)<=0; a=a.add(4)) {
      try {
        if(currentProgram.getMemory().getInt(a)==0xfff8e1f4) {
          say("POOL " + a);
          ReferenceIterator rit=currentProgram.getReferenceManager().getReferencesTo(a);
          int n=0;
          while(rit.hasNext() && n++<20) {
            Reference rr=rit.next(); Function cf=containing(rr.getFromAddress());
            say("  REF " + rr.getFromAddress() + " "+rr.getReferenceType()+" "+(cf==null?"<no function>":cf.getName()+"@"+cf.getEntryPoint()));
            if(cf!=null && cf.getEntryPoint().compareTo(addr("1c000000"))>=0 && cf.getEntryPoint().compareTo(addr("1c04d000"))<0) dumpFunction(cf);
          }
        }
      } catch(Exception ex) {}
    }
    say("\nINSTRUCTIONS REFERENCING FIELD OFFSET 0xE16");
    InstructionIterator all=listing.getInstructions(true);
    int found=0;
    while(all.hasNext()) {
      Instruction ins=all.next();
      if(ins.toString().toLowerCase().contains("0xe16")) {
        say(ins.getAddress()+" "+ins+" \t "+(containing(ins.getAddress())==null?"<no function>":containing(ins.getAddress()).getName()+"@"+containing(ins.getAddress()).getEntryPoint()));
        found++;
      }
    }
    say("OFFSET_HITS="+found);
    for (String[] range : new String[][]{{"1c000c00","1c000f20"},{"1c001000","1c001500"}}) {
      Address start=addr(range[0]), end=addr(range[1]);
      say("\nLISTING " + start + ".." + end);
      InstructionIterator it=listing.getInstructions(new AddressSet(start,end), true);
      while(it.hasNext()) {
        Instruction ins=it.next();
        say(ins.getAddress() + " " + ins + " \t " + (containing(ins.getAddress()) == null ? "<no function>" : containing(ins.getAddress()).getName()+"@"+containing(ins.getAddress()).getEntryPoint()));
      }
    }
    dec.dispose(); out.close();
    println("Wrote " + f.getAbsolutePath());
  }
}
