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
package Hardware.CPU.Intel80386.Instructions.i386.Bit;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Memory.OperandMemory;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class BTC16_MEM extends Instruction {
    
    private final OperandMemory m_memory;
    private final Operand m_bit;
    
    public BTC16_MEM(Intel80386 cpu,
                     OperandMemory memory,
                     Operand bit) {
        
        super(cpu);
        
        m_memory = memory;
        m_bit = bit;
    }

    @Override
    public void run() {
        
        int bit = m_bit.getValue();
        int bitMask = 1 << (bit & 0x0f);
        int bitAddr = (bit >>> 4) << 1;
        int data = m_memory.getValue(bitAddr);
        
        m_cpu.FLAGS.CF = (data & bitMask) != 0;
        m_memory.setValue(bitAddr, data ^ bitMask);
    }
    
    @Override
    public String toString() {
        
        return String.format("btc %s, %s", m_memory.toString(), m_bit.toString());
    }
}
