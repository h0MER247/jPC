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

import Hardware.CPU.Intel80386.Register.Segments.SegmentTypes.SegmentType;



public final class Descriptor {
    
    /* ----------------------------------------------------- *
     * Raw Descriptor data                                   *
     * ----------------------------------------------------- */
    private int m_lowWord;
    private int m_highWord;
    
    /* ----------------------------------------------------- *
     * Segment base and limit                                *
     * ----------------------------------------------------- */
    private int m_base;
    private int m_limit;
    private int m_limitMin;
    private int m_limitMax;
    
    /* ----------------------------------------------------- *
     * Access rights (AR)                                    *
     * ----------------------------------------------------- */
    private boolean m_isPresent;
    private int m_dpl;
    private int m_type;
    
    /* ----------------------------------------------------- *
     * Size flag (D/B)                                       *
     * ----------------------------------------------------- */
    private boolean m_isSize32;
    
    /* ----------------------------------------------------- *
     * Type information                                      *
     * ----------------------------------------------------- */
    private SegmentType m_typeInfo;
    
    /* ----------------------------------------------------- *
     * Gate information (only valid if the type is a gate)   *
     * ----------------------------------------------------- */
    private int m_targetSegment;
    private int m_targetOffset;
    private int m_parameterCount;
    
    
    
    public void setDescriptor(int lowWord, int highWord) {
        
        m_lowWord = lowWord;
        m_highWord = highWord;
        
        // Base
        m_base = (highWord & 0xff000000) | ((highWord & 0x000000ff) << 16) | ((lowWord >>> 16) & 0x0000ffff);

        // Limit (page or byte granularity)
        if((highWord & 0x00800000) != 0)
            m_limit = (((highWord & 0x000f0000) | (lowWord & 0x0000ffff)) << 12) | 0x00000fff;
        else
            m_limit = (highWord & 0x000f0000) | (lowWord & 0x0000ffff);
        
        // Size flag
        m_isSize32 = (highWord & 0x00400000) != 0;
        
        // Access rights
        int ar = highWord >>> 8;
        
        m_isPresent = (ar & 0x80) != 0;
        m_dpl = (ar >>> 5) & 0x03;
        m_type = ar & 0x1f;
        m_typeInfo = SegmentTypes.SEGMENT_TYPES[ar & 0x1f];
        
        if(m_typeInfo.isExpandDownDataSegment()) {
            
            m_limitMin = m_limit + 1;
            m_limitMax = m_isSize32 ? 0xffffffff : 0xffff;
        }
        else {
            
            m_limitMin = 0;
            m_limitMax = m_limit;
        }
        
        // Gates only: Target segment and offset, parameter count
        if(m_typeInfo.isGate()) {
            
            m_targetSegment = (lowWord >>> 16) & 0xffff;
            m_targetOffset = lowWord & 0xffff;
            
            if(m_typeInfo.is32BitGate())
                m_targetOffset |= (highWord & 0xffff0000);
                    
            m_parameterCount = highWord & 0x1f;
        }
    }
    
    
    
    public boolean isOutsideLimit(int offset, int size) {
        
        return Integer.compareUnsigned(m_limitMin, offset) > 0 ||
               Integer.compareUnsigned(m_limitMax, offset + (size - 1)) < 0;
    }
    
    
    
    public int getLowWord() {
        
        return m_lowWord;
    }
    
    public int getHighWord() {
        
        return m_highWord;
    }
    
    public int getBase() {
        
        return m_base;
    }
    
    public int getLimit() {
        
        return m_limit;
    }
    
    public int getType() {
        
        return m_type;
    }
    
    public SegmentType getTypeInfo() {
        
        return m_typeInfo;
    }
    
    public int getDPL() {
        
        return m_dpl;
    }
    
    public boolean isPresent() {
        
        return m_isPresent;
    }
    
    public boolean isSize32() {
        
        return m_isSize32;
    }
    
    public int getTargetSegment() {
        
        return m_targetSegment;
    }
    
    public int getTargetOffset() {
        
        return m_targetOffset;
    }
    
    public int getParameterCount() {
        
        return m_parameterCount;
    }
    
 
    
    @Override
    public String toString() {
        
        String ret = String.format("Type: %s", getTypeInfo().toString());
        
        if(getTypeInfo().isGate()) {
            
            ret += String.format(", Target: %04x:%08x, DPL: %d", getTargetSegment(), getTargetOffset(), getDPL());
            
            if(getTypeInfo().isCallGate())
                ret += String.format(", Parameters: %d", getParameterCount());
        }
        else {
            
            ret += String.format(", Base: %08x, Limit: %08x, DPL: %d", getBase(), getLimit(), getDPL());
        }
        ret += String.format(", Raw data: %08x %08x", m_highWord, m_lowWord);
        
        return ret;
    }
}
