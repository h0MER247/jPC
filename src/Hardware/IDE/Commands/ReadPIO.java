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
import java.io.IOException;



public final class ReadPIO extends ATACommand {

    public ReadPIO(IDE ide) {
        
        super(ide);
    }
    
    @Override
    public boolean onFirstExecute() {
        
        if(m_currDrive.getRegister().sectorCount == 0)
            m_currDrive.getRegister().sectorCount = 0xff;
        
        return proceed();
    }
    
    @Override
    public void onExecute() {
        
        m_currDrive.getRegister().sectorCount--;
        try {
            
            m_currDrive.getPIOBuffer().reset();
            m_currDrive.setDriveIndicator();
            m_currDrive.read(1);
            
            initPIOTransfer();
        }
        catch(IOException ex) {

            abort();
        }
    }
    
    @Override
    public void onPIOBufferEvent() {
        
        if(m_currDrive.getRegister().sectorCount > 0)
            onExecute();
        else
            finishPIOTransfer();
    }
}
