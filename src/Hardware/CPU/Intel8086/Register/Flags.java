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



public final class Flags {
    
    /* ----------------------------------------------------- *
     * Flag bitmasks                                         *
     * ----------------------------------------------------- */
    public static final int FLAG_CARRY = 0x0001;
    public static final int FLAG_PARITY = 0x0004;
    public static final int FLAG_AUXILIARY_CARRY = 0x0010;
    public static final int FLAG_ZERO = 0x0040;
    public static final int FLAG_SIGN = 0x0080;
    public static final int FLAG_TRAP = 0x0100;
    public static final int FLAG_INTERRUPT_ENABLE = 0x0200;
    public static final int FLAG_DIRECTION = 0x0400;
    public static final int FLAG_OVERFLOW = 0x0800;
    
    /* ----------------------------------------------------- *
     * Intel 8086 flags                                      *
     * ----------------------------------------------------- */
    public boolean CF;
    public boolean PF;
    public boolean AF;
    public boolean ZF;
    public boolean SF;
    public boolean TF;
    public boolean IF;
    public boolean DF;
    public boolean OF;
    
    /* ----------------------------------------------------- *
     * Parity Lookup Table                                   *
     * ----------------------------------------------------- */
    private final boolean m_parityLUT[];
    
    
    
    public Flags() {
        
        m_parityLUT = new boolean[0x100];
        for(int i = 0; i < 0x100; i++) {
            
            int bitCount = 0;
            for(int j = 0; j < 8; j++) {
                
                if((i & (1 << j)) != 0)
                    bitCount++;
            }
            
            m_parityLUT[i] = (bitCount & 0x01) == 0;
        }
    }
    
    
    
    public void reset() {
        
        CF = false;
        PF = false;
        AF = false;
        ZF = false;
        SF = false;
        TF = false;
        IF = false;
        DF = false;
        OF = false;
    }
    
    
    
    public int getValue() {
        
        int value = 0;
        
        if(CF) value |= FLAG_CARRY;
        if(PF) value |= FLAG_PARITY;
        if(AF) value |= FLAG_AUXILIARY_CARRY;
        if(ZF) value |= FLAG_ZERO;
        if(SF) value |= FLAG_SIGN;
        if(TF) value |= FLAG_TRAP;
        if(IF) value |= FLAG_INTERRUPT_ENABLE;
        if(DF) value |= FLAG_DIRECTION;
        if(OF) value |= FLAG_OVERFLOW;
        
        return value | 0xf02a;
    }
    
    public void setValue(int value) {
        
        CF = (value & FLAG_CARRY) != 0;
        PF = (value & FLAG_PARITY) != 0;
        AF = (value & FLAG_AUXILIARY_CARRY) != 0;
        ZF = (value & FLAG_ZERO) != 0;
        SF = (value & FLAG_SIGN) != 0;
        TF = (value & FLAG_TRAP) != 0;
        IF = (value & FLAG_INTERRUPT_ENABLE) != 0;
        DF = (value & FLAG_DIRECTION) != 0;
        OF = (value & FLAG_OVERFLOW) != 0;
    }
    
    
    
    public void setSZP_8(int data) {
        
        SF = (data & 0x80) != 0;
        ZF = (data & 0xff) == 0;
        PF = m_parityLUT[data & 0xff];
    }
    
    public void setSZP_16(int data) {
        
        SF = (data & 0x8000) != 0;
        ZF = (data & 0xffff) == 0;
        PF = m_parityLUT[data & 0xff];
    }
    
    
    
    @Override
    public String toString() {
        
        String ret = "";
        
        ret += OF ? "O" : "o";
        ret += DF ? "D" : "d";
        ret += IF ? "I" : "i";
        ret += TF ? "T" : "t";
        ret += SF ? "S" : "s";
        ret += ZF ? "Z" : "z";
        ret += AF ? "A" : "a";
        ret += PF ? "P" : "p";
        ret += CF ? "C" : "c";
        
        return ret;
    }
}
