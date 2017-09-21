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
    private class ReadMapping {
        
        MemoryReadable mem;
        int offset;
    }
    private class WriteMapping {
        
        MemoryWritable mem;
        int offset;
    }
    private final ReadMapping[] m_read;
    private final WriteMapping[] m_write;
    
    
    
    public MemoryMap(int sizeOfAddressBusInBit) {
        
        MAP_ADDR_MASK = (int)((1l << sizeOfAddressBusInBit) - 1l);
        MAP_PAGE_BITS = 12;
        MAP_PAGE_SIZE = 1 << MAP_PAGE_BITS;
        MAP_PAGE_MASK = MAP_PAGE_SIZE - 1;
        MAP_PAGE_COUNT = (int)(1l << (sizeOfAddressBusInBit - MAP_PAGE_BITS));
        
        m_devices = new HashMap<>();
        
        m_read = new ReadMapping[MAP_PAGE_COUNT];
        for(int i = 0; i < MAP_PAGE_COUNT; i++)
            m_read[i] = new ReadMapping();
        
        m_write = new WriteMapping[MAP_PAGE_COUNT];
        for(int i = 0; i < MAP_PAGE_COUNT; i++)
            m_write[i] = new WriteMapping();
        
        m_unmapped = new UnmappedMemoryDevice();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Reset">
    
    public void reset() {
        
        for(int i = 0; i < MAP_PAGE_COUNT; i++) {
            
            m_read[i].mem = m_unmapped;
            m_read[i].offset = i << MAP_PAGE_BITS;
            m_write[i].mem = m_unmapped;
            m_write[i].offset = i << MAP_PAGE_BITS;
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
            
            if(m_read[i].mem == memDevice) {
                
                m_read[i].mem = m_unmapped;
                m_read[i].offset = i << MAP_PAGE_BITS;
            }
        }
        
        int[][] mapping = memDevice.getReadableMemoryAddresses();
        if(mapping != null) {
            
            for(int[] mappingInfo : mapping) {
                
                if(mappingInfo.length != 3)
                    throw new IllegalArgumentException(String.format("The device '%s' specified illegal mapping data", memDevice));
                
                int startAddress = mappingInfo[0];
                int size = mappingInfo[1];
                int offset = mappingInfo[2];
                
                checkMappingInfo(memDevice, startAddress, size, offset);
                
                int pageStart = startAddress >>> MAP_PAGE_BITS;
                int pageEnd = (startAddress + size) >>> MAP_PAGE_BITS;
                
                for(int i = pageStart; i < pageEnd; i++, offset += MAP_PAGE_SIZE) {
            
                    m_read[i].mem = memDevice;
                    m_read[i].offset = offset;
                }
            }
        }
    }
    
    private void mapDeviceWrite(MemoryWritable memDevice) {
        
        for(int i = 0; i < MAP_PAGE_COUNT; i++) {
            
            if(m_write[i].mem == memDevice) {
                
                m_write[i].mem = m_unmapped;
                m_write[i].offset = i << MAP_PAGE_BITS;
            }
        }
        
        int[][] mapping = memDevice.getWritableMemoryAddresses();
        if(mapping != null) {
        
            for(int[] mappingInfo : mapping) {
                
                if(mappingInfo.length != 3)
                    throw new IllegalArgumentException(String.format("The device '%s' specified illegal mapping data", memDevice));
                
                int startAddress = mappingInfo[0];
                int size = mappingInfo[1];
                int offset = mappingInfo[2];
                
                checkMappingInfo(memDevice, startAddress, size, offset);
                
                int pageStart = startAddress >>> MAP_PAGE_BITS;
                int pageEnd = (startAddress + size) >>> MAP_PAGE_BITS;
                
                for(int i = pageStart; i < pageEnd; i++, offset += MAP_PAGE_SIZE) {
            
                    m_write[i].mem = memDevice;
                    m_write[i].offset = offset;
                }
            }
        }
    }
    
    private void checkMappingInfo(MemoryMapped device, int startAddress, int size, int offset) {
        
        if((startAddress % MAP_PAGE_SIZE) != 0)
            throw new IllegalArgumentException(String.format("The memory start address for device '%s' must be evenly divisible by the page size", device));
        
        if(((startAddress & ~MAP_ADDR_MASK) != 0) && MAP_ADDR_MASK != 0xffffffff)
            throw new IllegalArgumentException(String.format("The memory start address for device '%s' is outside the address space", device));
        
        if((((startAddress + size - 1) & ~MAP_ADDR_MASK) != 0) && MAP_ADDR_MASK != 0xffffffff)
            throw new IllegalArgumentException(String.format("The given memory range for device '%s' is outside the address space", device));
        
        if((size % MAP_PAGE_SIZE) != 0)
            throw new IllegalArgumentException(String.format("The memory size of device '%s' must be evenly divisible by the page size", device));        
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Memory access">
    
    public int readMEM8(int address) {
        
        address &= MAP_ADDR_MASK;
        
        ReadMapping r = m_read[address >>> MAP_PAGE_BITS];
        
        return r.mem.readMEM8(r.offset + (address & MAP_PAGE_MASK));
    }
    
    public int readMEM16(int address) {
            
        address &= MAP_ADDR_MASK;
        
        ReadMapping r = m_read[address >>> MAP_PAGE_BITS];
        
        return r.mem.readMEM16(r.offset + (address & MAP_PAGE_MASK));
    }
    
    public int readMEM32(int address) {
            
        address &= MAP_ADDR_MASK;
        
        ReadMapping r = m_read[address >>> MAP_PAGE_BITS];
        
        return r.mem.readMEM32(r.offset + (address & MAP_PAGE_MASK));
    }
    
    
    
    public void writeMEM8(int address, int data) {
        
        address &= MAP_ADDR_MASK;
        
        WriteMapping w = m_write[address >>> MAP_PAGE_BITS];
        
        w.mem.writeMEM8(w.offset + (address & MAP_PAGE_MASK), data);
    }
    
    public void writeMEM16(int address, int data) {
        
        address &= MAP_ADDR_MASK;
        
        WriteMapping w = m_write[address >>> MAP_PAGE_BITS];
        
        w.mem.writeMEM16(w.offset + (address & MAP_PAGE_MASK), data);
    }
    
    public void writeMEM32(int address, int data) {
        
        address &= MAP_ADDR_MASK;
        
        WriteMapping w = m_write[address >>> MAP_PAGE_BITS];
        
        w.mem.writeMEM32(w.offset + (address & MAP_PAGE_MASK), data);
    }
    
    // </editor-fold>
}
