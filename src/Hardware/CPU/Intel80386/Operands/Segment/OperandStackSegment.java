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
package Hardware.CPU.Intel80386.Operands.Segment;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;
import Hardware.CPU.Intel80386.Register.Segments.Descriptor;



public final class OperandStackSegment implements Operand {
    
    private final Intel80386 m_cpu;
    
    public OperandStackSegment(Intel80386 cpu) {
        
        m_cpu = cpu;
    }
    
    @Override
    public int getValue() {

        return m_cpu.SS.getSelector();
    }

    @Override
    public void setValue(int value) {
        
        value &= 0xffff;
        
        //
        // Real Mode
        //
        if(m_cpu.CR.isInRealMode()) {
            
            m_cpu.SS.loadRealMode(value);
        }
        
        //
        // Virtual 8086 Mode
        //
        else if(m_cpu.FLAGS.VM) {
            
            m_cpu.SS.loadVirtualMode(value);
        }
        
        //
        // Protected Mode
        //
        else {
            
            // Selector must be non null
            if(m_cpu.isNullSelector(value))
                throw CPUException.getGeneralProtectionFault(0);

            // Selector index must be within its descriptor table limit
            Descriptor desc;
            if((desc = m_cpu.getDescriptor(value)) == null)
                throw CPUException.getGeneralProtectionFault(value & 0xfffc);

            // Selectors RPL and descriptors DPL must be equal to CPL
            if(m_cpu.getCPL() != m_cpu.getSelectorsRPL(value) ||
               m_cpu.getCPL() != desc.getDPL()) {
                
                throw CPUException.getGeneralProtectionFault(value & 0xfffc);
            }
            
            // Segment must be a writable data segment
            if(!desc.getTypeInfo().isWritableDataSegment())
                throw CPUException.getGeneralProtectionFault(value & 0xfffc);
            
            // Segment must be present
            if(!desc.isPresent())
                throw CPUException.getStackFault(value & 0xfffc);
            
            // Load segment
            m_cpu.SS.loadProtectedMode(value, desc);
        }
    }

    @Override
    public String toString() {

        return m_cpu.SS.toString();
    }
}
