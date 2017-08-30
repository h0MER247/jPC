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
package Hardware.CPU.Intel80386.Instructions.i386.Datatransfer;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class LSEG extends Instruction {
    
    private final Operand m_segment;
    private final Operand m_register;
    private final Operand m_segmentValue;
    private final Operand m_registerValue;
    
    public LSEG(Intel80386 cpu,
                Operand segment,
                Operand register,
                Operand segmentValue,
                Operand registerValue) {
        
        super(cpu);
        
        m_segment = segment;
        m_register = register;
        m_segmentValue = segmentValue;
        m_registerValue = registerValue;
    }

    @Override
    public void run() {
        
        int seg = m_segmentValue.getValue();
        int reg = m_registerValue.getValue();
        
        m_segment.setValue(seg); 
        m_register.setValue(reg);
    }
    
    @Override
    public String toString() {
    
        return String.format("l%s %s, %s", m_segment.toString(), m_register.toString(), m_registerValue.toString());
    }
}
