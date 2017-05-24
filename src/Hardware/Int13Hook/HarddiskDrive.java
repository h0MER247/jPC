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



public class HarddiskDrive extends Drive {
    
    public HarddiskDrive() {
        
        super(DriveType.TYPE_HARDDISK);
    }
    
    @Override
    public boolean guessMediaGeometry() {
        
        m_sectors = 63;
        m_heads = 255;
        m_cylinders = m_size / ((m_sectors * m_heads) * 512);
        
        return m_cylinders <= 1023;
    }
}
