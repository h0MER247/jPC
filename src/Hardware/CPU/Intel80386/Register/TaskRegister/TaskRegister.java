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
package Hardware.CPU.Intel80386.Register.TaskRegister;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.MMU.MMU;
import Hardware.CPU.Intel80386.Register.Flags.Flags;
import Hardware.CPU.Intel80386.Register.Segments.*;
import Hardware.CPU.Intel80386.Register.Segments.SegmentTypes.SegmentType;




/**
 * This is currently a mess
 */
public final class TaskRegister {
    
    /* ----------------------------------------------------- *
     * Operations that can cause a task switch               *
     * ----------------------------------------------------- */
    public static final int TASKSWITCH_INT = 0;
    public static final int TASKSWITCH_IRET = 1;
    public static final int TASKSWITCH_JMP = 2;
    public static final int TASKSWITCH_CALL = 3;
    
    /* ----------------------------------------------------- *
     * Offsets in the task state segments                    *
     * ----------------------------------------------------- */
    private final int TSS_BACKLINK = 0x00;
    private final int TSS286_SP0 = 0x02;
    private final int TSS286_SS0 = 0x04;
    private final int TSS286_IP = 0x0e;
    private final int TSS286_FLAGS = 0x10;
    private final int TSS286_AX = 0x12;
    private final int TSS286_CX = 0x14;
    private final int TSS286_DX = 0x16;
    private final int TSS286_BX = 0x18;
    private final int TSS286_SP = 0x1a;
    private final int TSS286_BP = 0x1c;
    private final int TSS286_SI = 0x1e;
    private final int TSS286_DI = 0x20;
    private final int TSS286_ES = 0x22;
    private final int TSS286_CS = 0x24;
    private final int TSS286_SS = 0x26;
    private final int TSS286_DS = 0x28;
    private final int TSS286_LDT = 0x2a;
    private final int TSS386_ESP0 = 0x04;
    private final int TSS386_SS0 = 0x08;
    private final int TSS386_PDBR = 0x1c;
    private final int TSS386_EIP = 0x20;
    private final int TSS386_FLAGS = 0x24;
    private final int TSS386_EAX = 0x28;
    private final int TSS386_ECX = 0x2c;
    private final int TSS386_EDX = 0x30;
    private final int TSS386_EBX = 0x34;
    private final int TSS386_ESP = 0x38;
    private final int TSS386_EBP = 0x3c;
    private final int TSS386_ESI = 0x40;
    private final int TSS386_EDI = 0x44;
    private final int TSS386_ES = 0x48;
    private final int TSS386_CS = 0x4c;
    private final int TSS386_SS = 0x50;
    private final int TSS386_DS = 0x54;
    private final int TSS386_FS = 0x58;
    private final int TSS386_GS = 0x5c;
    private final int TSS386_LDT = 0x60;
    private final int TSS386_TRAP = 0x64;
    private final int TSS386_IOMAP = 0x66;
    
    /* ----------------------------------------------------- *
     * Task register state                                   *
     * ----------------------------------------------------- */
    private int m_selector;
    private int m_base;
    private int m_limit;
    private boolean m_isBusy;
    private int m_type;
    private SegmentType m_typeInfo;
    
    /* ----------------------------------------------------- *
     * Reference to the cpu and mmu                          *
     * ----------------------------------------------------- */
    private final Intel80386 m_cpu;
    private final MMU m_mmu;
    
    
    
    public TaskRegister(Intel80386 cpu, MMU mmu) {
        
        m_cpu = cpu;
        m_mmu = mmu;
    }
    
    
    public void reset() {
        
        setSelector(0);
        setBase(0);
        setLimit(0);
        
        m_isBusy = false;
    }
    
    
    
    public void loadTaskRegister(int selector, Descriptor descriptor) {
        
        setSelector(selector);
        setBase(descriptor.getBase());
        setLimit(descriptor.getLimit());
        setType(descriptor.getType());
    }
    
    
    
