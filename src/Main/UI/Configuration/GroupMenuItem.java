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
package Main.UI.Configuration;

import Main.Systems.ComponentConfig;
import java.util.HashMap;
import java.util.function.Supplier;
import javax.swing.ButtonGroup;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;



public final class GroupMenuItem extends JMenu {

    private HashMap<String, JMenuItem> m_menuItemLUT;
    
    public GroupMenuItem(ComponentConfig config,
                         Supplier<Boolean> resetInterceptor) {
        
        m_menuItemLUT = new HashMap<>();

        // Set label
        setText(config.getLabel());
        
        // Build radio button group
        ButtonGroup grp = new ButtonGroup();
        config.getOptions().forEach(option -> {
            
            String optionLabel = option[0];
            String optionValue = option[1];
            
            // Create radio button
            JMenuItem item = new JRadioButtonMenuItem(optionLabel);
            item.addActionListener(a ->  {
                    
                if(!config.isResettingSystem() || resetInterceptor.get())
                    config.setValue(optionValue);
            });
            add(item);
            grp.add(item);
            
            m_menuItemLUT.put(optionValue, item);
        });
        
        // Add listener
        config.addChangeListener(value -> {
            
            m_menuItemLUT.get(value).setSelected(true);
        });
    }
}
