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
package Hardware.ROM.System.Bochs;

import Hardware.ROM.ROM;



public final class BochsBios extends ROM {
    
    public BochsBios() {
        
        super(
                
            "BIOS-bochs-legacy",                // Image
            "FD359AC2EE5E1023D414924E4EA59DB2", // MD5 Checksum
            0xf0000,                            // Start address
            0x10000,                            // Length of the rom in bytes
            false                               // The system bios rom isn't optional
        );
    }
}
