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
package Hardware.CPU.Intel80386.Pointer.Size32;

import Hardware.CPU.Intel80386.Pointer.Pointer;
import Hardware.CPU.Intel80386.Register.General.Register;



public final class Pointer32BaseIndexDisplacement implements Pointer {

    private final Register m_base;
    private final Register m_index;
    private final int m_displacement;

    public Pointer32BaseIndexDisplacement(Register base,
                                          Register index,
                                          int displacement) {

        m_base = base;
        m_index = index;
        m_displacement = displacement;
    }

    @Override
    public int getAddress() {

        return m_base.getValue() + m_index.getValue() + m_displacement;
    }

    @Override
    public String toString() {

        return String.format("%s + %s + 0x%08x", m_base.toString(), m_index.toString(), m_displacement);
    }
}