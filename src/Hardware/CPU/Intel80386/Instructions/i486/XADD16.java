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
package Hardware.CPU.Intel80386.Instructions.i486;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class XADD16 extends Instruction {

    private final Operand m_destination;
    private final Operand m_source;
    
    public XADD16(Intel80386 cpu,
                  Operand destination,
                  Operand source) {
        
        super(cpu);
        
        m_destination = destination;
        m_source = source;
    }

    @Override
    public void run() {
        
        // Calculate result of the addition
        int dest = m_destination.getValue();
        int src = m_source.getValue();
        int result = (dest + src) & 0xffff;
        
        m_cpu.FLAGS.setSZP16(result);
        m_cpu.FLAGS.CF = Integer.compareUnsigned(result, dest) < 0;
        m_cpu.FLAGS.OF = (((dest ^ src ^ 0x8000) & (result ^ src)) & 0x8000) != 0;
        m_cpu.FLAGS.AF = (((dest ^ src) ^ result) & 0x10) != 0;
        
        // Exchange the destination with the source operand and store the
        // sum of the addition into the destination
        m_source.setValue(dest);
        m_destination.setValue(result);
    }
    
    @Override
    public String toString() {
        
        return String.format("xadd %s, %s", m_destination.toString(), m_source.toString());
    }
}
