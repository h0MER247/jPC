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
import Hardware.CPU.Intel80386.Operands.Operand;
import Hardware.CPU.Intel80386.Register.Segments.Descriptor;



public final class RET_FAR extends Instruction {
    
    private final Operand m_stackDisplacement;
    private final boolean m_is32;
    
    public RET_FAR(Intel80386 cpu,
                   Operand stackDisplacement,
                   boolean is32) {
        
        super(cpu);
        
        m_stackDisplacement = stackDisplacement;
        m_is32 = is32;
    }

    @Override
    public void run() {
        
        int oldESP = m_cpu.ESP.getValue();
        try {
            
            // Pop return address from stack
            int cs, ip;
            if(m_is32) {

                ip = m_cpu.popStack32();
                cs = m_cpu.popStack32() & 0xffff;
            }
            else {

                ip = m_cpu.popStack16();
                cs = m_cpu.popStack16();
            }
            
            if(m_cpu.CR.isInRealMode())
                RET_FAR_REAL_MODE(cs, ip);
            else if(m_cpu.FLAGS.VM)
                RET_FAR_VIRTUAL_MODE(cs, ip);
            else
                RET_FAR_PROTECTED_MODE(cs, ip);
        }
        catch(CPUException ex) {
            
            m_cpu.ESP.setValue(oldESP);
            
            throw ex;
        }
    }
    
    private void RET_FAR_REAL_MODE(int cs, int ip) {
        
        // Load CS:EIP
        m_cpu.CS.loadRealMode(cs);
        m_cpu.EIP.setValue(ip);
        
        // Update stack pointer
        if(m_stackDisplacement != null) {
            
            if(m_cpu.SS.isSize32())
                m_cpu.ESP.setValue(m_cpu.ESP.getValue() + m_stackDisplacement.getValue());
            else
                m_cpu.SP.setValue(m_cpu.SP.getValue() + m_stackDisplacement.getValue());
        }
    }
    
    private void RET_FAR_VIRTUAL_MODE(int cs, int ip) {
        
        // Load CS:EIP
        m_cpu.CS.loadVirtualMode(cs);
        m_cpu.EIP.setValue(ip);
        
        // Update stack pointer
        if(m_stackDisplacement != null) {
            
            if(m_cpu.SS.isSize32())
                m_cpu.ESP.setValue(m_cpu.ESP.getValue() + m_stackDisplacement.getValue());
            else
                m_cpu.SP.setValue(m_cpu.SP.getValue() + m_stackDisplacement.getValue());
        }
    }
    
    private void RET_FAR_PROTECTED_MODE(int cs, int ip) {
        
        // Get current privilege level
        int CPL = m_cpu.getCPL();
        
        
        // Return selector must be non-null
        if(m_cpu.isNullSelector(cs))
            throw CPUException.getGeneralProtectionFault(0);

        // Selector index must be within its descriptor table limits
        Descriptor descCS;
        if((descCS = m_cpu.getDescriptor(cs)) == null)
            throw CPUException.getGeneralProtectionFault(0);

        // Descriptor AR byte must indicate code segment
        if(!descCS.getTypeInfo().isCodeSegment())
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        
        // Return selector RPL must be >= CPL
        if(m_cpu.getSelectorsRPL(cs) < CPL)
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);

        // Code segment must be present
        if(!descCS.isPresent())
            throw CPUException.getSegmentNotPresent(cs & 0xfffc);
        
        
        // Update stack pointer
        if(m_stackDisplacement != null) {
            
            if(m_cpu.SS.isSize32())
                m_cpu.ESP.setValue(m_cpu.ESP.getValue() + m_stackDisplacement.getValue());
            else
                m_cpu.SP.setValue(m_cpu.SP.getValue() + m_stackDisplacement.getValue());
        }
        
        
        // Perform far return
        if(m_cpu.getSelectorsRPL(cs) == CPL)
            RET_FAR_SAME_LEVEL(cs, ip, descCS, CPL);
        else
            RET_FAR_OUTER_LEVEL(cs, ip, descCS, CPL);
    }
    
    private void RET_FAR_SAME_LEVEL(int cs, int ip, Descriptor descCS, int CPL) {

        // Instruction pointer must be within code-segment limit
        if(descCS.isOutsideLimit(ip, 1))
            throw CPUException.getGeneralProtectionFault(0);
        
        // If non-conforming then code segment DPL must be equal to CPL
        if(descCS.getTypeInfo().isNonConformingCodeSegment()) {

            if(descCS.getDPL() != CPL)
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        }
        // If conforming then code segment DPL must be <= CPL
        else {
            
            if(descCS.getDPL() > CPL)
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        }

        // Load CS:EIP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);
    }
    
    private void RET_FAR_OUTER_LEVEL(int cs, int ip, Descriptor descCS, int CPL) {
        
        // Instruction pointer must be within code-segment limit
        if(descCS.isOutsideLimit(ip, 1))
            throw CPUException.getGeneralProtectionFault(0);
        
        // If non-conforming then code segment DPL must be equal to the CS selectors RPL
        if(descCS.getTypeInfo().isNonConformingCodeSegment()) {

            if(descCS.getDPL() != m_cpu.getSelectorsRPL(cs))
                throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        }
        // If conforming then code segment DPL must be <= CS selectors RPL
        else if(descCS.getTypeInfo().isConformingCodeSegment()) {

            if(descCS.getDPL() > m_cpu.getSelectorsRPL(cs))
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
        
        // AR byte must indicate a writable data segment
        if(!descSS.getTypeInfo().isWritableDataSegment())
            throw CPUException.getGeneralProtectionFault(ss & 0xfffc);
        
        // SS must be present
        if(!descSS.isPresent())
            throw CPUException.getSegmentNotPresent(ss & 0xfffc);

        // Stack segment RPL and DPL must be equal to the RPL of the return CS selector
        if(m_cpu.getSelectorsRPL(cs) != m_cpu.getSelectorsRPL(ss) ||
           m_cpu.getSelectorsRPL(cs) != descSS.getDPL()) {
            
            throw CPUException.getGeneralProtectionFault(ss & 0xfffc);
        }
        
        
        // Load CS:EIP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);
        
        // Load SS:SP
        m_cpu.SS.loadProtectedMode(ss, descSS);
        if(m_stackDisplacement != null)
            sp += m_stackDisplacement.getValue();
        if(m_cpu.SS.isSize32())
            m_cpu.ESP.setValue(sp);
        else
            m_cpu.SP.setValue(sp);
        
        
        // Check segment validity for outer level
        m_cpu.DS.validateForOuterLevel();
        m_cpu.ES.validateForOuterLevel();
        m_cpu.FS.validateForOuterLevel();
        m_cpu.GS.validateForOuterLevel();
    }
    
    
    
    @Override
    public String toString() {
        
        if(m_stackDisplacement != null)
            return String.format("retf %s", m_stackDisplacement.toString());
        else
            return "retf";
    }
}
