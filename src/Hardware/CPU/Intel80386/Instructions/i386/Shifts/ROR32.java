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
package Hardware.CPU.Intel80386.Instructions.i386.Shifts;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class ROR32 extends Instruction {

    private final Operand m_destination;
    private final Operand m_count;
    
    public ROR32(Intel80386 cpu,
                 Operand destination,
                 Operand count) {
        
        super(cpu);
        
        m_destination = destination;
        m_count = count;
    }
    
    @Override
    public void run() {
        
        int dest = m_destination.getValue();
        int count = m_count.getValue() & 0x1f;
        
        if(count > 0) {
            
            // Calculate result
            int result = (dest >>> count) | (dest << (32 - count));
            
            // Calculate flags
            m_cpu.FLAGS.CF = (result & 0x80000000) != 0;
            m_cpu.FLAGS.OF = ((dest ^ result) & 0x80000000) != 0;
        
            // Store result
            m_destination.setValue(result);
        }
    }
    
    @Override
    public String toString() {
        
        return String.format("ror %s, %s", m_destination.toString(), m_count.toString());
    }
}
