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
package Hardware.CMOS;

import Hardware.HardwareComponent;
import Hardware.InterruptController.PICs;
import IOMap.IOReadable;
import IOMap.IOWritable;
import Scheduler.Schedulable;
import Scheduler.Scheduler;
import Utility.FileResource;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;



/**
 * Still many things missing:
 * 
 *  - Update in progress and completion handling
 *  - alarm interrupts
 *  - 
 */
public final class CMOS implements HardwareComponent,
                                   IOReadable,
                                   IOWritable,
                                   Schedulable {
    
    /* ----------------------------------------------------- *
     * RTC crystal frequency                                 *
     * ----------------------------------------------------- */
    private final float RTC_FREQUENCY = 32768.0f;
    
    /* ----------------------------------------------------- *
     * RTC register A                                        *
     * ----------------------------------------------------- */
    private final int REG_A_RATE_SELECT_MASK = 0x0f;
    private final int REG_A_UPDATE_IN_PROGRESS = 0x80;
    private int m_regA;
    
    /* ----------------------------------------------------- *
     * RTC register B                                        *
     * ----------------------------------------------------- */
    private final int REG_B_DAYLIGHT_SAVINGS = 0x01;
    private final int REG_B_24H_MODE = 0x02;
    private final int REG_B_DATAMODE_BINARY = 0x04;
    private final int REG_B_INT_ENABLE_MASK = 0x70;
    private final int REG_B_DISABLE_CLOCK_UPDATE = 0x80;
    private int m_regB;
    
    /* ----------------------------------------------------- *
     * RTC register C                                        *
     * ----------------------------------------------------- */
    private final int REG_C_INT_UPDATE_ENDED = 0x10;
    private final int REG_C_INT_ALARM = 0x20;
    private final int REG_C_INT_PERIODIC = 0x40;
    private final int REG_C_IRQ = 0x80;
    private int m_regC;
    
    /* ----------------------------------------------------- *
     * RTC register D                                        *
     * ----------------------------------------------------- */
    private final int REG_D_CMOS_BATTERY_OK = 0x80;
    private int m_regD;
    
    /* ----------------------------------------------------- *
     * Timer Alarm Register                                  *
     * ----------------------------------------------------- */
    private int m_alarmSeconds;
    private int m_alarmMinutes;
    private int m_alarmHours;
    
    /* ----------------------------------------------------- *
     * Calendar used to keep track of the rtc's time         *
     * ----------------------------------------------------- */
    private final GregorianCalendar m_calendar;
    private long m_calendarTime;
    
    /* ----------------------------------------------------- *
     * Scheduling                                            *
     * ----------------------------------------------------- */
    private float m_frequency;
    private float m_periodicINTRateInHz;
    private int m_periodicINTRemaining;
    private int m_periodicINTCycles;
    private int m_updateINTRemaining;
    private int m_updateINTCycles;
    
    /* ----------------------------------------------------- *
     * Reference to the interrupt controller                 *
     * ----------------------------------------------------- */
    private PICs m_pics;
    private boolean m_isUpdateEnabled;
    private boolean m_isPeriodicUpdateEnabled;
    
    /* ----------------------------------------------------- *
     * CMOS non volatile ram                                 *
     * ----------------------------------------------------- */
    private static final String CMOS_PATH = "data/cmos";
    private final File m_ramFile;
    private final int[] m_ram;
    private int m_address;
    
    
    
    public CMOS(String ramFileName) {
        
        m_ramFile = new File(CMOS_PATH, ramFileName);
        m_ram = new int[0x80];
        
        m_calendar = new GregorianCalendar();
    }

    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void init() {
        
        try {
        
            FileResource.read(m_ram, m_ramFile);
        }
        catch(IOException ex) {
            
            Arrays.fill(m_ram, 0x00);
            ex.printStackTrace(System.err);
        }
    }
    
    @Override
    public void reset() {
        
        m_address = 0x0d;
        
        // Clear shutdown status as this only confuses the bochs bios
        m_ram[0x0f] = 0x00;
        
        // Update calendar time
        m_calendarTime = m_calendar.getTimeInMillis();
        
        // Reset alarm register
        m_alarmSeconds = 0;
        m_alarmMinutes = 0;
        m_alarmHours = 0;
        
        // Init rtc status register
        m_regA = 0x06;
        m_regB = REG_B_24H_MODE;
        m_regC = 0;
        m_regD = REG_D_CMOS_BATTERY_OK;
        
        if(m_calendar.getTimeZone().inDaylightTime(m_calendar.getTime()))
            m_regB |= REG_B_DAYLIGHT_SAVINGS;
        
        updateTimings();
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof PICs)
            m_pics = (PICs)component;
    }
    
    @Override
    public void shutdown() {
        
        try {
            
            FileResource.write(m_ram, m_ramFile);
        }
        catch(IOException ex) {
            
            ex.printStackTrace(System.err);
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return new int[] { 0x70, 0x71 };
    }
    
    @Override
    public int readIO8(int port) {
        
        int data;
        switch(port) {
            
            // CMOS address
            case 0x70:
                return m_address;
            
            // CMOS data
            case 0x71:
                if((m_address & 0x7f) <= 0x0d)
                    data = readRTC();
                else
                    data = m_ram[m_address & 0x7f];
                m_address = 0x0d;
                return data;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return new int[] { 0x70, 0x71 };
    }
    
    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // CMOS address
            case 0x70:
                m_address = data & 0x7f;
                break;
                
            // CMOS data
            case 0x71:
                if(m_address <= 0x0d)
                    writeRTC(data);
                else
                    m_ram[m_address] = data;
                m_address = 0x0d;
                break;
                
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Schedulable">

    @Override
    public void setBaseFrequency(float baseFrequency) {
        
        m_frequency = baseFrequency;
        updateTimings();
    }

    @Override
    public void updateClock(int cycles) {
        
        if(m_isPeriodicUpdateEnabled) {
            
            m_periodicINTRemaining -= cycles;
            while(m_periodicINTRemaining <= 0) {
                
                m_periodicINTRemaining += m_periodicINTCycles;
                raiseIRQ(REG_C_INT_PERIODIC);
            }
        }
        
        if(m_isUpdateEnabled) {
            
            m_updateINTRemaining -= cycles;
            while(m_updateINTRemaining <= 0) {
                
                m_updateINTRemaining += m_updateINTCycles;
                
                // Update calendar time
                m_calendarTime += 10l;
            }
        }
    }
    
    private void updateTimings() {
        
        int rateSelect = m_regA & REG_A_RATE_SELECT_MASK;
        
        // Periodic interrupt (FIXME: the cycle count will overflow, if the cpu frequency is high enough)
        m_isPeriodicUpdateEnabled = rateSelect != 0;
        if(m_isPeriodicUpdateEnabled) {
            
            m_periodicINTRateInHz = RTC_FREQUENCY / (float)(1 << (rateSelect - 1));
            m_periodicINTRemaining = m_periodicINTCycles = Scheduler.toFixedPoint(m_frequency / m_periodicINTRateInHz);
        }
        
        // One second update timer: As Scheduler.toFixedPoint(m_frequency) would overflow, we
        // select a hundredth of a second as the update rate. updateClock() has to take thi
        // s into account.
        m_updateINTRemaining = m_updateINTCycles = Scheduler.toFixedPoint(m_frequency / 100.0f);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="RTC register access">
    
    public void writeRTC(int data) {
        
        // Convert bcd to binary before updating the internal alarm / time registers
        if(m_address <= 0x09 && isBCDDataMode())
            data = ((data & 0xf0) >> 1) + ((data & 0xf0) >> 3) + (data & 0x0f);
        
        switch(m_address) {
            
            // Alarm
            case 0x01: if(data >= 0 && data <= 59) m_alarmSeconds = data; return;
            case 0x03: if(data >= 0 && data <= 59) m_alarmMinutes = data; return;
            case 0x05: if(data >= 0 && data <= (is24HourMode() ? 24 : 12)) m_alarmHours = data; return;
            
            // Current time
            case 0x00: if(data >= 0 && data <= 59) m_calendar.set(Calendar.SECOND, data); break;
            case 0x02: if(data >= 0 && data <= 59) m_calendar.set(Calendar.MINUTE, data); break;
            case 0x04: if(data >= 0 && data <= (is24HourMode() ? 24 : 12)) m_calendar.set(is24HourMode() ? Calendar.HOUR_OF_DAY : Calendar.HOUR, data); break;
            case 0x06: if(data >= 1 && data <= 7) m_calendar.set(Calendar.DAY_OF_WEEK, data - 1); break;
            case 0x07: if(data >= 1 && data <= 31) m_calendar.set(Calendar.DAY_OF_MONTH, data); break;
            case 0x08: if(data >= 1 && data <= 12) m_calendar.set(Calendar.MONTH, data - 1); break;
            case 0x09: if(data >= 0 && data <= 99) m_calendar.set(Calendar.YEAR, (m_ram[0x32] * 100) + (data % 100)); break;
            
            // RTC status register A
            case 0x0a:
                m_regA = data & 0x7f;
                updateTimings();
                return;
                
            // RTC status register B
            case 0x0b:
                m_regB = data;
                m_isUpdateEnabled = isClockUpdateEnabled(); // Cache this value
                return;
        }
        
        // Update calendar time
        m_calendarTime = m_calendar.getTimeInMillis();
    }
    
    private int readRTC() {
        
        int data = 0;
        
        if(m_address >= 0x00 && m_address <= 0x09)
            m_calendar.setTimeInMillis(m_calendarTime);
        
        switch(m_address) {
            
            // Current time / date
            case 0x00: data = m_calendar.get(Calendar.SECOND); break;
            case 0x02: data = m_calendar.get(Calendar.MINUTE); break;
            case 0x04: data = m_calendar.get(is24HourMode() ? Calendar.HOUR_OF_DAY : Calendar.HOUR); break;
            case 0x06: data = m_calendar.get(Calendar.DAY_OF_WEEK) + 1; break;
            case 0x07: data = m_calendar.get(Calendar.DAY_OF_MONTH); break;
            case 0x08: data = m_calendar.get(Calendar.MONTH) + 1; break;
            case 0x09: data = m_calendar.get(Calendar.YEAR) % 100; break;
            
            // Alarm
            case 0x01: data = m_alarmSeconds; break;
            case 0x03: data = m_alarmMinutes; break;
            case 0x05: data = m_alarmHours; break;
            
            // RTC status register A
            case 0x0a:
                return m_regA;
            
            // RTC status register B
            case 0x0b:
                return m_regB;
            
            // RTC status register C
            case 0x0c:
                data = m_regC;
                clearIRQ();
                return data;
                
            // RTC status register D
            case 0x0d:
                return m_regD;
        }
        
        // Convert alarm / current time to bcd if needed
        if(isBCDDataMode())
            data = (((data / 10) % 10) << 4) + (data % 10);
        
        return data;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interrupt handling">
    
    public void raiseIRQ(int irq) {
        
        m_regC |= irq;
        if((m_regC & (m_regB & REG_B_INT_ENABLE_MASK)) != 0) {
            
            m_regC |= REG_C_IRQ;
            m_pics.setInterrupt(8);
        }
    }
    
    public void clearIRQ() {
        
        m_regC = 0;
        m_pics.clearInterrupt(8);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Some helper methods">
    
    private boolean isBCDDataMode() {
        
        return (m_regB & REG_B_DATAMODE_BINARY) == 0;
    }
    
    private boolean is24HourMode() {
        
        return (m_regB & REG_B_24H_MODE) != 0;
    }
    
    private boolean isClockUpdateEnabled() {
        
        return (m_regB & REG_B_DISABLE_CLOCK_UPDATE) == 0;
    }
    
    // </editor-fold>
}
