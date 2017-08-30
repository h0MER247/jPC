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
package Hardware.ROM.Peripherals.XTIDE;

import Hardware.ROM.ROM;



public final class XTIDEBios extends ROM {
    
    public XTIDEBios() {
        
        super(
            
            "ide_xt.bin",                       // Image
            "834636143B14B2D495DE7D33D3798C54", // MD5 Checksum
            0xc8000,                            // Start address
            0x2000,                             // Length of the rom in bytes
            true                                // XT-IDE bios is optional
        );
    }
}
