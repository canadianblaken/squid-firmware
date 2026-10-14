// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.lang.*;
import ghidra.program.model.mem.*;
import java.util.*;
import java.io.*;

public class TraceArpTiming extends GhidraScript {
  private PrintWriter out;
  private Address addr(String s) throws Exception { return currentProgram.getAddressFactory().getDefaultAddressSpace().getAddress(s); }
  private Function containing(Address a) { return currentProgram.getFunctionManager().getFunctionContaining(a); }
  private void around(String s) throws Exception {
    Address a=addr(s); out.println("\nINSTRUCTIONS AROUND "+a);
    Instruction ins=currentProgram.getListing().getInstructionAt(a);
    if(ins==null) ins=currentProgram.getListing().getInstructionContaining(a);
    if(ins==null) { out.println("no instruction defined"); return; }
    Instruction first=ins;
    for(int i=0;i<32;i++) { Instruction prev=currentProgram.getListing().getInstructionBefore(first.getAddress()); if(prev==null) break; first=prev; }
    Address cur=first.getAddress();
    for(int i=0;i<75;i++) { Instruction x=currentProgram.getListing().getInstructionAt(cur); if(x==null) break; out.println(x.getAddress()+" "+x); cur=x.getMaxAddress().next(); }
  }
  @Override public void run() throws Exception {
    out=new PrintWriter(new FileWriter("./analysis/arp-timing-xrefs.txt"));
    Listing listing=currentProgram.getListing();
    for(String s : new String[]{"1c0009e4","1c000c14","1c000c18","1c000c28","1c000c2c","1c000c30","1c000e64","1c000e68","1c000e6c","1c000e70","1c000e74","fff8ba08","fff8ba0c","fff8ba10","fff8ba14","fff8ba18","fff83440","fff83444","fff83458","fff8345c","fff83460","fff83464","fff83468","fff8346c","fff83470","fff83474","fff83478","fff8347c","fff83480","fff83484","fff89548","fff8954c","fff89550","fff89554","fff89558","fff8955c","fff89560","fff89564","fff8cec8","fff8cecc","fff8ced0","fff8ced4","fff8ced8","fff8cedc","fff8cee0","fff8cee4","fff8cee8","fff8ceec","fff8cef0","fff8cef4","fff8cef8","fff8cefc","fff8cf00","fff8cf04","fff8cf08","fff8cf0c","fff8cf10","fff8cf98","fff8cf9c","fff8cfa0","1c000c5a","1c000740","1c00091a"}) {
      Address a=addr(s); out.println("\nADDRESS "+a+" "+listing.getDataAt(a));
      try { int raw=currentProgram.getMemory().getInt(a); out.println("BYTES="+currentProgram.getMemory().getByte(a)+" "+currentProgram.getMemory().getByte(a.add(1))+" "+currentProgram.getMemory().getByte(a.add(2))+" "+currentProgram.getMemory().getByte(a.add(3))+" INT="+Integer.toUnsignedString(raw,16)+" FLOAT="+Float.intBitsToFloat(raw)); } catch(Exception e) { out.println("VALUE unavailable: "+e); }
      ReferenceIterator refs=currentProgram.getReferenceManager().getReferencesTo(a); int n=0;
      while(refs.hasNext() && n++<100) { Reference r=refs.next(); Function f=containing(r.getFromAddress()); out.println("REF "+r.getFromAddress()+" "+r.getReferenceType()+" FN="+(f==null?"none":f.getName()+"@"+f.getEntryPoint())); }
    }
    out.println("\nARP GLOBAL POINTER WORDS");
    for(String[] range : new String[][]{{"1c000990","1c0009e4"},{"1c000c14","1c000c34"}}) {
      Address a=addr(range[0]), end=addr(range[1]);
      while(a.compareTo(end)<=0) { try { out.println(a+" -> "+Integer.toUnsignedString(currentProgram.getMemory().getInt(a),16)); } catch(Exception e) { out.println(a+" unavailable"); } a=a.add(4); }
    }
    out.println("\nPOINTER LITERAL XREF SCAN");
    long[] targets={0xfff8d754L,0xfff8d75aL,0xfff8d76cL,0xfff8d7b0L,0x1c06ab60L,0x1c06a360L,0x1c067060L,0x1c068360L};
    for(MemoryBlock block:currentProgram.getMemory().getBlocks()) {
      if(!block.isInitialized()) continue;
      Address a=block.getStart();
      while(a.add(4).compareTo(block.getEnd())<=0) {
        try {
          long value=Integer.toUnsignedLong(currentProgram.getMemory().getInt(a));
          for(long target:targets) if(value==target) {
            out.println("LITERAL "+a+" -> "+Long.toHexString(target));
            ReferenceIterator refs=currentProgram.getReferenceManager().getReferencesTo(a);
            while(refs.hasNext()) { Reference r=refs.next(); Function f=containing(r.getFromAddress()); out.println("  REF "+r.getFromAddress()+" "+r.getReferenceType()+" FN="+(f==null?"none":f.getName()+"@"+f.getEntryPoint())); }
          }
        } catch(Exception e) {}
        a=a.add(4);
      }
    }
    around("fff83386");
    out.close(); println("Wrote arp-timing-xrefs.txt");
  }
}
