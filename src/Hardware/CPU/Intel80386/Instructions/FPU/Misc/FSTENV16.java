/*
 * Copyright (C) 2017 homer
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
package Hardware.CPU.Intel80386.Instructions.FPU.Misc;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Pointer.Pointer;
import Hardware.CPU.Intel80386.Register.Segments.Segment;



public final class FSTENV16 extends Instruction {
    
    private final Segment m_segment;
    private final Pointer m_offset;
    
    public FSTENV16(Intel80386 cpu,
                    Segment segment,
                    Pointer offset) {
        
        super(cpu);
        
        m_segment = segment;
        m_offset = offset;
    }

    @Override
    public void run() {
        
        int addr = m_offset.getAddress();
        
        m_cpu.writeMEM16(m_segment, addr, m_cpu.FPU.getControl());
        m_cpu.writeMEM16(m_segment, addr + 2, m_cpu.FPU.getStatus());
        m_cpu.writeMEM16(m_segment, addr + 4, m_cpu.FPU.getTags());
    }
    
    @Override
    public String toString() {
        
        return String.format("fstenv ptr [%s:%s]", m_segment.toString(), m_offset.toString());
    }
}
