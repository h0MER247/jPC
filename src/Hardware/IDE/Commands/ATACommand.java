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



public abstract class ATACommand {
    
    protected ATADrive m_drive;
    protected ATADrive m_otherDrive;

    
    
    public boolean init(ATADrive drive, ATADrive otherDrive) {
        
        m_drive = drive;
        m_otherDrive = otherDrive;
        
        return onFirstExecute();
    }
    
    
    
    protected boolean abort() {
        
        m_drive.getRegister().command = 0;
        m_drive.getRegister().status = ATA_SR_DRDY | ATA_SR_DSC | ATA_SR_ERR;
        m_drive.getRegister().error = ATA_ER_ABRT;
        m_drive.requestIRQ();
        
        return false;
    }
    
    protected boolean cancel() {
        
        return false;
    }
    
    protected boolean proceed() {
        
        return true;
    }
    
    
    
    protected void initPIOTransfer() {
        
        m_drive.getPIOBuffer().setPosition(0);
        m_drive.getRegister().status = ATA_SR_DRDY | ATA_SR_DSC | ATA_SR_DRQ;
        m_drive.requestIRQ();
    }
    
    protected void finishPIOTransfer() {
        
        m_drive.getRegister().status = ATA_SR_DRDY | ATA_SR_DSC;
        m_drive.requestIRQ();
    }
    
    
    
    public abstract boolean onFirstExecute();
    public abstract void onExecute();
    public abstract void onPIOBufferEvent();
}
