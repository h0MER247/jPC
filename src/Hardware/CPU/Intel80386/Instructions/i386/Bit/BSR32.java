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
package Hardware.CPU.Intel80386.Instructions.i386.Bit;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class BSR32 extends Instruction {

    private final Operand m_destination;
    private final Operand m_source;
    
    public BSR32(Intel80386 cpu,
                 Operand destination,
                 Operand source) {
        
        super(cpu);
        
        m_destination = destination;
        m_source = source;
    }
    
    @Override
    public void run() {
        
        int source = m_source.getValue();
        
        if(source != 0) {
            
            m_cpu.FLAGS.ZF = false;
            
            for(int i = 31; i >= 0; i--) {
                
                if((source & (1 << i)) != 0) {
                    
                    m_destination.setValue(i);
                    break;
                }
            }
        }
        else m_cpu.FLAGS.ZF = true;
    }
    
    @Override
    public String toString() {
        
        return String.format("bsr %s, %s", m_destination.toString(), m_source.toString());
    }
}
