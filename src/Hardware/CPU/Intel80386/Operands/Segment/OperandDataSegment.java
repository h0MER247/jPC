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
import Hardware.CPU.Intel80386.Register.Segments.*;



public final class OperandDataSegment implements Operand {

    private final Intel80386 m_cpu;
    private final Segment m_seg;

    public OperandDataSegment(Intel80386 cpu,
                              Segment seg) {
        
        m_cpu = cpu;
        m_seg = seg;
    }
    
    @Override
    public int getValue() {

        return m_seg.getSelector();
    }

    @Override
    public void setValue(int value) {
        
        value &= 0xffff;
        
        //
        // Real Mode
        //
        if(m_cpu.CR.isInRealMode()) {
            
            m_seg.loadRealMode(value);
        }
        
        //
        // Virtual 8086 Mode
        //
        else if(m_cpu.FLAGS.VM) {
            
            m_seg.loadVirtualMode(value);
        }
        
        //
        // Protected Mode
        //
        else {
            
            if(!m_cpu.isNullSelector(value)) {
            
                // Selector index must be within its descriptor table limits
                Descriptor desc;
                if((desc = m_cpu.getDescriptor(value)) == null)
                    throw CPUException.getGeneralProtectionFault(value & 0xfffc);
                
                // Segment has to be a data or readable code segment
                if(!(desc.getTypeInfo().isDataSegment() ||
                     desc.getTypeInfo().isReadableCodeSegment())) {
                    
                    throw CPUException.getGeneralProtectionFault(value & 0xfffc);
                }
                
                // The selectors RPL and the CPL must both be less than or equal to DPL
                // if we try to load a data segment or non conforming code segment
                if(desc.getTypeInfo().isDataSegment() ||
                   desc.getTypeInfo().isNonConformingCodeSegment()) {

                    if(desc.getDPL() < m_cpu.getSelectorsRPL(value) ||
                       desc.getDPL() < m_cpu.getCPL()) {
                        
                        throw CPUException.getGeneralProtectionFault(value & 0xfffc);
                    }
                }

                // Segment must be present
                if(!desc.isPresent())
                    throw CPUException.getSegmentNotPresent(value & 0xfffc);
                
                // Load segment
                m_seg.loadProtectedMode(value, desc);
            }
            else {
                
                m_seg.setSelector(value);
                m_seg.setValid(false);
            }
        }
    }

    @Override
    public String toString() {

        return m_seg.toString();
    }
}
