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



public final class STOSW extends Instruction {
    
    private final Register m_destIndex;
    
    public STOSW(Intel80386 cpu,
                 Register destIndex) {
        
        super(cpu);
        
        m_destIndex = destIndex;
    }

    @Override
    public void run() {
        
        int destIndex = m_destIndex.getValue();
        
        // Write AX to ES:[DI]
        m_cpu.writeMEM16(m_cpu.ES, destIndex, m_cpu.AX.getValue());
        
        // Update index
        if(m_cpu.FLAGS.DF)
            m_destIndex.setValue(destIndex - 2);
        else
            m_destIndex.setValue(destIndex + 2);
    }
    
    @Override
    public String toString() {
        
        return "stosw";
    }
}
