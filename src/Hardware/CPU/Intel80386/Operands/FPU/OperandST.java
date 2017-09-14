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



public final class OperandST implements OperandFPU {

    private final Intel80386 m_cpu;
    private final int m_index;
    
    public OperandST(Intel80386 cpu,
                     int index) {
        
        m_cpu = cpu;
        m_index = index;
    }
    
    @Override
    public double getValue() {
        
        return m_cpu.FPU.getST(m_index);
    }
    
    @Override
    public void setValue(double value) {
        
        m_cpu.FPU.setST(m_index, value);
    }
    
    @Override
    public String toString() {
        
        return String.format("st%d", m_index);
    }
}
