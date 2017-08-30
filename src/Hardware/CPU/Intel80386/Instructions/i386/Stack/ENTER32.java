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
package Hardware.CPU.Intel80386.Instructions.i386.Stack;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class ENTER32 extends Instruction {
    
    private final Operand m_stackDisplacement;
    private final Operand m_level;
    
    public ENTER32(Intel80386 cpu,
                   Operand stackDisplacement,
                   Operand level) {
        
        super(cpu);
        
        m_stackDisplacement = stackDisplacement;
        m_level = level;
    }

    @Override
    public void run() {
        
        int oldESP = m_cpu.ESP.getValue();
        try {
            
            // Push base pointer
            m_cpu.pushStack32(m_cpu.EBP.getValue());
            
            // Build stack frame
            int framePtr = m_cpu.ESP.getValue();
            int level = m_level.getValue() & 0x1f;
            if(level > 0) {
                
                int ebp = m_cpu.EBP.getValue();
                while(--level > 0) {
                    
                    ebp -= 4;
                    m_cpu.pushStack32(m_cpu.readMEM32(m_cpu.SS, ebp));
                }
                m_cpu.pushStack32(framePtr);
            }
            
            // Update base pointer
            m_cpu.EBP.setValue(framePtr);
            
            // Update stack pointer
            if(m_stackDisplacement != null) {
                
                if(m_cpu.SS.isSize32())
                    m_cpu.ESP.setValue(m_cpu.ESP.getValue() - m_stackDisplacement.getValue());
                else
                    m_cpu.SP.setValue(m_cpu.SP.getValue() - m_stackDisplacement.getValue());
            }
        }
        catch(CPUException ex) {
            
            m_cpu.ESP.setValue(oldESP);
            
            throw ex;
        }
    }
    
    @Override
    public String toString() {
        
        return String.format("enter %s, %s", m_stackDisplacement.toString(), m_level.toString());
    }
}
