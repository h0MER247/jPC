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
package Hardware.CPU.Intel8086.Instructions.Shifts;

import Hardware.CPU.Intel8086.Intel8086;
import Hardware.CPU.Intel8086.Instructions.Instruction;
import Hardware.CPU.Intel8086.Operands.Operand;



public final class ROL16 extends Instruction {

    private final Operand m_destination;
    private final Operand m_count;
    
    public ROL16(Intel8086 cpu,
                 Operand destination,
                 Operand count,
                 int cycles) {
        
        super(cpu, cycles);
        
        m_destination = destination;
        m_count = count;
    }
    
    @Override
    public void run() {
        
        int dest = m_destination.getValue();
        int count = m_count.getValue();
        int result = ((dest << (count & 0x0f)) | (dest >>> (16 - (count & 0x0f))));
        
        if(count == 1)
            m_cpu.FLAGS.OF = ((dest ^ result) & 0x8000) != 0;
        
        m_cpu.FLAGS.CF = (result & 0x01) != 0;
        
        m_destination.setValue(result);
        
        m_cpu.updateClock(getCycles() + (count << 2));
    }
    
    @Override
    public String toString() {
        
        return String.format("rol %s, %s", m_destination.toString(), m_count.toString());
    }
}
