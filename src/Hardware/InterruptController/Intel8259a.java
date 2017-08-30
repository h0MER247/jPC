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
package Hardware.InterruptController;

import Hardware.HardwareComponent;
import IOMap.IOReadable;
import IOMap.IOWritable;



public final class Intel8259a implements HardwareComponent,
                                         IOReadable,
                                         IOWritable {
    
    /* ----------------------------------------------------- *
     * PIC Register                                          *
     * ----------------------------------------------------- */
    private int m_vector;
    private int m_imr;
    private int m_irr;
    private int m_isr;
    private boolean m_autoEOI;
    private final boolean m_isMaster;
    
    /* ----------------------------------------------------- *
     * Initialization Control Words (ICW)                    *
     * ----------------------------------------------------- */
    private int m_icwIndex;
    private boolean m_isInitialized;
    private boolean m_singlePIC;
    private boolean m_needICW4;
    
    /* ----------------------------------------------------- *
     * Operation Control Words (OCW)                         *
     * ----------------------------------------------------- */
    private boolean m_statusReadISR;
    
    
    
    public Intel8259a(boolean isMaster) {
        
        m_isMaster = isMaster;
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_isInitialized = false;
        m_statusReadISR = false;
        
        m_vector = 0x08;
        m_imr = 0xff;
        m_irr = 0x00;
        m_isr = 0x00;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return m_isMaster ? new int[] { 0x20, 0x21 } :
                            new int[] { 0xa0, 0xa1 };
    }
    
    @Override
    public int readIO8(int port) {
        
        switch(port) {
            
            // Command
            case 0x20:
            case 0xa0:
                return m_statusReadISR ? m_isr : m_irr;
                
            // Data
            case 0x21:
            case 0xa1:
                return m_imr;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return m_isMaster ? new int[] { 0x20, 0x21 } :
                            new int[] { 0xa0, 0xa1 };
    }
    
    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // Command
            case 0x20:
            case 0xa0:
                if((data & 0x10) == 0x00) {
                    
                    if((data & 0x08) == 0x00)
                        writeOCW(2, data);
                    else
                        writeOCW(3, data);
                }
                else {
                    
                    // Begin initialization
                    m_isInitialized = false;
                    m_icwIndex = 1;
                    
                    m_imr = 0x00;
                    m_statusReadISR = false;
                    
                    writeICW(m_icwIndex, data);
                }
                break;
                
            // Data
            case 0x21:
            case 0xa1:
                if(m_isInitialized)
                    writeOCW(1, data);
                else
                    writeICW(m_icwIndex, data);
                break;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Handling of the initialization control words">
    
    private void writeICW(int icw, int data) {
        
        switch(icw) {
            
            case 1:
                m_needICW4 = (data & 0x01) != 0;
                m_singlePIC = (data & 0x02) != 0;
                m_icwIndex = 2;
                break;
                
            case 2:
                m_vector = data & 0xF8;
                m_icwIndex = !m_singlePIC ? 3 : (m_needICW4 ? 4 : 5);
                break;
                
            case 3:
                m_icwIndex = m_needICW4 ? 4 : 5;
                break;
                
            case 4:
                m_autoEOI = (data & 0x02) != 0;
                m_icwIndex = 5;
                break;
        }
        
        m_isInitialized = m_icwIndex == 5;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Handling of the operation control words">
    
    private void writeOCW(int ocw, int data) {
        
        switch(ocw) {
            
            case 1:
                m_imr = data;
                break;
                
            case 2:
                switch((data >> 5) & 0x07) {
                    
                    // Automatically clear the highest priority bit in the isr
                    case 0x01:
                        for(int i = 0; i < 8; i++) {
                            
                            if((m_isr & (1 << i)) != 0) {
                                
                                m_isr ^= 1 << i;
                                break;
                            }
                        }
                        break;
                      
                    // No operation
                    case 0x02:
                        break;
                      
                    // Clear a selected interrupt from the isr
                    case 0x03:
                        m_isr &= ~(1 << (data & 0x07));
                        break;
                        
                    // TODO: Implement other commands (0x04-0x07)
                    default:
                        //throw new IllegalArgumentException(String.format("Unimplemented OCW2 command 0x%02x, data: 0x%02x", (data >> 5) & 0x07, data));
                        System.err.printf("Unimplemented OCW2 command 0x%02x, data: 0x%02x", (data >> 5) & 0x07, data);
                        break;
                }
                break;
                
            case 3:
                if((data & 0x02) != 0)
                    m_statusReadISR = (data & 0x01) != 0;
                //if((data & 0x04) != 0)
                    //throw new IllegalArgumentException(String.format("%s: Unimplemented OCW3 polling command", getClass().getName()));
                //if((data & 0x40) != 0)
                    //throw new IllegalArgumentException(String.format("%s: Unimplemented OCW3 special mask mode", getClass().getName()));
                break;
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Setting, getting and clearing of interrupts">
    
    public void setInterrupt(int irq) {
        
        m_irr |= 1 << irq;
    }
    
    public void clearInterrupt(int irq) {
        
        m_irr &= ~(1 << irq);
    }
    
    public boolean isPending() {
        
        return (m_irr & ~m_imr) != 0;
    }
    
    public int getInterrupt() {
        
        int irq = m_irr & ~m_imr;
        
        // Find highest priority interrupt
        for(int i = 0; i < 8; i++) {
            
            int bit = 1 << i;
            if((irq & bit) != 0) {
                
                // Set the ISR bit (TODO: Is this correct?)
                if(!m_autoEOI)
                    m_isr |= bit;
                
                // Reset the IRR bit
                m_irr ^= bit;
                
                return m_vector + i;
            }
        }
        
        throw new IllegalStateException("There was no interrupt");
    }
    
    // </editor-fold>
}
