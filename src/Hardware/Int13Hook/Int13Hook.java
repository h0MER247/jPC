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

import Hardware.Int13Hook.Drive.DriveType;
import Hardware.CPU.Intel8086.Intel8086;
import Hardware.CPU.Intel8086.InterruptHook;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;



/**
 * This interrupt hook is used until an fdc/ide emulation is implemented
 */
public class Int13Hook implements InterruptHook {
    
    /* ----------------------------------------------------- *
     * Drives                                                *
     * ----------------------------------------------------- */
    private final HashMap<Integer, Drive> m_drives;
    private boolean m_isIndicatorEnabled;
    private boolean m_isIndicatorLit;
    
    /* ----------------------------------------------------- *
     * Buffer used while reading / writing disc sectors      *
     * ----------------------------------------------------- */
    private final byte[] m_buffer;
    
    
    
    public Int13Hook() {
        
        m_drives = new HashMap<>();
        m_drives.put(0x00, new FloppyDrive());
        m_drives.put(0x01, new FloppyDrive());
        m_drives.put(0x80, new HarddiskDrive());
        m_drives.put(0x81, new HarddiskDrive());
        m_drives.put(0x82, new HarddiskDrive());
        m_drives.put(0x83, new HarddiskDrive());
        
        m_buffer = new byte[512];
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation for InterruptHook">
    
    @Override
    public int[] getHandledInterrupts() {
        
        return new int[] { 0x13 };
    }
    
    @Override
    public boolean handleInterrupt(Intel8086 cpu, int func) {
        
        switch(cpu.AH.getValue()) {

            case 0x00: onResetDiskSystem(cpu); break;
            case 0x02: onReadDiskSectors(cpu); break;
            case 0x03: onWriteDiskSectors(cpu); break;
            case 0x04: onVerifySectors(cpu); break;
            case 0x08: onReadDriveParameters(cpu); break;
            case 0x15: onReadDiskType(cpu); break;
            
            default:
                m_isIndicatorLit = false;
                return false;
        }
        
        m_isIndicatorLit = true;
        return true;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Returning the number of floppy / hard drives">
    
    private int getNumberOfFloppyDrives() {
        
        return (int)m_drives.values()
                            .stream()
                            .filter(drive -> drive instanceof FloppyDrive)
                            .count();
    }
    
    private int getNumberOfHarddiskDrives() {
        
        return (int)m_drives.values()
                            .stream()
                            .filter(drive -> drive instanceof HarddiskDrive && drive.isInserted())
                            .count();
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Handling of the "drives" (loading, ejecting, shutdown)">
    
    public void load(boolean isHardDisk, int driveIdx, File imageFile) throws IOException {
        
        if(isHardDisk)
            driveIdx |= 0x80;
        
        if(!m_drives.containsKey(driveIdx))
            throw new IOException("The specified drive index is invalid");
        
        m_drives.get(driveIdx).open(imageFile);
    }
    
    public void eject(boolean isHardDisk, int driveIdx) throws IOException {
        
        if(isHardDisk)
            driveIdx |= 0x80;
        
        if(!m_drives.containsKey(driveIdx))
            throw new IOException("The specified drive index is invalid");

        m_drives.get(driveIdx).close();
    }
    
    public void shutdown() {
        
        m_drives.values().forEach(drive -> {
            
            try {
            
                drive.close();
            }
            catch(IOException ex) {
            }
        });
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Drive indicator stuff">
    
    public void setIndicatorEnable(boolean isEnabled) {
        
        m_isIndicatorEnabled = isEnabled;
    }
    
    public boolean isIndicatorLit() {
        
        boolean res = m_isIndicatorEnabled && m_isIndicatorLit;
        m_isIndicatorLit = false;
        return res;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="High level emulation of interrupt 13h">
    
    // <editor-fold defaultstate="collapsed" desc="Reset disk system (ah = 00h)">
    
    private void onResetDiskSystem(Intel8086 cpu) {
        
        Drive drive = m_drives.get(cpu.DL.getValue());
        
        if(drive != null && drive.isInserted())
            handleSuccess(cpu, 0x00);
        else
            handleFailure(cpu);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Read disk sectors (ah = 02h)">
    
    private void onReadDiskSectors(Intel8086 cpu) {
        
        Drive drive = m_drives.get(cpu.DL.getValue());
        
        if(drive != null && drive.isInserted()) {

            int count = cpu.AL.getValue();
            int cylinder = cpu.CH.getValue();
            int sector = cpu.CL.getValue() & 0x3f;
            int head = cpu.DH.getValue();
            int destBase = cpu.ES.getBase();
            int destOffset = cpu.BX.getValue();
            
            if(drive.getType() == DriveType.TYPE_HARDDISK)
                cylinder |= (cpu.CL.getValue() & 0xc0) << 2;
            
            int read = 0;
            try {
                
                drive.seek(cylinder, head, sector);
                for(; read < count; read++) {

                    drive.read(m_buffer);
                    
                    for(int j = 0; j < 512; j++, destOffset++)
                        cpu.writeMEM8(destBase, destOffset, m_buffer[j] & 0xff);
                }
                
                handleSuccess(cpu, 0x00);
            }
            catch(IOException ex) {
                
                handleFailure(cpu);
            }
            finally {
                
                cpu.AL.setValue(read);
            }
        }
        else {
            
            handleFailure(cpu);
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Write disk sectors (ah = 03h)">
    
    private void onWriteDiskSectors(Intel8086 cpu) {
        
        Drive drive = m_drives.get(cpu.DL.getValue());
        
        if(drive != null && drive.isInserted()) {
            
            int count = cpu.AL.getValue();
            int cylinder = cpu.CH.getValue();
            int sector = cpu.CL.getValue() & 0x3f;
            int head = cpu.DH.getValue();
            int srcBase = cpu.ES.getBase();
            int srcOffset = cpu.BX.getValue();
            
            if(drive.getType() == DriveType.TYPE_HARDDISK)
                cylinder |= (cpu.CL.getValue() & 0xc0) << 2;
            
            int written = 0;
            try {
                
                drive.seek(cylinder, head, sector);
                for(; written < count; written++) {

                    for(int j = 0; j < 512; j++, srcOffset++)
                        m_buffer[j] = (byte)cpu.readMEM8(srcBase, srcOffset);
                    
                    drive.write(m_buffer);
                }
                
                handleSuccess(cpu, 0x00);
            }
            catch(IOException ex) {
                
                handleFailure(cpu);
            }
            finally {
                
                cpu.AL.setValue(written);
            }
        }
        else {
            
            handleFailure(cpu);
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Verify disk sectors (ah = 04h)">
    
    private void onVerifySectors(Intel8086 cpu) {
        
        handleSuccess(cpu, 0x00);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Read drive parameters (ah = 08h)">
    
    private void onReadDriveParameters(Intel8086 cpu) {
        
        Drive drive = m_drives.get(cpu.DL.getValue());
        
        if(drive != null) {
            
            switch(drive.getType()) {
                
                // Just identify the floppy drives as 3.5" 1.44 MB
                case TYPE_FLOPPY:
                    cpu.CH.setValue(80 - 1);
                    cpu.CL.setValue(18);
                    cpu.DH.setValue(2 - 1);
                    
                    cpu.BL.setValue(0x04);
                    cpu.DL.setValue(getNumberOfFloppyDrives());
                    
                    handleSuccess(cpu, 0x00);
                    break;
                    
                case TYPE_HARDDISK:
                    if(drive.isInserted()) {
                        
                        int c = drive.getCylinders() - 1;
                        int h = drive.getHeads() - 1;
                        int s = drive.getSectors();
                  
                        cpu.CH.setValue(c & 0xff);
                        cpu.CL.setValue((s & 0x3f) | ((c & 0x300) >> 2));
                        cpu.DH.setValue(h & 0xff);
                        
                        cpu.BL.setValue(0x00);
                        cpu.DL.setValue(getNumberOfHarddiskDrives());
                        
                        handleSuccess(cpu, 0x00);
                    }
                    else {
                        
                        handleFailure(cpu);
                    }
                    break;
            }
        }
        else {
            
            handleFailure(cpu);
        }   
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Read disk type (ah = 15h)">
    
    private void onReadDiskType(Intel8086 cpu) {
        
        Drive drive = m_drives.get(cpu.DL.getValue());
        if(drive != null) {
            
            switch(drive.getType()) {
                
                case TYPE_FLOPPY:
                    handleSuccess(cpu, 0x01);
                    return;
                
                case TYPE_HARDDISK:
                    if(drive.isInserted()) {
                        
                        int sectors = drive.getSize() / 512;
                        cpu.CX.setValue(sectors >>> 16);
                        cpu.DX.setValue(sectors & 0xffff);
                        
                        handleSuccess(cpu, 0x03);
                        return;
                    }
                    break;
            }
        }
        handleSuccess(cpu, 0x00);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Handling of error / success codes">
    
    private void handleSuccess(Intel8086 cpu, int status) {
        
        cpu.AH.setValue(status);
        cpu.FLAGS.CF = false;
    }
    
    private void handleFailure(Intel8086 cpu) {
        
        // The error code is always 0xaa (Drive not present). Returning "real"
        // error codes is not implemented at the moment as i'd rather focus on
        // a real fdc / ide implementation
        cpu.AH.setValue(0xaa);
        cpu.FLAGS.CF = true;
    }
    
    // </editor-fold>
    
    // </editor-fold>
}
