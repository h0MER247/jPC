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
package Hardware.CPU.Intel80386.Register.FPU;

import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Intel80386;
import Utility.MathHelper;
import java.util.Arrays;



public final class FPURegisters {
    
    /* ----------------------------------------------------- *
     * FPU status register                                   *
     * ----------------------------------------------------- */
    private static final int STATUS_EX_INVALID_OPERATION = 0x0001;
    private static final int STATUS_EX_DENORMALIZED = 0x0002;
    private static final int STATUS_EX_ZERO_DIVIDE = 0x0004;
    private static final int STATUS_EX_OVERFLOW = 0x0008;
    private static final int STATUS_EX_UNDERFLOW = 0x0010;
    private static final int STATUS_EX_PRECISION = 0x0020;
    private static final int STATUS_EX_MASK = 0x003f;
    private static final int STATUS_EX_STACK_FAULT = 0x0040;
    private static final int STATUS_EX_EXCEPTION_FLAG = 0x0080;
    public static final int STATUS_CC_MASK = 0x4700;
    public static final int STATUS_CC_C3 = 0x4000;
    public static final int STATUS_CC_C2 = 0x0400;
    public static final int STATUS_CC_C1 = 0x0200;
    public static final int STATUS_CC_C0 = 0x0100;
    private static final int STATUS_SP_MASK = 0x3800;
    private static final int STATUS_BUSY = 0x8000;
    private int m_status;
    
    /* ----------------------------------------------------- *
     * FPU control register                                  *
     * ----------------------------------------------------- */
    private static final int CTRL_RM_MASK = 0x0c00;
    private static final int CTRL_RM_NEAREST = 0x0000;
    private static final int CTRL_RM_ROUND_DOWN = 0x0400;
    private static final int CTRL_RM_ROUND_UP = 0x0800;
    private static final int CTRL_RM_TRUNCATE = 0x0c00;
    private static final int CTRL_PC_MASK = 0x0300;
    private static final int CTRL_PC_24BIT = 0x0000;
    private static final int CTRL_PC_53BIT = 0x0200;
    private static final int CTRL_PC_64BIT = 0x0300;
    private static final int CTRL_INT_EX_MASK = 0x003f;
    private static final int CTRL_INT_EX_INVALID_OPERATION = 0x0001;
    private static final int CTRL_INT_EX_DENORMALIZED = 0x0002;
    private static final int CTRL_INT_EX_ZERO_DIVIDE = 0x0004;
    private static final int CTRL_INT_EX_OVERFLOW = 0x0008;
    private static final int CTRL_INT_EX_UNDERFLOW = 0x0010;
    private static final int CTRL_INT_EX_PRECISION = 0x0020;
    
    private int m_control;
    
    /* ----------------------------------------------------- *
     * FPU tag register                                      *
     * ----------------------------------------------------- */
    private static final int TAG_VALID = 0x00;
    private static final int TAG_ZERO = 0x01;
    private static final int TAG_SPECIAL = 0x02;
    private static final int TAG_EMPTY = 0x03;
    private final int[] m_tag;
    
    /* ----------------------------------------------------- *
     * FPU data stack                                        *
     * ----------------------------------------------------- */
    private final double[] m_stack;
    
    
    private final Intel80386 m_cpu;
    
    
    
    public FPURegisters(Intel80386 cpu) {
        
        m_cpu = cpu;
        
        m_stack = new double[8];
        m_tag = new int[8];
    }
    
    
    
    public void reset() {
        
        Arrays.fill(m_stack, 0.0);
        Arrays.fill(m_tag, TAG_EMPTY);
        
        m_status = 0x0000;
        m_control = CTRL_PC_64BIT | CTRL_INT_EX_MASK;
    }
    
    
    
