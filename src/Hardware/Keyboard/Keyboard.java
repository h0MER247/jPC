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
package Hardware.Keyboard;

import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;



public abstract class Keyboard<T> extends KeyAdapter {
    
    /* ----------------------------------------------------- *
     * Keyboard mappings                                     *
     * ----------------------------------------------------- */
    private final HashMap<Long, T> m_mapping;
    
    
    
    public Keyboard() {
        
        m_mapping = new HashMap<>();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of KeyAdapter">
    
    @Override
    public void keyPressed(KeyEvent e) {
        
        handleKeyEvent(e, true);
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
        
        handleKeyEvent(e, false);
    }
    
    private void handleKeyEvent(KeyEvent e, boolean isPressed) {
        
        if(!e.isConsumed()) {
            
            T key = m_mapping.get(
                    
                getMappingValue(e.getExtendedKeyCode(), e.getKeyLocation())
            );
            if(key != null) {
                
                if(isPressed)
                    onKeyDown(key);
                else
                    onKeyUp(key);
                
                e.consume();
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Binding of virtual keycodes to keys of a specific keyboard">
    
    protected void setKeyMapping(int keyCode, int keyLocation, T key) {
        
        m_mapping.put(getMappingValue(keyCode, keyLocation), key);
    }
    
    private long getMappingValue(int extendedKeyCode, int keyLocation) {
        
        return ((long)extendedKeyCode) | (((long)keyLocation) << 32);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Abstract methods that specific keyboards have to implement">
    
    protected abstract void onKeyDown(T key);
    protected abstract void onKeyUp(T key);
    
    public abstract void releaseAllKeys();
    public abstract void sendCtrlAltDelete();
    
    // </editor-fold>
}
