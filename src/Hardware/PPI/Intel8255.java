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
package Hardware.PPI;

import Hardware.HardwareComponent;
import Hardware.InterruptController.PICs;
import IOMap.IOReadable;
import IOMap.IOWritable;
import Hardware.Speaker.Speaker;
import Hardware.Timer.Intel8253;



public final class Intel8255 implements HardwareComponent,
                                        IOReadable,
                                        IOWritable {
    
    /* ----------------------------------------------------- *
     * DIP switch settings                                   *
     * ----------------------------------------------------- */
    private final int m_dipSwitch = 0b01001100; // Don't loop on post, no coprocessor installed, 640kb ram, reserved
                                                // display mode (for vga), two floppy drives
    
    /* ----------------------------------------------------- *
     * PPI registers                                         *
     * ----------------------------------------------------- */
    private int m_portA;
    private int m_portB;
    private int m_portC;
    
    /* ----------------------------------------------------- *
     * References to the PIC, PIT and speaker                *
     * ----------------------------------------------------- */
    private PICs m_pic;
    private Intel8253 m_pit;
    private Speaker m_speaker;
    
    
    
    public Intel8255() {
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_portA = 0x00;
        m_portB = 0x00;
        m_portC = 0x00;
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof PICs)
            m_pic = (PICs)component;
        
        if(component instanceof Intel8253)
            m_pit = (Intel8253)component;
        
        if(component instanceof Speaker)
            m_speaker = (Speaker)component;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return new int[] { 0x60, 0x61, 0x62, 0x63 };
    }
    
    @Override
    public int readIO8(int port) {
        
        switch(port) {
            
            // Port A
            case 0x60:
                return m_portA;
            
            // Port B
            case 0x61:
                return m_portB;
            
            // Port C
            case 0x62:
                return m_portC;
            
            // Control word
            case 0x63:
                return 0x99;
                
            
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return new int[] { 0x61 };
    }

    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // Port B
            case 0x61:
                m_portB = data;
                
                // Gate of timer 2
                m_pit.setCounterGate(2, (data & 0x01) != 0);
                
                // Speaker data
                m_speaker.setData((data & 0x02) != 0);
                
                // Read high / low switches
                if((data & 0x08) != 0)
                    m_portC = m_dipSwitch >>> 4;
                else
                    m_portC = m_dipSwitch & 0x0f;
                
                // Clear keyboard
                if((data & 0x80) != 0)
                    clearKeyboardData();
                break;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Methods that get used by the keyboard implementation">
    
    public boolean isAcceptingKeyboardData() {
        
        return m_portA == 0x00;
    }
    
    private void clearKeyboardData() {
        
        m_portA = 0x00;
        m_pic.clearInterrupt(1);
    }
    
    public void setKeyboardData(int data) {
        
        m_portA = data;
        m_pic.setInterrupt(1);
    }
    
    // </editor-fold>
}
