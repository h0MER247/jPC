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
package Hardware.CPU.Intel8086.Instructions.Programflow;

import Hardware.CPU.Intel8086.Intel8086;
import Hardware.CPU.Intel8086.Instructions.Instruction;



public final class IRET extends Instruction {

    public IRET(Intel8086 cpu,
                int cycles) {
        
        super(cpu, cycles);
    }

    @Override
    public void run() {
        
        int ip = m_cpu.popStack();
        int cs = m_cpu.popStack();
        int flags = m_cpu.popStack();
        
        m_cpu.IP.setValue(ip);
        m_cpu.CS.setSelector(cs);
        m_cpu.FLAGS.setValue(flags);
        
        m_cpu.updateClock(getCycles());
    }
        
        
    @Override
    public String toString() {
        
        return "iret";
    }
}
