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
package Utility;

import java.awt.AWTException;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Point;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;



public final class MouseGrabber {
    
    /* ----------------------------------------------------- *
     * MouseEvents get intercepted from this component       *
     * ----------------------------------------------------- */
    private final Component m_component;
    
    /* ----------------------------------------------------- *
     * This handles the mouse grabbing                       *
     * ----------------------------------------------------- */
    private final Runnable m_onMouseGrabbed;
    private Robot m_robot;
    private MouseAdapter m_handler;
    private Cursor m_invisibleCursor;
    private boolean m_isGrabbed;
    private int m_posX;
    private int m_posY;
    
    
    
    public MouseGrabber(Component component,
                        Runnable onMouseGrabbed) {
        
        m_component = component;
        m_onMouseGrabbed = onMouseGrabbed;
        
        try {
            
            // Initialize robot
            m_robot = new Robot();
            
            // Create an invisible cursor
            m_invisibleCursor = Toolkit.getDefaultToolkit().createCustomCursor(
                    
                new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                new Point(),
                null
            );
            
            // Create the grabbing handler
            m_handler = new MouseAdapter() {
                
                @Override public void mouseClicked(MouseEvent me) { onClick(me); }
                @Override public void mouseDragged(MouseEvent me) { onMove(me); }
                @Override public void mouseMoved(MouseEvent me) { onMove(me); }
            };
            
            m_component.addMouseListener(m_handler);
            m_component.addMouseMotionListener(m_handler);
        }
        catch(AWTException ex) {
        }
    }
    
    
    
    private void onClick(MouseEvent me) {
        
        if(m_robot == null)
            return;
        
        if(me.getClickCount() == 2) {
            
            switch(me.getButton()) {
                
                // Left mouse button
                case MouseEvent.BUTTON1:
                    if(!m_isGrabbed) {
                        
                        m_isGrabbed = true;
                        m_component.setCursor(m_invisibleCursor);
                        
                        if(m_onMouseGrabbed != null)
                            m_onMouseGrabbed.run();
                    }
                    break;
                    
                // Right mouse button
                case MouseEvent.BUTTON3:
                    if(m_isGrabbed) {
                        
                        m_isGrabbed = false;
                        m_component.setCursor(Cursor.getDefaultCursor());
                        
                        if(m_onMouseGrabbed != null)
                            m_onMouseGrabbed.run();
                    }
                    break;
            }
        }
    }
    
    private void onMove(MouseEvent me) {
        
        int mx = me.getX();
        int my = me.getY();
        
        if(m_isGrabbed) {
        
            Point p = m_component.getLocationOnScreen();

            int winX = p.x;
            int winY = p.y;
            int winCenterX = m_component.getWidth()  / 2;
            int winCenterY = m_component.getHeight() / 2;
            
            // Calculate mouse movement from the center
            mx -= winCenterX;
            my -= winCenterY;
            
            if(mx != 0 || my != 0) {
                
                // Move cursor back to the center of the window
                m_robot.mouseMove(
                        
                    winX + winCenterX,
                    winY + winCenterY
                );
                
                // Translate the position accordingly so that all other listeners
                // have no idea that the mouse grabber holds the mouse at a fixed
                // position.
                me.translatePoint(m_posX, m_posY);
                
                m_posX += mx;
                m_posY += my;
            }
            else {
                
                me.consume();
            }
        }
    }
    
    
    
    public boolean isMouseGrabbed() {
        
        return m_isGrabbed;
    }
}
