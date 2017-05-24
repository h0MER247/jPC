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
import Scheduler.Scheduler;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import Hardware.PPI.Intel8255;
import Scheduler.Schedulable;
import java.util.concurrent.ConcurrentLinkedQueue;



public final class Keyboard implements HardwareComponent,
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
    
    
    
    public Keyboard() {
        
        m_keys = new ConcurrentLinkedQueue<>();
        m_fifo = new ConcurrentLinkedQueue<>();
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
    
    // <editor-fold defaultstate="collapsed" desc="Handling of pressed / released keys">
    
    public void pressKey(KeyXT key) {
        
        if(!m_keys.contains(key)) {
        
            key.setTimeOfNextEventIn(TYPEMATIC_REPEAT_DELAY_IN_MS);                
                    
            m_keys.add(key);
            m_fifo.add(key.getMakeCode());
        }
    }
    
    public void releaseKey(KeyXT key) {
        
        if(m_keys.contains(key)) {
        
            m_keys.remove(key);
            m_fifo.add(key.getBreakCode());
        }
    }
    
    public void pressCtrlAltDelete() {
        
        pressKey(KeyXT.KEY_CTRL);
        pressKey(KeyXT.KEY_ALT);
        pressKey(KeyXT.KEY_DELETE);
        releaseKey(KeyXT.KEY_DELETE);
        releaseKey(KeyXT.KEY_ALT);
        releaseKey(KeyXT.KEY_CTRL);
    }
    
    // </editor-fold>
}
