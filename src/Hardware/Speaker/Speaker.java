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
package Hardware.Speaker;

import Hardware.HardwareComponent;
import Scheduler.Schedulable;
import Scheduler.Scheduler;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;



public class Speaker implements HardwareComponent,
                                Schedulable {
    
    /* ----------------------------------------------------- *
     * Some constants                                        *
     * ----------------------------------------------------- */
    private final float SAMPLE_RATE = 48000.0f;
    private final int NUM_BITS_PER_SAMPLE = 16;
    private final int NUM_CHANNELS = 1;
    private final int FRAME_SIZE = (NUM_BITS_PER_SAMPLE / 8) * NUM_CHANNELS;
    private final int BUFFER_LENGTH_IN_MS = 100;
    
    /* ----------------------------------------------------- *
     * Scheduling                                            *
     * ----------------------------------------------------- */
    private int m_cyclesSampleFrame;
    private int m_cyclesRemaining;
    
    /* ----------------------------------------------------- *
     * Source data line for audio output                     *
     * ----------------------------------------------------- */
    private final DataLine.Info m_dlInfo;
    private SourceDataLine m_sdl;
    
    /* ----------------------------------------------------- *
     * Sound data buffer related things                      *
     * ----------------------------------------------------- */
    private final byte[] m_sampleBuffer;
    private int m_bufferWriteIdx;
    
    /* ----------------------------------------------------- *
     * Speaker state                                         *
     * ----------------------------------------------------- */
    private boolean m_isInitialized;
    private boolean m_isEnabled;
    private boolean m_output;
    private boolean m_data;
    
    
    
    public Speaker() {
        
        // Audio format of the data line
        AudioFormat audioFormat = new AudioFormat(SAMPLE_RATE,
                                                  NUM_BITS_PER_SAMPLE,
                                                  NUM_CHANNELS,
                                                  true,
                                                  false);
        
        // The internal buffer of m_sdl has room for ~1 second of audio
        m_dlInfo = new DataLine.Info(SourceDataLine.class,
                                     audioFormat,
                                     Math.round(0.5f + SAMPLE_RATE * FRAME_SIZE) & ~(FRAME_SIZE - 1));
        
        // The sample buffer has room for ~BUFFER_LENGTH_IN_MS milliseconds of audio
        m_sampleBuffer = new byte[Math.round(0.5f + ((SAMPLE_RATE * BUFFER_LENGTH_IN_MS) / 1000.0f) * FRAME_SIZE) & ~(FRAME_SIZE - 1)];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void init() {
        
        try {
            
            m_sdl = (SourceDataLine)AudioSystem.getLine(m_dlInfo);
            m_sdl.open();
            m_sdl.start();
            
            m_isInitialized = true;
        }
        catch(LineUnavailableException ex) {
            
            System.err.println("The PC Speaker will not be available");
            ex.printStackTrace(System.err);
            
            m_isInitialized = false;
        }
    }
    
    @Override
    public void reset() {
    
        m_data = false;
        m_output = false;
        
        m_cyclesRemaining = 0;
        
        if(m_isInitialized)
            m_sdl.flush();
    }
    
    @Override
    public void shutdown() {
        
        if(m_isInitialized) {
            
            m_sdl.drain();
            m_sdl.stop();
            m_sdl.close();
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Schedulable">
    
    @Override
    public void setBaseFrequency(float cpuFrequency) {
        
        m_cyclesRemaining = m_cyclesSampleFrame = Scheduler.toFixedPoint(cpuFrequency / SAMPLE_RATE);
    }
    
    @Override
    public void updateClock(int cycles) {
        
        if(m_isInitialized && m_isEnabled) {
        
            m_cyclesRemaining -= cycles;
            while(m_cyclesRemaining <= 0) {
                
                m_cyclesRemaining += m_cyclesSampleFrame;
                
                
                generateSampleFrame();
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Speaker control">
    
    public void setEnable(boolean isEnabled) {
            
        m_isEnabled = isEnabled;
        
        if(!isEnabled && m_isInitialized)
            m_sdl.flush();
    }
    
    public void setData(boolean data) {
        
        m_data = data;
    }
    
    public void setOutput(boolean out) {
        
        m_output = out;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Generation of sample frames">
    
    private void generateSampleFrame() {
        
        byte data = 0x00;
        if(m_data)
            data = (byte)(m_output ? 0x0f : ~0x0f);
        
        m_sampleBuffer[m_bufferWriteIdx++] = 0x00;
        m_sampleBuffer[m_bufferWriteIdx++] = data;
        
        if(m_bufferWriteIdx == m_sampleBuffer.length) {
            
            m_sdl.write(m_sampleBuffer, 0, m_sampleBuffer.length);
            m_bufferWriteIdx = 0;
        }
    }
    
    // </editor-fold>
}