    private void setSelector(int selector) {
        
        m_selector = selector;
    }
    
    public int getSelector() {
        
        return m_selector;
    }
    
    private void setBase(int base) {
        
        m_base = base;
    }
    
    public int getBase() {
        
        return m_base;
    }
    
    private void setLimit(int limit) {
        
        m_limit = limit;
    }
    
    public int getLimit() {
        
        return m_limit;
    }
    
    private void setType(int type) {
        
        m_type = type;
        
        m_typeInfo = SegmentTypes.SEGMENT_TYPES[type];
    }
    
    private boolean isOutsideLimit(int offset, int size) {
        
        return Integer.compareUnsigned(m_limit, offset + size - 1) < 0;
    }
    
    
    
    public void setBusyFlag(boolean isBusy) {
        
        m_isBusy = isBusy;
        
        int address;
        if(m_cpu.isReferencingGDT(m_selector))
            address = m_cpu.GDT.getBase() + (m_selector & 0xfff8) + 5;
        else
            address = m_cpu.LDT.getBase() + (m_selector & 0xfff8) + 5;
        
        if(isBusy) {
            
            m_mmu.writeMEM8(address, m_mmu.readMEM8(address, false) | 0x02, false);
            setType(m_type | 0x02);
        }
        else {
            
            m_mmu.writeMEM8(address, m_mmu.readMEM8(address, false) & ~0x02, false);
            setType(m_type & ~0x02);
        }
    }
    
    
    public boolean isBusy() {
        
        return m_isBusy;
    }
    

    
    public int getBackLink() {
        
        return m_mmu.readMEM16(m_base + TSS_BACKLINK, false);
    }
    
    public int getStackSegment(int level) {
        
        int address;
        if(m_typeInfo.is386TaskStateSegment())
            address = TSS386_SS0 + (level << 3);
        else
            address = TSS286_SS0 + (level << 2);
        
        if(isOutsideLimit(address, 2))
            throw CPUException.getInvalidTSS(m_selector & 0xfffc);
        
        return m_mmu.readMEM16(m_base + address, false);
    }
    
    public int getStackPointer(int level)  {
        
        if(m_typeInfo.is386TaskStateSegment()) {
            
            int address = TSS386_ESP0 + (level << 3);
            if(isOutsideLimit(address, 4))
                throw CPUException.getInvalidTSS(m_selector & 0xfffc);
            
            return m_mmu.readMEM32(m_base + address, false);
        }
        else {
            
            int address = TSS286_SP0 + (level << 2);
            if(isOutsideLimit(address, 2))
                throw CPUException.getInvalidTSS(m_selector & 0xfffc);
            
            return m_mmu.readMEM16(m_base + address, false);
        }
    }
    
    public void checkIOAccess(int port, int size) {
        
        if(m_cpu.CR.isInProtectedMode() && (m_cpu.FLAGS.VM || m_cpu.getCPL() > m_cpu.FLAGS.IOPL)) {
            
            if(m_typeInfo.is286TaskStateSegment())
                throw CPUException.getGeneralProtectionFault(0);    
            
            int addr = m_mmu.readMEM16(m_base + TSS386_IOMAP, false) + (port >>> 3);
            int access = m_mmu.readMEM16(m_base + addr, false);
            int mask = ((size << 1) - 1) << (port & 0x07);
            
            if((access & mask) != 0)
                throw CPUException.getGeneralProtectionFault(0);
        }
    }
    
    
    
    public void switchToTask(int selector, Descriptor descriptor, int operationType) {
        
        if(descriptor.getTypeInfo().is286TaskStateSegment())
            switchTask286(selector, descriptor, operationType);
        else
            switchTask386(selector, descriptor, operationType);
    }
    
    private void switchTask286(int newTSSSelector, Descriptor newTSSDescriptor, int operationType) {
        
        throw new UnsupportedOperationException("Implement me");
    }
    
