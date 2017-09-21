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
package Hardware.CPU.Intel80386.Instructions.FPU.Misc;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Utility.MathHelper;



public final class FXAM extends Instruction {

    public FXAM(Intel80386 cpu) {
        
        super(cpu);
    }

    @Override
    public void run() {
        
        double st0 = m_cpu.FPU.getST0();
        
        // Set C1 depending on ST0's sign
        m_cpu.FPU.setC1(st0 < 0.0);
        
        // Empty
        if(m_cpu.FPU.isRegisterEmpty(0)) {
            
            m_cpu.FPU.setC3(true);
            m_cpu.FPU.setC2(false);
            m_cpu.FPU.setC0(true);
        }
        // Zero
        else if(st0 == 0.0) {
            
            m_cpu.FPU.setC3(true);
            m_cpu.FPU.setC2(false);
            m_cpu.FPU.setC0(false);
        }
        // Normal finite number
        else if(Double.isFinite(st0)) {
            
            m_cpu.FPU.setC3(false);
            m_cpu.FPU.setC2(true);
            m_cpu.FPU.setC0(false);
        }
        // NaN
        else if(Double.isNaN(st0)) {
            
            m_cpu.FPU.setC3(false);
            m_cpu.FPU.setC2(false);
            m_cpu.FPU.setC0(true);
        }
        // Infinity
        else if(Double.isInfinite(st0)) {
            
            m_cpu.FPU.setC3(false);
            m_cpu.FPU.setC2(true);
            m_cpu.FPU.setC0(true);
        }
        // Denormal number
        else if(MathHelper.isDenormal(st0)) {
            
            m_cpu.FPU.setC3(true);
            m_cpu.FPU.setC2(true);
            m_cpu.FPU.setC0(false);
        }
        // Unsupported
        else {
            
            m_cpu.FPU.setC3(false);
            m_cpu.FPU.setC2(false);
            m_cpu.FPU.setC0(false);
        }
    }
    
    @Override
    public String toString() {
        
        return "fxam";
    }
}
