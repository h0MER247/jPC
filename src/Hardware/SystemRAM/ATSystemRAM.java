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
package Hardware.SystemRAM;

import Hardware.CMOS.CMOSMap;
import Hardware.HardwareComponent;
import MemoryMap.MemoryReadable;
import MemoryMap.MemoryWritable;
import java.util.Arrays;



public final class ATSystemRAM implements HardwareComponent,
                                          MemoryReadable,
                                          MemoryWritable {
    
    /* ----------------------------------------------------- *
     * Fixed RAM size at the moment                          *
     * ----------------------------------------------------- */
    public static final int RAM_SIZE_IN_MB = 64;
    public static final int RAM_SIZE_MASK = (RAM_SIZE_IN_MB << 20) - 1;
    
    /* ----------------------------------------------------- *
     * RAM data                                              *
     * ----------------------------------------------------- */
    private final int[] m_data;
    private final int[][] m_mapping;
    
    

    public ATSystemRAM() {
            
        m_data = new int[RAM_SIZE_IN_MB << 20];
        m_mapping = new int[][] {

            new int[] { 0x00000, 0xa0000, 0 },
            new int[] { 0x00100000, (RAM_SIZE_IN_MB - 1) << 20, 0x00100000 }
        };
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        Arrays.fill(m_data, 0x00);
    }

    @Override
    public void updateCMOS(CMOSMap map) {
        
        map.setMemorySizeInMB(RAM_SIZE_IN_MB);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of MemoryReadable and MemoryWritable">
    
    @Override
    public int[][] getReadableMemoryAddresses() {
        
        return m_mapping;
    }
    
    @Override
    public int readMEM8(int address) {
        
        return m_data[address];
    }

    @Override
    public int readMEM16(int address) {
        
        return m_data[address] |
              (m_data[address + 1] << 8);
    }
    
    @Override
    public int readMEM32(int address) {
        
        return m_data[address] |
              (m_data[address + 1] << 8) |
              (m_data[address + 2] << 16) |
              (m_data[address + 3] << 24);
    }

    @Override
    public int[][] getWritableMemoryAddresses() {
        
        return m_mapping;
    }
    
    @Override
    public void writeMEM8(int address, int data) {
        
        m_data[address] = data & 0xff;
    }
    
    @Override
    public void writeMEM16(int address, int data) {
        
        m_data[address] = data & 0xff;
        m_data[address + 1] = (data >>> 8) & 0xff;
    }
    
    @Override
    public void writeMEM32(int address, int data) {
        
        m_data[address] = data & 0xff;
        m_data[address + 1] = (data >>> 8) & 0xff;
        m_data[address + 2] = (data >>> 16) & 0xff;
        m_data[address + 3] = (data >>> 24) & 0xff;
    }
    
    // </editor-fold>
}
