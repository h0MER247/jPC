/*
 * Copyright (C) 2017 homer
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
import Hardware.CPU.Intel80386.Register.FPU.FPURegisters;



public final class FPREM extends Instruction {
    
    public FPREM(Intel80386 cpu) {
        
        super(cpu);
    }

    @Override
    public void run() {
        
        double st0 = m_cpu.FPU.getST0();
        double st1 = m_cpu.FPU.getST1();
        
        // Calculate quotient and truncate it toward zero
        double q = st0 / st1;
        long Q = (long)(Math.floor(Math.abs(q)) * Math.signum(q));
        
        m_cpu.FPU.setST0(st0 - (st1 * Q));
        
        int s = m_cpu.FPU.getStatus() & ~FPURegisters.STATUS_CC_MASK;
        
        if((Q & 0x01l) != 0)
            s |= FPURegisters.STATUS_CC_C1;
        if((Q & 0x02l) != 0)
            s |= FPURegisters.STATUS_CC_C3;
        if((Q & 0x04l) != 0)
            s |= FPURegisters.STATUS_CC_C0;
        
        m_cpu.FPU.setStatus(s);
    }
    
    @Override
    public String toString() {
        
        return "fprem";
    }
}
