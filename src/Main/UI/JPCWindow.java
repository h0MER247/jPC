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

import Utility.EmptyFileCreator;
import Main.Emulator.JPCEmulator;
import Main.Emulator.JPCEmulator.JPCState;
import Main.UI.MenuBar.JPCMenuBar;
import Main.UI.OutputPanel.JPCOutputPanel;
import Main.UI.OutputPanel.JPCOutputPanel.QualitySettings;
import Utility.SwingDialogs;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.EventQueue;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.prefs.Preferences;
import javax.swing.JFrame;
import javax.swing.filechooser.FileSystemView;



public final class JPCWindow extends JFrame {
    
    /* ----------------------------------------------------- *
     * Emulator configuration                                *
     * ----------------------------------------------------- */
    public static final String CONFIG_CPU_FREQUENCY = "cpu.frequency";
    public static final String CONFIG_DRIVE_INDICATOR_ENABLE = "drive.indicatorEnable";
    public static final String CONFIG_DRIVE_FDD = "drive.fdd";
    public static final String CONFIG_DRIVE_HDD = "drive.hdd";
    public static final String CONFIG_SPEAKER_ENABLE = "speaker.enable";
    private Preferences m_configuration;
    
    /* ----------------------------------------------------- *
     * UI components                                         *
     * ----------------------------------------------------- */
    private JPCMenuBar m_menuBar;
    private JPCOutputPanel m_outputPanel;
    private JPCEmulator m_emulator;
    
    /* ----------------------------------------------------- *
     * Fullscreen / Windowed application handling            *
     * ----------------------------------------------------- */
    private Dimension m_windowedSize;
    private Point m_windowedPosition;
    private boolean m_isFullscreenEnabled;
    
    /* ----------------------------------------------------- *
     * Statistics                                            *
     * ----------------------------------------------------- */
    private boolean m_isStatisticVisible;
    
    
    
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
                
