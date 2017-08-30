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
package Main.UI.MenuBar;

import java.util.function.Consumer;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;



public final class JPCMenuEmulation extends JMenu {
    
    private final JPCMenuSystemSelection m_system;
    private final JMenuItem m_run;
    private final JMenuItem m_reset;
    private final JMenuItem m_pause;
    private final JMenuItem m_sendCtrlAltDelete;
    private final JMenuItem m_screenshot;
    private final JMenuItem m_statistic;
    private final JMenuItem m_fullscreen;
    private final JMenuItem m_exit;
    
    
    
    public JPCMenuEmulation() {
        
        super("Emulation");
        
        add(m_system = new JPCMenuSystemSelection());
        add(m_run = new JCheckBoxMenuItem("Run"));
        add(m_reset = new JMenuItem("Reset"));
        add(m_pause = new JCheckBoxMenuItem("Pause"));
        add(m_sendCtrlAltDelete = new JMenuItem("Send Ctrl+Alt+Delete"));
        add(m_screenshot = new JMenuItem("Take screenshot"));
        addSeparator();
        add(m_statistic = new JCheckBoxMenuItem("Enable statistics"));
        addSeparator();
        add(m_fullscreen = new JMenuItem("Fullscreen"));
        addSeparator();
        add(m_exit = new JMenuItem("Exit"));
        
        setSystemEnabled(true);
        setRunEnabled(false);
        setResetEnabled(false);
        setPauseEnabled(false);
        setCtrlAltDeleteEnabled(false);
        setScreenshotEnabled(false);
        setStatisticEnabled(false);
        setFullscreenEnabled(false);
    }
    
    
    
    public JPCMenuSystemSelection getSystemSelectionMenu() {
        
        return m_system;
    }
    
    
    
    public void addOnRunHandler(Consumer<Boolean> handler) {
        
        m_run.addActionListener(l -> handler.accept(m_run.isSelected()));
    }
    
    public void addOnResetHandler(Runnable handler) {
        
        m_reset.addActionListener(l -> handler.run());
    }
    
    public void addOnPauseHandler(Consumer<Boolean> handler) {
        
        m_pause.addActionListener(l -> handler.accept(m_pause.isSelected()));
    }
    
    public void addOnCtrlAltDeleteHandler(Runnable handler) {
        
        m_sendCtrlAltDelete.addActionListener(l -> handler.run());
    }
    
    public void addOnScreenshotHandler(Runnable handler) {
        
        m_screenshot.addActionListener(l -> handler.run());
    }
    
    public void addOnStatisticHandler(Consumer<Boolean> handler) {
        
        m_statistic.addActionListener(l -> handler.accept(m_statistic.isSelected()));
    }
    
    public void addOnFullscreenHandler(Runnable handler) {
        
        m_fullscreen.addActionListener(l -> handler.run());
    }
    
    public void addOnExitHandler(Runnable handler) {
        
        m_exit.addActionListener(l -> handler.run());
    }
    
    
    
    public void setRunSelected(boolean isSelected) {
        
        m_run.setSelected(isSelected);
    }
    
    public void setPauseSelected(boolean isSelected) {
        
        m_pause.setSelected(isSelected);
    }
    
    public void setStatisticSelected(boolean isSelected) {
        
        m_statistic.setSelected(isSelected);
    }
    
    
    
    public boolean isPauseSelected() {
        
        return m_pause.isSelected();
    }
    
    
    
    public void setSystemEnabled(boolean isEnabled) {
    
        m_system.setEnabled(isEnabled);
    }
    
    public void setRunEnabled(boolean isEnabled) {
        
        m_run.setEnabled(isEnabled);
    }
    
    public void setResetEnabled(boolean isEnabled) {
        
        m_reset.setEnabled(isEnabled);
    }
    
    public void setPauseEnabled(boolean isEnabled) {
        
        m_pause.setEnabled(isEnabled);
    }
    
    public void setCtrlAltDeleteEnabled(boolean isEnabled) {
        
        m_sendCtrlAltDelete.setEnabled(isEnabled);
    }
    
    public void setScreenshotEnabled(boolean isEnabled) {
        
        m_screenshot.setEnabled(isEnabled);
    }
    
    public void setStatisticEnabled(boolean isEnabled) {
        
        m_statistic.setEnabled(isEnabled);
    }
    
    public void setFullscreenEnabled(boolean isEnabled) {
        
        m_fullscreen.setEnabled(isEnabled);
    }
}
