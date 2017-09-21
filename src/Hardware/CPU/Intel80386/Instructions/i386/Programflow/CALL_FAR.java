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



public final class CALL_FAR extends Instruction {

    private final Operand m_cs;
    private final Operand m_eip;
    private final boolean m_is32;
    
    public CALL_FAR(Intel80386 cpu,
                    Operand eip,
                    Operand cs,
                    boolean is32) {
        
        super(cpu);
        
        m_cs = cs;
        m_eip = eip;
        m_is32 = is32;
    }
    
    @Override
    public void run() {
        
        int oldESP = m_cpu.ESP.getValue();
        try {
        
            if(m_cpu.CR.isInRealMode())
                CALL_FAR_REALMODE();
            else if(m_cpu.FLAGS.VM)
                CALL_FAR_VIRTUALMODE();
            else
                CALL_FAR_PROTECTEDMODE();
        }
        catch(CPUException ex) {
            
            m_cpu.ESP.setValue(oldESP);
            
            throw ex;
        }
    }
    
    private void CALL_FAR_REALMODE()  {
        
        // Get new CS:EIP
        int cs = m_cs.getValue() & 0xffff;
        int ip = m_eip.getValue();
        
        // Push return address
        if(m_is32) {

            m_cpu.pushStack32(m_cpu.CS.getSelector());
            m_cpu.pushStack32(m_cpu.EIP.getValue());
        }
        else {

            m_cpu.pushStack16(m_cpu.CS.getSelector());
            m_cpu.pushStack16(m_cpu.EIP.getValue());
            
            ip &= 0xffff;
        }

        // Load CS:EIP
        m_cpu.CS.loadRealMode(cs);
        m_cpu.EIP.setValue(ip);
    }
    
    private void CALL_FAR_VIRTUALMODE() {
        
        // Get new CS:EIP
        int cs = m_cs.getValue() & 0xffff;
        int ip = m_eip.getValue();
        
        // Push return address
        if(m_is32) {

            m_cpu.pushStack32(m_cpu.CS.getSelector());
            m_cpu.pushStack32(m_cpu.EIP.getValue());
        }
        else {

            m_cpu.pushStack16(m_cpu.CS.getSelector());
            m_cpu.pushStack16(m_cpu.EIP.getValue());
            
            ip &= 0xffff;
        }
        
        // Load CS:EIP
        m_cpu.CS.loadVirtualMode(cs);
        m_cpu.EIP.setValue(ip);
    }
    
    private void CALL_FAR_PROTECTEDMODE() {
        
        // Get new CS:EIP
        int cs = m_cs.getValue() & 0xffff;
        int ip = m_eip.getValue();
        
        if(!m_is32) ip &= 0xffff;
        
        
        // New CS selector must be not null
        if(m_cpu.isNullSelector(cs))
            throw CPUException.getGeneralProtectionFault(0);

        // Check that new CS selector index is within its descriptor table limits
        Descriptor descCS;
        if((descCS = m_cpu.getDescriptor(cs)) == null)
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);

