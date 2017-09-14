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

import java.util.Arrays;



public final class BochsCMOSMap extends CMOSMap {
    
    @Override
    public void initDefaults() {
        
        Arrays.fill(m_nvRam, 0x00);
         
        // Current century (bcd encoded)
        m_nvRam[0x32] = 0x20;
        
        // Configuration bytes (mouse enabled, no floppy drive)
        m_nvRam[0x10] = 0x00;
        m_nvRam[0x11] = 0x80;
        m_nvRam[0x13] = 0x80;
        m_nvRam[0x14] = 0x0d;
        
        // There is 640kb of base memory
        m_nvRam[0x15] = 0x80;
        m_nvRam[0x16] = 0x02;
        
        // Boot from HDD as default
        m_nvRam[0x2d] = 0x00;
        
        // Eltorito boot sequence (floppy, cdrom, hdd)
        m_nvRam[0x38] = 0x20;
        m_nvRam[0x3d] = 0x31;
        
        // ATA translation policy (set to LBA)
        m_nvRam[0x39] = 0x55;
        m_nvRam[0x3a] = 0x55;
        
        // ATA bios detect flags (set to auto)
        m_nvRam[0x3b] = 0x00;
        m_nvRam[0x3c] = 0x00;
        
        // Enable boot menu
        m_nvRam[0x3f] = 0x00;
    }
    
    @Override
    public void setMemorySizeInMB(int size) {
        
        if(size < 1)
            throw new IllegalArgumentException("Invalid ram size specified");
        
        // Extended memory size (in 1K blocks)
        int val = Math.min((size - 1) * 1024, 0xffff);
        m_nvRam[0x17] = val & 0xff;
        m_nvRam[0x18] = (val >>> 8) & 0xff;
        m_nvRam[0x30] = val & 0xff;
        m_nvRam[0x31] = (val >>> 8) & 0xff;
        
        // Extended memory size above 16M (in 64K blocks)
        val = Math.min(Math.max(((size * 1024) / 64) - ((16 * 1024) / 64), 0x0000), 0xffff);
        m_nvRam[0x34] = val & 0xff;
        m_nvRam[0x35] = (val >>> 8) & 0xff;
        
        // Extra memory above 4GB
        m_nvRam[0x5b] = 0x00;
        m_nvRam[0x5c] = 0x00;
        m_nvRam[0x5d] = 0x00;
    }
    
    @Override
    public void setDriveGeometry(int driveIdx, int extendedType, int numCylinders, int numHeads, int numSectors) {
        
        if(driveIdx != 0 && driveIdx != 1)
            throw new IllegalArgumentException("Invalid drive index specified");
        
        // HDD type (0 = none, f = extended type)
        int mask = 0xf0 >> (driveIdx * 4);
        if(extendedType == 0)
            m_nvRam[0x12] &= ~mask;
        else
            m_nvRam[0x12] |= mask;
        
        // HDD extended type
        m_nvRam[0x19 + driveIdx] = extendedType;
        
        // HDD geometry
        int base = 0x1b + (driveIdx * 9);
        
        if(extendedType == 0) {
            
            for(int i = 0; i < 9; i++)
                m_nvRam[base + i] = 0x00;
        }
        else {
            
            m_nvRam[base] = numCylinders & 0xff;
            m_nvRam[base + 1] = (numCylinders >>> 8) & 0xff;
            m_nvRam[base + 2] = numHeads & 0x1f;
            m_nvRam[base + 3] = 0xff;
            m_nvRam[base + 4] = 0xff;
            m_nvRam[base + 5] = numHeads > 8 ? 0x08 : 0x00;
            m_nvRam[base + 6] = 0x00;
            m_nvRam[base + 7] = 0x00;
            m_nvRam[base + 8] = numSectors & 0xff;
        }
    }
}
