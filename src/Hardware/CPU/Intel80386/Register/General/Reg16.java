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
package Hardware.CPU.Intel80386.Register.General;



public final class Reg16 implements Register {
    
    private final Reg32 m_reg32;
    private final String m_name;
    
    public Reg16(String name, Reg32 reg32) {
        
        m_name = name;
        m_reg32 = reg32;
    }
    
    @Override
    public int getValue() {
        
        return m_reg32.getValue() & 0xffff;
    }
    
    @Override
    public void setValue(int value) {
        
        m_reg32.setValue((m_reg32.getValue() & 0xffff0000) | (value & 0xffff));
    }
    
    @Override
    public String toString() {
        
        return m_name;
    }
}
