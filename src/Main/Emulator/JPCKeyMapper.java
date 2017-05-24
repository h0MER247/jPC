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
package Main.Emulator;

import Hardware.Keyboard.Keyboard;
import Hardware.Keyboard.Keyboard.KeyXT;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.HashMap;



public final class JPCKeyMapper implements KeyListener {
    
    /* ----------------------------------------------------- *
     * Mapping between KeyEvents and the keys of the IBM XT  *
     * ----------------------------------------------------- */
    private final HashMap<Long, KeyXT> m_mapping;
    
    /* ----------------------------------------------------- *
     * Reference to the keyboard implementation              *
     * ----------------------------------------------------- */
    private final Keyboard m_keyboard;
    
    
    
    public JPCKeyMapper(Keyboard keyboard) {
        
        m_keyboard = keyboard;
        
        m_mapping = new HashMap<>();
        initKeyMapping();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of KeyListener">
    
    @Override
    public void keyTyped(KeyEvent e) {
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        
        if(!e.isConsumed()) {
        
            KeyXT key = getKeyMapping(e);
            if(key != null) {
                
                m_keyboard.pressKey(key);
                e.consume();
            }
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
        
        if(!e.isConsumed()) {
            
            KeyXT key = getKeyMapping(e);
            if(key != null) {
                
                m_keyboard.releaseKey(key);
                e.consume();
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Key mapping between host and emulation">
    
    private void initKeyMapping() {
        
        //
        // These are mappings for a german keyboard layout (host side) into an
        // emulated us layout
        //
        // http://www.pcguide.com/ref/kb/layout/z_011261xt.jpg
        // https://www.computerhope.com/jargon/p/pcxtkeyb.gif
        //
        
        // First row
        addKeyMapping(KeyEvent.VK_F1, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F1);
        addKeyMapping(KeyEvent.VK_F2, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F2);
        addKeyMapping(KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_ESCAPE);
        addKeyMapping(KeyEvent.VK_1, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_1);
        addKeyMapping(KeyEvent.VK_2, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_2);
        addKeyMapping(KeyEvent.VK_3, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_3);
        addKeyMapping(KeyEvent.VK_4, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_4);
        addKeyMapping(KeyEvent.VK_5, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_5);
        addKeyMapping(KeyEvent.VK_6, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_6);
        addKeyMapping(KeyEvent.VK_7, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_7);
        addKeyMapping(KeyEvent.VK_8, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_8);
        addKeyMapping(KeyEvent.VK_9, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_9);
        addKeyMapping(KeyEvent.VK_0, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_0);
        addKeyMapping(0x010000df, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_MINUS);
        addKeyMapping(KeyEvent.VK_DEAD_ACUTE, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_EQUALS);
        addKeyMapping(KeyEvent.VK_BACK_SPACE, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BACKSPACE);
        addKeyMapping(KeyEvent.VK_NUM_LOCK, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUM_LOCK);
        addKeyMapping(KeyEvent.VK_SCROLL_LOCK, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_SCROLL_LOCK);
        
        // Second row
        addKeyMapping(KeyEvent.VK_F3, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F3);
        addKeyMapping(KeyEvent.VK_F4, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F4);
        addKeyMapping(KeyEvent.VK_TAB, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_TAB);
        addKeyMapping(KeyEvent.VK_Q, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_Q);
        addKeyMapping(KeyEvent.VK_W, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_W);
        addKeyMapping(KeyEvent.VK_E, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_E);
        addKeyMapping(KeyEvent.VK_R, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_R);
        addKeyMapping(KeyEvent.VK_T, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_T);
        addKeyMapping(KeyEvent.VK_Z, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_Y);
        addKeyMapping(KeyEvent.VK_U, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_U);
        addKeyMapping(KeyEvent.VK_I, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_I);
        addKeyMapping(KeyEvent.VK_O, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_O);
        addKeyMapping(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_P);
        addKeyMapping(0x010000fc, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BRACE_LEFT);
        addKeyMapping(KeyEvent.VK_PLUS, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BRACE_RIGHT);
        addKeyMapping(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_ENTER);
        addKeyMapping(KeyEvent.VK_NUMPAD7, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD7);
        addKeyMapping(KeyEvent.VK_NUMPAD8, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD8);
        addKeyMapping(KeyEvent.VK_NUMPAD9, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD9);
        addKeyMapping(KeyEvent.VK_SUBTRACT, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD_SUBTRACT);
        
        // Third row
        addKeyMapping(KeyEvent.VK_F5, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F5);
        addKeyMapping(KeyEvent.VK_F6, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F6);
        addKeyMapping(KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_LEFT, KeyXT.KEY_CTRL);
        addKeyMapping(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_A);
        addKeyMapping(KeyEvent.VK_S, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_S);
        addKeyMapping(KeyEvent.VK_D, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_D);
        addKeyMapping(KeyEvent.VK_F, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F);
        addKeyMapping(KeyEvent.VK_G, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_G);
        addKeyMapping(KeyEvent.VK_H, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_H);
        addKeyMapping(KeyEvent.VK_J, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_J);
        addKeyMapping(KeyEvent.VK_K, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_K);
        addKeyMapping(KeyEvent.VK_L, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_L);
        addKeyMapping(0x010000d6, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_SEMICOLON);
        addKeyMapping(0x010000c4, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_QUOTE);
        addKeyMapping(KeyEvent.VK_NUMBER_SIGN, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BACKQUOTE);
        addKeyMapping(KeyEvent.VK_NUMPAD4, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD4);
        addKeyMapping(KeyEvent.VK_NUMPAD5, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD5);
        addKeyMapping(KeyEvent.VK_NUMPAD6, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD6);
        addKeyMapping(KeyEvent.VK_ADD, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD_ADD);
        
        // Fourth row
        addKeyMapping(KeyEvent.VK_F7, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F7);
        addKeyMapping(KeyEvent.VK_F8, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F8);
        addKeyMapping(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT, KeyXT.KEY_SHIFT_LEFT);
        addKeyMapping(KeyEvent.VK_LESS, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BACKSLASH);
        addKeyMapping(KeyEvent.VK_Y, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_Z);
        addKeyMapping(KeyEvent.VK_X, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_X);
        addKeyMapping(KeyEvent.VK_C, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_C);
        addKeyMapping(KeyEvent.VK_V, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_V);
        addKeyMapping(KeyEvent.VK_B, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_B);
        addKeyMapping(KeyEvent.VK_N, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_N);
        addKeyMapping(KeyEvent.VK_M, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_M);
        addKeyMapping(KeyEvent.VK_COMMA, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_COMMA);
        addKeyMapping(KeyEvent.VK_PERIOD, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_PERIOD);
        addKeyMapping(KeyEvent.VK_MINUS, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_SLASH);
        addKeyMapping(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT, KeyXT.KEY_SHIFT_RIGHT);
        addKeyMapping(KeyEvent.VK_PRINTSCREEN, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_PRINT_SCR);
        addKeyMapping(KeyEvent.VK_NUMPAD1, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD1);
        addKeyMapping(KeyEvent.VK_NUMPAD2, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD2);
        addKeyMapping(KeyEvent.VK_NUMPAD3, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD3);
        
        // Fifth row
        addKeyMapping(KeyEvent.VK_F9, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F9);
        addKeyMapping(KeyEvent.VK_F10, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F10);
        addKeyMapping(KeyEvent.VK_ALT, KeyEvent.KEY_LOCATION_LEFT, KeyXT.KEY_ALT);
        addKeyMapping(KeyEvent.VK_SPACE, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_SPACE);
        addKeyMapping(KeyEvent.VK_CAPS_LOCK, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_CAPS_LOCK);
        addKeyMapping(KeyEvent.VK_NUMPAD0, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD0);
        addKeyMapping(KeyEvent.VK_DECIMAL, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_DELETE);
        
        // This is just here because shift overrides numlock. It somewhat works around the
        // issues if numlock is active, shift is held down and a key on the numpad is pressed
        addKeyMapping(KeyEvent.VK_HOME, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD7);
        addKeyMapping(KeyEvent.VK_UP, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD8);
        addKeyMapping(KeyEvent.VK_PAGE_UP, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD9);
        addKeyMapping(KeyEvent.VK_LEFT, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD4);
        addKeyMapping(KeyEvent.VK_CLEAR, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD5);
        addKeyMapping(KeyEvent.VK_RIGHT, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD6);
        addKeyMapping(KeyEvent.VK_END, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD1);
        addKeyMapping(KeyEvent.VK_DOWN, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD2);
        addKeyMapping(KeyEvent.VK_PAGE_DOWN, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD3);
        addKeyMapping(KeyEvent.VK_INSERT, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD0);
        addKeyMapping(KeyEvent.VK_DELETE, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_DELETE);   
    }
    
    private void addKeyMapping(int keyCode, int keyLocation, KeyXT key) {
        
        m_mapping.put(getMappingValue(keyCode, keyLocation), key);
    }
    
    private KeyXT getKeyMapping(KeyEvent key) {
        
        return m_mapping.get(getMappingValue(key.getExtendedKeyCode(), key.getKeyLocation()));
    }
    
    private long getMappingValue(int extendedKeyCode, int keyLocation) {
        
        return ((long)extendedKeyCode) | (((long)keyLocation) << 32);
    }
    
    // </editor-fold>
}
