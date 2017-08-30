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



public final class DescriptorTable {
    
    /* ----------------------------------------------------- *
     * Table name                                            *
     * ----------------------------------------------------- */
    private final String m_name;
    
    /* ----------------------------------------------------- *
     * Table base address and limit                          *
     * ----------------------------------------------------- */
    private int m_base;
    private int m_limit;
    
    /* ----------------------------------------------------- *
     * Table invalid flag and selector (only for LDT)        *
     * ----------------------------------------------------- */
    private boolean m_isInvalid;
    private int m_selector;
    
    
    
    public DescriptorTable(String name) {
        
        m_name = name;
    }
    
    
    
    public void reset() {
        
        setSelector(0);
        setBase(0);
        setLimit(0xffff);
        setValid(false);
    }
    
    
    
    public boolean isOutsideLimit(int offset, int size) {
        
        return Integer.compareUnsigned(m_limit, offset + size - 1) < 0;
    }
    
    
    
    public int getBase() {
        
        return m_base;
    }
    
    public void setBase(int base) {
        
        m_base = base;
    }
    
    public int getLimit() {
        
        return m_limit;
    }
    
    public void setLimit(int limit) {
        
        m_limit = limit;
    }
    
    public int getSelector() {
        
        return m_selector;
    }
    
    public void setSelector(int selector) {
        
        m_selector = selector;
    }
    
    public boolean isInvalid() {
        
        return m_isInvalid;
    }
    
    public void setValid(boolean isValid) {
        
        m_isInvalid = !isValid;
    }
    
    
    
    @Override
    public String toString() {
        
        return m_name;
    }
}