                m_emulator.releaseAllKeys();
            }
        });
        
        try {
            
            initFullscreenKeyAccelerator();
            initMenuBar();
            initOutputPanel(width, height);
            initEmulator();
            initConfiguration();
        }
        catch(IllegalArgumentException ex) {
            
            SwingDialogs.showExceptionMessage("Initialization of jPC failed", ex);
            onExit();
        }
        
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
        m_menuBar.getEmulationMenu().addOnRunHandler(this::onRun);
        m_menuBar.getEmulationMenu().addOnResetHandler(this::onResetHard);
        m_menuBar.getEmulationMenu().addOnPauseHandler(this::onPause);
        m_menuBar.getEmulationMenu().addOnStatisticHandler(this::onSetStatisticEnable);
        m_menuBar.getEmulationMenu().addOnFullscreenHandler(this::onToggleFullscreen);
        m_menuBar.getEmulationMenu().addOnExitHandler(this::onExit);
        m_menuBar.getEmulationMenu().addOnCtrlAltDeleteHandler(this::onResetSoft);
        m_menuBar.getEmulationMenu().addOnScreenshotHandler(this::onTakeScreenshot);
        
        // CPU
        m_menuBar.getCPUMenu().addOnFrequencyHandler(this::onSetCPUFrequency);
        
        // Drives
        m_menuBar.getDrivesMenu().addOnIndicatorHandler(this::onSetDriveIndicatorEnable);
        m_menuBar.getDrivesMenu().addOnFDDSelectHandler(driveIndex -> onChooseDiskImage(false, driveIndex));
        m_menuBar.getDrivesMenu().addOnFDDEjectHandler(driveIndex -> onEjectDiskImage(false, driveIndex));
        m_menuBar.getDrivesMenu().addOnHDDSelectHandler(driveIndex -> onChooseDiskImage(true, driveIndex));
        m_menuBar.getDrivesMenu().addOnHDDEjectHandler(driveIndex -> onEjectDiskImage(true, driveIndex));
        m_menuBar.getDrivesMenu().addOnHDDCreateHandler(driveIndex -> onCreateHDDImage(driveIndex));
        
        // Speaker
        m_menuBar.getSpeakerMenu().addOnEnableHandler(this::onSetSpeakerEnable);
    }
    
    private void initOutputPanel(int width, int height) {
        
        m_outputPanel = new JPCOutputPanel(QualitySettings.HighQuality){
            
            @Override
            public boolean isEmulationStopped() {
                
                return m_emulator.getCurrentState() == JPCState.Stopped;
            }
            
            @Override
            public boolean isEmulationPaused() {
                
                return m_emulator.getCurrentState() == JPCState.Paused;
            }
            
            @Override
            public boolean isStatisticVisible() {
                
                return m_isStatisticVisible;
            }
            
            @Override
            public boolean isFullscreenEnabled() {
                
                return m_isFullscreenEnabled;
            }
            
            @Override
            public boolean isDriveIndicatorLit() {
                
                return m_emulator.isDriveIndicatorLit();
            }
            
            @Override
            public String getStatisticData() {
                
                return m_emulator.getStatisticData();
            }
        };
        m_outputPanel.setBackground(Color.black);
        m_outputPanel.setDoubleBuffered(true);
        m_outputPanel.setPreferredSize(new Dimension(width, height));
        
        add(m_outputPanel, BorderLayout.CENTER);
    }
    
    private void initEmulator() {
        
        m_emulator = new JPCEmulator(m_outputPanel);
        addKeyListener(m_emulator.getKeyListener());
    }
    
    private void initConfiguration() {
        
        m_configuration = Preferences.userRoot().node("jPC");
        
        onSetCPUFrequency(m_configuration.getFloat(CONFIG_CPU_FREQUENCY, 16000000.0f));
        onSetDriveIndicatorEnable(m_configuration.getBoolean(CONFIG_DRIVE_INDICATOR_ENABLE, false));
        onSetSpeakerEnable(m_configuration.getBoolean(CONFIG_SPEAKER_ENABLE, true));
        
        for(int i = 0; i < 2; i++)
            setDiskImage(false, i, new File(m_configuration.get(CONFIG_DRIVE_FDD + String.valueOf(i), "")));
        for(int i = 0; i < 4; i++)
            setDiskImage(true, i, new File(m_configuration.get(CONFIG_DRIVE_HDD + String.valueOf(i), "")));
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Menubar callbacks">
    
    private void onRun(boolean isRunning) {
        
        if(isRunning)
            m_emulator.run(this::onEmulatorException);
        else
            m_emulator.stop();
        
        m_menuBar.getEmulationMenu().setPauseSelected(false);
        m_menuBar.getEmulationMenu().setPauseEnabled(isRunning);
        m_menuBar.getEmulationMenu().setResetEnabled(isRunning);
        m_menuBar.getEmulationMenu().setStatisticEnabled(isRunning);
        m_menuBar.getEmulationMenu().setCtrlAltDeleteEnabled(isRunning);
        m_menuBar.getEmulationMenu().setScreenshotEnabled(isRunning);
        
        repaint();
    }
    
    private void onEmulatorException(Exception ex, String cpuDump) {
        
        EventQueue.invokeLater(() -> { 
            
            m_menuBar.getEmulationMenu().setRunSelected(false);
            m_menuBar.getEmulationMenu().setPauseSelected(false);
            m_menuBar.getEmulationMenu().setPauseEnabled(false);
            m_menuBar.getEmulationMenu().setResetEnabled(false);
            m_menuBar.getEmulationMenu().setStatisticEnabled(false);
            m_menuBar.getEmulationMenu().setCtrlAltDeleteEnabled(false);
            m_menuBar.getEmulationMenu().setScreenshotEnabled(false);
            
            SwingDialogs.showExceptionMessage("jPC died", ex, cpuDump);
            repaint();
        });
    }
    
    private void onResetHard() {
        
        m_emulator.resetHard();
        repaint();
    }
    
    private void onPause(boolean isPaused) {
        
        m_emulator.pause(isPaused);
        repaint();
    }
    
    private void onResetSoft() {
        
        m_emulator.resetSoft();
        repaint();
    }
    
    private void onSetStatisticEnable(boolean isVisible) {
        
        m_isStatisticVisible = isVisible;
        repaint();
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
            setCursor(getToolkit().createCustomCursor(new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB),
                                                      new Point(),
                                                      null));
        }
        else {

            m_menuBar.setVisible(true);
            setSize(m_windowedSize);
            setLocation(m_windowedPosition);
            setUndecorated(false);
            setExtendedState(JFrame.NORMAL);
            setCursor(Cursor.getDefaultCursor());
        }
        setVisible(true);
    }
    
    private void onExit() {
        
        setVisible(false);
        
        if(m_emulator != null) {
            
            if(m_emulator.getCurrentState() != JPCState.Stopped) {
                
                m_emulator.stop();
                m_emulator.shutdown();
            }
        }
        
        dispose();
        System.exit(0);
    }
    
    private void onSetCPUFrequency(float frequency) {
        
        m_emulator.setCPUFrequency(frequency);
        m_configuration.putFloat(CONFIG_CPU_FREQUENCY, frequency);
        m_menuBar.getCPUMenu().setFrequencySelected(frequency);
    }
    
    private void onSetDriveIndicatorEnable(boolean isEnabled) {
        
        m_emulator.setDriveIndicatorEnable(isEnabled);
        m_configuration.putBoolean(CONFIG_DRIVE_INDICATOR_ENABLE, isEnabled);
        m_menuBar.getDrivesMenu().setEnableIndicatorSelected(isEnabled);
    }
    
    private void onSetSpeakerEnable(boolean isEnabled) {
        
        m_emulator.setSpeakerEnable(isEnabled);
        m_configuration.putBoolean(CONFIG_SPEAKER_ENABLE, isEnabled);
        m_menuBar.getSpeakerMenu().setEnableSpeakerSelected(isEnabled);
    }
    
    private void onChooseDiskImage(boolean isHardDisk, int driveIndex) {
        
        File file = SwingDialogs.showFileChooser("Choose disk image", true);
        if(file != null)
            setDiskImage(isHardDisk, driveIndex, file);
    }
    
    private void onEjectDiskImage(boolean isHardDisk, int driveIndex) {
        
        setDiskImage(isHardDisk, driveIndex, new File(""));
    }
    
    private void setDiskImage(boolean isHardDisk, int driveIndex, File file) {
        
        // TODO: Clean this mess up
        
        if(m_emulator.getCurrentState() == JPCState.Stopped || !isHardDisk ||
           SwingDialogs.showConfirmationMessage("Attention", "This will reset jPC. Do you want to continue?")) {
            
            try {
                
                if(file.exists()) {
                    
                    m_emulator.loadDiskImage(isHardDisk, driveIndex, file);
                }
                else {
                    
                    file = new File(""); // <- This is just here in case the file doesn't exist (anymore)
                    m_emulator.ejectDiskImage(isHardDisk, driveIndex);    
                }
            }
            catch(IOException ex) {

                file = new File("");

                SwingDialogs.showInformationMessage("Image file could not be loaded", ex.getMessage());
            }

            m_configuration.put((isHardDisk ? CONFIG_DRIVE_HDD : CONFIG_DRIVE_FDD) + String.valueOf(driveIndex), file.toString());
            
            if(isHardDisk) {
                
                m_menuBar.getDrivesMenu().setHDDImageName(driveIndex, file.getName());
                onResetHard();
            }
            else {
                
                m_menuBar.getDrivesMenu().setFDDImageName(driveIndex, file.getName());
            }
        }
    }
    
    private void onCreateHDDImage(int driveIndex) {
        
        EmptyFileCreator res = (EmptyFileCreator)SwingDialogs.showInputDialog(
                
            "Disk size",
            "Choose the size of the hard disk:",
            new EmptyFileCreator[] {

                new EmptyFileCreator(16 * 1024 * 1024),
                new EmptyFileCreator(32 * 1024 * 1024),
                new EmptyFileCreator(64 * 1024 * 1024),
                new EmptyFileCreator(128 * 1024 * 1024),
                new EmptyFileCreator(256 * 1024 * 1024),
                new EmptyFileCreator(512 * 1024 * 1024)
            },
            3
        );
        
        try {
            
            if(res != null) {

                File file = SwingDialogs.showFileChooser("Save image as", false);
                if(file != null) {

                    if(!file.exists() || SwingDialogs.showConfirmationMessage("File already exists",
                                                                              "This will overwrite the file. Do you want to continue?")) {
                        
                        res.write(file);
                        setDiskImage(true, driveIndex, file);
                    }
                }
            }
        }
        catch(IOException ex) {
            
            SwingDialogs.showExceptionMessage("Error occured", ex);
        }
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
            
            SwingDialogs.showExceptionMessage("Screenshot couldn't be stored", ex);
        }
    }
    
    // </editor-fold>
}
