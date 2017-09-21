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
package Hardware.CPU.Intel80386.Instructions.FPU.Arithmetic;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.FPU.OperandFPU;



public final class FADDP extends Instruction {

    private final OperandFPU m_destination;
    private final OperandFPU m_arg1;
    private final OperandFPU m_arg2;
    
    public FADDP(Intel80386 cpu,
                 OperandFPU destination,
                 OperandFPU arg1,
                 OperandFPU arg2) {
        
        super(cpu);
        
        m_destination = destination;
        m_arg1 = arg1;
        m_arg2 = arg2;
    }

    @Override
    public void run() {
        
        m_destination.setValue(m_arg1.getValue() + m_arg2.getValue());
        m_cpu.FPU.popStack();
    }
    
    @Override
    public String toString() {
        
        return String.format("faddp %s, %s, %s", m_destination.toString(), m_arg1.toString(), m_arg2.toString());
    }
}
