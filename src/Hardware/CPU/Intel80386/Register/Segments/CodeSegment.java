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
package Hardware.CPU.Intel80386.Register.Segments;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Intel80386;



public final class CodeSegment extends Segment {

    public CodeSegment(String name, Intel80386 cpu) {
        
        super(name, cpu);
    }
    
    @Override
    public void loadProtectedMode(int selector, Descriptor descriptor) {
        
        setValid(true);
        setSelector(selector);
        setBase(descriptor.getBase());
        setType(descriptor.getType());
        setSize32(descriptor.isSize32());
        setLimit(descriptor.getLimit());
        
        // Conforming code segments have no privilege level on their own.
        // They just conform to the current privilege level.
        if(descriptor.getTypeInfo().isConformingCodeSegment())
            setDPL(m_cpu.getCPL());
        else
            setDPL(descriptor.getDPL());
        
        // The RPL field of CS is ALWAYS equal to the CPL which in turn is
        // DEFINED as the DPL of the code segment.
        setRPL(getDPL());
    }
    
    @Override
    public void checkProtectionRead(int offset, int size) {
            
        if(isInvalid())
            throw CPUException.getGeneralProtectionFault(0);

        if(isOutsideLimit(offset, size))
            throw CPUException.getGeneralProtectionFault(0);
        
        if(!isReadable())
            throw CPUException.getGeneralProtectionFault(0);
        
        // TODO: Check Alignment (486+)
    }

    @Override
    public void checkProtectionWrite(int offset, int size) {
        
        // We can write to the code segment as long as we are in real- or
        // virtual 8086 mode
        if(m_cpu.CR.isInProtectedMode() && !m_cpu.FLAGS.VM) {
        
            throw CPUException.getGeneralProtectionFault(0);
        }
        else {
            
            if(isInvalid())
                throw CPUException.getGeneralProtectionFault(0);
            
            if(isOutsideLimit(offset, size))
                throw CPUException.getGeneralProtectionFault(0);
        
            // TODO: Check Alignment (486+)
        }
    }
}
