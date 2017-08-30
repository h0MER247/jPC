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
package Hardware.CPU.Intel80386.Instructions.i386.Arithmetic;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;
import static Utility.SignExtension.signExtend16To32;



public final class IMUL16_2 extends Instruction {

    private final Operand m_destination;
    private final Operand m_source1;
    private final Operand m_source2;
    
    public IMUL16_2(Intel80386 cpu,
                    Operand destination,
                    Operand source1,
                    Operand source2) {
        
        super(cpu);
        
        m_destination = destination;
        m_source1 = source1;
        m_source2 = source2;
    }
    
    @Override
    public void run() {
        
        int result = signExtend16To32(m_source1.getValue()) * signExtend16To32(m_source2.getValue());
        
        m_cpu.FLAGS.CF = ((result & 0xffff8000) != 0xffff8000) &&
                         ((result & 0xffff8000) != 0x00000000);
        m_cpu.FLAGS.OF = m_cpu.FLAGS.CF;
        
        m_destination.setValue(result);
    }
    
    @Override
    public String toString() {
        
        return String.format("imul %s, %s, %s", m_destination.toString(), m_source1.toString(), m_source2.toString());
    }
}
