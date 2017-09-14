/*
 * Copyright (C) 2017 homer
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
package Hardware.CPU.Intel80386.Instructions.FPU.Misc;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Memory.OperandMemory;



public final class FSTCW extends Instruction {

    private final OperandMemory m_destination;

    public FSTCW(Intel80386 cpu,
                 OperandMemory destination) {
        
        super(cpu);
        
        m_destination = destination;
    }

    @Override
    public void run() {
        
        m_destination.setValue(m_cpu.FPU.getControl());
    }
    
    @Override
    public String toString() {
        
        return String.format("fstcw %s", m_destination.toString());
    }
}
