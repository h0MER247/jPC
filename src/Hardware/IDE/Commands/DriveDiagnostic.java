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

import Hardware.IDE.ATARegister;
import Hardware.IDE.IDE;



public final class DriveDiagnostic extends ATACommand {

    public DriveDiagnostic(IDE ide) {
        
        super(ide);
    }
    
    @Override
    public boolean onFirstExecute() {
        
        return proceed();
    }
    
    @Override
    public void onExecute() {
        
        // Reset drive / head register TODO: Find a better way to do this
        m_currDrive.getRegister().driveAndHead = 0x00;
        m_otherDrive.getRegister().driveAndHead = 0x00;
        m_ide.updateDrive(0x00);
        
        m_currDrive.getRegister().error = ATARegister.ATA_ER_AMNF;
        m_currDrive.requestIRQ();
    }
    
    @Override
    public void onPIOBufferEvent() {
    }
}
