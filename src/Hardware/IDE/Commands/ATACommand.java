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

import Hardware.IDE.ATADrive;
import static Hardware.IDE.ATARegister.ATA_ER_ABRT;
import static Hardware.IDE.ATARegister.ATA_SR_DRDY;
import static Hardware.IDE.ATARegister.ATA_SR_DRQ;
import static Hardware.IDE.ATARegister.ATA_SR_DSC;
import static Hardware.IDE.ATARegister.ATA_SR_ERR;
import Hardware.IDE.IDE;



public abstract class ATACommand {
    
    protected final IDE m_ide;
    protected ATADrive m_currDrive;
    protected ATADrive m_otherDrive;
    
    
    
    public ATACommand(IDE ide) {
        
        m_ide = ide;
    }
    
    
    
    public boolean init() {
        
        m_currDrive = m_ide.getCurrentDrive();
        m_otherDrive = m_ide.getOtherDrive();
        
        if(m_currDrive.isDisconnected())
            return abort();
        else
            return onFirstExecute();
    }
    
    
    
    protected boolean abort() {
        
        m_currDrive.getRegister().command = 0;
        m_currDrive.getRegister().status = ATA_SR_DRDY | ATA_SR_DSC | ATA_SR_ERR;
        m_currDrive.getRegister().error = ATA_ER_ABRT;
        m_currDrive.requestIRQ();
        
        return false;
    }
    
    protected boolean cancel() {
        
        return false;
    }
    
    protected boolean proceed() {
        
        return true;
    }
    
    
    
    protected void initPIOTransfer() {
        
        m_currDrive.getPIOBuffer().setPosition(0);
        m_currDrive.getRegister().status = ATA_SR_DRDY | ATA_SR_DSC | ATA_SR_DRQ;
        m_currDrive.requestIRQ();
    }
    
    protected void finishPIOTransfer() {
        
        m_currDrive.getRegister().status = ATA_SR_DRDY | ATA_SR_DSC;
        m_currDrive.requestIRQ();
    }
    
    
    
    public abstract boolean onFirstExecute();
    public abstract void onExecute();
    public abstract void onPIOBufferEvent();
}
