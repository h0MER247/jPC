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
package Hardware.CPU.Intel80386.Instructions;

import Hardware.CPU.Intel80386.Intel80386;



public abstract class Instruction {
    
    protected final Intel80386 m_cpu;
    private int m_currentEIP;
    private int m_nextEIP;
    
    
    
    public Instruction(Intel80386 cpu) {
        
        m_cpu = cpu;
    }
    
    
    
    public void setEIP(int currentEIP, int nextEIP) {
        
        m_currentEIP = currentEIP;
        m_nextEIP = nextEIP;
    }
    
    public int getCurrentEIP() {
        
        return m_currentEIP;
    }
    
    public int getNextEIP() {
        
        return m_nextEIP;
    }
    
    
    
    public abstract void run();
}
