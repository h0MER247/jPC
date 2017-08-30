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
package Hardware.Serial;

import Hardware.HardwareComponent;
import Hardware.InterruptController.PICs;
import Hardware.Serial.COMPort.COMPortDevice;
import IOMap.IOReadable;
import IOMap.IOWritable;
import Scheduler.Schedulable;
import Scheduler.Scheduler;
import java.util.LinkedList;



/**
 * @see ftp://ftp.altera.com/pub/lit_req/document/ds/ds16450.pdf
 * 
 * I don't like the implementation of COMPort - same problem as with PS2Port
 * I really have to find a better way to implement the communication between
 * the device and the uart...
 */
public final class UART16450 implements HardwareComponent,
                                        IOReadable,
                                        IOWritable,
                                        Schedulable {
    
    /* ----------------------------------------------------- *
     * Frequency of the UART                                 *
     * ----------------------------------------------------- */
    private final float UART_FREQUENCY = 1843200.0f;
    
    /* ----------------------------------------------------- *
     * Serial port number and its irq                        *
     * ----------------------------------------------------- */
    private final int m_serialPortNum;
    private final int m_serialIRQ;
    
    /* ----------------------------------------------------- *
     * Line status register                                  *
     * ----------------------------------------------------- */
    private final int LSR_RECEIVER_DATA_READY = 0x01;
    private final int LSR_OVERRUN_ERROR = 0x02;
    private final int LSR_PARITY_ERROR = 0x04;
    private final int LSR_FRAMING_ERROR = 0x08;
    private final int LSR_BREAK_INTERRUPT = 0x10;
    private final int LSR_TRANSMITTER_HOLDING_REG_EMPTY = 0x20;
    private final int LSR_TRANSMITTER_EMPTY = 0x40;
    private int m_lsr;
    
    /* ----------------------------------------------------- *
     * Line control register                                 *
     * ----------------------------------------------------- */
    private final int LCR_WORD_LENGTH_MASK = 0x03;
    private final int LCR_STOP_BIT_CONTROL = 0x04;
    private final int LCR_PARITY_ENABLED = 0x08;
    private final int LCR_DIVISOR_ACCESS = 0x80;
    private int m_lcr;
    
    /* ----------------------------------------------------- *
     * Divisor latch register                                *
     * ----------------------------------------------------- */
    private int m_dlr;
    private boolean m_isDLRAccessible;
    
    /* ----------------------------------------------------- *
     * Modem control register                                *
     * ----------------------------------------------------- */
    private final int MCR_DATA_TERMINAL_READY = 0x01;
    private final int MCR_REQUEST_TO_SEND = 0x02;
    private final int MCR_INTERRUPT_ENABLE = 0x08;
    private int m_mcr;
    
    /* ----------------------------------------------------- *
     * Modem status register                                 *
     * ----------------------------------------------------- */
    private final int MSR_DELTA_CLEAR_TO_SEND = 0x01;
    private final int MSR_DELTA_DATA_SET_READY = 0x02;
    private final int MSR_TRAILING_EDGE_RING_INDICATOR = 0x04;
    private final int MSR_DELTA_DATA_CARRIER_DETECT = 0x08;
    private final int MSR_DELTA_BITS = 0x0f;
    private final int MSR_CLEAR_TO_SEND = 0x10;
    private final int MSR_DATA_SET_READY = 0x20;
    private final int MSR_RING_INDICATOR = 0x40;
    private final int MSR_DATA_CARRIER_DETECT = 0x80;
    private final int MSR_STATUS_BITS = 0xf0;
    private int m_msr;
    private int m_msrOld;
    
    /* ----------------------------------------------------- *
     * Interrupt handling                                    *
     * ----------------------------------------------------- */
    private final int IRQ_MODEM_STATUS = 0x08;
    private final int IRQ_TRANSMITTER_HOLDING_REG_EMPTY = 0x02;
    private final int IRQ_RECEIVER_DATA_AVAILABLE = 0x01;
    private final int IRQ_RECEIVER_LINE_STATUS = 0x04;
    private final int IIR_MODEM_STATUS = 0x00;
    private final int IIR_NO_INTERRUPT = 0x01;
    private final int IIR_TRANSMITTER_HOLDING_REG_EMPTY = 0x02;
    private final int IIR_RECEIVER_DATA_AVAILABLE = 0x04;
    private final int IIR_RECEIVER_LINE_STATUS = 0x06;
    private int m_iir;
    private int m_ier;
    private int m_irq;
    
    /* ----------------------------------------------------- *
     * Scratchpad                                            *
     * ----------------------------------------------------- */
    private int m_scratchPad;
    
    /* ----------------------------------------------------- *
     * Data transfer register                                *
     * ----------------------------------------------------- */
    private int m_thr;
    private int m_rbr;
    
    /* ----------------------------------------------------- *
     * Scheduling                                            *
     * ----------------------------------------------------- */
    private float m_baseFrequency;
    private int m_cyclesRemaining;
    private int m_cyclesSerial;
    
    /* ----------------------------------------------------- *
     * The serial communication port of this UART            *
     * ----------------------------------------------------- */
    private class COMPortImpl implements COMPort {
        
        private final LinkedList<Integer> m_buffer = new LinkedList<>();
        private COMPortDevice m_device = null;
        
        private COMPortImpl init(COMPortDevice device) { m_device = device; return this; }
        private void reset() { m_buffer.clear(); }
        
        @Override public void sendData(int data) { m_buffer.add(data); }
        @Override public void setDCD(boolean dcd) { UART16450.this.setDCD(dcd); }
        @Override public void setDSR(boolean dsr) { UART16450.this.setDSR(dsr); }
        @Override public void setRI(boolean ri) { UART16450.this.setRI(ri); }
        @Override public void setCTS(boolean cts) { UART16450.this.setCTS(cts); }
        
        private boolean isDataAvailable() { if(m_device != null) m_device.onUpdateDevice(); return !m_buffer.isEmpty(); }
        private int getData() { return m_buffer.pop(); }
        private void setDTR(boolean dtr, boolean dtrOld) { if(m_device != null) m_device.onDTRChanged(dtr, dtrOld); }
        private void setRTS(boolean rts, boolean rtsOld) { if(m_device != null) m_device.onRTSChanged(rts, rtsOld); }
        private void receiveData(int data) { if(m_device != null) m_device.onDataReceived(data); }
    };
    private final COMPortImpl m_comPort;
    
    /* ----------------------------------------------------- *
     * Reference to the interrupt controller                 *
     * ----------------------------------------------------- */
    private PICs m_pic;
    
    
    
    public UART16450(int portNum) {
        
        if(portNum < 0 || portNum > 4)
            throw new IllegalArgumentException("Invalid port number specified");
        
        m_serialPortNum = portNum;
        m_serialIRQ = (portNum & 0x01) != 0 ? 4 : 3;
        
        m_comPort = new COMPortImpl();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_dlr = 0x0001;
        m_isDLRAccessible = false;
        
        m_ier = 0x00;
        m_iir = 0x01;
        m_irq = 0x00;
        
        m_lsr = LSR_TRANSMITTER_HOLDING_REG_EMPTY | LSR_TRANSMITTER_EMPTY;
        m_lcr = 0x00;
        
        m_msr = m_msrOld = 0x00;
        m_mcr = 0x00;
        
        m_scratchPad = 0x00;
        
        m_thr = -1;
        m_rbr = 0;
        
        m_comPort.reset();
    }
    
    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof PICs)
            m_pic = (PICs)component;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        switch(m_serialPortNum) {
            
            case 1: return new int[] { 0x3f8, 0x3f9, 0x3fa, 0x3fb, 0x3fc, 0x3fd, 0x3fe, 0x3ff };
            case 2: return new int[] { 0x2f8, 0x2f9, 0x2fa, 0x2fb, 0x2fc, 0x2fd, 0x2fe, 0x2ff };
            case 3: return new int[] { 0x3e8, 0x3e9, 0x3fa, 0x3eb, 0x3ec, 0x3fd, 0x3fe, 0x3ef };
            case 4: return new int[] { 0x2e8, 0x2e9, 0x2fa, 0x2eb, 0x2ec, 0x2fd, 0x2fe, 0x2ef };
            
            default:
                throw new IllegalStateException();
        }
    }
    
    @Override
    public int readIO8(int port) {
        
        int res;
        
        switch(port) {
            
            // Divisor Latch Low Byte /  Receiver Buffer Register
            case 0x2e8: case 0x2f8:
            case 0x3e8: case 0x3f8:
                if(m_isDLRAccessible) {
                    
                    return (m_dlr >>> 8) & 0xff;
                }
                else {
                    
                    m_lsr &= ~LSR_RECEIVER_DATA_READY;
                    clearIRQ(IRQ_RECEIVER_DATA_AVAILABLE);
        
                    return m_rbr;
                }
                
            // Divisor Latch High Byte / Interrupt Enable Register
            case 0x2e9: case 0x2f9:
            case 0x3e9: case 0x3f9:
                if(m_isDLRAccessible)
                    return m_dlr & 0xff;
                else
                    return m_ier;
                
            // Interrupt Identification Register
            case 0x2ea: case 0x2fa:
            case 0x3ea: case 0x3fa:
                res = m_iir;
                if(res == IIR_TRANSMITTER_HOLDING_REG_EMPTY)
                    clearIRQ(IRQ_TRANSMITTER_HOLDING_REG_EMPTY);
                return res;
            
            // Line control register
            case 0x2eb: case 0x2fb:
            case 0x3eb: case 0x3fb:
                return m_lcr;
                
            // Modem control register
            case 0x2ec: case 0x2fc:
            case 0x3ec: case 0x3fc:
                return m_mcr;
                
            // Line status register
            case 0x2ed: case 0x2fd:
            case 0x3ed: case 0x3fd:
                res = m_lsr;
                m_lsr &= ~(LSR_OVERRUN_ERROR |
                           LSR_PARITY_ERROR |
                           LSR_FRAMING_ERROR |
                           LSR_BREAK_INTERRUPT);
                clearIRQ(IRQ_RECEIVER_LINE_STATUS);
                return res;
                
            // Modem status register
            case 0x2ee: case 0x2fe:
            case 0x3ee: case 0x3fe:
                res = m_msr;
                m_msrOld = m_msr & MSR_STATUS_BITS;
                m_msr &= ~MSR_DELTA_BITS;
                clearIRQ(IRQ_MODEM_STATUS);
                return res;
                
            // Scratch pad
            case 0x2ef: case 0x2ff:
            case 0x3ef: case 0x3ff:
                return m_scratchPad;
            
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }

    @Override
    public int[] getWritableIOPorts() {
        
        switch(m_serialPortNum) {
            
            case 1: return new int[] { 0x3f8, 0x3f9, 0x3fa, 0x3fb, 0x3fc, 0x3fd, 0x3fe, 0x3ff };
            case 2: return new int[] { 0x2f8, 0x2f9, 0x2fa, 0x2fb, 0x2fc, 0x2fd, 0x2fe, 0x2ff };
            case 3: return new int[] { 0x3e8, 0x3e9, 0x3fa, 0x3eb, 0x3ec, 0x3fd, 0x3fe, 0x3ef };
            case 4: return new int[] { 0x2e8, 0x2e9, 0x2fa, 0x2eb, 0x2ec, 0x2fd, 0x2fe, 0x2ef };
            
            default:
                throw new IllegalStateException();
        }
    }

    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // Divisor Latch Low Byte / Transmitter Holding Register
            case 0x2e8: case 0x2f8:
            case 0x3e8: case 0x3f8:
                if(m_isDLRAccessible) {
                    
                    m_dlr = (m_dlr & 0xff00) | data;
                }
                else {
                    
                    m_thr = data;
        
                    m_lsr &= ~(LSR_TRANSMITTER_HOLDING_REG_EMPTY |
                               LSR_TRANSMITTER_EMPTY);
                    clearIRQ(IRQ_TRANSMITTER_HOLDING_REG_EMPTY);
                }
                break;
                
            // Interrupt Enable Register / Divisor Latch High Byte
            case 0x2e9: case 0x2f9:
            case 0x3e9: case 0x3f9:
                if(m_isDLRAccessible) {
                    
                    m_dlr = (m_dlr & 0x00ff) | (data << 8);
                    updateTimings();
                }
                else {
                    
                    m_ier = data & 0x0f;
                    updateIIR();
                }
                break;
                
            // Unwritable
            case 0x2ea: case 0x2fa:
            case 0x3ea: case 0x3fa:
                break;
                
            // Line control register
            case 0x2eb: case 0x2fb:
            case 0x3eb: case 0x3fb:
                m_lcr = data;
                m_isDLRAccessible = (data & LCR_DIVISOR_ACCESS) != 0;
                updateTimings();
                break;
                
            // Modem control register
            case 0x2ec: case 0x2fc:
            case 0x3ec: case 0x3fc:
                if(((data ^ m_mcr) & MCR_DATA_TERMINAL_READY) != 0) {
                    
                    m_comPort.setDTR(
                            
                        (data & MCR_DATA_TERMINAL_READY) != 0,
                        (m_mcr & MCR_DATA_TERMINAL_READY) != 0
                    );
                }
                if(((data ^ m_mcr) & MCR_REQUEST_TO_SEND) != 0) {
                    
                    m_comPort.setRTS(
                            
                        (data & MCR_REQUEST_TO_SEND) != 0,
                        (m_mcr & MCR_REQUEST_TO_SEND) != 0
                    );
                }
                m_mcr = data;
                break;
                
            // Unwritable
            case 0x2ed: case 0x2fd:
            case 0x3ed: case 0x3fd:
                break;
                
            // Unwritable
            case 0x2ee: case 0x2fe:
            case 0x3ee: case 0x3fe:
                break;
                
            // Scratch pad
            case 0x2ef: case 0x2ff:
            case 0x3ef: case 0x3ff:
                m_scratchPad = data;
                break;
                
            
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Schedulable">
    
    @Override
    public void setBaseFrequency(float baseFrequency) {
        
        m_baseFrequency = baseFrequency;
        updateTimings();
    }

    @Override
    public void updateClock(int cycles) {
        
        m_cyclesRemaining -= cycles;
        if(m_cyclesRemaining <= 0) {
            
            m_cyclesRemaining += m_cyclesSerial;
            
            
            // Transmit data
            if(m_thr != -1) {
                
                m_comPort.receiveData(m_thr);
                m_thr = -1;
                
                m_lsr |= LSR_TRANSMITTER_HOLDING_REG_EMPTY |
                         LSR_TRANSMITTER_EMPTY;
                requestIRQ(IRQ_TRANSMITTER_HOLDING_REG_EMPTY);
            }
            
            // Receive data
            if(m_comPort.isDataAvailable()) {
                
                m_rbr = m_comPort.getData();
                
                if((m_lsr & LSR_RECEIVER_DATA_READY) != 0) {
                    
                    m_lsr |= LSR_OVERRUN_ERROR;
                    requestIRQ(IRQ_RECEIVER_LINE_STATUS);
                }
                m_lsr |= LSR_RECEIVER_DATA_READY;
                requestIRQ(IRQ_RECEIVER_DATA_AVAILABLE);
            }
        }
    }
    
    private void updateTimings() {
        
        if(m_dlr != 0) {
            
            // Calculate baud rate
            float baudRate = UART_FREQUENCY / (16.0f * m_dlr);

            // Calculate the transmission time for one byte based on the baud rate
            // and the total number of bits
            int startBits = 1;
            int dataBits = 5 + (m_lcr & LCR_WORD_LENGTH_MASK);
            int stopBits = ((m_lcr & LCR_STOP_BIT_CONTROL) != 0) ? 2 : 1;
            int parityBits = ((m_lcr & LCR_PARITY_ENABLED) != 0) ? 1 : 0;

            float timeForOneByte = (startBits + dataBits + stopBits + parityBits) / (baudRate * 8.0f);
            
            // Calculate cycles
            m_cyclesSerial = Scheduler.toFixedPoint(m_baseFrequency * timeForOneByte);
            m_cyclesRemaining %= m_cyclesSerial;
        }
    }
    
    // </editor-fold>
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Methods for serial communication port devices">
    
    public int getPortNumber() {
        
        return m_serialPortNum;
    }
    
    public COMPort getCOMPort(COMPortDevice device) {
        
        return m_comPort.init(device);
    }
    
    private void setCTS(boolean cts) {
        
        if(cts) m_msr |= MSR_CLEAR_TO_SEND;
        else m_msr &= ~MSR_CLEAR_TO_SEND;
        
        if(cts ^ ((m_msrOld & MSR_CLEAR_TO_SEND) != 0)) {
            
            m_msr |= MSR_DELTA_CLEAR_TO_SEND;
            requestIRQ(IRQ_MODEM_STATUS);
        }
    }
    
    private void setDSR(boolean dsr) {
        
        if(dsr) m_msr |= MSR_DATA_SET_READY;
        else m_msr &= ~MSR_DATA_SET_READY;
        
        if(dsr ^ ((m_msrOld & MSR_DATA_SET_READY) != 0)) {
            
            m_msr |= MSR_DELTA_DATA_SET_READY;
            requestIRQ(IRQ_MODEM_STATUS);
        }
    }
    
    private void setRI(boolean ri) {
        
        if(ri) m_msr |= MSR_RING_INDICATOR;
        else m_msr &= ~MSR_RING_INDICATOR;
        
        if(ri && ((m_msrOld & MSR_RING_INDICATOR) == 0)) {
            
            m_msr |= MSR_TRAILING_EDGE_RING_INDICATOR;
            requestIRQ(IRQ_MODEM_STATUS);
        }
    }
    
    private void setDCD(boolean dcd) {
        
        if(dcd) m_msr |= MSR_DATA_CARRIER_DETECT;
        else m_msr &= ~MSR_DATA_CARRIER_DETECT;
        
        if(dcd ^ ((m_msrOld & MSR_DATA_CARRIER_DETECT) != 0)) {
            
            m_msr |= MSR_DELTA_DATA_CARRIER_DETECT;
            requestIRQ(IRQ_MODEM_STATUS);
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Interrupt logic">
    
    private void clearIRQ(int irq) {
        
        m_irq &= ~irq;
        updateIIR();
    }
    
    private void requestIRQ(int irq) {
        
        m_irq |= irq;
        updateIIR();
    }
    
    private void updateIIR() {
        
        int irqs;
        if((irqs = m_irq & m_ier) != 0) {
        
            if((irqs & IRQ_RECEIVER_LINE_STATUS) != 0)
                m_iir = IIR_RECEIVER_LINE_STATUS;
            
            else if((irqs & IRQ_RECEIVER_DATA_AVAILABLE) != 0)
                m_iir = IIR_RECEIVER_DATA_AVAILABLE;
            
            else if((irqs & IRQ_TRANSMITTER_HOLDING_REG_EMPTY) != 0)
                m_iir = IIR_TRANSMITTER_HOLDING_REG_EMPTY;
            
            else if((irqs & IRQ_MODEM_STATUS) != 0)
                m_iir = IIR_MODEM_STATUS;
            
            
            if((m_mcr & MCR_INTERRUPT_ENABLE) != 0)
                m_pic.setInterrupt(m_serialIRQ);
        }
        else {
            
            m_iir = IIR_NO_INTERRUPT;
        }
    }
    
    // </editor-fold>
}
