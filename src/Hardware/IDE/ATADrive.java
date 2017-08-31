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
package Hardware.IDE;

import Hardware.InterruptController.PICs;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;



public final class ATADrive {
    
    /* ----------------------------------------------------- *
     * Mounted image                                         *
     * ----------------------------------------------------- */
    private File m_imageFile;
    private long m_imageSize;
    private RandomAccessFile m_image;
    
    /* ----------------------------------------------------- *
     * Drive indicator                                       *
     * ----------------------------------------------------- */
    private boolean m_driveIndicator;
    
    /* ----------------------------------------------------- *
     * Disk geometry (default and also translated)           *
     * ----------------------------------------------------- */   
    private static final int IDX_DEFAULT = 0;
    private static final int IDX_TRANSLATED = 1; 
    private final int[] m_sectors;
    private final int[] m_heads;
    private final int[] m_cylinders;
    private int m_geometryIndex;
    private int m_geometryLBAs;
    private boolean m_isLBAEnabled;
    
    /* ----------------------------------------------------- *
     * Interrupt handling                                    *
     * ----------------------------------------------------- */
    public boolean m_irq;
    public boolean m_irqEnable;
    public final int m_irqNumber;
    private PICs m_pics;
    
    /* ----------------------------------------------------- *
     * Register set and pio buffer for this drive            *
     * ----------------------------------------------------- */   
    private final ATARegister m_regs;
    private final PIOBuffer m_pioBuffer;
    
    
    
    public ATADrive(int irqNumber) {

        m_irqNumber = irqNumber;
        
        m_sectors = new int[2];
        m_heads = new int[2];
        m_cylinders = new int[2];
        
        m_regs = new ATARegister();
        m_pioBuffer = new PIOBuffer(0x10000);
    }
    
    
    
    public boolean mountImage(String image) {

        m_regs.isDisconnected = true;
        
        File imageFile = new File(image);
        if(imageFile.exists()) {
        
            try {

                m_image = new RandomAccessFile(imageFile, "rw");
                m_imageFile = imageFile;
                m_imageSize = m_image.length();
                
                // Define default disk geometry
                m_geometryLBAs = (int)(m_imageSize / 512l);
                
                m_geometryIndex = IDX_DEFAULT;
                m_sectors[IDX_DEFAULT] = 63;
                m_heads[IDX_DEFAULT] = 16;
                m_cylinders[IDX_DEFAULT] = (int)(m_imageSize / (m_sectors[0] * m_heads[0] * 512l));
                
                // Success
                m_regs.isDisconnected = false;
            }
            catch(IOException ex) {
            }
        }
        
        return !m_regs.isDisconnected;
    }
    
    public boolean ejectImage() {
        
        try {
            
            if(m_image != null) {

                m_image.close();
                m_image = null;
            }
        }
        catch(IOException ex) {
        }
        
        return m_regs.isDisconnected = true;
    }
    
    
    
    public ATARegister getRegister() {
        
        return m_regs;
    }
    
    public PIOBuffer getPIOBuffer() {
        
        return m_pioBuffer;
    }
    
    public String getFileName() {
        
        if(!isDisconnected())
            return m_imageFile.getName().toUpperCase();
        
        return "";
    }
    
    public boolean isDisconnected() {
        
        return m_regs.isDisconnected;
    }
    
    
    
    public int getTotalNumberOfLBAs() {
        
        return m_geometryLBAs;
    }
    
    public int getTotalNumberOfSectorsDefault() {

        return getCylindersDefault() * getHeadsDefault() * getSectorsDefault() * 512;
    }
    
    public int getCylindersDefault() {

        return m_cylinders[IDX_DEFAULT];
    }
    
    public int getHeadsDefault() {
        
        return m_heads[IDX_DEFAULT];
    }
    
    public int getSectorsDefault() {

        return m_sectors[IDX_DEFAULT];
    }
    
    
    
    public void setTranslation(int sectors, int heads) {
    
        m_sectors[IDX_TRANSLATED] = sectors;
        m_heads[IDX_TRANSLATED] = heads;
        m_cylinders[IDX_TRANSLATED] = (int)(m_imageSize / (m_sectors[IDX_DEFAULT] * m_heads[IDX_DEFAULT] * 512l));
        
        m_geometryIndex = IDX_TRANSLATED;
    }
    
    public int getTotalNumberOfSectors() {

        return getCylinders() * getHeads() * getSectors() * 512;
    }
    
    public int getCylinders() {
        
        return m_cylinders[m_geometryIndex];
    }
    
    public int getHeads() {

        return m_heads[m_geometryIndex];
    }
    
    public int getSectors() {
        
        return m_sectors[m_geometryIndex];
    }
    
    
    
    public void setLBAEnable(boolean isEnabled) {
        
        m_isLBAEnabled = isEnabled;
    }
    
    
    
    private long getCHSAddress() {
        
        return (m_regs.getCylinder() * getHeads() + m_regs.getHead()) * getSectors() + m_regs.getSector() - 1;
    }
    
    private void setCHSAddress(long sector) {
        
        long c = sector / (getHeads() * getSectors());
        long t = sector % (getHeads() * getSectors());
        long h = t / getSectors();
        long s = t % getSectors() + 1;
        
        m_regs.setCylinder((int)c);
        m_regs.setHead((int)h);
        m_regs.setSector((int)s);
    }
    
    private long getLBA28Address() {
        
        return (m_regs.getHead() << 24) | (m_regs.getCylinder() << 8) | m_regs.getSector();
    }
    
    private void setLBA28Address(long sector) {
        
        int c = (int)((sector >>> 8) & 0xffffl);
        int h = (int)((sector >>> 24) & 0x0fl);
        int s = (int)(sector & 0xffl);
        
        m_regs.setCylinder(c);
        m_regs.setHead(h);
        m_regs.setSector(s);
    }
    
    
    
    private long getAddress() {
        
        if(m_isLBAEnabled)
            return getLBA28Address();
        else
            return getCHSAddress();
    }
    
    private void setAddress(long sector) {
        
        if(m_isLBAEnabled)
            setLBA28Address(sector);
        else
            setCHSAddress(sector);
    }
    
    public void advanceSectors(int numSectors) {
        
        setAddress(getAddress() + numSectors);
    }
    
    
    
    public void read(int numSectors) throws IOException {
        
        long lba = getAddress();
        
        m_image.seek(lba * 512l);
        m_image.read(m_pioBuffer.getArray(), 0, numSectors * 512);
        
        setAddress(lba + numSectors);
    }
    
    public void write(int numSectors) throws IOException {
        
        long lba = getAddress();
        
        m_image.seek(lba * 512);
        m_image.write(m_pioBuffer.getArray(), 0, numSectors * 512);
        
        setAddress(lba + numSectors);
    }
    
    

    public void setPICs(PICs pics) {
        
        m_pics = pics;
    }

    public void setInterruptEnable(boolean isEnabled) {
        
        m_irqEnable = isEnabled;
    }
    
    public void requestIRQ() {
        
        m_irq = true;
        updateIRQ();
    }
    
    public void clearIRQ() {
        
        m_irq = false;
        updateIRQ();
    }
    
    public void updateIRQ() {
        
        if(m_irq && m_irqEnable)
            m_pics.setInterrupt(m_irqNumber);
        else if(!m_irq)
            m_pics.clearInterrupt(m_irqNumber);
    }
    
    
    
    public void setDriveIndicator() {
        
        m_driveIndicator = true;
    }
    
    public boolean isDriveIndicatorLit() {
        
        boolean di = m_driveIndicator;
        m_driveIndicator = false;
        
        return di;
    }
}
