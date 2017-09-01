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
package Hardware.CPU.Intel80386.Decoder;

import Hardware.CPU.Intel80386.Instructions.i386.Arithmetic.*;
import Hardware.CPU.Intel80386.Instructions.i386.Bit.*;
import Hardware.CPU.Intel80386.Instructions.i386.Datatransfer.*;
import Hardware.CPU.Intel80386.Instructions.i386.Flags.*;
import Hardware.CPU.Intel80386.Instructions.i386.IO.*;
import Hardware.CPU.Intel80386.Instructions.i386.Logical.*;
import Hardware.CPU.Intel80386.Instructions.i386.Misc.*;
import Hardware.CPU.Intel80386.Instructions.i386.Programflow.*;
import Hardware.CPU.Intel80386.Instructions.i386.Repeats.*;
import Hardware.CPU.Intel80386.Instructions.i386.Shifts.*;
import Hardware.CPU.Intel80386.Instructions.i386.Stack.*;
import Hardware.CPU.Intel80386.Instructions.i386.String.*;
import Hardware.CPU.Intel80386.Codeblock.CodeBlock;
import Hardware.CPU.Intel80386.Condition.Condition;
import Hardware.CPU.Intel80386.Condition.Conditions.*;
import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.MMU.MMU;
import Hardware.CPU.Intel80386.Operands.Immediate.OperandImmediate;
import Hardware.CPU.Intel80386.Operands.Memory.OperandMemory;
import Hardware.CPU.Intel80386.Pointer.Pointer;
import Hardware.CPU.Intel80386.Register.General.*;
import Hardware.CPU.Intel80386.Register.General.Register;
import Hardware.CPU.Intel80386.Register.Segments.Segment;
import Hardware.CPU.Intel80386.Operands.Memory.*;
import Hardware.CPU.Intel80386.Operands.Operand;
import Hardware.CPU.Intel80386.Operands.Register.*;
import Hardware.CPU.Intel80386.Operands.Segment.*;
import Hardware.CPU.Intel80386.Pointer.Size16.*;
import Hardware.CPU.Intel80386.Pointer.Size32.*;
import static Utility.SignExtension.signExtend8To16;
import static Utility.SignExtension.signExtend8To32;
import java.util.ArrayList;



public final class Decoder {
    
    /* ----------------------------------------------------- *
     * Decoder page crossing exception                       *
     * ----------------------------------------------------- */
    private class DecoderPageCrossException extends RuntimeException {
    }
    private final DecoderPageCrossException PAGECROSS_EXCEPTION;
    
    /* ----------------------------------------------------- *
     * Decoder table                                         *
     * ----------------------------------------------------- */
    private interface InstructionDecoder {
        
        Instruction decode();
    }
    private final InstructionDecoder[] m_decoderTable;
    private final InstructionDecoder[] m_decoderTable0f;
    private InstructionDecoder[] m_decoderTableCurrent;
    
    /* ----------------------------------------------------- *
     * MOD/RM / SIB byte decoding                            *
     * ----------------------------------------------------- */
    private int m_mod;
    private int m_reg;
    private int m_rm;
    private int m_scale;
    private int m_base;
    private int m_index;
    private boolean m_hasReadModRM;
    
    /* ----------------------------------------------------- *
     * Decoded MOD/RM / SIB byte                             *
     * ----------------------------------------------------- */
    private Register m_addrBase;
    private Register m_addrIdx;
    private Segment m_addrSeg;
    private int m_addrDisp;
    
    /* ----------------------------------------------------- *
     * Registers and segments                                *
     * ----------------------------------------------------- */
    private final int SEGMENT_ES = 0;
    private final int SEGMENT_CS = 1;
    private final int SEGMENT_SS = 2;
    private final int SEGMENT_DS = 3;
    private final int SEGMENT_FS = 4;
    private final int SEGMENT_GS = 5;
    private final Reg16[] m_addrBaseRegs16;
    private final Reg16[] m_addrIndexRegs16;
    private final Reg32[] m_addrBaseIndexRegs32;
    private final Segment[] m_addrSegs16;
    private final Segment[] m_addrSegs32;
    private final Reg8[] m_reg8;
    private final Reg16[] m_reg16;
    private final Reg32[] m_reg32;
            
    /* ----------------------------------------------------- *
     * Segment overrides                                     *
     * ----------------------------------------------------- */
    private Segment m_prefixSegmentOverride;
    
    /* ----------------------------------------------------- *
     * Repeat prefix                                         *
     * ----------------------------------------------------- */
    private boolean m_prefixRepZ;
    private boolean m_prefixRepNZ;
    private boolean m_prefixOperandSizeOverride;
    private boolean m_prefixAddressSizeOverride;
    private boolean m_prefixLock;
    private boolean m_prefixWait;
    
    /* ----------------------------------------------------- *
     * Decoder running                                       *
     * ----------------------------------------------------- */
    private boolean m_decoderRunning;
    private int m_decoderPage;
    private int m_decoderOffset;
    private final ArrayList<Instruction> m_buffer;
    
    /* ----------------------------------------------------- *
     * Decoder running                                       *
     * ----------------------------------------------------- */
    private final Intel80386 m_cpu;
    private final MMU m_mmu;
    
    
    
    public Decoder(Intel80386 cpu, MMU mmu) {
        
        m_cpu = cpu;
        m_mmu = mmu;
        
        m_buffer = new ArrayList<>();
        PAGECROSS_EXCEPTION = new DecoderPageCrossException();
        
        // Initialize segment register references for 16 bit memory pointers
        m_addrSegs16 = new Segment[] {

            cpu.DS, cpu.DS,
            cpu.SS, cpu.SS,
            cpu.DS, cpu.DS,
            cpu.SS, cpu.DS
        };
        
        // Initialize segment register references for 32 bit memory pointers
        m_addrSegs32 = new Segment[] {
        
            cpu.DS, cpu.DS,
            cpu.DS, cpu.DS,
            cpu.SS, cpu.SS,
            cpu.DS, cpu.DS,
        };
        
        // Initialize base and index register references for 16 bit memory pointers
        m_addrBaseRegs16 = new Reg16[] {
            
            cpu.BX, cpu.BX,
            cpu.BP, cpu.BP,
            cpu.SI, cpu.DI,
            cpu.BP, cpu.BX
        };
        m_addrIndexRegs16 = new Reg16[] {
        
            cpu.SI, cpu.DI,
            cpu.SI, cpu.DI,
            null, null,
            null, null
        };
        
        // Initialize base and index register references for 32 bit memory pointers
        m_addrBaseIndexRegs32 = new Reg32[] {
            
            cpu.EAX, cpu.ECX,
            cpu.EDX, cpu.EBX,
            cpu.ESP, cpu.EBP,
            cpu.ESI, cpu.EDI
        };
        
        m_reg8 = new Reg8[] {
            
            cpu.AL, cpu.CL,
            cpu.DL, cpu.BL,
            cpu.AH, cpu.CH,
            cpu.DH, cpu.BH
        };
        
        m_reg16 = new Reg16[] {
            
            cpu.AX, cpu.CX,
            cpu.DX, cpu.BX,
            cpu.SP, cpu.BP,
            cpu.SI, cpu.DI
        };
        
        m_reg32 = new Reg32[] {
            
            cpu.EAX, cpu.ECX,
            cpu.EDX, cpu.EBX,
            cpu.ESP, cpu.EBP,
            cpu.ESI, cpu.EDI
        };
        
        // <editor-fold defaultstate="collapsed" desc="Decoder table initialization">
        
        // <editor-fold defaultstate="collapsed" desc="Extended table (0fh)">
        
        m_decoderTable0f = new InstructionDecoder[] {
            
            ( /* 0x0f 0x00: grp6 rm16 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* sldt rm16 */
                    case 0:
                        return new SLDT(cpu, buildRM16());
                    
                    /* str rm16 */
                    case 1:
                        return new STR(cpu, buildRM16());
                    
                    /* lldt rm16 */
                    case 2:
                        return new LLDT(cpu, buildRM16());
                        
                    /* ltr rm16 */
                    case 3:
                        return new LTR(cpu, buildRM16());
                        
                    /* verr rm16 */
                    case 4:
                        return new VERR(cpu, buildRM16());
                        
                    /* verw rm16 */
                    case 5:
                        return new VERW(cpu, buildRM16());
                    
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0x01: grp7 rm16/32 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* sgdt m16:32 */
                    case 0:
                        if(m_mod == 3)
                            throw CPUException.getInvalidOpcode();
                        
                        return new SGDT(cpu, buildMEM16(0), buildMEM32(2));
                    
                    /* sidt m16:32 */
                    case 1: 
                        if(m_mod == 3)
                            throw CPUException.getInvalidOpcode();
                        
                        return isOperandSize32() ? new SIDT32(cpu, buildMEM16(0), buildMEM32(2)) :
                                                   new SIDT16(cpu, buildMEM16(0), buildMEM32(2));

                    /* lgdt m16:32 */
                    case 2:
                        if(m_mod == 3)
                            throw CPUException.getInvalidOpcode();

                        return isOperandSize32() ? new LGDT32(cpu, buildMEM16(0), buildMEM32(2)) :
                                                   new LGDT16(cpu, buildMEM16(0), buildMEM32(2));

                    /* lidt m16:32 */
                    case 3:
                        if(m_mod == 3)
                            throw CPUException.getInvalidOpcode();

                        return isOperandSize32() ? new LIDT32(cpu, buildMEM16(0), buildMEM32(2)) :
                                                   new LIDT16(cpu, buildMEM16(0), buildMEM32(2));

                    /* smsw rm16/32 */
                    case 4:
                        return isOperandSize32() ? new SMSW(cpu, buildRM32(true)) :
                                                   new SMSW(cpu, buildRM16());

                    /* lmsw rm16 */    
                    case 6:
                        return finish(new LMSW(cpu, buildRM16()));
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0x02: lar r16/32, rm16/32 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new LAR(cpu, buildREG32(), buildRM32(), true) :
                                           new LAR(cpu, buildREG16(), buildRM16(), false);
            },

            ( /* 0x0f 0x03: lsl r16/32, rm16/32 */ ) -> {
                
                decodeMODRM();
                
                return isOperandSize32() ? new LSL(cpu, buildREG32(), buildRM32()) :
                                           new LSL(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x0f 0x04: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x05: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x06: clts */ ) -> {

                return new CLTS(cpu);
            },

            ( /* 0x0f 0x07: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x08: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x09: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x0a: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x0b: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x0c: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x0d: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x0e: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x0f: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x10: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x11: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x12: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x13: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x14: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x15: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x16: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x17: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x18: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x19: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x1a: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x1b: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x1c: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x1d: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x1e: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x1f: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x20: mov r32, cr0/2/3 */ ) -> {

                decodeMODRM();
                
                if(m_mod != 3)
                    throw CPUException.getInvalidOpcode();
                
                switch(m_reg) {
                    
                    case 0: return new MOV(cpu, buildRM32(), buildCR0());
                    case 2: return new MOV(cpu, buildRM32(), buildCR2());
                    case 3: return new MOV(cpu, buildRM32(), buildCR3());
                    
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0x21: mov r32, dr0-3..6-7 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    case 0: return new MOVSpecial(cpu, buildRM32(), buildDR0());
                    case 1: return new MOVSpecial(cpu, buildRM32(), buildDR1());
                    case 2: return new MOVSpecial(cpu, buildRM32(), buildDR2());
                    case 3: return new MOVSpecial(cpu, buildRM32(), buildDR3());
                    case 6: return new MOVSpecial(cpu, buildRM32(), buildDR6());
                    case 7: return new MOVSpecial(cpu, buildRM32(), buildDR7());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0x22: mov cr0/2/3, r32 */ ) -> {
                
                decodeMODRM();
                
                if(m_mod != 3)
                    throw CPUException.getInvalidOpcode();
                
                switch(m_reg) {
                    
                    case 0: return finish(new MOVSpecial(cpu, buildCR0(), buildRM32()));
                    case 2: return new MOVSpecial(cpu, buildCR2(), buildRM32());
                    case 3: return new MOVSpecial(cpu, buildCR3(), buildRM32());
                    
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0x23:  mov dr0-3..6-7, r32 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    case 0: return new MOVSpecial(cpu, buildDR0(), buildRM32());
                    case 1: return new MOVSpecial(cpu, buildDR1(), buildRM32());
                    case 2: return new MOVSpecial(cpu, buildDR2(), buildRM32());
                    case 3: return new MOVSpecial(cpu, buildDR3(), buildRM32());
                    case 6: return new MOVSpecial(cpu, buildDR6(), buildRM32());
                    case 7: return new MOVSpecial(cpu, buildDR7(), buildRM32());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0x24: mov t6-7, r32 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    case 6: return new MOVSpecial(cpu, buildTR6(), buildRM32());
                    case 7: return new MOVSpecial(cpu, buildTR7(), buildRM32());
                    
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0x25: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x26: mov r32, t6-7 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    case 6: return new MOVSpecial(cpu, buildRM32(), buildTR6());
                    case 7: return new MOVSpecial(cpu, buildRM32(), buildTR7());
                    
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0x27: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x28: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x29: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x2a: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x2b: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x2c: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x2d: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x2e: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x2f: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x30: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x31: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x32: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x33: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x34: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x35: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x36: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x37: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x38: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x39: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x3a: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x3b: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x3c: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x3d: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x3e: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x3f: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x40: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x41: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x42: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x43: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x44: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x45: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x46: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x47: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x48: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x49: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x4a: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x4b: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x4c: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x4d: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x4e: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x4f: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x50: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x51: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x52: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x53: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x54: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x55: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x56: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x57: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x58: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x59: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x5a: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x5b: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x5c: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x5d: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x5e: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x5f: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x60: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x61: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x62: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x63: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x64: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x65: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x66: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x67: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x68: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x69: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x6a: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x6b: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x6c: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x6d: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x6e: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x6f: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x70: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x71: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x72: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x73: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x74: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x75: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x76: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x77: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x78: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x79: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x7a: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x7b: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x7c: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x7d: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x7e: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x7f: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0x80: jo rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionOverflow()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionOverflow()));
            },

            ( /* 0x0f 0x81: jno rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionNotOverflow()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionNotOverflow()));
            },

            ( /* 0x0f 0x82: jb rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionBellow()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionBellow()));
            },

            ( /* 0x0f 0x83: jnb rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionNotBellow()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionNotBellow()));
            },

            ( /* 0x0f 0x84: jz rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionZero()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionZero()));
            },

            ( /* 0x0f 0x85: jnz rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionNotZero()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionNotZero()));
            },

            ( /* 0x0f 0x86: jbe rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionBellowOrEqual()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionBellowOrEqual()));
            },

            ( /* 0x0f 0x87: jnbe rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionNotBellowOrEqual()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionNotBellowOrEqual()));
            },

            ( /* 0x0f 0x88: js rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionSign()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionSign()));
            },

            ( /* 0x0f 0x89: jns rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionNotSign()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionNotSign()));
            },

            ( /* 0x0f 0x8a: jp rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionParity()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionParity()));
            },

            ( /* 0x0f 0x8b: jnp rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionNotParity()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionNotParity()));
            },

            ( /* 0x0f 0x8c: jl rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionLess()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionLess()));
            },

            ( /* 0x0f 0x8d: jnl rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionNotLess()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionNotLess()));
            },

            ( /* 0x0f 0x8e: jle rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionLessOrEqual()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionLessOrEqual()));
            },

            ( /* 0x0f 0x8f: jnle rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_CONDITIONAL(cpu, buildIPRelativeIMM32(), buildConditionNotLessOrEqual()) :
                                                           new JMP_CONDITIONAL(cpu, buildIPRelativeIMM16(), buildConditionNotLessOrEqual()));
            },

            ( /* 0x0f 0x90: seto rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionOverflow());
            },

            ( /* 0x0f 0x91: setno rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionNotOverflow());
            },

            ( /* 0x0f 0x92: setb rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionBellow());
            },

            ( /* 0x0f 0x93: setnb rm8 */ ) -> {

                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionNotBellow());
            },

            ( /* 0x0f 0x94: setz rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionZero());
            },

            ( /* 0x0f 0x95: setnz rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionNotZero());
            },

            ( /* 0x0f 0x96: setbe rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionBellowOrEqual());
            },

            ( /* 0x0f 0x97: setnbe rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionNotBellowOrEqual());
            },

            ( /* 0x0f 0x98: sets rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionSign());
            },

            ( /* 0x0f 0x99: setns rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionNotSign());
            },

            ( /* 0x0f 0x9a: setp rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionParity());
            },

            ( /* 0x0f 0x9b: setnp rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionNotParity());
            },

            ( /* 0x0f 0x9c: setl rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionLess());
            },

            ( /* 0x0f 0x9d: setnl rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionNotLess());
            },

            ( /* 0x0f 0x9e: setle rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionLessOrEqual());
            },

            ( /* 0x0f 0x9f: setnle rm8 */ ) -> {
                
                decodeMODRM();
                
                return new SET(cpu, buildRM8(), buildConditionNotLessOrEqual());
            },
            
            ( /* 0x0f 0xa0: push fs */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildFS()) :
                                           new PUSH16(cpu, buildFS());
            },

            ( /* 0x0f 0xa1: pop fs */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildFS()) :
                                           new POP16(cpu, buildFS());
            },

