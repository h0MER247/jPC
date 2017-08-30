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
import Hardware.CPU.Intel80386.Operands.Operand;



public final class BTS32_REG extends Instruction {
    
    private final Operand m_register;
    private final Operand m_bit;
    
    public BTS32_REG(Intel80386 cpu,
                     Operand register,
                     Operand bit) {
        
        super(cpu);
        
        m_register = register;
        m_bit = bit;
    }

    @Override
    public void run() {
        
        int register = m_register.getValue();
        int bit = 1 << (m_bit.getValue() & 0x1f);
        
        m_cpu.FLAGS.CF = (register & bit) != 0;
        m_register.setValue(register | bit);
    }
    
    @Override
    public String toString() {
        
        return String.format("bts %s, %s", m_register.toString(), m_bit.toString());
    }
}
