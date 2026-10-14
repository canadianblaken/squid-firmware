// @category SQUID
import ghidra.app.script.GhidraScript;
import ghidra.app.decompiler.*;
import ghidra.program.model.address.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;
import ghidra.program.model.mem.*;
import java.io.*;
import java.util.*;
public class TraceNoteOutputs extends GhidraScript {
 public void run() throws Exception {
  PrintWriter out=new PrintWriter(new FileWriter("./analysis/note-output-trace.txt"));
  out.println("PROGRAM="+currentProgram.getName()+" LANGUAGE="+currentProgram.getLanguageID()+" MODE=readOnly-headless");
  Set<Function> funcs=new LinkedHashSet<>();
  long[] targets={0xfff8d7acL,0xfff8e130L,0xfff8e140L,0xfff8e110L,0xfff8e120L,0xfff8e150L,0xfff8e160L,0x1c067460L,0x1c067560L,0x1c067660L,0x1c066974L,0x1c066964L,0x1c0661e4L,0xfff8ecdcL,0xfff8f2fcL,0x1c0669a4L,0x1c066cb4L,0xfff8e0c6L,0xfff8db72L,0xfff8e440L,0x1c06c284L,0x1c06c684L,0xfff8ebdcL,0x1c067060L,0x1c067160L,0x1c068360L,0x1c066f60L,0x1c066994L,0x1c0667e4L,0x1c066170L,0x1c066cc4L,0x1c066cd4L,0xfff8e0e0L,0xfff8e1d0L,0xfff8f35cL,0xfff8d9a0L,0xfff8ddbcL,0x1c052d10L,0xfff8e06eL,0xfff8e072L,0xfff8e074L,0xfff8e08aL,0xfff8e094L,0xfff8e090L,0xfff8dfbcL,0xfff8dfdcL,0xfff8dfecL,0xfff8dfb4L,0xfff87ab6L,0x1c0201fcL,0xfff8f4d8L,0xfff8e004L,0xfff8f4c0L,0xfff8f524L,0xfff8f574L,0x1c063644L};
  for(MemoryBlock b:currentProgram.getMemory().getBlocks()) {
   if(!b.isInitialized()) continue;
   for(Address a=b.getStart();a.add(3).compareTo(b.getEnd())<=0;a=a.add(2)) {
    long v=Integer.toUnsignedLong(currentProgram.getMemory().getInt(a));
    boolean found=false; for(long t:targets) if(v>=t && v<t+16) found=true;
    if(!found) continue;
    out.println("LITERAL "+a+" -> "+Long.toHexString(v));
    ReferenceIterator refs=currentProgram.getReferenceManager().getReferencesTo(a);
    while(refs.hasNext()) {Reference r=refs.next(); Function f=getFunctionContaining(r.getFromAddress());out.println("REF "+r.getFromAddress()+" "+r.getReferenceType()+" FN="+(f==null?"none":f.getEntryPoint()));if(f!=null) funcs.add(f);}
   }
  }
  String[] queries=getScriptArgs();
  if(queries.length==0) queries=new String[]{"1c00091a","fff8942c","fff88e28","fff89146","fff891bc","fff8ca64","fff8d0ec","fff88ab8","+fff87324","+1c0177fa","+1c017844","+1c017930","+1c0179d8","fff84f26","fff88266","+fff85122","+fff87480","+1c010a72","+fff8678e","+fff861ea","+1c034c30","+1c034602","+1c034724","+1c0328b4","+1c032fa4","+fff8b676","+1c034848","+1c0348b2","+fff88c30","+fff88d1c","+1c0127e6","+fff83200","+fff83386","fff8b1b4","fff8b228","+1c033ba6","+1c00fe58","+1c00fcc4","fff8423a","+fff8839c","+fff883a2","+fff87a20","+fff87a98","+fff87ab6","+fff87b20","+fff87b4e","+fff87b74","+fff82104","+fff82156","+fff82254","+fff8231e","+fff82448","+fff82474","+fff824c8","+1c00195a","+1c0019e8","+fff82554","+fff87c60","+1c024282","+1c01f964","+1c01f9a4","+1c0201fc"};
  for(String s:queries) {if(s.startsWith("+")){s=s.substring(1);disassemble(toAddr(s));createFunction(toAddr(s),null);} Function f=getFunctionContaining(toAddr(s));if(f==null) {FunctionIterator it=currentProgram.getFunctionManager().getFunctions(toAddr(s),false);if(it.hasNext())f=it.next();}if(f!=null)funcs.add(f);}
  DecompInterface dec=new DecompInterface();dec.openProgram(currentProgram);
  for(Function f:funcs) {
   out.println("\nFUNCTION "+f.getEntryPoint()+" "+f.getName());
   DecompileResults d=dec.decompileFunction(f,45,monitor); if(d.decompileCompleted())out.println(d.getDecompiledFunction().getC());
   InstructionIterator ins=currentProgram.getListing().getInstructions(f.getBody(),true);
   while(ins.hasNext()) {
    Instruction i=ins.next();out.println(i.getAddress()+" "+i);
    for(Reference r:i.getReferencesFrom())if(r.getReferenceType().isRead())try {Address a=r.getToAddress();out.println("  READ "+a+" VALUE="+Integer.toUnsignedString(currentProgram.getMemory().getInt(a),16));}catch(Exception e){}
   }
  }
  for(String p:new String[]{"fff874e8","fff874ec","fff874fc","fff87500","fff87504","fff87508","fff87510","fff87514","fff87518","fff8751c","fff87520","fff87524","fff87528","1c017a30","1c017a40","1c017a44","fff86680","fff86684","fff8836c","fff88370","fff88374","fff8839c","fff883a2","fff87968","fff879ce"}) out.println("RESOLVED_POOL "+p+" -> "+Integer.toUnsignedString(currentProgram.getMemory().getInt(toAddr(p)),16));
  for(String p:new String[]{"fff8d9a0","fff8d9ac","fff8d9b8","fff8d9c4","fff8ddbc","1c052d10","1c052d18","fff8e06e","fff8e072","fff8e074","fff8e08a","fff8e094","fff8e090","fff8dfbc","fff8dfec","fff8dfb4","fff8f4d8","fff8e004","fff8f4c0","fff8f524","fff8f574","1c063644"}) {ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(toAddr(p));while(ri.hasNext()){Reference r=ri.next();Function f=getFunctionContaining(r.getFromAddress());out.println("DATA_XREF "+p+" FROM="+r.getFromAddress()+" TYPE="+r.getReferenceType()+" FN="+(f==null?"none":f.getEntryPoint()));}}
  for(String p:new String[]{"1c034c30","1c035484","fff87324","fff8901a","fff861ea","fff8e440","fff8b1b4","fff8b228","fff8cc18","fff8897a","fff8942c","fff88e28","fff88d1c","fff84f26","fff88266","fff8812c","fff8839c","fff883a2","fff87b20","fff82156","fff82104","1c01f830","fff87b74","1c024282","1c033ba6"}) {ReferenceIterator ri=currentProgram.getReferenceManager().getReferencesTo(toAddr(p));while(ri.hasNext()){Reference r=ri.next();Function f=getFunctionContaining(r.getFromAddress());out.println("FUNCTION_XREF "+p+" FROM="+r.getFromAddress()+" TYPE="+r.getReferenceType()+" FN="+(f==null?"none":f.getEntryPoint()));}}
  InstructionIterator allIns=currentProgram.getListing().getInstructions(true);
  while(allIns.hasNext()) { Instruction ii=allIns.next(); String mn=ii.getMnemonicString().toLowerCase(); if(mn.contains("vbr") || mn.startsWith("rte")) out.println("VECTOR_INSN "+ii.getAddress()+" "+ii); }
  out.println("TRACE_COMPLETE functions="+funcs.size());
  dec.dispose();out.close();
 }
}
