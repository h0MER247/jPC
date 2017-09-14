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
package Main.Systems;

import Hardware.CMOS.CMOS;
import Hardware.CPU.CPU;
import Hardware.HardwareComponent;
import Hardware.IDE.IDE;
import Hardware.Keyboard.JPCKeyboardAdapter;
import Hardware.Keyboard.Keyboard;
import Hardware.Mouse.JPCMouseAdapter;
import Hardware.Mouse.Mouse;
import Hardware.Video.GraphicsCard;
import IOMap.IOMap;
import IOMap.IOMapped;
import MemoryMap.MemoryMap;
import MemoryMap.MemoryMapped;
import Scheduler.Schedulable;
import Scheduler.Scheduler;
import java.awt.EventQueue;
import java.util.ArrayList;
import java.util.TreeMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;



public abstract class JPCSystem {
    
    /* ----------------------------------------------------- *
     * System state listener                                 *
     * ----------------------------------------------------- */
    public interface JPCSystemStateListener {
    
        void onStateChanged();
    }
    private final ArrayList<JPCSystemStateListener> m_listener;
    
    /* ----------------------------------------------------- *
     * System name                                           *
     * ----------------------------------------------------- */
    private final String m_systemName;
    
    /* ----------------------------------------------------- *
     * Configuration of the hardware components              *
     * ----------------------------------------------------- */
    private final TreeMap<String, ArrayList<ComponentConfig>> m_config;
    
    /* ----------------------------------------------------- *
     * Hardware components                                   *
     * ----------------------------------------------------- */
    private final IOMap m_ioMap;
    private final MemoryMap m_memMap;
    private final Scheduler m_scheduler;
    private final ArrayList<HardwareComponent> m_components;
    private JPCMouseAdapter m_mouseAdapter;
    private JPCKeyboardAdapter m_keyboardAdapter;
    private GraphicsCard m_graphicsCard;
    private IDE m_primaryIDE;
    private IDE m_secondaryIDE;
    private CPU m_cpu;
    private CMOS m_cmos;
    
    /* ----------------------------------------------------- *
     * The thread in which the emulation runs                *
     * ----------------------------------------------------- */
    private Thread m_mainThread;
    
    /* ----------------------------------------------------- *
     * The current state of the emulator                     *
     * ----------------------------------------------------- */
    private boolean m_isRunning;
    private boolean m_flagReset;
    private boolean m_flagPause;
    private boolean m_isPaused;
    
    
    
    public JPCSystem(String systemName,
                     int sizeOfAddressbusInBit) {
        
        m_systemName = systemName;
        m_config = new TreeMap<>();
        
        m_listener = new ArrayList<>();
        
        m_ioMap = new IOMap();
        m_memMap = new MemoryMap(sizeOfAddressbusInBit);
        m_scheduler = new Scheduler();
        m_components = new ArrayList<>();
        
        m_isRunning = false;
    }
    
    
    
    public final void run(Consumer<Exception> exceptionHandler) {
        
        if(m_mainThread != null)
            return;
        
        m_mainThread = new Thread(() -> {
            
            // Auto wire and initialize all the components
            m_components.forEach(cA -> m_components.forEach(cB -> cA.wireWith(cB)));
            m_components.forEach(c -> c.init());
            
            try {
                
                m_flagReset = true;
                m_flagPause = false;
                m_isRunning = true;
                m_isPaused = false;
                
                notifyListener();
                
                while(m_isRunning) {
                    
                    // Reset everything
                    if(m_flagReset) {
                        
                        configure();
                        
                        m_ioMap.reset();
                        m_memMap.reset();
                        m_components.forEach(c -> c.reset());
                        if(m_cmos != null) {
                            
                            m_components.forEach(c -> c.updateCMOS(m_cmos.getCMOSMap()));
                        }
                        m_scheduler.reset();
                        
                        m_flagReset = false;
                    }
                    
                    // Let the cpu run a few code blocks
                    m_cpu.run(8192);
                    
                    // Don't waste cpu while being paused
                    while(m_flagPause) {
                        
                        synchronized(this) {

                            m_isPaused = true;
                            this.notifyAll();
                        }
                        
                        notifyListener();
                        
                        while(m_flagPause && m_isRunning) {

                            Thread.sleep(250);
                        }
                        m_isPaused = false;
                        
                        if(!m_isRunning)
                            break;
                    }
                }
            }
            catch(Exception ex) {
                
                m_isRunning = false;
                m_flagPause = false;
                m_isPaused = false;
                
                // Report exceptions with an added dump of the cpus state
                if(exceptionHandler != null)
                    EventQueue.invokeLater(() -> exceptionHandler.accept(new Exception("\n\n" + m_cpu.toString() + "\n", ex)));
            }
            
            notifyListener();
            
            m_components.forEach(c -> c.shutdown());
            m_mainThread = null;
        });
        
        m_mainThread.start();
    }
    
