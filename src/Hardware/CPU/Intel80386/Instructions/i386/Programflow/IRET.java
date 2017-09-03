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
package Hardware.CPU.Intel80386.Instructions.i386.Programflow;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Register.Flags.Flags;
import Hardware.CPU.Intel80386.Register.Segments.Descriptor;
import Hardware.CPU.Intel80386.Register.TaskRegister.TaskRegister;



public final class IRET extends Instruction {
    
    private final boolean m_is32;
    
    public IRET(Intel80386 cpu,
                boolean is32) {
        
        super(cpu);
        
        m_is32 = is32;
    }

    @Override
    public void run() {
        
        int oldESP = m_cpu.ESP.getValue();
        try {
        
            if(m_cpu.CR.isInRealMode())
                IRET_REALMODE();
            else if(m_cpu.FLAGS.VM)
                IRET_VIRTUALMODE();
            else
                IRET_PROTECTEDMODE();
        }
        catch(CPUException ex) {
            
            m_cpu.ESP.setValue(oldESP);
            
            throw ex;
        }
    }
    
    private void IRET_REALMODE() {
        
        int cs, ip, flags;
        
        // Read return address and flags
        if(m_is32) {

            ip = m_cpu.popStack32();
            cs = m_cpu.popStack32() & 0xffff;
            flags = m_cpu.popStack32();
        }
        else {

            ip = m_cpu.popStack16();
            cs = m_cpu.popStack16();
            flags = m_cpu.popStack16();
        }

        // Load CS:EIP
        m_cpu.CS.loadRealMode(cs);
        m_cpu.EIP.setValue(ip);
        
        // Load flags
        m_cpu.FLAGS.setValue(flags, m_is32 ? Flags.MASK_EFLAGS : Flags.MASK_FLAGS);
    }
    
    private void IRET_VIRTUALMODE() {
        
        if(m_cpu.FLAGS.IOPL != 3)
            throw CPUException.getGeneralProtectionFault(0);

        int cs, ip, flags, flagMask;
        
        // Read return address and flags
        if(m_is32) {

            ip = m_cpu.popStack32();
            cs = m_cpu.popStack32() & 0xffff;
            flags = m_cpu.popStack32();
            flagMask = Flags.MASK_EFLAGS;
        }
        else {

            ip = m_cpu.popStack16();
            cs = m_cpu.popStack16();
            flags = m_cpu.popStack16();
            flagMask = Flags.MASK_FLAGS;
        }
        
        // Load CS:EIP
        m_cpu.CS.loadVirtualMode(cs);
        m_cpu.EIP.setValue(ip);

        // Load flags (VM and IOPL are not modified)
        m_cpu.FLAGS.setValue(flags, flagMask & ~(Flags.MASK_VM_8086 | Flags.MASK_IOPL));
    }
    
    private void IRET_PROTECTEDMODE() {
        
        if(m_cpu.CR.isInProtectedMode() && m_cpu.FLAGS.VM && m_cpu.FLAGS.IOPL != 3)
            throw CPUException.getGeneralProtectionFault(0);
        
        
        if(m_cpu.FLAGS.NT) {
            
            IRET_TASK_RETURN();
        }
        else {
            
            // Read return address and flags
            int cs, ip, flags;
            if(m_is32) {

                ip = m_cpu.popStack32();
                cs = m_cpu.popStack32() & 0xffff;
                flags = m_cpu.popStack32();
                
                if((flags & Flags.MASK_VM_8086) != 0) {
                    
                    IRET_RETURN_TO_V86(cs, ip, flags);
                    return;
                }
            }
            else {

                ip = m_cpu.popStack16();
                cs = m_cpu.popStack16();
                flags = m_cpu.popStack16();
            }
            
            // Get current privilege level
            int CPL = m_cpu.getCPL();
            

            // Determine which flags can be restored
            int flagMask = m_is32 ? Flags.MASK_EFLAGS : Flags.MASK_FLAGS;
            if(CPL != 0)
                flagMask &= ~Flags.MASK_IOPL;
            if(m_cpu.FLAGS.IOPL < CPL)
                flagMask &= ~Flags.MASK_INTERRUPT_ENABLE;
            
            // Return CS selector RPL must be >= CPL
            if(m_cpu.getSelectorsRPL(cs) < CPL)
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
            
            // Return CS selector must be non-null
            if(m_cpu.isNullSelector(cs))
                throw CPUException.getGeneralProtectionFault(0);
            
            // Selector index must be within its descriptor table limits
            Descriptor descCS;
            if((descCS = m_cpu.getDescriptor(cs)) == null)
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
            
            // AR byte must indicate code segment
            if(!descCS.getTypeInfo().isCodeSegment())
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
            
            // Segment must be present
            if(!descCS.isPresent())
                throw CPUException.getSegmentNotPresent(cs & 0xfffc);
            
            // Instruction pointer must be within code segment limits
            if(descCS.isOutsideLimit(ip, 1))
                throw CPUException.getGeneralProtectionFault(0);


            // Perform the return
            if(m_cpu.getSelectorsRPL(cs) == CPL)
                IRET_RETURN_TO_SAME_LEVEL(cs, ip, flags, flagMask, descCS, CPL);
            else
                IRET_RETURN_TO_OUTER_LEVEL(cs, ip, flags, flagMask, descCS, CPL);
        }
    }
    
    
    
