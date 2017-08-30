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
package Hardware.Mouse.Serial;

import Hardware.HardwareComponent;
import Hardware.Mouse.JPCMouseAdapter;
import Hardware.Mouse.Mouse;
import Hardware.Serial.COMPort;
import Hardware.Serial.COMPort.COMPortDevice;
import Hardware.Serial.UART16450;



public final class SerialMouse implements HardwareComponent,
                                          COMPortDevice,
                                          Mouse {
    
    /* ----------------------------------------------------- *
     * Reference to the serial communication port            *
     * ----------------------------------------------------- */
    private final JPCMouseAdapter m_mouseAdapter;
    
    /* ----------------------------------------------------- *
     * Reference to the serial communication port            *
     * ----------------------------------------------------- */
    private COMPort m_comPort;
    
    
    
    public SerialMouse() {
        
        m_mouseAdapter = new JPCMouseAdapter();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_mouseAdapter.reset();
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof UART16450) {
            
            // The serial mouse is atm always connected to COM1
            UART16450 uart = (UART16450)component;
            if(uart.getPortNumber() == 1)
                m_comPort = uart.getCOMPort(this);
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of COMPortDevice">
    
    @Override
    public void onDTRChanged(boolean dtr, boolean dtrOld) {
    }
    
    @Override
    public void onRTSChanged(boolean rts, boolean rtsOld) {
        
        if(rts && !rtsOld) {
            
            // Identify as a "Microsoft Mouse" with wheel support
            m_comPort.sendData('M');
            m_comPort.sendData('Z');
        }
    }
    
    @Override
    public void onDataReceived(int data) {
    }
    
    @Override
    public void onUpdateDevice() {
        
        if(m_mouseAdapter.hasChangedState()) {
        
            int dx = m_mouseAdapter.getDeltaX(-128, 127, false);
            int dy = m_mouseAdapter.getDeltaY(-128, 127, false);
            int dw = m_mouseAdapter.getDeltaWheel(-8, 7);

            int buttons = 0x00;
            if(m_mouseAdapter.isLeftButtonPressed())
                buttons |= 0x02;
            if(m_mouseAdapter.isMiddleButtonPressed())
                buttons |= 0x04;
            if(m_mouseAdapter.isRightButtonPressed())
                buttons |= 0x01;

            m_comPort.sendData(0x40 | ((dy & 0xc0) >> 4) | ((dx & 0xc0) >> 6) | ((buttons & 0x03) << 4));
            m_comPort.sendData(dx & 0x3f);
            m_comPort.sendData(dy & 0x3f);
            m_comPort.sendData(((buttons & 0x04) << 2) | (dw & 0x0f));
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
