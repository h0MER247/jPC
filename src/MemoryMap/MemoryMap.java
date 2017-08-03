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
package MemoryMap;

import java.util.HashMap;



public final class MemoryMap {
    
    /* ----------------------------------------------------- *
     * Some constants, needed for the memory mapping         *
     * ----------------------------------------------------- */
    public final int MAP_ADDR_MASK;
    public final int MAP_PAGE_BITS;
    public final int MAP_PAGE_COUNT;
    private final int MAP_PAGE_SIZE;
    private final int MAP_PAGE_MASK;
    
    /* ----------------------------------------------------- *
     * List of registered memory devices                     *
     * ----------------------------------------------------- */
    private final HashMap<MemoryMapped, Runnable> m_devices;
    
    /* ----------------------------------------------------- *
     * Unmapped memory device                                *
     * ----------------------------------------------------- */
    private class UnmappedMemoryDevice implements MemoryReadable,
                                                  MemoryWritable {

        private final boolean VERBOSE = false;
        
        @Override public int[][] getReadableMemoryAddresses() { return null; }
        @Override public int readMEM8(int address) { if(VERBOSE) System.out.printf("Read 8 bit from unmapped memory address 0x%08x\n", address); return 0xff; }
        @Override public int readMEM16(int address) { if(VERBOSE) System.out.printf("Read 16 bit from unmapped memory address 0x%08x\n", address); return 0xffff; }
        @Override public int readMEM32(int address) { if(VERBOSE) System.out.printf("Read 32 bit from unmapped memory address 0x%08x\n", address); return 0xffffffff; }

        @Override public int[][] getWritableMemoryAddresses() { return null; }
        @Override public void writeMEM8(int address, int data) { if(VERBOSE) System.out.printf("Write 8 bit to unmapped memory address 0x%08x: 0x%02x\n", address, data); }
        @Override public void writeMEM16(int address, int data) { if(VERBOSE) System.out.printf("Write 16 bit to unmapped memory address 0x%08x: 0x%04x\n", address, data); }
        @Override public void writeMEM32(int address, int data) { if(VERBOSE) System.out.printf("Write 32 bit to unmapped memory address 0x%08x: 0x%08x\n", address, data); }
    }
    private final UnmappedMemoryDevice m_unmapped;
    
    /* ----------------------------------------------------- *
     * Memory mapping                                        *
     * ----------------------------------------------------- */
    private final MemoryReadable[] m_read;
    private final int[] m_readOffset;
    private final MemoryWritable[] m_write;
    private final int[] m_writeOffset;
    
    
    
