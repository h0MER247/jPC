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
package Utility;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;



public final class EmptyFileCreator {
    
    private final long m_sizeInByte;
    
    
    
    public EmptyFileCreator(long sizeInByte) {

        m_sizeInByte = sizeInByte;
    }
    
    
    
    public void write(File imageFile) throws IOException {
            
        try(RandomAccessFile out = new RandomAccessFile(imageFile, "rw")) {

            out.setLength(m_sizeInByte);
        }
    }
    
    
    
    @Override
    public String toString() {
        
        if(m_sizeInByte < 1024l)
            return String.format("%d Byte", m_sizeInByte);
        else if(m_sizeInByte < 2l * 1024l * 1024l)
            return String.format("%.2f KByte", m_sizeInByte / 1024f);
        else
            return String.format("%.2f MByte", m_sizeInByte / (1024f * 1024f));
    }
}
