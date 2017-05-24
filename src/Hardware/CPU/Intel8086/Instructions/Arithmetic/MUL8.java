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
package Hardware.CPU.Intel8086.Instructions.Arithmetic;

import Hardware.CPU.Intel8086.Intel8086;
import Hardware.CPU.Intel8086.Instructions.Instruction;
import Hardware.CPU.Intel8086.Operands.Operand;



public final class MUL8 extends Instruction {

    private final Operand m_source;
    
    public MUL8(Intel8086 cpu,
                Operand source,
                int cycles) {
        
        super(cpu, cycles);
        
        m_source = source;
    }
    
    @Override
    public void run() {
        
        int result = (m_source.getValue() * m_cpu.AL.getValue()) & 0xffff;
        
        m_cpu.FLAGS.setSZP_8(result);
        m_cpu.FLAGS.CF = (result & 0xff00) != 0;
        m_cpu.FLAGS.OF = m_cpu.FLAGS.CF;
        m_cpu.FLAGS.ZF = result == 0;
        
        m_cpu.AH.setValue(result >>> 8);
        m_cpu.AL.setValue(result);
        
        m_cpu.updateClock(getCycles());
    }
    
    @Override
    public String toString() {
        
        return String.format("mul %s, al", m_source.toString());
    }
}
