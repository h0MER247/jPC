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

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;
import static Utility.SignExtension.signExtend16To32;
import static Utility.SignExtension.signExtend8To32;



public final class IDIV8 extends Instruction {
    
    private final Operand m_source;
    
    public IDIV8(Intel80386 cpu,
                 Operand source) {
        
        super(cpu);
        
        m_source = source;
    }
    
    @Override
    public void run() {
        
        int src = m_source.getValue();
        if(src == 0)
            throw CPUException.getDivideError();
        
        src = signExtend8To32(src);
        
        int dividend = signExtend16To32(m_cpu.AX.getValue());
        int quotient = dividend / src;
        int remainder = dividend % src;
        
        if((quotient > 0x7f) || (quotient < -0x7f))
            throw CPUException.getDivideError();
        
        m_cpu.AH.setValue(remainder);
        m_cpu.AL.setValue(quotient);
    }
    
    @Override
    public String toString() {
        
        return String.format("idiv %s, ax", m_source.toString());
    }
}
