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
package Scheduler;

import java.util.concurrent.TimeUnit;



/**
 * This doesn't really schedule anything at the moment. Currently every component manages
 * their own cycle counting. So there is still room for improvement...
 */
public final class Scheduler {
    
    /* ----------------------------------------------------- *
     * Fixed point representation to increase accuracy       *
     * ----------------------------------------------------- */
    private static final int FIXED_POINT_BITS = 6;
    private static final float TO_FIXED_POINT = (float)(1 << FIXED_POINT_BITS);
    private static final float FROM_FIXED_POINT = 1.0f / TO_FIXED_POINT;
    
    /* ----------------------------------------------------- *
     * Some constants                                        *
     * ----------------------------------------------------- */
    private final int NUM_MAX_DEVICES = 10;
    private final long NUM_SYNC_POINTS = 70l;
    private final long SLEEP_FUDGE_IN_MS = 1l;
    private final long TIME_NEXT_SYNC_POINT_IN_NS = TimeUnit.SECONDS.toNanos(1l) / NUM_SYNC_POINTS;
    
    /* ----------------------------------------------------- *
     * The base frequency (the one of the cpu) in Hz         *
     * ----------------------------------------------------- */
    private float m_baseFrequency;
    
    /* ----------------------------------------------------- *
     * Clocked hardware devices                              *
     * ----------------------------------------------------- */
    private int m_numDevices;
    private final Schedulable[] m_devices;
    private long m_cycleCounter;
    
    /* ----------------------------------------------------- *
     * To calculate the cpus emulated clock speed in Hz      *
     * ----------------------------------------------------- */
    private long m_cycleCounterStatistic;
    private long m_statisticCycles;
    private long m_statisticTime;
    private float m_effectiveMHz;
    private float m_effectivePercentage;
    
    /* ----------------------------------------------------- *
     * Synchronization of emulated and wall clock time       *
     * ----------------------------------------------------- */
    private long m_cycleCounterSync;
    private long m_syncCycles;
    private long m_syncTime;
    
    
    
    public Scheduler() {
        
        m_devices = new Schedulable[NUM_MAX_DEVICES];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Reset">
    
    public void reset() {
        
        m_cycleCounter = 0l;
        m_cycleCounterSync = m_syncCycles;
        m_cycleCounterStatistic = m_statisticCycles;
        
        m_statisticTime = System.nanoTime();
        m_syncTime = System.nanoTime() + TIME_NEXT_SYNC_POINT_IN_NS;
        
        for(int i = 0; i < m_numDevices; i++)
            m_devices[i].setBaseFrequency(m_baseFrequency);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Adding of devices that implement Schedulable">
    
    public void addDevice(Schedulable device) {
        
        if(m_numDevices == NUM_MAX_DEVICES)
            throw new IllegalArgumentException("There are too many devices registered");
        
        m_devices[m_numDevices++] = device;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Setting the base clock frequency">
    
    public void setBaseFrequency(float baseFrequency, boolean forceUpdate) {
        
        long fixedBaseFreq = Math.round((double)(baseFrequency * TO_FIXED_POINT));
        
        m_syncCycles = fixedBaseFreq / NUM_SYNC_POINTS;
        m_statisticCycles = fixedBaseFreq;
        
        m_baseFrequency = baseFrequency;
        
        if(forceUpdate) {
            
            for(int i = 0; i < m_numDevices; i++)
                m_devices[i].setBaseFrequency(baseFrequency);
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Update elapsed clock cycles for all registered devices">
    
    public void updateClock(int cycles) {
        
        m_cycleCounter += cycles;
        
        // Update components
        for(int i = 0; i < m_numDevices; i++)
            m_devices[i].updateClock(cycles);
        
        // Synchronization
        if(Long.compareUnsigned(m_cycleCounter, m_cycleCounterSync) >= 0) {
            
            m_cycleCounterSync += m_syncCycles;
            syncWithWallClock();
        }
        
        // Statistic
        if(Long.compareUnsigned(m_cycleCounter, m_cycleCounterStatistic) >= 0) {
            
            m_cycleCounterStatistic += m_statisticCycles;
            updateStatistics();
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Synchronization with wall clock time">
    
    private void syncWithWallClock() {
        
        // TODO: Find a better synchronization method...
        long timeInMS = TimeUnit.NANOSECONDS.toMillis(m_syncTime - System.nanoTime()) - SLEEP_FUDGE_IN_MS;
        if(timeInMS > 0l) {

            try {

                Thread.sleep(timeInMS);
            }
            catch(InterruptedException ex) {
            }
        }
        while(System.nanoTime() < m_syncTime) { }
        
        m_syncTime = System.nanoTime() + TIME_NEXT_SYNC_POINT_IN_NS;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Statistics">
    
    private void updateStatistics() {
        
        float p = 1000000000f / (System.nanoTime() - m_statisticTime);
        
        m_effectiveMHz = p * (m_baseFrequency / 1000000.0f);
        m_effectivePercentage = p * 100f;
        
        m_statisticTime = System.nanoTime();
    }
    
    public float getEffectiveMhz() {
        
        return m_effectiveMHz;
    }
    
    public float getEffectivePercentage() {
        
        return m_effectivePercentage;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Fixed point calculation helper">
    
    public static int toFixedPoint(float cycles) {
        
        return Math.round(cycles * TO_FIXED_POINT);
    }
    
    public static int toFixedPoint(int cycles) {
        
        return cycles << FIXED_POINT_BITS;
    }
    
    public static int fromFixedPoint(float fixedPointCycles) {
        
        return Math.round(fixedPointCycles * FROM_FIXED_POINT);
    }
    
    public static int fromFixedPoint(int fixedPointCycles) {
        
        return fixedPointCycles >>> FIXED_POINT_BITS;
    }
    
    // </editor-fold>
}
