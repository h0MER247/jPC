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
package Main.UI;

import Main.UI.OutputPanel.JPCOutputPanel;
import Main.UI.MenuBar.JPCMenuBar;
import Main.Systems.AT386System;
import Main.Systems.AT486System;
import Main.Systems.JPCSystem;
import Main.Systems.XTSystem;
import Main.UI.Configuration.ToggleMenuItem;
import Main.UI.Configuration.FileMenuItem;
import Main.UI.Configuration.GroupMenuItem;
import Main.UI.MenuBar.JPCMenuEmulation;
import Main.UI.MenuBar.JPCMenuSystemSelection;
import Utility.MouseGrabber;
import Utility.SwingDialogs;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.filechooser.FileSystemView;



public final class JPCWindow extends JFrame {
    
    /* ----------------------------------------------------- *
     * UI components                                         *
     * ----------------------------------------------------- */
    private JPCMenuBar m_menuBar;
    private JPCOutputPanel m_outputPanel;
    
    /* ----------------------------------------------------- *
     * Fullscreen / Windowed application handling            *
     * ----------------------------------------------------- */
    private Dimension m_windowedSize;
    private Point m_windowedPosition;
    private boolean m_isFullscreenEnabled;
    
    /* ----------------------------------------------------- *
     * Mouse grabber                                         *
     * ----------------------------------------------------- */
    private final MouseGrabber m_mouseGrabber;
    
    /* ----------------------------------------------------- *
     * Reference to the emulated system                      *
     * ----------------------------------------------------- */
    private final ArrayList<JMenu> m_systemMenus;
    private JPCSystem m_system;
    
    
    
