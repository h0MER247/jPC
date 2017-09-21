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
import Utility.MathHelper;



public final class FPREM extends Instruction {
    
    public FPREM(Intel80386 cpu) {
        
        super(cpu);
    }
    
    @Override
    public void run() {
        
        double st0 = m_cpu.FPU.getST0();
        double st1 = m_cpu.FPU.getST1();
        
        int d = Math.getExponent(st0) - Math.getExponent(st1);
        if(d < 64) {
            
            // Operation is complete
            m_cpu.FPU.setC2(false);
            
            // Determine resulting remainder "r"
            double q = st0 / st1;
            double r = st0 % st1;
            
            // Determine C1, C3 and C0
            long Q = (long)MathHelper.roundToZero(q);
            m_cpu.FPU.setC1((Q & 0x01l) != 0);
            m_cpu.FPU.setC3((Q & 0x02l) != 0);
            m_cpu.FPU.setC0((Q & 0x04l) != 0);
            
            // Store result
            m_cpu.FPU.setST0(r);
        }
        else {
            
            // Operation is incomplete
            m_cpu.FPU.setC2(true);
            
            double e = Math.pow(2.0, d - 63.0);
            double q = st0 / (st1 * e);
            
            // Store result
            m_cpu.FPU.setST0(st0 - (st1 * MathHelper.roundToZero(q) * e));
        }
    }
    
    @Override
    public String toString() {
        
        return "fprem";
    }
}
