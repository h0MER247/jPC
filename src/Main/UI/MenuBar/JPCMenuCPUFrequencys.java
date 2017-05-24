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
import javax.swing.JMenuItem;
import javax.swing.JRadioButtonMenuItem;



public final class JPCMenuCPUFrequencys extends JMenu {
    
    private final float[] CPU_FREQUENCYS = new float[] {  4770000.0f,   8000000.0f,  10000000.0f,
                                                         12000000.0f,  16000000.0f,  33000000.0f,
                                                         40000000.0f,  75000000.0f, 100000000.0f };
    
    private final JMenuItem[] m_selections;
    
    
    
    public JPCMenuCPUFrequencys() {
        
        super("Frequency");
        
        ButtonGroup grp = new ButtonGroup();
        
        m_selections = new JMenuItem[CPU_FREQUENCYS.length];
        for(int i = 0; i < m_selections.length; i++) {
            
            if(i == 5)
                addSeparator();
            
            add(m_selections[i] = new JRadioButtonMenuItem(String.format("%.2f MHz", CPU_FREQUENCYS[i] / 1000000.0f)));
            grp.add(m_selections[i]);
        }
    }
    
    
    
    protected void addOnFrequencyHandler(Consumer<Float> handler) {
        
        for(int i = 0; i < CPU_FREQUENCYS.length; i++) {
            
            final int j = i;
            m_selections[i].addActionListener(l -> handler.accept(CPU_FREQUENCYS[j]));
        }
    }
    
    
    
    protected void setFrequencySelected(float frequency) {
        
        for(int i = 0; i < CPU_FREQUENCYS.length; i++) {
            
            if(CPU_FREQUENCYS[i] == frequency) {
                
                m_selections[i].setSelected(true);
                break;
            }
        }
    }
}
