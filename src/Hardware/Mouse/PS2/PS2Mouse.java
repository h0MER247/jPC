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
package Hardware.Mouse.PS2;

import Hardware.HardwareComponent;
import Hardware.Mouse.JPCMouseAdapter;
import Hardware.Mouse.Mouse;
import Hardware.PS2.PS2Controller;
import Hardware.PS2.PS2Port;
import Hardware.PS2.PS2Port.PS2PortDevice;



public final class PS2Mouse implements HardwareComponent,
                                       PS2PortDevice,
                                       Mouse {
    
    /* ----------------------------------------------------- *
     * Mouse state (most things do nothing atm)              *
     * ----------------------------------------------------- */
    private boolean m_streamMode;
    private boolean m_streamActive;
    private boolean m_scaling1To1;
    private int m_resolution;
    private int m_sampleRate;
    
    /* ----------------------------------------------------- *
     * Last received command from the ps/2 controller        *
     * ----------------------------------------------------- */
    private int m_command;
    private PS2Port m_ps2Port;
    
    /* ----------------------------------------------------- *
     * Mouse adapter which drives the mouse emulation        *
     * ----------------------------------------------------- */
    private final JPCMouseAdapter m_mouseAdapter;
    
    
    
    public PS2Mouse() {
        
        m_mouseAdapter = new JPCMouseAdapter();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_mouseAdapter.reset();
        
        m_command = -1;
        
        m_streamMode = true;
        m_streamActive = false;
        m_scaling1To1 = true;
        m_resolution = 4;
        m_sampleRate = 100;
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof PS2Controller)
            m_ps2Port = ((PS2Controller)component).getMousePort(this);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of PS2PortDevice">
    
    @Override
    public void onUpdateDevice() {
        
        if(!m_mouseAdapter.hasChangedState())
            return;
        
        if(m_streamMode && m_streamActive) {
            
            int dx = m_mouseAdapter.getDeltaX(-255, 255, false);
            int dy = m_mouseAdapter.getDeltaY(-255, 255, true);

            int data = 0xc8;
            if(dy < 0) { data |= 0x20; dy &= 0xff; }
            if(dx < 0) { data |= 0x10; dx &= 0xff; }
            if(m_mouseAdapter.isMiddleButtonPressed())
                data |= 0x04;
            if(m_mouseAdapter.isRightButtonPressed())
                data |= 0x02;
            if(m_mouseAdapter.isLeftButtonPressed())
                data |= 0x01;
            
            m_ps2Port.sendData(data);
            m_ps2Port.sendData(dx);
            m_ps2Port.sendData(dy);
        }
    }
    
    @Override
    public void onDataReceived(int data) {
        
        if(m_command == -1) {
            
            switch(data) {

                // Set scaling 1:1
                case 0xe6:
                    m_scaling1To1 = true;
                    m_ps2Port.sendData(0xfa);
                    break;
                    
                // Set scaling 1:2
                case 0xe7:
                    m_scaling1To1 = false;
                    m_ps2Port.sendData(0xfa);
                    break;
                
                // Set resolution
                case 0xe8:
                    m_ps2Port.sendData(0xfa);
                    m_command = data;
                    break;
                    
                // Status request
                case 0xe9:
                    m_ps2Port.sendData(0xfa);
                    m_ps2Port.sendData((m_streamMode ? 0x40 : 0x00) |
                                       (m_streamActive ? 0x20 : 0x00) |
                                       (m_scaling1To1 ? 0x10 : 0x00) |
                                       (m_mouseAdapter.isLeftButtonPressed() ? 0x04 : 0x00) |
                                       (m_mouseAdapter.isMiddleButtonPressed() ? 0x02 : 0x00) |
                                       (m_mouseAdapter.isRightButtonPressed() ? 0x01 : 0x00));
                    m_ps2Port.sendData(m_resolution);
                    m_ps2Port.sendData(m_sampleRate);
                    break;
                    
                // Get mouse ID
                case 0xf2:
                    m_ps2Port.sendData(0xfa);
                    m_ps2Port.sendData(0x00);
                    break;

                // Set sample rate
                case 0xf3:
                    m_ps2Port.sendData(0xfa);
                    m_command = data;
                    break;
                    
                // Enable packet streaming
                case 0xf4:
                    m_ps2Port.sendData(0xfa);
                    m_streamActive = true;
                    break;
                    
                // Disable packet streaming
                case 0xf5:
                    m_ps2Port.sendData(0xfa);
                    m_streamActive = false;
                    break;
                    
                // Reset
                case 0xff:
                    reset();
                    m_ps2Port.sendData(0xfa);
                    m_ps2Port.sendData(0xaa);
                    m_ps2Port.sendData(0x00);
                    break;
                    
                default:
                    throw new IllegalArgumentException(String.format("Unknown mouse command 0x%02X", data));
            }
        }
        else {
            
            switch(m_command) {
                
                // Set resolution
                case 0xe8:
                    m_resolution = data;
                    m_ps2Port.sendData(0xfa);
                    break;
                    
                // Set sample rate
                case 0xf3:
                    m_sampleRate = data;
                    m_ps2Port.sendData(0xfa);
                    break;
                    
                default:
                    throw new IllegalArgumentException();
            }
            m_command = -1;
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Mouse">
    
    @Override
    public JPCMouseAdapter getAdapter() {
        
        return m_mouseAdapter;
    }
    
    // </editor-fold>
}
