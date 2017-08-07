/*
 * Copyright (C) 2017 homer
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
package Hardware.Mouse;

import Hardware.HardwareComponent;
import Hardware.Serial.COMPort;
import Hardware.Serial.UART16450;



public class SerialMouse extends Mouse
                         implements HardwareComponent {
    
    /* ----------------------------------------------------- *
     * Reference to the serial communication port            *
     * ----------------------------------------------------- */
    private COMPort m_comPort;
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        resetMouse();
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof UART16450) {
            
            // The serial mouse is atm always connected to COM1
            UART16450 uart = (UART16450)component;
            if(uart.getPortNumber() == 1) {
                
                m_comPort = uart.getCOMPort(
                        
                        this::onUpdateDevice,
                        null,
                        this::onRTSChanged,
                        null
                );
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Callbacks for the serial communication">
    
    private void onRTSChanged(boolean rts, boolean rtsOld) {
        
        if(rts && !rtsOld) {
            
            // Identify as a "Microsoft Mouse" with wheel support
            m_comPort.sendData('M');
            m_comPort.sendData('Z');
        }
    }
    
    private void onUpdateDevice() {
        
        if(hasMouseChangedState()) {
            
            int dx = Math.min(Math.max(getDeltaX(), -128), 127);
            int dy = Math.min(Math.max(getDeltaY(), -128), 127);
            int dw = Math.min(Math.max(getDeltaWheel(), -8), 7);
            
            int buttons = 0x00;
            if(isLeftButtonPressed())
                buttons |= 0x02;
            if(isMiddleButtonPressed())
                buttons |= 0x04;
            if(isRightButtonPressed())
                buttons |= 0x01;
            
            m_comPort.sendData(0x40 | ((dy & 0xc0) >> 4) | ((dx & 0xc0) >> 6) | ((buttons & 0x03) << 4));
            m_comPort.sendData(dx & 0x3f);
            m_comPort.sendData(dy & 0x3f);
            m_comPort.sendData(((buttons & 0x04) << 2) | (dw & 0x0f));
        }
    }
    
    // </editor-fold>
}
