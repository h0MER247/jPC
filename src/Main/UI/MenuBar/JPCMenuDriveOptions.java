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
import javax.swing.JMenuItem;



public final class JPCMenuDriveOptions extends JMenu {
    
    private final String m_driveType;
    private final int m_driveIndex;
    private final JMenuItem m_select;
    private final JMenuItem m_eject;
    private final JMenuItem m_create;
    
    
    
    public JPCMenuDriveOptions(String driveType, int driveIndex, boolean hasCreateOption) {
        
        m_driveType = driveType.toLowerCase();
        m_driveIndex = driveIndex;
        
        add(m_select = new JMenuItem("Select image"));
        add(m_eject = new JMenuItem("Eject"));
        
        if(hasCreateOption)
            add(m_create = new JMenuItem("Create image"));
        else
            m_create = null;
        
        setImageName("");
    }
    
    
    
    protected void addOnSelectHandler(Consumer<Integer> handler) {
        
        m_select.addActionListener(l -> handler.accept(m_driveIndex));
    }
    
    protected void addOnCreateHandler(Consumer<Integer> handler) {
        
        if(m_create == null)
            throw new UnsupportedOperationException("Adding a create handler is not supported by this drive");
        
        m_create.addActionListener(l -> handler.accept(m_driveIndex));
    }
    
    protected void addOnEjectHandler(Consumer<Integer> handler) {
        
        m_eject.addActionListener(l -> handler.accept(m_driveIndex));
    }
    
    
    
    public void setImageName(String name) {
        
        setText(String.format("%s%d: %s", m_driveType,
                                          m_driveIndex,
                                          name.isEmpty() ? "None" : name));
        
        m_eject.setEnabled(!name.isEmpty());
    }
}
