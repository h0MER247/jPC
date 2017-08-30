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
package Hardware.CPU.Intel80386.Instructions.i386.String;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Register.General.Register;



public final class SCASD extends Instruction {
    
    private final Register m_destIndex;
    
    public SCASD(Intel80386 cpu,
                 Register destIndex) {
        
        super(cpu);
        
        m_destIndex = destIndex;
    }
    
    @Override
    public void run() {
        
        int destIndex = m_destIndex.getValue();
        
        // Compare EAX with ES:[DI]
        int arg1 = m_cpu.EAX.getValue();
        int arg2 = m_cpu.readMEM32(m_cpu.ES, destIndex);
        int result = arg1 - arg2;
        
        m_cpu.FLAGS.setSZP32(result);
        m_cpu.FLAGS.CF = Integer.compareUnsigned(arg1, arg2) < 0;
        m_cpu.FLAGS.OF = (((arg1 ^ arg2) & (arg1 ^ result)) & 0x80000000) != 0;
        m_cpu.FLAGS.AF = (((arg1 ^ arg2) ^ result) & 0x10) != 0;
        
        // Update index
        if(m_cpu.FLAGS.DF)
            m_destIndex.setValue(destIndex - 4);
        else
            m_destIndex.setValue(destIndex + 4);
    }
    
    @Override
    public String toString() {
        
        return "scasd";
    }
}
