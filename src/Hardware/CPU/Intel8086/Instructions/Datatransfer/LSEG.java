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
package Hardware.CPU.Intel8086.Instructions.Datatransfer;

import Hardware.CPU.Intel8086.Intel8086;
import Hardware.CPU.Intel8086.Instructions.Instruction;
import Hardware.CPU.Intel8086.Operands.Operand;



public final class LSEG extends Instruction {
    
    private final Operand m_segment;
    private final Operand m_register;
    private final Operand m_registerValue;
    private final Operand m_segmentValue;
    
    public LSEG(Intel8086 cpu,
                Operand segment,
                Operand register,
                Operand registerValue,
                Operand segmentValue,
                int cycles) {
        
        super(cpu, cycles);
        
        m_segment = segment;
        m_register = register;
        m_registerValue = registerValue;
        m_segmentValue = segmentValue;
    }

    @Override
    public void run() {
        
        int reg = m_registerValue.getValue();
        int seg = m_segmentValue.getValue();
        
        m_register.setValue(reg);
        m_segment.setValue(seg);
        
        m_cpu.updateClock(getCycles());
    }
    
    @Override
    public String toString() {
    
        return String.format("l%s %s, %s", m_segment.toString(), m_register.toString(), m_registerValue.toString());
    }
}
