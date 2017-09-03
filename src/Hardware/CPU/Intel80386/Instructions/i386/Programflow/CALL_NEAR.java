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
package Hardware.CPU.Intel80386.Instructions.i386.Programflow;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class CALL_NEAR extends Instruction {

    private final Operand m_eip;
    private final boolean m_is32;
    
    public CALL_NEAR(Intel80386 cpu,
                     Operand eip,
                     boolean is32) {
        
        super(cpu);
        
        m_eip = eip;
        m_is32 = is32;
    }
    
    @Override
    public void run() {
        
        int oldESP = m_cpu.ESP.getValue();
        try {
        
            int ip = m_eip.getValue();
            
            // Push return address
            if(m_is32) {
                
                m_cpu.pushStack32(m_cpu.EIP.getValue());
            }
            else {
                
                m_cpu.pushStack16(m_cpu.EIP.getValue());
                
                ip &= 0xffff;
            }

            // Update EIP
            m_cpu.EIP.setValue(ip);
        }
        catch(CPUException ex) {
            
            m_cpu.ESP.setValue(oldESP);
            
            throw ex;
        }
    }
    
    @Override
    public String toString() {
        
        return String.format("call %s", m_eip.toString());
    }
}
