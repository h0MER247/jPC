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
package Hardware.CPU.Intel80386.Instructions.i486;

import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Operands.Operand;



public final class BSWAP extends Instruction {

    private final Operand m_destination;
    
    public BSWAP(Intel80386 cpu,
                 Operand destination) {
        
        super(cpu);
        
        m_destination = destination;
    }

    @Override
    public void run() {
        
        int dst = m_destination.getValue();
        
        // Swap bits 0..7 with bits 24..31 and 8..15 with 16..23
        int res;
        res = (dst << 24) & 0xff000000;
        res |= (dst << 8) & 0x00ff0000;
        res |= (dst >>> 8) & 0x0000ff00;
        res |= (dst >>> 24) & 0x000000ff;
        m_destination.setValue(res);
    }
    
    @Override
    public String toString() {
        
        return String.format("bswap %s", m_destination.toString());
    }
}
