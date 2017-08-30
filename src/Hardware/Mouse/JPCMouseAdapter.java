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
package Hardware.Mouse;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;



public final class JPCMouseAdapter extends MouseAdapter {
    
    /* ----------------------------------------------------- *
     * Current state of the mouse                            *
     * ----------------------------------------------------- */
    private final boolean[] m_buttons;
    private int m_posX, m_posY;
    private int m_deltaX, m_deltaY, m_deltaWheel;
    private boolean m_hasChangedState;
    
    
    
    public JPCMouseAdapter() {
        
        m_buttons = new boolean[3];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of MouseAdapter">
    
    @Override
    public void mouseMoved(MouseEvent me) {
     
        setPosition(me.getX(), me.getY());
    }
    
    @Override
    public void mousePressed(MouseEvent me) {
        
        setButtons(me, true);
    }
    
    @Override
    public void mouseReleased(MouseEvent me) {
        
        setButtons(me, false);
    }
    
    @Override
    public void mouseWheelMoved(MouseWheelEvent mwe) {
        
        m_deltaWheel += mwe.getWheelRotation();
        m_hasChangedState = true;
    }
    
    private void setPosition(int x, int y) {
        
        m_deltaX += x - m_posX; m_posX = x;
        m_deltaY += y - m_posY; m_posY = y;
        m_hasChangedState = true;
    }
    
    private void setButtons(MouseEvent me, boolean isPressed) {
        
        if(me.getButton() == MouseEvent.BUTTON1) m_buttons[0] = isPressed;
        if(me.getButton() == MouseEvent.BUTTON2) m_buttons[1] = isPressed;
        if(me.getButton() == MouseEvent.BUTTON3) m_buttons[2] = isPressed;
        m_hasChangedState = true;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Methods to return the current state of the mouse">
    
    public void reset() {
        
        m_buttons[0] = m_buttons[1] = m_buttons[2] = false;
        m_posX = m_posY = 0;
        m_deltaX = m_deltaY = m_deltaWheel = 0;
        
        m_hasChangedState = false;
    }
    
    public boolean hasChangedState() {
        
        boolean res = m_hasChangedState;
        m_hasChangedState = false;
        
        return res;
    }
    
    public int getDeltaX(int min, int max, boolean negate) {
        
        int res = Math.min(Math.max(negate ? -m_deltaX : m_deltaX, min), max);
        m_deltaX = 0;
        
        return res;
    }
    
    public int getDeltaY(int min, int max, boolean negate) {
        
        int res = Math.min(Math.max(negate ? -m_deltaY : m_deltaY, min), max);
        m_deltaY = 0;
        
        return res;
    }
    
    public int getDeltaWheel(int min, int max) {
        
        int res = Math.min(Math.max(m_deltaWheel, min), max);
        m_deltaWheel = 0;
        
        return res;
    }
    
    public boolean isLeftButtonPressed() {
        
        return m_buttons[0];
    }
    
    public boolean isMiddleButtonPressed() {
        
        return m_buttons[1];
    }
    
    public boolean isRightButtonPressed() {
        
        return m_buttons[2];
    }
    
    // </editor-fold>
}
