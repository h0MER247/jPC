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
package Hardware.CPU.Intel8086.Instructions.String;

import Hardware.CPU.Intel8086.Intel8086;
import Hardware.CPU.Intel8086.Instructions.Instruction;
import Hardware.CPU.Intel8086.Segments.Segment;



public final class CMPSW extends Instruction {
    
    private final Segment m_defaultSegment;
    
    public CMPSW(Intel8086 cpu,
                 Segment defaultSegment,
                 int cycles) {
        
        super(cpu, cycles);
        
        m_defaultSegment = defaultSegment;
    }

    @Override
    public void run() {
        
        int srcIndex = m_cpu.SI.getValue();
        int destIndex = m_cpu.DI.getValue();
        
        // Compare [SI] with ES:[DI]
        int arg1 = m_cpu.readMEM16(m_defaultSegment.getBase(), srcIndex);
        int arg2 = m_cpu.readMEM16(m_cpu.ES.getBase(), destIndex);
        int result = (arg1 - arg2) & 0xffff;
        
        m_cpu.FLAGS.setSZP_16(result);
        m_cpu.FLAGS.CF = Integer.compareUnsigned(arg1, arg2) < 0;
        m_cpu.FLAGS.OF = (((arg1 ^ arg2) & (arg1 ^ result)) & 0x8000) != 0;
        m_cpu.FLAGS.AF = (((arg1 ^ arg2) ^ result) & 0x10) != 0;
        
        // Update index
        if(m_cpu.FLAGS.DF) {
            
            m_cpu.SI.setValue(srcIndex - 2);
            m_cpu.DI.setValue(destIndex - 2);
        }
        else {
            
            m_cpu.SI.setValue(srcIndex + 2);
            m_cpu.DI.setValue(destIndex + 2);
        }
        
        m_cpu.updateClock(getCycles());
    }
    
    @Override
    public String toString() {
        
        return "cmpsw";
    }
}