    private void IRET_TASK_RETURN() {
        
        int tssSelector = m_cpu.TR.getBackLink();

        // The selector must reference GDT
        if(m_cpu.isReferencingLDT(tssSelector))
            throw CPUException.getInvalidTSS(tssSelector & 0xfffc);

        // Index must be within GDT limits
        Descriptor tssDesc;
        if((tssDesc = m_cpu.getDescriptor(m_cpu.GDT, tssSelector & 0xfff8)) == null)
            throw CPUException.getInvalidTSS(tssSelector & 0xfffc);
        
        // New TSS must be a busy task state segment
        if(!tssDesc.getTypeInfo().isBusyTaskStateSegment())
            throw CPUException.getInvalidTSS(tssSelector & 0xfffc);

        // TSS must be present
        if(!tssDesc.isPresent())
            throw CPUException.getSegmentNotPresent(tssSelector & 0xfffc);
        
        // Switch back to the old task
        m_cpu.TR.switchToTask(tssSelector, tssDesc, TaskRegister.TASKSWITCH_IRET);
    }
    
    private void IRET_RETURN_TO_V86(int cs, int ip, int flags) {
        
        // Pop new stack segment & pointer
        int sp = m_cpu.popStack32();
        int ss = m_cpu.popStack32() & 0xffff;
        
        // Pop data register
        int es = m_cpu.popStack32() & 0xffff;
        int ds = m_cpu.popStack32() & 0xffff;
        int fs = m_cpu.popStack32() & 0xffff;
        int gs = m_cpu.popStack32() & 0xffff;
        
        // Update flags
        m_cpu.FLAGS.setValue(flags, Flags.MASK_EFLAGS);

        // Load registers
        m_cpu.DS.loadVirtualMode(ds);
        m_cpu.ES.loadVirtualMode(es);
        m_cpu.FS.loadVirtualMode(fs);
        m_cpu.GS.loadVirtualMode(gs);

        // Load SS:ESP
        m_cpu.SS.loadVirtualMode(ss);
        m_cpu.ESP.setValue(sp);

        // Load CS:EIP
        m_cpu.CS.loadVirtualMode(cs);
        m_cpu.EIP.setValue(ip);
    }
    
    private void IRET_RETURN_TO_SAME_LEVEL(int cs, int ip, int flags, int flagMask, Descriptor descCS, int CPL) {
        
        // If non-conforming then code segment DPL must be equal to CPL
        if(descCS.getTypeInfo().isNonConformingCodeSegment()) {

            if(descCS.getDPL() != CPL)
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        }
        // If conforming then code segment DPL must be <= CPL
        else if(descCS.getTypeInfo().isConformingCodeSegment()) {

            if(descCS.getDPL() > CPL)
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        }

        // Load CS:EIP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);

        // Load flags
        m_cpu.FLAGS.setValue(flags, flagMask);
    }
    
    private void IRET_RETURN_TO_OUTER_LEVEL(int cs, int ip, int flags, int flagMask, Descriptor descCS, int CPL) {
        
        // If non-conforming then code segment DPL must be equal to the CS selectors RPL
        if(descCS.getTypeInfo().isNonConformingCodeSegment()) {

            if(descCS.getDPL() != m_cpu.getSelectorsRPL(cs))
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        }
        // If conforming then code segment DPL must be > CPL */
        else if(descCS.getTypeInfo().isConformingCodeSegment()) {

            if(descCS.getDPL() <= CPL)
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        }

        
        // Load stack segment & pointer from stack
        int ss, sp;
        if(m_is32) {

            sp = m_cpu.popStack32();
            ss = m_cpu.popStack32() & 0xffff;
        }
        else {

            sp = m_cpu.popStack16();
            ss = m_cpu.popStack16();
        }
        
        // Return SS selector must be non-null
        if(m_cpu.isNullSelector(ss))
            throw CPUException.getGeneralProtectionFault(0);

        // Selector index must be within its descriptor table limits
        Descriptor descSS;
        if((descSS = m_cpu.getDescriptor(ss)) == null)
            throw CPUException.getGeneralProtectionFault(ss & 0xfffc);

        /* Selector RPL must equal the RPL of the return CS selector */
        if(m_cpu.getSelectorsRPL(ss) != m_cpu.getSelectorsRPL(cs))
            throw CPUException.getGeneralProtectionFault(ss & 0xfffc);

        // AR byte must indicate a writable data segment
        if(!descSS.getTypeInfo().isWritableDataSegment())
            throw CPUException.getGeneralProtectionFault(ss & 0xfffc);

        // Stack segment DPL must equal the RPL of the return CS selector
        if(descSS.getDPL() != m_cpu.getSelectorsRPL(cs))
            throw CPUException.getGeneralProtectionFault(ss & 0xfffc);

        // SS must be present
        if(!descSS.isPresent())
            throw CPUException.getSegmentNotPresent(ss & 0xfffc);
        
        
        // Load CS:EIP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);
        
        // Load SS:SP
        m_cpu.SS.loadProtectedMode(ss, descSS);
        if(m_cpu.SS.isSize32())
            m_cpu.ESP.setValue(sp);
        else
            m_cpu.SP.setValue(sp);
        
        // Load flags
        m_cpu.FLAGS.setValue(flags, flagMask);

        // Check segment validity for outer level
        m_cpu.DS.validateForOuterLevel();
        m_cpu.ES.validateForOuterLevel();
        m_cpu.FS.validateForOuterLevel();
        m_cpu.GS.validateForOuterLevel();
    }
    
    @Override
    public String toString() {
        
        return m_is32 ? "iretd" : "iret";
    }
}
