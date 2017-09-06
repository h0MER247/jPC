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
import Hardware.CPU.Intel80386.Register.TaskRegister.TaskRegister;



public final class JMP_FAR extends Instruction {

    private final Operand m_ip;
    private final Operand m_cs;
    private final boolean m_is32;

    public JMP_FAR(Intel80386 cpu,
                   Operand ip,
                   Operand cs,
                   boolean is32) {
        
        super(cpu);
        
        m_ip = ip;
        m_cs = cs;
        m_is32 = is32;
    }

    @Override
    public void run() {
        
        int cs = m_cs.getValue() & 0xffff;
        int ip = m_ip.getValue();
        
        if(!m_is32)
            ip &= 0xffff;
        
        if(m_cpu.CR.isInRealMode())
            JMP_FAR_REAL_MODE(cs, ip);
        else if(m_cpu.FLAGS.VM)
            JMP_FAR_VIRTUAL_MODE(cs, ip);
        else
            JMP_FAR_PROTECTED_MODE(cs, ip);
    }
    
    private void JMP_FAR_REAL_MODE(int cs, int ip) {
        
        m_cpu.CS.loadRealMode(cs);
        m_cpu.EIP.setValue(ip);
    }
    
    private void JMP_FAR_VIRTUAL_MODE(int cs, int ip) {
        
        m_cpu.CS.loadVirtualMode(cs);
        m_cpu.EIP.setValue(ip);
    }
    
    private void JMP_FAR_PROTECTED_MODE(int cs, int ip) {
        
        // Destination selector must be non null
        if(m_cpu.isNullSelector(cs))
            throw CPUException.getGeneralProtectionFault(0);

        // Destination selector index has to be within the limits of its descriptor table
        Descriptor descCS;
        if((descCS = m_cpu.getDescriptor(cs)) == null)
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);

        // Segment must be present
        if(!descCS.isPresent())
            throw CPUException.getSegmentNotPresent(cs & 0xfffc);
        
        // Instruction pointer must be within code-segment limit
        if(descCS.isOutsideLimit(ip, 1))
            throw CPUException.getGeneralProtectionFault(0);
        
        
        // Perform the far jump
        int CPL = m_cpu.getCPL();
        
        if(descCS.getTypeInfo().isConformingCodeSegment())
            JMP_FAR_CONFORMING_CS(cs, ip, descCS, CPL);
        else if(descCS.getTypeInfo().isNonConformingCodeSegment())
            JMP_FAR_NON_CONFORMING_CS(cs, ip, descCS, CPL);
        else if(descCS.getTypeInfo().isCallGate())
            JMP_FAR_CALL_GATE(cs, ip, descCS, CPL);
        else if(descCS.getTypeInfo().isTaskGate())
            JMP_FAR_TASK_GATE(cs, ip, descCS, CPL);
        else if(descCS.getTypeInfo().isTaskStateSegment())
            JMP_FAR_TASK_STATE_SEGMENT(cs, descCS, CPL);
        else
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
    }
    
    private void JMP_FAR_CONFORMING_CS(int cs, int ip, Descriptor descCS, int CPL) {
    
        // Descriptor DPL must be <= CPL
        if(descCS.getDPL() > CPL)
            throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        
        // Load CS:IP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);
    }
    
    private void JMP_FAR_NON_CONFORMING_CS(int cs, int ip, Descriptor descCS, int CPL) {
        
        // RPL of destination selector must be <= CPL
        if(m_cpu.getSelectorsRPL(cs) > CPL)
           throw CPUException.getGeneralProtectionFault(cs & 0xfffc);

        // Descriptor DPL must be equal to CPL
        if(descCS.getDPL() != CPL)
           throw CPUException.getGeneralProtectionFault(cs & 0xfffc);
        
        // Load CS:IP
        m_cpu.CS.loadProtectedMode(cs, descCS);
        m_cpu.EIP.setValue(ip);
    }
    
    private void JMP_FAR_CALL_GATE(int cs, int ip, Descriptor descCS, int CPL) {
        
        throw new UnsupportedOperationException("Implement me");
    }
    
    private void JMP_FAR_TASK_GATE(int cs, int ip, Descriptor descCS, int CPL) {
        
        throw new UnsupportedOperationException("Implement me");
    }
    
    private void JMP_FAR_TASK_STATE_SEGMENT(int tssSelector, Descriptor descTSS, int CPL) {
        
        // Descriptor DPL must be >= CPL, as well as RPL of the tss selector
        if(descTSS.getDPL() < CPL ||
           descTSS.getDPL() < m_cpu.getSelectorsRPL(tssSelector)) {
            
            throw CPUException.getGeneralProtectionFault(tssSelector & 0xfffc);
        }
        
        // Task must be available
        if(!descTSS.getTypeInfo().isAvailableTaskStateSegment())
            throw CPUException.getGeneralProtectionFault(tssSelector & 0xfffc);
        
        // Segment must be present
        if(!descTSS.isPresent())
            throw CPUException.getSegmentNotPresent(tssSelector & 0xfffc);
        
        m_cpu.TR.switchToTask(tssSelector, descTSS, TaskRegister.TASKSWITCH_JMP);
    }
    
    @Override
    public String toString() {

        return String.format("jmp far %s:%s", m_cs.toString(), m_ip.toString());
    }
}
