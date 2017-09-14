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
package Hardware.CPU.Intel80386.Instructions.FPU.Misc;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Register.FPU.FPURegisters;



public final class FXAM extends Instruction {

    public FXAM(Intel80386 cpu) {
        
        super(cpu);
    }

    @Override
    public void run() {
        
        m_cpu.FPU.clearConditions();
        
        // Set C1 depending on ST0's sign
        double value = m_cpu.FPU.getST0();
        if(value < 0.0)
            m_cpu.FPU.setConditions(FPURegisters.STATUS_CC_C1);
        
        // Set C3, C0 if register is empty
        if(m_cpu.FPU.isRegisterEmpty(0)) {
            
            m_cpu.FPU.setConditions(FPURegisters.STATUS_CC_C3 | FPURegisters.STATUS_CC_C0);
        }
        else {
            
            // Set C2 if register contains a valid finite number
            if(Double.isFinite(value))
                m_cpu.FPU.setConditions(FPURegisters.STATUS_CC_C2);
            
            // Set C3 if register contains zero
            else if(value == 0.0)
                m_cpu.FPU.setConditions(FPURegisters.STATUS_CC_C3);
            
            // Set C2, C0 if register contains infinity
            else if(Double.isInfinite(value))
                m_cpu.FPU.setConditions(FPURegisters.STATUS_CC_C2 | FPURegisters.STATUS_CC_C0);
        
            // Set C0 if register contains NaN
            else if(Double.isNaN(value))
                m_cpu.FPU.setConditions(FPURegisters.STATUS_CC_C0);
        }
    }
    
    @Override
    public String toString() {
        
        return "fxam";
    }
}