    public JPCWindow(int width, int height) {
        
        setTitle("jPC");
        setLayout(new BorderLayout());
        setFocusTraversalKeysEnabled(false);
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            
            @Override
            public void windowClosing(WindowEvent e) {
                
                onExit();
            }
        });
        addFocusListener(new FocusAdapter() {
            
            @Override
            public void focusLost(FocusEvent fe) {
                
                if(m_system != null)
                    m_system.getKeyAdapter().releaseAllKeys();
            }
        });
        
        try {
            
            initFullscreenKeyAccelerator();
            initOutputPanel(width, height);
            initMenuBar();
        }
        catch(IllegalArgumentException ex) {
            
            SwingDialogs.showExceptionMessage("Initialization of jPC failed", ex);
            onExit();
        }
        
        // Init mouse grabber
        m_mouseGrabber = new MouseGrabber(this, this::onUpdateTitle);
        
        // This holds the JMenus which are created for the purpose of
        // configuring the system
        m_systemMenus = new ArrayList<>();
        
        // Center the window on the screen
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        
        pack();
        setLocation((screen.width - getWidth()) / 2,
                    (screen.height - getHeight()) / 2);
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Initialization">
    
    private void initFullscreenKeyAccelerator() {
        
        addKeyListener(new KeyAdapter() {
            
            @Override
            public void keyReleased(KeyEvent e) {
                
                if(e.getKeyCode() == KeyEvent.VK_ENTER && e.isAltDown())
                    e.consume();
            }

            @Override
            public void keyPressed(KeyEvent e) {
                
                if(e.getKeyCode() == KeyEvent.VK_ENTER && e.isAltDown()) {
                    
                    e.consume();
                    onToggleFullscreen();
                }
            }
        });
    }
    
    private void initMenuBar() {
        
        setJMenuBar(m_menuBar = new JPCMenuBar());
        
        // Emulation
        JPCMenuEmulation emulationMenu = m_menuBar.getEmulationMenu();
        emulationMenu.addOnRunHandler(this::onRun);
        emulationMenu.addOnResetHandler(this::onResetHard);
        emulationMenu.addOnPauseHandler(this::onPause);
        emulationMenu.addOnStatisticHandler(this::onSetStatisticEnable);
        emulationMenu.addOnFullscreenHandler(this::onToggleFullscreen);
        emulationMenu.addOnExitHandler(this::onExit);
        emulationMenu.addOnCtrlAltDeleteHandler(this::onResetSoft);
        emulationMenu.addOnScreenshotHandler(this::onTakeScreenshot);
        
        // System selection menu
        JPCMenuSystemSelection systemMenu = emulationMenu.getSystemSelectionMenu();
        systemMenu.addSystemHandler(XTSystem.SYSTEM_NAME, this::initSystem);
        systemMenu.addSystemHandler(AT386System.SYSTEM_NAME, this::initSystem);
        systemMenu.addSystemHandler(AT486System.SYSTEM_NAME, this::initSystem);
        
        m_menuBar.updateUI();
    }
    
    private void initOutputPanel(int width, int height) {
        
        m_outputPanel = new JPCOutputPanel();
        m_outputPanel.setBackground(Color.black);
        m_outputPanel.setDoubleBuffered(true);
        m_outputPanel.setPreferredSize(new Dimension(width, height));
        
        add(m_outputPanel, BorderLayout.CENTER);
    }
    
    private void initSystem(String systemName) {
        
        // Tear down the previous selected system
        if(m_system != null) {
            
            // Remove old keyboard and mouse listener
            removeKeyListener(m_system.getKeyAdapter());
            removeMouseListener(m_system.getMouseAdapter());
            removeMouseMotionListener(m_system.getMouseAdapter());
            removeMouseWheelListener(m_system.getMouseAdapter());
        
            // Also remove the configuration menus
            m_systemMenus.forEach(menu -> m_menuBar.remove(menu));
            m_systemMenus.clear();
        }
        
        // Initialize new system
        JPCSystem system;
        try {
            
            switch(systemName) {

                case XTSystem.SYSTEM_NAME:
                    system = new XTSystem(m_outputPanel);
                    break;

                case AT386System.SYSTEM_NAME:
                    system = new AT386System(m_outputPanel);
                    break;

                case AT486System.SYSTEM_NAME:
                    system = new AT486System(m_outputPanel);
                    break;

                default:
                    throw new IllegalArgumentException("Unknown system selected");
            }
        }
        catch(IllegalArgumentException ex) {
            
            SwingDialogs.showExceptionMessage("Error while creating the system", ex);
            return;
        }
        
        // Initialize keyboard and mouse listener
        addKeyListener(system.getKeyAdapter());
        addMouseListener(system.getMouseAdapter());
        addMouseMotionListener(system.getMouseAdapter());
        addMouseWheelListener(system.getMouseAdapter());
        
        // Initialize configuration menus
        system.forEachConfiguration((category, configList) -> {
            
            JMenu menu = new JMenu(category);
            configList.forEach(config -> {

                switch(config.getType()) {

                    case ToggleValue:
                        menu.add(new ToggleMenuItem(config, this::isSystemResetOkay));
                        break;

                    case FileValue:
                        menu.add(new FileMenuItem(config, this::isSystemResetOkay));
                        break;

                    case ToggleGroup:
                        menu.add(new GroupMenuItem(config, this::isSystemResetOkay));
                        break;
                }
            });

            m_systemMenus.add(menu);
            m_menuBar.add(menu);
        });
        system.configure();
        
        // Setup output panel
        m_outputPanel.setSystem(system);
        
        // Done
        m_system = system;
        m_menuBar.getEmulationMenu().setRunEnabled(true);
        m_menuBar.updateUI();
        
        onUpdateTitle();
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Menubar callbacks">
    
    private void onRun(boolean isRunning) {
        
        if(isRunning)
            m_system.run(this::onEmulatorException);
        else
            m_system.stop();
        
        m_mouseGrabber.setEnable(isRunning);
        
        m_menuBar.getEmulationMenu().setSystemEnabled(!isRunning);
        m_menuBar.getEmulationMenu().setPauseSelected(false);
        m_menuBar.getEmulationMenu().setPauseEnabled(isRunning);
        m_menuBar.getEmulationMenu().setResetEnabled(isRunning);
        m_menuBar.getEmulationMenu().setStatisticEnabled(isRunning);
        m_menuBar.getEmulationMenu().setCtrlAltDeleteEnabled(isRunning);
        m_menuBar.getEmulationMenu().setScreenshotEnabled(isRunning);
        m_menuBar.getEmulationMenu().setFullscreenEnabled(isRunning);
    }
    
    private void onEmulatorException(Exception ex) {
        
        m_menuBar.getEmulationMenu().setSystemEnabled(true);
        m_menuBar.getEmulationMenu().setRunSelected(false);
        m_menuBar.getEmulationMenu().setPauseSelected(false);
        m_menuBar.getEmulationMenu().setPauseEnabled(false);
        m_menuBar.getEmulationMenu().setResetEnabled(false);
        m_menuBar.getEmulationMenu().setStatisticEnabled(false);
        m_menuBar.getEmulationMenu().setCtrlAltDeleteEnabled(false);
        m_menuBar.getEmulationMenu().setFullscreenEnabled(false);
        m_menuBar.getEmulationMenu().setScreenshotEnabled(false);
        
        m_mouseGrabber.setEnable(false);
        
        SwingDialogs.showExceptionMessage("jPC died", ex);
        ex.printStackTrace(System.err);
    }
    
    private void onResetHard() {
        
        m_system.reset();
    }
    
    private void onPause(boolean isPaused) {
        
        m_system.pause(isPaused);
    }
    
    private void onResetSoft() {
        
        m_system.getKeyAdapter().sendCtrlAltDelete();
    }
    
    private void onSetStatisticEnable(boolean isEnabled) {
        
        m_outputPanel.setStatisticEnabled(isEnabled);
    }
    
    private void onToggleFullscreen() {

        m_isFullscreenEnabled ^= true;
        
        setVisible(false);
        dispose();
        if(m_isFullscreenEnabled) {

            m_menuBar.setVisible(false);
            m_windowedSize = getSize();
            m_windowedPosition = getLocation();
            setUndecorated(true);
            setExtendedState(JFrame.MAXIMIZED_BOTH);
        }
        else {

            m_menuBar.setVisible(true);
            setSize(m_windowedSize);
            setLocation(m_windowedPosition);
            setUndecorated(false);
            setExtendedState(JFrame.NORMAL);
        }
        setVisible(true);
    }
    
    private void onExit() {
        
        if(m_system != null)
            m_system.stop();
        
        setVisible(false);
        dispose();
        
        System.exit(0);
    }
    
    private void onTakeScreenshot() {
        
        File defaultUserDir = FileSystemView.getFileSystemView().getDefaultDirectory();
        File screenshotDir = new File(defaultUserDir, ".\\jPC\\");
        
        try {
            
            if(!Files.isDirectory(screenshotDir.toPath()))
                Files.createDirectory(screenshotDir.toPath());
            
            int idx = screenshotDir.listFiles((d, n) -> n.startsWith("screen") && n.endsWith(".png")).length;
            
            m_outputPanel.takeScreenshot(new File(screenshotDir, String.format("screen%d.png", idx)));
        }
        catch(IOException ex) {
            
            SwingDialogs.showExceptionMessage("Your screenshot can't be saved", ex);
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Reset message and JFrame title updating">
    
    private boolean isSystemResetOkay() {
        
        boolean wasPaused = m_system.isPaused();
        
        // Make sure the system has come to rest before trying to
        // modify any critical values. Yes it is synchronized on a
        // non final field but i think that this is okay, as the system
        // can't be changed while it is running - so thats like as it
        // is final? :)   TODO: Investigate this further
        if(!m_system.isStopped()) {
            
            try {

                synchronized(m_system) {

                    m_system.pause(true);
                    while(!m_system.isPaused())
                        m_system.wait();
                }
            }
            catch(InterruptedException ex) {
            }
        }
        
        // Make sure that the user really wants to reset the system
        boolean res = m_system.isStopped() || SwingDialogs.showConfirmationMessage(

            "Attention: This will reset the system",
            "The system will now reset in order to apply your changes.\n\nDo you want to continue?"
        );
        if(res)
            onResetHard();
        
        // Resume or keep paused
        if(!m_system.isStopped())
            m_system.pause(wasPaused);
        
        return res;
    }
    
    private void onUpdateTitle() {
        
        String title = "jPC";
        
        if(m_system != null)
            title += " - [" + m_system.getSystemName() + "]";
        
        if(m_mouseGrabber.isMouseGrabbed())
            title += "   (Mouse grabbed: Release it by double clicking the right mouse button)";
        
        setTitle(title);
    }
    
    // </editor-fold>
}
