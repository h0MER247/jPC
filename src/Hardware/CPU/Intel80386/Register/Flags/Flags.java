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
package Hardware.CPU.Intel80386.Register.Flags;

import Hardware.CPU.Intel80386.Intel80386;



public final class Flags {
    
    /* ----------------------------------------------------- *
     * Flagregister bitmasks                                 *
     * ----------------------------------------------------- */
    public static final int MASK_CARRY = 0x00000001;
    public static final int MASK_PARITY = 0x00000004;
    public static final int MASK_AUXILIARY_CARRY = 0x00000010;
    public static final int MASK_ZERO = 0x00000040;
    public static final int MASK_SIGN = 0x00000080;
    public static final int MASK_TRAP = 0x00000100;
    public static final int MASK_INTERRUPT_ENABLE = 0x00000200;
    public static final int MASK_DIRECTION = 0x00000400;
    public static final int MASK_OVERFLOW = 0x00000800;
    public static final int MASK_IOPL = 0x00003000;
    public static final int MASK_NESTED_TASK = 0x00004000;
    public static final int MASK_RESUME = 0x00010000;
    public static final int MASK_VM_8086 = 0x00020000;
    public static final int MASK_ALIGN_CHECK = 0x00040000;
    public static final int MASK_FLAGS = 0x00007fd5;
    public static final int MASK_EFLAGS = 0x00077fd5;
    public static final int MASK_UNPRIVILEGED_FLAGS = 0x00004dd5;
    public static final int MASK_UNPRIVILEGED_EFLAGS = 0x00044dd5;
    private final int m_capabilityMask;
    
    /* ----------------------------------------------------- *
     * Flagregister content                                  *
     * ----------------------------------------------------- */
    public boolean CF; // Carry Flag
    public boolean PF; // Parity Flag
    public boolean AF; // Auxiliary Carry Flag
    public boolean ZF; // Zero Flag
    public boolean SF; // Sign Flag
    public boolean TF; // Trap Flag
    public boolean IF; // Interrupt Enable Flag
    public boolean DF; // Direction Flag
    public boolean OF; // Overflow Flag
    public boolean NT; // Nested Task Level
    public boolean RF; // Resume Flag
    public boolean VM; // Virtual 8086 Mode
    public boolean AC; // Alignment Check (486+)
    public int IOPL;   // I/O Privilege Level
    
    /* ----------------------------------------------------- *
     * Parity Lookup Table                                   *
     * ----------------------------------------------------- */
    private final boolean m_parityLUT[];
    
    
    
    public Flags(Intel80386 cpu) {
        
        m_parityLUT = new boolean[0x100];
        for(int i = 0; i < 0x100; i++) {
            
            int bitCount = 0;
            for(int j = 0; j < 8; j++) {
                
                if((i & (1 << j)) != 0)
                    bitCount++;
            }
            
            m_parityLUT[i] = (bitCount & 0x01) == 0;
        }
        
        // TODO: Maybe there is a better solution to this problem than masking
        //       away all flags that aren't supported by a particular cpu
        switch(cpu.getCPUType()) {
            
            // The i386 cpu doesn't have the alignment check flag
            case i386:
                m_capabilityMask = MASK_EFLAGS & ~MASK_ALIGN_CHECK;
                break;
            
            // The i486 has all currently available flags
            case i486:
                m_capabilityMask = MASK_EFLAGS;
                break;
                
            default:
                throw new IllegalArgumentException("Unknown cpu type");
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
        NT = false;
        RF = false;
        VM = false;
        AC = false;
        
        IOPL = 0;
    }
    
    
    
    public void setValue(int value, int changeMask) {
        
        changeMask &= m_capabilityMask;
        
        if((changeMask & MASK_ALIGN_CHECK) != 0)
            AC = (value & MASK_ALIGN_CHECK) != 0;
        if((changeMask & MASK_VM_8086) != 0)
            VM = (value & MASK_VM_8086) != 0;
        if((changeMask & MASK_RESUME) != 0)
            RF = (value & MASK_RESUME) != 0;
        if((changeMask & MASK_NESTED_TASK) != 0)
            NT = (value & MASK_NESTED_TASK) != 0;
        if((changeMask & MASK_OVERFLOW) != 0)
            OF = (value & MASK_OVERFLOW) != 0;
        if((changeMask & MASK_DIRECTION) != 0)
            DF = (value & MASK_DIRECTION) != 0;
        if((changeMask & MASK_INTERRUPT_ENABLE) != 0)
            IF = (value & MASK_INTERRUPT_ENABLE) != 0;
        if((changeMask & MASK_TRAP) != 0)
            TF = (value & MASK_TRAP) != 0;
        if((changeMask & MASK_SIGN) != 0)
            SF = (value & MASK_SIGN) != 0;
        if((changeMask & MASK_ZERO) != 0)
            ZF = (value & MASK_ZERO) != 0;
        if((changeMask & MASK_AUXILIARY_CARRY) != 0)
            AF = (value & MASK_AUXILIARY_CARRY) != 0;
        if((changeMask & MASK_PARITY) != 0)
            PF = (value & MASK_PARITY) != 0;
        if((changeMask & MASK_CARRY) != 0)
            CF = (value & MASK_CARRY) != 0;
        if((changeMask & MASK_IOPL) != 0)
            IOPL = (value & MASK_IOPL) >>> 12;
    }
    
    public int getValue() {
        
        int flags = IOPL << 12;
        
        if(AC) flags |= MASK_ALIGN_CHECK;
        if(VM) flags |= MASK_VM_8086;
        if(RF) flags |= MASK_RESUME;
        if(NT) flags |= MASK_NESTED_TASK;
        if(OF) flags |= MASK_OVERFLOW;
        if(DF) flags |= MASK_DIRECTION;
        if(IF) flags |= MASK_INTERRUPT_ENABLE;
        if(TF) flags |= MASK_TRAP;
        if(SF) flags |= MASK_SIGN;
        if(ZF) flags |= MASK_ZERO;
        if(AF) flags |= MASK_AUXILIARY_CARRY;
        if(PF) flags |= MASK_PARITY;
        if(CF) flags |= MASK_CARRY;
        
        return 0x00000002 | (flags & m_capabilityMask);
    }
    
    
    
    public void setSZP8(int data) {
        
        SF = (data & 0x80) != 0;
        ZF = (data & 0xff) == 0;
        PF = m_parityLUT[data & 0xff];
    }
    
    public void setSZP16(int data) {
        
        SF = (data & 0x8000) != 0;
        ZF = (data & 0xffff) == 0;
        PF = m_parityLUT[data & 0xff];
    }
    
    public void setSZP32(int data) {
        
        SF = (data & 0x80000000) != 0;
        ZF = (data & 0xffffffff) == 0;
        PF = m_parityLUT[data & 0xff];
    }
    
    
    
    @Override
    public String toString() {
        
        String flags = String.format("%s%s%s%s[%02x]%s%s%s%s%s%s%s%s%s",
                    
            AC ? "A" : "a",
            VM ? "V" : "v",
            RF ? "R" : "r",
            NT ? "N" : "n",
            IOPL,
            OF ? "O" : "o",
            DF ? "D" : "d",
            IF ? "I" : "i",
            TF ? "T" : "t",
            SF ? "S" : "s",
            ZF ? "Z" : "z",
            AF ? "A" : "a",
            PF ? "P" : "p",
            CF ? "C" : "c"
        );
        
        return String.format("%08x (%s)", getValue(), flags);
    }
}
