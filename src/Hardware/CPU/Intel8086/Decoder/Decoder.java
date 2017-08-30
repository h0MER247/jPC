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
package Hardware.CPU.Intel8086.Decoder;

import Hardware.CPU.Intel8086.Codeblock.CodeBlock;
import Hardware.CPU.Intel8086.Condition.Conditions.*;
import Hardware.CPU.Intel8086.Instructions.Instruction;
import Hardware.CPU.Intel8086.Exceptions.DecoderException;
import Hardware.CPU.Intel8086.Instructions.Arithmetic.*;
import Hardware.CPU.Intel8086.Instructions.Datatransfer.*;
import Hardware.CPU.Intel8086.Instructions.Flags.*;
import Hardware.CPU.Intel8086.Instructions.IO.*;
import Hardware.CPU.Intel8086.Instructions.Logical.*;
import Hardware.CPU.Intel8086.Instructions.Misc.*;
import Hardware.CPU.Intel8086.Instructions.Programflow.*;
import Hardware.CPU.Intel8086.Instructions.Repeats.*;
import Hardware.CPU.Intel8086.Instructions.Shifts.*;
import Hardware.CPU.Intel8086.Instructions.String.*;
import Hardware.CPU.Intel8086.Intel8086;
import Hardware.CPU.Intel8086.Operands.Flags.OperandFlags;
import Hardware.CPU.Intel8086.Operands.Immediate.OperandImmediate;
import Hardware.CPU.Intel8086.Operands.Memory.OperandAddress;
import Hardware.CPU.Intel8086.Operands.Memory.OperandMemory16;
import Hardware.CPU.Intel8086.Operands.Memory.OperandMemory8;
import Hardware.CPU.Intel8086.Operands.Operand;
import Hardware.CPU.Intel8086.Operands.Register.OperandRegister16;
import Hardware.CPU.Intel8086.Operands.Register.OperandRegister8;
import Hardware.CPU.Intel8086.Operands.Segment.OperandSegment;
import Hardware.CPU.Intel8086.Pointer.PointerDisplacement;
import Hardware.CPU.Intel8086.Pointer.PointerBase;
import Hardware.CPU.Intel8086.Pointer.PointerBaseDisplacement;
import Hardware.CPU.Intel8086.Pointer.Pointer;
import Hardware.CPU.Intel8086.Pointer.PointerBaseIndex;
import Hardware.CPU.Intel8086.Pointer.PointerBaseIndexDisplacement;
import Hardware.CPU.Intel8086.Register.Reg16;
import Hardware.CPU.Intel8086.Register.Reg8;
import Hardware.CPU.Intel8086.Segments.Segment;
import Scheduler.Scheduler;
import static Utility.SignExtension.signExtend8To16;
import java.util.ArrayList;



public final class Decoder {
    
    /* ----------------------------------------------------- *
     * Decoder table                                         *
     * ----------------------------------------------------- */
    private interface InstructionDecoder {
        
        Instruction decode();
    }
    private final InstructionDecoder[] m_decoderTable;
    
    /* ----------------------------------------------------- *
     * MOD/RM byte decoding                                  *
     * ----------------------------------------------------- */
    private int m_mod;
    private int m_reg;
    private int m_rm;
    private int m_displacement;
    private boolean m_hasReadModRM;
    
    /* ----------------------------------------------------- *
     * Registers and segments                                *
     * ----------------------------------------------------- */
    private final Reg16[] m_regs16;
    private final Reg8[] m_regs8;
    private final Segment[] m_segs;
    private final Segment[] m_segsMemPtr;
    
    /* ----------------------------------------------------- *
     * Segment overrides                                     *
     * ----------------------------------------------------- */
    private Segment m_segOverride;
    
    /* ----------------------------------------------------- *
     * Prefixes                                              *
     * ----------------------------------------------------- */
    private boolean m_repZ;
    private boolean m_repNZ;
    private boolean m_lock;
    private boolean m_wait;
    
    /* ----------------------------------------------------- *
     * Decoder context                                       *
     * ----------------------------------------------------- */
    private boolean m_isRunning;
    private int m_base;
    private int m_offset;
    private final ArrayList<Instruction> m_buffer;
    
    /* ----------------------------------------------------- *
     * Reference to the Intel 8086 CPU                       *
     * ----------------------------------------------------- */
    private final Intel8086 m_cpu;
    
    
    
