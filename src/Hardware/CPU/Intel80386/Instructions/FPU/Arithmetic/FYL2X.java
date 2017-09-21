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
package Hardware.CPU.Intel80386.Instructions.FPU.Arithmetic;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;



public final class FYL2X extends Instruction {
    
    public FYL2X(Intel80386 cpu) {
        
        super(cpu);
    }

    @Override
    public void run() {
        
        double st0 = m_cpu.FPU.getST0();
        double st1 = m_cpu.FPU.getST1();
        
        m_cpu.FPU.setST1(
                
            st1 * (Math.log10(st0) * 3.3219280948873623478703194294894)
        );
        m_cpu.FPU.popStack();
    }
    
    @Override
    public String toString() {
        
        return "fyl2x";
    }
}
