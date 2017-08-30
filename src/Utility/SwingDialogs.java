/*
 * Copyright (C) 2017 thmtr
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
package Utility;

import java.awt.Dimension;
import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.filechooser.FileFilter;



public final class SwingDialogs {
    
    private SwingDialogs() {
    }
    
    
    
    public static File showFileChooser(String title, boolean isOpenDialog) {
        
        FileFilter filter = new FileFilter() {
            
            @Override
            public boolean accept(File f) {
                
                String fileName = f.getName().toLowerCase();
                
                return f.isDirectory() ||
                       fileName.endsWith(".bin") ||
                       fileName.endsWith(".ima") ||
                       fileName.endsWith(".img");
            }
            
            @Override
            public String getDescription() {
                
                return "Raw binary images (*.bin, *.ima, *.img)";
            }
        };
        
        JFileChooser fc = new JFileChooser();
        fc.setMultiSelectionEnabled(false);
        fc.addChoosableFileFilter(filter);
        fc.setFileFilter(filter);
        fc.setCurrentDirectory(new File(".\\"));
        fc.setDialogTitle(title);
        
        if(JFileChooser.APPROVE_OPTION == (isOpenDialog ? fc.showOpenDialog(null) :
                                                          fc.showSaveDialog(null))) {
            
            return fc.getSelectedFile();
        }
        
        return null;
    }
    
    public static void showExceptionMessage(String title, Throwable exception) {
        
        StringWriter e = new StringWriter();
        exception.printStackTrace(new PrintWriter(e));
        
        JOptionPane.showMessageDialog(
                
            null,
            new JScrollPane(new JTextArea(e.toString())) {
            
                @Override
                public Dimension getPreferredSize() {

                    return new Dimension(600, 300);
                }
            },
            title,
            JOptionPane.ERROR_MESSAGE
        );
    }
    
    public static void showInformationMessage(String title, String message) {
        
        JOptionPane.showMessageDialog(
                
            null,
            message,
            title,
            JOptionPane.INFORMATION_MESSAGE
        );
    }
    
    public static Object showInputDialog(String title, String message, Object[] selections, int defaultSelectionIndex) {
        
        return JOptionPane.showInputDialog(

            null,
            message,
            title,
            JOptionPane.QUESTION_MESSAGE,
            null,
            selections,
            selections[defaultSelectionIndex]
        );
    }
    
    public static boolean showConfirmationMessage(String title, String message) {
        
        return JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                
            null,
            message,
            title,
            JOptionPane.YES_NO_OPTION,
            JOptionPane.QUESTION_MESSAGE
        );
    }
}
