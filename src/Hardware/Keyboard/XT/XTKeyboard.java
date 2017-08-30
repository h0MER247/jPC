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
package Hardware.Keyboard.XT;

import Hardware.HardwareComponent;
import Hardware.Keyboard.JPCKeyboardAdapter;
import Hardware.Keyboard.Keyboard;
import Scheduler.Scheduler;
import java.awt.event.KeyEvent;
import Hardware.PPI.Intel8255;
import Scheduler.Schedulable;
import java.util.LinkedList;
import java.util.Queue;



public final class XTKeyboard implements HardwareComponent,
                                         Schedulable,
                                         Keyboard {
    
    /* ----------------------------------------------------- *
     * Keyboard adapter                                      *
     * ----------------------------------------------------- */
    private final JPCKeyboardAdapter m_keyAdapter;
    private final Queue<Integer> m_buffer;
    
    /* ----------------------------------------------------- *
     * Scheduling                                            *
     * ----------------------------------------------------- */
    private int m_cyclesKeyboard;
    private int m_cyclesRemaining;
    
    /* ----------------------------------------------------- *
     * Reference to the PPI                                  *
     * ----------------------------------------------------- */
    private Intel8255 m_ppi;
    
    
    
    public XTKeyboard() {
        
        m_keyAdapter = new JPCKeyboardAdapter();
        m_buffer = new LinkedList<>();
        
        initKeyMappings();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_keyAdapter.reset(false, false, false);
        m_cyclesRemaining = m_cyclesKeyboard;
    }
    
    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof Intel8255)
            m_ppi = (Intel8255)component;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Schedulable">
    
    @Override
    public void setBaseFrequency(float frequency) {
        
        m_cyclesKeyboard = Scheduler.toFixedPoint(frequency / 70.0f);
    }
    
    @Override
    public void updateClock(int cycles) {
        
        m_cyclesRemaining -= cycles;
        if(m_cyclesRemaining <= 0) {
            
            m_cyclesRemaining += m_cyclesKeyboard;
            
            if(m_keyAdapter.hasChangedState())
                m_keyAdapter.update(data -> m_buffer.add(data));
            
            if(!m_buffer.isEmpty()) {
                
                if(m_ppi.isAcceptingKeyboardData())
                    m_ppi.setKeyboardData(m_buffer.poll());
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Keyboard">
    
    @Override
    public JPCKeyboardAdapter getAdapter() {
        
        return m_keyAdapter;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Initialization of the key mappings">

    private int[][] newKey(int make) { return new int[][] { new int[] { make }, new int[] { make | 0x80 } }; }
    
    private void initKeyMappings() {
        
        //
        // These are mappings for a german keyboard layout (host side) into an
        // emulated us layout
        //
        // http://www.pcguide.com/ref/kb/layout/z_011261xt.jpg
        // https://www.computerhope.com/jargon/p/pcxtkeyb.gif
        //
        
        // First row
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F1, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x3b));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F2, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x3c));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x01));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_1, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x02));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_2, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x03));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_3, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x04));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_4, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x05));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_5, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x06));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_6, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x07));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_7, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x08));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_8, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x09));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_9, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x0a));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_0, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x0b));
        m_keyAdapter.setKeyBinding(0x010000df, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x0c));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_DEAD_ACUTE, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x0d));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_BACK_SPACE, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x0e));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUM_LOCK, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x45));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_SCROLL_LOCK, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x46));
        
        // Second row
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F3, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x3d));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F4, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x3e));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_TAB, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x0f));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_Q, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x10));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_W, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x11));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_E, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x12));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_R, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x13));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_T, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x14));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_Z, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x15));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_U, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x16));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_I, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x17));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_O, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x18));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x19));
        m_keyAdapter.setKeyBinding(0x010000fc, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x1a));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_PLUS, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x1b));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x1c));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD7, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x47));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD8, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x48));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD9, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x49));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_SUBTRACT, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4a));
        
        // Third row
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F5, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x3f));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F6, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x40));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_LEFT, newKey(0x1d));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x1e));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_S, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x1f));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_D, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x20));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x21));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_G, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x22));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_H, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x23));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_J, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x24));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_K, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x25));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_L, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x26));
        m_keyAdapter.setKeyBinding(0x010000d6, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x27));
        m_keyAdapter.setKeyBinding(0x010000c4, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x28));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMBER_SIGN, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x29));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD4, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4b));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD5, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4c));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD6, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4d));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_ADD, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4e));

        // Fourth row
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F7, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x41));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F8, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x42));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT, newKey(0x2a));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_LESS, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x2b));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_Y, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x2c));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_X, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x2d));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_C, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x2e));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_V, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x2f));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_B, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x30));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_N, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x31));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_M, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x32));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_COMMA, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x33));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_PERIOD, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x34));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_MINUS, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x35));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT, newKey(0x36));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_PRINTSCREEN, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x37));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD1, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4f));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD2, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x50));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD3, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x51));
        
        // Fifth row
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F9, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x43));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_F10, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x44));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_ALT, KeyEvent.KEY_LOCATION_LEFT, newKey(0x38));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_SPACE, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x39));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_CAPS_LOCK, KeyEvent.KEY_LOCATION_STANDARD, newKey(0x3a));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_NUMPAD0, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x52));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_DECIMAL, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x53));
        
        // This is just here because shift overrides numlock. It somewhat works around the
        // issues if numlock is active, shift is held down and a key on the numpad is pressed
        m_keyAdapter.setKeyBinding(KeyEvent.VK_HOME, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x47));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_UP, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x48));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_PAGE_UP, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x49));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_LEFT, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4b));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_CLEAR, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4c));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_RIGHT, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4d));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_END, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x4f));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_DOWN, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x50));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_PAGE_DOWN, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x51));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_INSERT, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x52));
        m_keyAdapter.setKeyBinding(KeyEvent.VK_DELETE, KeyEvent.KEY_LOCATION_NUMPAD, newKey(0x53));
    }
    
    // </editor-fold>
}
