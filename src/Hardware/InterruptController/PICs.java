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
import java.util.ArrayList;



/**
 * TODO: This is just a bad hack until i implement propper master / slave
 *       configurations in the i8259 emulation. Some software might break
 *       as this is not the correct behaviour.
 */
public final class PICs implements HardwareComponent {

    /* ----------------------------------------------------- *
     * Master and Slave PICs                                 *
     * ----------------------------------------------------- */
    private final Intel8259a m_picMaster;
    private final Intel8259a m_picSlave;
    
    private final boolean m_isSinglePIC;
    
    
    
    public PICs(boolean isSinglePIC) {
        
        m_isSinglePIC = isSinglePIC;
        
        m_picMaster = new Intel8259a(true);
        m_picSlave = new Intel8259a(false);
    }
    
    @Override
    public void reset() {
        
        m_picMaster.reset();
        
        if(!m_isSinglePIC)
            m_picSlave.reset();
    }
    
    @Override
    public ArrayList<HardwareComponent> getSubComponents() {
        
        ArrayList<HardwareComponent> list = new ArrayList<>();
        
        list.add(m_picMaster);
        
        if(!m_isSinglePIC)
            list.add(m_picSlave);
        
        return list;
    }
    
    
    
    public boolean isPending() {
        
        if(m_isSinglePIC)
            return m_picMaster.isPending();
        
        return m_picMaster.isPending() ||
               m_picSlave.isPending();
    }
    
    public int getInterrupt() {
        
        if(m_isSinglePIC)
            return m_picMaster.getInterrupt();
        
        if(m_picSlave.isPending())
            return m_picSlave.getInterrupt();
        
        return m_picMaster.getInterrupt();
    }
    
    public void setInterrupt(int irq) {
        
        if(!m_isSinglePIC && irq == 0x02)
            irq = 0x09;
        
        if(irq <= 0x07 || m_isSinglePIC)
            m_picMaster.setInterrupt(irq);
        else
            m_picSlave.setInterrupt(irq & 0x07);
    }
    
    public void clearInterrupt(int irq) {
        
        if(irq <= 0x07 || m_isSinglePIC)
            m_picMaster.clearInterrupt(irq);
        else
            m_picSlave.clearInterrupt(irq & 0x07);
    }
}
