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
import java.awt.Toolkit;
import java.util.LinkedList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;



public final class JPCKeyboardAdapter extends KeyAdapter {
    
    /* ----------------------------------------------------- *
     * The keybindings and a list of currently pressed keys  *
     * ----------------------------------------------------- */
    private interface JPCVirtualKey {
        
        int[] getMake();
        int[] getBreak();
    }
    private final HashMap<Long, JPCVirtualKey> m_bindings;
    private final LinkedList<JPCVirtualKey> m_pressedKeys;
    
    /* ----------------------------------------------------- *
     * Used to generate the appropriate make and break codes *
     * ----------------------------------------------------- */
    private final class JPCKeyAction {
        
        private final JPCVirtualKey key;
        private final boolean isPressed;
        private long timeNextEvent;
        
        public JPCKeyAction(JPCVirtualKey key, boolean isPressed) {
            
            this.key = key;
            this.isPressed = isPressed;
            this.timeNextEvent = System.currentTimeMillis();
        }
    }
    private final ConcurrentLinkedQueue<JPCKeyAction> m_keyChain;
    private JPCKeyAction m_typematicKey;
    
    /* ----------------------------------------------------- *
     * Some keyboard settings                                *
     * ----------------------------------------------------- */
    private long m_typematicRepeatDelay;
    private long m_typematicRepeatRate;
    private boolean m_isScanningEnabled;
    
    
    
    public JPCKeyboardAdapter() {
        
        m_bindings = new HashMap<>();
        m_pressedKeys = new LinkedList<>();
        m_keyChain = new ConcurrentLinkedQueue<>();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of KeyAdapter">
    
    @Override
    public void keyPressed(KeyEvent ke) {
        
        if(!ke.isConsumed() && m_isScanningEnabled)
            onKeyEvent(ke, true);
    }
    
    @Override
    public void keyReleased(KeyEvent ke) {

        if(!ke.isConsumed() && m_isScanningEnabled)
            onKeyEvent(ke, false);
    }
    
    private synchronized void onKeyEvent(KeyEvent e, boolean isPressed) {
        
        JPCVirtualKey key = m_bindings.get(
                
            getMappingValue(e.getExtendedKeyCode(), e.getKeyLocation())
        );
        if(key != null) {
            
            e.consume();
            
            if(isPressed && !m_pressedKeys.contains(key)) {

                pressKey(key);
                m_pressedKeys.add(key);
            }
            else if(!isPressed && m_pressedKeys.contains(key)) {
                
                releaseKey(key);
                m_pressedKeys.remove(key);
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Binding of java virtual keycodes to internal virtual keycodes">
    
    public void setKeyBinding(int vk, int location, int[][] scancodes) {
        
        m_bindings.put(
                
            getMappingValue(vk, location),
            new JPCVirtualKey() {
            
                @Override public int[] getMake() { return scancodes[0]; }
                @Override public int[] getBreak() { return scancodes[1]; }
            }
        );
    }
    
    private long getMappingValue(int vk, int location) {
        
        return ((long)vk) | (((long)location) << 32);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Key event handling logic">
    
    public void setEnableScanning(boolean isEnabled) {
        
        m_isScanningEnabled = isEnabled;
    }
    
    public void setTypematicRepeatDelay(long ms) {
        
        m_typematicRepeatDelay = ms;
    }
    
    public void setTypematicRepeatRate(long ms) {
        
        m_typematicRepeatRate = ms;
    }
    
    public void reset(boolean numLock, boolean capsLock, boolean scrollLock) {

        Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_NUM_LOCK, numLock);
        Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_CAPS_LOCK, capsLock);
        Toolkit.getDefaultToolkit().setLockingKeyState(KeyEvent.VK_SCROLL_LOCK, scrollLock);
        
        m_pressedKeys.clear();
        m_keyChain.clear();
        
        m_isScanningEnabled = true;
        m_typematicRepeatDelay = 350l;
        m_typematicRepeatRate = 50l;
        m_typematicKey = null;
    }
    
    private void pressKey(JPCVirtualKey key) {
        
        m_keyChain.add(new JPCKeyAction(key, true));
    }
    
    private void releaseKey(JPCVirtualKey key) {
    
        m_keyChain.add(new JPCKeyAction(key, false));
    }
    
    public boolean hasChangedState() {
        
        return !m_keyChain.isEmpty() || m_typematicKey != null;
    }
    
    public void update(Consumer<Integer> consumer) {
            
        long now = System.currentTimeMillis();

        // Handle key presses and releases
        while(!m_keyChain.isEmpty()) {

            JPCKeyAction k = m_keyChain.poll();
            if(k.isPressed) {

                m_typematicKey = k;
                m_typematicKey.timeNextEvent = now + m_typematicRepeatDelay;
                offer(consumer, k.key.getMake());
            }
            else {

                if(m_typematicKey != null &&
                   m_typematicKey.key == k.key) {

                    m_typematicKey = null;
                }
                offer(consumer, k.key.getBreak());
            }
        }

        // Handle typematic key
        if(m_typematicKey != null &&
           m_typematicKey.timeNextEvent <= now) {

            m_typematicKey.timeNextEvent = now + m_typematicRepeatRate;
            offer(consumer, m_typematicKey.key.getMake());
        }
    }
    
    private void offer(Consumer<Integer> queue, int[] data) {
        
        for(int i = 0; i < data.length; i++)
            queue.accept(data[i]);
    }
    
    public synchronized void releaseAllKeys() {
        
        m_pressedKeys.forEach(k -> releaseKey(k));
        m_pressedKeys.clear();
    }
    
    public synchronized void sendCtrlAltDelete() {
        
        // Get Ctrl, Alt and Delete keys
        JPCVirtualKey ctrl = m_bindings.get(getMappingValue(KeyEvent.VK_CONTROL, KeyEvent.KEY_LOCATION_LEFT));
        JPCVirtualKey alt = m_bindings.get(getMappingValue(KeyEvent.VK_ALT, KeyEvent.KEY_LOCATION_LEFT));
        JPCVirtualKey delete = m_bindings.get(getMappingValue(KeyEvent.VK_DELETE, KeyEvent.KEY_LOCATION_STANDARD));
        
        if(delete == null)
            delete = m_bindings.get(getMappingValue(KeyEvent.VK_DELETE, KeyEvent.KEY_LOCATION_NUMPAD));
        
        // Send Ctrl+Alt+Delete
        if(ctrl != null && alt != null && delete != null) {
            
            pressKey(ctrl);
            pressKey(alt);
            pressKey(delete);
            
            releaseKey(delete);
            releaseKey(alt);
            releaseKey(ctrl);
        }
    }
    
    // </editor-fold>
}
