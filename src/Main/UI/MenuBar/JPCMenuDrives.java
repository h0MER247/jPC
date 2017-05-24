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

import java.util.function.Consumer;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;



public final class JPCMenuDrives extends JMenu {
    
    private final JPCMenuDrive m_fdd;
    private final JPCMenuDrive m_hdd;
    private final JMenuItem m_indicator;
    
    
    
    public JPCMenuDrives() {
        
        super("Drives");
        
        add(m_fdd = new JPCMenuDrive("fdd", 2, false));
        add(m_hdd = new JPCMenuDrive("hdd", 4, true));
        addSeparator();
        add(m_indicator = new JCheckBoxMenuItem("Enable drive indicator"));
    }
    
    
    
    public void addOnFDDSelectHandler(Consumer<Integer> handler) {
        
        m_fdd.addOnSelectHandler(handler);
    }
    
    public void addOnFDDEjectHandler(Consumer<Integer> handler) {
        
        m_fdd.addOnEjectHandler(handler);
    }
    
    public void addOnHDDSelectHandler(Consumer<Integer> handler) {
        
        m_hdd.addOnSelectHandler(handler);
    }
    
    public void addOnHDDCreateHandler(Consumer<Integer> handler) {
        
        m_hdd.addOnCreateHandler(handler);
    }
    
    public void addOnHDDEjectHandler(Consumer<Integer> handler) {
        
        m_hdd.addOnEjectHandler(handler);
    }
    
    public void addOnIndicatorHandler(Consumer<Boolean> handler) {
        
        m_indicator.addActionListener(l -> handler.accept(m_indicator.isSelected()));
    }
    
    
    
    public void setFDDImageName(int driveIndex, String name) {
        
        m_fdd.setImageName(driveIndex, name);
    }
    
    public void setHDDImageName(int driveIndex, String name) {
        
        m_hdd.setImageName(driveIndex, name);
    }
    
    public void setEnableIndicatorSelected(boolean isEnabled) {
        
        m_indicator.setSelected(isEnabled);
    }
}
