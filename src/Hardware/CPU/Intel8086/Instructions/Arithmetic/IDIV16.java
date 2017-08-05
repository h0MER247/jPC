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
import Hardware.CPU.Intel8086.Exceptions.DivisionException;
import static Utility.SignExtension.signExtend16To32;



public final class IDIV16 extends Instruction {
    
    private final Operand m_source;
    
    public IDIV16(Intel8086 cpu,
                  Operand source,
                  int cycles) {
        
        super(cpu, cycles);
        
        m_source = source;
    }
    
    @Override
    public void run() {
        
        int src = m_source.getValue();
        
        if(src == 0)
            throw new DivisionException();
        
        src = signExtend16To32(src);
        
        int dividend = (m_cpu.DX.getValue() << 16) | m_cpu.AX.getValue();
        int quotient = dividend / src;
        int remainder = dividend % src;
        
        if(quotient > 0x7fff || quotient < -0x7fff)
            throw new DivisionException();
        
        m_cpu.DX.setValue(remainder);
        m_cpu.AX.setValue(quotient);
        
        m_cpu.updateClock(getCycles());
    }
    
    @Override
    public String toString() {
        
        return String.format("idiv %s, dx:ax", m_source.toString());
    }
}
