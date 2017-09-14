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
package Hardware.IDE.Commands;

import Hardware.IDE.IDE;
import java.nio.charset.Charset;
import Hardware.IDE.PIOBuffer;



public final class Identify extends ATACommand {
    
    private final Charset ASCII = Charset.forName("US-ASCII");

    public Identify(IDE ide) {
        
        super(ide);
    }
    
    @Override
    public boolean onFirstExecute() {
        
        return proceed();
    }
    
    @Override
    public void onExecute() {
        
        PIOBuffer pio = m_currDrive.getPIOBuffer();
        pio.reset();
        
        // Create identification packet
        pio.setInt16(2, m_currDrive.getCylindersDefault());
        pio.setInt16(6, m_currDrive.getHeadsDefault());
        pio.setInt16(12, m_currDrive.getSectorsDefault());
        pio.setRPadStr16(20, 20, "S/N:007", ASCII);
        pio.setInt16(40, 0x0003); // Buffer type
        pio.setInt16(42, pio.getSize() / 512); // Buffer size in 512 byte increments
        pio.setLPadStr16(46, 8, "v0.1", ASCII);
        pio.setLPadStr16(54, 40, String.format("jPC HDD - [%s]", m_currDrive.getFileName()), ASCII);
        pio.setInt16(96, 1); // DWord PIO transfer supported
        pio.setInt16(98, 1 << 9); // LBA supported
        pio.setInt16(108, m_currDrive.getCylinders());
        pio.setInt16(110, m_currDrive.getHeads());
        pio.setInt16(112, m_currDrive.getSectors());
        pio.setInt16(114, m_currDrive.getTotalNumberOfSectors() & 0xffff);
        pio.setInt16(116, m_currDrive.getTotalNumberOfSectors() >>> 16);
        pio.setInt16(120, m_currDrive.getTotalNumberOfLBAs()& 0xffff);
        pio.setInt16(122, m_currDrive.getTotalNumberOfLBAs() >>> 16);
        
        initPIOTransfer();
    }
    
    @Override
    public void onPIOBufferEvent() {
        
        finishPIOTransfer();
    }
}