    private void switchTask386(int tss, Descriptor descTSS, int opType) {
        
        // Limit of incoming TSS must be greater than or equal to 103
        if(descTSS.getLimit() < 103)
            throw CPUException.getInvalidTSS(tss & 0xfffc);
        
        
        // Clear current tasks busy flag
        if(opType == TASKSWITCH_JMP || opType == TASKSWITCH_IRET)
            setBusyFlag(false);
        
        //
        // Store cpu context inside the task that's currently running
        //
        m_mmu.writeMEM32(m_base + TSS386_PDBR, m_cpu.CR.getCR3(), false);
        m_mmu.writeMEM32(m_base + TSS386_EIP, m_cpu.EIP.getValue(), false);
        if(opType == TASKSWITCH_IRET)
            m_mmu.writeMEM32(m_base + TSS386_FLAGS, m_cpu.FLAGS.getValue() & ~Flags.MASK_NESTED_TASK, false);
        else
            m_mmu.writeMEM32(m_base + TSS386_FLAGS, m_cpu.FLAGS.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_EAX, m_cpu.EAX.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_EBX, m_cpu.EBX.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_ECX, m_cpu.ECX.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_EDX, m_cpu.EDX.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_ESP, m_cpu.ESP.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_EBP, m_cpu.EBP.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_ESI, m_cpu.ESI.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_EDI, m_cpu.EDI.getValue(), false);
        m_mmu.writeMEM32(m_base + TSS386_CS, m_cpu.CS.getSelector(), false);
        m_mmu.writeMEM32(m_base + TSS386_DS, m_cpu.DS.getSelector(), false);
        m_mmu.writeMEM32(m_base + TSS386_ES, m_cpu.ES.getSelector(), false);
        m_mmu.writeMEM32(m_base + TSS386_FS, m_cpu.FS.getSelector(), false);
        m_mmu.writeMEM32(m_base + TSS386_GS, m_cpu.GS.getSelector(), false);
        m_mmu.writeMEM32(m_base + TSS386_SS, m_cpu.SS.getSelector(), false);
        
        // Store the selector of the current task in the backlink field of the
        // new task, so that the IRET instruction can return to the current task
        if(opType == TASKSWITCH_CALL || opType == TASKSWITCH_INT)
            m_mmu.writeMEM32(descTSS.getBase() + TSS_BACKLINK, m_selector, false);
        
        
        //
        // Load cpu context from the new task
        //
        int pdbr = m_mmu.readMEM32(descTSS.getBase() + TSS386_PDBR, false);
        int ip = m_mmu.readMEM32(descTSS.getBase() + TSS386_EIP, false);
        int flags = m_mmu.readMEM32(descTSS.getBase() + TSS386_FLAGS, false);
        int eax = m_mmu.readMEM32(descTSS.getBase() + TSS386_EAX, false);
        int ebx = m_mmu.readMEM32(descTSS.getBase() + TSS386_EBX, false);
        int ecx = m_mmu.readMEM32(descTSS.getBase() + TSS386_ECX, false);
        int edx = m_mmu.readMEM32(descTSS.getBase() + TSS386_EDX, false);
        int esp = m_mmu.readMEM32(descTSS.getBase() + TSS386_ESP, false);
        int ebp = m_mmu.readMEM32(descTSS.getBase() + TSS386_EBP, false);
        int esi = m_mmu.readMEM32(descTSS.getBase() + TSS386_ESI, false);
        int edi = m_mmu.readMEM32(descTSS.getBase() + TSS386_EDI, false);
        int cs = m_mmu.readMEM32(descTSS.getBase() + TSS386_CS, false) & 0xffff;
        int ds = m_mmu.readMEM32(descTSS.getBase() + TSS386_DS, false) & 0xffff;
        int es = m_mmu.readMEM32(descTSS.getBase() + TSS386_ES, false) & 0xffff;
        int fs = m_mmu.readMEM32(descTSS.getBase() + TSS386_FS, false) & 0xffff;
        int gs = m_mmu.readMEM32(descTSS.getBase() + TSS386_GS, false) & 0xffff;
        int ss = m_mmu.readMEM32(descTSS.getBase() + TSS386_SS, false) & 0xffff;
        int ldt = m_mmu.readMEM32(descTSS.getBase() + TSS386_LDT, false) & 0xffff;
        
        // Set nested task flag if the task switch was issued by an interrupt or
        // call instruction
        if(opType == TASKSWITCH_INT || opType == TASKSWITCH_CALL)
            flags |= Flags.MASK_NESTED_TASK;
        

        //
        // Load the new task
        //
        loadTaskRegister(tss, descTSS);
        m_cpu.CR.setTaskSwitched(true);
        
        // Set the new tasks busy flag
        if(opType != TASKSWITCH_IRET)
            setBusyFlag(true);
        
        // Set all registers to values from the new TSS
        m_cpu.FLAGS.setValue(flags, Flags.MASK_EFLAGS);
        m_cpu.CR.setCR3(pdbr);
        m_cpu.EIP.setValue(ip);
        m_cpu.EAX.setValue(eax);
        m_cpu.EBX.setValue(ebx);
        m_cpu.ECX.setValue(ecx);
        m_cpu.EDX.setValue(edx);
        m_cpu.ESP.setValue(esp);
        m_cpu.EBP.setValue(ebp);
        m_cpu.ESI.setValue(esi);
        m_cpu.EDI.setValue(edi);
        
        // Load segment selectors without actually loading any descriptor data
        m_cpu.LDT.setSelector(ldt); m_cpu.LDT.setValid(false);
        m_cpu.CS.setSelector(cs); m_cpu.CS.setValid(false);
        m_cpu.DS.setSelector(ds); m_cpu.DS.setValid(false);
        m_cpu.ES.setSelector(es); m_cpu.ES.setValid(false);
        m_cpu.FS.setSelector(fs); m_cpu.FS.setValid(false);
        m_cpu.GS.setSelector(gs); m_cpu.GS.setValid(false);
        m_cpu.SS.setSelector(ss); m_cpu.SS.setValid(false);
        
        // Now load all descriptor data
        loadLDT(ldt);
        loadCS(cs);
        loadSS(ss);
        loadDataSegment(m_cpu.DS, ds);
        loadDataSegment(m_cpu.ES, es);
        loadDataSegment(m_cpu.FS, fs);
        loadDataSegment(m_cpu.GS, gs);
    }
    
    
    
