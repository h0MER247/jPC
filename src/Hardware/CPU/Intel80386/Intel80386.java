/*
 * Copyright (C) 2017 h0MER247
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package Hardware.CPU.Intel80386;

import Hardware.CPU.CPU;
import Hardware.CPU.Intel80386.Codeblock.CodeBlock;
import Hardware.CPU.Intel80386.Decoder.Decoder;
import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.MMU.MMU;
import Hardware.CPU.Intel80386.Register.Control.Control;
import Hardware.CPU.Intel80386.Register.Debug.RegDebug;
import Hardware.CPU.Intel80386.Register.FPU.FPURegisters;
import Hardware.CPU.Intel80386.Register.Flags.Flags;
import Hardware.CPU.Intel80386.Register.General.Reg16;
import Hardware.CPU.Intel80386.Register.General.Reg32;
import Hardware.CPU.Intel80386.Register.General.Reg8;
import Hardware.CPU.Intel80386.Register.General.Reg8Hi;
import Hardware.CPU.Intel80386.Register.General.Reg8Lo;
import Hardware.CPU.Intel80386.Register.Segments.CodeSegment;
import Hardware.CPU.Intel80386.Register.Segments.DataSegment;
import Hardware.CPU.Intel80386.Register.Segments.Descriptor;
import Hardware.CPU.Intel80386.Register.Segments.DescriptorTable;
import Hardware.CPU.Intel80386.Register.Segments.Segment;
import Hardware.CPU.Intel80386.Register.Segments.StackSegment;
import Hardware.CPU.Intel80386.Register.TaskRegister.TaskRegister;
import Hardware.CPU.Intel80386.Register.Test.RegTest;
import Hardware.HardwareComponent;
import Hardware.InterruptController.PICs;
import Hardware.SystemRAM.ATSystemRAM;
import IOMap.IOMap;
import Main.Systems.ComponentConfig;
import Main.Systems.ComponentConfig.Type;
import MemoryMap.MemoryMap;
import Scheduler.Scheduler;
import java.util.ArrayList;
import java.util.HashMap;



public final class Intel80386 implements HardwareComponent,
                                         CPU {
    
    /* ----------------------------------------------------- *
     * Type of cpu that is currently emulated                *
     * ----------------------------------------------------- */
    public enum CPUType {
        
        i386, i486
    }
    private final CPUType m_cpuType;
    private final boolean m_hasFPU;
    
    /* ----------------------------------------------------- *
     * Intel 80386 register set                              *
     * ----------------------------------------------------- */
    public final Reg32 EAX, EBX, ECX, EDX, ESP, EBP, ESI, EDI, EIP;
    public final Reg16 AX, BX, CX, DX, SP, BP, SI, DI;
    public final Reg8 AH, BH, CH, DH, AL, BL, CL, DL;
    public final Flags FLAGS;
    public final Control CR;
    public final CodeSegment CS;
    public final DataSegment DS, ES, FS, GS;
    public final StackSegment SS;
    public final DescriptorTable GDT, LDT, IDT;
    public final TaskRegister TR;
    public final RegDebug DR0, DR1, DR2, DR3, DR6, DR7;
    public final RegTest TR6, TR7;
    public final FPURegisters FPU;
    public boolean HALTED;
    
    /* ----------------------------------------------------- *
     * Code block cache                                      *
     * ----------------------------------------------------- */
    private final class CodeBlockCache {
        
        public final HashMap<Integer, CodeBlock> map = new HashMap<>();
        public boolean isValid;
    }
    private final CodeBlockCache[] m_codeBlockCache;
    private final Integer[] m_integerLUT;
    private CodeBlock m_currentBlock;
    
    /* ----------------------------------------------------- *
     * Code block decoder                                    *
     * ----------------------------------------------------- */
    private final Decoder m_decoder;
    
    /* ----------------------------------------------------- *
     * Descriptor cache                                      *
     * ----------------------------------------------------- */
    private final Descriptor[] m_descriptorCache;
    private int m_descriptorIndex;
    
    /* ----------------------------------------------------- *
     * Interrupt handling                                    *
     * ----------------------------------------------------- */
    public static int INTERRUPT_HARDWARE = 0;
    public static int INTERRUPT_SOFTWARE = 1;
    private PICs m_pics;
    
    /* ----------------------------------------------------- *
     * References to iomap, mmu and scheduler                *
     * ----------------------------------------------------- */
    private final IOMap m_ioMap;
    private final MMU m_mmu;
    private final Scheduler m_scheduler;
    
    
    
    public Intel80386(CPUType cpuType, boolean hasFPU, IOMap ioMap, MemoryMap memoryMap, Scheduler scheduler) {
        
        m_cpuType = cpuType;
        m_hasFPU = hasFPU;
        
        m_mmu = new MMU(this, memoryMap);
        m_ioMap = ioMap;
        m_scheduler = scheduler;
        
        // Initialize register set
        EAX = new Reg32("eax"); EBX = new Reg32("ebx");
        ECX = new Reg32("ecx"); EDX = new Reg32("edx");
        ESP = new Reg32("esp"); EBP = new Reg32("ebp");
        ESI = new Reg32("esi"); EDI = new Reg32("edi");
        EIP = new Reg32("eip");
        AX = new Reg16("ax", EAX); BX = new Reg16("bx", EBX);
        CX = new Reg16("cx", ECX); DX = new Reg16("dx", EDX);
        SP = new Reg16("sp", ESP); BP = new Reg16("bp", EBP);
        SI = new Reg16("si", ESI); DI = new Reg16("di", EDI);
        AH = new Reg8Hi("ah", EAX); BH = new Reg8Hi("bh", EBX);
        CH = new Reg8Hi("ch", ECX); DH = new Reg8Hi("dh", EDX);
        AL = new Reg8Lo("al", EAX); BL = new Reg8Lo("bl", EBX);
        CL = new Reg8Lo("cl", ECX); DL = new Reg8Lo("dl", EDX);
        CS = new CodeSegment("cs", this); DS = new DataSegment("ds", this);
        ES = new DataSegment("es", this); FS = new DataSegment("fs", this);
        GS = new DataSegment("gs", this); SS = new StackSegment("ss", this);
        FLAGS = new Flags(this);
        CR = new Control(this);
        
        FPU = new FPURegisters(this);
        
        GDT = new DescriptorTable("gdt");
        LDT = new DescriptorTable("ldt");
        IDT = new DescriptorTable("idt");
        TR = new TaskRegister(this, m_mmu);
        
        DR0 = new RegDebug(0);
        DR1 = new RegDebug(1);
        DR2 = new RegDebug(2);
        DR3 = new RegDebug(3);
        DR6 = new RegDebug(6);
        DR7 = new RegDebug(7);
        TR6 = new RegTest(6);
        TR7 = new RegTest(7);
        
        // Initialize decoder
        m_decoder = new Decoder(this);
        
        // Initialize descriptor cache
        m_descriptorCache = new Descriptor[4];
        for(int i = 0; i < 4; i++)
            m_descriptorCache[i] = new Descriptor();
        
        // Initialize codeblock cache
        m_codeBlockCache = new CodeBlockCache[(ATSystemRAM.RAM_SIZE_IN_MB * 1024 * 1024) >>> 12];
        for(int i = 0; i < m_codeBlockCache.length; i++)
            m_codeBlockCache[i] = new CodeBlockCache();
        
        // Initialize Integer lookup table. This is needed in order to bypass
        // the Integer autoboxing in findBlock()
        m_integerLUT = new Integer[0x1000];
        for(int i = 0; i < m_integerLUT.length; i++)
            m_integerLUT[i] = i;
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        // Reset registers
        EAX.reset(); EBX.reset(); ECX.reset(); EDX.reset();
        ESP.reset(); EBP.reset(); ESI.reset(); EDI.reset();
        EIP.reset(); FLAGS.reset(); CR.reset(); HALTED = false;
        DR0.reset(); DR1.reset(); DR2.reset();
        DR3.reset(); DR6.reset(); DR7.reset();
        TR6.reset(); TR7.reset();
        
        if(m_cpuType == CPUType.i486)
            DX.setValue(0x470);
        
        // Reset segments
        CS.reset(); DS.reset(); ES.reset(); FS.reset();
        GS.reset(); SS.reset();

        // Reset descriptor tables
        GDT.reset(); LDT.reset(); IDT.reset();
        
        // Initialize entry point for CS:IP and setup IDT
        CS.setSelector(0xf000);
        CS.setBase(0xf0000);
        CS.setValid(true);
        EIP.setValue(0xfff0);
        IDT.setLimit(0x3ff);
        
        // Reset descriptor cache
        m_descriptorIndex = 0;

        // Reset codeblock cache
        for(CodeBlockCache cache : m_codeBlockCache) {
            
            cache.map.clear();
            cache.isValid = true;
        }
        
        // Reset mmu
        m_mmu.reset();
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof PICs)
            m_pics = (PICs)component;
    }

    @Override
    public ArrayList<HardwareComponent> getSubComponents() {
        
        ArrayList<HardwareComponent> list = new ArrayList<>();
        list.add(m_mmu);
        
        return list;
    }
    
    @Override
    public String getConfigCategory() {
        
        return "CPU";
    }

    @Override
    public void provideConfigValues(ComponentConfig.Builder builder) {
        
        builder.value("Frequency", "16000000", Type.ToggleGroup, this::setFrequency)
               .option("12 MHz", "12000000")
               .option("16 MHz", "16000000")
               .option("20 MHz", "20000000")
               .option("25 MHz", "25000000")
               .option("33 MHz", "33000000")
               .option("40 MHz", "40000000")
               .option("50 MHz", "50000000")
               .option("60 MHz", "60000000")
               .option("66 MHz", "66000000")
               .option("75 MHz", "75000000")
               .build();
    }
    
    private boolean setFrequency(String value) {
        
        m_scheduler.setBaseFrequency(Float.valueOf(value), true);
        return true;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of CPU">
    
    @Override
    public void run(int numBlocks) {
        
        while(numBlocks-- > 0) {
            
            try {
                
                if(m_pics.isPending()) {
                    
                    HALTED = false;
                    if(FLAGS.IF) {
                    
                        if(CR.isInRealMode())
                            handleRealModeInterrupt(m_pics.getInterrupt());
                        else
                            handleProtectedModeInterrupt(m_pics.getInterrupt(), INTERRUPT_HARDWARE, null);
                        
                        m_scheduler.updateClock(Scheduler.toFixedPoint(61));
                    }
                }
                if(HALTED) {
                    
                    m_scheduler.updateClock(Scheduler.toFixedPoint(4));
                }
                else {
                    
                    m_currentBlock = getCurrentBlock();
                    m_currentBlock.run();
                }
            }
            catch(CPUException ex) {
                
                if(ex.isNotInternalException())
                    handleCPUException(ex);
            }
        }
    }
    
    public void updateClock(int cycles) {
        
        m_scheduler.updateClock(cycles);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Interrupt handling">
    
    public boolean isInterruptPending() {
        
        return FLAGS.IF & m_pics.isPending();
    }
    
    private void handleCPUException(CPUException exception) {
        
        try {
            
            if(CR.isInRealMode())
                handleRealModeInterrupt(exception.getVector());
            else
                handleProtectedModeInterrupt(exception.getVector(), INTERRUPT_HARDWARE, exception);
        }
        catch(CPUException secondException) {
            
            if(exception.getVector() == 0x08)
                reset();
            else if(exception.combinesToDoubleFaultWith(secondException))
                handleCPUException(CPUException.getDoubleFault());
            else
                handleCPUException(secondException);
        }
    }
    
    public void handleRealModeInterrupt(int vector) {
        
        int oldESP = ESP.getValue();
        try {
            
            int offset = vector << 2;

            // Push flags and return address
            pushStack16(FLAGS.getValue());
            pushStack16(CS.getSelector());
            pushStack16(EIP.getValue());

            // Disable interrupt and trap flag
            FLAGS.IF = false;
            FLAGS.TF = false;

            // Load new CS:IP
            int ip = m_mmu.readMEM16(IDT.getBase() + offset, false);
            int cs = m_mmu.readMEM16(IDT.getBase() + offset + 2, false);
            
            CS.loadRealMode(cs);
            EIP.setValue(ip);
        }
        catch(CPUException ex) {
            
            ESP.setValue(oldESP);
            
            throw ex;
        }
    }
    
    public void handleProtectedModeInterrupt(int vector, int type, CPUException exception) {
        
        if(FLAGS.VM && (FLAGS.IOPL != 3) && (type != INTERRUPT_HARDWARE))
            throw CPUException.getGeneralProtectionFault(0);
        
        int oldESP = ESP.getValue();
        try {
        
            int offset = vector << 3;
            int EXT = (type == INTERRUPT_HARDWARE) ? 1 : 0;
            
            // Interrupt vector must be within IDT table limits
            Descriptor descGate;
            if((descGate = getDescriptor(IDT, offset)) == null)
                throw CPUException.getGeneralProtectionFault(offset + 2 + EXT);
            

            // If software interrupt (INT n, INT 3, INTO) then gate descriptor DPL >= CPL
            if(type != INTERRUPT_HARDWARE && descGate.getDPL() < getCPL())
                throw CPUException.getGeneralProtectionFault(offset + 2 + EXT);
            
            // Gate must be present
            if(!descGate.isPresent())
                throw CPUException.getSegmentNotPresent(offset + 2 + EXT);

            
            //
            // Trap or Interrupt Gate
            //
            if(descGate.getTypeInfo().isTrapGate() ||
               descGate.getTypeInfo().isInterruptGate()) {

                int cs = descGate.getTargetSegment();
                int ip = descGate.getTargetOffset();
                

                // Selector must be non-null
                if(isNullSelector(cs))
                    throw CPUException.getGeneralProtectionFault(EXT);

                // Selector must be within its descriptor table limits
                Descriptor descCS;
                if((descCS = getDescriptor(cs)) == null)
                    throw CPUException.getGeneralProtectionFault((cs & 0xfffc) + EXT);

                // AR byte must indicate code segment
                if(!descCS.getTypeInfo().isCodeSegment())
                    throw CPUException.getGeneralProtectionFault((cs & 0xfffc) + EXT);

                // Segment must be present
                if(!descCS.isPresent())
                    throw CPUException.getSegmentNotPresent((cs & 0xfffc) + EXT);

                // Instruction pointer must be within CS segment limits
                if(descCS.isOutsideLimit(ip, 1))
                    throw CPUException.getGeneralProtectionFault(0);
                
                
                if(!descCS.isSize32())
                    ip &= 0xffff;
                
                
                //
                // Trap or Interrupt Gate: Inner privilege
                //
                if(descCS.getTypeInfo().isNonConformingCodeSegment() &&
                   descCS.getDPL() < getCPL()) {

                    // CS selector's DPL has to be 0 in VM8086 Mode
                    if(FLAGS.VM && descCS.getDPL() != 0)
                        throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
                    
                    
                    int oldSS = SS.getSelector();
                    int oldSP = ESP.getValue();
                    int ss = TR.getStackSegment(descCS.getDPL());
                    int sp = TR.getStackPointer(descCS.getDPL());
                    
                    
                    // SS selector must be non-null
                    if(isNullSelector(ss))
                        throw CPUException.getGeneralProtectionFault(EXT);

                    // Selector index must be within its descriptor table limits
                    Descriptor descSS;
                    if((descSS = getDescriptor(ss)) == null)
                        throw CPUException.getInvalidTSS((ss & 0xfffc) + EXT);

                    // Selectors RPL and DPL must be equal to the code segments DPL
                    if(descCS.getDPL() != getSelectorsRPL(ss) ||
                       descCS.getDPL() != descSS.getDPL()) {
                        
                        throw CPUException.getInvalidTSS((ss & 0xfffc) + EXT);
                    }

                    // Descriptor must indicate writable data segment
                    if(!descSS.getTypeInfo().isWritableDataSegment())
                        throw CPUException.getInvalidTSS((ss & 0xfffc) + EXT);

                    // Segment must be present
                    if(!descSS.isPresent())
                        throw CPUException.getStackFault((ss & 0xfffc) + EXT);
                    
                    
                    // Load SS:eSP
                    SS.loadProtectedMode(ss, descSS);
                    if(SS.isSize32())
                        ESP.setValue(sp);
                    else
                        SP.setValue(sp);
                    
                    
                    // Push return information on the new stack
                    if(descGate.getTypeInfo().is32BitGate()) {

                        if(FLAGS.VM) {

                            pushStack32(GS.getSelector());
                            pushStack32(FS.getSelector());
                            pushStack32(DS.getSelector());
                            pushStack32(ES.getSelector());
                            
                            DS.invalidate();
                            ES.invalidate();
                            FS.invalidate();
                            GS.invalidate();
                        }
                        
                        pushStack32(oldSS);
                        pushStack32(oldSP);
                    }
                    else {

                        pushStack16(oldSS);
                        pushStack16(oldSP);
                    }
                }
                
                //
                // Trap or Interrupt Gate: Same privilege
                //
                else if(descCS.getTypeInfo().isConformingCodeSegment() ||
                        descCS.getDPL() == getCPL()) {

                    // This case gets handled down bellow as it shares the
                    // same steps as in the "More privilege" branch on top
                }
                
                //
                // Trap or Interrupt Gate: Invalid code segment type
                //
                else {
                    
                    throw CPUException.getGeneralProtectionFault((cs & 0xfffc) + EXT);
                }
                
                // Push return information on the stack
                if(descGate.getTypeInfo().is32BitGate()) {

                    pushStack32(FLAGS.getValue());
                    pushStack32(CS.getSelector());
                    pushStack32(EIP.getValue());

                    if(exception != null && exception.hasErrorCode())
                        pushStack32(exception.getErrorCode());
                }
                else {

                    pushStack16(FLAGS.getValue());
                    pushStack16(CS.getSelector());
                    pushStack16(EIP.getValue());

                    if(exception != null && exception.hasErrorCode())
                        pushStack16(exception.getErrorCode());
                }
                
                // Load CS:IP
                CS.loadProtectedMode(cs, descCS);
                EIP.setValue(ip);
                
                // Update flags
                FLAGS.NT = false;
                FLAGS.TF = false;
                FLAGS.VM = false;
                if(descGate.getTypeInfo().isInterruptGate())
                    FLAGS.IF = false;
            }
            
            //
            // Task Gate
            //
            else if(descGate.getTypeInfo().isTaskGate()) {
                
                int tssSelector = descGate.getTargetSegment();
                
                // Must specify global in the local/global bit
                if(isReferencingLDT(tssSelector))
                    throw CPUException.getInvalidTSS(tssSelector & 0xfffc);

                // Index must be within GDT limits
                Descriptor tssDescriptor;
                if((tssDescriptor = getDescriptor(GDT, tssSelector & 0xfff8)) == null)
                    throw CPUException.getInvalidTSS(tssSelector & 0xfffc);

                // AR byte must specify an available task state segment
                if(!tssDescriptor.getTypeInfo().isAvailableTaskStateSegment())
                    throw CPUException.getInvalidTSS(tssSelector & 0xfffc);
                
                // TSS must be present
                if(!tssDescriptor.isPresent())
                    throw CPUException.getSegmentNotPresent(tssSelector & 0xfffc);

                // Switch to task
                TR.switchToTask(tssSelector, tssDescriptor, TaskRegister.TASKSWITCH_INT);
                
                // Push error code (if any)
                if((exception != null) && exception.hasErrorCode()) {

                    if(tssDescriptor.getTypeInfo().is286TaskStateSegment())
                        pushStack16(exception.getErrorCode());
                    else
                        pushStack32(exception.getErrorCode());
                }
            }
            
            //
            // Invalid
            //
            else {
                
                throw CPUException.getGeneralProtectionFault(offset + 2 + EXT);
            }
        }
        catch(CPUException ex) {
            
            ESP.setValue(oldESP);
            
            throw ex;
        }
    
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Codeblock cache">
    
    public void invalidateAddress(int addrStart, int offs) {
        
        // Everything about this is sooooo hacky... Find a way to do this
        // properly
        
        if((addrStart & ~ATSystemRAM.RAM_SIZE_MASK) == 0) {
            
            m_codeBlockCache[addrStart >>> 12].isValid = false;
            
            if(m_currentBlock.isCoveringPhysicalAddress(addrStart, addrStart + offs))
                m_currentBlock.invalidate();
        }
    }
    
    public CodeBlock getCurrentBlock() {
        
        int address = m_mmu.getPhysicalAddress(CS.getBase() + EIP.getValue(), false, true);
        
        CodeBlockCache cache = m_codeBlockCache[address >>> 12];
        CodeBlock block;
        
        if(cache.isValid) {
            
            // Try to find the block and check if it's the right one
            if((block = cache.map.get(m_integerLUT[address & 0xfff])) != null) {
                
                if(block.isMatching(
                        
                    address,
                    CS.getSelector(),
                    EIP.getValue(),
                    CS.isSize32(),
                    SS.isSize32())) {
                    
                    return block;
                }
            }
        }
        else {
            
            // A write to memory into this page happened... We just discard
            // the whole block cache for this page. TODO: Find a better way.
            // The method in Intel8086 sucks also as it creates a ton of
            // HashMap entry iterator objects...
            cache.map.clear();
            cache.isValid = true;
        }
        
        // Decode the block and put it into the cache
        block = m_decoder.decodeCodeBlock();
        if(block.isCacheable())
            cache.map.put(m_integerLUT[address & 0xfff], block);
        
        return block;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Descriptor cache">
    
    public Descriptor getDescriptor(int selector) {
        
        int offset = selector & 0xfff8;
        
        if(isReferencingGDT(selector))
            return getDescriptor(GDT, offset);
        else
            return getDescriptor(LDT, offset);
    }
    
    public Descriptor getDescriptor(DescriptorTable table, int offset) {
        
        if(table.isOutsideLimit(offset, 8))
            return null;
        
        Descriptor desc = m_descriptorCache[m_descriptorIndex];
        desc.setDescriptor(
                
            m_mmu.readMEM32(table.getBase() + offset, false),
            m_mmu.readMEM32(table.getBase() + offset + 4, false)
        );
        m_descriptorIndex = (m_descriptorIndex + 1) & 0x03;
        
        return desc;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Methods for I/O access">
    
    public int readIO8(int port) {
        
        TR.checkIOAccess(port, 1);
        return m_ioMap.readIO8(port);
    }
    
    public int readIO16(int port) {
        
        TR.checkIOAccess(port, 2);
        return m_ioMap.readIO16(port);
    }
    
    public int readIO32(int port) {
        
        TR.checkIOAccess(port, 4);
        return m_ioMap.readIO32(port);
    }
    
    public void writeIO8(int port, int data) {
        
        TR.checkIOAccess(port, 1);
        m_ioMap.writeIO8(port, data & 0xff);
    }
    
    public void writeIO16(int port, int data) {
        
        TR.checkIOAccess(port, 2);
        m_ioMap.writeIO16(port, data & 0xffff);
    }
    
    public void writeIO32(int port, int data) {
        
        TR.checkIOAccess(port, 4);
        m_ioMap.writeIO32(port, data);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Methods for memory access">
    
    public int readMEM8(Segment segment, int offset) {
        
        segment.checkProtectionRead(offset, 1);
        return m_mmu.readMEM8(segment.getBase() + offset, MMU.USER_ACCESS);
    }
    
    public int readMEM16(Segment segment, int offset) {
        
        segment.checkProtectionRead(offset, 2);
        return m_mmu.readMEM16(segment.getBase() + offset, MMU.USER_ACCESS);
    }
    
    public int readMEM32(Segment segment, int offset) {
        
        segment.checkProtectionRead(offset, 4);
        return m_mmu.readMEM32(segment.getBase() + offset, MMU.USER_ACCESS);
    }
    
    public void writeMEM8(Segment segment, int offset, int data) {
        
        segment.checkProtectionWrite(offset, 1);
        m_mmu.writeMEM8(segment.getBase() + offset, data, MMU.USER_ACCESS);
    }
    
    public void writeMEM16(Segment segment, int offset, int data) {
        
        segment.checkProtectionWrite(offset, 2);
        m_mmu.writeMEM16(segment.getBase() + offset, data, MMU.USER_ACCESS);
    }
    
    public void writeMEM32(Segment segment, int offset, int data) {
        
        segment.checkProtectionWrite(offset, 4);
        m_mmu.writeMEM32(segment.getBase() + offset, data, MMU.USER_ACCESS);
    }
    
    public void pushStack16(int data) {
        
        if(SS.isSize32()) {
            
            ESP.setValue(ESP.getValue() - 2);
            writeMEM16(SS, ESP.getValue(), data);
        }
        else {
            
            SP.setValue(SP.getValue() - 2);
            writeMEM16(SS, SP.getValue(), data);
        }
    }
    
    public void pushStack32(int data) {
        
        if(SS.isSize32()) {
            
            ESP.setValue(ESP.getValue() - 4);
            writeMEM32(SS, ESP.getValue(), data);
        }
        else {
            
            SP.setValue(SP.getValue() - 4);
            writeMEM32(SS, SP.getValue(), data);
        }
    }
    
    public int popStack16() {
        
        int data;
        
        if(SS.isSize32()) {
            
            data = readMEM16(SS, ESP.getValue());
            ESP.setValue(ESP.getValue() + 2);
        }
        else {
            
            data = readMEM16(SS, SP.getValue());
            SP.setValue(SP.getValue() + 2);
        }
        
        return data;
    }
    
    public int popStack32() {
        
        int data;
        
        if(SS.isSize32()) {
            
            data = readMEM32(SS, ESP.getValue());
            ESP.setValue(ESP.getValue() + 4);
        }
        else {
            
            data = readMEM32(SS, SP.getValue());
            SP.setValue(SP.getValue() + 4);
        }
        
        return data;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Some helper">
    
    public CPUType getCPUType() {
        
        return m_cpuType;
    }
    
    public boolean hasFPU() {
        
        return m_hasFPU;
    }
    
    public MMU getMMU() {
        
        return m_mmu;
    }
    
    public int getCPL() {
        
        return CS.getDPL();
    }
    
    public int getSelectorsRPL(int selector) {
        
        return selector & 0x03;
    }
    
    public boolean isNullSelector(int selector) {
        
        return (selector & 0xfffc) == 0;
    }
    
    public boolean isReferencingGDT(int selector) {
        
        return (selector & 0x04) == 0;
    }
    
    public boolean isReferencingLDT(int selector) {
        
        return (selector & 0x04) != 0;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Dump of the current cpu state">
    
    private String dumpSegment(Segment seg) {
        
        return String.format("%s:%04x (Base:%08x, Limit:%08x, Size:%dbit, DPL:%d, RPL:%d, Valid:%s, Type:%s)",
                
            seg.toString(),
            seg.getSelector(),
            seg.getBase(),
            seg.getLimit(),
            seg.isSize32() ? 32 : 16,
            seg.getDPL(),
            seg.getRPL(),
            seg.isInvalid() ? "No" : "Yes",
            CS.getTypeInfo().toString()
        );
    }
    
    @Override
    public String toString() {
        
        String gpr = String.format("EAX:%08x, EBX:%08x, ECX:%08x, EDX:%08x, ESP:%08x, EBP:%08x, ESI:%08x, EDI:%08x EIP:%08x",
                
            EAX.getValue(),
            EBX.getValue(),
            ECX.getValue(),
            EDX.getValue(),
            ESP.getValue(),
            EBP.getValue(),
            ESI.getValue(),
            EDI.getValue(),
            EIP.getValue()
        );
        
        String flags = "Flags:" + FLAGS.toString();
        
        String segCS = dumpSegment(CS);
        String segDS = dumpSegment(DS);
        String segES = dumpSegment(ES);
        String segFS = dumpSegment(FS);
        String segGS = dumpSegment(GS);
        String segSS = dumpSegment(SS);
        
        String gdt = String.format("GDT:%08x (Limit:%04x)",
                
            GDT.getBase(),
            GDT.getLimit()
        );
        
        String idt = String.format("IDT:%08x (Limit:%04x)",
                
            IDT.getBase(),
            IDT.getLimit()
        );
        
        String ldt = String.format("LDT:%08x (Selector:%04x, Limit:%04x, Valid:%s)",
                
            LDT.getBase(),
            LDT.getSelector(),
            LDT.getLimit(),
            LDT.isInvalid() ? "No" : "Yes"
        );
        
        String tr = String.format("TR:%08x (Selector:%04x, Limit:%04x, Busy:%s)",
                
            TR.getBase(),
            TR.getSelector(),
            TR.getLimit(),
            TR.isBusy() ? "Yes" : "No"
        );
        
        String special = String.format("CPL:%d, CR0:%08x, CR2:%08x, CR3:%08x, Protected Mode: %b, Paging: %b",
                
            getCPL(),
            CR.getCR0(),
            CR.getCR2(),
            CR.getCR3(),
            CR.isInProtectedMode(),
            CR.isPagingEnabled()
        );
        
        String fpu = FPU.toString();
        
        String block = m_currentBlock != null ? m_currentBlock.toString() : "No information";
        
        return "CPU State:\n" + gpr + "\n" + flags + "\n" + segCS + "\n" + segDS + "\n" + segES +
               "\n" + segFS + "\n" + segGS + "\n" + segSS + "\n" + gdt + "\n" + idt +
               "\n" + ldt + "\n" + tr + "\n" + special + "\n" + fpu + "\n\n" + "Dump of the current code block:\n" + block;
    }
    
    // </editor-fold>
}