    public MemoryMap(int sizeOfAddressBusInBit) {
        
        MAP_ADDR_MASK = (1 << sizeOfAddressBusInBit) - 1;
        MAP_PAGE_BITS = 12;
        MAP_PAGE_SIZE = 1 << MAP_PAGE_BITS;
        MAP_PAGE_MASK = MAP_PAGE_SIZE - 1;
        MAP_PAGE_COUNT = 1 << (sizeOfAddressBusInBit - MAP_PAGE_BITS);
        
        m_devices = new HashMap<>();
        
        m_read = new MemoryReadable[MAP_PAGE_COUNT];
        m_readOffset = new int[MAP_PAGE_COUNT];
        m_write = new MemoryWritable[MAP_PAGE_COUNT];
        m_writeOffset = new int[MAP_PAGE_COUNT];
        m_unmapped = new UnmappedMemoryDevice();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Reset">
    
    public void reset() {
        
        for(int i = 0; i < MAP_PAGE_COUNT; i++) {
            
            m_read[i] = m_unmapped;
            m_readOffset[i] = i << MAP_PAGE_BITS;
            
            m_write[i] = m_unmapped;
            m_writeOffset[i] = i << MAP_PAGE_BITS;
        }
        
        m_devices.forEach((memDevice, delegate) -> {
            
            memDevice.offerMemoryMapperDelegate(delegate);
            delegate.run();
        });
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Adding of devices that implement MemoryMapped">
    
    public void addDevice(MemoryMapped memDevice) {
        
        if(memDevice == null)
            throw new IllegalArgumentException("The given memory device reference was null");
        
        boolean isReadable = memDevice instanceof MemoryReadable;
        boolean isWritable = memDevice instanceof MemoryWritable;
        
        if(!isReadable && !isWritable)
            throw new IllegalArgumentException("The memory device has to implement MemoryReadable or MemoryWritable");
        
        
        m_devices.put(memDevice, () -> {
            
            if(isReadable)
                mapDeviceRead((MemoryReadable)memDevice);
            
            if(isWritable)
                mapDeviceWrite((MemoryWritable)memDevice);
        });
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Memory mapping">
    
    private void mapDeviceRead(MemoryReadable memDevice) {
        
        for(int i = 0; i < MAP_PAGE_COUNT; i++) {
            
            if(m_read[i] == memDevice) {
                
                m_read[i] = m_unmapped;
                m_readOffset[i] = i << MAP_PAGE_BITS;
            }
        }
        
        int[][] mapping = memDevice.getReadableMemoryAddresses();
        if(mapping != null) {
            
            for(int[] mappingInfo : mapping)
                performMapping(memDevice, mappingInfo, m_read, m_readOffset);
        }
    }
    
    private void mapDeviceWrite(MemoryWritable memDevice) {
        
        for(int i = 0; i < MAP_PAGE_COUNT; i++) {
            
            if(m_write[i] == memDevice) {
                
                m_write[i] = m_unmapped;
                m_writeOffset[i] = i << MAP_PAGE_BITS;
            }
        }
        
        int[][] mapping = memDevice.getWritableMemoryAddresses();
        if(mapping != null) {
        
            for(int[] mappingInfo : mapping)
                performMapping(memDevice, mappingInfo, m_write, m_writeOffset);
        }
    }
    
    private void performMapping(MemoryMapped memDevice, int[] mappingInfo, MemoryMapped[] dstMap, int[] dstOffset) {
                
        // Read and check mapping info
        if(mappingInfo.length != 3)
            throw new IllegalArgumentException(String.format("The device '%s' specified illegal mapping data", memDevice));
        
        int startAddress = mappingInfo[0];
        int size = mappingInfo[1];
        int offset = mappingInfo[2];
        
        if((startAddress % MAP_PAGE_SIZE) != 0)
            throw new IllegalArgumentException(String.format("The memory start address for device '%s' must be evenly divisible by the page size", memDevice));
        if((startAddress & ~MAP_ADDR_MASK) != 0)
            throw new IllegalArgumentException(String.format("The memory start address for device '%s' is outside the address space", memDevice));
        if(((startAddress + size - 1) & ~MAP_ADDR_MASK) != 0)
            throw new IllegalArgumentException(String.format("The given memory range for device '%s' is outside the address space", memDevice));
        if((size % MAP_PAGE_SIZE) != 0)
            throw new IllegalArgumentException(String.format("The memory size of device '%s' must be evenly divisible by the page size", memDevice));
        
        // Perform the actual mapping
        int pageStart = startAddress >>> MAP_PAGE_BITS;
        int pageEnd = (startAddress + size) >>> MAP_PAGE_BITS;
                
        for(int i = pageStart; i < pageEnd; i++, offset += MAP_PAGE_SIZE) {
            
            dstMap[i] = memDevice;
            dstOffset[i] = offset;
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Memory access">
    
    public int readMEM8(int address) {
        
        address &= MAP_ADDR_MASK;
        
        int page = address >>> MAP_PAGE_BITS;
        
        return m_read[page].readMEM8(m_readOffset[page] + (address & MAP_PAGE_MASK));
    }
    
    public void writeMEM8(int address, int data) {
        
        address &= MAP_ADDR_MASK;
        
        int page = address >>> MAP_PAGE_BITS;
        
        m_write[page].writeMEM8(m_readOffset[page] + (address & MAP_PAGE_MASK), data);
    }
    
    public int readMEM16(int address) {
            
        address &= MAP_ADDR_MASK;
        
        int page = address >>> MAP_PAGE_BITS;
        
        return m_read[page].readMEM16(m_readOffset[page] + (address & MAP_PAGE_MASK));
    }
    
    public void writeMEM16(int address, int data) {
        
        address &= MAP_ADDR_MASK;
        
        int page = address >>> MAP_PAGE_BITS;
        
        m_write[page].writeMEM16(m_readOffset[page] + (address & MAP_PAGE_MASK), data);
    }
    
    public int readMEM32(int address) {
            
        address &= MAP_ADDR_MASK;
        
        int page = address >>> MAP_PAGE_BITS;
        
        return m_read[page].readMEM32(m_readOffset[page] + (address & MAP_PAGE_MASK));
    }
    
    public void writeMEM32(int address, int data) {
        
        address &= MAP_ADDR_MASK;
        
        int page = address >>> MAP_PAGE_BITS;
        
        m_write[page].writeMEM32(m_readOffset[page] + (address & MAP_PAGE_MASK), data);
    }
    
    // </editor-fold>
}