    private void loadLDT(int ldt) {
        
        if(!m_cpu.isNullSelector(ldt)) {
            
            // Selector has to point into GDT
            if(m_cpu.isReferencingLDT(ldt))
                throw CPUException.getInvalidTSS(ldt & 0xfffc);
            
            // Selector has to be in GDTs limit
            Descriptor desc;
            if((desc = m_cpu.getDescriptor(m_cpu.GDT, ldt & 0xfff8)) == null)
                throw CPUException.getInvalidTSS(ldt & 0xfffc);
            
            // AR byte must indicate LDT
            if(!desc.getTypeInfo().isLDT())
                throw CPUException.getInvalidTSS(ldt & 0xfffc);
            
            // Segment must be present
            if(!desc.isPresent())
                throw CPUException.getInvalidTSS(ldt & 0xfffc);
           
            // Load local descriptor table
            m_cpu.LDT.setBase(desc.getBase());
            m_cpu.LDT.setLimit(desc.getLimit());
            m_cpu.LDT.setValid(true);
        }
    }

    private void loadCS(int cs) {
        
        if(m_cpu.FLAGS.VM) {
            
            m_cpu.CS.loadVirtualMode(cs);
        }
        else {
            
            // CS selector must be non null
            if(m_cpu.isNullSelector(cs))
                throw CPUException.getInvalidTSS(0);

            // CS selector must be within its descriptor table limits
            Descriptor descCS;
            if((descCS = m_cpu.getDescriptor(cs)) == null)
                throw CPUException.getInvalidTSS(cs & 0xfffc);

            // If non-conforming then DPL must equal selectors RPL
            if(descCS.getTypeInfo().isNonConformingCodeSegment()) {

                if(descCS.getDPL() != m_cpu.getSelectorsRPL(cs))
                    throw CPUException.getInvalidTSS(cs & 0xfffc);
            }
            // If conforming then DPL must be <= selectors RPL
            else if(descCS.getTypeInfo().isConformingCodeSegment()) {

                if(descCS.getDPL() > m_cpu.getSelectorsRPL(cs))
                    throw CPUException.getInvalidTSS(cs & 0xfffc);
            }
            // AR byte of the descriptor must indicate a code segment
            else {

                throw CPUException.getInvalidTSS(cs & 0xfffc);
            }

            // Segment must be present
            if(!descCS.isPresent())
                throw CPUException.getSegmentNotPresent(cs & 0xfffc);

            // Load code segment
            m_cpu.CS.loadProtectedMode(cs, descCS);
        }
    }
    