    public void compareWithST0(double src) {
        
        double st0 = getST0();
        
        if(Double.isNaN(st0) || Double.isNaN(src)) {
            
            if(setException(STATUS_EX_INVALID_OPERATION))
                m_status |= STATUS_CC_C0 | STATUS_CC_C2 | STATUS_CC_C3;
        }
        else {
            
            m_status &= ~(STATUS_CC_C0 | STATUS_CC_C2 | STATUS_CC_C3);
            
            if(st0 > src) {
            }
            else if(st0 < src)
                m_status |= STATUS_CC_C0;
            else if(Math.abs(st0 - src) == 0.0)
                m_status |= STATUS_CC_C3;
        }
    }
    
    public void compareUnorderedWithST0(double src) {
        
        double st0 = getST0();
        
        if(Double.isNaN(st0) || Double.isNaN(src)) {
            
            if(MathHelper.isQuietNaN(st0) &&
               MathHelper.isQuietNaN(src)) {
                
                m_status |= STATUS_CC_C0 | STATUS_CC_C2 | STATUS_CC_C3;
            }
            else {
                
                if(setException(m_control))
                    m_status |= STATUS_CC_C0 | STATUS_CC_C2 | STATUS_CC_C3;
            }
        }
        else {
            
            m_status &= ~(STATUS_CC_C0 | STATUS_CC_C2 | STATUS_CC_C3);
            
            if(st0 > src) {
            }
            else if(st0 < src)
                m_status |= STATUS_CC_C0;
            else if(Math.abs(st0 - src) == 0.0)
                m_status |= STATUS_CC_C3;
        }
    }
    
    
    
    public void clearExceptions() {
        
        m_status &= 0x7f00;
    }
    
    private boolean setException(int exception) {
        
        m_status |= exception;
        
        if(((m_status & ~m_control) & STATUS_EX_MASK) != 0) {
        
            m_status |= STATUS_EX_EXCEPTION_FLAG;
            
            if(m_cpu.CR.isNumericErrorEnabled())
                throw CPUException.getCoprocessorError();
            
            return false;
        }
        
        return true;
    }
    
    public boolean generateZeroDivisionException() {
        
        return setException(STATUS_EX_ZERO_DIVIDE);
    }
    
    
    
    public void setConditions(int conditions) {
        
        m_status |= conditions & STATUS_CC_MASK;
    }
    
    public void clearConditions(int conditions) {
        
        m_status &= ~(conditions & STATUS_CC_MASK);
    }
    
    public void clearConditions() {
        
        m_status &= ~(STATUS_CC_C0 |
                      STATUS_CC_C1 |
                      STATUS_CC_C2 |
                      STATUS_CC_C3);
    }
    
    
    
    public boolean isRegisterValid(int reg) {
        
        return m_tag[getStackPtr(reg)] == TAG_VALID;
    }

    public boolean isRegisterZero(int reg) {
        
        return m_tag[getStackPtr(reg)] == TAG_ZERO;
    }
    
    public boolean isRegisterSpecial(int reg) {
        
        return m_tag[getStackPtr(reg)] == TAG_SPECIAL;
    }
    
    public boolean isRegisterEmpty(int reg) {
        
        return m_tag[getStackPtr(reg)] == TAG_EMPTY;
    }
    
    
    
    public double getRoundedValue(double value) {
        
        if(Double.isInfinite(value))
            return value;
        
        switch(m_control & CTRL_RM_MASK) {
            
            case CTRL_RM_NEAREST: return MathHelper.roundToNearestEven(value);
            case CTRL_RM_ROUND_DOWN: return MathHelper.roundToNegativeInfinity(value);
            case CTRL_RM_ROUND_UP: return MathHelper.roundToPositiveInfinity(value);
            case CTRL_RM_TRUNCATE: return MathHelper.roundToZero(value);
            
            default:
                throw new IllegalArgumentException("Invalid rounding mode");
        }
    }
    
    public double roundTowardZero(double value) {
        
        if(value < 0.0)
            return Math.ceil(value);
        else
            return Math.floor(value);
    }
    
    public void setStatus(int status) {
        
        m_status = status;
    }
    
