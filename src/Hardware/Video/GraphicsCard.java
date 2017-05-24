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
package Hardware.Video;

import Hardware.HardwareComponent;



public abstract class GraphicsCard implements HardwareComponent {
    
    /* ----------------------------------------------------- *
     * Listener for the output of the graphics card          *
     * ----------------------------------------------------- */
    private final GraphicsCardListener m_listener;
    
    /* ----------------------------------------------------- *
     * Frame data                                            *
     * ----------------------------------------------------- */
    protected int m_frameWidth;
    protected int m_frameHeight;
    protected int m_frameNumber;
    private int m_lastFrameNumber;
    
    /* ----------------------------------------------------- *
     * The frames data                                       *
     * ----------------------------------------------------- */    
    protected int[] m_frameData;
    
    
    
    public GraphicsCard(GraphicsCardListener listener) {
        
        if(listener == null)
            throw new IllegalArgumentException("The graphics card listener can't be null");
        
        m_listener = listener;
    }
    
    
    
    protected final void setResolution(int width, int height) {
        
        if(width != m_frameWidth ||
           height != m_frameHeight) {
            
            m_frameWidth = width;
            m_frameHeight = height;
            m_frameData = new int[width * height];
            
            m_listener.onInit(m_frameData, width, height);
        }
    }
    
    protected final void drawOutput() {
        
        m_frameNumber++;
        m_listener.onRedraw();
    }
    
    
    
    public int getElapsedFrames() {
        
        int numFrames = m_frameNumber - m_lastFrameNumber;
        m_lastFrameNumber = m_frameNumber;
        
        return numFrames;
    }
}
