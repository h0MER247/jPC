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
package Main.UI.MenuBar;

import javax.swing.JMenuBar;



public final class JPCMenuBar extends JMenuBar {
    
    private final JPCMenuEmulation m_emulation;
    private final JPCMenuCPU m_cpu;
    private final JPCMenuDrives m_drives;
    private final JPCMenuSpeaker m_speaker;
    
    
    
    public JPCMenuBar() {
        
        add(m_emulation = new JPCMenuEmulation());
        add(m_cpu = new JPCMenuCPU());
        add(m_drives = new JPCMenuDrives());
        add(m_speaker = new JPCMenuSpeaker());
    }
    
    
    
    public JPCMenuEmulation getEmulationMenu() {
        
        return m_emulation;
    }
    
    public JPCMenuCPU getCPUMenu() {
        
        return m_cpu;
    }
    
    public JPCMenuDrives getDrivesMenu() {
        
        return m_drives;
    }
    
    public JPCMenuSpeaker getSpeakerMenu() {
        
        return m_speaker;
    }
}
