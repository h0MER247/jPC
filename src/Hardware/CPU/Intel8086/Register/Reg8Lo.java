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
package Hardware.CPU.Intel8086.Register;



public final class Reg8Lo extends Reg8 {

    public Reg8Lo(String name, Reg16 reg) {
        
        super(name, reg);
    }

    @Override
    public int getValue() {
        
        return m_reg.getValue() & 0xff;
    }
    
    @Override
    public void setValue(int value) {
        
        m_reg.setValue((m_reg.getValue() & 0xff00) | (value & 0xff));
    }
}
