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
import javax.swing.JMenu;



public final class JPCMenuDrive extends JMenu {
    
    private final JPCMenuDriveOptions[] m_driveOptions;
    
    
    
    public JPCMenuDrive(String driveType, int count, boolean hasCreateOption) {
    
        super(driveType.toUpperCase());
        
        m_driveOptions = new JPCMenuDriveOptions[count];
        for(int i = 0; i < count; i++)
            add(m_driveOptions[i] = new JPCMenuDriveOptions(driveType, i, hasCreateOption));
    }
    
    
    
    protected void addOnSelectHandler(Consumer<Integer> handler) {
        
        for(JPCMenuDriveOptions option : m_driveOptions)
            option.addOnSelectHandler(handler);
    }
    
    protected void addOnCreateHandler(Consumer<Integer> handler) {
        
        for(JPCMenuDriveOptions option : m_driveOptions)
            option.addOnCreateHandler(handler);
    }
    
    protected void addOnEjectHandler(Consumer<Integer> handler) {
        
        for (JPCMenuDriveOptions option : m_driveOptions)
            option.addOnEjectHandler(handler);
    }
    
    
    
    public void setImageName(int driveIndex, String name) {
        
        m_driveOptions[driveIndex].setImageName(name);
    }
}
