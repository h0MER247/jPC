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
import static Utility.SignExtension.signExtend32To64;



public final class IMUL32 extends Instruction {

    private final Operand m_source;
    
    public IMUL32(Intel80386 cpu,
                  Operand source) {
        
        super(cpu);
        
        m_source = source;
    }
    
    @Override
    public void run() {
        
        long result = signExtend32To64(m_source.getValue()) * signExtend32To64(m_cpu.EAX.getValue());
        
        m_cpu.FLAGS.CF = ((result & 0xffffffff80000000l) != 0xffffffff80000000l) &&
                         ((result & 0xffffffff80000000l) != 0x0000000000000000l);
        m_cpu.FLAGS.OF = m_cpu.FLAGS.CF;
        
        m_cpu.EDX.setValue((int)(result >>> 32));
        m_cpu.EAX.setValue((int)(result & 0xffffffffl));
    }
    
    @Override
    public String toString() {
        
        return String.format("imul %s, eax", m_source.toString());
    }
}
