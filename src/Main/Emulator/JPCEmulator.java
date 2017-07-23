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
package Main.Emulator;

import Hardware.ROM.Basic.BasicROM;
import Hardware.ROM.BIOS.BIOSROM;
import Hardware.DMAController.Intel8237;
import Hardware.HardwareComponent;
import IOMap.IOMap;
import Hardware.CPU.Intel8086.Intel8086;
import Hardware.InterruptController.Intel8259;
import Hardware.Keyboard.Keyboard;
import MemoryMap.MemoryMap;
import Hardware.Timer.Intel8253;
import Hardware.PPI.Intel8255;
import Hardware.SystemRAM.SystemRAM;
import Scheduler.Scheduler;
import Hardware.Int13Hook.Int13Hook;
import Hardware.Speaker.Speaker;
import Hardware.Video.VGA.TsengET4000.TsengET4000;
import IOMap.IOMapped;
import MemoryMap.MemoryMapped;
import Scheduler.Schedulable;
import java.awt.event.KeyListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.function.BiConsumer;
import Hardware.Video.GraphicsCardListener;



public final class JPCEmulator {
    
    /* ----------------------------------------------------- *
     * The current state of the emulator                     *
     * ----------------------------------------------------- */
    public enum JPCState { 
        
        Stopped, Paused, Running, Reset
    };
    private JPCState m_state;
    
    /* ----------------------------------------------------- *
     * The thread in which the emulation runs                *
     * ----------------------------------------------------- */
    private Thread m_mainThread;
    
    /* ----------------------------------------------------- *
     * Timer to update some statistics about the emulation   *
     * ----------------------------------------------------- */
    private Timer m_statisticTimer;
    private String m_statisticData;
            
    /* ----------------------------------------------------- *
     * PC XT hardware components                             *
     * ----------------------------------------------------- */
    private final ArrayList<HardwareComponent> m_components;
    private final IOMap m_ioMap;
    private final MemoryMap m_memMap;
    private final Scheduler m_scheduler;
    private final Int13Hook m_drives;
    private final Intel8086 m_cpu;
    private final Keyboard m_keyboard;
    private final TsengET4000 m_tsengET4000;
    private final Speaker m_speaker;
    
    
    
