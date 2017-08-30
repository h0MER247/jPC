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
package IOMap;



public interface IOWritable extends IOMapped {
    
    int[] getWritableIOPorts();
    
    void writeIO8(int port, int data);
    
    // May not be the best solution for every device
    default void writeIO16(int port, int data) {
        
        writeIO8(port, data & 0xff);
        writeIO8(port + 1, (data >>> 8) & 0xff);
    }
    default void writeIO32(int port, int data) {
        
        writeIO8(port, data & 0xff);
        writeIO8(port + 1, (data >>> 8) & 0xff);
        writeIO8(port + 2, (data >>> 16) & 0xff);
        writeIO8(port + 3, (data >>> 24) & 0xff);
        
    }
}
