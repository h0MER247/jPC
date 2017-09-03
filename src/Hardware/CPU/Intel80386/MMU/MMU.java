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
package Hardware.CPU.Intel80386.MMU;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.HardwareComponent;
import MemoryMap.MemoryMap;
import java.util.HashMap;



public final class MMU implements HardwareComponent {

    /* ----------------------------------------------------- *
     * Some constants to make the intention clearer          *
     * ----------------------------------------------------- */    
    private static final boolean WRITE_ACCESS = true;
    private static final boolean READ_ACCESS = false;
    public static final boolean USER_ACCESS = true;
    public static final boolean SYSTEM_ACCESS = false;
    
    /* ----------------------------------------------------- *
     * Page Directory/Table Entry bitmasks                   *
     * ----------------------------------------------------- */
    private final int PG_PRESENT = 0x01;
    private final int PG_RW = 0x02;
    private final int PG_US = 0x04;
    private final int PG_ACCESSED = 0x20;
    private final int PG_DIRTY = 0x40;
    
    /* ----------------------------------------------------- *
     * TLB                                                   *
     * ----------------------------------------------------- */
    private final class TLBEntry {
        
        public boolean isReadOnly;
        public boolean isSystemPage;
        public int physicalAddr;
        
        public TLBEntry(int pageDirectoryEntry,
                        int pageTableEntry) {
            
            isReadOnly = ((pageDirectoryEntry & pageTableEntry) & PG_RW) == 0;
            isSystemPage = ((pageDirectoryEntry & pageTableEntry) & PG_US) == 0;
            physicalAddr = pageTableEntry & 0xfffff000;
        }
    }
    private final HashMap<Integer, TLBEntry> m_tlb;
    private final Integer[] m_integerLUT;
    
    /* ----------------------------------------------------- *
     * Current MMU state                                     *
     * ----------------------------------------------------- */
    private int m_pdbr;
    private int m_pfla;
    private boolean m_isPagingEnabled;
    
    /* ----------------------------------------------------- *
     * A20 Gate                                              *
     * ----------------------------------------------------- */
    private int m_a20GateMask;
    
    /* ----------------------------------------------------- *
     * Reference to the cpu and memory map                   *
     * ----------------------------------------------------- */
    private final Intel80386 m_cpu;
    private final MemoryMap m_memoryMap;
    
    
    
