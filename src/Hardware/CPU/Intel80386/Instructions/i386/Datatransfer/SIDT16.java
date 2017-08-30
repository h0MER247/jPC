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
package Hardware.CPU.Intel80386.Instructions.i386.Datatransfer;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class SIDT16 extends Instruction {
    
    private final Operand m_destinationLimit;
    private final Operand m_destinationBase;
    
    public SIDT16(Intel80386 cpu,
                  Operand destinationLimit,
                  Operand destinationBase) {

        super(cpu);
        
        m_destinationLimit = destinationLimit;
        m_destinationBase = destinationBase;
    }

    @Override
    public void run() {
        
        m_destinationLimit.setValue(m_cpu.IDT.getLimit());
        m_destinationBase.setValue(m_cpu.IDT.getBase() & 0x00ffffff);
    }
    
    @Override
    public String toString() {
        
        return String.format("sidt %s", m_destinationLimit.toString());
    }
}
