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

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class ARPL extends Instruction {

    private final Operand m_source;
    private final Operand m_destination;
    
    public ARPL(Intel80386 cpu,
                Operand source,
                Operand destination) {
        
        super(cpu);
        
        m_source = source;
        m_destination = destination;
    }
    
    @Override
    public void run() {
        
        if(m_cpu.CR.isInRealMode() || m_cpu.FLAGS.VM)
            throw CPUException.getInvalidOpcode();
        
        int src = m_source.getValue();
        int dst = m_destination.getValue();
            
        if(m_cpu.getSelectorsRPL(dst) < m_cpu.getSelectorsRPL(src)) {
            
            m_destination.setValue((dst & 0xfffc) | (src & 0x0003));
            m_cpu.FLAGS.ZF = true;
        }
        else {

            m_cpu.FLAGS.ZF = false;
        }
    }
    
    @Override
    public String toString() {
        
        return String.format("arpl %s, %s", m_source.toString(), m_destination.toString());
    }
}
