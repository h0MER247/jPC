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



public class FloppyDrive extends Drive {
    
    public FloppyDrive() {
        
        super(DriveType.TYPE_FLOPPY);
    }
    
    @Override
    public boolean guessMediaGeometry() {
        
        switch(getSize()) {
            
            case 160 * 1024: 
                m_cylinders = 40;
                m_heads = 1;
                m_sectors = 8;
                break;
                
            case 180 * 1024: 
                m_cylinders = 40;
                m_heads = 1;
                m_sectors = 9;
                break;
                
            case 320 * 1024: 
                m_cylinders = 40;
                m_heads = 2;
                m_sectors = 8;
                break;
                
            case 360 * 1024:
                m_cylinders = 40;
                m_heads = 2;
                m_sectors = 9;
                break;
                
            case 720 * 1024:
                m_cylinders = 80;
                m_heads = 2;
                m_sectors = 9;
                break;
                
            case 1200 * 1024:
                m_cylinders = 80;
                m_heads = 2;
                m_sectors = 15;
                break;
                
            case 1440 * 1024:
                m_cylinders = 80;
                m_heads = 2;
                m_sectors = 18;
                break;
                
            case 2880 * 1024:
                m_cylinders = 80;
                m_heads = 2;
                m_sectors = 36;
                break;
                
            default:
                return false;
        }
        
        return true;
    }
}
