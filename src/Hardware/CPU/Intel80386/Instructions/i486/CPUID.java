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
package Hardware.CPU.Intel80386.Instructions.i486;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;



public final class CPUID extends Instruction {

    public CPUID(Intel80386 cpu) {
        
        super(cpu);
    }

    @Override
    public void run() {
        
        System.out.println("CPUID");
        
        // TODO: I have to rethink this approach if I'll ever support
        //       other cpu types. But it's okay for testing.
        switch(m_cpu.EAX.getValue()) {
            
            case 0:
                m_cpu.EAX.setValue(1);          // Maximum input value for EAX
                m_cpu.EBX.setValue(0x756e6547); // Genu
                m_cpu.EDX.setValue(0x49656e69); // ineI
                m_cpu.ECX.setValue(0x6c65746e); // ntel
                break;
            
            // Version information
            case 1:
                m_cpu.EAX.setValue(0x45b);
                m_cpu.EBX.setValue(0);
                m_cpu.EDX.setValue(1);
                m_cpu.ECX.setValue(0);
                break;
                
            default:
                m_cpu.EAX.setValue(0);
                break;
        }
    }
    
    @Override
    public String toString() {
        
        return "cpuid";
    }
}
