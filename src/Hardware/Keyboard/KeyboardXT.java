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

import Hardware.HardwareComponent;
import Hardware.Keyboard.KeyboardXT.KeyXT;
import Scheduler.Scheduler;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import Hardware.PPI.Intel8255;
import Scheduler.Schedulable;
import java.util.concurrent.ConcurrentLinkedQueue;



public final class KeyboardXT extends Keyboard<KeyXT>
                              implements HardwareComponent,
                                         Schedulable {
    
    /* ----------------------------------------------------- *
     * IBM XT Keyboard keys and their scancodes              *
     * ----------------------------------------------------- */
    public enum KeyXT {
        
        KEY_ESCAPE(0x01),    KEY_1(0x02),               KEY_2(0x03),           KEY_3(0x04),
        KEY_4(0x05),         KEY_5(0x06),               KEY_6(0x07),           KEY_7(0x08),
        KEY_8(0x09),         KEY_9(0x0a),               KEY_0(0x0b),           KEY_MINUS(0x0c),
        KEY_EQUALS(0x0d),    KEY_BACKSPACE(0x0e),       KEY_TAB(0x0f),         KEY_Q(0x10),
        KEY_W(0x11),         KEY_E(0x12),               KEY_R(0x13),           KEY_T(0x14),
        KEY_Y(0x15),         KEY_U(0x16),               KEY_I(0x17),           KEY_O(0x18),
        KEY_P(0x19),         KEY_BRACE_LEFT(0x1a),      KEY_BRACE_RIGHT(0x1b), KEY_ENTER(0x1c),
        KEY_CTRL(0x1d),      KEY_A(0x1e),               KEY_S(0x1f),           KEY_D(0x20),
        KEY_F(0x21),         KEY_G(0x22),               KEY_H(0x23),           KEY_J(0x24),
        KEY_K(0x25),         KEY_L(0x26),               KEY_SEMICOLON(0x27),   KEY_QUOTE(0x28),
        KEY_BACKQUOTE(0x29), KEY_SHIFT_LEFT(0x2a),      KEY_BACKSLASH(0x2b),   KEY_Z(0x2c),
        KEY_X(0x2d),         KEY_C(0x2e),               KEY_V(0x2f),           KEY_B(0x30),
        KEY_N(0x31),         KEY_M(0x32),               KEY_COMMA(0x33),       KEY_PERIOD(0x34),
        KEY_SLASH(0x35),     KEY_SHIFT_RIGHT(0x36),     KEY_PRINT_SCR(0x37),   KEY_ALT(0x38),
        KEY_SPACE(0x39),     KEY_CAPS_LOCK(0x3a),       KEY_F1(0x3b),          KEY_F2(0x3c),
        KEY_F3(0x3d),        KEY_F4(0x3e),              KEY_F5(0x3f),          KEY_F6(0x40),
        KEY_F7(0x41),        KEY_F8(0x42),              KEY_F9(0x43),          KEY_F10(0x44),
        KEY_NUM_LOCK(0x45),  KEY_SCROLL_LOCK(0x46),     KEY_NUMPAD7(0x47),     KEY_NUMPAD8(0x48),
        KEY_NUMPAD9(0x49),   KEY_NUMPAD_SUBTRACT(0x4a), KEY_NUMPAD4(0x4b),     KEY_NUMPAD5(0x4c),
        KEY_NUMPAD6(0x4d),   KEY_NUMPAD_ADD(0x4e),      KEY_NUMPAD1(0x4f),     KEY_NUMPAD2(0x50),
        KEY_NUMPAD3(0x51),   KEY_NUMPAD0(0x52),         KEY_DELETE(0x53);
        
        KeyXT(int make) { m_make = make; }
        
        private final int m_make;
        private long m_nextEventTime;
        
        int getMakeCode() { return m_make; }
        int getBreakCode() { return m_make | 0x80; }
        public boolean isTimeOfEventReached() { return System.currentTimeMillis() >= m_nextEventTime; }
        public void setTimeOfNextEventIn(long ms) { m_nextEventTime = System.currentTimeMillis() + ms; }
    }
    
    /* ----------------------------------------------------- *
     * Constants for the typematic repeat rates              *
     * ----------------------------------------------------- */
    private final long TYPEMATIC_REPEAT_DELAY_IN_MS = 350;
    private final long TYPEMATIC_REPEAT_RATE_IN_MS = 50;
    
    /* ----------------------------------------------------- *
     * State of the keyboard keys                            *
     * ----------------------------------------------------- */
    private final ConcurrentLinkedQueue<KeyXT> m_keys;
    private final ConcurrentLinkedQueue<Integer> m_fifo;
    
    /* ----------------------------------------------------- *
     * Scheduling                                            *
     * ----------------------------------------------------- */
    private int m_cyclesKeyboard;
    private int m_cyclesRemaining;
    
    /* ----------------------------------------------------- *
     * Reference to the PPI                                  *
     * ----------------------------------------------------- */
    private Intel8255 m_ppi;
    
    
    
    public KeyboardXT() {
        
        m_keys = new ConcurrentLinkedQueue<>();
        m_fifo = new ConcurrentLinkedQueue<>();
        
        initKeyMappings();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_CAPS_LOCK, false);
        Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_SCROLL_LOCK, false);
        Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_NUM_LOCK, false);
        
        m_keys.clear();
        m_fifo.clear();
        
        m_cyclesRemaining = 0;
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
        
        m_cyclesRemaining = m_cyclesKeyboard = Scheduler.toFixedPoint(frequency / 70.0f);
    }

    @Override
    public void updateClock(int cycles) {
        
        m_cyclesRemaining -= cycles;
        if(m_cyclesRemaining <= 0) {
            
            m_cyclesRemaining += m_cyclesKeyboard;
            
            
            if(!m_keys.isEmpty()) {

                m_keys.stream().filter(key -> key.isTimeOfEventReached()).forEach(key -> {
                    
                    key.setTimeOfNextEventIn(TYPEMATIC_REPEAT_RATE_IN_MS);
                    m_fifo.add(key.getMakeCode());
                });
            }
            
            if(!m_fifo.isEmpty()) {
                
                if(m_ppi.isAcceptingKeyboardData())
                    m_ppi.setKeyboardData(m_fifo.poll());
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Keyboard">
    
    @Override
    public void onKeyDown(KeyXT key) {
        
        if(!m_keys.contains(key)) {
        
            key.setTimeOfNextEventIn(TYPEMATIC_REPEAT_DELAY_IN_MS);                
                    
            m_keys.add(key);
            m_fifo.add(key.getMakeCode());
        }
    }
    
    @Override
    public void onKeyUp(KeyXT key) {
        
        if(m_keys.contains(key)) {
        
            m_keys.remove(key);
            m_fifo.add(key.getBreakCode());
        }
    }
    
    @Override
    public void releaseAllKeys() {
        
        m_keys.forEach(key -> m_fifo.add(key.getBreakCode()));
        m_keys.clear();
    }
    
    @Override
    public void sendCtrlAltDelete() {
        
        onKeyDown(KeyXT.KEY_CTRL);
        onKeyDown(KeyXT.KEY_ALT);
        onKeyDown(KeyXT.KEY_DELETE);
        onKeyUp(KeyXT.KEY_DELETE);
        onKeyUp(KeyXT.KEY_ALT);
        onKeyUp(KeyXT.KEY_CTRL);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Initialization of the key mappings">
    
    private void initKeyMappings() {
        
        //
        // These are mappings for a german keyboard layout (host side) into an
        // emulated us layout
        //
        // http://www.pcguide.com/ref/kb/layout/z_011261xt.jpg
        // https://www.computerhope.com/jargon/p/pcxtkeyb.gif
        //
        
        // First row
        setKeyMapping(KeyEvent.VK_F1, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F1);
        setKeyMapping(KeyEvent.VK_F2, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F2);
        setKeyMapping(KeyEvent.VK_ESCAPE, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_ESCAPE);
        setKeyMapping(KeyEvent.VK_1, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_1);
        setKeyMapping(KeyEvent.VK_2, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_2);
        setKeyMapping(KeyEvent.VK_3, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_3);
        setKeyMapping(KeyEvent.VK_4, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_4);
        setKeyMapping(KeyEvent.VK_5, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_5);
        setKeyMapping(KeyEvent.VK_6, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_6);
        setKeyMapping(KeyEvent.VK_7, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_7);
        setKeyMapping(KeyEvent.VK_8, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_8);
        setKeyMapping(KeyEvent.VK_9, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_9);
        setKeyMapping(KeyEvent.VK_0, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_0);
        setKeyMapping(0x010000df, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_MINUS);
        setKeyMapping(KeyEvent.VK_DEAD_ACUTE, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_EQUALS);
        setKeyMapping(KeyEvent.VK_BACK_SPACE, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BACKSPACE);
        setKeyMapping(KeyEvent.VK_NUM_LOCK, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUM_LOCK);
        setKeyMapping(KeyEvent.VK_SCROLL_LOCK, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_SCROLL_LOCK);
        
        // Second row
        setKeyMapping(KeyEvent.VK_F3, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F3);
        setKeyMapping(KeyEvent.VK_F4, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F4);
        setKeyMapping(KeyEvent.VK_TAB, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_TAB);
        setKeyMapping(KeyEvent.VK_Q, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_Q);
        setKeyMapping(KeyEvent.VK_W, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_W);
        setKeyMapping(KeyEvent.VK_E, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_E);
        setKeyMapping(KeyEvent.VK_R, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_R);
        setKeyMapping(KeyEvent.VK_T, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_T);
        setKeyMapping(KeyEvent.VK_Z, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_Y);
        setKeyMapping(KeyEvent.VK_U, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_U);
        setKeyMapping(KeyEvent.VK_I, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_I);
        setKeyMapping(KeyEvent.VK_O, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_O);
        setKeyMapping(KeyEvent.VK_P, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_P);
        setKeyMapping(0x010000fc, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BRACE_LEFT);
        setKeyMapping(KeyEvent.VK_PLUS, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BRACE_RIGHT);
        setKeyMapping(KeyEvent.VK_ENTER, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_ENTER);
        setKeyMapping(KeyEvent.VK_NUMPAD7, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD7);
        setKeyMapping(KeyEvent.VK_NUMPAD8, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD8);
        setKeyMapping(KeyEvent.VK_NUMPAD9, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD9);
        setKeyMapping(KeyEvent.VK_SUBTRACT, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD_SUBTRACT);
        
        // Third row
        setKeyMapping(KeyEvent.VK_F5, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F5);
        setKeyMapping(KeyEvent.VK_F6, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F6);
        setKeyMapping(KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_LEFT, KeyXT.KEY_CTRL);
        setKeyMapping(KeyEvent.VK_A, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_A);
        setKeyMapping(KeyEvent.VK_S, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_S);
        setKeyMapping(KeyEvent.VK_D, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_D);
        setKeyMapping(KeyEvent.VK_F, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F);
        setKeyMapping(KeyEvent.VK_G, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_G);
        setKeyMapping(KeyEvent.VK_H, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_H);
        setKeyMapping(KeyEvent.VK_J, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_J);
        setKeyMapping(KeyEvent.VK_K, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_K);
        setKeyMapping(KeyEvent.VK_L, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_L);
        setKeyMapping(0x010000d6, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_SEMICOLON);
        setKeyMapping(0x010000c4, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_QUOTE);
        setKeyMapping(KeyEvent.VK_NUMBER_SIGN, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BACKQUOTE);
        setKeyMapping(KeyEvent.VK_NUMPAD4, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD4);
        setKeyMapping(KeyEvent.VK_NUMPAD5, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD5);
        setKeyMapping(KeyEvent.VK_NUMPAD6, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD6);
        setKeyMapping(KeyEvent.VK_ADD, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD_ADD);
        
        // Fourth row
        setKeyMapping(KeyEvent.VK_F7, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F7);
        setKeyMapping(KeyEvent.VK_F8, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F8);
        setKeyMapping(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_LEFT, KeyXT.KEY_SHIFT_LEFT);
        setKeyMapping(KeyEvent.VK_LESS, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_BACKSLASH);
        setKeyMapping(KeyEvent.VK_Y, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_Z);
        setKeyMapping(KeyEvent.VK_X, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_X);
        setKeyMapping(KeyEvent.VK_C, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_C);
        setKeyMapping(KeyEvent.VK_V, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_V);
        setKeyMapping(KeyEvent.VK_B, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_B);
        setKeyMapping(KeyEvent.VK_N, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_N);
        setKeyMapping(KeyEvent.VK_M, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_M);
        setKeyMapping(KeyEvent.VK_COMMA, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_COMMA);
        setKeyMapping(KeyEvent.VK_PERIOD, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_PERIOD);
        setKeyMapping(KeyEvent.VK_MINUS, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_SLASH);
        setKeyMapping(KeyEvent.VK_SHIFT, KeyEvent.KEY_LOCATION_RIGHT, KeyXT.KEY_SHIFT_RIGHT);
        setKeyMapping(KeyEvent.VK_PRINTSCREEN, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_PRINT_SCR);
        setKeyMapping(KeyEvent.VK_NUMPAD1, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD1);
        setKeyMapping(KeyEvent.VK_NUMPAD2, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD2);
        setKeyMapping(KeyEvent.VK_NUMPAD3, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD3);
        
        // Fifth row
        setKeyMapping(KeyEvent.VK_F9, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F9);
        setKeyMapping(KeyEvent.VK_F10, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_F10);
        setKeyMapping(KeyEvent.VK_ALT, KeyEvent.KEY_LOCATION_LEFT, KeyXT.KEY_ALT);
        setKeyMapping(KeyEvent.VK_SPACE, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_SPACE);
        setKeyMapping(KeyEvent.VK_CAPS_LOCK, KeyEvent.KEY_LOCATION_STANDARD, KeyXT.KEY_CAPS_LOCK);
        setKeyMapping(KeyEvent.VK_NUMPAD0, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD0);
        setKeyMapping(KeyEvent.VK_DECIMAL, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_DELETE);
        
        // This is just here because shift overrides numlock. It somewhat works around the
        // issues if numlock is active, shift is held down and a key on the numpad is pressed
        setKeyMapping(KeyEvent.VK_HOME, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD7);
        setKeyMapping(KeyEvent.VK_UP, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD8);
        setKeyMapping(KeyEvent.VK_PAGE_UP, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD9);
        setKeyMapping(KeyEvent.VK_LEFT, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD4);
        setKeyMapping(KeyEvent.VK_CLEAR, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD5);
        setKeyMapping(KeyEvent.VK_RIGHT, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD6);
        setKeyMapping(KeyEvent.VK_END, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD1);
        setKeyMapping(KeyEvent.VK_DOWN, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD2);
        setKeyMapping(KeyEvent.VK_PAGE_DOWN, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD3);
        setKeyMapping(KeyEvent.VK_INSERT, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_NUMPAD0);
        setKeyMapping(KeyEvent.VK_DELETE, KeyEvent.KEY_LOCATION_NUMPAD, KeyXT.KEY_DELETE);   
    }
    
    // </editor-fold>
}