            ( /* 0x0f 0xa2: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xa3: bt rm16/32, r16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3) {
                    
                    return isOperandSize32() ? new BT32_REG(cpu, buildRM32(), buildREG32()) :
                                               new BT16_REG(cpu, buildRM16(), buildREG16());
                }
                else {
                    
                    return isOperandSize32() ? new BT32_MEM(cpu, buildMEM32(0), buildREG32()) :
                                               new BT16_MEM(cpu, buildMEM16(0), buildREG16());
                }
            },

            ( /* 0x0f 0xa4: shld rm16/32, r16/32, imm8 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new SHLD32(cpu, buildRM32(), buildREG32(), buildIMM8()) :
                                           new SHLD16(cpu, buildRM16(), buildREG16(), buildIMM8());
            },

            ( /* 0x0f 0xa5: shld rm16/32, r16/32, cl */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new SHLD32(cpu, buildRM32(), buildREG32(), buildCL()) :
                                           new SHLD16(cpu, buildRM16(), buildREG16(), buildCL());
            },

            ( /* 0x0f 0xa6: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xa7: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xa8: push gs */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildGS()) :
                                           new PUSH16(cpu, buildGS());
            },

            ( /* 0x0f 0xa9: pop gs */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildGS()) :
                                           new POP16(cpu, buildGS());
            },

            ( /* 0x0f 0xaa: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xab: bts rm16/32, r16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3) {
                    
                    return isOperandSize32() ? new BTS32_REG(cpu, buildRM32(), buildREG32()) :
                                               new BTS16_REG(cpu, buildRM16(), buildREG16());
                }
                else {
                    
                    return isOperandSize32() ? new BTS32_MEM(cpu, buildMEM32(0), buildREG32()) :
                                               new BTS16_MEM(cpu, buildMEM16(0), buildREG16());
                }
            },

            ( /* 0x0f 0xac: shrd rm16/32, r16/32, imm8 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new SHRD32(cpu, buildRM32(), buildREG32(), buildIMM8()) :
                                           new SHRD16(cpu, buildRM16(), buildREG16(), buildIMM8());
            },

            ( /* 0x0f 0xad: shrd rm16/32, r16/32, cl */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new SHRD32(cpu, buildRM32(), buildREG32(), buildCL()) :
                                           new SHRD16(cpu, buildRM16(), buildREG16(), buildCL());
            },

            ( /* 0x0f 0xae: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xaf: imul r16/32, rm16/32 */ ) -> {
                
                decodeMODRM();
                
                return isOperandSize32() ? new IMUL32_2(cpu, buildREG32(), buildREG32(), buildRM32()) :
                                           new IMUL16_2(cpu, buildREG16(), buildREG16(), buildRM16());
            },

            ( /* 0x0f 0xb0: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xb1: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xb2: lss r16/32, m16:16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3)
                    throw CPUException.getInvalidOpcode();
                
                return isOperandSize32() ? new LSEG(cpu, buildSS(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                           new LSEG(cpu, buildSS(), buildREG16(), buildMEM16(2), buildMEM16(0));
            },

            ( /* 0x0f 0xb3: btr rm16/32, r16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3) {
                    
                    return isOperandSize32() ? new BTR32_REG(cpu, buildRM32(), buildREG32()) :
                                               new BTR16_REG(cpu, buildRM16(), buildREG16());
                }
                else {
                    
                    return isOperandSize32() ? new BTR32_MEM(cpu, buildMEM32(0), buildREG32()) :
                                               new BTR16_MEM(cpu, buildMEM16(0), buildREG16());
                }
            },

            ( /* 0x0f 0xb4: lfs r16/32, m16:16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3)
                    throw CPUException.getInvalidOpcode();
                
                return isOperandSize32() ? new LSEG(cpu, buildFS(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                           new LSEG(cpu, buildFS(), buildREG16(), buildMEM16(2), buildMEM16(0));
            },

            ( /* 0x0f 0xb5: lgs r16/32, m16:16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3)
                    throw CPUException.getInvalidOpcode();
                
                return isOperandSize32() ? new LSEG(cpu, buildGS(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                           new LSEG(cpu, buildGS(), buildREG16(), buildMEM16(2), buildMEM16(0));
            },

            ( /* 0x0f 0xb6: movzx r16/32, rm8 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new MOVZX(cpu, buildREG32(), buildRM8()) :
                                           new MOVZX(cpu, buildREG16(), buildRM8());
            },

            ( /* 0x0f 0xb7: movzx r32, rm16 */ ) -> {

                decodeMODRM();
                
                return new MOVZX(cpu, buildREG32(), buildRM16());
            },

            ( /* 0x0f 0xb8: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xb9: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xba: btx rm16/32, imm8 */ ) -> {
                
                decodeMODRM();
                
                switch(m_reg) {
                    
                    case 4: return isOperandSize32() ? new BT32_REG(cpu, buildRM32(), buildIMM8()) :
                                                       new BT16_REG(cpu, buildRM16(), buildIMM8());
                        
                    case 5: return isOperandSize32() ? new BTS32_REG(cpu, buildRM32(), buildIMM8()) :
                                                       new BTS16_REG(cpu, buildRM16(), buildIMM8());
                    
                    case 6: return isOperandSize32() ? new BTR32_REG(cpu, buildRM32(), buildIMM8()) :
                                                       new BTR16_REG(cpu, buildRM16(), buildIMM8());
                    
                    case 7: return isOperandSize32() ? new BTC32_REG(cpu, buildRM32(), buildIMM8()) :
                                                       new BTC16_REG(cpu, buildRM16(), buildIMM8());
                    
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x0f 0xbb: btc rm16/32, r16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3) {
                    
                    return isOperandSize32() ? new BTC32_REG(cpu, buildRM32(), buildREG32()) :
                                               new BTC16_REG(cpu, buildRM16(), buildREG16());
                }
                else {
                    
                    return isOperandSize32() ? new BTC32_MEM(cpu, buildMEM32(0), buildREG32()) :
                                               new BTC16_MEM(cpu, buildMEM16(0), buildREG16());
                }
            },

            ( /* 0x0f 0xbc: bsf r16/32, rm16/32 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new BSF32(cpu, buildREG32(), buildRM32()) :
                                           new BSF16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x0f 0xbd: bsr r16/32, rm16/32 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new BSR32(cpu, buildREG32(), buildRM32()) :
                                           new BSR16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x0f 0xbe: movsx r16/32, rm8 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new MOVSX8TO32(cpu, buildREG32(), buildRM8()) :
                                           new MOVSX8TO16(cpu, buildREG16(), buildRM8());
            },

            ( /* 0x0f 0xbf: movsx r32, rm16 */ ) -> {

                decodeMODRM();
                
                return new MOVSX16TO32(cpu, buildREG32(), buildRM16());
            },

            ( /* 0x0f 0xc0: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc1: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc2: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc3: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc4: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc5: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc6: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc7: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc8: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xc9: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xca: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xcb: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xcc: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xcd: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xce: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xcf: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd0: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd1: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd2: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd3: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd4: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd5: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd6: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd7: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd8: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xd9: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xda: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xdb: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xdc: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xdd: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xde: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xdf: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe0: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe1: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe2: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe3: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe4: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe5: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe6: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe7: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe8: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xe9: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xea: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xeb: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xec: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xed: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xee: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xef: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf0: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf1: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf2: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf3: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf4: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf5: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf6: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf7: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf8: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xf9: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xfa: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xfb: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xfc: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xfd: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xfe: Invalid */ ) -> {

                throw CPUException.getInvalidOpcode();
            },

            ( /* 0x0f 0xff: Invalid */ ) -> {
                
                throw CPUException.getInvalidOpcode();
            }
        };
        
        // </editor-fold>
        // <editor-fold defaultstate="collapsed" desc="Default table">
        
        m_decoderTable = new InstructionDecoder[] {
        
            ( /* 0x00: add rm8, r8 */ ) -> {
                
                decodeMODRM();

                return new ADD8(cpu, buildRM8(), buildREG8());
            },
        
            ( /* 0x01: add rm16/32, r16/32 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new ADD32(cpu, buildRM32(), buildREG32()) :
                                           new ADD16(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x02: add r8, rm8 */ ) -> {
                
                decodeMODRM();

                return new ADD8(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x03: add r16, rm16 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new ADD32(cpu, buildREG32(), buildRM32()) :
                                           new ADD16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x04: add al, imm8 */ ) -> {

                return new ADD8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0x05: add ax/eax, imm16/32 */ ) -> {

                return isOperandSize32() ? new ADD32(cpu, buildEAX(), buildIMM32()) :
                                           new ADD16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0x06: push es */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildES()) :
                                           new PUSH16(cpu, buildES());
            },

            ( /* 0x07: pop es */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildES()) :
                                           new POP16(cpu, buildES());
            },
            
            ( /* 0x08: or rm8, r8 */ ) -> {
                
                decodeMODRM();
                
                return new OR8(cpu, buildRM8(), buildREG8());
            },

            ( /* 0x09: or rm16/32, r16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new OR32(cpu, buildRM32(), buildREG32()) :
                                           new OR16(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x0a: or r8, rm8 */ ) -> {
                
                decodeMODRM();

                return new OR8(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x0b: or r16/32, rm16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new OR32(cpu, buildREG32(), buildRM32()) :
                                           new OR16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x0c: or al, imm8 */ ) -> {

                return new OR8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0x0d: or ax/eax, imm16/32 */ ) -> {

                return isOperandSize32() ? new OR32(cpu, buildEAX(), buildIMM32()) :
                                           new OR16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0x0e: push cs */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildCS()) :
                                           new PUSH16(cpu, buildCS());
            },

            ( /* 0x0f: Extended opcode table */ ) -> {

                m_decoderTableCurrent = m_decoderTable0f;
                
                return null;
            },
            
            ( /* 0x10: adc rm8, r8 */ ) -> {
                
                decodeMODRM();

                return new ADC8(cpu, buildRM8(), buildREG8());
            },

            ( /* 0x11: adc rm16/32, r16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new ADC32(cpu, buildRM32(), buildREG32()) :
                                           new ADC16(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x12: adc r8, rm8 */ ) -> {
                
                decodeMODRM();

                return new ADC8(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x13: adc r16/32, rm16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new ADC32(cpu, buildREG32(), buildRM32()) :
                                           new ADC16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x14: adc al, imm8 */ ) -> {

                return new ADC8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0x15: adc ax/eax, imm16/32 */ ) -> {

                return isOperandSize32() ? new ADC32(cpu, buildEAX(), buildIMM32()) :
                                           new ADC16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0x16: push ss */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildSS()) :
                                           new PUSH16(cpu, buildSS());
            },

            ( /* 0x17: pop ss */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildSS()) :
                                           new POP16(cpu, buildSS());
            },
            
            ( /* 0x18: sbb rm8, r8 */ ) -> {
                
                decodeMODRM();

                return new SBB8(cpu, buildRM8(), buildREG8());
            },

            ( /* 0x19: sbb rm16/32, r16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new SBB32(cpu, buildRM32(), buildREG32()) :
                                           new SBB16(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x1a: sbb r8, rm8 */ ) -> {
                
                decodeMODRM();

                return new SBB8(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x1b: sbb r16/32, rm16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new SBB32(cpu, buildREG32(), buildRM32()) :
                                           new SBB16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x1c: sbb al, imm8 */ ) -> {

                return new SBB8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0x1d: sbb ax/eax, imm16/32 */ ) -> {

                return isOperandSize32() ? new SBB32(cpu, buildEAX(), buildIMM32()) :
                                           new SBB16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0x1e: push ds */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildDS()) :
                                           new PUSH16(cpu, buildDS());
            },

            ( /* 0x1f: pop ds */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildDS()) :
                                           new POP16(cpu, buildDS());
            },
            
            ( /* 0x20: and rm8, r8 */ ) -> {
                
                decodeMODRM();

                return new AND8(cpu, buildRM8(), buildREG8());
            },

            ( /* 0x21: and rm16/32, r16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new AND32(cpu, buildRM32(), buildREG32()) :
                                           new AND16(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x22: and r8, rm8 */ ) -> {
                
                decodeMODRM();

                return new AND8(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x23: and r16/32, rm16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new AND32(cpu, buildREG32(), buildRM32()) :
                                           new AND16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x24: and al, imm8 */ ) -> {

                return new AND8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0x25: and ax/eax, imm16/32 */ ) -> {

                return isOperandSize32() ? new AND32(cpu, buildEAX(), buildIMM32()) :
                                           new AND16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0x26: es: */ ) -> {

                //if(m_prefixSegmentOverride == null)
                    m_prefixSegmentOverride = cpu.ES;
                
                return null;
            },

            ( /* 0x27: daa */ ) -> {

                return new DAA(cpu);
            },
            
            ( /* 0x28: sub rm8, r8 */ ) -> {
                
                decodeMODRM();

                return new SUB8(cpu, buildRM8(), buildREG8());
            },

            ( /* 0x29: sub rm16/32, r16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new SUB32(cpu, buildRM32(), buildREG32()) :
                                           new SUB16(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x2a: sub r8, rm8 */ ) -> {
                
                decodeMODRM();

                return new SUB8(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x2b: sub r16/32, rm16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new SUB32(cpu, buildREG32(), buildRM32()) :
                                           new SUB16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x2c: sub al, imm8 */ ) -> {

                return new SUB8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0x2d: sub ax/eax, imm16/32 */ ) -> {

                return isOperandSize32() ? new SUB32(cpu, buildEAX(), buildIMM32()) :
                                           new SUB16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0x2e: cs: */ ) -> {

                //if(m_prefixSegmentOverride == null)
                    m_prefixSegmentOverride = cpu.CS;
                
                return null;
            },

            ( /* 0x2f: das */ ) -> {

                return new DAS(cpu);
            },

            ( /* 0x30: xor rm8, r8 */ ) -> {
                
                decodeMODRM();

                return new XOR8(cpu, buildRM8(), buildREG8());
            },

            ( /* 0x31: xor rm16/32, r16/32 */ ) -> {
                
                decodeMODRM();
                
                return isOperandSize32() ? new XOR32(cpu, buildRM32(), buildREG32()) :
                                           new XOR16(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x32: xor r8, rm8 */ ) -> {

                decodeMODRM();
                
                return new XOR8(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x33: xor r16/32, rm16/32 */ ) -> {
                
                decodeMODRM();
                
                return isOperandSize32() ? new XOR32(cpu, buildREG32(), buildRM32()) :
                                           new XOR16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x34: xor al, imm8 */ ) -> {

                return new XOR8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0x35: xor ax, imm16 */ ) -> {
                
                return isOperandSize32() ? new XOR32(cpu, buildEAX(), buildIMM32()) :
                                           new XOR16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0x36: ss: */ ) -> {

                //if(m_prefixSegmentOverride == null)
                    m_prefixSegmentOverride = cpu.SS;
                
                return null;
            },

            ( /* 0x37: aaa */ ) -> {

                return new AAA(cpu);
            },
            
            ( /* 0x38: cmp rm8, r8 */ ) -> {
                
                decodeMODRM();

                return new CMP8(cpu, buildRM8(), buildREG8());
            },

            ( /* 0x39: cmp rm16/32, r16/32 */ ) -> {
                
                decodeMODRM();
                
                return isOperandSize32() ? new CMP32(cpu, buildRM32(), buildREG32()) :
                                           new CMP16(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x3a: cmp r8, rm8 */ ) -> {

                decodeMODRM();
                
                return new CMP8(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x3b: cmp r16/32, rm16/32 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new CMP32(cpu, buildREG32(), buildRM32()) :
                                           new CMP16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x3c: cmp al, imm8 */ ) -> {

                return new CMP8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0x3d: cmp ax/eax, imm16/32 */ ) -> {
                
                return isOperandSize32() ? new CMP32(cpu, buildEAX(), buildIMM32()) :
                                           new CMP16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0x3e: ds: */ ) -> {

                //if(m_prefixSegmentOverride == null)
                    m_prefixSegmentOverride = cpu.DS;
                
                return null;
            },

            ( /* 0x3f: aas */ ) -> {

                return new AAS(cpu);
            },

            ( /* 0x40: inc ax/eax */ ) -> {

                return isOperandSize32() ? new INC32(cpu, buildEAX()) :
                                           new INC16(cpu, buildAX());
            },

            ( /* 0x41: inc cx/ecx */ ) -> {

                return isOperandSize32() ? new INC32(cpu, buildECX()) :
                                           new INC16(cpu, buildCX());
            },

            ( /* 0x42: inc dx/edx */ ) -> {

                return isOperandSize32() ? new INC32(cpu, buildEDX()) :
                                           new INC16(cpu, buildDX());
            },

            ( /* 0x43: inc bx/ebx */ ) -> {

                return isOperandSize32() ? new INC32(cpu, buildEBX()) :
                                           new INC16(cpu, buildBX());
            },

            ( /* 0x44: inc sp/esp */ ) -> {

                return isOperandSize32() ? new INC32(cpu, buildESP()) :
                                           new INC16(cpu, buildSP());
            },

            ( /* 0x45: inc bp/ebp */ ) -> {

                return isOperandSize32() ? new INC32(cpu, buildEBP()) :
                                           new INC16(cpu, buildBP());
            },

            ( /* 0x46: inc si/esi */ ) -> {

                return isOperandSize32() ? new INC32(cpu, buildESI()) :
                                           new INC16(cpu, buildSI());
            },

            ( /* 0x47: inc di/edi */ ) -> {

                return isOperandSize32() ? new INC32(cpu, buildEDI()) :
                                           new INC16(cpu, buildDI());
            },

            ( /* 0x48: dec ax/eax */ ) -> {

                return isOperandSize32() ? new DEC32(cpu, buildEAX()) :
                                           new DEC16(cpu, buildAX());
            },

            ( /* 0x49: dec cx/ecx */ ) -> {

                return isOperandSize32() ? new DEC32(cpu, buildECX()) :
                                           new DEC16(cpu, buildCX());
            },

            ( /* 0x4a: dec dx/edx */ ) -> {

                return isOperandSize32() ? new DEC32(cpu, buildEDX()) :
                                           new DEC16(cpu, buildDX());
            },

            ( /* 0x4b: dec bx/ebx */ ) -> {

                return isOperandSize32() ? new DEC32(cpu, buildEBX()) :
                                           new DEC16(cpu, buildBX());
            },

            ( /* 0x4c: dec sp/esp */ ) -> {

                return isOperandSize32() ? new DEC32(cpu, buildESP()) :
                                           new DEC16(cpu, buildSP());
            },

            ( /* 0x4d: dec bp/ebp */ ) -> {

                return isOperandSize32() ? new DEC32(cpu, buildEBP()) :
                                           new DEC16(cpu, buildBP());
            },

            ( /* 0x4e: dec si/esi */ ) -> {

                return isOperandSize32() ? new DEC32(cpu, buildESI()) :
                                           new DEC16(cpu, buildSI());
            },

            ( /* 0x4f: dec di/edi */ ) -> {

                return isOperandSize32() ? new DEC32(cpu, buildEDI()) :
                                           new DEC16(cpu, buildDI());
            },

            ( /* 0x50: push ax/eax */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildEAX()) :
                                           new PUSH16(cpu, buildAX());
            },

            ( /* 0x51: push cx/ecx */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildECX()) :
                                           new PUSH16(cpu, buildCX());
            },

            ( /* 0x52: push dx/edx */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildEDX()) :
                                           new PUSH16(cpu, buildDX());
            },

            ( /* 0x53: push bx/ebx */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildEBX()) :
                                           new PUSH16(cpu, buildBX());
            },

            ( /* 0x54: push sp/esp */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildESP()) :
                                           new PUSH16(cpu, buildSP());
            },

            ( /* 0x55: push bp/ebp */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildEBP()) :
                                           new PUSH16(cpu, buildBP());
            },

            ( /* 0x56: push si/esi */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildESI()) :
                                           new PUSH16(cpu, buildSI());
            },

            ( /* 0x57: push di/edi */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildEDI()) :
                                           new PUSH16(cpu, buildDI());
            },

            ( /* 0x58: pop ax/eax */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildEAX()) :
                                           new POP16(cpu, buildAX());
            },

            ( /* 0x59: pop cx/ecx */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildECX()) :
                                           new POP16(cpu, buildCX());
            },

            ( /* 0x5a: pop dx/edx */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildEDX()) :
                                           new POP16(cpu, buildDX());
            },

            ( /* 0x5b: pop bx/ebx */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildEBX()) :
                                           new POP16(cpu, buildBX());
            },

            ( /* 0x5c: pop sp/esp */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildESP()) :
                                           new POP16(cpu, buildSP());
            },

            ( /* 0x5d: pop bp/ebp */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildEBP()) :
                                           new POP16(cpu, buildBP());
            },

            ( /* 0x5e: pop si/esi */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildESI()) :
                                           new POP16(cpu, buildSI());
            },

            ( /* 0x5f: pop di/edi */ ) -> {

                return isOperandSize32() ? new POP32(cpu, buildEDI()) :
                                           new POP16(cpu, buildDI());
            },

            ( /* 0x60: pusha */ ) -> {

                return isOperandSize32() ? new PUSHA32(cpu) :
                                           new PUSHA16(cpu);
            },

            ( /* 0x61: popa */ ) -> {

                return isOperandSize32() ? new POPA32(cpu) :
                                           new POPA16(cpu);
            },

            ( /* 0x62: bound r16/32, m16/32&16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3)
                    throw CPUException.getInvalidOpcode();
                
                return isOperandSize32() ? new BOUND32(cpu, buildREG32(), buildMEM32(0), buildMEM32(4)) :
                                           new BOUND16(cpu, buildREG16(), buildMEM16(0), buildMEM16(2));
            },

            ( /* 0x63: arpl r16, rm16 */ ) -> {

                decodeMODRM();
                
                return new ARPL(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x64: fs: */ ) -> {

                //if(m_prefixSegmentOverride == null)
                    m_prefixSegmentOverride = cpu.FS;
                
                return null;
            },

            ( /* 0x65: gs: */ ) -> {

                //if(m_prefixSegmentOverride == null)
                    m_prefixSegmentOverride = cpu.GS;
                
                return null;
            },

            ( /* 0x66: Operand size override */ ) -> {

                m_prefixOperandSizeOverride = true;
                
                return null;
            },

            ( /* 0x67: Address size override */ ) -> {

                m_prefixAddressSizeOverride = true;
                
                return null;
            },

            ( /* 0x68: push imm16/32 */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildIMM32()) :
                                           new PUSH16(cpu, buildIMM16());
            },

            ( /* 0x69: imul r16/32, rm16/32, imm16/32 */ ) -> {

                decodeMODRM();

                return isOperandSize32() ? new IMUL32_2(cpu, buildREG32(), buildRM32(), buildIMM32()) :
                                           new IMUL16_2(cpu, buildREG16(), buildRM16(), buildIMM16());
            },

            ( /* 0x6a: push imm8 */ ) -> {

                return isOperandSize32() ? new PUSH32(cpu, buildSIMM8()) :
                                           new PUSH16(cpu, buildSIMM8());
            },

            ( /* 0x6b: imul r16/32, r/m16/32, imm8 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new IMUL32_2(cpu, buildREG32(), buildRM32(), buildSIMM8()) :
                                           new IMUL16_2(cpu, buildREG16(), buildRM16(), buildSIMM8());
            },

            ( /* 0x6c: insb */ ) -> {

                return new INSB(cpu, getDestIndex());
            },

            ( /* 0x6d: insw/insd */ ) -> {

                return isOperandSize32() ? new INSD(cpu, getDestIndex()) :
                                           new INSW(cpu, getDestIndex());
            },

            ( /* 0x6e: outsb */ ) -> {

                return new OUTSB(cpu, getSrcIndex(), getDefaultSegment());
            },

            ( /* 0x6f: outsw/outsd */ ) -> {

                return isOperandSize32() ? new OUTSD(cpu, getSrcIndex(), getDefaultSegment()) :
                                           new OUTSW(cpu, getSrcIndex(), getDefaultSegment());
            },

            ( /* 0x70: jo rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionOverflow()));
            },

            ( /* 0x71: jno rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionNotOverflow()));
            },

            ( /* 0x72: jb rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionBellow()));
            },

            ( /* 0x73: jnb rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionNotBellow()));
            },

            ( /* 0x74: jz rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionZero()));
            },

            ( /* 0x75: jnz rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionNotZero()));
            },

            ( /* 0x76: jbe rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionBellowOrEqual()));
            },

            ( /* 0x77: jnbe rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionNotBellowOrEqual()));
            },

            ( /* 0x78: js rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionSign()));
            },

            ( /* 0x79: jns rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionNotSign()));
            },

            ( /* 0x7a: jp rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionParity()));
            },

            ( /* 0x7b: jnp rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionNotParity()));
            },

            ( /* 0x7c: jl rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionLess()));
            },

            ( /* 0x7d: jnl rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionNotLess()));
            },

            ( /* 0x7e: jle rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionLessOrEqual()));
            },

            ( /* 0x7f: jnle rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionNotLessOrEqual()));
            },

            ( /* 0x80: grp1 rm8, imm8 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* add rm8, imm8 */
                    case 0: return new ADD8(cpu, buildRM8(), buildIMM8());

                    /* or rm8, imm8 */
                    case 1: return new OR8(cpu, buildRM8(), buildIMM8());

                    /* adc rm8, imm8 */
                    case 2: return new ADC8(cpu, buildRM8(), buildIMM8());

                    /* sbb rm8, imm8 */
                    case 3: return new SBB8(cpu, buildRM8(), buildIMM8());

                    /* and rm8, imm8 */
                    case 4: return new AND8(cpu, buildRM8(), buildIMM8());

                    /* sub rm8, imm8 */
                    case 5: return new SUB8(cpu, buildRM8(), buildIMM8());

                    /* xor rm8, imm8 */
                    case 6: return new XOR8(cpu, buildRM8(), buildIMM8());

                    /* cmp rm8, imm8 */
                    case 7: return new CMP8(cpu, buildRM8(), buildIMM8());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x81: grp1 rm16/32, imm16/32 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* add rm16/32, imm16/32 */
                    case 0: return isOperandSize32() ? new ADD32(cpu, buildRM32(), buildIMM32()) :
                                                       new ADD16(cpu, buildRM16(), buildIMM16());

                    /* or rm16/32, imm16/32 */
                    case 1: return isOperandSize32() ? new OR32(cpu, buildRM32(), buildIMM32()) :
                                                       new OR16(cpu, buildRM16(), buildIMM16());

                    /* adc rm16/32, imm16/32 */
                    case 2: return isOperandSize32() ? new ADC32(cpu, buildRM32(), buildIMM32()) :
                                                       new ADC16(cpu, buildRM16(), buildIMM16());

                    /* sbb rm16/32, imm16/32 */
                    case 3: return isOperandSize32() ? new SBB32(cpu, buildRM32(), buildIMM32()) :
                                                       new SBB16(cpu, buildRM16(), buildIMM16());

                    /* and rm16/32, imm16/32 */
                    case 4: return isOperandSize32() ? new AND32(cpu, buildRM32(), buildIMM32()) :
                                                       new AND16(cpu, buildRM16(), buildIMM16());

                    /* sub rm16/32, imm16/32 */
                    case 5: return isOperandSize32() ? new SUB32(cpu, buildRM32(), buildIMM32()) :
                                                       new SUB16(cpu, buildRM16(), buildIMM16());

                    /* xor rm16/32, imm16/32 */
                    case 6: return isOperandSize32() ? new XOR32(cpu, buildRM32(), buildIMM32()) :
                                                       new XOR16(cpu, buildRM16(), buildIMM16());

                    /* cmp rm16/32, imm16/32 */
                    case 7: return isOperandSize32() ? new CMP32(cpu, buildRM32(), buildIMM32()) :
                                                       new CMP16(cpu, buildRM16(), buildIMM16());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x82: grp1 rm8, imm8 */ ) -> {
                
                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* add rm8, imm8 */
                    case 0: return new ADD8(cpu, buildRM8(), buildIMM8());

                    /* or rm8, imm8 */
                    case 1: return new OR8(cpu, buildRM8(), buildIMM8());

                    /* adc rm8, imm8 */
                    case 2: return new ADC8(cpu, buildRM8(), buildIMM8());

                    /* sbb rm8, imm8 */
                    case 3: return new SBB8(cpu, buildRM8(), buildIMM8());

                    /* and rm8, imm8 */
                    case 4: return new AND8(cpu, buildRM8(), buildIMM8());

                    /* sub rm8, imm8 */
                    case 5: return new SUB8(cpu, buildRM8(), buildIMM8());

                    /* xor rm8, imm8 */
                    case 6: return new XOR8(cpu, buildRM8(), buildIMM8());

                    /* cmp rm8, imm8 */
                    case 7: return new CMP8(cpu, buildRM8(), buildIMM8());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },
            
            ( /* 0x83: grp1 rm16/32, simm8  */ ) -> {
                
                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* add rm16/32, simm8 */
                    case 0: return isOperandSize32() ? new ADD32(cpu, buildRM32(), buildSIMM8()) :
                                                       new ADD16(cpu, buildRM16(), buildSIMM8());
                        
                    /* or rm16/32, simm8 */
                    case 1: return isOperandSize32() ? new OR32(cpu, buildRM32(), buildSIMM8()) :
                                                       new OR16(cpu, buildRM16(), buildSIMM8());

                    /* adc rm16/32, simm8 */
                    case 2: return isOperandSize32() ? new ADC32(cpu, buildRM32(), buildSIMM8()) :
                                                       new ADC16(cpu, buildRM16(), buildSIMM8());

                    /* sbb rm16/32, simm8 */
                    case 3: return isOperandSize32() ? new SBB32(cpu, buildRM32(), buildSIMM8()) :
                                                       new SBB16(cpu, buildRM16(), buildSIMM8());

                    /* and rm16/32, simm8 */
                    case 4: return isOperandSize32() ? new AND32(cpu, buildRM32(), buildSIMM8()) :
                                                       new AND16(cpu, buildRM16(), buildSIMM8());

                    /* sub rm16/32, simm8 */
                    case 5: return isOperandSize32() ? new SUB32(cpu, buildRM32(), buildSIMM8()) :
                                                       new SUB16(cpu, buildRM16(), buildSIMM8());

                    /* xor rm16/32, simm8 */
                    case 6: return isOperandSize32() ? new XOR32(cpu, buildRM32(), buildSIMM8()) :
                                                       new XOR16(cpu, buildRM16(), buildSIMM8());

                    /* cmp rm16/32, simm8 */
                    case 7: return isOperandSize32() ? new CMP32(cpu, buildRM32(), buildSIMM8()) :
                                                       new CMP16(cpu, buildRM16(), buildSIMM8());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0x84: test r8, rm8 */ ) -> {
                
                decodeMODRM();

                return new TEST8(cpu, buildREG8(),
                                 buildRM8());
            },

            ( /* 0x85: test r16/32, rm16/32 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new TEST32(cpu, buildREG32(), buildRM32()) :
                                           new TEST16(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x86: xchg r8, rm8 */ ) -> {

                decodeMODRM();
                
                return new XCHG(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x87: xchg r16/32, rm16/32 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new XCHG(cpu, buildREG32(), buildRM32()) :
                                           new XCHG(cpu, buildREG16(), buildRM16());
            },
            
            ( /* 0x88: mov rm8, r8 */ ) -> {
                
                decodeMODRM();

                return new MOV(cpu, buildRM8(),
                               buildREG8());
            },

            ( /* 0x89: mov rm16/32, r16/32 */ ) -> {
                
                decodeMODRM();
                
                return isOperandSize32() ? new MOV(cpu, buildRM32(), buildREG32()) :
                                           new MOV(cpu, buildRM16(), buildREG16());
            },

            ( /* 0x8a: mov r8, rm8 */ ) -> {

                decodeMODRM();
                
                return new MOV(cpu, buildREG8(), buildRM8());
            },

            ( /* 0x8b: mov r16/32, rm16/32 */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new MOV(cpu, buildREG32(), buildRM32()) :
                                           new MOV(cpu, buildREG16(), buildRM16());
            },

            ( /* 0x8c: mov rm16/32, sreg */ ) -> {

                decodeMODRM();
                
                return isOperandSize32() ? new MOV(cpu, buildRM32(true), buildSEG(false)) :
                                           new MOV(cpu, buildRM16(), buildSEG(false));
            },
            
            ( /* 0x8d: lea r16/32, m16/32 */ ) -> {
                
                decodeMODRM();
                
                return isOperandSize32() ? new LEA(cpu, buildREG32(), buildADDR(0)) :
                                           new LEA(cpu, buildREG16(), buildADDR(0));
            },

            ( /* 0x8e: mov sreg, rm16 */ ) -> {
                
                decodeMODRM();
                
                return new MOV(cpu, buildSEG(true), buildRM16());
            },

            ( /* 0x8f: pop rm16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new POP32(cpu, buildRM32()) :
                                           new POP16(cpu, buildRM16());
            },

            ( /* 0x90: nop */ ) -> {

                return new NOP(cpu);
            },

            ( /* 0x91: xchg ax/eax, cx/ecx */ ) -> {

                return isOperandSize32() ? new XCHG(cpu, buildEAX(), buildECX()) :
                                           new XCHG(cpu, buildAX(), buildCX());
            },

            ( /* 0x92: xchg ax/eax, dx/edx */ ) -> {

                return isOperandSize32() ? new XCHG(cpu, buildEAX(), buildEDX()) :
                                           new XCHG(cpu, buildAX(), buildDX());
            },

            ( /* 0x93: xchg ax/eax, bx/ebx */ ) -> {

                return isOperandSize32() ? new XCHG(cpu, buildEAX(), buildEBX()) :
                                           new XCHG(cpu, buildAX(), buildBX());
            },

            ( /* 0x94: xchg ax/eax, sp/esp */ ) -> {

                return isOperandSize32() ? new XCHG(cpu, buildEAX(), buildESP()) :
                                           new XCHG(cpu, buildAX(), buildSP());
            },

            ( /* 0x95: xchg ax/eax, bp/ebp */ ) -> {

                return isOperandSize32() ? new XCHG(cpu, buildEAX(), buildEBP()) :
                                           new XCHG(cpu, buildAX(), buildBP());
            },

            ( /* 0x96: xchg ax/eax, si/esi */ ) -> {

                return isOperandSize32() ? new XCHG(cpu, buildEAX(), buildESI()) :
                                           new XCHG(cpu, buildAX(), buildSI());
            },

            ( /* 0x97: xchg ax/eax, di/edi */ ) -> {

                return isOperandSize32() ? new XCHG(cpu, buildEAX(), buildEDI()) :
                                           new XCHG(cpu, buildAX(), buildDI());
            },

            ( /* 0x98: cbw/cwde */ ) -> {

                return isOperandSize32() ? new CWDE(cpu) :
                                           new CBW(cpu);
            },

            ( /* 0x99: cwd/cwq */ ) -> {

                return isOperandSize32() ? new CDQ(cpu) :
                                           new CWD(cpu);
            },

            ( /* 0x9a: call imm16:16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new CALL_FAR(cpu, buildIMM32(), buildIMM16(), true) :
                                                  new CALL_FAR(cpu, buildIMM16(), buildIMM16(), false));
            },

            ( /* 0x9b: wait */ ) -> {
                
                m_prefixWait = true;
                
                return null;
            },

            ( /* 0x9c: pushf */ ) -> {
                
                return isOperandSize32() ? new PUSHF32(cpu) :
                                           new PUSHF16(cpu);
            },

            ( /* 0x9d: popf */ ) -> {

                return isOperandSize32() ? new POPF32(cpu) :
                                           new POPF16(cpu);
            },

            ( /* 0x9e: sahf */ ) -> {

                return new SAHF(cpu);
            },

            ( /* 0x9f: lahf */ ) -> {

                return new LAHF(cpu);
            },

            ( /* 0xa0: mov al, moffs8 */ ) -> {

                return new MOV(cpu, buildAL(), buildMOFFS8());
            },

            ( /* 0xa1: mov ax/eax, moffs16/32 */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildEAX(), buildMOFFS32()) :
                                           new MOV(cpu, buildAX(), buildMOFFS16());
            },

            ( /* 0xa2: mov moffs8, al */ ) -> {

                return new MOV(cpu, buildMOFFS8(), buildAL());
            },

            ( /* 0xa3: mov moffs16/32, ax/eax */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildMOFFS32(), buildEAX()) :
                                           new MOV(cpu, buildMOFFS16(), buildAX());
            },

            ( /* 0xa4: movsb */ ) -> {

                return new MOVSB(cpu, getDefaultSegment(), getSrcIndex(), getDestIndex());
            },

            ( /* 0xa5: movsw/movsd */ ) -> {

                return isOperandSize32() ? new MOVSD(cpu, getDefaultSegment(), getSrcIndex(), getDestIndex()) :
                                           new MOVSW(cpu, getDefaultSegment(), getSrcIndex(), getDestIndex());
            },

            ( /* 0xa6: cmpsb */ ) -> {

                return new CMPSB(cpu, getDefaultSegment(), getSrcIndex(), getDestIndex());
            },

            ( /* 0xa7: cmpsw */ ) -> {

                return isOperandSize32() ? new CMPSD(cpu, getDefaultSegment(), getSrcIndex(), getDestIndex()) :
                                           new CMPSW(cpu, getDefaultSegment(), getSrcIndex(), getDestIndex());
            },

            ( /* 0xa8: test al, imm8 */ ) -> {

                return new TEST8(cpu, buildAL(), buildIMM8());
            },

            ( /* 0xa9: test ax/eax, imm16/32 */ ) -> {

                return isOperandSize32() ? new TEST32(cpu, buildEAX(), buildIMM32()) :
                                           new TEST16(cpu, buildAX(), buildIMM16());
            },

            ( /* 0xaa: stosb */ ) -> {

                return new STOSB(cpu, getDestIndex());
            },

            ( /* 0xab: stosw/stosd */ ) -> {

                return isOperandSize32() ? new STOSD(cpu, getDestIndex()) :
                                           new STOSW(cpu, getDestIndex());
            },

            ( /* 0xac: lodsb */ ) -> {

                return new LODSB(cpu, getDefaultSegment(), getSrcIndex());
            },

            ( /* 0xad: lodsw/lodsd */ ) -> {

                return isOperandSize32() ? new LODSD(cpu, getDefaultSegment(), getSrcIndex()) :
                                           new LODSW(cpu, getDefaultSegment(), getSrcIndex());
            },

            ( /* 0xae: scasb */ ) -> {

                return new SCASB(cpu, getDestIndex());
            },

            ( /* 0xaf: scasw/scasd */ ) -> {

                return isOperandSize32() ? new SCASD(cpu, getDestIndex()) :
                                           new SCASW(cpu, getDestIndex());
            },
            
            ( /* 0xb0: mov al, imm8 */ ) -> {

                return new MOV(cpu, buildAL(), buildIMM8());
            },

            ( /* 0xb1: mov cl, imm8 */ ) -> {

                return new MOV(cpu, buildCL(), buildIMM8());
            },

            ( /* 0xb2: mov dl, imm8 */ ) -> {

                return new MOV(cpu, buildDL(), buildIMM8());
            },

            ( /* 0xb3: mov bl, imm8 */ ) -> {

                return new MOV(cpu, buildBL(), buildIMM8());
            },

            ( /* 0xb4: mov ah, imm8 */ ) -> {

                return new MOV(cpu, buildAH(), buildIMM8());
            },

            ( /* 0xb5: mov ch, imm8 */ ) -> {

                return new MOV(cpu, buildCH(), buildIMM8());
            },

            ( /* 0xb6: mov dh, imm8 */ ) -> {

                return new MOV(cpu, buildDH(), buildIMM8());
            },

            ( /* 0xb7: mov bh, imm8 */ ) -> {

                return new MOV(cpu, buildBH(), buildIMM8());
            },

            ( /* 0xb8: mov ax/eax, imm16/32 */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildEAX(), buildIMM32()) :
                                           new MOV(cpu, buildAX(), buildIMM16());
            },

            ( /* 0xb9: mov cx/ecx, imm16/32 */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildECX(), buildIMM32()):
                                           new MOV(cpu, buildCX(), buildIMM16());
            },

            ( /* 0xba: mov dx/edx, imm16/32 */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildEDX(), buildIMM32()) :
                                           new MOV(cpu, buildDX(), buildIMM16());
            },

            ( /* 0xbb: mov bx/ebx, imm16/32 */ ) -> {
                
                return isOperandSize32() ? new MOV(cpu, buildEBX(), buildIMM32()) :
                                           new MOV(cpu, buildBX(), buildIMM16());
            },

            ( /* 0xbc: mov sp/esp, imm16/32 */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildESP(), buildIMM32()) :
                                           new MOV(cpu, buildSP(), buildIMM16());
            },

            ( /* 0xbd: mov bp/ebp, imm16/32 */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildEBP(), buildIMM32()) :
                                           new MOV(cpu, buildBP(), buildIMM16());
            },

            ( /* 0xbe: mov si/esi, imm16/32 */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildESI(), buildIMM32()) :
                                           new MOV(cpu, buildSI(), buildIMM16());
            },

            ( /* 0xbf: mov di/edi, imm16/32 */ ) -> {

                return isOperandSize32() ? new MOV(cpu, buildEDI(), buildIMM32()) :
                                           new MOV(cpu, buildDI(), buildIMM16());
            },
            
            ( /* 0xc0: grp2 rm8, imm8 */ ) -> {
                
                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* ROL rm8, imm8 */
                    case 0: return new ROL8(cpu, buildRM8(), buildIMM8());

                    /* ROR rm8, imm8 */
                    case 1: return new ROR8(cpu, buildRM8(), buildIMM8());

                    /* RCL rm8, imm8 */
                    case 2: return new RCL8(cpu, buildRM8(), buildIMM8());

                    /* RCR rm8, imm8 */
                    case 3: return new RCR8(cpu, buildRM8(), buildIMM8());

                    /* SHL rm8, imm8 */
                    case 4: return new SHL8(cpu, buildRM8(), buildIMM8());

                    /* SHR rm8, imm8 */
                    case 5: return new SHR8(cpu, buildRM8(), buildIMM8());

                    /* SAL rm8, imm8 */
                    case 6: return new SHL8(cpu, buildRM8(), buildIMM8());

                    /* SAR rm8, imm8 */
                    case 7: return new SAR8(cpu, buildRM8(), buildIMM8());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xc1: grp2 rm16/32, imm8 */ ) -> {
                
                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* ROL rm16/32, imm8 */
                    case 0: return isOperandSize32() ? new ROL32(cpu, buildRM32(), buildIMM8()) :
                                                       new ROL16(cpu, buildRM16(), buildIMM8());
                        
                    /* ROR rm16/32, imm8 */
                    case 1: return isOperandSize32() ? new ROR32(cpu, buildRM32(), buildIMM8()) :
                                                       new ROR16(cpu, buildRM16(), buildIMM8());

                    /* RCL rm16/32, imm8 */
                    case 2: return isOperandSize32() ? new RCL32(cpu, buildRM32(), buildIMM8()) :
                                                       new RCL16(cpu, buildRM16(), buildIMM8());

                    /* RCR rm16/32, imm8 */
                    case 3: return isOperandSize32() ? new RCR32(cpu, buildRM32(), buildIMM8()) :
                                                       new RCR16(cpu, buildRM16(), buildIMM8());

                    /* SHL rm16/32, imm8 */
                    case 4: return isOperandSize32() ? new SHL32(cpu, buildRM32(), buildIMM8()) :
                                                       new SHL16(cpu, buildRM16(), buildIMM8());

                    /* SHR rm16/32, imm8 */
                    case 5: return isOperandSize32() ? new SHR32(cpu, buildRM32(), buildIMM8()) :
                                                       new SHR16(cpu, buildRM16(), buildIMM8());

                    /* SAL rm16/32, imm8 */
                    case 6: return isOperandSize32() ? new SHL32(cpu, buildRM32(), buildIMM8()) :
                                                       new SHL16(cpu, buildRM16(), buildIMM8());

                    /* SAR rm16/32, imm8 */
                    case 7: return isOperandSize32() ? new SAR32(cpu, buildRM32(), buildIMM8()) :
                                                       new SAR16(cpu, buildRM16(), buildIMM8());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xc2: ret imm16 */ ) -> {
                
                return finish(isOperandSize32() ? new RET_NEAR(cpu, buildIMM16(), true) :
                                                  new RET_NEAR(cpu, buildIMM16(), false));
            },
            
            ( /* 0xc3: ret */ ) -> {
                
                return finish(isOperandSize32() ? new RET_NEAR(cpu, null, true) :
                                                  new RET_NEAR(cpu, null, false));
            },

            ( /* 0xc4: les r16/32, m16/32 */ ) -> {

                decodeMODRM();
                
                if(m_mod == 3)
                    throw CPUException.getInvalidOpcode();
                
                return isOperandSize32() ? new LSEG(cpu, buildES(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                           new LSEG(cpu, buildES(), buildREG16(), buildMEM16(2), buildMEM16(0));
            },

            ( /* 0xc5: lds r16/32, m16/32 */ ) -> {
                
                decodeMODRM();
                
                if(m_mod == 3)
                    throw CPUException.getInvalidOpcode();
                
                return isOperandSize32() ? new LSEG(cpu, buildDS(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                           new LSEG(cpu, buildDS(), buildREG16(), buildMEM16(2), buildMEM16(0));
            },

            ( /* 0xc6: mov rm8, imm8 */ ) -> {

                decodeMODRM();
                
                return new MOV(cpu, buildRM8(), buildIMM8());
            },

            ( /* 0xc7: mov rm16/32, imm16/32 */ ) -> {
                
                decodeMODRM();

                return isOperandSize32() ? new MOV(cpu, buildRM32(), buildIMM32()) :
                                           new MOV(cpu, buildRM16(), buildIMM16());
            },

            ( /* 0xc8: enter imm16, imm8 */ ) -> {

                return isOperandSize32() ? new ENTER32(cpu, buildIMM16(), buildIMM8()) :
                                           new ENTER16(cpu, buildIMM16(), buildIMM8());
            },

            ( /* 0xc9: leave */ ) -> {

                return isOperandSize32() ? new LEAVE32(cpu) :
                                           new LEAVE16(cpu);
            },

            ( /* 0xca: retf imm16 */ ) -> {
                
                return finish(isOperandSize32() ? new RET_FAR(cpu, buildIMM16(), true) :
                                                  new RET_FAR(cpu, buildIMM16(), false));
            },

            ( /* 0xcb: retf */ ) -> {
                
                return finish(isOperandSize32() ? new RET_FAR(cpu, null, true) :
                                                  new RET_FAR(cpu, null, false));
            },

            ( /* 0xcc: int 3h */ ) -> {
                
                return finish(new INT(cpu, buildImmediate(3)));
            },

            ( /* 0xcd: int imm8 */ ) -> {
                
                return finish(new INT(cpu, buildIMM8()));
            },

            ( /* 0xce: into */ ) -> {

                return finish(new INTO(cpu));
            },

            ( /* 0xcf: iret */ ) -> {
                
                return finish(isOperandSize32() ? new IRET(cpu, true) :
                                                  new IRET(cpu, false));
            },

            ( /* 0xd0: grp2 rm8, 1 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* rol rm8, 1 */
                    case 0: return new ROL8(cpu, buildRM8(), buildImmediate(1));
                        
                    /* ror rm8, 1 */
                    case 1: return new ROR8(cpu, buildRM8(), buildImmediate(1));
                        
                    /* rcl rm8, 1 */
                    case 2: return new RCL8(cpu, buildRM8(), buildImmediate(1));
                        
                    /* rcr rm8, 1 */
                    case 3: return new RCR8(cpu, buildRM8(), buildImmediate(1));
                        
                    /* shl rm8, 1 */
                    case 4: return new SHL8(cpu, buildRM8(), buildImmediate(1));
                        
                    /* shr rm8, 1 */
                    case 5: return new SHR8(cpu, buildRM8(), buildImmediate(1));
                        
                    /* sal rm8, 1 */
                    case 6: return new SHL8(cpu, buildRM8(), buildImmediate(1));
                        
                    /* sar rm8, 1 */
                    case 7: return new SAR8(cpu, buildRM8(), buildImmediate(1));
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xd1: grp2 rm16/32, 1 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* rol rm16/32, 1 */
                    case 0: return isOperandSize32() ? new ROL32(cpu, buildRM32(), buildImmediate(1)) :
                                                       new ROL16(cpu, buildRM16(), buildImmediate(1));
                        
                    /* ror rm16/32, 1 */
                    case 1: return isOperandSize32() ? new ROR32(cpu, buildRM32(), buildImmediate(1)) :
                                                       new ROR16(cpu, buildRM16(), buildImmediate(1));
                        
                    /* rcl rm16/32, 1 */
                    case 2: return isOperandSize32() ? new RCL32(cpu, buildRM32(), buildImmediate(1)) :
                                                       new RCL16(cpu, buildRM16(), buildImmediate(1));
                        
                    /* rcr rm16/32, 1 */
                    case 3: return isOperandSize32() ? new RCR32(cpu, buildRM32(), buildImmediate(1)) :
                                                       new RCR16(cpu, buildRM16(), buildImmediate(1));
                        
                    /* shl rm16/32, 1 */
                    case 4: return isOperandSize32() ? new SHL32(cpu, buildRM32(), buildImmediate(1)) :
                                                       new SHL16(cpu, buildRM16(), buildImmediate(1));
                        
                    /* shr rm16/32, 1 */
                    case 5: return isOperandSize32() ? new SHR32(cpu, buildRM32(), buildImmediate(1)) :
                                                       new SHR16(cpu, buildRM16(), buildImmediate(1));
                        
                    /* sal rm16/32, 1 */
                    case 6: return isOperandSize32() ? new SHL32(cpu, buildRM32(), buildImmediate(1)) :
                                                       new SHL16(cpu, buildRM16(), buildImmediate(1));
                        
                    /* sar rm16/32, 1 */
                    case 7: return isOperandSize32() ? new SAR32(cpu, buildRM32(), buildImmediate(1)) :
                                                       new SAR16(cpu, buildRM16(), buildImmediate(1));
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xd2: grp2 rm8, cl */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* rol rm8, cl */
                    case 0: return new ROL8(cpu, buildRM8(), buildCL());
                        
                    /* ror rm8, cl */
                    case 1: return new ROR8(cpu, buildRM8(), buildCL());
                        
                    /* rcl rm8, cl */
                    case 2: return new RCL8(cpu, buildRM8(), buildCL());
                        
                    /* rcr rm8, cl */
                    case 3: return new RCR8(cpu, buildRM8(), buildCL());
                        
                    /* shl rm8, cl */
                    case 4: return new SHL8(cpu, buildRM8(), buildCL());
                        
                    /* shr rm8, cl */
                    case 5: return new SHR8(cpu, buildRM8(), buildCL());
                        
                    /* sal rm8, cl */
                    case 6: return new SHL8(cpu, buildRM8(), buildCL());
                        
                    /* sar rm8, cl */
                    case 7: return new SAR8(cpu, buildRM8(), buildCL());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xd3: grp2 rm16/32, cl */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* rol rm16/32, cl */
                    case 0: return isOperandSize32() ? new ROL32(cpu, buildRM32(), buildCL()) :
                                                       new ROL16(cpu, buildRM16(), buildCL());
                        
                    /* ror rm16/32, cl */
                    case 1: return isOperandSize32() ? new ROR32(cpu, buildRM32(), buildCL()) :
                                                       new ROR16(cpu, buildRM16(), buildCL());
                        
                    /* rcl rm16/32, cl */
                    case 2: return isOperandSize32() ? new RCL32(cpu, buildRM32(), buildCL()) :
                                                       new RCL16(cpu, buildRM16(), buildCL());
                        
                    /* rcr rm16/32, cl */
                    case 3: return isOperandSize32() ? new RCR32(cpu, buildRM32(), buildCL()) :
                                                       new RCR16(cpu, buildRM16(), buildCL());
                        
                    /* shl rm16/32, cl */
                    case 4: return isOperandSize32() ? new SHL32(cpu, buildRM32(), buildCL()) :
                                                       new SHL16(cpu, buildRM16(), buildCL());
                        
                    /* shr rm16/32, cl */
                    case 5: return isOperandSize32() ? new SHR32(cpu, buildRM32(), buildCL()) :
                                                       new SHR16(cpu, buildRM16(), buildCL());
                        
                    /* sal rm16/32, cl */
                    case 6: return isOperandSize32() ? new SHL32(cpu, buildRM32(), buildCL()) :
                                                       new SHL16(cpu, buildRM16(), buildCL());
                        
                    /* sar rm16/32, cl */
                    case 7: return isOperandSize32() ? new SAR32(cpu, buildRM32(), buildCL()) :
                                                       new SAR16(cpu, buildRM16(), buildCL());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xd4: aam */ ) -> {

                return new AAM(cpu, buildIMM8());
            },

            ( /* 0xd5: aad */ ) -> {

                return new AAD(cpu, buildIMM8());
            },

            ( /* 0xd6: salc */ ) -> {

                return new SALC(cpu);
            },

            ( /* 0xd7: xlat */ ) -> {

                return isAddressSize32() ? new XLAT32(cpu, getDefaultSegment()) :
                                           new XLAT16(cpu, getDefaultSegment());
            },

            ( /* 0xd8: escape */ ) -> {

                decodeMODRM();
                
                return new ESCAPE(cpu);
            },

            ( /* 0xd9: escape */ ) -> {

                decodeMODRM();
                
                return new ESCAPE(cpu);
            },

            ( /* 0xda: escape */ ) -> {

                decodeMODRM();
                
                return new ESCAPE(cpu);
            },

            ( /* 0xdb: escape */ ) -> {

                decodeMODRM();
                
                return new ESCAPE(cpu);
            },

            ( /* 0xdc: escape */ ) -> {

                decodeMODRM();
                
                return new ESCAPE(cpu);
            },

            ( /* 0xdd: escape */ ) -> {

                decodeMODRM();
                
                return new ESCAPE(cpu);
            },

            ( /* 0xde: escape */ ) -> {

                decodeMODRM();
                
                return new ESCAPE(cpu);
            },

            ( /* 0xdf: escape */ ) -> {

                decodeMODRM();
                
                return new ESCAPE(cpu);
            },

            ( /* 0xe0: loopnz rel8 */ ) -> {
                
                return finish(new LOOPNZ(cpu, buildIPRelativeIMM8(), getCounter()));
            },

            ( /* 0xe1: loopz rel8 */ ) -> {
                
                return finish(new LOOPZ(cpu, buildIPRelativeIMM8(), getCounter()));
            },

            ( /* 0xe2: loop rel8 */ ) -> {
                
                return finish(new LOOP(cpu, buildIPRelativeIMM8(), getCounter()));
            },
            
            ( /* 0xe3: jcxz/jecxz rel8 */ ) -> {
                
                return finish(new JMP_CONDITIONAL(cpu, buildIPRelativeIMM8(), buildConditionCTRZero()));
            },
            
            ( /* 0xe4: in al, imm8 */ ) -> {

                return new IN8(cpu, buildIMM8());
            },

            ( /* 0xe5: in ax/eax, imm8 */ ) -> {
                
                return isOperandSize32() ? new IN32(cpu, buildIMM8()) :
                                           new IN16(cpu, buildIMM8());
            },

            ( /* 0xe6: out imm8, al */ ) -> {

                return new OUT8(cpu, buildIMM8());
            },

            ( /* 0xe7: out imm8, ax/eax */ ) -> {

                return isOperandSize32() ? new OUT32(cpu, buildIMM8()) :
                                           new OUT16(cpu, buildIMM8());
            },

            ( /* 0xe8: call rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new CALL_NEAR(cpu, buildIPRelativeIMM32(), true) :
                                                  new CALL_NEAR(cpu, buildIPRelativeIMM16(), false));
            },
            
            ( /* 0xe9: jmp rel16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP(cpu, buildIPRelativeIMM32()) :
                                                  new JMP(cpu, buildIPRelativeIMM16()));
            },
            
            ( /* 0xea: jmp far imm16:16/32 */ ) -> {
                
                return finish(isOperandSize32() ? new JMP_FAR(cpu, buildIMM32(), buildIMM16(), true) :
                                                  new JMP_FAR(cpu, buildIMM16(), buildIMM16(), false));
            },

            ( /* 0xeb: jmp rel8 */ ) -> {
                
                return finish(new JMP(cpu, buildIPRelativeIMM8()));
            },

            ( /* 0xec: in al, dx */ ) -> {

                return new IN8(cpu, buildDX());
            },

            ( /* 0xed: in ax/eax, dx */ ) -> {

                return isOperandSize32() ? new IN32(cpu, buildDX()) :
                                           new IN16(cpu, buildDX());
            },

            ( /* 0xee: out dx, al */ ) -> {

                return new OUT8(cpu, buildDX());
            },

            ( /* 0xef: out dx, ax/eax */ ) -> {

                return isOperandSize32() ? new OUT32(cpu, buildDX()) :
                                           new OUT16(cpu, buildDX());
            },

            ( /* 0xf0: lock */ ) -> {

                m_prefixLock = true;
                
                return null;
            },

            ( /* 0xf1: int 1h */ ) -> {

                return finish(new INT(cpu, buildImmediate(1)));
            },

            ( /* 0xf2: repnz */ ) -> {

                m_prefixRepNZ = true;
                
                return null;
            },

            ( /* 0xf3: repz */ ) -> {

                m_prefixRepZ = true;
                
                return null;
            },

            ( /* 0xf4: halt */ ) -> {
                
                return finish(new HALT(cpu));
            },

            ( /* 0xf5: cmc */ ) -> {
                
                return new CMC(cpu);
            },

            ( /* 0xf6: GRP3a rm8 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* test rm8, imm8 */
                    case 0:
                    case 1: return new TEST8(cpu, buildRM8(), buildIMM8());
                    
                    /* not rm8 */
                    case 2: return new NOT(cpu, buildRM8());
                        
                    /* neg rm8 */
                    case 3: return new NEG8(cpu, buildRM8());
                        
                    /* mul al, rm8 */
                    case 4: return new MUL8(cpu, buildRM8());
                        
                    /* imul al, rm8 */
                    case 5: return new IMUL8(cpu, buildRM8());
                        
                    /* div al, rm8 */
                    case 6: return new DIV8(cpu, buildRM8());
                        
                    /* idiv al, rm8 */
                    case 7: return new IDIV8(cpu, buildRM8());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xf7: GRP3b rm16/32 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* test rm16/32, imm16/32 */
                    case 0:
                    case 1: return isOperandSize32() ? new TEST32(cpu, buildRM32(), buildIMM32()) :
                                                       new TEST16(cpu, buildRM16(), buildIMM16());
                    
                    /* not rm16/32 */
                    case 2: return isOperandSize32() ? new NOT(cpu, buildRM32()) :
                                                       new NOT(cpu, buildRM16());
                        
                    /* neg rm16/32 */
                    case 3: return isOperandSize32() ? new NEG32(cpu, buildRM32()) :
                                                       new NEG16(cpu, buildRM16());
                        
                    /* mul al, rm16/32 */
                    case 4: return isOperandSize32() ? new MUL32(cpu, buildRM32()) :
                                                       new MUL16(cpu, buildRM16());
                        
                    /* imul al, rm16/32 */
                    case 5: return isOperandSize32() ? new IMUL32(cpu, buildRM32()) :
                                                       new IMUL16(cpu, buildRM16());
                        
                    /* div al, rm16/32 */
                    case 6: return isOperandSize32() ? new DIV32(cpu, buildRM32()) :
                                                       new DIV16(cpu, buildRM16());
                        
                    /* idiv al, rm16/32 */
                    case 7: return isOperandSize32() ? new IDIV32(cpu, buildRM32()) :
                                                       new IDIV16(cpu, buildRM16());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xf8: clc */ ) -> {

                return new CLC(cpu);
            },

            ( /* 0xf9: stc */ ) -> {

                return new STC(cpu);
            },

            ( /* 0xfa: cli */ ) -> {

                return new CLI(cpu);
            },

            ( /* 0xfb: sti */ ) -> {
                
                return finish(new STI(cpu));
            },

            ( /* 0xfc: cld */ ) -> {

                return new CLD(cpu);
            },

            ( /* 0xfd: std */ ) -> {

                return new STD(cpu);
            },

            ( /* 0xfe: grp4 rm8 */ ) -> {

                decodeMODRM();

                switch(m_reg) {

                    /* inc rm8 */
                    case 0: return new INC8(cpu, buildRM8());

                    /* dec rm8 */
                    case 1: return new DEC8(cpu, buildRM8());

                    default:
                        throw CPUException.getInvalidOpcode();
                }
            },

            ( /* 0xff: grp5 rm16/32 */ ) -> {

                decodeMODRM();
                
                switch(m_reg) {
                    
                    /* inc rm16/32 */
                    case 0: return isOperandSize32() ? new INC32(cpu, buildRM32()) :
                                                       new INC16(cpu, buildRM16());
                        
                    /* dec rm16/32 */
                    case 1: return isOperandSize32() ? new DEC32(cpu, buildRM32()) :
                                                       new DEC16(cpu, buildRM16());
                        
                    /* call rm16/32 */
                    case 2:
                        return finish(isOperandSize32() ? new CALL_NEAR(cpu, buildRM32(), true) :
                                                          new CALL_NEAR(cpu, buildRM16(), false));
                        
                    /* call m16:16/32 */
                    case 3:
                        if(m_mod == 3)
                            throw CPUException.getInvalidOpcode();
                        
                        return finish(isOperandSize32() ? new CALL_FAR(cpu, buildMEM32(0), buildMEM16(4), true) :
                                                          new CALL_FAR(cpu, buildMEM16(0), buildMEM16(2), false));
                        
                    /* jmp rm16/32 */
                    case 4:
                        return finish(isOperandSize32() ? new JMP(cpu, buildRM32()) :
                                                          new JMP(cpu, buildRM16()));
                        
                    /* jmp m16:16/32 */
                    case 5:
                        if(m_mod == 3)
                            throw CPUException.getInvalidOpcode();
                        
                        return finish(isOperandSize32() ? new JMP_FAR(cpu, buildMEM32(0), buildMEM16(4), true) :
                                                          new JMP_FAR(cpu, buildMEM16(0), buildMEM16(2), false));
                        
                    /* push rm16/32 */
                    case 6:
                        return isOperandSize32() ? new PUSH32(cpu, buildRM32()) :
                                                   new PUSH16(cpu, buildRM16());
                        
                    default:
                        throw CPUException.getInvalidOpcode();
                }
            }
        };
        
        // </editor-fold>
        
        // </editor-fold>
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Some helper methods">
    
    private Instruction finish(Instruction instr) {
        
        m_decoderRunning = false;
        return instr;
    }
    
    private boolean isOperandSize32() {
        
        return m_cpu.CS.isSize32() ^ m_prefixOperandSizeOverride;
    }
    
    private boolean isAddressSize32() {
        
        return m_cpu.CS.isSize32() ^ m_prefixAddressSizeOverride;
    }
    
    private boolean isLeavingCurrentPage(int size) {
        
        return ((m_decoderOffset + size - 1) & 0xfffff000) != m_decoderPage;
    }
    
    private Register getCounter() {
        
        return isAddressSize32() ? m_cpu.ECX : m_cpu.CX;
    }
    
    private Register getSrcIndex() {
        
        return isAddressSize32() ? m_cpu.ESI : m_cpu.SI;
    }
    
    private Register getDestIndex() {
        
        return isAddressSize32() ? m_cpu.EDI : m_cpu.DI;
    }
    
    private Segment getDefaultSegment() {
        
        return getDefaultSegment(m_cpu.DS);
    }
    
    private Segment getDefaultSegment(Segment seg) {
        
        if(m_prefixSegmentOverride != null)
            return m_prefixSegmentOverride;
        
        return seg;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Decoding">
    // <editor-fold defaultstate="collapsed" desc="Reading of data at the instruction pointer">
    
    private int readIMM8() {
        
        // Leave the decoding phase if the next read crosses a page boundary
        if(isLeavingCurrentPage(1) && m_buffer.size() > 0)
            throw PAGECROSS_EXCEPTION;
        
        int data = m_cpu.readMEM8(m_cpu.CS, m_decoderOffset);
        
        m_decoderOffset += 1;
        if(!m_cpu.CS.isSize32())
            m_decoderOffset &= 0xffff;
        
        return data;
    }
    
    private int readIMM16() {
        
        // Leave the decoding phase if the next read crosses a page boundary
        if(isLeavingCurrentPage(2) && m_buffer.size() > 0)
            throw PAGECROSS_EXCEPTION;
        
        int data = m_cpu.readMEM16(m_cpu.CS, m_decoderOffset);
        
        m_decoderOffset += 2;
        if(!m_cpu.CS.isSize32())
            m_decoderOffset &= 0xffff;
        
        return data;
    }
    
    private int readIMM32() {
        
        // Leave the decoding phase if the next read crosses a page boundary
        if(isLeavingCurrentPage(4) && m_buffer.size() > 0)
            throw PAGECROSS_EXCEPTION;
        
        int data = m_cpu.readMEM32(m_cpu.CS, m_decoderOffset);
        
        m_decoderOffset += 4;
        if(!m_cpu.CS.isSize32())
            m_decoderOffset &= 0xffff;
        
        return data;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Decoding of a codeblock">
    
    public CodeBlock decodeCodeBlock() {
        
        int currentEIP = m_cpu.EIP.getValue();
        
        boolean isCacheable = true;
        try {
            
            //
            // Decode a block
            //
            m_buffer.clear();
            
            m_decoderOffset = currentEIP;
            m_decoderPage = currentEIP & 0xfffff000;
            m_decoderRunning = true;
            while(m_decoderRunning) {

                // Reset instruction prefixes
                m_prefixOperandSizeOverride = false;
                m_prefixAddressSizeOverride = false;
                m_prefixRepZ = false;
                m_prefixRepNZ = false;
                m_prefixLock = false;
                m_prefixWait = false;
                m_prefixSegmentOverride = null;
                m_hasReadModRM = false;
                m_decoderTableCurrent = m_decoderTable;
                
                
                // Decode instructions
                currentEIP = m_decoderOffset;
                
                Instruction instr = null;
                while(instr == null)
                    instr = m_decoderTableCurrent[readIMM8()].decode();
                
                
                // Handle REP, WAIT and LOCK prefixes
                if(m_prefixRepZ || m_prefixRepNZ) {
                    
                    if(instr instanceof MOVSB || instr instanceof MOVSW || instr instanceof MOVSD ||
                       instr instanceof STOSB || instr instanceof STOSW || instr instanceof STOSD ||
                       instr instanceof LODSB || instr instanceof LODSW || instr instanceof LODSD ||
                       instr instanceof OUTSB || instr instanceof OUTSW || instr instanceof OUTSD ||
                       instr instanceof INSB || instr instanceof INSW || instr instanceof INSD) {

                        instr = new REP(m_cpu, instr, getCounter());
                    }
                    else if(instr instanceof CMPSB || instr instanceof CMPSW || instr instanceof CMPSD ||
                            instr instanceof SCASB || instr instanceof SCASW || instr instanceof SCASD) {

                        if(m_prefixRepZ)
                            instr = new REPZ(m_cpu, instr, getCounter());
                        else
                            instr = new REPNZ(m_cpu, instr, getCounter());
                    }
                    else {
                        
                        throw CPUException.getInvalidOpcode();
                    }
                }
                if(m_prefixWait) instr = new WAIT(m_cpu, instr);
                if(m_prefixLock) instr = new LOCK(m_cpu, instr);
                
                
                // Add instruction
                instr.setEIP(currentEIP, m_decoderOffset);
                m_buffer.add(instr);
            }
        }
        
        // An instruction generated an exception. We wrap this exception
        // and add it as an instruction to the block. The current block
        // will then come to an end. Blocks that throw wrapped exceptions
        // will not get cached!
        catch(CPUException ex) {
            
            isCacheable = false;
            
            Instruction instr = new EXCEPTION_WRAPPER(m_cpu, ex);
            instr.setEIP(currentEIP, -1);
            m_buffer.add(instr);
        }
        
        // An instruction crossed the boundary of the current page. The
        // current block has to end now, as we dont't know if the other page
        // is currently in memory. The instruction that crosses the page
        // boundary will then get decoded in the next run.
        catch(DecoderPageCrossException ex) {
        }
        
        // Some other horrible error in the decoder happened...
        catch(NullPointerException | IllegalArgumentException | IllegalStateException ex) {
            
            ex.printStackTrace(System.out);
            System.exit(0);
        }
        
        int physicalAddressBegin = m_mmu.getPhysicalAddress(m_cpu.CS.getBase() + m_cpu.EIP.getValue(), false, true);
        int physicalAddressEnd = m_mmu.getPhysicalAddress(m_cpu.CS.getBase() + m_decoderOffset - 1, false, true);
        
        return new CodeBlock(
                
            m_cpu,
            m_cpu.CS.getSelector(),
            m_cpu.EIP.getValue(),
            physicalAddressBegin,
            physicalAddressEnd,
            m_cpu.CS.isSize32(),
            m_cpu.SS.isSize32(),
            isCacheable,
            m_buffer.toArray(new Instruction[m_buffer.size()])
        );
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Decoding of the MOD R/M byte">
    
    private void decodeMODRM() {
        
        if(m_hasReadModRM)
            throw new IllegalStateException("MOD/RM byte is already decoded");
        
        m_hasReadModRM = true;
        
        // Read MOD/RM byte
        int data = readIMM8();
        
        m_mod = (data >> 6) & 0x03;
        m_reg = (data >> 3) & 0x07;
        m_rm = data & 0x07;
        
        if(m_mod == 3)
            return;
        
        // Decode MOD/RM byte
        if(isAddressSize32()) {

            if(m_rm == 4) {

                data = readIMM8();

                m_scale = (data >> 6) & 0x03;
                m_index = (data >> 3) & 0x07;
                m_base = data & 0x07;
                
                if(m_base == 5 && m_mod == 0) {

                    m_addrSeg = getDefaultSegment();
                    m_addrBase = null;
                    m_addrDisp = readIMM32();
                }
                else {

                    m_addrSeg = getDefaultSegment(m_addrSegs32[m_base]);
                    m_addrBase = m_addrBaseIndexRegs32[m_base];

                    switch(m_mod) {

                        case 0: m_addrDisp = 0; break;
                        case 1: m_addrDisp = signExtend8To32(readIMM8()); break;
                        case 2: m_addrDisp = readIMM32(); break;
                    }
                }

                m_addrIdx = m_index != 4 ? m_addrBaseIndexRegs32[m_index] : null;
            }
            else {

                if(m_rm == 5 && m_mod == 0) {

                    m_addrSeg = getDefaultSegment();
                    m_addrBase = null;
                    m_addrIdx = null;
                    m_addrDisp = readIMM32();
                }
                else {

                    m_addrSeg = getDefaultSegment(m_addrSegs32[m_rm]);
                    m_addrBase = m_addrBaseIndexRegs32[m_rm];
                    m_addrIdx = null;

                    switch(m_mod) {

                        case 0: m_addrDisp = 0; break;
                        case 1: m_addrDisp = signExtend8To32(readIMM8()); break;
                        case 2: m_addrDisp = readIMM32(); break;
                    }
                }
            }
        }
        else {

            if(m_rm == 6 && m_mod == 0) {

                m_addrSeg = getDefaultSegment();
                m_addrBase = null;
                m_addrIdx = null;
                m_addrDisp = readIMM16();
            }
            else {

                m_addrSeg = getDefaultSegment(m_addrSegs16[m_rm]);
                m_addrBase = m_addrBaseRegs16[m_rm];
                m_addrIdx = m_addrIndexRegs16[m_rm];
                
                switch(m_mod) {

                    case 0: m_addrDisp = 0; break;
                    case 1: m_addrDisp = signExtend8To16(readIMM8()); break;
                    case 2: m_addrDisp = readIMM16(); break;
                }
            }
        }
    }
    
    // </editor-fold>
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Builder">
    
    // <editor-fold defaultstate="collapsed" desc="Pointer builder">
    
    private Pointer buildPointer32(int displacement) {
        
        if(m_addrBase != null) {
            
            if(m_addrIdx != null) {
                
                if(displacement != 0) {
                    
                    return m_scale != 0 ? new Pointer32BaseIndexScaleDisplacement(m_addrBase, m_addrIdx, m_scale, displacement) :
                                          new Pointer32BaseIndexDisplacement(m_addrBase, m_addrIdx, displacement);
                }
                else {
                    
                    return m_scale != 0 ? new Pointer32BaseIndexScale(m_addrBase, m_addrIdx, m_scale) :
                                          new Pointer32BaseIndex(m_addrBase, m_addrIdx);
                }
            }
            else {
                
                return displacement != 0 ? new Pointer32BaseDisplacement(m_addrBase, displacement) :
                                           new Pointer32Base(m_addrBase);
            }
        }
        else {
            
            if(m_addrIdx != null) {
                
                if(displacement != 0) {

                    return m_scale != 0 ? new Pointer32IndexScaleDisplacement(m_addrIdx, m_scale, displacement) :
                                          new Pointer32BaseDisplacement(m_addrIdx, displacement);
                }
                else {

                    return m_scale != 0 ? new Pointer32IndexScale(m_addrIdx, m_scale) :
                                          new Pointer32Base(m_addrIdx);
                }
            }
            else {
                
                return new Pointer32Displacement(displacement);
            }
        }
    }
    
    private Pointer buildPointer16(int displacement) {
        
        if(m_addrBase != null) {
            
            if(m_addrIdx != null) {
                
                return displacement != 0 ? new Pointer16BaseIndexDisplacement(m_addrBase, m_addrIdx, displacement) :
                                           new Pointer16BaseIndex(m_addrBase, m_addrIdx);
            }
            else {
                
                return displacement != 0 ? new Pointer16BaseDisplacement(m_addrBase, displacement) :
                                           new Pointer16Base(m_addrBase);
            }
        }
        
        return new Pointer16Displacement(displacement);
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
        
        if(isOperandSize32())
            return buildImmediate(signExtend8To32(readIMM8()));
        else
            return buildImmediate(signExtend8To16(readIMM8()));
    }
    
    private Operand buildIMM16() {
        
        return buildImmediate(readIMM16());
    }
    
    private Operand buildIMM32() {
        
        return buildImmediate(readIMM32());
    }
    
    private Operand buildIPRelativeIMM8() {
        
        int relative = readIMM8();
        
        if(isOperandSize32())
            return buildImmediate(m_decoderOffset + signExtend8To32(relative));
        else
            return buildImmediate((m_decoderOffset + signExtend8To16(relative)) & 0xffff);
    }
    
    private Operand buildIPRelativeIMM16() {
        
        int relative = readIMM16();
        
        return buildImmediate((m_decoderOffset + relative) & 0xffff);
    }
    
    private Operand buildIPRelativeIMM32() {
        
        int relative = readIMM32();
        
        return buildImmediate(m_decoderOffset + relative);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Register operand builder">
    // <editor-fold defaultstate="collapsed" desc="General purpose register">
    
    private Operand buildREG(int size, int reg) {
        
        switch(size) {
            
            case 8: return new OperandRegister(m_reg8[reg]);
            case 16: return new OperandRegister(m_reg16[reg]);
            case 32: return new OperandRegister(m_reg32[reg]);
            
            default:
                throw new IllegalArgumentException(String.format("Illegal register size of %d specified", size));
        }
    }
    
    private Operand buildAH() {
        
        return new OperandRegister(m_cpu.AH);
    }
    
    private Operand buildBH() {
        
        return new OperandRegister(m_cpu.BH);
    }
    
    private Operand buildCH() {
        
        return new OperandRegister(m_cpu.CH);
    }
    
    private Operand buildDH() {
        
        return new OperandRegister(m_cpu.DH);
    }
    
    private Operand buildAL() {
        
        return new OperandRegister(m_cpu.AL);
    }
    
    private Operand buildBL() {
        
        return new OperandRegister(m_cpu.BL);
    }
    
    private Operand buildCL() {
        
        return new OperandRegister(m_cpu.CL);
    }
    
    private Operand buildDL() {
        
        return new OperandRegister(m_cpu.DL);
    }
    
    private Operand buildAX() {
        
        return new OperandRegister(m_cpu.AX);
    }
    
    private Operand buildBX() {
        
        return new OperandRegister(m_cpu.BX);
    }
    
    private Operand buildCX() {
        
        return new OperandRegister(m_cpu.CX);
    }
    
    private Operand buildDX() {
        
        return new OperandRegister(m_cpu.DX);
    }
    
    private Operand buildSP() {
        
        return new OperandRegister(m_cpu.SP);
    }
    
    private Operand buildBP() {
        
        return new OperandRegister(m_cpu.BP);
    }
    
    private Operand buildSI() {
        
        return new OperandRegister(m_cpu.SI);
    }
    
    private Operand buildDI() {
        
        return new OperandRegister(m_cpu.DI);
    }
    
    private Operand buildEAX() {
        
        return new OperandRegister(m_cpu.EAX);
    }
    
    private Operand buildEBX() {
        
        return new OperandRegister(m_cpu.EBX);
    }
    
    private Operand buildECX() {
        
        return new OperandRegister(m_cpu.ECX);
    }
    
    private Operand buildEDX() {
        
        return new OperandRegister(m_cpu.EDX);
    }
    
    private Operand buildESP() {
        
        return new OperandRegister(m_cpu.ESP);
    }
    
    private Operand buildEBP() {
        
        return new OperandRegister(m_cpu.EBP);
    }
    
    private Operand buildESI() {
        
        return new OperandRegister(m_cpu.ESI);
    }
    
    private Operand buildEDI() {
        
        return new OperandRegister(m_cpu.EDI);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Control register">
    
    private Operand buildCR0() {
        
        return new OperandCR0(m_cpu);
    }
    
    private Operand buildCR2() {
        
        return new OperandCR2(m_cpu);
    }
    
    private Operand buildCR3() {
        
        return new OperandCR3(m_cpu);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Debug register">
    
    private Operand buildDR0() {
        
        return new OperandRegister(m_cpu.DR0);
    }
    
    private Operand buildDR1() {
        
        return new OperandRegister(m_cpu.DR1);
    }
    
    private Operand buildDR2() {
        
        return new OperandRegister(m_cpu.DR2);
    }
    
    private Operand buildDR3() {
        
        return new OperandRegister(m_cpu.DR3);
    }
    
    private Operand buildDR6() {
        
        return new OperandRegister(m_cpu.DR6);
    }
    
    private Operand buildDR7() {
        
        return new OperandRegister(m_cpu.DR7);
    
    }
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Test register">
    
    private Operand buildTR6() {
        
        return new OperandRegister(m_cpu.TR6);
    }
    
    private Operand buildTR7() {
        
        return new OperandRegister(m_cpu.TR7);
    }
    
    // </editor-fold>
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Register / memory operand builder">
    
    private Operand buildREG8() {
        
        if(!m_hasReadModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        return buildREG(8, m_reg);
    }
    
    private Operand buildREG16() {
        
        if(!m_hasReadModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        return buildREG(16, m_reg);
    }
    
    private Operand buildREG32() {
        
        if(!m_hasReadModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        return buildREG(32, m_reg);
    }
    
    private Operand buildRM8() {
        
        if(!m_hasReadModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        if(m_mod == 3)
            return buildREG(8, m_rm);
        else
            return buildMEM(8, 0);
    }
    
    private Operand buildRM16() {
        
        if(!m_hasReadModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        if(m_mod == 3)
            return buildREG(16, m_rm);
        else
            return buildMEM(16, 0);
    }
    
    private Operand buildRM32() {
        
        return buildRM32(false);
    }
    
    private Operand buildRM32(boolean limitMemTo16) {
        
        if(!m_hasReadModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        if(m_mod == 3)
            return buildREG(32, m_rm);
        else if(limitMemTo16)
            return buildMEM(16, 0);
        else
            return buildMEM(32, 0);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Memory operand builder">
    
    private OperandMemory buildMEM(int size, int extraDisplacement) {
        
        int disp = m_addrDisp + extraDisplacement;
        
        Pointer ptr = isAddressSize32() ? buildPointer32(disp) :
                                          buildPointer16(disp);
        
        // Create memory operand
        switch(size) {
            
            case 8: return new OperandMemory8(m_cpu, m_addrSeg, ptr);
            case 16: return new OperandMemory16(m_cpu, m_addrSeg, ptr);
            case 32: return new OperandMemory32(m_cpu, m_addrSeg, ptr);
                
            default:
                throw new IllegalArgumentException("Illegal memory operand size specified");
        }
    }
    
    private OperandAddress buildADDR(int extraDisplacement) {
        
        int disp = m_addrDisp + extraDisplacement;
        
        Pointer ptr = isAddressSize32() ? buildPointer32(disp) :
                                          buildPointer16(disp);
        
        return new OperandAddress(ptr);
    }
    
    private OperandMemory buildMEM16(int extraDisplacement) {
        
        return buildMEM(16, extraDisplacement);
    }
    
    private OperandMemory buildMEM32(int extraDisplacement) {
        
        return buildMEM(32, extraDisplacement);
    }
    
    private Operand buildMOFFS8() {
        
        Pointer ptr = isAddressSize32() ? new Pointer32Displacement(readIMM32()) :
                                          new Pointer16Displacement(readIMM16());
        
        return new OperandMemory8(m_cpu, getDefaultSegment(), ptr);
    } 
    
    private Operand buildMOFFS16() {
        
        Pointer ptr = isAddressSize32() ? new Pointer32Displacement(readIMM32()) :
                                          new Pointer16Displacement(readIMM16());
        
        return new OperandMemory16(m_cpu, getDefaultSegment(), ptr);
    }
    
    private Operand buildMOFFS32() {
        
        Pointer ptr = isAddressSize32() ? new Pointer32Displacement(readIMM32()) :
                                          new Pointer16Displacement(readIMM16());
        
        return new OperandMemory32(m_cpu, getDefaultSegment(), ptr);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Segment operand builder">
    // <editor-fold defaultstate="collapsed" desc="Based on the MOD r/m byte">
    
    private Operand buildSEG(boolean isDestination) {
        
        if(!m_hasReadModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        if(isDestination && m_reg == SEGMENT_CS)
            throw CPUException.getInvalidOpcode();
        
        switch(m_reg) {
            
            case SEGMENT_ES: return buildES();
            case SEGMENT_CS: return buildCS();
            case SEGMENT_SS: return buildSS();
            case SEGMENT_DS: return buildDS();
            case SEGMENT_FS: return buildFS();
            case SEGMENT_GS: return buildGS();
            
            default:
                throw new IllegalArgumentException("Invalid register specified");
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Direct">
    
    private Operand buildCS() {
        
        return new OperandCodeSegment(m_cpu);
    }

    private Operand buildDS() {
        
        return new OperandDataSegment(m_cpu, m_cpu.DS);
    }
    
    private Operand buildES() {
        
        return new OperandDataSegment(m_cpu, m_cpu.ES);
    }
    
    private Operand buildFS() {
        
        return new OperandDataSegment(m_cpu, m_cpu.FS);
    }
    
    private Operand buildGS() {
        
        return new OperandDataSegment(m_cpu, m_cpu.GS);
    }
    
    private Operand buildSS() {
        
        return new OperandStackSegment(m_cpu);
    }
    
    // </editor-fold>
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Condition builder">
    
    private Condition buildConditionOverflow() {
        
        return new ConditionOverflow(m_cpu);
    }
    
    private Condition buildConditionNotOverflow() {
        
        return new ConditionNotOverflow(m_cpu);
    }
    
    private Condition buildConditionBellow() {
        
        return new ConditionBellow(m_cpu);
    }
    
    private Condition buildConditionNotBellow() {
        
        return new ConditionNotBellow(m_cpu);
    }
    
    private Condition buildConditionZero() {
        
        return new ConditionZero(m_cpu);
    }
    
    private Condition buildConditionNotZero() {
        
        return new ConditionNotZero(m_cpu);
    }
    
    private Condition buildConditionBellowOrEqual() {
        
        return new ConditionBellowOrEqual(m_cpu);
    }
    
    private Condition buildConditionNotBellowOrEqual() {
        
        return new ConditionNotBellowOrEqual(m_cpu);
    }
    
    private Condition buildConditionSign() {
        
        return new ConditionSign(m_cpu);
    }
    
    private Condition buildConditionNotSign() {
        
        return new ConditionNotSign(m_cpu);
    }
    
    private Condition buildConditionParity() {
        
        return new ConditionParity(m_cpu);
    }
    
    private Condition buildConditionNotParity() {
        
        return new ConditionNotParity(m_cpu);
    }
    
    private Condition buildConditionLess() {
        
        return new ConditionLess(m_cpu);
    }
    
    private Condition buildConditionNotLess() {
        
        return new ConditionNotLess(m_cpu);
    }
    
    private Condition buildConditionLessOrEqual() {
        
        return new ConditionLessOrEqual(m_cpu);
    }
    
    private Condition buildConditionNotLessOrEqual() {
        
        return new ConditionNotLessOrEqual(m_cpu);
    }
    
    private Condition buildConditionCTRZero() {
        
        return new ConditionCTRZero(getCounter());
    }
    
    // </editor-fold>
    
    // </editor-fold>
}
