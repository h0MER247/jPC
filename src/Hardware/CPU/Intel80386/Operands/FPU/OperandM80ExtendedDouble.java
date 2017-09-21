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
package Hardware.CPU.Intel80386.Operands.FPU;

import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Pointer.Pointer;
import Hardware.CPU.Intel80386.Register.Segments.Segment;
import Utility.ExtendedDouble;
import Utility.ExtendedDouble.Float80;



public final class OperandM80ExtendedDouble implements OperandFPU {

    private final Intel80386 m_cpu;
    private final Segment m_segment;
    private final Pointer m_offset;
    
    public OperandM80ExtendedDouble(Intel80386 cpu,
                                    Segment segment,
                                    Pointer offset) {
        
        m_cpu = cpu;
        m_segment = segment;
        m_offset = offset;
    }
    
    @Override
    public double getValue() {
        
        int low = m_cpu.readMEM32(m_segment, m_offset.getAddress());
        int mid = m_cpu.readMEM32(m_segment, m_offset.getAddress() + 4);
        int high = m_cpu.readMEM16(m_segment, m_offset.getAddress() + 8);
        
        return ExtendedDouble.fromFloat80(low, mid, high);
    }
    
    @Override
    public void setValue(double value) {
        
        Float80 f80 = ExtendedDouble.toFloat80(value);
        
        m_cpu.writeMEM32(m_segment, m_offset.getAddress(), f80.low);
        m_cpu.writeMEM32(m_segment, m_offset.getAddress() + 4, f80.mid);
        m_cpu.writeMEM16(m_segment, m_offset.getAddress() + 8, f80.high);
    }
    
    @Override
    public String toString() {
        
        return String.format("m80fp ptr [%s:%s]", m_segment.toString(), m_offset.toString());
    }
}
