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
package Hardware.CPU.Intel8086.Instructions;

import Hardware.CPU.Intel8086.Intel8086;



public abstract class Instruction {
    
    protected final Intel8086 m_cpu;
    private int m_currentIP;
    private int m_nextIP;
    private final int m_cyclesBT;   // Number of cycles for a non branching instruction or a branch instruction
                                    // that took a branch
    private final int m_cyclesBNT;  // Number of cycles for a branching instruction that did not take a branch
    
    
    
    public Instruction(Intel8086 cpu, int cycles) {
        
        this(cpu, cycles, cycles);
    }
    
    public Instruction(Intel8086 cpu, int cyclesBranchTaken, int cyclesBranchNotTaken) {
        
        m_cpu = cpu;
        m_cyclesBT = cyclesBranchTaken;
        m_cyclesBNT = cyclesBranchNotTaken;
    }
    
    
    
    public void setIP(int currentIP, int nextIP) {
        
        m_currentIP = currentIP;
        m_nextIP = nextIP;
    }
    
    public int getCurrentIP() {
        
        return m_currentIP;
    }
    
    public int getNextIP() {
        
        return m_nextIP;
    }
    
    
    
    protected int getCycles() { return m_cyclesBT; }
    protected int getCyclesBranchTaken() { return m_cyclesBT; }
    protected int getCyclesBranchNotTaken() { return m_cyclesBNT; }
    
    
    
    public abstract void run();
}
