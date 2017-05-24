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
package Hardware.Int13Hook;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;



public abstract class Drive {
    
    /* ----------------------------------------------------- *
     * Type of the drive                                     *
     * ----------------------------------------------------- */
    public enum DriveType {
        
        TYPE_FLOPPY,
        TYPE_HARDDISK
    }
    private final DriveType m_type;    
    
    /* ----------------------------------------------------- *
     * Image data                                            *
     * ----------------------------------------------------- */
    protected RandomAccessFile m_image;
    protected int m_size;
    
    /* ----------------------------------------------------- *
     * The geometry of the inserted media                    *
     * ----------------------------------------------------- */
    protected int m_cylinders;
    protected int m_heads;
    protected int m_sectors;
    
    
    
    public Drive(DriveType type) {
        
        m_type = type;
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Handling of disk images">
    
    public void open(File imageFile) throws IOException {
        
        close();
        
        m_image = new RandomAccessFile(imageFile, "rw");
        m_size = (int)m_image.length();
        
        if(!guessMediaGeometry()) {
            
            close();
            throw new IOException(String.format("Unable to guess the geometry for %s", imageFile));
        }
    }
    
    public void close() throws IOException {
        
        if(m_image != null) {
        
            m_image.close();
            m_image = null;
        }
    }
    
    public void seek(int cylinder, int head, int sector) throws IOException {
        
        m_image.seek(512L * (((cylinder * m_heads + head) * m_sectors) + sector - 1));
    }
    
    public void read(byte[] buffer) throws IOException {
        
        m_image.read(buffer, 0, 512);
    }
    
    public void write(byte[] buffer) throws IOException {
        
        m_image.write(buffer, 0, 512);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Information about the drive and its media">
    
    public boolean isInserted() {
        
        return m_image != null;
    }
    
    public DriveType getType() {
        
        return m_type;
    }
    
    public int getSize() {
        
        return m_size;
    }
    
    public int getCylinders() {
        
        return m_cylinders;
    }
    
    public int getHeads() {
        
        return m_heads;
    }
    
    public int getSectors() {
        
        return m_sectors;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Abstract methods">
    
    public abstract boolean guessMediaGeometry();
    
    // </editor-fold>
}
