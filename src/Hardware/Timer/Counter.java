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
package Hardware.Timer;

import Hardware.HardwareComponent;
import Scheduler.Schedulable;
import Scheduler.Scheduler;



/**
 * 
 * @see https://technicalpublications.org/media/freedownloads/8253_54-1.pdf
 *      http://www.cpcwiki.eu/imgs/e/e3/8253.pdf
 */
public final class Counter implements HardwareComponent,
                                      Schedulable {
    
    /* ----------------------------------------------------- *
     * Frequency of the counter crystal                      *
     * ----------------------------------------------------- */
    private final float PIT_FREQUENCY = 1193182.0f;
    
    /* ----------------------------------------------------- *
     * Some constants for the read / write mode              *
     * ----------------------------------------------------- */
    private final int RWMODE_LSB = 0x00;
    private final int RWMODE_MSB = 0x01;
    private final int RWMODE_WORD_LSB = 0x02;
    private final int RWMODE_WORD_MSB = 0x03;
    
    /* ----------------------------------------------------- *
     * Counter state                                         *
     * ----------------------------------------------------- */
    private final int m_counterIdx;
    private boolean m_gate;
    private int m_opMode;
    private int m_readMode;
    private int m_writeMode;
    private int m_counterLatch;
    private int m_counterRegister;
    private int m_counterCycles;
    private boolean m_isLatched;
    private boolean m_isRunning;
    private boolean m_isDisabled;
    private boolean m_isInitial;
    private boolean m_isTerminalCountReached;
    
    /* ----------------------------------------------------- *
     * Listener for the output of this counter               *
     * ----------------------------------------------------- */
    private TimerOutputListener m_outputListener;
    private boolean m_output;
    
    /* ----------------------------------------------------- *
     * Conversion between cycles and counter values          *
     * ----------------------------------------------------- */
    private float m_counterToCycles;
    private float m_cyclesToCounter;
    
    
    
    public Counter(int counterIdx) {
        
        m_counterIdx = counterIdx;
        setOutputListener((n, o) -> {});
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_counterRegister = 0xffff;
        m_counterCycles = getCyclesFromCounter(0xffff);
        
        // Reset output
        m_output = false;
        
        // Reset control word
        m_opMode = 0;
        m_readMode = m_writeMode = RWMODE_LSB;
        
        // Clear latch
        m_isLatched = false;
        m_counterLatch = 0x0000;
        
        // The gate of counter 0 and 1 is always high
        m_gate = m_counterIdx < 2;
        
        // Counter is not running at all
        m_isRunning = false;
        m_isDisabled = true;
        m_isInitial = false;
        m_isTerminalCountReached = true;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Schedulable">
    
    @Override
    public void setBaseFrequency(float cpuFrequency) {
        
        m_counterToCycles = cpuFrequency / PIT_FREQUENCY;
        m_cyclesToCounter = PIT_FREQUENCY / cpuFrequency;
    }
    
    @Override
    public void updateClock(int cycles) {
        
        if(!m_isRunning)
            return;
        
        m_counterCycles -= cycles;
        if(m_counterCycles <= 0) {
        
            if(m_isDisabled) {

                m_counterCycles += getCyclesFromCounter(0xffff);
            }
            else {
                
                switch(m_opMode) {

                    case 0: // Interrupt on terminal count
                    case 1: // Hardware retriggerable one-shot
                        if(!m_isTerminalCountReached) {

                            setOutput(true);
                            m_isTerminalCountReached = true;
                        }
                        m_counterCycles += getCyclesFromCounter(0xffff);
                        break;

                    case 2: // Rate generator
                        m_counterCycles += getCyclesFromCounter(m_counterRegister);
                        setOutput(false);
                        setOutput(true);
                        break;

                    case 3: // Square wave mode
                        if(m_output) {

                            setOutput(false);
                            m_counterCycles += getCyclesFromCounter(m_counterRegister >>> 1);
                        }
                        else {

                            setOutput(true);
                            m_counterCycles += getCyclesFromCounter((m_counterRegister + 1) >>> 1);
                        }
                        break;

                    case 4: // Software triggered strobe
                        throw new IllegalArgumentException("Unimplemented");

                    case 5: // Hardware triggered strobe (retriggerable)
                        if(!m_isTerminalCountReached) {

                            setOutput(false);
                            setOutput(true);
                            m_isTerminalCountReached = true;
                        }
                        m_counterCycles += getCyclesFromCounter(0xffff);
                        break;  
                }
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Write control word">
    
    public void writeControl(int data) {
        
        boolean bcdMode = (data & 0x01) != 0;
        int opMode = (data >>> 1) & 0x07;
        int rwMode = (data >>> 4) & 0x03;
        
        // Currently unimplemented as i haven't seen it used anywhere
        if(bcdMode)
            throw new UnsupportedOperationException("BCD mode is currently unimplemented");
        
        // TODO: Find out how real hardware handles this
        if(opMode > 5)
            throw new IllegalArgumentException("Illegal operation mode specified");
        
        // TODO: Implement software triggered strobe mode
        if(opMode == 0x04)
            throw new UnsupportedOperationException("Software triggered strobe is currently unimplemented");
        
        
        switch(rwMode) {
            
            // Latch command
            case 0x00:
                if(!m_isLatched) {
                
                    m_counterLatch = getCounterValue();
                    m_isLatched = true;
                }
                break;
                
            // Control word
            default:
                setOutput(opMode != 0);
                m_opMode = opMode;
                m_readMode = m_writeMode = rwMode - 1;
                m_isInitial = true;
                m_isDisabled = true;
                break;
        }
        
        m_isTerminalCountReached = false;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Read counter value">
    
    public int readCounter() {
        
        int value = m_isLatched ? m_counterLatch : getCounterValue();
        
        switch(m_readMode) {
            
            case RWMODE_LSB:
                m_isLatched = false;
                break;
                
            case RWMODE_MSB:
                value >>>= 8;
                m_isLatched = false;
                break;
                
            case RWMODE_WORD_LSB:
                m_readMode = RWMODE_WORD_MSB;
                break;
                
            case RWMODE_WORD_MSB:
                value >>>= 8;
                m_readMode = RWMODE_WORD_LSB;
                m_isLatched = false;
                break;
        }
        
        return value & 0xff;
    }
    
    private int getCounterValue() {
        
        int value = getCounterFromCycles(m_counterCycles);
        
        if(m_opMode == 2)
            value++;
        else if(m_opMode == 3)
            value <<= 1;
        
        return Math.min(Math.max(value, 0x0000), 0xffff);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Write counter value">
    
    public void writeCounter(int data) {
        
        switch(m_writeMode) {
            
            case RWMODE_LSB:
                m_counterRegister = data;
                loadCounter();
                break;
                
            case RWMODE_MSB:
                m_counterRegister = data << 8;
                loadCounter();
                break;
                
            case RWMODE_WORD_LSB:
                m_counterRegister &= 0xff00;
                m_counterRegister |= data;
                m_writeMode = RWMODE_WORD_MSB;
                break;
                
            case RWMODE_WORD_MSB:
                m_counterRegister &= 0x00ff;
                m_counterRegister |= data << 8;
                m_writeMode = RWMODE_WORD_LSB;
                loadCounter();
                break;
        }
    }
    private void loadCounter() {
        
        if(m_counterRegister == 0)
            m_counterRegister = 0x10000;
        
        switch(m_opMode) {
            
            case 0: // Interrupt on terminal count
                setOutput(false);
                m_counterCycles = getCyclesFromCounter(m_counterRegister);
                m_isTerminalCountReached = false;
                m_isRunning = m_gate;
                break;
                
            case 1: // Hardware retriggerable one-shot
            case 5: // Hardware triggered strobe (retriggerable)
                m_isRunning = true;
                break;
                
            case 2: // Rate generator
                if(m_isInitial) {
                    
                    setOutput(true);
                    m_counterCycles = getCyclesFromCounter(m_counterRegister - 1);
                    m_isTerminalCountReached = false;
                }
                m_isRunning = m_gate;
                break;
                
            case 3: // Square wave mode
                if(m_isInitial) {
                    
                    setOutput(true);
                    m_counterCycles = getCyclesFromCounter((m_counterRegister + 1) >>> 1);
                    m_isTerminalCountReached = false;
                }
                m_isRunning = m_gate;
                break;
                
            case 4: // Software triggered strobe
                throw new UnsupportedOperationException("Unimplemented");
        }
        
        m_isInitial = false;
        m_isDisabled = false;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Counter gate">
    
    public void setGate(boolean gate) {
        
        if(m_isDisabled) {
        
            m_gate = gate;
        }
        else {
            
            boolean isTriggered = gate && !m_gate;
            
            m_gate = gate;
            switch(m_opMode) {
                
                case 0: // Interrupt on terminal count
                    m_isRunning = gate;
                    break;

                case 1: // Hardware retriggerable one-shot
                case 5: // Hardware triggered strobe (retriggerable)
                    if(isTriggered) {
                    
                        setOutput(false);
                        
                        m_counterCycles = getCyclesFromCounter(m_counterRegister);
                        m_isRunning = true;
                        m_isTerminalCountReached = false;
                    }
                    break;

                case 2: // Rate generator
                case 3: // Square wave mode
                    if(isTriggered) {
                        
                        setOutput(true);
                        
                        if(m_opMode == 2)
                            m_counterCycles = getCyclesFromCounter(m_counterRegister - 1);
                        else
                            m_counterCycles = getCyclesFromCounter((m_counterRegister + 1) >>> 1);
                        
                        m_isTerminalCountReached = false;
                    }
                    m_isRunning = gate;
                    break;
                    
                case 4: // Software triggered strobe
                    throw new UnsupportedOperationException("Unimplemented");
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Timer output related methods">
    
    public void setOutputListener(TimerOutputListener listener) {
        
        m_outputListener = listener;
    }
        
    private void setOutput(boolean newOut) {
        
        if(m_output != newOut)
            m_outputListener.onTimerOutputChanged(newOut, m_output);
        
        m_output = newOut;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Some helper methods">
    
    private int getCounterFromCycles(int cycles) {
        
        return Scheduler.fromFixedPoint(cycles * m_cyclesToCounter);
    }
    
    private int getCyclesFromCounter(int counter) {

        return Scheduler.toFixedPoint(counter * m_counterToCycles);
    }
    
    // </editor-fold>
}
