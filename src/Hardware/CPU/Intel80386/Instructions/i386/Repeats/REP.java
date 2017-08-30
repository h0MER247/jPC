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
package Hardware.CPU.Intel80386.Instructions.i386.Repeats;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Register.General.Register;
import Scheduler.Scheduler;



public final class REP extends Instruction {

    private final Instruction m_instruction;
    private final Register m_counter;
    
    public REP(Intel80386 cpu,
               Instruction instruction,
               Register counter) {
        
        super(cpu);
        
        m_instruction = instruction;
        m_counter = counter;
    }

    @Override
    public void run() {
        
        int counter = m_counter.getValue();
        try {
            
            while(counter != 0) {
                
                if(m_cpu.isInterruptPending())
                    throw CPUException.getREPInterrupted();

                m_instruction.run();
                counter--;
            }
        }
        catch(CPUException ex) {
            
            throw ex;
        }
        finally {
        
            m_cpu.updateClock(Scheduler.toFixedPoint((m_counter.getValue() - counter) << 2));
            m_counter.setValue(counter);
        }
    }
    
    @Override
    public String toString() {
        
        return String.format("rep %s", m_instruction.toString());
    }
}
