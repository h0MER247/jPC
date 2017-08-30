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
package Main;

import Main.UI.JPCWindow;
import java.awt.DefaultKeyboardFocusManager;
import java.awt.EventQueue;
import java.awt.KeyEventPostProcessor;
import java.awt.KeyboardFocusManager;
import java.io.FileNotFoundException;
import java.io.IOException;
import javax.swing.UIManager;
import javax.swing.UnsupportedLookAndFeelException;



public final class Main {
    
    public static void main(String[] args) throws FileNotFoundException, IOException {
        
        EventQueue.invokeLater(() -> {
            
            KeyboardFocusManager.setCurrentKeyboardFocusManager(new DefaultKeyboardFocusManager() {

                @Override
                public void addKeyEventPostProcessor(KeyEventPostProcessor p) {
                    
                    if(!p.getClass().getName().contains("WindowsRootPaneUI$AltProcessor"))
                        super.addKeyEventPostProcessor(p);
                }
            });
            
            try {
                
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            }
            catch(ClassNotFoundException |
                  InstantiationException |
                  IllegalAccessException |
                  UnsupportedLookAndFeelException ex) {
                
                System.err.println("jPC couldn't set the systems native look and feel");
            }
            
            JPCWindow wnd = new JPCWindow(720, 400);
            wnd.setVisible(true);
        });
    }
}
