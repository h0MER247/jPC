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
import Hardware.CPU.Intel80386.Register.Flags.Flags;



public final class POPF16 extends Instruction {

    public POPF16(Intel80386 cpu) {
        
        super(cpu);
    }
    
    @Override
    public void run() {
            
        if(m_cpu.FLAGS.VM && m_cpu.FLAGS.IOPL != 3)
            throw CPUException.getGeneralProtectionFault(0);
        
        int oldESP = m_cpu.ESP.getValue();
        try {
            
            // All unprivileged flags can change with a POPF
            int mask = Flags.MASK_UNPRIVILEGED_FLAGS;
            
            // IOPL can change if CPL == 0
            if(m_cpu.getCPL() == 0)
                mask |= Flags.MASK_IOPL;
            
            // IF does change when executing at a privilege level at least
            // as privileged as IOPL
            if(m_cpu.getCPL() <= m_cpu.FLAGS.IOPL)
                mask |= Flags.MASK_INTERRUPT_ENABLE;
            
            
            m_cpu.FLAGS.setValue(m_cpu.popStack16(), mask);
        }
        catch(CPUException ex) {
            
            m_cpu.ESP.setValue(oldESP);
            
            throw ex;
        }
    }
    
    @Override
    public String toString() {
        
        return "popf";
    }
}