    public Decoder(Intel8086 cpu) {
        
        m_cpu = cpu;
        
        // Initialize buffer
        m_buffer = new ArrayList<>(4096);
        
        // Initialize 16 bit register references
        m_regs16 = new Reg16[] {
            
            m_cpu.AX, m_cpu.CX,
            m_cpu.DX, m_cpu.BX,
            m_cpu.SP, m_cpu.BP,
            m_cpu.SI, m_cpu.DI
        };
        
        // Initialize 8 bit register references
        m_regs8 = new Reg8[] {
            
            m_cpu.AL, m_cpu.CL,
            m_cpu.DL, m_cpu.BL,
            m_cpu.AH, m_cpu.CH,
            m_cpu.DH, m_cpu.BH
        };
        
        // Initialize segment register references
        m_segs = new Segment[] {
            
            m_cpu.ES, m_cpu.CS,
            m_cpu.SS, m_cpu.DS
        };
        
        // Initialize segment register references for memory pointers
        m_segsMemPtr = new Segment[] {
            
            m_cpu.DS, m_cpu.DS,
            m_cpu.SS, m_cpu.SS,
            m_cpu.DS, m_cpu.DS,
            m_cpu.SS, m_cpu.DS
        };
        
        // <editor-fold defaultstate="collapsed" desc="Decoder table initialization">
        
        m_decoderTable = new InstructionDecoder[] {
        
            ( /* 0x00: add rm8, r8 */ ) -> {

                return new ADD8(m_cpu,
                                buildRM8(),
                                buildREG8(),
                                getCycleCount(isMOD3() ? 3 : 24));
            },
        
            ( /* 0x01: add rm16, r16 */ ) -> {

                return new ADD16(m_cpu,
                                 buildRM16(),
                                 buildREG16(),
                                 getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x02: add r8, rm8 */ ) -> {

                return new ADD8(m_cpu,
                                buildREG8(),
                                buildRM8(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x03: add r16, rm16 */ ) -> {

                return new ADD16(m_cpu,
                                 buildREG16(),
                                 buildRM16(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x04: add al, imm8 */ ) -> {

                return new ADD8(m_cpu,
                                buildAL(),
                                buildIMM8(),
                                getCycleCount(4));
            },

            ( /* 0x05: add ax, imm16 */ ) -> {

                return new ADD16(m_cpu,
                                 buildAX(),
                                 buildIMM16(),
                                 getCycleCount(4));
            },

            ( /* 0x06: push es */ ) -> {

                return new PUSH(m_cpu,
                                buildES(),
                                getCycleCount(14));
            },

            ( /* 0x07: pop es */ ) -> {

                return new POP(m_cpu,
                               buildES(),
                               getCycleCount(12));
            },
            
            ( /* 0x08: or rm8, r8 */ ) -> {

                return new OR8(m_cpu,
                               buildRM8(),
                               buildREG8(),
                               getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x09: or rm16, r16 */ ) -> {

                return new OR16(m_cpu,
                                buildRM16(),
                                buildREG16(),
                                getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x0a: or r8, rm8 */ ) -> {

                return new OR8(m_cpu,
                               buildREG8(),
                               buildRM8(),
                               getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x0b: or r16, rm16 */ ) -> {

                return new OR16(m_cpu,
                                buildREG16(),
                                buildRM16(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x0c: or al, imm8 */ ) -> {

                return new OR8(m_cpu,
                               buildAL(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0x0d: or ax, imm16 */ ) -> {

                return new OR16(m_cpu,
                                buildAX(),
                                buildIMM16(),
                                getCycleCount(4));
            },

            ( /* 0x0e: push cs */ ) -> {

                return new PUSH(m_cpu,
                                buildCS(),
                                getCycleCount(14));
            },

            ( /* 0x0f: pop cs */ ) -> {
                
                m_isRunning = false;
                
                return new POP(m_cpu,
                               buildCS(),
                               getCycleCount(12));
            },
            
            ( /* 0x10: adc rm8, r8 */ ) -> {

                return new ADC8(m_cpu,
                                buildRM8(),
                                buildREG8(),
                                getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x11: adc rm16, r16 */ ) -> {

                return new ADC16(m_cpu,
                                 buildRM16(),
                                 buildREG16(),
                                 getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x12: adc r8, rm8 */ ) -> {

                return new ADC8(m_cpu,
                                buildREG8(),
                                buildRM8(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x13: adc r16, rm16 */ ) -> {

                return new ADC16(m_cpu,
                                 buildREG16(),
                                 buildRM16(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x14: adc al, imm8 */ ) -> {

                return new ADC8(m_cpu,
                                buildAL(),
                                buildIMM8(),
                                getCycleCount(4));
            },

            ( /* 0x15: adc ax, imm16 */ ) -> {

                return new ADC16(m_cpu,
                                 buildAX(),
                                 buildIMM16(),
                                 getCycleCount(4));
            },

            ( /* 0x16: push ss */ ) -> {

                return new PUSH(m_cpu,
                                buildSS(),
                                getCycleCount(14));
            },

            ( /* 0x17: pop ss */ ) -> {

                return new POP(m_cpu,
                               buildSS(),
                               getCycleCount(12));
            },
            
            ( /* 0x18: sbb rm8, r8 */ ) -> {

                return new SBB8(m_cpu,
                                buildRM8(),
                                buildREG8(),
                                getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x19: sbb rm16, r16 */ ) -> {

                return new SBB16(m_cpu,
                                 buildRM16(),
                                 buildREG16(),
                                 getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x1a: sbb r8, rm8 */ ) -> {

                return new SBB8(m_cpu,
                                buildREG8(),
                                buildRM8(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x1b: sbb r16, rm16 */ ) -> {

                return new SBB16(m_cpu,
                                 buildREG16(),
                                 buildRM16(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x1c: sbb al, imm8 */ ) -> {

                return new SBB8(m_cpu,
                                buildAL(),
                                buildIMM8(),
                                getCycleCount(4));
            },

            ( /* 0x1d: sbb ax, imm16 */ ) -> {

                return new SBB16(m_cpu,
                                 buildAX(),
                                 buildIMM16(),
                                 getCycleCount(4));
            },

            ( /* 0x1e: push ds */ ) -> {

                return new PUSH(m_cpu,
                                buildDS(),
                                getCycleCount(14));
            },

            ( /* 0x1f: pop ds */ ) -> {

                return new POP(m_cpu,
                               buildDS(),
                               getCycleCount(12));
            },
            
            ( /* 0x20: and rm8, r8 */ ) -> {

                return new AND8(m_cpu,
                                buildRM8(),
                                buildREG8(),
                                getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x21: and rm16, r16 */ ) -> {

                return new AND16(m_cpu,
                                 buildRM16(),
                                 buildREG16(),
                                 getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x22: and r8, rm8 */ ) -> {

                return new AND8(m_cpu,
                                buildREG8(),
                                buildRM8(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x23: and r16, rm16 */ ) -> {

                return new AND16(m_cpu,
                                 buildREG16(),
                                 buildRM16(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x24: and al, imm8 */ ) -> {

                return new AND8(m_cpu,
                                buildAL(),
                                buildIMM8(),
                                4);
            },

            ( /* 0x25: and ax, imm16 */ ) -> {

                return new AND16(m_cpu,
                                 buildAX(),
                                 buildIMM16(),
                                 getCycleCount(4));
            },

            ( /* 0x26: es: */ ) -> {

                //if(m_segOverride == null)
                    m_segOverride = m_cpu.ES;
                
                return null;
            },

            ( /* 0x27: daa */ ) -> {

                return new DAA(m_cpu,
                               getCycleCount(4));
            },
            
            ( /* 0x28: sub rm8, r8 */ ) -> {

                return new SUB8(m_cpu,
                                buildRM8(),
                                buildREG8(),
                                getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x29: sub rm16, r16 */ ) -> {

                return new SUB16(m_cpu,
                                 buildRM16(),
                                 buildREG16(),
                                 getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x2a: sub r8, rm8 */ ) -> {

                return new SUB8(m_cpu,
                                buildREG8(),
                                buildRM8(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x2b: sub r16, rm16 */ ) -> {

                return new SUB16(m_cpu,
                                 buildREG16(),
                                 buildRM16(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x2c: sub al, imm8 */ ) -> {

                return new SUB8(m_cpu,
                                buildAL(),
                                buildIMM8(),
                                getCycleCount(4));
            },

            ( /* 0x2d: sub ax, imm16 */ ) -> {

                return new SUB16(m_cpu,
                                 buildAX(),
                                 buildIMM16(),
                                 getCycleCount(4));
            },

            ( /* 0x2e: cs: */ ) -> {

                //if(m_segOverride == null)
                    m_segOverride = m_cpu.CS;
                
                return null;
            },

            ( /* 0x2f: das */ ) -> {

                return new DAS(m_cpu,
                               getCycleCount(4));
            },
            
            ( /* 0x30: xor rm8, r8 */ ) -> {

                return new XOR8(m_cpu,
                                buildRM8(),
                                buildREG8(),
                                getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x31: xor rm16, r16 */ ) -> {

                return new XOR16(m_cpu,
                                 buildRM16(),
                                 buildREG16(),
                                 getCycleCount(isMOD3() ? 3 : 24));
            },

            ( /* 0x32: xor r8, rm8 */ ) -> {

                return new XOR8(m_cpu,
                                buildREG8(),
                                buildRM8(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x33: xor r16, rm16 */ ) -> {

                return new XOR16(m_cpu,
                                 buildREG16(),
                                 buildRM16(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x34: xor al, imm8 */ ) -> {

                return new XOR8(m_cpu,
                                buildAL(),
                                buildIMM8(),
                                getCycleCount(4));
            },

            ( /* 0x35: xor ax, imm16 */ ) -> {

                return new XOR16(m_cpu,
                                 buildAX(),
                                 buildIMM16(),
                                 getCycleCount(4));
            },

            ( /* 0x36: ss: */ ) -> {

                //if(m_segOverride == null)
                    m_segOverride = m_cpu.SS;
                
                return null;
            },

            ( /* 0x37: aaa */ ) -> {

                return new AAA(m_cpu,
                               getCycleCount(8));
            },
            
            ( /* 0x38: cmp rm8, r8 */ ) -> {

                return new CMP8(m_cpu,
                                buildRM8(),
                                buildREG8(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x39: cmp rm16, r16 */ ) -> {

                return new CMP16(m_cpu,
                                 buildRM16(),
                                 buildREG16(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x3a: cmp r8, rm8 */ ) -> {

                return new CMP8(m_cpu,
                                buildREG8(),
                                buildRM8(),
                                getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x3b: cmp r16, rm16 */ ) -> {

                return new CMP16(m_cpu,
                                 buildREG16(),
                                 buildRM16(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x3c: cmp al, imm8 */ ) -> {

                return new CMP8(m_cpu,
                                buildAL(),
                                buildIMM8(),
                                getCycleCount(4));
            },

            ( /* 0x3d: cmp ax, imm16 */ ) -> {

                return new CMP16(m_cpu,
                                 buildAX(),
                                 buildIMM16(),
                                 getCycleCount(4));
            },

            ( /* 0x3e: ds: */ ) -> {

                //if(m_segOverride == null)
                    m_segOverride = m_cpu.DS;
                
                return null;
            },

            ( /* 0x3f: aas */ ) -> {

                return new AAS(m_cpu,
                               getCycleCount(8));
            },

            ( /* 0x40: inc ax */ ) -> {

                return new INC16(m_cpu,
                                 buildAX(),
                                 getCycleCount(3));
            },

            ( /* 0x41: inc cx */ ) -> {

                return new INC16(m_cpu,
                                 buildCX(),
                                 getCycleCount(3));
            },

            ( /* 0x42: inc dx */ ) -> {

                return new INC16(m_cpu,
                                 buildDX(),
                                 getCycleCount(3));
            },

            ( /* 0x43: inc bx */ ) -> {

                return new INC16(m_cpu,
                                 buildBX(),
                                 getCycleCount(3));
            },

            ( /* 0x44: inc sp */ ) -> {

                return new INC16(m_cpu,
                                 buildSP(),
                                 getCycleCount(3));
            },

            ( /* 0x45: inc bp */ ) -> {

                return new INC16(m_cpu,
                                 buildBP(),
                                 getCycleCount(3));
            },

            ( /* 0x46: inc si */ ) -> {

                return new INC16(m_cpu,
                                 buildSI(),
                                 getCycleCount(3));
            },

            ( /* 0x47: inc di */ ) -> {

                return new INC16(m_cpu,
                                 buildDI(),
                                 getCycleCount(3));
            },

            ( /* 0x48: dec ax */ ) -> {

                return new DEC16(m_cpu,
                                 buildAX(),
                                 getCycleCount(3));
            },

            ( /* 0x49: dec cx */ ) -> {

                return new DEC16(m_cpu,
                                 buildCX(),
                                 getCycleCount(3));
            },

            ( /* 0x4a: dec dx */ ) -> {

                return new DEC16(m_cpu,
                                 buildDX(),
                                 getCycleCount(3));
            },

            ( /* 0x4b: dec bx */ ) -> {

                return new DEC16(m_cpu,
                                 buildBX(),
                                 getCycleCount(3));
            },

            ( /* 0x4c: dec sp */ ) -> {

                return new DEC16(m_cpu,
                                 buildSP(),
                                 getCycleCount(3));
            },

            ( /* 0x4d: dec bp */ ) -> {

                return new DEC16(m_cpu,
                                 buildBP(),
                                 getCycleCount(3));
            },

            ( /* 0x4e: dec si */ ) -> {

                return new DEC16(m_cpu,
                                 buildSI(),
                                 getCycleCount(3));
            },

            ( /* 0x4f: dec di */ ) -> {

                return new DEC16(m_cpu,
                                 buildDI(),
                                 getCycleCount(3));
            },

            ( /* 0x50: push ax */ ) -> {

                return new PUSH(m_cpu,
                                buildAX(),
                                getCycleCount(15));
            },

            ( /* 0x51: push cx */ ) -> {

                return new PUSH(m_cpu,
                                buildCX(),
                                getCycleCount(15));
            },

            ( /* 0x52: push dx */ ) -> {

                return new PUSH(m_cpu,
                                buildDX(),
                                getCycleCount(15));
            },

            ( /* 0x53: push bx */ ) -> {

                return new PUSH(m_cpu,
                                buildBX(),
                                getCycleCount(15));
            },

            ( /* 0x54: push sp */ ) -> {

                return new PUSH(m_cpu,
                                buildSP(),
                                getCycleCount(15));
            },

            ( /* 0x55: push bp */ ) -> {

                return new PUSH(m_cpu,
                                buildBP(),
                                getCycleCount(15));
            },

            ( /* 0x56: push si */ ) -> {

                return new PUSH(m_cpu,
                                buildSI(),
                                getCycleCount(15));
            },

            ( /* 0x57: push di */ ) -> {

                return new PUSH(m_cpu,
                                buildDI(),
                                getCycleCount(15));
            },

            ( /* 0x58: pop ax */ ) -> {

                return new POP(m_cpu,
                               buildAX(),
                               getCycleCount(12));
            },

            ( /* 0x59: pop cx */ ) -> {

                return new POP(m_cpu,
                               buildCX(),
                               getCycleCount(12));
            },

            ( /* 0x5a: pop dx */ ) -> {

                return new POP(m_cpu,
                               buildDX(),
                               getCycleCount(12));
            },

            ( /* 0x5b: pop bx */ ) -> {

                return new POP(m_cpu,
                               buildBX(),
                               getCycleCount(12));
            },

            ( /* 0x5c: pop sp */ ) -> {

                return new POP(m_cpu,
                               buildSP(),
                               getCycleCount(12));
            },

            ( /* 0x5d: pop bp */ ) -> {

                return new POP(m_cpu,
                               buildBP(),
                               getCycleCount(12));
            },

            ( /* 0x5e: pop si */ ) -> {

                return new POP(m_cpu,
                               buildSI(),
                               getCycleCount(12));
            },

            ( /* 0x5f: pop di */ ) -> {

                return new POP(m_cpu,
                               buildDI(),
                               getCycleCount(12));
            },

            ( /* 0x60: jo rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionOverflow(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x61: jno rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotOverflow(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x62: jb rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionBellow(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x63: jnb rel8 (alias) */ ) -> {

                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotBellow(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x64: jz rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionZero(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x65: jnz rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotZero(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x66: jbe rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionBellowOrEqual(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x67: jnbe rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotBellowOrEqual(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x68: js rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionSign(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x69: jns rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotSign(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x6a: jp rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionParity(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x6b: jnp rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotParity(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x6c: jl rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionLess(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x6d: jnl rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotLess(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x6e: jle rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionLessOrEqual(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x6f: jnle rel8 (alias) */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotLessOrEqual(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x70: jo rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionOverflow(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x71: jno rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotOverflow(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x72: jb rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionBellow(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x73: jnb rel8 */ ) -> {

                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotBellow(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x74: jz rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionZero(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x75: jnz rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotZero(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x76: jbe rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionBellowOrEqual(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x77: jnbe rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotBellowOrEqual(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x78: js rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionSign(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x79: jns rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotSign(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x7a: jp rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionParity(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x7b: jnp rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotParity(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x7c: jl rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionLess(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x7d: jnl rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotLess(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x7e: jle rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionLessOrEqual(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x7f: jnle rel8 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionNotLessOrEqual(m_cpu),
                                           getCycleCount(16),
                                           getCycleCount(4));
            },

            ( /* 0x80: grp1 rm8, imm8  */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* add rm8, imm8 */
                    case 0x00:
                        return new ADD8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* or rm8, imm8 */
                    case 0x01:
                        return new OR8(m_cpu,
                                       buildRM8(),
                                       buildIMM8(),
                                       getCycleCount(isMOD3() ? 4 : 23));

                    /* adc rm8, imm8 */
                    case 0x02:
                        return new ADC8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* sbb rm8, imm8 */
                    case 0x03:
                        return new SBB8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* and rm8, imm8 */
                    case 0x04:
                        return new AND8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* sub rm8, imm8 */
                    case 0x05:
                        return new SUB8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* xor rm8, imm8 */
                    case 0x06:
                        return new XOR8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* cmp rm8, imm8 */
                    case 0x07:
                        return new CMP8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 14));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP1 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0x81: grp1 rm16, imm16  */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* add rm16, imm16 */
                    case 0x00:
                        return new ADD16(m_cpu,
                                         buildRM16(),
                                         buildIMM16(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* or rm16, imm16 */
                    case 0x01:
                        return new OR16(m_cpu,
                                        buildRM16(),
                                        buildIMM16(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* adc rm16, imm16 */
                    case 0x02:
                        return new ADC16(m_cpu,
                                         buildRM16(),
                                         buildIMM16(),
                                         getCycleCount(isMOD3() ? 4 : 23));
                        
                    /* sbb rm16, imm16 */
                    case 0x03:
                        return new SBB16(m_cpu,
                                         buildRM16(),
                                         buildIMM16(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* and rm16, imm16 */
                    case 0x04:
                        return new AND16(m_cpu,
                                         buildRM16(),
                                         buildIMM16(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* sub rm16, imm16 */
                    case 0x05:
                        return new SUB16(m_cpu,
                                         buildRM16(),
                                         buildIMM16(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* xor rm16, imm16 */
                    case 0x06:
                        return new XOR16(m_cpu,
                                         buildRM16(),
                                         buildIMM16(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* cmp rm16, imm16 */
                    case 0x07:
                        return new CMP16(m_cpu,
                                         buildRM16(),
                                         buildIMM16(),
                                         getCycleCount(isMOD3() ? 4 : 14));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP1 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0x82: grp1 rm8, imm8  */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* add rm8, imm8 */
                    case 0x00:
                        return new ADD8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* or rm8, imm8 */
                    case 0x01:
                        return new OR8(m_cpu,
                                       buildRM8(),
                                       buildIMM8(),
                                       getCycleCount(isMOD3() ? 4 : 23));

                    /* adc rm8, imm8 */
                    case 0x02:
                        return new ADC8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* sbb rm8, imm8 */
                    case 0x03:
                        return new SBB8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* and rm8, imm8 */
                    case 0x04:
                        return new AND8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* sub rm8, imm8 */
                    case 0x05:
                        return new SUB8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* xor rm8, imm8 */
                    case 0x06:
                        return new XOR8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* cmp rm8, imm8 */
                    case 0x07:
                        return new CMP8(m_cpu,
                                        buildRM8(),
                                        buildIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 14));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP1 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0x83: grp1 rm16, simm8  */ ) -> {
                
                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* add rm16, simm8 */
                    case 0x00:
                        return new ADD16(m_cpu,
                                         buildRM16(),
                                         buildSIMM8(),
                                         getCycleCount(isMOD3() ? 4 : 23));
                        
                    /* or rm16, simm8 */
                    case 0x01:
                        return new OR16(m_cpu,
                                        buildRM16(),
                                        buildSIMM8(),
                                        getCycleCount(isMOD3() ? 4 : 23));

                    /* adc rm16, simm8 */
                    case 0x02:
                        return new ADC16(m_cpu,
                                         buildRM16(),
                                         buildSIMM8(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* sbb rm16, simm8 */
                    case 0x03:
                        return new SBB16(m_cpu,
                                         buildRM16(),
                                         buildSIMM8(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* and rm16, simm8 */
                    case 0x04:
                        return new AND16(m_cpu,
                                         buildRM16(),
                                         buildSIMM8(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* sub rm16, simm8 */
                    case 0x05:
                        return new SUB16(m_cpu,
                                         buildRM16(),
                                         buildSIMM8(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* xor rm16, simm8 */
                    case 0x06:
                        return new XOR16(m_cpu,
                                         buildRM16(),
                                         buildSIMM8(),
                                         getCycleCount(isMOD3() ? 4 : 23));

                    /* cmp rm16, simm8 */
                    case 0x07:
                        return new CMP16(m_cpu,
                                         buildRM16(),
                                         buildSIMM8(),
                                         getCycleCount(isMOD3() ? 4 : 14));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP1 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0x84: test r8, rm8 */ ) -> {

                return new TEST8(m_cpu,
                                 buildREG8(),
                                 buildRM8(),
                                 getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x85: test r16, rm16 */ ) -> {

                return new TEST16(m_cpu,
                                  buildREG16(),
                                  buildRM16(),
                                  getCycleCount(isMOD3() ? 3 : 13));
            },

            ( /* 0x86: xchg r8, rm8 */ ) -> {

                return new XCHG(m_cpu,
                                buildREG8(),
                                buildRM8(),
                                getCycleCount(isMOD3() ? 4 : 25));
            },

            ( /* 0x87: xchg r16, rm16 */ ) -> {

                return new XCHG(m_cpu,
                                buildREG16(),
                                buildRM16(),
                                getCycleCount(isMOD3() ? 4 : 25));
            },

            ( /* 0x88: mov rm8, r8 */ ) -> {

                return new MOV(m_cpu,
                               buildRM8(),
                               buildREG8(),
                               getCycleCount(isMOD3() ? 2 : 13));
            },

            ( /* 0x89: mov rm16, r16 */ ) -> {

                return new MOV(m_cpu,
                               buildRM16(),
                               buildREG16(),
                               getCycleCount(isMOD3() ? 2 : 13));
            },

            ( /* 0x8a: mov r8, rm8 */ ) -> {

                return new MOV(m_cpu,
                               buildREG8(),
                               buildRM8(),
                               getCycleCount(isMOD3() ? 2 : 12));
            },

            ( /* 0x8b: mov r16, rm16 */ ) -> {

                return new MOV(m_cpu,
                               buildREG16(),
                               buildRM16(),
                               getCycleCount(isMOD3() ? 2 : 12));
            },

            ( /* 0x8c: mov rm16, sreg */ ) -> {

                return new MOV(m_cpu,
                               buildRM16(),
                               buildSEGREG(),
                               getCycleCount(isMOD3() ? 2 : 13));
            },

            ( /* 0x8d: lea r16, maddr16 */ ) -> {

                return new LEA(m_cpu,
                               buildREG16(),
                               buildAddress(),
                               getCycleCount(2));
            },

            ( /* 0x8e: mov sreg, rm16 */ ) -> {
                
                return new MOV(m_cpu,
                               buildSEGREG(),
                               buildRM16(),
                               getCycleCount(isMOD3() ? 2 : 12));
            },

            ( /* 0x8f: pop rm16 */ ) -> {

                return new POP(m_cpu,
                               buildRM16(),
                               getCycleCount(25));
            },

            ( /* 0x90: nop */ ) -> {

                return new NOP(m_cpu,
                               getCycleCount(3));
            },

            ( /* 0x91: xchg ax, cx */ ) -> {

                return new XCHG(m_cpu,
                                buildAX(),
                                buildCX(),
                                getCycleCount(3));
            },

            ( /* 0x92: xchg ax, dx */ ) -> {

                return new XCHG(m_cpu,
                                buildAX(),
                                buildDX(),
                                getCycleCount(3));
            },

            ( /* 0x93: xchg ax, bx */ ) -> {

                return new XCHG(m_cpu,
                                buildAX(),
                                buildBX(),
                                getCycleCount(3));
            },

            ( /* 0x94: xchg ax, sp */ ) -> {

                return new XCHG(m_cpu,
                                buildAX(),
                                buildSP(),
                                getCycleCount(3));
            },

            ( /* 0x95: xchg ax, bp */ ) -> {

                return new XCHG(m_cpu,
                                buildAX(),
                                buildBP(),
                                getCycleCount(3));
            },

            ( /* 0x96: xchg ax, si */ ) -> {

                return new XCHG(m_cpu,
                                buildAX(),
                                buildSI(),
                                getCycleCount(3));
            },

            ( /* 0x97: xchg ax, di */ ) -> {

                return new XCHG(m_cpu,
                                buildAX(),
                                buildDI(),
                                getCycleCount(3));
            },

            ( /* 0x98: cbw */ ) -> {

                return new CBW(m_cpu,
                               getCycleCount(2));
            },

            ( /* 0x99: cwd */ ) -> {

                return new CWD(m_cpu,
                               getCycleCount(5));
            },

            ( /* 0x9a: call imm16:16 */ ) -> {

                m_isRunning = false;
                
                return new CALL_FAR(m_cpu,
                                    buildIMM16(),
                                    buildIMM16(),
                                    getCycleCount(36));
            },

            ( /* 0x9b: wait */ ) -> {

                m_wait = true;
                
                return null;
            },

            ( /* 0x9c: pushf */ ) -> {

                return new PUSH(m_cpu,
                                buildFlags(),
                                getCycleCount(14));
            },

            ( /* 0x9d: popf */ ) -> {

                return new POP(m_cpu,
                               buildFlags(),
                               getCycleCount(12));
            },

            ( /* 0x9e: sahf */ ) -> {

                return new SAHF(m_cpu,
                                getCycleCount(4));
            },

            ( /* 0x9f: lahf */ ) -> {

                return new LAHF(m_cpu,
                                getCycleCount(4));
            },

            ( /* 0xa0: mov al, moffs8 */ ) -> {

                return new MOV(m_cpu,
                               buildAL(),
                               buildMOFFS8(),
                               getCycleCount(14));
            },

            ( /* 0xa1: mov ax, moffs16 */ ) -> {

                return new MOV(m_cpu,
                               buildAX(),
                               buildMOFFS16(),
                               getCycleCount(14));
            },

            ( /* 0xa2: mov moffs8, al */ ) -> {

                return new MOV(m_cpu,
                               buildMOFFS8(),
                               buildAL(),
                               getCycleCount(14));
            },

            ( /* 0xa3: mov moffs16, ax */ ) -> {

                return new MOV(m_cpu,
                               buildMOFFS16(),
                               buildAX(),
                               getCycleCount(14));
            },

            ( /* 0xa4: movsb */ ) -> {

                return new MOVSB(m_cpu,
                                 getDefaultSegment(m_cpu.DS),
                                 getCycleCount(isREP() ? 17 : 18));
            },

            ( /* 0xa5: movsw */ ) -> {

                return new MOVSW(m_cpu,
                                 getDefaultSegment(m_cpu.DS),
                                 getCycleCount(isREP() ? 17 : 18));
            },

            ( /* 0xa6: cmpsb */ ) -> {

                return new CMPSB(m_cpu,
                                 getDefaultSegment(m_cpu.DS),
                                 getCycleCount(30));
            },

            ( /* 0xa7: cmpsw */ ) -> {

                return new CMPSW(m_cpu,
                                 getDefaultSegment(m_cpu.DS),
                                 getCycleCount(30));
            },

            ( /* 0xa8: test al, imm8 */ ) -> {

                return new TEST8(m_cpu,
                                 buildAL(),
                                 buildIMM8(),
                                 getCycleCount(5));
            },

            ( /* 0xa9: test ax, imm16 */ ) -> {

                return new TEST16(m_cpu,
                                  buildAX(),
                                  buildIMM16(),
                                  getCycleCount(5));
            },

            ( /* 0xaa: stosb */ ) -> {

                return new STOSB(m_cpu,
                                 getCycleCount(isREP() ? 10 : 11));
            },

            ( /* 0xab: stosw */ ) -> {

                return new STOSW(m_cpu,
                                 getCycleCount(isREP() ? 10 : 11));
            },

            ( /* 0xac: lodsb */ ) -> {

                return new LODSB(m_cpu,
                                 getDefaultSegment(m_cpu.DS),
                                 getCycleCount(isREP() ? 4 : 16));
            },

            ( /* 0xad: lodsw */ ) -> {

                return new LODSW(m_cpu,
                                 getDefaultSegment(m_cpu.DS),
                                 getCycleCount(isREP() ? 4 : 16));
            },

            ( /* 0xae: scasb */ ) -> {

                return new SCASB(m_cpu,
                                 getCycleCount(isREP() ? 15 : 19));
            },

            ( /* 0xaf: scasw */ ) -> {

                return new SCASW(m_cpu,
                                 getCycleCount(isREP() ? 15 : 19));
            },

            ( /* 0xb0: mov al, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildAL(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0xb1: mov cl, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildCL(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0xb2: mov dl, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildDL(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0xb3: mov bl, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildBL(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0xb4: mov ah, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildAH(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0xb5: mov ch, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildCH(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0xb6: mov dh, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildDH(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0xb7: mov bh, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildBH(),
                               buildIMM8(),
                               getCycleCount(4));
            },

            ( /* 0xb8: mov ax, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildAX(),
                               buildIMM16(),
                               getCycleCount(4));
            },

            ( /* 0xb9: mov cx, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildCX(),
                               buildIMM16(),
                               getCycleCount(4));
            },

            ( /* 0xba: mov dx, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildDX(),
                               buildIMM16(),
                               getCycleCount(4));
            },

            ( /* 0xbb: mov bx, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildBX(),
                               buildIMM16(),
                               getCycleCount(4));
            },

            ( /* 0xbc: mov sp, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildSP(),
                               buildIMM16(),
                               getCycleCount(4));
            },

            ( /* 0xbd: mov bp, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildBP(),
                               buildIMM16(),
                               getCycleCount(4));
            },

            ( /* 0xbe: mov si, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildSI(),
                               buildIMM16(),
                               getCycleCount(4));
            },

            ( /* 0xbf: mov di, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildDI(),
                               buildIMM16(),
                               getCycleCount(4));
            },

            ( /* 0xc0: ret imm16 (alias) */ ) -> {

                m_isRunning = false;
                
                return new RET_POP(m_cpu,
                                   buildIMM16(),
                                   getCycleCount(24));
            },

            ( /* 0xc1: ret (alias) */ ) -> {

                m_isRunning = false;
                
                return new RET(m_cpu,
                               getCycleCount(20));
            },

            ( /* 0xc2: ret imm16 */ ) -> {

                m_isRunning = false;
                
                return new RET_POP(m_cpu,
                                   buildIMM16(),
                                   getCycleCount(24));
            },

            ( /* 0xc3: ret */ ) -> {

                m_isRunning = false;
                
                return new RET(m_cpu,
                               getCycleCount(20));
            },

            ( /* 0xc4: les r16, m16 */ ) -> {
                
                return new LSEG(m_cpu,
                                buildES(),
                                buildREG16(),
                                buildM16(0),
                                buildM16(2),
                                getCycleCount(24));
            },

            ( /* 0xc5: lds r16, m16 */ ) -> {

                return new LSEG(m_cpu,
                                buildDS(),
                                buildREG16(),
                                buildM16(0),
                                buildM16(2),
                                getCycleCount(24));
            },

            ( /* 0xc6: mov rm8, imm8 */ ) -> {

                return new MOV(m_cpu,
                               buildRM8(),
                               buildIMM8(),
                               getCycleCount(isMOD3() ? 4 : 14));
            },

            ( /* 0xc7: mov rm16, imm16 */ ) -> {

                return new MOV(m_cpu,
                               buildRM16(),
                               buildIMM16(),
                               getCycleCount(isMOD3() ? 4 : 14));
            },

            ( /* 0xc8: retf imm16 (alias) */ ) -> {

                m_isRunning = false;

                return new RET_FAR_POP(m_cpu,
                                       buildIMM16(),
                                       getCycleCount(33));
            },

            ( /* 0xc9: retf (alias) */ ) -> {

                m_isRunning = false;
                
                return new RET_FAR(m_cpu,
                                   getCycleCount(34));
            },

            ( /* 0xca: retf imm16 */ ) -> {
                
                m_isRunning = false;

                return new RET_FAR_POP(m_cpu,
                                       buildIMM16(),
                                       getCycleCount(33));
            },

            ( /* 0xcb: retf */ ) -> {

                m_isRunning = false;
                
                return new RET_FAR(m_cpu,
                                   getCycleCount(34));
            },

            ( /* 0xcc: int 3 */ ) -> {

                m_isRunning = false;
                
                return new INT(m_cpu,
                               buildImmediate(3),
                               true,
                               getCycleCount(72));
            },

            ( /* 0xcd: int imm8 */ ) -> {

                m_isRunning = false;
                
                return new INT(m_cpu,
                               buildIMM8(),
                               false,
                               getCycleCount(71));
            },

            ( /* 0xce: into */ ) -> {

                m_isRunning = false;
                
                return new INTO(m_cpu,
                                getCycleCount(53),
                                getCycleCount(4));
            },

            ( /* 0xcf: iret */ ) -> {

                m_isRunning = false;
                
                return new IRET(m_cpu,
                                getCycleCount(44));
            },

            ( /* 0xd0: grp2 rm8, 1 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* rol rm8, 1 */
                    case 0: return new ROL8(m_cpu,
                                            buildRM8(),
                                            buildImmediate(1),
                                            getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* ror rm8, 1 */
                    case 1: return new ROR8(m_cpu,
                                            buildRM8(),
                                            buildImmediate(1),
                                            getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* rcl rm8, 1 */
                    case 2: return new RCL8(m_cpu,
                                            buildRM8(),
                                            buildImmediate(1),
                                            getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* rcr rm8, 1 */
                    case 3: return new RCR8(m_cpu,
                                            buildRM8(),
                                            buildImmediate(1),
                                            getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* shl rm8, 1 */
                    case 4: return new SHL8(m_cpu,
                                            buildRM8(),
                                            buildImmediate(1),
                                            getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* shr rm8, 1 */
                    case 5: return new SHR8(m_cpu,
                                            buildRM8(),
                                            buildImmediate(1),
                                            getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* sal rm8, 1 */
                    case 6: return new SHL8(m_cpu,
                                            buildRM8(),
                                            buildImmediate(1),
                                            getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* sar rm8, 1 */
                    case 7: return new SAR8(m_cpu,
                                            buildRM8(),
                                            buildImmediate(1),
                                            getCycleCount(isMOD3() ? 2 : 23));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP2 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0xd1: grp2 rm16, 1 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* rol rm16, 1 */
                    case 0: return new ROL16(m_cpu,
                                             buildRM16(),
                                             buildImmediate(1),
                                             getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* ror rm16, 1 */
                    case 1: return new ROR16(m_cpu,
                                             buildRM16(),
                                             buildImmediate(1),
                                             getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* rcl rm16, 1 */
                    case 2: return new RCL16(m_cpu,
                                             buildRM16(),
                                             buildImmediate(1),
                                             getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* rcr rm16, 1 */
                    case 3: return new RCR16(m_cpu,
                                             buildRM16(),
                                             buildImmediate(1),
                                             getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* shl rm16, 1 */
                    case 4: return new SHL16(m_cpu,
                                             buildRM16(),
                                             buildImmediate(1),
                                             getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* shr rm16, 1 */
                    case 5: return new SHR16(m_cpu,
                                             buildRM16(),
                                             buildImmediate(1),
                                             getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* sal rm16, 1 */
                    case 6: return new SHL16(m_cpu,
                                             buildRM16(),
                                             buildImmediate(1),
                                             getCycleCount(isMOD3() ? 2 : 23));
                        
                    /* sar rm16, 1 */
                    case 7: return new SAR16(m_cpu,
                                             buildRM16(),
                                             buildImmediate(1),
                                             getCycleCount(isMOD3() ? 2 : 23));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP2 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0xd2: grp2 rm8, cl */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* rol rm8, cl */
                    case 0: return new ROL8(m_cpu,
                                            buildRM8(),
                                            buildCL(),
                                            getCycleCount(isMOD3() ? 8 : 28));
                    
                    /* ror rm8, cl */
                    case 1: return new ROR8(m_cpu,
                                            buildRM8(),
                                            buildCL(),
                                            getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* rcl rm8, cl */
                    case 2: return new RCL8(m_cpu,
                                            buildRM8(),
                                            buildCL(),
                                            getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* rcr rm8, cl */
                    case 3: return new RCR8(m_cpu,
                                            buildRM8(),
                                            buildCL(),
                                            getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* shl rm8, cl */
                    case 4: return new SHL8(m_cpu,
                                            buildRM8(),
                                            buildCL(),
                                            getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* shr rm8, cl */
                    case 5: return new SHR8(m_cpu,
                                            buildRM8(),
                                            buildCL(),
                                            getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* sal rm8, cl */
                    case 6: return new SHL8(m_cpu,
                                            buildRM8(),
                                            buildCL(),
                                            getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* sar rm8, cl */
                    case 7: return new SAR8(m_cpu,
                                            buildRM8(),
                                            buildCL(),
                                            getCycleCount(isMOD3() ? 8 : 28));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP2 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0xd3: grp2 rm16, cl */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* rol rm16, cl */
                    case 0: return new ROL16(m_cpu,
                                             buildRM16(),
                                             buildCL(),
                                             getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* ror rm16, cl */
                    case 1: return new ROR16(m_cpu,
                                             buildRM16(),
                                             buildCL(),
                                             getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* rcl rm16, cl */
                    case 2: return new RCL16(m_cpu,
                                             buildRM16(),
                                             buildCL(),
                                             getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* rcr rm16, cl */
                    case 3: return new RCR16(m_cpu,
                                             buildRM16(),
                                             buildCL(),
                                             getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* shl rm16, cl */
                    case 4: return new SHL16(m_cpu,
                                             buildRM16(),
                                             buildCL(),
                                             getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* shr rm16, cl */
                    case 5: return new SHR16(m_cpu,
                                             buildRM16(),
                                             buildCL(),
                                             getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* sal rm16, cl */
                    case 6: return new SHL16(m_cpu,
                                             buildRM16(),
                                             buildCL(),
                                             getCycleCount(isMOD3() ? 8 : 28));
                        
                    /* sar rm16, cl */
                    case 7: return new SAR16(m_cpu,
                                             buildRM16(),
                                             buildCL(),
                                             getCycleCount(isMOD3() ? 8 : 28));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP2 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0xd4: AAM */ ) -> {

                return new AAM(m_cpu,
                               buildIMM8(),
                               getCycleCount(83));
            },

            ( /* 0xd5: AAD */ ) -> {

                return new AAD(m_cpu,
                               buildIMM8(),
                               getCycleCount(60));
            },

            ( /* 0xd6: salc */ ) -> {

                return new SALC(m_cpu,
                                getCycleCount(4));
            },

            ( /* 0xd7: xlat */ ) -> {

                return new XLAT(m_cpu,
                                getDefaultSegment(m_cpu.DS),
                                getCycleCount(11));
            },

            ( /* 0xd8: fpu */ ) -> {
                
                decodeMODRM();
                
                /* TODO */
                return new NOP(m_cpu,
                               getCycleCount(4));
            },

            ( /* 0xd9: fpu */ ) -> {

                decodeMODRM();
                
                /* TODO */
                return new NOP(m_cpu,
                               getCycleCount(4));
            },

            ( /* 0xda: fpu */ ) -> {

                decodeMODRM();
                
                /* TODO */
                return new NOP(m_cpu,
                               getCycleCount(4));
            },

            ( /* 0xdb: fpu */ ) -> {

                decodeMODRM();
                
                /* TODO */
                return new NOP(m_cpu,
                               getCycleCount(4));
            },

            ( /* 0xdc: fpu */ ) -> {

                decodeMODRM();
                
                /* TODO */
                return new NOP(m_cpu,
                               getCycleCount(4));
            },

            ( /* 0xdd: fpu */ ) -> {

                decodeMODRM();
                
                /* TODO */
                return new NOP(m_cpu,
                               getCycleCount(4));
            },

            ( /* 0xde: fpu */ ) -> {

                decodeMODRM();
                
                /* TODO */
                return new NOP(m_cpu,
                               getCycleCount(4));
            },

            ( /* 0xdf: fpu */ ) -> {

                decodeMODRM();
                
                /* TODO */
                return new NOP(m_cpu,
                               getCycleCount(4));
            },

            ( /* 0xe0: loopnz rel8 */ ) -> {

                m_isRunning = false;
                
                return new LOOPNZ(m_cpu,
                                  buildIPRelativeIMM8(),
                                  getCycleCount(18),
                                  getCycleCount(6));
            },

            ( /* 0xe1: loopz rel8 */ ) -> {

                m_isRunning = false;
                
                return new LOOPZ(m_cpu,
                                 buildIPRelativeIMM8(),
                                 getCycleCount(18),
                                 getCycleCount(6));
            },

            ( /* 0xe2: loop rel8 */ ) -> {

                m_isRunning = false;
                
                return new LOOP(m_cpu,
                                buildIPRelativeIMM8(),
                                getCycleCount(17),
                                getCycleCount(5));
            },

            ( /* 0xe3: jcxz rel8 */ ) -> {

                m_isRunning = false;
                
                return new JMP_CONDITIONAL(m_cpu,
                                           buildIPRelativeIMM8(),
                                           new ConditionCXZero(m_cpu),
                                           getCycleCount(18),
                                           getCycleCount(6));
            },

            ( /* 0xe4: in al, imm8 */ ) -> {

                return new IN8(m_cpu,
                               buildIMM8(),
                               getCycleCount(14));
            },

            ( /* 0xe5: in ax, imm8 */ ) -> {

                return new IN16(m_cpu,
                                buildIMM8(),
                                getCycleCount(14));
            },

            ( /* 0xe6: out imm8, al */ ) -> {

                return new OUT8(m_cpu,
                                buildIMM8(),
                                getCycleCount(14));
            },

            ( /* 0xe7: out imm8, ax */ ) -> {

                return new OUT16(m_cpu,
                                 buildIMM8(),
                                 getCycleCount(14));
            },

            ( /* 0xe8: call rel16 */ ) -> {
                
                m_isRunning = false;
                
                return new CALL_NEAR(m_cpu,
                                     buildIPRelativeIMM16(),
                                     getCycleCount(23));
            },

            ( /* 0xe9: jmp rel16 */ ) -> {

                m_isRunning = false;
                
                return new JMP(m_cpu,
                               buildIPRelativeIMM16(),
                               getCycleCount(15));
            },

            ( /* 0xea: jmp imm16:16 */ ) -> {
                
                m_isRunning = false;
                
                return new JMP_FAR(m_cpu,
                                   buildIMM16(),
                                   buildIMM16(),
                                   getCycleCount(15));
            },

            ( /* 0xeb: jmp rel8 */ ) -> {

                m_isRunning = false;
                
                return new JMP(m_cpu,
                               buildIPRelativeIMM8(),
                               getCycleCount(15));
            },

            ( /* 0xec: in al, dx */ ) -> {

                return new IN8(m_cpu,
                               buildDX(),
                               getCycleCount(12));
            },

            ( /* 0xed: in ax, dx */ ) -> {

                return new IN16(m_cpu,
                                buildDX(),
                                getCycleCount(12));
            },

            ( /* 0xee: out dx, al */ ) -> {

                return new OUT8(m_cpu,
                                buildDX(),
                                getCycleCount(12));
            },

            ( /* 0xef: out dx, ax */ ) -> {

                return new OUT16(m_cpu,
                                 buildDX(),
                                 getCycleCount(12));
            },

            ( /* 0xf0: lock */ ) -> {

                m_lock = true;
                
                return null;
            },

            ( /* 0xf1: lock (alias) */ ) -> {

                m_lock = true;
                
                return null;
            },

            ( /* 0xf2: repnz */ ) -> {

                m_repZ = false;
                m_repNZ = true;
                
                return null;
            },

            ( /* 0xf3: repz */ ) -> {
                
                m_repNZ = false;
                m_repZ = true;
                
                return null;
            },

            ( /* 0xf4: halt */ ) -> {

                m_isRunning = false;
                
                return new HALT(m_cpu,
                                getCycleCount(2));
            },

            ( /* 0xf5: cmc */ ) -> {

                return new CMC(m_cpu,
                               getCycleCount(2));
            },

            ( /* 0xf6: GRP3a rm8 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* test rm8, imm8 */
                    case 0:
                    case 1: return new TEST8(m_cpu,
                                             buildRM8(),
                                             buildIMM8(),
                                             getCycleCount(isMOD3() ? 5 : 11));
                    
                    /* not rm8 */
                    case 2: return new NOT8(m_cpu,
                                            buildRM8(),
                                            getCycleCount(isMOD3() ? 3 : 24));
                        
                    /* neg rm8 */
                    case 3: return new NEG8(m_cpu,
                                            buildRM8(),
                                            getCycleCount(isMOD3() ? 3 : 24));
                        
                    /* mul al, rm8 */
                    case 4: return new MUL8(m_cpu,
                                            buildRM8(),
                                            getCycleCount(70));
                        
                    /* imul al, rm8 */
                    case 5: return new IMUL8(m_cpu,
                                             buildRM8(),
                                             getCycleCount(80));
                        
                    /* div al, rm8 */
                    case 6: return new DIV8(m_cpu,
                                            buildRM8(),
                                            getCycleCount(80));
                        
                    /* idiv al, rm8 */
                    case 7: return new IDIV8(m_cpu,
                                             buildRM8(),
                                             getCycleCount(101));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP3a opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0xf7: GRP3b rm16 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* test rm16, imm16 */
                    case 0:
                    case 1: return new TEST16(m_cpu,
                                              buildRM16(),
                                              buildIMM16(),
                                              getCycleCount(isMOD3() ? 5 : 11));
                    
                    /* not rm16 */
                    case 2: return new NOT16(m_cpu,
                                             buildRM16(),
                                             getCycleCount(isMOD3() ? 3 : 24));
                        
                    /* neg rm16 */
                    case 3: return new NEG16(m_cpu,
                                             buildRM16(),
                                             getCycleCount(isMOD3() ? 3 : 24));
                        
                    /* mul al, rm16 */
                    case 4: return new MUL16(m_cpu,
                                             buildRM16(),
                                             getCycleCount(118));
                        
                    /* imul al, rm16 */
                    case 5: return new IMUL16(m_cpu,
                                              buildRM16(),
                                              getCycleCount(128));
                        
                    /* div al, rm16 */
                    case 6: return new DIV16(m_cpu,
                                             buildRM16(),
                                             getCycleCount(114));
                        
                    /* idiv al, rm16 */
                    case 7: return new IDIV16(m_cpu,
                                              buildRM16(),
                                              getCycleCount(165));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP3b opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0xf8: clc */ ) -> {

                return new CLC(m_cpu,
                               getCycleCount(2));
            },

            ( /* 0xf9: stc */ ) -> {

                return new STC(m_cpu,
                               getCycleCount(2));
            },

            ( /* 0xfa: cli */ ) -> {

                return new CLI(m_cpu,
                               getCycleCount(2));
            },

            ( /* 0xfb: sti */ ) -> {

                return new STI(m_cpu,
                               getCycleCount(2));
            },

            ( /* 0xfc: cld */ ) -> {

                return new CLD(m_cpu,
                               getCycleCount(2));
            },

            ( /* 0xfd: std */ ) -> {

                return new STD(m_cpu,
                               getCycleCount(2));
            },

            ( /* 0xfe: grp4 rm8 */ ) -> {

                decodeMODRM();

                switch(m_reg) {

                    /* inc rm8 */
                    case 0: return new INC8(m_cpu,
                                            buildRM8(),
                                            getCycleCount(isMOD3() ? 3 : 23));

                    /* dec rm8 */
                    case 1: return new DEC8(m_cpu,
                                            buildRM8(),
                                            getCycleCount(isMOD3() ? 3 : 23));

                    default:
                        throw new DecoderException(String.format("Illegal GRP4 opcode (reg = 0x%02x)", m_reg));
                }
            },

            ( /* 0xff: grp5 rm16 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* inc rm16 */
                    case 0: return new INC16(m_cpu,
                                             buildRM16(),
                                             getCycleCount(isMOD3() ? 3 : 23));
                        
                    /* dec rm16 */
                    case 1: return new DEC16(m_cpu,
                                             buildRM16(),
                                             getCycleCount(isMOD3() ? 3 : 23));
                        
                    /* call rm16 */
                    case 2:
                        m_isRunning = false;
                        return new CALL_NEAR(m_cpu,
                                             buildRM16(),
                                             getCycleCount(isMOD3() ? 20 : 29));
                        
                    /* call m16:16 */
                    case 3:
                        m_isRunning = false;
                        return new CALL_FAR(m_cpu,
                                            buildM16(0),
                                            buildM16(2),
                                            getCycleCount(53));
                        
                    /* jmp rm16 */
                    case 4:
                        m_isRunning = false;
                        return new JMP(m_cpu,
                                       buildRM16(),
                                       getCycleCount(isMOD3() ? 11 : 18));
                        
                    /* jmp m16:16 */
                    case 5:
                        m_isRunning = false;
                        return new JMP_FAR(m_cpu,
                                           buildM16(0),
                                           buildM16(2),
                                           getCycleCount(24));
                        
                    /* push rm16 */
                    case 6:
                        return new PUSH(m_cpu,
                                        buildRM16(),
                                        getCycleCount(isMOD3() ? 15 : 24));
                        
                    default:
                        throw new DecoderException(String.format("Illegal GRP5 opcode (reg = 0x%02x)", m_reg));
                }
            }
        };
        
        // </editor-fold>
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Some helper methods">
    
    private boolean isMOD3() {
        
        return m_mod == 0x03;
    }
    
    private boolean isREP() {
        
        return m_repZ || m_repNZ;
    }
    
    private int getCycleCount(int cycles) {
        
        if(m_segOverride != null)
            cycles += 2;
        
        return Scheduler.toFixedPoint(cycles);
    }
    
    private Segment getDefaultSegment(Segment defaultSegment) {
        
        if(m_segOverride == null)
            return defaultSegment;
        
        return m_segOverride;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Decoding">
    // <editor-fold defaultstate="collapsed" desc="Reading of data at the instruction pointer">
    
    private int readIMM8() {
        
        int data = m_cpu.readMEM8(m_base, m_offset);
        
        m_offset += 1;
        m_offset &= 0xffff;
        
        return data;
    }
    
    private int readIMM16() {
        
        int data = m_cpu.readMEM16(m_base, m_offset);
        
        m_offset += 2;
        m_offset &= 0xffff;
        
        return data;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Decoding of a codeblock at a given start address">
    
    public CodeBlock decodeCodeBlock(int base, int offset) {
        
        Instruction instr;
        int instrOffset;
        
        // Initialize a new code block
        m_base = base;
        m_offset = offset;
        m_buffer.clear();
        
        
        //
        // Decode instructions
        //
        m_isRunning = true;

        while(m_isRunning) {

            m_hasReadModRM = false;
            m_segOverride = null;
            m_repZ = false;
            m_repNZ = false;
            m_lock = false;
            m_wait = false;

            instrOffset = m_offset;
            instr = null;
            
            while(instr == null)
                instr = m_decoderTable[readIMM8()].decode();
            
            
            // Repeat prefix?
            if(m_repZ || m_repNZ) {
                
                if(instr instanceof MOVSB || instr instanceof MOVSW ||
                   instr instanceof STOSB || instr instanceof STOSW ||
                   instr instanceof LODSB || instr instanceof LODSW) {

                    instr = new REP(m_cpu, instr, getCycleCount(2));
                }
                else if(instr instanceof CMPSB || instr instanceof CMPSW ||
                        instr instanceof SCASB || instr instanceof SCASW) {
                    
                    if(m_repZ)
                        instr = new REPZ(m_cpu, instr, getCycleCount(2));
                    else
                        instr = new REPNZ(m_cpu, instr, getCycleCount(2));
                }
            }
            
            // Lock prefix?
            if(m_lock)
                instr = new LOCK(m_cpu, instr, getCycleCount(4));
            
            // Wait prefix?
            if(m_wait)
                instr = new WAIT(m_cpu, instr, getCycleCount(3));

            instr.setIP(instrOffset, m_offset);
            m_buffer.add(instr);
            
            if(m_buffer.size() >= 4096)
                throw new DecoderException("Decoder buffer overrun. This is most likely caused by trying to run code that was not made for this cpu.");
        }
        
        return new CodeBlock(m_cpu,
                             base,
                             offset,
                             m_offset,
                             m_buffer.toArray(new Instruction[m_buffer.size()]));
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Decoding of the MOD R/M byte">
    
    private void decodeMODRM() {
        
        if(!m_hasReadModRM) {
            
            m_hasReadModRM = true;
            
            // Read mod r/m byte
            int data = readIMM8();
            m_mod = (data >> 6) & 0x03;
            m_reg = (data >> 3) & 0x07;
            m_rm = data & 0x07;
            
            /* Determine displacement */
            switch(m_mod) {

                case 0:
                    m_displacement = m_rm == 6 ? readIMM16() : 0;
                    break;

                case 1:
                    m_displacement = signExtend8To16(readIMM8());
                    break;

                case 2:
                    m_displacement = readIMM16();
                    break;
            }   
        }
    }
    
    // </editor-fold>
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Builder">
    // <editor-fold defaultstate="collapsed" desc="Pointer builder">
    
    private Pointer buildPointer(int address) {
        
        return new PointerDisplacement(address);
    }
    
    private Pointer buildPointer(Reg16 baseReg, int displacement) {
        
        if(displacement == 0)
            return new PointerBase(baseReg);
        else
            return new PointerBaseDisplacement(baseReg, displacement);
    }
    
    private Pointer buildPointer(Reg16 baseReg,
                                 Reg16 indexReg,
                                 int displacement) {
        
        if(displacement == 0)
            return new PointerBaseIndex(baseReg, indexReg);
        else
            return new PointerBaseIndexDisplacement(baseReg, indexReg, displacement);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Immediate operand builder">
    
    private Operand buildImmediate(int value) {
        
        return new OperandImmediate(value);
    }
    
    private Operand buildIMM8() {
        
        return buildImmediate(readIMM8());
    }
    
    private Operand buildSIMM8() {
        
        return buildImmediate(signExtend8To16(readIMM8()));
    }
    
    private Operand buildIMM16() {
        
        return buildImmediate(readIMM16());
    }
    
    private Operand buildIPRelative(int displacement) {
        
        return buildImmediate((m_offset + displacement) & 0xffff);
    }
    
    private Operand buildIPRelativeIMM8() {
        
        return buildIPRelative(signExtend8To16(readIMM8()));
    }
    
    private Operand buildIPRelativeIMM16() {
        
        return buildIPRelative(readIMM16());
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Register operand builder">
    
    private Operand buildREG(boolean isWord, int regIndex) {
        
        if(isWord)
            return new OperandRegister16(m_regs16[regIndex]);
        else
            return new OperandRegister8(m_regs8[regIndex]);
    }
    
    private Operand buildAH() {
        
        return new OperandRegister8(m_cpu.AH);
    }
    
    private Operand buildBH() {
        
        return new OperandRegister8(m_cpu.BH);
    }
    
    private Operand buildCH() {
        
        return new OperandRegister8(m_cpu.CH);
    }
    
    private Operand buildDH() {
        
        return new OperandRegister8(m_cpu.DH);
    }
    
    private Operand buildAL() {
        
        return new OperandRegister8(m_cpu.AL);
    }
    
    private Operand buildBL() {
        
        return new OperandRegister8(m_cpu.BL);
    }
    
    private Operand buildCL() {
        
        return new OperandRegister8(m_cpu.CL);
    }
    
    private Operand buildDL() {
        
        return new OperandRegister8(m_cpu.DL);
    }
    
    private Operand buildAX() {
        
        return new OperandRegister16(m_cpu.AX);
    }
    
    private Operand buildBX() {
        
        return new OperandRegister16(m_cpu.BX);
    }
    
    private Operand buildCX() {
        
        return new OperandRegister16(m_cpu.CX);
    }
    
    private Operand buildDX() {
        
        return new OperandRegister16(m_cpu.DX);
    }
    
    private Operand buildSP() {
        
        return new OperandRegister16(m_cpu.SP);
    }
    
    private Operand buildBP() {
        
        return new OperandRegister16(m_cpu.BP);
    }
    
    private Operand buildSI() {
        
        return new OperandRegister16(m_cpu.SI);
    }
    
    private Operand buildDI() {
        
        return new OperandRegister16(m_cpu.DI);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Register / memory operand builder">
    
    private Operand buildREG8() {
        
        decodeMODRM();
        
        return buildREG(false, m_reg);
    }
    
    private Operand buildREG16() {
        
        decodeMODRM();
        
        return buildREG(true, m_reg);
    }
    
    private Operand buildRM8() {
        
        decodeMODRM();
        
        return buildRM(false);
    }
    
    private Operand buildRM16() {
        
        decodeMODRM();
        
        return buildRM(true);
    }
    
    private Operand buildRM(boolean isWord) {
        
        if(m_mod == 3)
            return buildREG(isWord, m_rm);
        else
            return buildMEM(isWord, false, 0);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Memory operand builder">
    
    private Operand buildMEM(boolean isWord, boolean addressOnly, int extraDisplacement) {
        
        Segment segment;
        Pointer pointer;
        
        int displacement = m_displacement + extraDisplacement;
        
        if(m_mod == 0 && m_rm == 6) {

            segment = getDefaultSegment(m_cpu.DS);
            pointer = buildPointer(displacement);
        }
        else {
            
            segment = getDefaultSegment(m_segsMemPtr[m_rm]);
            switch(m_rm) {
                
                case 0: pointer = buildPointer(m_cpu.BX, m_cpu.SI, displacement); break;
                case 1: pointer = buildPointer(m_cpu.BX, m_cpu.DI, displacement); break;
                case 2: pointer = buildPointer(m_cpu.BP, m_cpu.SI, displacement); break;
                case 3: pointer = buildPointer(m_cpu.BP, m_cpu.DI, displacement); break;
                case 4: pointer = buildPointer(m_cpu.SI, displacement); break;
                case 5: pointer = buildPointer(m_cpu.DI, displacement); break;
                case 6: pointer = buildPointer(m_cpu.BP, displacement); break;
                case 7: pointer = buildPointer(m_cpu.BX, displacement); break;

                default:
                    throw new DecoderException(String.format("Illegal mod/rm byte (mod = 0x%02x, reg = 0x%02x, rm = 0x%02x)", m_mod, m_reg, m_rm));
            }
        }
        
        Operand offset = new OperandAddress(pointer);
        
        if(addressOnly)
            return offset;
        
        if(isWord)
            return new OperandMemory16(m_cpu, segment, offset);
        else
            return new OperandMemory8(m_cpu, segment, offset);
    }
    
    
    
    private Operand buildAddress() {
        
        decodeMODRM();
        
        return buildMEM(true, true, 0);
    }
    
    
    
    private Operand buildM16(int displacement) {
        
        decodeMODRM();
        
        return buildMEM(true, false, displacement);
    }
    
    
    
    private Operand buildMOFFS8() {
        
        return new OperandMemory8(m_cpu,
                                  getDefaultSegment(m_cpu.DS),
                                  buildIMM16());
    }
    
    private Operand buildMOFFS16() {
        
        return new OperandMemory16(m_cpu,
                                   getDefaultSegment(m_cpu.DS),
                                   buildIMM16());
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Segment operand builder">
    // <editor-fold defaultstate="collapsed" desc="Based on the MOD r/m byte">
    
    private Operand buildSEGREG() {
        
        decodeMODRM();
        
        return new OperandSegment(m_segs[m_reg & 0x03]);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Direct">
    
    private Operand buildCS() {
        
        return new OperandSegment(m_cpu.CS);
    }
    
    private Operand buildDS() {
        
        return new OperandSegment(m_cpu.DS);
    }
    
    private Operand buildES() {
        
        return new OperandSegment(m_cpu.ES);
    }
    
    private Operand buildSS() {
        
        return new OperandSegment(m_cpu.SS);
    
    }
    // </editor-fold>
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Flag operand builder">
    
    private Operand buildFlags() {
        
        return new OperandFlags(m_cpu.FLAGS);
    }
    
    // </editor-fold>
    // </editor-fold>
}