    public MMU(Intel80386 cpu, MemoryMap memoryMap) {
        
        m_cpu = cpu;
        m_memoryMap = memoryMap;
        
        m_tlb = new HashMap<>();
        m_integerLUT = new Integer[1 << 20];
        
        for(int i = 0; i < m_integerLUT.length; i++)
            m_integerLUT[i] = i;
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        setPageDirectoryBaseRegister(0);
        setPageFaultLinearAddress(0);
        
        setPagingEnabled(false);
        setA20Gate(false);
        
        flushTLB();
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="A20 Gate">
    
    public void setA20Gate(boolean gate) {
        
        m_a20GateMask = 0xffffffff;
        
        if(!gate)
            m_a20GateMask &= ~(1 << 20);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="TLB access methods">
    
    public void setPagingEnabled(boolean isPagingEnabled) {
        
        m_isPagingEnabled = isPagingEnabled;
    }
    
    public boolean isPagingEnabled() {
        
        return m_isPagingEnabled;
    }
    
    public int getPageFaultLinearAddress() {
        
        return m_pfla;
    }
    
    public void setPageFaultLinearAddress(int address) {
        
        m_pfla = address;
    }
    
    public int getPageDirectoryBaseRegister() {
        
        return m_pdbr;
    }
    
    public void setPageDirectoryBaseRegister(int address) {
        
        m_pdbr = address & 0xfffff000;
        flushTLB();
    }
    
    public void flushTLB() {
        
        m_tlb.clear();
    }
    
    public void invalidatePage(int address) {
        
        throw new UnsupportedOperationException("Implement me");
    }
    
    public int getPhysicalAddress(int linearAddress, boolean isWrite, boolean isUserAccess) {
        
        if(m_isPagingEnabled) {
            
            TLBEntry phys = m_tlb.get(m_integerLUT[linearAddress >>> 12]);
            
            //
            // Walk the page tables if no entry in the tlb was found
            //
            if(phys == null) {
                
                // Read page directory
                int pdEntryAddress = m_pdbr + ((linearAddress >>> 20) & 0xffc);
                int pdEntry = m_memoryMap.readMEM32(pdEntryAddress);
                
                if((pdEntry & PG_PRESENT) == 0) {

                    setPageFaultLinearAddress(linearAddress);

                    throw CPUException.getPageNotPresent(isWrite, m_cpu.getCPL() == 3);
                }

                // Read page table
                int ptEntryAddress = (pdEntry & 0xfffff000) + ((linearAddress >>> 10) & 0xffc);
                int ptEntry = m_memoryMap.readMEM32(ptEntryAddress);
                
                if((ptEntry & PG_PRESENT) == 0) {

                    setPageFaultLinearAddress(linearAddress);

                    throw CPUException.getPageNotPresent(isWrite, m_cpu.getCPL() == 3);
                }

                // Mark page directory/table accesses
                if((pdEntry & PG_ACCESSED) == 0)
                    m_memoryMap.writeMEM8(pdEntryAddress, pdEntry | PG_ACCESSED);
                
                if(isWrite && ((ptEntry & PG_DIRTY) == 0))
                    m_memoryMap.writeMEM8(ptEntryAddress, ptEntry | PG_ACCESSED | PG_DIRTY);

                else if((ptEntry & PG_ACCESSED) == 0)
                    m_memoryMap.writeMEM8(ptEntryAddress, ptEntry | PG_ACCESSED);

                // Put entry in the tlb
                m_tlb.put(m_integerLUT[linearAddress >>> 12], phys = new TLBEntry(pdEntry, ptEntry));
            }


            //
            // Check access rights if the cpu operates in user mode (cpl == 3).
            // Users don't have access to non user pages and can't write into
            // read-only pages.
            //
            if(m_cpu.getCPL() == 3 && isUserAccess) {
                
                if(phys.isSystemPage || (phys.isReadOnly && isWrite)) {

                    setPageFaultLinearAddress(linearAddress);
                    throw CPUException.getPageProtectionViolation(isWrite, true);
                }
            }
            
            // Return the physical address
            return (phys.physicalAddr | (linearAddress & 0xfff)) & m_a20GateMask;
        }
        else {
            
            // Return the linear address, which is equal to the physical address
            // when paging is disabled
            return linearAddress & m_a20GateMask;
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Methods for the paged memory access">
    
    public int readMEM8(int address, boolean isUserAccess) {
            
        return m_memoryMap.readMEM8(getPhysicalAddress(address, READ_ACCESS, isUserAccess));
    }
    
    public int readMEM16(int address, boolean isUserAccess) {
        
        if((address & 0x01) == 0) {
        
            return m_memoryMap.readMEM16(getPhysicalAddress(address, READ_ACCESS, isUserAccess));
        }
        else {
            
            return readMEM8(address, isUserAccess) |
                  (readMEM8(address + 1, isUserAccess) << 8);
        }
    }
    
    public int readMEM32(int address, boolean isUserAccess) {
        
        if((address & 0x03) == 0) {
            
            return m_memoryMap.readMEM32(getPhysicalAddress(address, READ_ACCESS, isUserAccess));
        }
        else {
            
            return readMEM8(address, isUserAccess) |
                  (readMEM8(address + 1, isUserAccess) << 8) |
                  (readMEM8(address + 2, isUserAccess) << 16) |
                  (readMEM8(address + 3, isUserAccess) << 24);
        }
    }
    
    public void writeMEM8(int address, int data, boolean isUserAccess) {
        
        address = getPhysicalAddress(address, WRITE_ACCESS, isUserAccess);
        
        m_cpu.invalidateAddress(address, 1);
        m_memoryMap.writeMEM8(address, data & 0xff);
    }
    
    public void writeMEM16(int address, int data, boolean isUserAccess) {
        
        if((address & 0x01) == 0) {
            
            address = getPhysicalAddress(address, WRITE_ACCESS, isUserAccess);
            
            m_cpu.invalidateAddress(address, 2);
            m_memoryMap.writeMEM16(address, data & 0xffff);
        }
        else {
            
            writeMEM8(address, data & 0xff, isUserAccess);
            writeMEM8(address + 1, (data >>> 8) & 0xff, isUserAccess);
        }
    }
    
    public void writeMEM32(int address, int data, boolean isUserAccess) {
        
        if((address & 0x03) == 0) {
            
            address = getPhysicalAddress(address, WRITE_ACCESS, isUserAccess);
            
            m_cpu.invalidateAddress(address, 4);
            m_memoryMap.writeMEM32(address, data);
        }
        else {
            
            writeMEM8(address, data & 0xff, isUserAccess);
            writeMEM8(address + 1, (data >>> 8) & 0xff, isUserAccess);
            writeMEM8(address + 2, (data >>> 16) & 0xff, isUserAccess);
            writeMEM8(address + 3, (data >>> 24) & 0xff, isUserAccess);
        }
    }
    
    // </editor-fold>
}
