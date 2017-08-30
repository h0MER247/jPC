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
package Hardware.DMAController;

import Hardware.HardwareComponent;
import java.util.Arrays;
import IOMap.IOReadable;
import IOMap.IOWritable;



/**
 * This is currently incomplete and unused. This will change as soon as a
 * propper floppy disk controller is implemented.
 * 
 * http://wiki.osdev.org/ISA_DMA
 * http://zet.aluzina.org/images/8/8c/Intel-8237-dma.pdf
 */
public final class Intel8237 implements HardwareComponent,
                                        IOReadable,
                                        IOWritable {
    
    /* ----------------------------------------------------- *
     * Status register bitmasks                              *
     * ----------------------------------------------------- */
    private final int STATUS_TC_0 = 0x01;
    private final int STATUS_TC_1 = 0x02;
    private final int STATUS_TC_2 = 0x04;
    private final int STATUS_TC_3 = 0x08;
    
    /* ----------------------------------------------------- *
     * (Single channel) Mask register bitmasks               *
     * ----------------------------------------------------- */
    private final int SINGLE_MASK_CHANNEL = 0x03;
    private final int SINGLE_MASK_ON = 0x04;
    
    /* ----------------------------------------------------- *
     * Registers                                             *
     * ----------------------------------------------------- */
    private final int[] m_page;    // Page Address Register (Channel 0-3)
    private final int[] m_address; // Start Address Register (Channel 0-3)
    private final int[] m_count;   // Count Register (Channel 0-3)
    private int m_status;          // Status Register
    private int m_mask;            // Single Channel Mask Register
    private final int[] m_mode;    // Mode Register
    private boolean m_flipFlop;    // Current status of the low/high byte flip-flop
    
    
    
    public Intel8237() {
        
        m_page = new int[4];
        m_address = new int[4];
        m_count = new int[4];
        m_mode = new int[4];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        Arrays.fill(m_page, 0x00);
        Arrays.fill(m_address, 0x00);
        Arrays.fill(m_count, 0x00);
        Arrays.fill(m_mode, 0x00);
        
        m_status = 0x00;
        m_mask = 0x00;
        m_flipFlop = false;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return new int[] {
            
            0x00, 0x01, 0x02, 0x03,
            0x04, 0x05, 0x06, 0x07,
            0x08, 0x0f, 0x81, 0x82,
            0x83, 0x87
        };
    }
    
    @Override
    public int readIO8(int port) {
        
        int data;
        
        switch(port) {
            
            // Start Address Register (Channel 0-3)
            case 0x00: case 0x02:
            case 0x04: case 0x06:
                data = m_address[port >> 1];
                if(m_flipFlop) data >>= 8;
                m_flipFlop ^= true;
                return data & 0xff;
                
            // Count Register (Channel 0-3)
            case 0x01: case 0x03:
            case 0x05: case 0x07:
                data = m_count[port >> 1];
                if(m_flipFlop) data >>= 8;
                m_flipFlop ^= true;
                return data & 0xff;
                
            // Status Register
            case 0x08:
                data = m_status;
                m_status &= ~(STATUS_TC_0 | STATUS_TC_1 | STATUS_TC_2 | STATUS_TC_3);
                return data;
                
            // Multi Channel Mask Register
            case 0x0f:
                return m_mask;
                
            // Page Address Register (Channel 0-3)
            case 0x87:
                return m_page[0];
                
            case 0x83:
                return m_page[1];
                
            case 0x81:
                return m_page[2];
                
            case 0x82:
                return m_page[3];
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return new int[] {
            
                0x00, 0x01, 0x02, 0x03,
                0x04, 0x05, 0x06, 0x07,
                0x08, 0x09, 0x0a, 0x0b,
                0x0c, 0x0d, 0x0e, 0x0f,
                0x81, 0x82, 0x83, 0x87
        };
    }
    
    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // Start Address Register [Channel 0-3]
            case 0x00: case 0x02:
            case 0x04: case 0x06:
                if(m_flipFlop)
                    m_address[port >> 1] = (m_address[port >> 1] & 0x00ff00ff) | (data << 8);
                else
                    m_address[port >> 1] = (m_address[port >> 1] & 0x00ffff00) | data;
                m_flipFlop ^= true;
                break;
                
            // Count Register [Channel 0-3]
            case 0x01: case 0x03:
            case 0x05: case 0x07:
                if(m_flipFlop)
                    m_count[port >> 1] = (m_count[port >> 1] & 0x00ff) | (data << 8);
                else
                    m_count[port >> 1] = (m_count[port >> 1] & 0xff00) | data;
                m_flipFlop ^= true;
                break;
                
            // Command Register
            case 0x08:
                // ...
                break;
                
            // Request Register
            case 0x09:
                // ...
                break;
                
            // Single Channel Mask Register
            case 0x0a:
                if((data & SINGLE_MASK_ON) != 0)
                    m_mask |= (1 << (data & SINGLE_MASK_CHANNEL));
                else
                    m_mask &= ~(1 << (data & SINGLE_MASK_CHANNEL));
                break;
                
            // Mode
            case 0x0b:
                m_mode[data & 0x03] = data;
                break;
                
            // Flip-Flop Reset
            case 0x0c:
                m_flipFlop = false;
                break;
                
            // Master Reset
            case 0x0d:
                m_flipFlop = false;
                m_status &= ~(STATUS_TC_0 | STATUS_TC_1 | STATUS_TC_2 | STATUS_TC_3);
                m_mask = 0x0f;
                break;
                
            // Mask Reset Register
            case 0x0e:
                m_mask = 0x00;
                break;
                
            // Multi Channel Mask Register
            case 0x0f:
                m_mask = data & 0x0f;
                break;
                
            // Page Address Register (Channel 0-3)
            case 0x87:
                m_page[0] = data;
                break;
                
            case 0x83:
                m_page[1] = data;
                break;
                
            case 0x81:
                m_page[2] = data;
                break;
                
            case 0x82:
                m_page[3] = data;
                break;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
}
