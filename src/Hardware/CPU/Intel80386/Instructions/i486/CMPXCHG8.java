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



public final class CMPXCHG8 extends Instruction {

    private final Operand m_destination;
    private final Operand m_source;
    
    public CMPXCHG8(Intel80386 cpu,
                    Operand destination,
                    Operand source) {
        
        super(cpu);
        
        m_destination = destination;
        m_source = source;
    }

    @Override
    public void run() {
        
        // Compare AL with destination
        int arg1 = m_cpu.AL.getValue();
        int arg2 = m_destination.getValue();
        int result = arg1 - arg2;
        
        m_cpu.FLAGS.setSZP8(result);
        m_cpu.FLAGS.CF = Integer.compareUnsigned(arg1, arg2) < 0;
        m_cpu.FLAGS.OF = (((arg1 ^ arg2) & (arg1 ^ result)) & 0x80) != 0;
        m_cpu.FLAGS.AF = (((arg1 ^ arg2) ^ result) & 0x10) != 0;
        
        // Exchange
        if(arg1 == arg2)
            m_destination.setValue(m_source.getValue());
        else
            m_cpu.AL.setValue(m_destination.getValue());
    }
    
    @Override
    public String toString() {
        
        return String.format("cmpxchg %s, %s", m_destination.toString(), m_source.toString());
    }
}
