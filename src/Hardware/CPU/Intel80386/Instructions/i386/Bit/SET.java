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

import Hardware.CPU.Intel80386.Condition.Condition;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class SET extends Instruction {
    
    private final Operand m_destination;
    private final Condition m_condition;
    
    public SET(Intel80386 cpu,
               Operand destination,
               Condition condition) {
        
        super(cpu);
        
        m_destination = destination;
        m_condition = condition;
    }

    @Override
    public void run() {
        
        m_destination.setValue(m_condition.isTrue() ? 1 : 0);
    }
    
    @Override
    public String toString() {
        
        return String.format("set%s %s", m_condition.toString(), m_destination.toString());
    }
}
