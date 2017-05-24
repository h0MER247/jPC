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



public final class JPCMenuSpeaker extends JMenu {
    
    private final JMenuItem m_enable;
    
    
    
    public JPCMenuSpeaker() {
        
        super("Speaker");
        
        add(m_enable = new JCheckBoxMenuItem("Enable PC Speaker"));
    }
    
    
    
    public void addOnEnableHandler(Consumer<Boolean> handler) {
        
        m_enable.addActionListener(l -> handler.accept(m_enable.isSelected()));
    }
    
    
    
    public void setEnableSpeakerSelected(boolean isSelected) {
        
        m_enable.setSelected(isSelected);
    }
}
