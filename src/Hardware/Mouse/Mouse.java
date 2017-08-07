/*
 * Copyright (C) 2017 homer
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



public abstract class Mouse extends MouseAdapter {
    
    /* ----------------------------------------------------- *
     * Current state of the mouse                            *
     * ----------------------------------------------------- */
    private final boolean[] m_buttons;
    private int m_posX, m_posY;
    private int m_deltaX, m_deltaY, m_deltaWheel;
    private boolean m_hasChangedState;
    
    
    
    public Mouse() {
        
        m_buttons = new boolean[3];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of MouseAdapter">
    
    @Override
    public void mouseDragged(MouseEvent me) {
        
        setPosition(me.getX(), me.getY());
    }
    
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
    
    protected void resetMouse() {
        
        m_buttons[0] = m_buttons[1] = m_buttons[2] = false;
        m_posX = m_posY = 0;
        m_deltaX = m_deltaY = m_deltaWheel = 0;
        
        m_hasChangedState = false;
    }
    
    protected boolean hasMouseChangedState() {
        
        boolean res = m_hasChangedState;
        m_hasChangedState = false;
        
        return res;
    }
    
    protected int getDeltaX() {
        
        int res = m_deltaX;
        m_deltaX = 0;
        
        return res;
    }
    
    protected int getDeltaY() {
        
        int res = m_deltaY;
        m_deltaY = 0;
        
        return res;
    }
    
    protected int getDeltaWheel() {
        
        int res = m_deltaWheel;
        m_deltaWheel = 0;
        
        return res;
    }
    
    protected boolean isLeftButtonPressed() {
        
        return m_buttons[0];
    }
    
    protected boolean isMiddleButtonPressed() {
        
        return m_buttons[1];
    }
    
    protected boolean isRightButtonPressed() {
        
        return m_buttons[2];
    }
    
    // </editor-fold>
}
