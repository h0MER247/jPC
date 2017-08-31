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
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JRadioButtonMenuItem;



public final class JPCMenuSystemSelection extends JMenu {
    
    private final ButtonGroup m_grp;
    
    public JPCMenuSystemSelection() {
        
        super("System");
        
        m_grp = new ButtonGroup();
    }
    
    
    
    public void addSystemHandler(String systemName, Consumer<String> handler) {
        
        JRadioButtonMenuItem sysItem = new JRadioButtonMenuItem(systemName);
        sysItem.addActionListener(a -> handler.accept(systemName));
        
        add(sysItem);
        m_grp.add(sysItem);
    }
}