        // Segment must be present
        if(!descCS.isPresent())
            throw CPUException.getSegmentNotPresent(cs & 0xfffc);
        
        
        // Perform far call
        int CPL = m_cpu.getCPL();
        if(descCS.getTypeInfo().isConformingCodeSegment())
            CALL_FAR_CONFORMING_CS(cs, ip, descCS, CPL);
        else if(descCS.getTypeInfo().isNonConformingCodeSegment())
            CALL_FAR_NON_CONFORMING_CS(cs, ip, descCS, CPL);
        else if(descCS.getTypeInfo().isCallGate())
            CALL_FAR_CALL_GATE(cs, descCS, CPL);
        else if(descCS.getTypeInfo().isTaskGate())
            CALL_FAR_TASK_GATE(cs, ip, descCS, CPL);
        else if(descCS.getTypeInfo().isTaskStateSegment())
            CALL_FAR_TASK_STATE_SEGMENT(cs, ip, descCS, CPL);
        else
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
    }
    
    private void CALL_FAR_CONFORMING_CS(int cs, int ip, Descriptor descCS, int CPL) {
        
        // Instruction pointer must be in code segment limit
        if(descCS.isOutsideLimit(ip, 1))
            throw CPUException.getGeneralProtectionFault(0);
        
        // Descriptor DPL must be <= CPL
        if(descCS.getDPL() > CPL)
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        
        
        // Push return address
        if(m_is32) {

            m_cpu.pushStack32(m_cpu.CS.getSelector());
            m_cpu.pushStack32(m_cpu.EIP.getValue());
        }
        else {

            m_cpu.pushStack16(m_cpu.CS.getSelector());
            m_cpu.pushStack16(m_cpu.EIP.getValue());
        }
        
        
        // Load CS:IP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);
    }
    
    private void CALL_FAR_NON_CONFORMING_CS(int cs, int ip, Descriptor descCS, int CPL) {
        
        // Instruction pointer must be in code segment limit
        if(descCS.isOutsideLimit(ip, 1))
            throw CPUException.getGeneralProtectionFault(0);
        
        // RPL of destination selector must be <= CPL
        if(m_cpu.getSelectorsRPL(cs) > CPL)
           throw CPUException.getGeneralProtectionFault(cs & 0xfffc);

        // Descriptor DPL must be equal to CPL
        if(descCS.getDPL() != CPL)
           throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        
        
        // Push return address
        if(m_is32) {

            m_cpu.pushStack32(m_cpu.CS.getSelector());
            m_cpu.pushStack32(m_cpu.EIP.getValue());
        }
        else {

            m_cpu.pushStack16(m_cpu.CS.getSelector());
            m_cpu.pushStack16(m_cpu.EIP.getValue());
        }
        
        
        // Load CS:EIP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);
    }
    
    private void CALL_FAR_CALL_GATE(int gateSelector, Descriptor descGate, int CPL) {
        
        // Call gate DPL must be >= CPL
        if(descGate.getDPL() < CPL)
            throw CPUException.getGeneralProtectionFault(gateSelector & 0xfffc);
        
        // Call gate DPL must be >= RPL
        if(descGate.getDPL() < m_cpu.getSelectorsRPL(gateSelector))
            throw CPUException.getGeneralProtectionFault(gateSelector & 0xfffc);
        
        
        // Get cs:ip from gate descriptor
        int cs = descGate.getTargetSegment();
        int ip = descGate.getTargetOffset();
        
        
        // CS selector must be non null
        if(m_cpu.isNullSelector(cs))
            throw CPUException.getGeneralProtectionFault(0);
        
        // Make sure that the new CS selector index is within its descriptor table limits
        Descriptor descCS;
        if((descCS = m_cpu.getDescriptor(cs)) == null)
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        
        // Instruction pointer must be in code segment limit
        if(descCS.isOutsideLimit(ip, 1))
            throw CPUException.getGeneralProtectionFault(0);
        
        // Descriptor AR byte must indicate code segment
        if(!descCS.getTypeInfo().isCodeSegment())
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        
        // The descriptors DPL must be <= CPL
        if(descCS.getDPL() > CPL)
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        
        
        // Perform call
        if(descCS.getTypeInfo().isNonConformingCodeSegment() && descCS.getDPL() < CPL)
            CALL_FAR_CALL_GATE_MORE_PRIVILEGE(cs, ip, descGate, descCS);
        else
            CALL_FAR_CALL_GATE_SAME_PRIVILEGE(descCS);
    }
    
    private void CALL_FAR_CALL_GATE_MORE_PRIVILEGE(int cs, int ip, Descriptor descGate, Descriptor descCS) {
        
        // Get stack segment selector for the new privilege level from the task state segment
        int ss = m_cpu.TR.getStackSegment(descCS.getDPL());
        int sp = m_cpu.TR.getStackPointer(descCS.getDPL());
        
        
        // SS Selector must be non null
        if(m_cpu.isNullSelector(ss))
            throw CPUException.getInvalidTSS(0);
        
        // Selectors RPL must be equal to DPL of cs
        if(m_cpu.getSelectorsRPL(ss) != descCS.getDPL())
            throw CPUException.getInvalidTSS(ss & 0xfffc);
        
        // Make sure that the new SS selector index is within its descriptor table limits
        Descriptor descSS;
        if((descSS = m_cpu.getDescriptor(ss)) == null)
            throw CPUException.getInvalidTSS(ss & 0xfffc);
        
        // Stack segment DPL must equal DPL of code segment
        if(descSS.getDPL() != descCS.getDPL())
            throw CPUException.getInvalidTSS(ss & 0xfffc);
        
        // Descriptor must indicate writable data segment
        if(!descSS.getTypeInfo().isWritableDataSegment())
            throw CPUException.getInvalidTSS(ss & 0xfffc);
        
        // Segment must be present
        if(!descSS.isPresent())
            throw CPUException.getStackFault(ss & 0xfffc);

        
        // New stack must have enough room for the long pointer to the current
        // stack and return address as well as the parameters
        boolean is32BitGate = descGate.getTypeInfo().is32BitGate();
        
        int numParams = descGate.getParameterCount();
        int neededSpace;
        if(is32BitGate)
            neededSpace = 8 + (numParams << 1);
        else
            neededSpace = 16 + (numParams << 2);
        
        if(descSS.isOutsideLimit(sp - neededSpace, 1))
            throw CPUException.getStackFault(0);
        
        
        // Store long pointer to the current stack and program address
        int oldSS = m_cpu.SS.getSelector();
        int oldSP = m_cpu.ESP.getValue();
        int oldCS = m_cpu.CS.getSelector();
        int oldIP = m_cpu.EIP.getValue();
        
        if(!m_cpu.SS.isSize32())
            oldSP &= 0xffff;
        if(!m_cpu.CS.isSize32())
            oldIP &= 0xffff;
        
        
        // Read parameters from current stack
        int[] param = new int[numParams];
        for(int i = numParams - 1, j = 0; i >= 0; i--, j++) {
            
            if(is32BitGate)
                param[j] = m_cpu.readMEM32(m_cpu.SS, oldSP + (i * 4));
            else
                param[j] = m_cpu.readMEM16(m_cpu.SS, (oldSP + (i * 2)) & 0xffff);
        }
        
        
        // Load CS:EIP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);
        
        // Load SS:ESP
        m_cpu.SS.loadProtectedMode(ss, descSS);
        if(m_cpu.SS.isSize32())
            m_cpu.ESP.setValue(sp);
        else
            m_cpu.SP.setValue(sp);
        
        
        // Push return information and parameters to the new stack
        if(is32BitGate) {
        
            m_cpu.pushStack32(oldSS);
            m_cpu.pushStack32(oldSP);
            for(int i = 0; i < numParams; i++)
                m_cpu.pushStack32(param[i]);
            m_cpu.pushStack32(oldCS);
            m_cpu.pushStack32(oldIP);
        }
        else {
            
            m_cpu.pushStack16(oldSS);
            m_cpu.pushStack16(oldSP);
            for(int i = 0; i < numParams; i++)
                m_cpu.pushStack16(param[i]);
            m_cpu.pushStack16(oldCS);
            m_cpu.pushStack16(oldIP);
        }
    }
    
    private void CALL_FAR_CALL_GATE_SAME_PRIVILEGE(Descriptor descCS) {
        
        throw new UnsupportedOperationException("Implement me");
    }
    
    private void CALL_FAR_TASK_GATE(int cs, int ip, Descriptor descCS, int CPL) {
        
        throw new UnsupportedOperationException("Implement me");
    }
    
    private void CALL_FAR_TASK_STATE_SEGMENT(int cs, int ip, Descriptor descCS, int CPL) {
        
        throw new UnsupportedOperationException("Implement me");
    }
    
    @Override
    public String toString() {
        
        return String.format("call %s:%s", m_cs.toString(), m_eip.toString());
    }
}
