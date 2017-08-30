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
package Hardware.Timer;

import Hardware.HardwareComponent;
import IOMap.IOReadable;
import IOMap.IOWritable;
import Hardware.InterruptController.PICs;
import Hardware.Speaker.Speaker;
import java.util.ArrayList;



public final class Intel8253 implements HardwareComponent,
                                        IOReadable,
                                        IOWritable {
    
    /* ----------------------------------------------------- *
     * References to the counter implementations             *
     * ----------------------------------------------------- */
    private final Counter m_counter[];
    
    
    
    public Intel8253() {
        
        m_counter = new Counter[3];
        for(int i = 0; i < 3; i++)
            m_counter[i] = new Counter(i);
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_counter[0].reset();
        m_counter[1].reset();
        m_counter[2].reset();
    }

    @Override
    public void wireWith(HardwareComponent component) {
        

        if(component instanceof PICs) {
            
            final PICs pics = (PICs)component;
            m_counter[0].setOutputListener((newOut, oldOut) -> {
                
                if(!oldOut && newOut)
                    pics.setInterrupt(0);
                if(!newOut)
                    pics.clearInterrupt(0);
            });
        }
        
        if(component instanceof Speaker) {
            
            final Speaker spkr = (Speaker)component;
            m_counter[2].setOutputListener((newOut, oldOut) -> {
                
                spkr.setOutput(newOut);
            });
        }
    }

    @Override
    public ArrayList<HardwareComponent> getSubComponents() {
        
        ArrayList<HardwareComponent> subComponents = new ArrayList<>();
        subComponents.add(m_counter[0]);
        subComponents.add(m_counter[1]);
        subComponents.add(m_counter[2]);
        
        return subComponents;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return new int[] { 0x40, 0x41, 0x42, 0x43 };
    }
    
    @Override
    public int readIO8(int port) {
        
        switch(port) {
        
            // Counter
            case 0x40:
            case 0x41:
            case 0x42:
                return m_counter[port & 0x03].readCounter();
            
            // Control
            case 0x43:
                return 0xff;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return new int[] { 0x40, 0x41, 0x42, 0x43 };
    }

    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
        
            // Counter
            case 0x40:
            case 0x41:
            case 0x42:
                m_counter[port & 0x03].writeCounter(data);
                break;
                
            // Control
            case 0x43:
                int counterIdx = (data >> 6) & 0x03;
                if(counterIdx < 3)
                    m_counter[counterIdx].writeControl(data);
                break;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Counter gate control">
    
    public void setCounterGate(int counter, boolean gate) {
        
        m_counter[counter].setGate(gate);
    }
    
    // </editor-fold>
}