    public final void reset() {
        
        m_flagReset = true;
    }
        
    public final void pause(boolean isPaused) {
        
        m_flagPause = isPaused;
    }
    
    public final void stop() {
        
        if(m_mainThread != null) {
            
            m_isRunning = false;
            try {
                
                m_mainThread.join();
                m_mainThread = null;
            }
            catch(InterruptedException ex) {
            }
        }
    }
    
    public final boolean isPaused() {
        
        return m_isPaused;
    }
    
    public final boolean isStopped() {
        
        return !m_isRunning;
    }
    
    public final void addStateListener(JPCSystemStateListener listener) {
        
        m_listener.add(listener);
    }
    
    public final void removeStateListener(JPCSystemStateListener listener) {
        
        m_listener.remove(listener);
    }
    
    private void notifyListener() {
        
        EventQueue.invokeLater(() -> m_listener.forEach(l -> l.onStateChanged()));
    }
    
    
    
    public final JPCKeyboardAdapter getKeyAdapter() {
        
        return m_keyboardAdapter;
    }
    
    public final JPCMouseAdapter getMouseAdapter() {
        
        return m_mouseAdapter;
    }
    
    
    
    public final String getSystemName() {
        
        return m_systemName;
    }
    
    public final String getStatistics() {
        
        return String.format("FPS: %d, CPU: %.0f%% (%.2f MHz)",
                            
            m_graphicsCard.getElapsedFrames(),
            m_scheduler.getEffectivePercentage(),
            m_scheduler.getEffectiveMhz()
        );
    }
    
    public final boolean isDriveIndicatorLit() {
        
        boolean result = false;
        
        if(m_primaryIDE != null)
            result |= m_primaryIDE.isDriveIndicatorLit();
        if(m_secondaryIDE != null)
            result |= m_secondaryIDE.isDriveIndicatorLit();
        
        return result;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Adding of hardware components">
    
    protected final IOMap getIOMap() {
        
        return m_ioMap;
    }
    
    protected final MemoryMap getMemoryMap() {
        
        return m_memMap;
    }
    
    protected final Scheduler getScheduler() {
        
        return m_scheduler;
    }
    
    protected final void addComponent(HardwareComponent component) {
        
        if(m_components.contains(component)) {
            
            throw new IllegalArgumentException(String.format("The hardware component '%s' is already registered", component));
        }
        else {
            
            if(component instanceof IDE) {
                
                IDE ide = (IDE)component;
                if(ide.isPrimaryAdapter())
                    m_primaryIDE = ide;
                else
                    m_secondaryIDE = ide;
            }
            
            if(component instanceof Mouse)
                m_mouseAdapter = ((Mouse)component).getAdapter();
            
            if(component instanceof Keyboard)
                m_keyboardAdapter = ((Keyboard)component).getAdapter();
            
            if(component instanceof GraphicsCard)
                m_graphicsCard = (GraphicsCard)component;
            
            if(component instanceof CPU)
                m_cpu = (CPU)component;
            
            if(component instanceof CMOS)
                m_cmos = (CMOS)component;
            
            
            if(component instanceof IOMapped)
                m_ioMap.addDevice((IOMapped)component);
            
            if(component instanceof MemoryMapped)
                m_memMap.addDevice((MemoryMapped)component);
            
            if(component instanceof Schedulable)
                m_scheduler.addDevice((Schedulable)component);
            
            
            // Get the component configuration
            if(!component.getConfigCategory().isEmpty()) {
                
                component.provideConfigValues(

                    new ComponentConfig.Builder(

                        m_systemName,
                        component.getConfigCategory(),
                        this::addComponentConfig
                    )
                );
            }
            
            m_components.add(component);
            component.getSubComponents().forEach(subComponent -> addComponent(subComponent));
        }
    }
    
    private void addComponentConfig(ComponentConfig config) {
        
        ArrayList<ComponentConfig> configList;
        
        configList = m_config.get(config.getCategory());
        if(configList == null)
            m_config.put(config.getCategory(), configList = new ArrayList<>());
        
        configList.add(config);
    }
    
    public final void configure() {
        
        m_config.values()
                .forEach(configList -> {
                    
            configList.forEach(config -> config.initValue());
        });
    }
    
    public final void forEachConfiguration(BiConsumer<String, ArrayList<ComponentConfig>> consumer) {
        
        m_config.forEach((category, componentList) -> {
            
            consumer.accept(category, new ArrayList<>(componentList));
        });
    }
    
    // </editor-fold>
}