    public void setC0(boolean value) {
        
        if(value)
            m_status |= STATUS_CC_C0;
        else
            m_status &= ~STATUS_CC_C0;
    }
    
    public void setC1(boolean value) {
        
        if(value)
            m_status |= STATUS_CC_C1;
        else
            m_status &= ~STATUS_CC_C1;
    }
    
    public void setC2(boolean value) {
        
        if(value)
            m_status |= STATUS_CC_C2;
        else
            m_status &= ~STATUS_CC_C2;
    }
    
    public void setC3(boolean value) {
        
        if(value)
            m_status |= STATUS_CC_C3;
        else
            m_status &= ~STATUS_CC_C3;
    }
    
    
    
    public int getStatus() {
    
        return m_status;
    }
    
    public int getTags() {
        
        int tags = 0;
        for(int i = 7; i >= 0; i--)
            tags |= m_tag[i] << (i << 1);
        
        return tags;
    }
    
    public void setTags(int tags) {
        
        for(int i = 0; i < 8; i++, tags >>= 2)
            m_tag[i] = tags & 0x03;
    }
    
    
    
    public void setControl(int control) {
        
        m_control = control;
    }
    
    public int getControl() {
        
        return m_control;
    }
    
    
    
    public void freeTag(int reg) {
        
        setTag(reg, TAG_EMPTY);
    }
    
    public void setTag(int reg, int tag) {
        
        m_tag[getStackPtr(reg)] = tag;
    }
    
    private int getStackPtr(int idx) {
        
        return ((m_status >>> 11) + idx) & 0x07;
    }
    
    private void setStackPtr(int topIdx) {
        
        m_status &= ~STATUS_SP_MASK;
        m_status |= (topIdx & 0x07) << 11;
    }
    
    
    
    public void decrementStackPtr() {
        
        setStackPtr(getStackPtr(0) - 1);
    }
    
    public void incrementStackPtr() {
        
        setStackPtr(getStackPtr(0) + 1);
    }
    
    
    
    public void pushStack(double value) {
        
        decrementStackPtr();
        setValue(0, value);
    }
    
    public void popStack() {
        
        freeTag(0);
        incrementStackPtr();
    }
    
    
    
    public void setST0(double value) {
        
        setValue(0, value);
    }
    
    public void setST1(double value) {
        
        setValue(1, value);
    }
    
    public void setST(int reg, double value) {
        
        setValue(reg, value);
    }
    
    private void setValue(int reg, double value) {
        
        m_stack[getStackPtr(reg)] = value;
            
        if(value == 0.0)
            setTag(reg, TAG_ZERO);
        else if(Double.isInfinite(value) || Double.isNaN(value))
            setTag(reg, TAG_SPECIAL);
        else
            setTag(reg, TAG_VALID);
    }
    
    
    
    public double getST0() {
        
        return getValue(0);
    }
    
    public double getST1() {
        
        return getValue(1);
    }
    
    public double getST(int reg) {
        
        return getValue(reg);
    }
    
    private double getValue(int idx) {
        
        return m_stack[getStackPtr(idx)];
    }
    
    
    
    
    @Override
    public String toString() {
        
        return String.format("ST0:%f, ST1:%f, ST2:%f, ST3:%f, ST4:%f, ST5:%f, ST6:%f, ST7:%f, C0:%b, C1:%b, C2:%b, C3:%b, Tags: %08X, Status:%04x, Control:%04x, TOP:%d",
                
                getST(0),
                getST(1),
                getST(2),
                getST(3),
                getST(4),
                getST(5),
                getST(6),
                getST(7),
                (m_status & STATUS_CC_C0) != 0,
                (m_status & STATUS_CC_C1) != 0,
                (m_status & STATUS_CC_C2) != 0,
                (m_status & STATUS_CC_C3) != 0,
                getTags(),
                m_status,
                m_control,
                (m_status & STATUS_SP_MASK) >> 11
        );
    }
}
