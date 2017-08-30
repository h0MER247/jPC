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
package Hardware.CPU.Intel80386.Register.Control;

import Hardware.CPU.Intel80386.MMU.MMU;



public final class Control {
    
    /* ----------------------------------------------------- *
     * Controlregister 0 bitmasks                            *
     * ----------------------------------------------------- */
    public static final int CR0_PROTECTION_ENABLED = 1;
    public static final int CR0_MATH_PRESENT = 1 << 1;
    public static final int CR0_EMULATION = 1 << 2;
    public static final int CR0_TASK_SWITCHED = 1 << 3;
    public static final int CR0_EXTENSION_TYPE = 1 << 4;
    public static final int CR0_PAGING_ENABLED = 1 << 31;
    
    /* ----------------------------------------------------- *
     * Controlregister 0                                     *
     * ----------------------------------------------------- */
    private int m_cr0;
    
    /* ----------------------------------------------------- *
     * Reference to the memory management unit               *
     * ----------------------------------------------------- */
    private final MMU m_mmu;
    
    
    
    public Control(MMU mmu) {
        
        m_mmu = mmu;
    }
    
    
    
    public void reset() {
        
        setCR0(0);
        setCR2(0);
        setCR3(0);
    }
    
    
    
    
    public int getMSW() {
        
        return m_cr0 & 0xffff;
    }
    
    public void setMSW(int val) {
        
        // Changing the MSW can't be used to switch back to real mode
        setCR0((m_cr0 & 0xffff0001) | (val & 0xffff));
    }
    
    public int getCR0() {
        
        return m_cr0;
    }
        
    public void setCR0(int val) {
        
        // Flush the TLB if paging or protected mode gets enabled
        //if((((m_cr0 ^ val) & val) & (CR0_PAGING_ENABLED | CR0_PROTECTION_ENABLED)) != 0)
        //    m_mmu.flushTLB();
        
        m_cr0 = val | 0x7ffffff0;
        m_mmu.setPagingEnabled((m_cr0 & CR0_PAGING_ENABLED) != 0);
    }
    
    public int getCR2() {
        
        return m_mmu.getPageFaultLinearAddress();
    }
    
    public void setCR2(int val) {
        
        m_mmu.setPageFaultLinearAddress(val);
    }
    
    public int getCR3() {
        
        return m_mmu.getPageDirectoryBaseRegister();
    }
    
    public void setCR3(int val) {
        
        m_mmu.setPageDirectoryBaseRegister(val);
    }
    
    
    
    public boolean isInRealMode() {
        
        return (m_cr0 & CR0_PROTECTION_ENABLED) == 0;
    }
    
    public boolean isInProtectedMode() {
        
        return (m_cr0 & CR0_PROTECTION_ENABLED) != 0;
    }
    
    public boolean isMathPresent() {
        
        return (m_cr0 & CR0_MATH_PRESENT) != 0;
    }
    
    public boolean isEmulationEnabled() {
        
        return (m_cr0 & CR0_EMULATION) != 0;
    }
    
    public boolean isTaskSwitched() {
        
        return (m_cr0 & CR0_TASK_SWITCHED) != 0;
    }
    
    public boolean isPagingEnabled() {
        
        return (m_cr0 & CR0_PAGING_ENABLED) != 0;
    }
    
    public void setTaskSwitched(boolean isTaskSwitched) {
        
        if(isTaskSwitched)
            m_cr0 |= CR0_TASK_SWITCHED;
        else
            m_cr0 &= ~CR0_TASK_SWITCHED;
    }
    
    
    
    @Override
    public String toString() {
        
        return String.format("CR0:%08X, CR2:%08X, CR3:%08X", getCR0(), getCR2(), getCR3());
    }
}
