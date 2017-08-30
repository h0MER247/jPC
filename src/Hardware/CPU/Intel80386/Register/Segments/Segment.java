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

import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Register.Segments.SegmentTypes.SegmentType;



public abstract class Segment {
    
    /* ----------------------------------------------------- *
     * Segment name                                          *
     * ----------------------------------------------------- */
    private final String m_name;
    
    /* ----------------------------------------------------- *
     * Segment selector                                      *
     * ----------------------------------------------------- */
    private int m_selector;
    
    /* ----------------------------------------------------- *
     * Segment descriptor cache ("hidden part")              *
     * ----------------------------------------------------- */
    private boolean m_isInvalid;
    private boolean m_isSize32;
    private boolean m_isReadable;
    private boolean m_isWritable;
    private int m_type;
    private int m_base;
    private int m_dpl;
    private int m_limit;
    private int m_limitMin;
    private int m_limitMax;
    
    /* ----------------------------------------------------- *
     * Cached type information                               *
     * ----------------------------------------------------- */
    private SegmentType m_typeInfo;
    
    /* ----------------------------------------------------- *
     * Reference to the intel 80386 cpu                      *
     * ----------------------------------------------------- */
    protected final Intel80386 m_cpu;
    
    
    
    public Segment(String name, Intel80386 cpu) {
        
        m_name = name;
        m_cpu = cpu;
    }
    
    
    
    public void reset() {
        
        setValid(true);
        
        // Initialize the segment as an "ExpandUp, Writable and Accessed Datasegment"
        setType(0x13);
        
        setSelector(0);
        setBase(0);
        setDPL(0);
        setSize32(false);
        setLimit(0xffff);
    }
    
    public void invalidate() {
        
        setSelector(0);
        setValid(false);
    }
    
    
    
    public boolean isOutsideLimit(int offset, int size) {
        
        return Integer.compareUnsigned(m_limitMin, offset) > 0 ||
               Integer.compareUnsigned(m_limitMax, offset + (size - 1)) < 0;
    }
    
    
    
    public int getBase() {
        
        return m_base;
    }
    
    public void setBase(int base) {
        
        m_base = base;
    }
    
    public int getSelector() {
        
        return m_selector;
    }
    
    public void setSelector(int selector) {
        
        m_selector = selector;
    }
    
    public void setDPL(int dpl) {
        
        m_dpl = dpl;
    }
    
    public int getDPL() {
        
        return m_dpl;
    }
    
    public void setRPL(int rpl) {
        
        m_selector = (m_selector & 0xfffc) | (rpl & 0x03);
    }
    
    public int getRPL() {
        
        return m_selector & 0x03;
    }
    
    public boolean isSize32() {
        
        return m_isSize32;
    }
    
    public void setSize32(boolean isSize32) {
        
        m_isSize32 = isSize32;
    }
    
    public void setValid(boolean isValid) {
        
        m_isInvalid = !isValid;
    }
    
    public boolean isInvalid() {
        
        return m_isInvalid;
    }
    
    public boolean isReadable() {
        
        return m_isReadable;
    }

    public boolean isWritable() {
        
        return m_isWritable;
    }
    
    public int getType() {
        
        return m_type;
    }
    
    public void setType(int type) {
        
        m_type = type;
        m_typeInfo = SegmentTypes.SEGMENT_TYPES[type];
        
        m_isReadable = m_typeInfo.isReadable();
        m_isWritable = m_typeInfo.isWritable();
    }
    
    public SegmentType getTypeInfo() {
        
        return m_typeInfo;
    }
    
    public int getLimit() {
        
        return m_limit;
    }
    
    public void setLimit(int limit) {
        
        m_limit = limit;
        
        if(m_typeInfo.isExpandDownDataSegment()) {
            
            m_limitMin = limit + 1;
            m_limitMax = isSize32() ? 0xffffffff : 0xffff;
        }
        else {
            
            m_limitMin = 0;
            m_limitMax = limit;
        }
    }
    
    
    
    public void loadRealMode(int selector) {
        
        setValid(true);
        
        setSelector(selector);
        setBase(selector << 4);
        setDPL(0);
    }
    
    public void loadVirtualMode(int selector) {
        
        setValid(true);
        
        setSelector(selector);
        setBase(selector << 4);
        setType(0x13);
        setSize32(false);
        setDPL(3);
        setLimit(0xffff);
    }
    
    public void loadProtectedMode(int selector, Descriptor descriptor) {
        
        setValid(true);
        
        setSelector(selector);
        setBase(descriptor.getBase());
        setType(descriptor.getType());
        setSize32(descriptor.isSize32());
        setDPL(descriptor.getDPL());
        setLimit(descriptor.getLimit());
    }
    
    
    
    public void validateForOuterLevel() {
        
        if(isInvalid())
            return;
        
        // Selector index must be within descriptor table limits
        DescriptorTable tbl = m_cpu.isReferencingGDT(getSelector()) ? m_cpu.GDT :
                                                                      m_cpu.LDT;
        if(tbl.isOutsideLimit(getSelector() & 0xfff8, 8)) {

            invalidate();
            return;
        }
        
        // AR byte must indicate data or readable code segment
        if(!(getTypeInfo().isDataSegment() ||
             getTypeInfo().isReadableCodeSegment())) {

            invalidate();
            return;
        }

        // If segment is data or non-conforming code segment the DPL
        // must be >= CPL or DPL must be >= RPL
        if(getTypeInfo().isDataSegment() ||
           getTypeInfo().isNonConformingCodeSegment()) {

            if(getDPL() < m_cpu.getCPL() || getDPL() < getRPL())
                invalidate();
        }
    }
    
    
    
    public abstract void checkProtectionRead(int offset, int size);
    public abstract void checkProtectionWrite(int offset, int size);
    
    
    
    @Override
    public String toString() {
        
        return m_name;
    }
}