    private void loadSS(int ss) {
        
        if(m_cpu.FLAGS.VM) {
            
            m_cpu.SS.loadVirtualMode(ss);
        }
        else {
        
            // SS selector must be non null
            if(m_cpu.isNullSelector(ss))
                throw CPUException.getInvalidTSS(0);

            // SS selector must be within its descriptor table limits
            Descriptor descSS;
            if((descSS = m_cpu.getDescriptor(ss)) == null)
                throw CPUException.getInvalidTSS(ss & 0xfffc);

            // AR byte must indicate a writable data segment
            if(!descSS.getTypeInfo().isWritableDataSegment())
                throw CPUException.getInvalidTSS(ss & 0xfffc);

            // SS selector RPL and descriptor DPL must be equal to CPL
            if(m_cpu.getCPL() != m_cpu.getSelectorsRPL(ss) ||
               m_cpu.getCPL() != descSS.getDPL()) {

                throw CPUException.getInvalidTSS(ss & 0xfffc);
            }

            // Segment must be present
            if(!descSS.isPresent())
                throw CPUException.getStackFault(ss & 0xfffc);

            // Load stack segment
            m_cpu.SS.loadProtectedMode(ss, descSS);
        }
    }
    
    private void loadDataSegment(Segment segment, int selector) {
        
        if(m_cpu.FLAGS.VM) {
            
            segment.loadVirtualMode(selector);
        }
        else if(!m_cpu.isNullSelector(selector)) {
            
            // Selector must be within its descriptor table limits
            Descriptor desc;
            if((desc = m_cpu.getDescriptor(selector)) == null)
                throw CPUException.getInvalidTSS(selector & 0xfffc);
            
            // Segment has to be a data or readable code segment
            if(!(desc.getTypeInfo().isDataSegment() ||
                 desc.getTypeInfo().isReadableCodeSegment())) {
                
                throw CPUException.getInvalidTSS(selector & 0xfffc);
            }
            
            // The selectors RPL and the CPL must both be less than or equal to DPL
            // if we try to load a data segment or non conforming code segment
            if(desc.getTypeInfo().isDataSegment() ||
               desc.getTypeInfo().isNonConformingCodeSegment()) {

                if(desc.getDPL() < m_cpu.getSelectorsRPL(selector) ||
                   desc.getDPL() < m_cpu.getCPL()) {

                    throw CPUException.getGeneralProtectionFault(selector & 0xfffc);
                }
            }
            
            // Segment must be present
            if(!desc.isPresent())
                throw CPUException.getSegmentNotPresent(selector & 0xfffc);
            
            // Load data segment
            segment.loadProtectedMode(selector, desc);
        }
    }
    
    
    
    @Override
    public String toString() {
        
        return "tr";
    }
}
