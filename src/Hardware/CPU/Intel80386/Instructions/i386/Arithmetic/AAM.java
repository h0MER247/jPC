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



public final class AAM extends Instruction {
    
    private final Operand m_immediate;
    
    public AAM(Intel80386 cpu,
               Operand immediate) {
        
        super(cpu);
        
        m_immediate = immediate;
    }

    @Override
    public void run() {
        
        int imm = m_immediate.getValue();
        
        if(imm == 0)
            throw CPUException.getDivideError();
        
        m_cpu.AH.setValue(m_cpu.AL.getValue() / imm);
        m_cpu.AL.setValue(m_cpu.AL.getValue() % imm);
        
        m_cpu.FLAGS.setSZP16(m_cpu.AX.getValue());
    }
}
