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

import Hardware.HardwareComponent;
import MemoryMap.MemoryReadable;
import MemoryMap.MemoryWritable;
import java.util.Arrays;



public final class XTSystemRAM implements HardwareComponent,
                                          MemoryReadable,
                                          MemoryWritable {
    
    /* ----------------------------------------------------- *
     * RAM data                                              *
     * ----------------------------------------------------- */
    private final int[] m_data;
    
    

    public XTSystemRAM() {
        
        m_data = new int[640 * 1024];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        Arrays.fill(m_data, 0x00);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of MemoryReadable and MemoryWritable">
    
    @Override
    public int[][] getReadableMemoryAddresses() {
        
        return new int[][] {
            
            new int[] { 0x00000, 0xa0000, 0 }
        };
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
        
        throw new UnsupportedOperationException("Unsupported 32 bit access");
    }
    
    @Override
    public int[][] getWritableMemoryAddresses() {
        
        return new int[][] {
            
            new int[] { 0x00000, 0xa0000, 0 }
        };
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
        
        throw new UnsupportedOperationException("Unsupported 32 bit access");
    }
    
    // </editor-fold>
}