    public JPCEmulator(GraphicsCardListener gfxListener) {
        
        // Initialize all components
        m_ioMap = new IOMap();
        m_memMap = new MemoryMap(20);
        m_scheduler = new Scheduler();
        
        m_components = new ArrayList<>();
        addComponent(m_cpu = new Intel8086(m_ioMap, m_memMap, m_scheduler));
        addComponent(m_tsengET4000 = new TsengET4000(gfxListener));
        addComponent(m_keyboard = new Keyboard());
        addComponent(m_speaker = new Speaker());
        addComponent(new Intel8259());
        addComponent(new Intel8253());
        addComponent(new Intel8255());
        addComponent(new Intel8237());
        addComponent(new SystemRAM());
        addComponent(new BIOSROM());
        addComponent(new BasicROM());
        
        // Initialize interrupt 13h hook
        m_cpu.setInterruptHook(m_drives = new Int13Hook());
        
        // "Autowire" the components
        m_components.forEach(component -> m_components.forEach(c -> component.wireWith(c)));
        
        // Initial state is stopped
        m_state = JPCState.Stopped;
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Hardware component initialization">
    
    private void addComponent(HardwareComponent component) {
        
        if(m_components.contains(component)) {
            
            throw new IllegalArgumentException(String.format("The hardware component '%s' is already registered", component));
        }
        else {
            
            m_components.add(component);
            
            if(component instanceof IOMapped)
                m_ioMap.addDevice((IOMapped)component);
            
            if(component instanceof MemoryMapped)
                m_memMap.addDevice((MemoryMapped)component);
            
            if(component instanceof Schedulable)
                m_scheduler.addDevice((Schedulable)component);
            
            component.getSubComponents().forEach(subComponent -> addComponent(subComponent));
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Emulators main methods">
    
    public void run(BiConsumer<Exception, String> exceptionCallback) {

        if(m_mainThread != null)
            return;
        
        m_mainThread = new Thread(() -> {

            // Initialize statistic timer
            m_statisticTimer = new Timer();
            m_statisticTimer.schedule(new TimerTask() {

                @Override
                public void run() {
                    
                    m_statisticData = String.format("FPS: %d, CPU: %.0f%% (%.2f MHz)",
                            
                        m_tsengET4000.getElapsedFrames(),
                        m_scheduler.getEffectivePercentage(),
                        m_scheduler.getEffectiveMhz()
                    );
                }

            }, 1000, 1000);
            
            // Initialize all hardware components
            m_components.forEach(c -> c.init());
            
            //
            // Main loop
            //
            m_state = JPCState.Reset;
            while(m_state != JPCState.Stopped) {
                
                switch(m_state) {

                    // Let the cpu run a few code blocks
                    case Running:
                        try {

                            m_cpu.run(2048);
                        }
                        catch(Exception ex) {

                            exceptionCallback.accept(ex, m_cpu.toString());
                            m_state = JPCState.Stopped;
                        }
                        break;

                    // Reset everything
                    case Reset:
                        m_ioMap.reset();
                        m_memMap.reset();
                        m_scheduler.reset();
                        m_components.forEach(c -> c.reset());
                        m_state = JPCState.Running;
                        break;

                    // Don't waste cpu while paused
                    case Paused:
                        try {

                            Thread.sleep(250);
                        }
                        catch(InterruptedException ex) {
                        }
                        break;
                }
            }
            
            // Shutdown all hardware components
            m_components.forEach(c -> c.shutdown());
            
            // Kill statistic timer
            m_statisticTimer.cancel();
            m_statisticTimer = null;
            
            // Done
            m_mainThread = null;
        });
        
        m_mainThread.start();
    }
    
    public void resetHard() {
        
        if(m_state != JPCState.Stopped)
            m_state = JPCState.Reset;
    }
    
    public void resetSoft() {
        
        if(m_state != JPCState.Stopped)
            m_keyboard.pressCtrlAltDelete();
    }
    
    public void pause(boolean isPaused) {
        
        if(m_state != JPCState.Stopped) {
        
            if(isPaused)
                m_state = JPCState.Paused;
            else
                m_state = JPCState.Running;
        }
    }
    
    public void stop() {
        
        if(m_mainThread != null) {
            
            m_state = JPCState.Stopped;
            
            try {
                
                m_mainThread.join();
            }
            catch(InterruptedException ex) {
            }
        }
    }
    
    public void shutdown() {
        
        m_drives.shutdown();
    }
    
    public void setCPUFrequency(float frequency) {
        
        m_scheduler.setBaseFrequency(frequency);
    }
    
    public void setDriveIndicatorEnable(boolean isEnabled) {
        
        m_drives.setIndicatorEnable(isEnabled);
    }
    
    public void setSpeakerEnable(boolean isEnabled) {
        
        m_speaker.setEnable(isEnabled);
    }
    
    public void loadDiskImage(boolean isHardDisk, int driveIndex, File imageFile) throws IOException {
        
        m_drives.load(isHardDisk, driveIndex, imageFile);
    }
    
    public void ejectDiskImage(boolean isHardDisk, int driveIndex) throws IOException {
        
        m_drives.eject(isHardDisk, driveIndex);
    }
    
    public KeyListener getKeyListener() {
        
        return new JPCKeyMapper(m_keyboard);
    }
    
    public JPCState getCurrentState() {
        
        return m_state;
    }
    
    public String getStatisticData() {
        
        return m_statisticData;
    }
    
    public boolean isDriveIndicatorLit() {
        
        return m_drives.isIndicatorLit();
    }
    
    // </editor-fold>
}
