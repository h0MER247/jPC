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
package Hardware.CPU.Intel8086.Codeblock;

import Hardware.CPU.Intel8086.Exceptions.InterruptException;
import Hardware.CPU.Intel8086.Instructions.Instruction;
import Hardware.CPU.Intel8086.Intel8086;



public final class CodeBlock {
    
    /* ----------------------------------------------------- *
     * Address of this codeblock                             *
     * ----------------------------------------------------- */
    private final int m_cs;
    private final int m_paragraphStart;
    private final int m_paragraphEnd;
    
    /* ----------------------------------------------------- *
     * Last time this block was executed                     *
     * ----------------------------------------------------- */
    private long m_lastExecutionTime;
    
    /* ----------------------------------------------------- *
     * Instructions of this codeblock                        *
     * ----------------------------------------------------- */
    private final Instruction[] m_instructions;
    private boolean m_isRunning;
    
    /* ----------------------------------------------------- *
     * Reference to the Intel 8086 CPU                       *
     * ----------------------------------------------------- */
    private final Intel8086 m_cpu;
    
    
    
    public CodeBlock(Intel8086 cpu,
                     int base,
                     int offsetStart,
                     int offsetEnd,
                     Instruction[] instructions) {
        
        m_cpu = cpu;
        
        m_cs = base >>> 4;
        m_paragraphStart = (base + offsetStart) & 0xffff0;
        m_paragraphEnd = (base + offsetEnd) & 0xffff0;
        
        m_instructions = instructions;
        m_lastExecutionTime = 0L;
    }
    
    
    
    public boolean isInsideParagraph(int paragraph) {
        
        return m_paragraphStart <= paragraph &&
               m_paragraphEnd >= paragraph;
    }
    
    public long getLastExecutionTime() {
        
        return m_lastExecutionTime;
    }
    
    
    
    public void run() {
        
        int i = 0;
        try {
            
            m_lastExecutionTime = System.currentTimeMillis();
            m_isRunning = true;
            
            while(i < m_instructions.length & m_isRunning) {
                
                m_cpu.IP.setValue(m_instructions[i].getNextIP());
                m_instructions[i].run();
                
                i++;
            }
        }
        catch(InterruptException ex) {
            
            m_cpu.IP.setValue(m_instructions[i].getCurrentIP());
            throw ex;
        }
    }
    
    public void abort() {
        
        m_isRunning = false;
    }
    
    
    
    @Override
    public String toString() {
        
        String res = "";
        for(Instruction i : m_instructions)
            res += String.format("%04x:%04x %s\n", m_cs, i.getCurrentIP(), i.toString());
        
        return res;
    }
}
