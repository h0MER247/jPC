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
package Hardware.CPU.Intel80386.Codeblock;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Scheduler.Scheduler;



public final class CodeBlock {
    
    /* ----------------------------------------------------- *
     * Codeblock information                                 *
     * ----------------------------------------------------- */
    private final int m_cs;
    private final int m_ip;
    private final int m_physicalAddressBegin;
    private final int m_physicalAddressEnd;
    private final boolean m_isCode32;
    private final boolean m_isStack32;
    private final boolean m_isCacheable;
    private final int m_cycles;
    private boolean m_isRunning;
    
    /* ----------------------------------------------------- *
     * Instructions of this codeblock                        *
     * ----------------------------------------------------- */
    private final Instruction[] m_instructions;
    
    /* ----------------------------------------------------- *
     * Reference to the Intel 80386 cpu                      *
     * ----------------------------------------------------- */
    private final Intel80386 m_cpu;
    
    
    
    public CodeBlock(Intel80386 cpu,
                     int cs,
                     int ip,
                     int physicalAddressBegin,
                     int physicalAddressEnd,
                     boolean isCode32,
                     boolean isStack32,
                     boolean isCacheable,
                     Instruction[] instructions) {
        
        m_cpu = cpu;
        m_cs = cs;
        m_ip = ip;
        m_physicalAddressBegin = physicalAddressBegin;
        m_physicalAddressEnd = physicalAddressEnd;
        m_isCode32 = isCode32;
        m_isStack32 = isStack32;
        m_isCacheable = isCacheable;
        m_instructions = instructions;
        
        m_cycles = Scheduler.toFixedPoint(instructions.length * 6);
    }
    
    
    
    public boolean isMatching(int address, int cs, int ip, boolean isCode32, boolean isStack32) {
        
        return m_physicalAddressBegin == address &&
               m_cs == cs &&
               m_ip == ip &&
               m_isCode32 == isCode32 &&
               m_isStack32 == isStack32;
    }
    
    public boolean isCoveringPhysicalAddress(int start, int end) {
        
        return Integer.compareUnsigned(start, m_physicalAddressEnd) <= 0 &&
               Integer.compareUnsigned(end, m_physicalAddressBegin) >= 0;
    }
    
    public boolean isCacheable() {
        
        return m_isCacheable;
    }
    
    public void invalidate() {
        
        m_isRunning = false;
    }
    
    
    
    public void run() {
        
        int i = 0;
        try {
            
            m_isRunning = true;
            while(i < m_instructions.length && m_isRunning) {
                
                m_cpu.EIP.setValue(m_instructions[i].getNextEIP());
                m_instructions[i].run();
                
                i++;
            }
            m_cpu.updateClock(m_cycles);
        }
        catch(CPUException ex) {
            
            if(ex.isPointingToFaultedInstruction())
                m_cpu.EIP.setValue(m_instructions[i].getCurrentEIP());
            
            throw ex;
        }
    }
    
    
    
    @Override
    public String toString() {
        
        String res = "";
        for(Instruction i : m_instructions)
            res += String.format("%04x:%08x %s\n", m_cs, i.getCurrentEIP(), i.toString());
        
        return res;
    }
}
