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



public final class MOVSB extends Instruction {

    private final Segment m_sourceSegment;
    
    public MOVSB(Intel8086 cpu,
                 Segment sourceSegment,
                 int cycles) {
        
        super(cpu, cycles);
        
        m_sourceSegment = sourceSegment;
    }
    
    @Override
    public void run() {
        
        int srcIndex = m_cpu.SI.getValue();
        int destIndex = m_cpu.DI.getValue();
        
        // Move [(E)SI] to ES:[(E)DI]
        m_cpu.writeMEM8(m_cpu.ES.getBase(),
                        destIndex,
                        m_cpu.readMEM8(m_sourceSegment.getBase(), srcIndex));
        
        // Update index
        if(m_cpu.FLAGS.DF) {
            
            m_cpu.SI.setValue(srcIndex - 1);
            m_cpu.DI.setValue(destIndex - 1);
        }
        else {
            
            m_cpu.SI.setValue(srcIndex + 1);
            m_cpu.DI.setValue(destIndex + 1);
        }
        
        m_cpu.updateClock(getCycles());
    }
    
    @Override
    public String toString() {
        
        return String.format("%s: movsb", m_sourceSegment.toString());
    }
}
