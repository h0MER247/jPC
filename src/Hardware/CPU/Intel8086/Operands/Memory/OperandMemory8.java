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
package Hardware.CPU.Intel8086.Operands.Memory;

import Hardware.CPU.Intel8086.Intel8086;
import Hardware.CPU.Intel8086.Operands.Operand;
import Hardware.CPU.Intel8086.Segments.Segment;



public final class OperandMemory8 implements Operand {
    
    private final Intel8086 m_cpu;
    private final Segment m_segment;
    private final Operand m_offset;

    public OperandMemory8(Intel8086 cpu,
                          Segment segment,
                          Operand offset) {
        
        m_cpu = cpu;
        m_segment = segment;
        m_offset = offset;
    }

    @Override
    public int getValue() {

        return m_cpu.readMEM8(m_segment.getBase(), m_offset.getValue());
    }

    @Override
    public void setValue(int value) {

        m_cpu.writeMEM8(m_segment.getBase(),
                        m_offset.getValue(),
                        value);
    }

    @Override
    public String toString() {

        return String.format("byteptr [%s:%s]", m_segment.toString(), m_offset.toString());
    }
}
