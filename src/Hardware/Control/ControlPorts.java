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
package Hardware.Control;

import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.MMU.MMU;
import Hardware.HardwareComponent;
import Hardware.Speaker.Speaker;
import Hardware.Timer.Intel8253;
import IOMap.IOReadable;
import IOMap.IOWritable;



public final class ControlPorts implements HardwareComponent,
                                           IOReadable,
                                           IOWritable {
    
    /* ----------------------------------------------------- *
     * Control Port Data                                     *
     * ----------------------------------------------------- */
    private int m_ctrlPortA;
    private int m_ctrlPortB;
    
    /* ----------------------------------------------------- *
     * References to various other hardware components       *
     * ----------------------------------------------------- */
    private Speaker m_speaker;
    private Intel8253 m_pit;
    private MMU m_mmu;
    private Intel80386 m_cpu;
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_ctrlPortA = 0x00;
        m_ctrlPortB = 0x00;
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof Speaker)
            m_speaker = (Speaker)component;
        
        if(component instanceof Intel8253)
            m_pit = (Intel8253)component;
        
        if(component instanceof MMU)
            m_mmu = (MMU)component;
        
        if(component instanceof Intel80386)
            m_cpu = (Intel80386)component;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return new int[] { 0x61, 0x92 };
    }
    
    @Override
    public int readIO8(int port) {
        
        switch(port) {
            
            case 0x61:
                m_ctrlPortB ^= 0x10;
                return m_ctrlPortB & ~0xc0;
            
            case 0x92:
                return m_ctrlPortA;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }

    @Override
    public int[] getWritableIOPorts() {
        
        return new int[] { 0x61, 0x92 };
    }
    
    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            case 0x61:
                m_ctrlPortB = (m_ctrlPortB & 0x10) | (data & 0x03);
                
                // PIT timer 2 gate
                m_pit.setCounterGate(2, (data & 0x01) != 0);
                
                // Speaker data
                if(m_speaker != null)
                    m_speaker.setData((data & 0x02) != 0);
                break;
                
            case 0x92:
                m_ctrlPortA = data & 0x03;
                
                // Reset cpu
                if((m_ctrlPortA & 0x01) != 0)
                    m_cpu.reset();
                
                // Set A20 Gate
                m_mmu.setA20Gate((m_ctrlPortA & 0x02) != 0);
                break;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
}
