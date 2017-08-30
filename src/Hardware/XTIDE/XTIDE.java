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
package Hardware.XTIDE;

import Hardware.ROM.Peripherals.XTIDE.XTIDEBios;
import Hardware.HardwareComponent;
import Hardware.IDE.IDE;
import Hardware.ROM.ROM;
import IOMap.IOReadable;
import IOMap.IOWritable;
import java.util.ArrayList;



public final class XTIDE implements HardwareComponent,
                                    IOReadable,
                                    IOWritable {
    
    /* ----------------------------------------------------- *
     * Register to store the high byte of a 16 bit PIO x-fer *
     * ----------------------------------------------------- */
    private int m_pioReg;
    
    /* ----------------------------------------------------- *
     * Reference to the IDE controller and XT-IDE bios       *
     * ----------------------------------------------------- */
    private final IDE m_ide;
    private final ROM m_bios;
    
    
    
    public XTIDE() {
        
        m_ide = new IDE(true, 5);
        m_bios = new XTIDEBios();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">

    @Override
    public ArrayList<HardwareComponent> getSubComponents() {
        
        ArrayList<HardwareComponent> l = new ArrayList<>();
        l.add(m_ide);
        l.add(m_bios);
        
        return l;
    }

    @Override
    public void reset() {
        
        m_pioReg = 0;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return new int[] {
            
            0x300, 0x301, 0x302, 0x303, 0x304,
            0x305, 0x306, 0x307, 0x308, 0x30e
        };
    }

    @Override
    public int readIO8(int port) {
        
        switch(port) {
            
            // Low byte of the PIO data
            case 0x300:
                m_pioReg = m_ide.readIO16(0x1f0);
                return m_pioReg & 0xff;
                
            // IDE register passthrough
            case 0x301: case 0x302: case 0x303:
            case 0x304: case 0x305: case 0x306:
            case 0x307:
                return m_ide.readIO8(0x1f0 | (port & 0x07));
                
            // High byte of the PIO data
            case 0x308:
                return (m_pioReg >>> 8) & 0xff;
                
            // IDE alternative status register
            case 0x30e:
                return m_ide.readIO8(0x3f6);
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }

    @Override
    public int[] getWritableIOPorts() {
        
        return new int[] {
            
            0x300, 0x301, 0x302, 0x303, 0x304,
            0x305, 0x306, 0x307, 0x308, 0x30e
        };
    }

    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // Low byte of the PIO data
            case 0x300:
                m_pioReg |= data;
                m_ide.writeIO16(0x1f0, m_pioReg);
                break;
                
            // IDE register passthrough
            case 0x301: case 0x302: case 0x303:
            case 0x304: case 0x305: case 0x306:
            case 0x307:
                m_ide.writeIO8(0x1f0 | (port & 0x07), data);
                break;
                
            // High byte of the PIO data
            case 0x308:
                m_pioReg = data << 8;
                break;
            
            // IDE alternative status register
            case 0x30e:
                m_ide.writeIO8(0x3f6, data);
                break;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
}
