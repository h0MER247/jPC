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
package Hardware.CPU.Intel80386.Instructions.i386.Misc;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;
import Hardware.CPU.Intel80386.Register.Segments.Descriptor;



public final class LAR extends Instruction {
    
    private final Operand m_destination;
    private final Operand m_selector;
    private final boolean m_is32;
    
    private static final boolean[] VALID_TYPES = {
        
        false, true,  true,  true, // System
        true,  true,  true,  true,
        false, true,  false, true,
        true,  false, true,  true,
        
        true,  true,  true,  true, // Code / Data
        true,  true,  true,  true,
        true,  true,  true,  true,
        true,  true,  true,  true
    };
    
    public LAR(Intel80386 cpu,
               Operand destination,
               Operand selector,
               boolean is32) {
        
        super(cpu);
        
        m_destination = destination;
        m_selector = selector;
        m_is32 = is32;
    }

    @Override
    public void run() {
        
        if(m_cpu.CR.isInRealMode() || m_cpu.FLAGS.VM)
            throw CPUException.getInvalidOpcode();
        
        
        m_cpu.FLAGS.ZF = false;
        
        int selector = m_selector.getValue() & 0xffff;
        if(!m_cpu.isNullSelector(selector)) {
        
            // Check descriptor type
            Descriptor desc;
            if((desc = m_cpu.getDescriptor(selector)) == null)
                return;
            
            if(VALID_TYPES[desc.getType()]) {
                
                // Check privileges (exclude conforming code segments)
                if(!desc.getTypeInfo().isConformingCodeSegment()) {

                    // DPL must be >= CPL and RPL
                    if(desc.getDPL() < m_cpu.getCPL() ||
                       desc.getDPL() < m_cpu.getSelectorsRPL(selector)) {
                        
                        return;
                    }
                }
                
                m_cpu.FLAGS.ZF = true;
                m_destination.setValue(desc.getHighWord() & (m_is32 ? 0x00ffff00 : 0xff00));
            }
        }
    }
    
    @Override
    public String toString() {
        
        return String.format("lar %s, %s", m_destination, m_selector);
    }
}
