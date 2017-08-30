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
package Hardware.PS2;

import Hardware.CPU.Intel80386.MMU.MMU;
import Hardware.HardwareComponent;
import Hardware.InterruptController.PICs;
import Hardware.PS2.PS2Port.PS2PortDevice;
import IOMap.IOReadable;
import IOMap.IOWritable;
import Scheduler.Schedulable;
import Scheduler.Scheduler;
import java.util.LinkedList;
import java.util.Queue;



/**
 * I don't like the implementation of PS2Port. I have to find a better way to
 * implement the communication between controller and port.
 * 
 *  - Also unconnected devices are not properly handled
 *  - As correct cycle counts are not implemented in the i386 emulation
 *    drivers like keyboard.sys will break and present something like "^@^@^@"
 *    after booting into DOS. This problem will solve itself after correct
 *    cycle counting is implemented.
 */
public final class PS2Controller implements HardwareComponent,
                                            Schedulable,
                                            IOReadable,
                                            IOWritable {
    
    /* ----------------------------------------------------- *
     * Status register                                       *
     * ----------------------------------------------------- */
    private final int STATUS_OFULL = 0x01;
    private final int STATUS_SYSFLAG = 0x04;
    private final int STATUS_CMD = 0x08;
    private final int STATUS_KEYLOCK = 0x10;
    private final int STATUS_AUX = 0x20;
    private final int STATUS_TIME_OUT = 0x40;
    private int m_status;
    
    /* ----------------------------------------------------- *
     * Command register                                      *
     * ----------------------------------------------------- */
    private int m_command;
    
    /* ----------------------------------------------------- *
     * Data buffers                                          *
     * ----------------------------------------------------- */
    private final LinkedList<Integer> m_controllerFIFO;
    private int m_data;
    private int m_irq;
    
    /* ----------------------------------------------------- *
     * PS2 controller ram                                    *
     * ----------------------------------------------------- */
    private final int CTRL_KEYDISABLED = 0x10;
    private final int CTRL_AUXDISABLED = 0x20;
    private final int[] m_ram;
    
    /* ----------------------------------------------------- *
     * PS2 devices                                           *
     * ----------------------------------------------------- */
    private class PS2PortImpl implements PS2Port {
        
        private final Queue<Integer> m_buffer = new LinkedList<>();
        private PS2PortDevice m_device = null;
        
        private PS2PortImpl init(PS2PortDevice device) {
            
            m_device = device;
            return this;
        }
        private void reset() { m_buffer.clear(); }
        
        @Override public void sendData(int data) { m_buffer.add(data); }
        
        private boolean isDataAvailable() { if(m_device != null) m_device.onUpdateDevice(); return !m_buffer.isEmpty(); }
        private int getData() { return m_buffer.poll(); }
        private void receiveData(int data) { if(m_device != null) m_device.onDataReceived(data); }
    }
    private final PS2PortImpl m_keyPort;
    private final PS2PortImpl m_mousePort;
    
    /* ----------------------------------------------------- *
     * Scheduling                                            *
     * ----------------------------------------------------- */
    private int m_cyclesRemaining;
    private int m_cycles;
    
    private PICs m_pics;
    private MMU m_mmu;
    
    
    
    public PS2Controller() {
        
        m_keyPort = new PS2PortImpl();
        m_mousePort = new PS2PortImpl();
        
        m_controllerFIFO = new LinkedList<>();
        m_ram = new int[32];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_status = STATUS_KEYLOCK | STATUS_CMD;
        m_irq = 0;
        
        for(int i = 0; i < 32; i++)
            m_ram[i] = 0x00;
        
        m_controllerFIFO.clear();
        m_keyPort.reset();
        m_mousePort.reset();
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof PICs)
            m_pics = (PICs)component;
        
        if(component instanceof MMU)
            m_mmu = (MMU)component;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return new int[] { 0x60, 0x64 };
    }
    
    @Override
    public int readIO8(int port) {
        
        switch(port) {
            
            // Data register
            case 0x60:
                return readData();
                
            // Status register
            case 0x64:
                return readStatus();
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return new int[] { 0x60, 0x64 };
    }
    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // Data register
            case 0x60:
                writeData(data);
                break;
                
            // Command register
            case 0x64: 
                writeCommand(data);
                break;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Schedulable">

    @Override
    public void setBaseFrequency(float baseFrequency) {
        
        m_cycles = m_cyclesRemaining = Scheduler.toFixedPoint(baseFrequency / 512.0f);
    }
    
    @Override
    public void updateClock(int cycles) {
        
        m_cyclesRemaining -= cycles;
        
        while(m_cyclesRemaining <= 0) {
            
            if(m_irq == 0) {
                
                if(m_controllerFIFO.size() > 0) {
                    
                    setOutputData(m_controllerFIFO.poll(), false);
                }
                else if(m_mousePort.isDataAvailable()) {
                    
                    setOutputData(m_mousePort.getData(), true);
                }
                else if(isKeyboardEnabled() && m_keyPort.isDataAvailable()) {
                    
                    setOutputData(m_keyPort.getData(), false);
                }
            }
            
            m_cyclesRemaining += m_cycles;
        }
    }
    
    // </editor-fold>
    
    
    /* ---------------------------------------------------------------------- *
     *                  [ Internal PS/2 Controller Handling ]                 *
     * ---------------------------------------------------------------------- */
    
    private void writeData(int data) {
        
        if((m_status & STATUS_CMD) == 0) {
        
            // Write to device on first ps/2 port
            m_keyPort.receiveData(data);
        }
        else {
            
            m_status &= ~STATUS_CMD;
            
            if(m_command >= 0x60 && m_command <= 0x7f) {
                
                // Write to internal ram
                m_ram[m_command & 0x1f] = data;
            }
            else if(m_command >= 0xf0 && m_command <= 0xff) {
                
                // Pulse the output line... eh ok :) ?
            }
            else {
                
                switch(m_command) {
                    
                    // Write to controller output
                    case 0xd1:
                        m_mmu.setA20Gate((data & 0x02) != 0);
                        break;
                        
                    // Write to device on second ps/2 port
                    case 0xd4:
                        m_mousePort.receiveData(data);
                        break;
                        
                    default:
                        throw new IllegalArgumentException(String.format("Unhandled ps/2 command %02xh with data %02xh", m_command, data));
                }
            }
        }
    }
    
    private void writeCommand(int command) {
        
        m_command = command;
        m_status &= ~STATUS_CMD;
        
        // Read from internal ram
        if(command >= 0x20 && command <= 0x3f) {
            
            int index = command & 0x1f;
            if(index == 0)
                m_controllerFIFO.add(m_ram[0] | (m_status & STATUS_SYSFLAG));
            else
                m_controllerFIFO.add(m_ram[index]);
        }
        
        // Write to internal ram
        else if(command >= 0x60 && command <= 0x7f) {
            
            m_status |= STATUS_CMD;
        }
        
        // Pulse output line
        else if(command >= 0xf0 && command <= 0xff) {
            
            m_status |= STATUS_CMD;
        }
        
        else {
            
            switch(command) {
                
                // Read firmware
                case 0xa1:
                    m_controllerFIFO.add(0x00);
                    break;
                    
                // Disable second ps/2 port
                case 0xa7:
                    m_ram[0] |= CTRL_AUXDISABLED;
                    break;
                    
                // Enable second ps/2 port
                case 0xa8:
                    m_ram[0] &= ~CTRL_AUXDISABLED;
                    break;
                    
                // Test mouse port
                case 0xa9:
                    m_controllerFIFO.add(0x00);
                    break;
                
                // Test ps2 controller
                case 0xaa:
                    m_status |= STATUS_SYSFLAG;
                    m_ram[0x00] |= 0x04;
                    m_controllerFIFO.add(0x55);
                    break;
                    
                // Test first ps/2 port
                case 0xab:
                    m_controllerFIFO.add(0x00);
                    break;
                    
                // Disable first ps/2 port
                case 0xad:
                    m_ram[0] |= CTRL_KEYDISABLED;
                    break;
                    
                // Enable first ps/2 port
                case 0xae:
                    m_ram[0] &= ~CTRL_KEYDISABLED;
                    break;
                    
                // Read input port
                case 0xc0:
                    m_controllerFIFO.add(0xb0);
                    break;
                    
                // Write to controller output
                case 0xd1:
                    m_status |= STATUS_CMD;
                    break;
                    
                // Write to device on second ps/2 port
                case 0xd4:
                    m_status |= STATUS_CMD;
                    break;
                    
                // ??? No idea ???
                case 0xbc:
                case 0xc9:
                    break;
                    
                default:
                    throw new IllegalArgumentException(String.format("Unknown PS2 Command: %02X", command));
            }
        }
    }
    
    private int readData() {
        
        m_status &= ~(STATUS_OFULL | STATUS_AUX);
        
        // Clear interrupt
        if(m_irq != 0) {
            
            m_pics.clearInterrupt(m_irq);
            m_irq = 0;
        }
        
        return m_data;
    }
    
    private int readStatus() {
        
        m_status &= ~STATUS_TIME_OUT;
        
        return m_status;
    }
    
    private void setOutputData(int data, boolean isAUX) {
        
        m_data = data;
        
        // Update status flags
        m_status |= STATUS_OFULL;
        if(isAUX) m_status |= STATUS_AUX;
        else      m_status &= ~STATUS_AUX;
        
        // Raise interrupt if enabled
        m_irq = 0;
        if(isAUX && isMouseInterruptEnabled()) {

            m_irq = 12;
            m_pics.setInterrupt(m_irq);
        }
        else if(!isAUX && isKeyboardInterruptEnabled()) {
            
            m_irq = 1;
            m_pics.setInterrupt(m_irq);
        }
    }
    
    
    
    public PS2Port getKeyboardPort(PS2PortDevice device) {
        
        return m_keyPort.init(device);
    }
    
    public PS2Port getMousePort(PS2PortDevice device) {
        
        return m_mousePort.init(device);
    }
    
    
    
    private boolean isMouseEnabled() {
        
        return (m_ram[0] & CTRL_AUXDISABLED) == 0;
    }
    
    private boolean isKeyboardEnabled() {
        
        return (m_ram[0] & CTRL_KEYDISABLED) == 0;
    }
    
    private boolean isKeyboardInterruptEnabled() {
        
        return (m_ram[0] & 0x01) != 0;
    }
    
    private boolean isMouseInterruptEnabled() {
        
        return (m_ram[0] & 0x02) != 0;
    }
}
