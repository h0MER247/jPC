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
import Hardware.CPU.Intel80386.Instructions.i486.*;
import Hardware.CPU.Intel80386.Instructions.FPU.Arithmetic.*;
import Hardware.CPU.Intel80386.Instructions.FPU.Datatransfer.*;
import Hardware.CPU.Intel80386.Instructions.FPU.Misc.*;
import Hardware.CPU.Intel80386.Codeblock.CodeBlock;
import Hardware.CPU.Intel80386.Condition.Condition;
import Hardware.CPU.Intel80386.Condition.Conditions.*;
import Hardware.CPU.Intel80386.Exceptions.CPUException;
import Hardware.CPU.Intel80386.Instructions.Instruction;
import Hardware.CPU.Intel80386.Intel80386;
import Hardware.CPU.Intel80386.Intel80386.CPUType;
import Hardware.CPU.Intel80386.MMU.MMU;
import Hardware.CPU.Intel80386.Operands.Immediate.OperandImmediate;
import Hardware.CPU.Intel80386.Operands.Memory.OperandMemory;
import Hardware.CPU.Intel80386.Pointer.Pointer;
import Hardware.CPU.Intel80386.Register.General.*;
import Hardware.CPU.Intel80386.Register.General.Register;
import Hardware.CPU.Intel80386.Register.Segments.Segment;
import Hardware.CPU.Intel80386.Operands.FPU.*;
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
    private final InstructionDecoder[] m_decoderTableDefault;
    private final InstructionDecoder[] m_decoderTable0F;
    
    /* ----------------------------------------------------- *
     * MOD/RM / SIB byte decoding                            *
     * ----------------------------------------------------- */
    private int m_modRMByte;
    private int m_mod;
    private int m_reg;
    private int m_rm;
    private int m_scale;
    private int m_base;
    private int m_index;
    private boolean m_hasDecodedModRM;
    
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
     * Prefixes                                              *
     * ----------------------------------------------------- */
    private boolean m_prefixRepZ;
    private boolean m_prefixRepNZ;
    private boolean m_prefixOperandSizeOverride;
    private boolean m_prefixAddressSizeOverride;
    private Segment m_prefixSegmentOverride;
    private boolean m_prefixLock;
    private boolean m_prefixWait;
    
    /* ----------------------------------------------------- *
     * Decoder buffer                                        *
     * ----------------------------------------------------- */
    private boolean m_decoderRunning;
    private int m_decoderPage;
    private int m_decoderOffset;
    private final ArrayList<Instruction> m_buffer;
    
    /* ----------------------------------------------------- *
     * Whether or not a fpu instruction was decoded          *
     * ----------------------------------------------------- */
    private boolean m_hasDecodedFPUInstruction;
    
    /* ----------------------------------------------------- *
     * References to the cpu and mmu                         *
     * ----------------------------------------------------- */
    private final Intel80386 m_cpu;
    private final MMU m_mmu;
    
    
    
    public Decoder(Intel80386 cpu) {
        
        m_cpu = cpu;
        m_mmu = cpu.getMMU();
        
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
        
        // Initialize decoder tables
        m_decoderTableDefault = new InstructionDecoder[256];
        fillDefaultTable();
        
        m_decoderTable0F = new InstructionDecoder[256];
        fillExtendedTable();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Some helper methods">
    
    private void requiresCPUOfAtLeast(CPUType type) {
        
        if(type.ordinal() > m_cpu.getCPUType().ordinal())
            throw CPUException.getInvalidOpcode();
    }
    
    private Instruction finishBlock(Instruction instr) {
        
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
        
        Instruction instr;
        int instrEIP = m_cpu.EIP.getValue();
        
        boolean isFPUEscaped = false;
        boolean isCacheable = true;
        try {
            
            //
            // Decode a block
            //
            m_buffer.clear();
            
            m_decoderOffset = instrEIP;
            m_decoderPage = instrEIP & 0xfffff000;
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
                m_hasDecodedModRM = false;
                m_hasDecodedFPUInstruction = false;
                
                // Decode one instruction
                instrEIP = m_decoderOffset;
                instr = m_decoderTableDefault[readIMM8()].decode();
                
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
                
                // TODO: Not all instructions are allowed to have those prefixes
                //       (even if they essentially do nothing at the moment)
                if(m_prefixWait) instr = new WAIT(m_cpu, instr);
                if(m_prefixLock) instr = new LOCK(m_cpu, instr);
                
                // Escape FPU instruction
                if(m_hasDecodedFPUInstruction && !isFPUEscaped) {
                    
                    isFPUEscaped = true;
                    
                    Instruction escape = new ESCAPE(m_cpu);
                    escape.setEIP(instrEIP, m_decoderOffset);
                    m_buffer.add(escape);
                }
                
                // Add instruction
                instr.setEIP(instrEIP, m_decoderOffset);
                m_buffer.add(instr);
            }
        }
        
        // An instruction generated an exception. We wrap this exception
        // and add it as an instruction to the block. The current block
        // will then come to an end. Blocks that throw wrapped exceptions
        // will not get cached!
        catch(CPUException ex) {
            
            isCacheable = false;
            
            instr = new EXCEPTION_WRAPPER(m_cpu, ex);
            instr.setEIP(instrEIP, -1);
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
        
        if(m_hasDecodedModRM)
            throw new IllegalStateException("MOD/RM byte is already decoded");
        
        m_hasDecodedModRM = true;
        
        // Decode MOD/RM byte
        int data = readIMM8();
        
        m_modRMByte = data;
        m_mod = (data >> 6) & 0x03;
        m_reg = (data >> 3) & 0x07;
        m_rm = data & 0x07;
        
        if(m_mod == 3)
            return;
        
        if(isAddressSize32()) {

            if(m_rm == 4) {

                int sib = readIMM8();

                m_scale = (sib >> 6) & 0x03;
                m_index = (sib >> 3) & 0x07;
                m_base = sib & 0x07;

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
        
        if(!m_hasDecodedModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        return buildREG(8, m_reg);
    }
    
    private Operand buildREG16() {
        
        if(!m_hasDecodedModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        return buildREG(16, m_reg);
    }
    
    private Operand buildREG32() {
        
        if(!m_hasDecodedModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        return buildREG(32, m_reg);
    }
    
    private Operand buildRM8() {
        
        if(!m_hasDecodedModRM)
            throw new IllegalArgumentException("ModRM byte needs to be decoded first");
        
        if(m_mod == 3)
            return buildREG(8, m_rm);
        else
            return buildMEM(8, 0);
    }
    
    private Operand buildRM16() {
        
        if(!m_hasDecodedModRM)
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
        
        if(!m_hasDecodedModRM)
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
        
        if(!m_hasDecodedModRM)
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
    // <editor-fold defaultstate="collapsed" desc="FPU specific operands">
    
    private OperandFPU buildMEMFloatingPoint(int size) {
        
        Pointer ptr = isAddressSize32() ? buildPointer32(m_addrDisp) :
                                          buildPointer16(m_addrDisp);
        
        switch(size) {
            
            case 32: return new OperandM32Float(m_cpu, m_addrSeg, ptr);
            case 64: return new OperandM64Double(m_cpu, m_addrSeg, ptr);
            case 80: return new OperandM80ExtendedDouble(m_cpu, m_addrSeg, ptr);
            
            default:
                throw new IllegalArgumentException("Invalid size specified");
        }
    }
    
    private OperandFPU buildMEMInteger(int size) {
        
        Pointer ptr = isAddressSize32() ? buildPointer32(m_addrDisp) :
                                          buildPointer16(m_addrDisp);
        
        switch(size) {
            
            case 16: return new OperandM16Integer(m_cpu, m_addrSeg, ptr);
            case 32: return new OperandM32Integer(m_cpu, m_addrSeg, ptr);
            case 64: return new OperandM64Integer(m_cpu, m_addrSeg, ptr);
            
            default:
                throw new IllegalArgumentException("Invalid size specified");
        }
    }
    
    private OperandFPU buildMEMBinaryCodedDecimal() {
        
        if(isAddressSize32())
            return new OperandM80BCD(m_cpu, m_addrSeg, buildPointer32(m_addrDisp));
        else
            return new OperandM80BCD(m_cpu, m_addrSeg, buildPointer16(m_addrDisp));
    }
    
    private OperandFPU buildST(int index) {
        
        return new OperandST(m_cpu, index);
    }
    
    private OperandFPU buildST0() {
        
        return buildST(0);
    }
    
    private OperandFPU buildST1() {
        
        return buildST(1);
    }
    
    private OperandFPU buildSTi() {
        
        return buildST(m_rm);
    }
    
    // </editor-fold>
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Decoder tables">
    
    // <editor-fold defaultstate="collapsed" desc="Default table">
    
    private void fillDefaultTable() {
        
        m_decoderTableDefault[0x00] = ( /* add rm8, r8 */ ) -> {

            decodeMODRM();

            return new ADD8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTableDefault[0x01] = ( /* add rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new ADD32(m_cpu, buildRM32(), buildREG32()) :
                                       new ADD16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTableDefault[0x02] = ( /* add r8, rm8 */ ) -> {

            decodeMODRM();

            return new ADD8(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x03] = ( /* add r16, rm16 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new ADD32(m_cpu, buildREG32(), buildRM32()) :
                                       new ADD16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x04] = ( /* add al, imm8 */ ) -> {

            return new ADD8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0x05] = ( /* add ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new ADD32(m_cpu, buildEAX(), buildIMM32()) :
                                       new ADD16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0x06] = ( /* push es */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildES()) :
                                       new PUSH16(m_cpu, buildES());
        };

        m_decoderTableDefault[0x07] = ( /* pop es */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildES()) :
                                       new POP16(m_cpu, buildES());
        };

        m_decoderTableDefault[0x08] = ( /* or rm8, r8 */ ) -> {

            decodeMODRM();

            return new OR8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTableDefault[0x09] = ( /* or rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new OR32(m_cpu, buildRM32(), buildREG32()) :
                                       new OR16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTableDefault[0x0a] = ( /* or r8, rm8 */ ) -> {

            decodeMODRM();

            return new OR8(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x0b] = ( /* or r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new OR32(m_cpu, buildREG32(), buildRM32()) :
                                       new OR16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x0c] = ( /* or al, imm8 */ ) -> {

            return new OR8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0x0d] = ( /* or ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new OR32(m_cpu, buildEAX(), buildIMM32()) :
                                       new OR16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0x0e] = ( /* push cs */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildCS()) :
                                       new PUSH16(m_cpu, buildCS());
        };

        m_decoderTableDefault[0x0f] = ( /* Extended opcode table */ ) -> {

            return m_decoderTable0F[readIMM8()].decode();
        };

        m_decoderTableDefault[0x10] = ( /* adc rm8, r8 */ ) -> {

            decodeMODRM();

            return new ADC8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTableDefault[0x11] = ( /* adc rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new ADC32(m_cpu, buildRM32(), buildREG32()) :
                                       new ADC16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTableDefault[0x12] = ( /* adc r8, rm8 */ ) -> {

            decodeMODRM();

            return new ADC8(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x13] = ( /* adc r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new ADC32(m_cpu, buildREG32(), buildRM32()) :
                                       new ADC16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x14] = ( /* adc al, imm8 */ ) -> {

            return new ADC8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0x15] = ( /* adc ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new ADC32(m_cpu, buildEAX(), buildIMM32()) :
                                       new ADC16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0x16] = ( /* push ss */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildSS()) :
                                       new PUSH16(m_cpu, buildSS());
        };

        m_decoderTableDefault[0x17] = ( /* pop ss */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildSS()) :
                                       new POP16(m_cpu, buildSS());
        };

        m_decoderTableDefault[0x18] = ( /* sbb rm8, r8 */ ) -> {

            decodeMODRM();

            return new SBB8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTableDefault[0x19] = ( /* sbb rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new SBB32(m_cpu, buildRM32(), buildREG32()) :
                                       new SBB16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTableDefault[0x1a] = ( /* sbb r8, rm8 */ ) -> {

            decodeMODRM();

            return new SBB8(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x1b] = ( /* sbb r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new SBB32(m_cpu, buildREG32(), buildRM32()) :
                                       new SBB16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x1c] = ( /* sbb al, imm8 */ ) -> {

            return new SBB8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0x1d] = ( /* sbb ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new SBB32(m_cpu, buildEAX(), buildIMM32()) :
                                       new SBB16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0x1e] = ( /* push ds */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildDS()) :
                                       new PUSH16(m_cpu, buildDS());
        };

        m_decoderTableDefault[0x1f] = ( /* pop ds */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildDS()) :
                                       new POP16(m_cpu, buildDS());
        };

        m_decoderTableDefault[0x20] = ( /* and rm8, r8 */ ) -> {

            decodeMODRM();

            return new AND8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTableDefault[0x21] = ( /* and rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new AND32(m_cpu, buildRM32(), buildREG32()) :
                                       new AND16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTableDefault[0x22] = ( /* and r8, rm8 */ ) -> {

            decodeMODRM();

            return new AND8(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x23] = ( /* and r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new AND32(m_cpu, buildREG32(), buildRM32()) :
                                       new AND16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x24] = ( /* and al, imm8 */ ) -> {

            return new AND8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0x25] = ( /* and ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new AND32(m_cpu, buildEAX(), buildIMM32()) :
                                       new AND16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0x26] = ( /* es: */ ) -> {

            m_prefixSegmentOverride = m_cpu.ES;

            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x27] = ( /* daa */ ) -> {

            return new DAA(m_cpu);
        };

        m_decoderTableDefault[0x28] = ( /* sub rm8, r8 */ ) -> {

            decodeMODRM();

            return new SUB8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTableDefault[0x29] = ( /* sub rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new SUB32(m_cpu, buildRM32(), buildREG32()) :
                                       new SUB16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTableDefault[0x2a] = ( /* sub r8, rm8 */ ) -> {

            decodeMODRM();

            return new SUB8(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x2b] = ( /* sub r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new SUB32(m_cpu, buildREG32(), buildRM32()) :
                                       new SUB16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x2c] = ( /* sub al, imm8 */ ) -> {

            return new SUB8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0x2d] = ( /* sub ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new SUB32(m_cpu, buildEAX(), buildIMM32()) :
                                       new SUB16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0x2e] = ( /* cs: */ ) -> {
            
            m_prefixSegmentOverride = m_cpu.CS;

            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x2f] = ( /* das */ ) -> {

            return new DAS(m_cpu);
        };

        m_decoderTableDefault[0x30] = ( /* xor rm8, r8 */ ) -> {

            decodeMODRM();

            return new XOR8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTableDefault[0x31] = ( /* xor rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new XOR32(m_cpu, buildRM32(), buildREG32()) :
                                       new XOR16(m_cpu, buildRM16(), buildREG16());
        };
        
        m_decoderTableDefault[0x32] = ( /* xor r8, rm8 */ ) -> {

            decodeMODRM();

            return new XOR8(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x33] = ( /* xor r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new XOR32(m_cpu, buildREG32(), buildRM32()) :
                                       new XOR16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x34] = ( /* xor al, imm8 */ ) -> {

            return new XOR8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0x35] = ( /* xor ax, imm16 */ ) -> {

            return isOperandSize32() ? new XOR32(m_cpu, buildEAX(), buildIMM32()) :
                                       new XOR16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0x36] = ( /* ss: */ ) -> {
            
            m_prefixSegmentOverride = m_cpu.SS;

            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x37] = ( /* aaa */ ) -> {

            return new AAA(m_cpu);
        };

        m_decoderTableDefault[0x38] = ( /* cmp rm8, r8 */ ) -> {

            decodeMODRM();

            return new CMP8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTableDefault[0x39] = ( /* cmp rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new CMP32(m_cpu, buildRM32(), buildREG32()) :
                                       new CMP16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTableDefault[0x3a] = ( /* cmp r8, rm8 */ ) -> {

            decodeMODRM();

            return new CMP8(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x3b] = ( /* cmp r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new CMP32(m_cpu, buildREG32(), buildRM32()) :
                                       new CMP16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x3c] = ( /* cmp al, imm8 */ ) -> {

            return new CMP8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0x3d] = ( /* cmp ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new CMP32(m_cpu, buildEAX(), buildIMM32()) :
                                       new CMP16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0x3e] = ( /* ds: */ ) -> {
            
            m_prefixSegmentOverride = m_cpu.DS;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x3f] = ( /* aas */ ) -> {

            return new AAS(m_cpu);
        };

        m_decoderTableDefault[0x40] = ( /* inc ax/eax */ ) -> {

            return isOperandSize32() ? new INC32(m_cpu, buildEAX()) :
                                       new INC16(m_cpu, buildAX());
        };

        m_decoderTableDefault[0x41] = ( /* inc cx/ecx */ ) -> {

            return isOperandSize32() ? new INC32(m_cpu, buildECX()) :
                                       new INC16(m_cpu, buildCX());
        };

        m_decoderTableDefault[0x42] = ( /* inc dx/edx */ ) -> {

            return isOperandSize32() ? new INC32(m_cpu, buildEDX()) :
                                       new INC16(m_cpu, buildDX());
        };

        m_decoderTableDefault[0x43] = ( /* inc bx/ebx */ ) -> {

            return isOperandSize32() ? new INC32(m_cpu, buildEBX()) :
                                       new INC16(m_cpu, buildBX());
        };

        m_decoderTableDefault[0x44] = ( /* inc sp/esp */ ) -> {

            return isOperandSize32() ? new INC32(m_cpu, buildESP()) :
                                       new INC16(m_cpu, buildSP());
        };

        m_decoderTableDefault[0x45] = ( /* inc bp/ebp */ ) -> {

            return isOperandSize32() ? new INC32(m_cpu, buildEBP()) :
                                       new INC16(m_cpu, buildBP());
        };

        m_decoderTableDefault[0x46] = ( /* inc si/esi */ ) -> {

            return isOperandSize32() ? new INC32(m_cpu, buildESI()) :
                                       new INC16(m_cpu, buildSI());
        };

        m_decoderTableDefault[0x47] = ( /* inc di/edi */ ) -> {

            return isOperandSize32() ? new INC32(m_cpu, buildEDI()) :
                                       new INC16(m_cpu, buildDI());
        };

        m_decoderTableDefault[0x48] = ( /* dec ax/eax */ ) -> {

            return isOperandSize32() ? new DEC32(m_cpu, buildEAX()) :
                                       new DEC16(m_cpu, buildAX());
        };

        m_decoderTableDefault[0x49] = ( /* dec cx/ecx */ ) -> {

            return isOperandSize32() ? new DEC32(m_cpu, buildECX()) :
                                       new DEC16(m_cpu, buildCX());
        };

        m_decoderTableDefault[0x4a] = ( /* dec dx/edx */ ) -> {

            return isOperandSize32() ? new DEC32(m_cpu, buildEDX()) :
                                       new DEC16(m_cpu, buildDX());
        };

        m_decoderTableDefault[0x4b] = ( /* dec bx/ebx */ ) -> {

            return isOperandSize32() ? new DEC32(m_cpu, buildEBX()) :
                                       new DEC16(m_cpu, buildBX());
        };

        m_decoderTableDefault[0x4c] = ( /* dec sp/esp */ ) -> {

            return isOperandSize32() ? new DEC32(m_cpu, buildESP()) :
                                       new DEC16(m_cpu, buildSP());
        };

        m_decoderTableDefault[0x4d] = ( /* dec bp/ebp */ ) -> {

            return isOperandSize32() ? new DEC32(m_cpu, buildEBP()) :
                                       new DEC16(m_cpu, buildBP());
        };

        m_decoderTableDefault[0x4e] = ( /* dec si/esi */ ) -> {

            return isOperandSize32() ? new DEC32(m_cpu, buildESI()) :
                                       new DEC16(m_cpu, buildSI());
        };

        m_decoderTableDefault[0x4f] = ( /* dec di/edi */ ) -> {

            return isOperandSize32() ? new DEC32(m_cpu, buildEDI()) :
                                       new DEC16(m_cpu, buildDI());
        };

        m_decoderTableDefault[0x50] = ( /* push ax/eax */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildEAX()) :
                                       new PUSH16(m_cpu, buildAX());
        };

        m_decoderTableDefault[0x51] = ( /* push cx/ecx */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildECX()) :
                                       new PUSH16(m_cpu, buildCX());
        };

        m_decoderTableDefault[0x52] = ( /* push dx/edx */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildEDX()) :
                                       new PUSH16(m_cpu, buildDX());
        };

        m_decoderTableDefault[0x53] = ( /* push bx/ebx */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildEBX()) :
                                       new PUSH16(m_cpu, buildBX());
        };

        m_decoderTableDefault[0x54] = ( /* push sp/esp */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildESP()) :
                                       new PUSH16(m_cpu, buildSP());
        };

        m_decoderTableDefault[0x55] = ( /* push bp/ebp */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildEBP()) :
                                       new PUSH16(m_cpu, buildBP());
        };

        m_decoderTableDefault[0x56] = ( /* push si/esi */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildESI()) :
                                       new PUSH16(m_cpu, buildSI());
        };

        m_decoderTableDefault[0x57] = ( /* push di/edi */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildEDI()) :
                                       new PUSH16(m_cpu, buildDI());
        };

        m_decoderTableDefault[0x58] = ( /* pop ax/eax */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildEAX()) :
                                       new POP16(m_cpu, buildAX());
        };

        m_decoderTableDefault[0x59] = ( /* pop cx/ecx */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildECX()) :
                                       new POP16(m_cpu, buildCX());
        };

        m_decoderTableDefault[0x5a] = ( /* pop dx/edx */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildEDX()) :
                                       new POP16(m_cpu, buildDX());
        };

        m_decoderTableDefault[0x5b] = ( /* pop bx/ebx */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildEBX()) :
                                       new POP16(m_cpu, buildBX());
        };

        m_decoderTableDefault[0x5c] = ( /* pop sp/esp */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildESP()) :
                                       new POP16(m_cpu, buildSP());
        };

        m_decoderTableDefault[0x5d] = ( /* pop bp/ebp */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildEBP()) :
                                       new POP16(m_cpu, buildBP());
        };

        m_decoderTableDefault[0x5e] = ( /* pop si/esi */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildESI()) :
                                       new POP16(m_cpu, buildSI());
        };

        m_decoderTableDefault[0x5f] = ( /* pop di/edi */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildEDI()) :
                                       new POP16(m_cpu, buildDI());
        };

        m_decoderTableDefault[0x60] = ( /* pusha */ ) -> {

            return isOperandSize32() ? new PUSHA32(m_cpu) :
                                       new PUSHA16(m_cpu);
        };

        m_decoderTableDefault[0x61] = ( /* popa */ ) -> {

            return isOperandSize32() ? new POPA32(m_cpu) :
                                       new POPA16(m_cpu);
        };

        m_decoderTableDefault[0x62] = ( /* bound r16/32, m16/32&16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3)
                throw CPUException.getInvalidOpcode();

            return isOperandSize32() ? new BOUND32(m_cpu, buildREG32(), buildMEM32(0), buildMEM32(4)) :
                                       new BOUND16(m_cpu, buildREG16(), buildMEM16(0), buildMEM16(2));
        };

        m_decoderTableDefault[0x63] = ( /* arpl r16, rm16 */ ) -> {

            decodeMODRM();

            return new ARPL(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x64] = ( /* fs: */ ) -> {

            m_prefixSegmentOverride = m_cpu.FS;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x65] = ( /* gs: */ ) -> {
            
            m_prefixSegmentOverride = m_cpu.GS;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x66] = ( /* Operand size override */ ) -> {

            m_prefixOperandSizeOverride = true;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x67] = ( /* Address size override */ ) -> {

            m_prefixAddressSizeOverride = true;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x68] = ( /* push imm16/32 */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildIMM32()) :
                                       new PUSH16(m_cpu, buildIMM16());
        };

        m_decoderTableDefault[0x69] = ( /* imul r16/32, rm16/32, imm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new IMUL32_2(m_cpu, buildREG32(), buildRM32(), buildIMM32()) :
                                       new IMUL16_2(m_cpu, buildREG16(), buildRM16(), buildIMM16());
        };

        m_decoderTableDefault[0x6a] = ( /* push imm8 */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildSIMM8()) :
                                       new PUSH16(m_cpu, buildSIMM8());
        };

        m_decoderTableDefault[0x6b] = ( /* imul r16/32, r/m16/32, imm8 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new IMUL32_2(m_cpu, buildREG32(), buildRM32(), buildSIMM8()) :
                                       new IMUL16_2(m_cpu, buildREG16(), buildRM16(), buildSIMM8());
        };

        m_decoderTableDefault[0x6c] = ( /* insb */ ) -> {

            return new INSB(m_cpu, getDestIndex());
        };

        m_decoderTableDefault[0x6d] = ( /* insw/insd */ ) -> {

            return isOperandSize32() ? new INSD(m_cpu, getDestIndex()) :
                                       new INSW(m_cpu, getDestIndex());
        };

        m_decoderTableDefault[0x6e] = ( /* outsb */ ) -> {

            return new OUTSB(m_cpu, getSrcIndex(), getDefaultSegment());
        };

        m_decoderTableDefault[0x6f] = ( /* outsw/outsd */ ) -> {

            return isOperandSize32() ? new OUTSD(m_cpu, getSrcIndex(), getDefaultSegment()) :
                                       new OUTSW(m_cpu, getSrcIndex(), getDefaultSegment());
        };

        m_decoderTableDefault[0x70] = ( /* jo rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionOverflow()));
        };

        m_decoderTableDefault[0x71] = ( /* jno rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionNotOverflow()));
        };

        m_decoderTableDefault[0x72] = ( /* jb rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionBellow()));
        };

        m_decoderTableDefault[0x73] = ( /* jnb rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionNotBellow()));
        };

        m_decoderTableDefault[0x74] = ( /* jz rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionZero()));
        };

        m_decoderTableDefault[0x75] = ( /* jnz rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionNotZero()));
        };

        m_decoderTableDefault[0x76] = ( /* jbe rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionBellowOrEqual()));
        };

        m_decoderTableDefault[0x77] = ( /* jnbe rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionNotBellowOrEqual()));
        };

        m_decoderTableDefault[0x78] = ( /* js rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionSign()));
        };

        m_decoderTableDefault[0x79] = ( /* jns rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionNotSign()));
        };

        m_decoderTableDefault[0x7a] = ( /* jp rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionParity()));
        };

        m_decoderTableDefault[0x7b] = ( /* jnp rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionNotParity()));
        };

        m_decoderTableDefault[0x7c] = ( /* jl rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionLess()));
        };

        m_decoderTableDefault[0x7d] = ( /* jnl rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionNotLess()));
        };

        m_decoderTableDefault[0x7e] = ( /* jle rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionLessOrEqual()));
        };

        m_decoderTableDefault[0x7f] = ( /* jnle rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionNotLessOrEqual()));
        };

        m_decoderTableDefault[0x80] = ( /* grp1 rm8, imm8 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* add rm8, imm8 */
                case 0: return new ADD8(m_cpu, buildRM8(), buildIMM8());

                /* or rm8, imm8 */
                case 1: return new OR8(m_cpu, buildRM8(), buildIMM8());

                /* adc rm8, imm8 */
                case 2: return new ADC8(m_cpu, buildRM8(), buildIMM8());

                /* sbb rm8, imm8 */
                case 3: return new SBB8(m_cpu, buildRM8(), buildIMM8());

                /* and rm8, imm8 */
                case 4: return new AND8(m_cpu, buildRM8(), buildIMM8());

                /* sub rm8, imm8 */
                case 5: return new SUB8(m_cpu, buildRM8(), buildIMM8());

                /* xor rm8, imm8 */
                case 6: return new XOR8(m_cpu, buildRM8(), buildIMM8());

                /* cmp rm8, imm8 */
                case 7: return new CMP8(m_cpu, buildRM8(), buildIMM8());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0x81] = ( /* grp1 rm16/32, imm16/32 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* add rm16/32, imm16/32 */
                case 0: return isOperandSize32() ? new ADD32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new ADD16(m_cpu, buildRM16(), buildIMM16());

                /* or rm16/32, imm16/32 */
                case 1: return isOperandSize32() ? new OR32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new OR16(m_cpu, buildRM16(), buildIMM16());

                /* adc rm16/32, imm16/32 */
                case 2: return isOperandSize32() ? new ADC32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new ADC16(m_cpu, buildRM16(), buildIMM16());

                /* sbb rm16/32, imm16/32 */
                case 3: return isOperandSize32() ? new SBB32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new SBB16(m_cpu, buildRM16(), buildIMM16());

                /* and rm16/32, imm16/32 */
                case 4: return isOperandSize32() ? new AND32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new AND16(m_cpu, buildRM16(), buildIMM16());

                /* sub rm16/32, imm16/32 */
                case 5: return isOperandSize32() ? new SUB32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new SUB16(m_cpu, buildRM16(), buildIMM16());

                /* xor rm16/32, imm16/32 */
                case 6: return isOperandSize32() ? new XOR32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new XOR16(m_cpu, buildRM16(), buildIMM16());

                /* cmp rm16/32, imm16/32 */
                case 7: return isOperandSize32() ? new CMP32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new CMP16(m_cpu, buildRM16(), buildIMM16());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0x82] = ( /* grp1 rm8, imm8 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* add rm8, imm8 */
                case 0: return new ADD8(m_cpu, buildRM8(), buildIMM8());

                /* or rm8, imm8 */
                case 1: return new OR8(m_cpu, buildRM8(), buildIMM8());

                /* adc rm8, imm8 */
                case 2: return new ADC8(m_cpu, buildRM8(), buildIMM8());

                /* sbb rm8, imm8 */
                case 3: return new SBB8(m_cpu, buildRM8(), buildIMM8());

                /* and rm8, imm8 */
                case 4: return new AND8(m_cpu, buildRM8(), buildIMM8());

                /* sub rm8, imm8 */
                case 5: return new SUB8(m_cpu, buildRM8(), buildIMM8());

                /* xor rm8, imm8 */
                case 6: return new XOR8(m_cpu, buildRM8(), buildIMM8());

                /* cmp rm8, imm8 */
                case 7: return new CMP8(m_cpu, buildRM8(), buildIMM8());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0x83] = ( /* grp1 rm16/32, simm8  */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* add rm16/32, simm8 */
                case 0: return isOperandSize32() ? new ADD32(m_cpu, buildRM32(), buildSIMM8()) :
                                                   new ADD16(m_cpu, buildRM16(), buildSIMM8());

                /* or rm16/32, simm8 */
                case 1: return isOperandSize32() ? new OR32(m_cpu, buildRM32(), buildSIMM8()) :
                                                   new OR16(m_cpu, buildRM16(), buildSIMM8());

                /* adc rm16/32, simm8 */
                case 2: return isOperandSize32() ? new ADC32(m_cpu, buildRM32(), buildSIMM8()) :
                                                   new ADC16(m_cpu, buildRM16(), buildSIMM8());

                /* sbb rm16/32, simm8 */
                case 3: return isOperandSize32() ? new SBB32(m_cpu, buildRM32(), buildSIMM8()) :
                                                   new SBB16(m_cpu, buildRM16(), buildSIMM8());

                /* and rm16/32, simm8 */
                case 4: return isOperandSize32() ? new AND32(m_cpu, buildRM32(), buildSIMM8()) :
                                                   new AND16(m_cpu, buildRM16(), buildSIMM8());

                /* sub rm16/32, simm8 */
                case 5: return isOperandSize32() ? new SUB32(m_cpu, buildRM32(), buildSIMM8()) :
                                                   new SUB16(m_cpu, buildRM16(), buildSIMM8());

                /* xor rm16/32, simm8 */
                case 6: return isOperandSize32() ? new XOR32(m_cpu, buildRM32(), buildSIMM8()) :
                                                   new XOR16(m_cpu, buildRM16(), buildSIMM8());

                /* cmp rm16/32, simm8 */
                case 7: return isOperandSize32() ? new CMP32(m_cpu, buildRM32(), buildSIMM8()) :
                                                   new CMP16(m_cpu, buildRM16(), buildSIMM8());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0x84] = ( /* test r8, rm8 */ ) -> {

            decodeMODRM();

            return new TEST8(m_cpu, buildREG8(),
                             buildRM8());
        };

        m_decoderTableDefault[0x85] = ( /* test r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new TEST32(m_cpu, buildREG32(), buildRM32()) :
                                       new TEST16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x86] = ( /* xchg r8, rm8 */ ) -> {

            decodeMODRM();

            return new XCHG(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x87] = ( /* xchg r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new XCHG(m_cpu, buildREG32(), buildRM32()) :
                                       new XCHG(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x88] = ( /* mov rm8, r8 */ ) -> {

            decodeMODRM();

            return new MOV(m_cpu, buildRM8(),
                           buildREG8());
        };

        m_decoderTableDefault[0x89] = ( /* mov rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new MOV(m_cpu, buildRM32(), buildREG32()) :
                                       new MOV(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTableDefault[0x8a] = ( /* mov r8, rm8 */ ) -> {

            decodeMODRM();

            return new MOV(m_cpu, buildREG8(), buildRM8());
        };

        m_decoderTableDefault[0x8b] = ( /* mov r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new MOV(m_cpu, buildREG32(), buildRM32()) :
                                       new MOV(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTableDefault[0x8c] = ( /* mov rm16/32, sreg */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new MOV(m_cpu, buildRM32(true), buildSEG(false)) :
                                       new MOV(m_cpu, buildRM16(), buildSEG(false));
        };

        m_decoderTableDefault[0x8d] = ( /* lea r16/32, m16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new LEA(m_cpu, buildREG32(), buildADDR(0)) :
                                       new LEA(m_cpu, buildREG16(), buildADDR(0));
        };

        m_decoderTableDefault[0x8e] = ( /* mov sreg, rm16 */ ) -> {

            decodeMODRM();

            return new MOV(m_cpu, buildSEG(true), buildRM16());
        };

        m_decoderTableDefault[0x8f] = ( /* pop rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new POP32(m_cpu, buildRM32()) :
                                       new POP16(m_cpu, buildRM16());
        };

        m_decoderTableDefault[0x90] = ( /* nop */ ) -> {

            return new NOP(m_cpu);
        };

        m_decoderTableDefault[0x91] = ( /* xchg ax/eax, cx/ecx */ ) -> {

            return isOperandSize32() ? new XCHG(m_cpu, buildEAX(), buildECX()) :
                                       new XCHG(m_cpu, buildAX(), buildCX());
        };

        m_decoderTableDefault[0x92] = ( /* xchg ax/eax, dx/edx */ ) -> {

            return isOperandSize32() ? new XCHG(m_cpu, buildEAX(), buildEDX()) :
                                       new XCHG(m_cpu, buildAX(), buildDX());
        };

        m_decoderTableDefault[0x93] = ( /* xchg ax/eax, bx/ebx */ ) -> {

            return isOperandSize32() ? new XCHG(m_cpu, buildEAX(), buildEBX()) :
                                       new XCHG(m_cpu, buildAX(), buildBX());
        };

        m_decoderTableDefault[0x94] = ( /* xchg ax/eax, sp/esp */ ) -> {

            return isOperandSize32() ? new XCHG(m_cpu, buildEAX(), buildESP()) :
                                       new XCHG(m_cpu, buildAX(), buildSP());
        };

        m_decoderTableDefault[0x95] = ( /* xchg ax/eax, bp/ebp */ ) -> {

            return isOperandSize32() ? new XCHG(m_cpu, buildEAX(), buildEBP()) :
                                       new XCHG(m_cpu, buildAX(), buildBP());
        };

        m_decoderTableDefault[0x96] = ( /* xchg ax/eax, si/esi */ ) -> {

            return isOperandSize32() ? new XCHG(m_cpu, buildEAX(), buildESI()) :
                                       new XCHG(m_cpu, buildAX(), buildSI());
        };

        m_decoderTableDefault[0x97] = ( /* xchg ax/eax, di/edi */ ) -> {

            return isOperandSize32() ? new XCHG(m_cpu, buildEAX(), buildEDI()) :
                                       new XCHG(m_cpu, buildAX(), buildDI());
        };

        m_decoderTableDefault[0x98] = ( /* cbw/cwde */ ) -> {

            return isOperandSize32() ? new CWDE(m_cpu) :
                                       new CBW(m_cpu);
        };

        m_decoderTableDefault[0x99] = ( /* cwd/cwq */ ) -> {

            return isOperandSize32() ? new CDQ(m_cpu) :
                                       new CWD(m_cpu);
        };

        m_decoderTableDefault[0x9a] = ( /* call imm16:16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new CALL_FAR(m_cpu, buildIMM32(), buildIMM16(), true) :
                                              new CALL_FAR(m_cpu, buildIMM16(), buildIMM16(), false));
        };

        m_decoderTableDefault[0x9b] = ( /* wait */ ) -> {

            m_prefixWait = true;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0x9c] = ( /* pushf */ ) -> {

            return isOperandSize32() ? new PUSHF32(m_cpu) :
                                       new PUSHF16(m_cpu);
        };

        m_decoderTableDefault[0x9d] = ( /* popf */ ) -> {

            return isOperandSize32() ? new POPF32(m_cpu) :
                                       new POPF16(m_cpu);
        };

        m_decoderTableDefault[0x9e] = ( /* sahf */ ) -> {

            return new SAHF(m_cpu);
        };

        m_decoderTableDefault[0x9f] = ( /* lahf */ ) -> {

            return new LAHF(m_cpu);
        };

        m_decoderTableDefault[0xa0] = ( /* mov al, moffs8 */ ) -> {

            return new MOV(m_cpu, buildAL(), buildMOFFS8());
        };

        m_decoderTableDefault[0xa1] = ( /* mov ax/eax, moffs16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildEAX(), buildMOFFS32()) :
                                       new MOV(m_cpu, buildAX(), buildMOFFS16());
        };

        m_decoderTableDefault[0xa2] = ( /* mov moffs8, al */ ) -> {

            return new MOV(m_cpu, buildMOFFS8(), buildAL());
        };

        m_decoderTableDefault[0xa3] = ( /* mov moffs16/32, ax/eax */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildMOFFS32(), buildEAX()) :
                                       new MOV(m_cpu, buildMOFFS16(), buildAX());
        };

        m_decoderTableDefault[0xa4] = ( /* movsb */ ) -> {

            return new MOVSB(m_cpu, getDefaultSegment(), getSrcIndex(), getDestIndex());
        };

        m_decoderTableDefault[0xa5] = ( /* movsw/movsd */ ) -> {

            return isOperandSize32() ? new MOVSD(m_cpu, getDefaultSegment(), getSrcIndex(), getDestIndex()) :
                                       new MOVSW(m_cpu, getDefaultSegment(), getSrcIndex(), getDestIndex());
        };

        m_decoderTableDefault[0xa6] = ( /* cmpsb */ ) -> {

            return new CMPSB(m_cpu, getDefaultSegment(), getSrcIndex(), getDestIndex());
        };

        m_decoderTableDefault[0xa7] = ( /* cmpsw */ ) -> {

            return isOperandSize32() ? new CMPSD(m_cpu, getDefaultSegment(), getSrcIndex(), getDestIndex()) :
                                       new CMPSW(m_cpu, getDefaultSegment(), getSrcIndex(), getDestIndex());
        };

        m_decoderTableDefault[0xa8] = ( /* test al, imm8 */ ) -> {

            return new TEST8(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0xa9] = ( /* test ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new TEST32(m_cpu, buildEAX(), buildIMM32()) :
                                       new TEST16(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0xaa] = ( /* stosb */ ) -> {

            return new STOSB(m_cpu, getDestIndex());
        };

        m_decoderTableDefault[0xab] = ( /* stosw/stosd */ ) -> {

            return isOperandSize32() ? new STOSD(m_cpu, getDestIndex()) :
                                       new STOSW(m_cpu, getDestIndex());
        };

        m_decoderTableDefault[0xac] = ( /* lodsb */ ) -> {

            return new LODSB(m_cpu, getDefaultSegment(), getSrcIndex());
        };

        m_decoderTableDefault[0xad] = ( /* lodsw/lodsd */ ) -> {

            return isOperandSize32() ? new LODSD(m_cpu, getDefaultSegment(), getSrcIndex()) :
                                       new LODSW(m_cpu, getDefaultSegment(), getSrcIndex());
        };

        m_decoderTableDefault[0xae] = ( /* scasb */ ) -> {

            return new SCASB(m_cpu, getDestIndex());
        };

        m_decoderTableDefault[0xaf] = ( /* scasw/scasd */ ) -> {

            return isOperandSize32() ? new SCASD(m_cpu, getDestIndex()) :
                                       new SCASW(m_cpu, getDestIndex());
        };

        m_decoderTableDefault[0xb0] = ( /* mov al, imm8 */ ) -> {

            return new MOV(m_cpu, buildAL(), buildIMM8());
        };

        m_decoderTableDefault[0xb1] = ( /* mov cl, imm8 */ ) -> {

            return new MOV(m_cpu, buildCL(), buildIMM8());
        };

        m_decoderTableDefault[0xb2] = ( /* mov dl, imm8 */ ) -> {

            return new MOV(m_cpu, buildDL(), buildIMM8());
        };

        m_decoderTableDefault[0xb3] = ( /* mov bl, imm8 */ ) -> {

            return new MOV(m_cpu, buildBL(), buildIMM8());
        };

        m_decoderTableDefault[0xb4] = ( /* mov ah, imm8 */ ) -> {

            return new MOV(m_cpu, buildAH(), buildIMM8());
        };

        m_decoderTableDefault[0xb5] = ( /* mov ch, imm8 */ ) -> {

            return new MOV(m_cpu, buildCH(), buildIMM8());
        };

        m_decoderTableDefault[0xb6] = ( /* mov dh, imm8 */ ) -> {

            return new MOV(m_cpu, buildDH(), buildIMM8());
        };

        m_decoderTableDefault[0xb7] = ( /* mov bh, imm8 */ ) -> {

            return new MOV(m_cpu, buildBH(), buildIMM8());
        };

        m_decoderTableDefault[0xb8] = ( /* mov ax/eax, imm16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildEAX(), buildIMM32()) :
                                       new MOV(m_cpu, buildAX(), buildIMM16());
        };

        m_decoderTableDefault[0xb9] = ( /* mov cx/ecx, imm16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildECX(), buildIMM32()):
                                       new MOV(m_cpu, buildCX(), buildIMM16());
        };

        m_decoderTableDefault[0xba] = ( /* mov dx/edx, imm16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildEDX(), buildIMM32()) :
                                       new MOV(m_cpu, buildDX(), buildIMM16());
        };

        m_decoderTableDefault[0xbb] = ( /* mov bx/ebx, imm16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildEBX(), buildIMM32()) :
                                       new MOV(m_cpu, buildBX(), buildIMM16());
        };

        m_decoderTableDefault[0xbc] = ( /* mov sp/esp, imm16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildESP(), buildIMM32()) :
                                       new MOV(m_cpu, buildSP(), buildIMM16());
        };

        m_decoderTableDefault[0xbd] = ( /* mov bp/ebp, imm16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildEBP(), buildIMM32()) :
                                       new MOV(m_cpu, buildBP(), buildIMM16());
        };

        m_decoderTableDefault[0xbe] = ( /* mov si/esi, imm16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildESI(), buildIMM32()) :
                                       new MOV(m_cpu, buildSI(), buildIMM16());
        };

        m_decoderTableDefault[0xbf] = ( /* mov di/edi, imm16/32 */ ) -> {

            return isOperandSize32() ? new MOV(m_cpu, buildEDI(), buildIMM32()) :
                                       new MOV(m_cpu, buildDI(), buildIMM16());
        };

        m_decoderTableDefault[0xc0] = ( /* grp2 rm8, imm8 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* ROL rm8, imm8 */
                case 0: return new ROL8(m_cpu, buildRM8(), buildIMM8());

                /* ROR rm8, imm8 */
                case 1: return new ROR8(m_cpu, buildRM8(), buildIMM8());

                /* RCL rm8, imm8 */
                case 2: return new RCL8(m_cpu, buildRM8(), buildIMM8());

                /* RCR rm8, imm8 */
                case 3: return new RCR8(m_cpu, buildRM8(), buildIMM8());

                /* SHL rm8, imm8 */
                case 4: return new SHL8(m_cpu, buildRM8(), buildIMM8());

                /* SHR rm8, imm8 */
                case 5: return new SHR8(m_cpu, buildRM8(), buildIMM8());

                /* SAL rm8, imm8 */
                case 6: return new SHL8(m_cpu, buildRM8(), buildIMM8());

                /* SAR rm8, imm8 */
                case 7: return new SAR8(m_cpu, buildRM8(), buildIMM8());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xc1] = ( /* grp2 rm16/32, imm8 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* ROL rm16/32, imm8 */
                case 0: return isOperandSize32() ? new ROL32(m_cpu, buildRM32(), buildIMM8()) :
                                                   new ROL16(m_cpu, buildRM16(), buildIMM8());

                /* ROR rm16/32, imm8 */
                case 1: return isOperandSize32() ? new ROR32(m_cpu, buildRM32(), buildIMM8()) :
                                                   new ROR16(m_cpu, buildRM16(), buildIMM8());

                /* RCL rm16/32, imm8 */
                case 2: return isOperandSize32() ? new RCL32(m_cpu, buildRM32(), buildIMM8()) :
                                                   new RCL16(m_cpu, buildRM16(), buildIMM8());

                /* RCR rm16/32, imm8 */
                case 3: return isOperandSize32() ? new RCR32(m_cpu, buildRM32(), buildIMM8()) :
                                                   new RCR16(m_cpu, buildRM16(), buildIMM8());

                /* SHL rm16/32, imm8 */
                case 4: return isOperandSize32() ? new SHL32(m_cpu, buildRM32(), buildIMM8()) :
                                                   new SHL16(m_cpu, buildRM16(), buildIMM8());

                /* SHR rm16/32, imm8 */
                case 5: return isOperandSize32() ? new SHR32(m_cpu, buildRM32(), buildIMM8()) :
                                                   new SHR16(m_cpu, buildRM16(), buildIMM8());

                /* SAL rm16/32, imm8 */
                case 6: return isOperandSize32() ? new SHL32(m_cpu, buildRM32(), buildIMM8()) :
                                                   new SHL16(m_cpu, buildRM16(), buildIMM8());

                /* SAR rm16/32, imm8 */
                case 7: return isOperandSize32() ? new SAR32(m_cpu, buildRM32(), buildIMM8()) :
                                                   new SAR16(m_cpu, buildRM16(), buildIMM8());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xc2] = ( /* ret imm16 */ ) -> {

            return finishBlock(isOperandSize32() ? new RET_NEAR(m_cpu, buildIMM16(), true) :
                                              new RET_NEAR(m_cpu, buildIMM16(), false));
        };

        m_decoderTableDefault[0xc3] = ( /* ret */ ) -> {

            return finishBlock(isOperandSize32() ? new RET_NEAR(m_cpu, null, true) :
                                              new RET_NEAR(m_cpu, null, false));
        };

        m_decoderTableDefault[0xc4] = ( /* les r16/32, m16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3)
                throw CPUException.getInvalidOpcode();

            return isOperandSize32() ? new LSEG(m_cpu, buildES(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                       new LSEG(m_cpu, buildES(), buildREG16(), buildMEM16(2), buildMEM16(0));
        };

        m_decoderTableDefault[0xc5] = ( /* lds r16/32, m16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3)
                throw CPUException.getInvalidOpcode();

            return isOperandSize32() ? new LSEG(m_cpu, buildDS(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                       new LSEG(m_cpu, buildDS(), buildREG16(), buildMEM16(2), buildMEM16(0));
        };

        m_decoderTableDefault[0xc6] = ( /* mov rm8, imm8 */ ) -> {

            decodeMODRM();

            return new MOV(m_cpu, buildRM8(), buildIMM8());
        };

        m_decoderTableDefault[0xc7] = ( /* mov rm16/32, imm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new MOV(m_cpu, buildRM32(), buildIMM32()) :
                                       new MOV(m_cpu, buildRM16(), buildIMM16());
        };

        m_decoderTableDefault[0xc8] = ( /* enter imm16, imm8 */ ) -> {

            return isOperandSize32() ? new ENTER32(m_cpu, buildIMM16(), buildIMM8()) :
                                       new ENTER16(m_cpu, buildIMM16(), buildIMM8());
        };

        m_decoderTableDefault[0xc9] = ( /* leave */ ) -> {

            return isOperandSize32() ? new LEAVE32(m_cpu) :
                                       new LEAVE16(m_cpu);
        };

        m_decoderTableDefault[0xca] = ( /* retf imm16 */ ) -> {

            return finishBlock(isOperandSize32() ? new RET_FAR(m_cpu, buildIMM16(), true) :
                                              new RET_FAR(m_cpu, buildIMM16(), false));
        };

        m_decoderTableDefault[0xcb] = ( /* retf */ ) -> {

            return finishBlock(isOperandSize32() ? new RET_FAR(m_cpu, null, true) :
                                              new RET_FAR(m_cpu, null, false));
        };

        m_decoderTableDefault[0xcc] = ( /* int 3h */ ) -> {

            return finishBlock(new INT(m_cpu, buildImmediate(3)));
        };

        m_decoderTableDefault[0xcd] = ( /* int imm8 */ ) -> {

            return finishBlock(new INT(m_cpu, buildIMM8()));
        };

        m_decoderTableDefault[0xce] = ( /* into */ ) -> {

            return finishBlock(new INTO(m_cpu));
        };

        m_decoderTableDefault[0xcf] = ( /* iret */ ) -> {

            return finishBlock(isOperandSize32() ? new IRET(m_cpu, true) :
                                              new IRET(m_cpu, false));
        };

        m_decoderTableDefault[0xd0] = ( /* grp2 rm8, 1 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* rol rm8, 1 */
                case 0: return new ROL8(m_cpu, buildRM8(), buildImmediate(1));

                /* ror rm8, 1 */
                case 1: return new ROR8(m_cpu, buildRM8(), buildImmediate(1));

                /* rcl rm8, 1 */
                case 2: return new RCL8(m_cpu, buildRM8(), buildImmediate(1));

                /* rcr rm8, 1 */
                case 3: return new RCR8(m_cpu, buildRM8(), buildImmediate(1));

                /* shl rm8, 1 */
                case 4: return new SHL8(m_cpu, buildRM8(), buildImmediate(1));

                /* shr rm8, 1 */
                case 5: return new SHR8(m_cpu, buildRM8(), buildImmediate(1));

                /* sal rm8, 1 */
                case 6: return new SHL8(m_cpu, buildRM8(), buildImmediate(1));

                /* sar rm8, 1 */
                case 7: return new SAR8(m_cpu, buildRM8(), buildImmediate(1));

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xd1] = ( /* grp2 rm16/32, 1 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* rol rm16/32, 1 */
                case 0: return isOperandSize32() ? new ROL32(m_cpu, buildRM32(), buildImmediate(1)) :
                                                   new ROL16(m_cpu, buildRM16(), buildImmediate(1));

                /* ror rm16/32, 1 */
                case 1: return isOperandSize32() ? new ROR32(m_cpu, buildRM32(), buildImmediate(1)) :
                                                   new ROR16(m_cpu, buildRM16(), buildImmediate(1));

                /* rcl rm16/32, 1 */
                case 2: return isOperandSize32() ? new RCL32(m_cpu, buildRM32(), buildImmediate(1)) :
                                                   new RCL16(m_cpu, buildRM16(), buildImmediate(1));

                /* rcr rm16/32, 1 */
                case 3: return isOperandSize32() ? new RCR32(m_cpu, buildRM32(), buildImmediate(1)) :
                                                   new RCR16(m_cpu, buildRM16(), buildImmediate(1));

                /* shl rm16/32, 1 */
                case 4: return isOperandSize32() ? new SHL32(m_cpu, buildRM32(), buildImmediate(1)) :
                                                   new SHL16(m_cpu, buildRM16(), buildImmediate(1));

                /* shr rm16/32, 1 */
                case 5: return isOperandSize32() ? new SHR32(m_cpu, buildRM32(), buildImmediate(1)) :
                                                   new SHR16(m_cpu, buildRM16(), buildImmediate(1));

                /* sal rm16/32, 1 */
                case 6: return isOperandSize32() ? new SHL32(m_cpu, buildRM32(), buildImmediate(1)) :
                                                   new SHL16(m_cpu, buildRM16(), buildImmediate(1));

                /* sar rm16/32, 1 */
                case 7: return isOperandSize32() ? new SAR32(m_cpu, buildRM32(), buildImmediate(1)) :
                                                   new SAR16(m_cpu, buildRM16(), buildImmediate(1));

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xd2] = ( /* grp2 rm8, cl */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* rol rm8, cl */
                case 0: return new ROL8(m_cpu, buildRM8(), buildCL());

                /* ror rm8, cl */
                case 1: return new ROR8(m_cpu, buildRM8(), buildCL());

                /* rcl rm8, cl */
                case 2: return new RCL8(m_cpu, buildRM8(), buildCL());

                /* rcr rm8, cl */
                case 3: return new RCR8(m_cpu, buildRM8(), buildCL());

                /* shl rm8, cl */
                case 4: return new SHL8(m_cpu, buildRM8(), buildCL());

                /* shr rm8, cl */
                case 5: return new SHR8(m_cpu, buildRM8(), buildCL());

                /* sal rm8, cl */
                case 6: return new SHL8(m_cpu, buildRM8(), buildCL());

                /* sar rm8, cl */
                case 7: return new SAR8(m_cpu, buildRM8(), buildCL());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xd3] = ( /* grp2 rm16/32, cl */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* rol rm16/32, cl */
                case 0: return isOperandSize32() ? new ROL32(m_cpu, buildRM32(), buildCL()) :
                                                   new ROL16(m_cpu, buildRM16(), buildCL());

                /* ror rm16/32, cl */
                case 1: return isOperandSize32() ? new ROR32(m_cpu, buildRM32(), buildCL()) :
                                                   new ROR16(m_cpu, buildRM16(), buildCL());

                /* rcl rm16/32, cl */
                case 2: return isOperandSize32() ? new RCL32(m_cpu, buildRM32(), buildCL()) :
                                                   new RCL16(m_cpu, buildRM16(), buildCL());

                /* rcr rm16/32, cl */
                case 3: return isOperandSize32() ? new RCR32(m_cpu, buildRM32(), buildCL()) :
                                                   new RCR16(m_cpu, buildRM16(), buildCL());

                /* shl rm16/32, cl */
                case 4: return isOperandSize32() ? new SHL32(m_cpu, buildRM32(), buildCL()) :
                                                   new SHL16(m_cpu, buildRM16(), buildCL());

                /* shr rm16/32, cl */
                case 5: return isOperandSize32() ? new SHR32(m_cpu, buildRM32(), buildCL()) :
                                                   new SHR16(m_cpu, buildRM16(), buildCL());

                /* sal rm16/32, cl */
                case 6: return isOperandSize32() ? new SHL32(m_cpu, buildRM32(), buildCL()) :
                                                   new SHL16(m_cpu, buildRM16(), buildCL());

                /* sar rm16/32, cl */
                case 7: return isOperandSize32() ? new SAR32(m_cpu, buildRM32(), buildCL()) :
                                                   new SAR16(m_cpu, buildRM16(), buildCL());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xd4] = ( /* aam */ ) -> {

            return new AAM(m_cpu, buildIMM8());
        };

        m_decoderTableDefault[0xd5] = ( /* aad */ ) -> {

            return new AAD(m_cpu, buildIMM8());
        };

        m_decoderTableDefault[0xd6] = ( /* salc */ ) -> {

            return new SALC(m_cpu);
        };

        m_decoderTableDefault[0xd7] = ( /* xlat */ ) -> {

            return isAddressSize32() ? new XLAT32(m_cpu, getDefaultSegment()) :
                                       new XLAT16(m_cpu, getDefaultSegment());
        };

        m_decoderTableDefault[0xd8] = ( /* escape */ ) -> {
            
            decodeMODRM();
            
            m_hasDecodedFPUInstruction = true;
            
            if(!m_cpu.hasFPU())
                return new ESCAPE(m_cpu);
            
                
            if(m_mod == 3) {
                    
                switch(m_reg) {

                    /* fadd st0, sti */
                    case 0:
                        return new FADD(m_cpu, buildST0(), buildST0(), buildSTi());

                    /* fmul st0, sti */
                    case 1:
                        return new FMUL(m_cpu, buildST0(), buildST0(), buildSTi());

                    /* fcom sti */
                    case 2:
                        return new FCOM(m_cpu, buildSTi());

                    /* fcomp sti */
                    case 3:
                        return new FCOMP(m_cpu, buildSTi());

                    /* fsub st0, sti */
                    case 4:
                        return new FSUB(m_cpu, buildST0(), buildST0(), buildSTi());

                    /* fsubr st0, sti */
                    case 5:
                        return new FSUB(m_cpu, buildST0(), buildSTi(), buildST0());

                    /* fdiv st0, sti */
                    case 6:
                        return new FDIV(m_cpu, buildST0(), buildST0(), buildSTi());

                    /* fdivr st0, sti */
                    case 7:
                        return new FDIV(m_cpu, buildST0(), buildSTi(), buildST0());
                }
            }
            else {

                switch(m_reg) {

                    /* fadd st0, m32fp */
                    case 0:
                        return new FADD(m_cpu, buildST0(), buildST0(), buildMEMFloatingPoint(32));

                    /* fmul st0, m32fp */
                    case 1:
                        return new FMUL(m_cpu, buildST0(), buildST0(), buildMEMFloatingPoint(32));

                    /* fcom m32fp */
                    case 2:
                        return new FCOM(m_cpu, buildMEMFloatingPoint(32));

                    /* fcomp m32fp */
                    case 3:
                        return new FCOMP(m_cpu, buildMEMFloatingPoint(32));

                    /* fsub st0, m32fp */
                    case 4:
                        return new FSUB(m_cpu, buildST0(), buildST0(), buildMEMFloatingPoint(32));

                    /* fsubr st0, m32fp */
                    case 5:
                        return new FSUB(m_cpu, buildST0(), buildMEMFloatingPoint(32), buildST0());

                    /* fdiv st0, m32fp */
                    case 6:
                        return new FDIV(m_cpu, buildST0(), buildST0(), buildMEMFloatingPoint(32));

                    /* fdivr st0, m32fp */
                    case 7:
                        return new FDIV(m_cpu, buildST0(), buildMEMFloatingPoint(32), buildST0());
                }
            }
            
            throw new IllegalArgumentException(String.format("Invalid FPU instruction 0xd8 0x%02x", m_modRMByte));
        };

        m_decoderTableDefault[0xd9] = ( /* escape */ ) -> {
            
            decodeMODRM();
            
            m_hasDecodedFPUInstruction = true;
            
            if(!m_cpu.hasFPU())
                return new ESCAPE(m_cpu);
            
            
            if(m_mod == 3) {
                
                /* fld sti */
                if(m_modRMByte >= 0xc0 && m_modRMByte <= 0xc7)
                    return new FLD(m_cpu, buildSTi());
                
                /* fxch sti */
                if(m_modRMByte >= 0xc8 && m_modRMByte <= 0xcf)
                    return new FXCH(m_cpu, buildSTi());
                
                switch(m_modRMByte) {
                    
                    /* fnop */
                    case 0xd0: return new FNOP(m_cpu);
                    
                    /* fnop */
                    case 0xd8: return new FNOP(m_cpu);
                
                    /* fchs */
                    case 0xe0: return new FCHS(m_cpu);
                
                    /* fabs */
                    case 0xe1: return new FABS(m_cpu);
                
                    /* ftst */
                    case 0xe4: return new FTST(m_cpu);
                
                    /* fxam */
                    case 0xe5: return new FXAM(m_cpu);
                
                    /* fld1 */
                    case 0xe8: return new FLD1(m_cpu);
                
                    /* fldl2t */
                    case 0xe9: return new FLDL2T(m_cpu);
                
                    /* fldl2e */
                    case 0xea: return new FLDL2E(m_cpu);
                
                    /* fldpi */
                    case 0xeb: return new FLDPI(m_cpu);
                
                    /* fldlg2 */
                    case 0xec: return new FLDLG2(m_cpu);
                
                    /* fldln2 */
                    case 0xed: return new FLDLN2(m_cpu);
                
                    /* fldz */
                    case 0xee: return new FLDZ(m_cpu);
                
                    /* f2xm1 */
                    case 0xf0: return new F2XM1(m_cpu);
                
                    /* fyl2x */
                    case 0xf1: return new FYL2X(m_cpu);
                
                    /* fptan */
                    case 0xf2: return new FPTAN(m_cpu);
                
                    /* fpatan */
                    case 0xf3: return new FPATAN(m_cpu);
                
                    /* fxtract */
                    case 0xf4: return new FXTRACT(m_cpu);
                
                    /* fprem1 */
                    case 0xf5: return new FPREM1(m_cpu);
                
                    /* fdecstp */
                    case 0xf6: return new FDECSTP(m_cpu);
                
                    /* fincstp */
                    case 0xf7: return new FINCSTP(m_cpu);
                
                    /* fprem */
                    case 0xf8: return new FPREM(m_cpu);
                
                    /* fyl2xp1 */
                    case 0xf9: return new FYL2XP1(m_cpu);
                
                    /* fsqrt */
                    case 0xfa: return new FSQRT(m_cpu);
                
                    /* fsincos */
                    case 0xfb: return new FSINCOS(m_cpu);
                
                    /* frndint */
                    case 0xfc: return new FRNDINT(m_cpu);
                
                    /* fscale */
                    case 0xfd: return new FSCALE(m_cpu);
                
                    /* fsin */
                    case 0xfe: return new FSIN(m_cpu);
                
                    /* fsin */
                    case 0xff: return new FCOS(m_cpu);
                }
            }
            else {
                
                Pointer ptr;
                
                switch(m_reg) {
                    
                    /* fld m32fp */
                    case 0: return new FLD(m_cpu, buildMEMFloatingPoint(32));
                    
                    /* fst m32fp */
                    case 2: return new FST(m_cpu, buildMEMFloatingPoint(32));
                    
                    /* fstp m32fp */
                    case 3: return new FSTP(m_cpu, buildMEMFloatingPoint(32));
                    
                    /* fldenv m16/32 */
                    case 4:
                        ptr = isAddressSize32() ? buildPointer32(m_addrDisp) :
                                                  buildPointer16(m_addrDisp);
                        
                        return isOperandSize32() ? new FLDENV32(m_cpu, m_addrSeg, ptr) :
                                                   new FLDENV16(m_cpu, m_addrSeg, ptr);
                    
                    /* fldcw m16 */
                    case 5: return new FLDCW(m_cpu, buildMEM16(0));
                    
                    /* fstenv m16/32 */
                    case 6:
                        ptr = isAddressSize32() ? buildPointer32(m_addrDisp) :
                                                  buildPointer16(m_addrDisp);
                        
                        return isOperandSize32() ? new FSTENV32(m_cpu, m_addrSeg, ptr) :
                                                   new FSTENV16(m_cpu, m_addrSeg, ptr);
                    
                    /* fstcw m16 */
                    case 7: return new FSTCW(m_cpu, buildMEM16(0));
                }
            }
            
            // Invalid instruction
            throw new IllegalArgumentException(String.format("Invalid FPU instruction 0xd9 0x%02x", m_modRMByte));
        };

        m_decoderTableDefault[0xda] = ( /* escape */ ) -> {
            
            decodeMODRM();
            
            m_hasDecodedFPUInstruction = true;
            
            if(!m_cpu.hasFPU())
                return new ESCAPE(m_cpu);
            
            
            if(m_mod == 3) {
                
                if(m_modRMByte == 0xe9)
                    return new FUCOMPP(m_cpu, buildST1());
            }
            else {
                
                switch(m_reg) {
                    
                    /* fiadd st0, m32i */
                    case 0: return new FADD(m_cpu, buildST0(), buildST0(), buildMEMInteger(32));
                    
                    /* fimul st0, m32i */
                    case 1: return new FMUL(m_cpu, buildST0(), buildST0(), buildMEMInteger(32));
                    
                    /* ficom st0, m32i */
                    case 2: return new FCOM(m_cpu, buildMEMInteger(32));
                    
                    /* ficomp st0, m32i */
                    case 3: return new FCOMP(m_cpu, buildMEMInteger(32));
                    
                    /* fisub st0, m32i */
                    case 4: return new FSUB(m_cpu, buildST0(), buildST0(), buildMEMInteger(32));
                    
                    /* fisubr st0, m32i */
                    case 5: return new FSUB(m_cpu, buildST0(), buildMEMInteger(32), buildST0());
                    
                    /* fidiv st0, m32i */
                    case 6: return new FDIV(m_cpu, buildST0(), buildST0(), buildMEMInteger(32));
                    
                    /* fisubr st0, m32i */
                    case 7: return new FDIV(m_cpu, buildST0(), buildMEMInteger(32), buildST0());
                }
            }
            
            // Invalid instruction
            throw new IllegalArgumentException(String.format("Invalid FPU instruction 0xda 0x%02x", m_modRMByte));
        };

        m_decoderTableDefault[0xdb] = ( /* escape */ ) -> {
            
            decodeMODRM();
            
            m_hasDecodedFPUInstruction = true;
            
            if(!m_cpu.hasFPU())
                return new ESCAPE(m_cpu);
            
            
            if(m_mod == 3) {
                
                switch(m_modRMByte) {
                    
                    /* fdisi (8087 only) */
                    case 0xe1: return new FNOP(m_cpu);
                    
                    /* fclex */
                    case 0xe2: return new FCLEX(m_cpu);
                    
                    /* finit */
                    case 0xe3: return new FINIT(m_cpu);
                    
                    /* fsetpm (287 only) */
                    case 0xe4: return new FNOP(m_cpu);
                    
                    /* frstpm (287 only) */
                    case 0xe5: return new FNOP(m_cpu);
                    
                    /* feni (8087 only) */
                    case 0xf0: return new FNOP(m_cpu);
                }
            }
            else {
                
                switch(m_reg) {
                    
                    /* fild m32i */
                    case 0: return new FLD(m_cpu, buildMEMInteger(32));
                    
                    /* fist m32i */
                    case 2: return new FST(m_cpu, buildMEMInteger(32));
                    
                    /* fistp m32i */
                    case 3: return new FSTP(m_cpu, buildMEMInteger(32));
                    
                    /* fld m80 */
                    case 5: return new FLD(m_cpu, buildMEMFloatingPoint(80));
                    
                    /* fstp m80 */
                    case 7: return new FSTP(m_cpu, buildMEMFloatingPoint(80));
                }
            }
            
            // Invalid instruction
            throw new IllegalArgumentException(String.format("Invalid FPU instruction 0xdb 0x%02x", m_modRMByte));
        };

        m_decoderTableDefault[0xdc] = ( /* escape */ ) -> {
            
            decodeMODRM();
            
            m_hasDecodedFPUInstruction = true;
            
            if(!m_cpu.hasFPU())
                return new ESCAPE(m_cpu);
            
            
            if(m_mod == 3) {
                
                /* fadd sti, st0 */
                if(m_modRMByte >= 0xc0 && m_modRMByte <= 0xc7)
                    return new FADD(m_cpu, buildSTi(), buildSTi(), buildST0());
                
                /* fmul sti, st0 */
                if(m_modRMByte >= 0xc8 && m_modRMByte <= 0xcf)
                    return new FMUL(m_cpu, buildSTi(), buildSTi(), buildST0());
                
                /* fsubr sti, st0 */
                if(m_modRMByte >= 0xe0 && m_modRMByte <= 0xe7)
                    return new FSUB(m_cpu, buildSTi(), buildST0(), buildSTi());
                
                /* fsub sti, st0 */
                if(m_modRMByte >= 0xe8 && m_modRMByte <= 0xef)
                    return new FSUB(m_cpu, buildSTi(), buildSTi(), buildST0());
                
                /* fdivr sti, st0 */
                if(m_modRMByte >= 0xf0 && m_modRMByte <= 0xf7)
                    return new FDIV(m_cpu, buildSTi(), buildST0(), buildSTi());
                
                /* fdiv sti, st0 */
                if(m_modRMByte >= 0xf8 && m_modRMByte <= 0xff)
                    return new FDIV(m_cpu, buildSTi(), buildSTi(), buildST0());
            }
            else {
                
                switch(m_reg) {
                    
                    /* fadd st0, m64fp */
                    case 0: return new FADD(m_cpu, buildST0(), buildST0(), buildMEMFloatingPoint(64));
                    
                    /* fmul st0, m64fp */
                    case 1: return new FMUL(m_cpu, buildST0(), buildST0(), buildMEMFloatingPoint(64));
                    
                    /* fcom m64fp */
                    case 2: return new FCOM(m_cpu, buildMEMFloatingPoint(64));
                    
                    /* fcomp m64fp */
                    case 3: return new FCOMP(m_cpu, buildMEMFloatingPoint(64));
                    
                    /* fsub st0, m64fp */
                    case 4: return new FSUB(m_cpu, buildST0(), buildST0(), buildMEMFloatingPoint(64));
                    
                    /* fsubr st0, m64fp */
                    case 5: return new FSUB(m_cpu, buildST0(), buildMEMFloatingPoint(64), buildST0());
                    
                    /* fdiv st0, m64fp */
                    case 6: return new FDIV(m_cpu, buildST0(), buildST0(), buildMEMFloatingPoint(64));
                    
                    /* fdivr st0, m64fp */
                    case 7: return new FDIV(m_cpu, buildST0(), buildMEMFloatingPoint(64), buildST0());
                }
            }
            
            // Invalid instruction
            throw new IllegalArgumentException(String.format("Invalid FPU instruction 0xdc 0x%02x", m_modRMByte));
        };

        m_decoderTableDefault[0xdd] = ( /* escape */ ) -> {
            
            decodeMODRM();
            
            m_hasDecodedFPUInstruction = true;
            
            if(!m_cpu.hasFPU())
                return new ESCAPE(m_cpu);
            
            
            if(m_mod == 3) {
                
                switch(m_reg) {
                    
                    /* ffree sti */
                    case 0: return new FFREE(m_cpu, m_rm);
                    
                    /* fst sti */
                    case 2: return new FST(m_cpu, buildSTi());
                    
                    /* fstp sti */
                    case 3: return new FSTP(m_cpu, buildSTi());
                    
                    /* fucom sti */
                    case 4: return new FUCOM(m_cpu, buildSTi());
                    
                    /* fucomp sti */
                    case 5: return new FUCOMP(m_cpu, buildSTi());
                }
            }
            else {
                
                Pointer ptr;
                
                switch(m_reg) {
                    
                    /* fld m64fp */
                    case 0: return new FLD(m_cpu, buildMEMFloatingPoint(64));
                    
                    /* fst m64fp */
                    case 2: return new FST(m_cpu, buildMEMFloatingPoint(64));
                    
                    /* fstp m64fp */
                    case 3: return new FSTP(m_cpu, buildMEMFloatingPoint(64));
                    
                    /* frstor m16/32 */
                    case 4:
                        ptr = isAddressSize32() ? buildPointer32(m_addrDisp) :
                                                  buildPointer16(m_addrDisp);
                        
                        return isOperandSize32() ? new FRSTOR32(m_cpu, m_addrSeg, ptr) :
                                                   new FRSTOR16(m_cpu, m_addrSeg, ptr);
                    
                    /* fsave m16/32 */
                    case 6:
                        ptr = isAddressSize32() ? buildPointer32(m_addrDisp) :
                                                  buildPointer16(m_addrDisp);
                        
                        return isOperandSize32() ? new FSAVE32(m_cpu, m_addrSeg, ptr) :
                                                   new FSAVE16(m_cpu, m_addrSeg, ptr);
                    
                    /* fstsw m16 */
                    case 7: return new FSTSW(m_cpu, buildMEM16(0));
                }
            }
            
            // Invalid instruction
            throw new IllegalArgumentException(String.format("Invalid FPU instruction 0xdd 0x%02x", m_modRMByte));
        };

        m_decoderTableDefault[0xde] = ( /* escape */ ) -> {
            
            decodeMODRM();
            
            m_hasDecodedFPUInstruction = true;
            
            if(!m_cpu.hasFPU())
                return new ESCAPE(m_cpu);
            
            
            if(m_mod == 3) {
                
                /* faddp sti, st0 */
                if(m_modRMByte >= 0xc0 && m_modRMByte <= 0xc7)
                    return new FADDP(m_cpu, buildSTi(), buildSTi(), buildST0());
                
                /* fmulp sti, st0 */
                if(m_modRMByte >= 0xc8 && m_modRMByte <= 0xcf)
                    return new FMULP(m_cpu, buildSTi(), buildSTi(), buildST0());
                
                /* fcompp st0, st1 */
                if(m_modRMByte == 0xd9)
                    return new FCOMPP(m_cpu, buildST1());
                
                /* fsubrp sti, st0 */
                if(m_modRMByte >= 0xe0 && m_modRMByte <= 0xe7)
                    return new FSUBP(m_cpu, buildSTi(), buildST0(), buildSTi());
                
                /* fsubp sti, st0 */
                if(m_modRMByte >= 0xe8 && m_modRMByte <= 0xef)
                    return new FSUBP(m_cpu, buildSTi(), buildSTi(), buildST0());
                
                /* fdivrp sti, st0 */
                if(m_modRMByte >= 0xf0 && m_modRMByte <= 0xf7)
                    return new FDIVP(m_cpu, buildSTi(), buildST0(), buildSTi());
                
                /* fdivp sti, st0 */
                if(m_modRMByte >= 0xf8 && m_modRMByte <= 0xff)
                    return new FDIVP(m_cpu, buildSTi(), buildSTi(), buildST0());
            }
            else {
                
                switch(m_reg) {
                    
                    /* fiadd st0, m16i */
                    case 0: return new FADD(m_cpu, buildST0(), buildST0(), buildMEMInteger(16));
                    
                    /* fimul st0, m16i */
                    case 1: return new FMUL(m_cpu, buildST0(), buildST0(), buildMEMInteger(16));
                    
                    /* ficom st0, m16i */
                    case 2: return new FCOM(m_cpu, buildMEMInteger(16));
                    
                    /* ficomp st0, m16i */
                    case 3: return new FCOMP(m_cpu, buildMEMInteger(16));
                    
                    /* fisub st0, m16i */
                    case 4: return new FSUB(m_cpu, buildST0(), buildST0(), buildMEMInteger(16));
                    
                    /* fisubr st0, m16i */
                    case 5: return new FSUB(m_cpu, buildST0(), buildMEMInteger(16), buildST0());
                    
                    /* fidiv st0, m16i */
                    case 6: return new FDIV(m_cpu, buildST0(), buildST0(), buildMEMInteger(16));
                    
                    /* fidivr st0, m16i */
                    case 7: return new FDIV(m_cpu, buildST0(), buildMEMInteger(16), buildST0());
                }
            }
            
            // Invalid instruction
            throw new IllegalArgumentException(String.format("Invalid FPU instruction 0xde 0x%02x", m_modRMByte));
        };

        m_decoderTableDefault[0xdf] = ( /* escape */ ) -> {
            
            decodeMODRM();
            
            m_hasDecodedFPUInstruction = true;
            
            if(!m_cpu.hasFPU())
                return new ESCAPE(m_cpu);
            
            
            if(m_mod == 3) {
                
                /* fstsw ax */
                if(m_modRMByte == 0xe0)
                    return new FSTSW(m_cpu, buildAX());
            }
            else {
                
                switch(m_reg) {
                    
                    /* fild m16i */
                    case 0: return new FLD(m_cpu, buildMEMInteger(16));
                    
                    /* fist m16i */
                    case 2: return new FST(m_cpu, buildMEMInteger(16));
                    
                    /* fistp m16i */
                    case 3: return new FSTP(m_cpu, buildMEMInteger(16));
                    
                    /* fild m64i */
                    case 5: return new FLD(m_cpu, buildMEMInteger(64));
                    
                    /* fbstp m80bcd */
                    case 6: return new FSTP(m_cpu, buildMEMBinaryCodedDecimal());
                    
                    /* fistp m64i */
                    case 7: return new FSTP(m_cpu, buildMEMInteger(64));
                }
            }
            
            // Invalid instruction
            throw new IllegalArgumentException(String.format("Invalid FPU instruction 0xdf 0x%02x", m_modRMByte));
        };

        m_decoderTableDefault[0xe0] = ( /* loopnz rel8 */ ) -> {

            return finishBlock(new LOOPNZ(m_cpu, buildIPRelativeIMM8(), getCounter()));
        };

        m_decoderTableDefault[0xe1] = ( /* loopz rel8 */ ) -> {

            return finishBlock(new LOOPZ(m_cpu, buildIPRelativeIMM8(), getCounter()));
        };

        m_decoderTableDefault[0xe2] = ( /* loop rel8 */ ) -> {

            return finishBlock(new LOOP(m_cpu, buildIPRelativeIMM8(), getCounter()));
        };

        m_decoderTableDefault[0xe3] = ( /* jcxz/jecxz rel8 */ ) -> {

            return finishBlock(new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM8(), buildConditionCTRZero()));
        };

        m_decoderTableDefault[0xe4] = ( /* in al, imm8 */ ) -> {

            return new IN8(m_cpu, buildIMM8());
        };

        m_decoderTableDefault[0xe5] = ( /* in ax/eax, imm8 */ ) -> {

            return isOperandSize32() ? new IN32(m_cpu, buildIMM8()) :
                                       new IN16(m_cpu, buildIMM8());
        };

        m_decoderTableDefault[0xe6] = ( /* out imm8, al */ ) -> {

            return new OUT8(m_cpu, buildIMM8());
        };

        m_decoderTableDefault[0xe7] = ( /* out imm8, ax/eax */ ) -> {

            return isOperandSize32() ? new OUT32(m_cpu, buildIMM8()) :
                                       new OUT16(m_cpu, buildIMM8());
        };

        m_decoderTableDefault[0xe8] = ( /* call rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new CALL_NEAR(m_cpu, buildIPRelativeIMM32(), true) :
                                              new CALL_NEAR(m_cpu, buildIPRelativeIMM16(), false));
        };

        m_decoderTableDefault[0xe9] = ( /* jmp rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP(m_cpu, buildIPRelativeIMM32()) :
                                              new JMP(m_cpu, buildIPRelativeIMM16()));
        };

        m_decoderTableDefault[0xea] = ( /* jmp far imm16:16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_FAR(m_cpu, buildIMM32(), buildIMM16(), true) :
                                              new JMP_FAR(m_cpu, buildIMM16(), buildIMM16(), false));
        };

        m_decoderTableDefault[0xeb] = ( /* jmp rel8 */ ) -> {

            return finishBlock(new JMP(m_cpu, buildIPRelativeIMM8()));
        };

        m_decoderTableDefault[0xec] = ( /* in al, dx */ ) -> {

            return new IN8(m_cpu, buildDX());
        };

        m_decoderTableDefault[0xed] = ( /* in ax/eax, dx */ ) -> {

            return isOperandSize32() ? new IN32(m_cpu, buildDX()) :
                                       new IN16(m_cpu, buildDX());
        };

        m_decoderTableDefault[0xee] = ( /* out dx, al */ ) -> {

            return new OUT8(m_cpu, buildDX());
        };

        m_decoderTableDefault[0xef] = ( /* out dx, ax/eax */ ) -> {

            return isOperandSize32() ? new OUT32(m_cpu, buildDX()) :
                                       new OUT16(m_cpu, buildDX());
        };

        m_decoderTableDefault[0xf0] = ( /* lock */ ) -> {

            m_prefixLock = true;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0xf1] = ( /* int 1h */ ) -> {

            return finishBlock(new INT(m_cpu, buildImmediate(1)));
        };

        m_decoderTableDefault[0xf2] = ( /* repnz */ ) -> {

            m_prefixRepNZ = true;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0xf3] = ( /* repz */ ) -> {

            m_prefixRepZ = true;
            
            return m_decoderTableDefault[readIMM8()].decode();
        };

        m_decoderTableDefault[0xf4] = ( /* halt */ ) -> {

            return finishBlock(new HALT(m_cpu));
        };

        m_decoderTableDefault[0xf5] = ( /* cmc */ ) -> {

            return new CMC(m_cpu);
        };

        m_decoderTableDefault[0xf6] = ( /* GRP3a rm8 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* test rm8, imm8 */
                case 0:
                case 1: return new TEST8(m_cpu, buildRM8(), buildIMM8());

                /* not rm8 */
                case 2: return new NOT(m_cpu, buildRM8());

                /* neg rm8 */
                case 3: return new NEG8(m_cpu, buildRM8());

                /* mul al, rm8 */
                case 4: return new MUL8(m_cpu, buildRM8());

                /* imul al, rm8 */
                case 5: return new IMUL8(m_cpu, buildRM8());

                /* div al, rm8 */
                case 6: return new DIV8(m_cpu, buildRM8());

                /* idiv al, rm8 */
                case 7: return new IDIV8(m_cpu, buildRM8());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xf7] = ( /* GRP3b rm16/32 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* test rm16/32, imm16/32 */
                case 0:
                case 1: return isOperandSize32() ? new TEST32(m_cpu, buildRM32(), buildIMM32()) :
                                                   new TEST16(m_cpu, buildRM16(), buildIMM16());

                /* not rm16/32 */
                case 2: return isOperandSize32() ? new NOT(m_cpu, buildRM32()) :
                                                   new NOT(m_cpu, buildRM16());

                /* neg rm16/32 */
                case 3: return isOperandSize32() ? new NEG32(m_cpu, buildRM32()) :
                                                   new NEG16(m_cpu, buildRM16());

                /* mul al, rm16/32 */
                case 4: return isOperandSize32() ? new MUL32(m_cpu, buildRM32()) :
                                                   new MUL16(m_cpu, buildRM16());

                /* imul al, rm16/32 */
                case 5: return isOperandSize32() ? new IMUL32(m_cpu, buildRM32()) :
                                                   new IMUL16(m_cpu, buildRM16());

                /* div al, rm16/32 */
                case 6: return isOperandSize32() ? new DIV32(m_cpu, buildRM32()) :
                                                   new DIV16(m_cpu, buildRM16());

                /* idiv al, rm16/32 */
                case 7: return isOperandSize32() ? new IDIV32(m_cpu, buildRM32()) :
                                                   new IDIV16(m_cpu, buildRM16());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xf8] = ( /* clc */ ) -> {

            return new CLC(m_cpu);
        };

        m_decoderTableDefault[0xf9] = ( /* stc */ ) -> {

            return new STC(m_cpu);
        };

        m_decoderTableDefault[0xfa] = ( /* cli */ ) -> {

            return new CLI(m_cpu);
        };

        m_decoderTableDefault[0xfb] = ( /* sti */ ) -> {

            return finishBlock(new STI(m_cpu));
        };

        m_decoderTableDefault[0xfc] = ( /* cld */ ) -> {

            return new CLD(m_cpu);
        };

        m_decoderTableDefault[0xfd] = ( /* std */ ) -> {

            return new STD(m_cpu);
        };

        m_decoderTableDefault[0xfe] = ( /* grp4 rm8 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* inc rm8 */
                case 0: return new INC8(m_cpu, buildRM8());

                /* dec rm8 */
                case 1: return new DEC8(m_cpu, buildRM8());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTableDefault[0xff] = ( /* grp5 rm16/32 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* inc rm16/32 */
                case 0: return isOperandSize32() ? new INC32(m_cpu, buildRM32()) :
                                                   new INC16(m_cpu, buildRM16());

                /* dec rm16/32 */
                case 1: return isOperandSize32() ? new DEC32(m_cpu, buildRM32()) :
                                                   new DEC16(m_cpu, buildRM16());

                /* call rm16/32 */
                case 2:
                    return finishBlock(isOperandSize32() ? new CALL_NEAR(m_cpu, buildRM32(), true) :
                                                      new CALL_NEAR(m_cpu, buildRM16(), false));

                /* call m16:16/32 */
                case 3:
                    if(m_mod == 3)
                        throw CPUException.getInvalidOpcode();

                    return finishBlock(isOperandSize32() ? new CALL_FAR(m_cpu, buildMEM32(0), buildMEM16(4), true) :
                                                      new CALL_FAR(m_cpu, buildMEM16(0), buildMEM16(2), false));

                /* jmp rm16/32 */
                case 4:
                    return finishBlock(isOperandSize32() ? new JMP(m_cpu, buildRM32()) :
                                                      new JMP(m_cpu, buildRM16()));

                /* jmp m16:16/32 */
                case 5:
                    if(m_mod == 3)
                        throw CPUException.getInvalidOpcode();

                    return finishBlock(isOperandSize32() ? new JMP_FAR(m_cpu, buildMEM32(0), buildMEM16(4), true) :
                                                      new JMP_FAR(m_cpu, buildMEM16(0), buildMEM16(2), false));

                /* push rm16/32 */
                case 6:
                    return isOperandSize32() ? new PUSH32(m_cpu, buildRM32()) :
                                               new PUSH16(m_cpu, buildRM16());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Extended table (0x0f)">
        
    private void fillExtendedTable() {
        
        m_decoderTable0F[0x00] = ( /* 0x0f grp6 rm16 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* sldt rm16 */
                case 0:
                    return new SLDT(m_cpu, buildRM16());

                /* str rm16 */
                case 1:
                    return new STR(m_cpu, buildRM16());

                /* lldt rm16 */
                case 2:
                    return new LLDT(m_cpu, buildRM16());

                /* ltr rm16 */
                case 3:
                    return new LTR(m_cpu, buildRM16());

                /* verr rm16 */
                case 4:
                    return new VERR(m_cpu, buildRM16());

                /* verw rm16 */
                case 5:
                    return new VERW(m_cpu, buildRM16());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0x01] = ( /* 0x0f grp7 rm16/32 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                /* sgdt m16:32 */
                case 0:
                    if(m_mod == 3)
                        throw CPUException.getInvalidOpcode();

                    return new SGDT(m_cpu, buildMEM16(0), buildMEM32(2));

                /* sidt m16:32 */
                case 1: 
                    if(m_mod == 3)
                        throw CPUException.getInvalidOpcode();

                    return isOperandSize32() ? new SIDT32(m_cpu, buildMEM16(0), buildMEM32(2)) :
                                               new SIDT16(m_cpu, buildMEM16(0), buildMEM32(2));

                /* lgdt m16:32 */
                case 2:
                    if(m_mod == 3)
                        throw CPUException.getInvalidOpcode();

                    return isOperandSize32() ? new LGDT32(m_cpu, buildMEM16(0), buildMEM32(2)) :
                                               new LGDT16(m_cpu, buildMEM16(0), buildMEM32(2));

                /* lidt m16:32 */
                case 3:
                    if(m_mod == 3)
                        throw CPUException.getInvalidOpcode();

                    return isOperandSize32() ? new LIDT32(m_cpu, buildMEM16(0), buildMEM32(2)) :
                                               new LIDT16(m_cpu, buildMEM16(0), buildMEM32(2));

                /* smsw rm16/32 */
                case 4:
                    return isOperandSize32() ? new SMSW(m_cpu, buildRM32(true)) :
                                               new SMSW(m_cpu, buildRM16());

                /* lmsw rm16 */    
                case 6:
                    return finishBlock(new LMSW(m_cpu, buildRM16()));

                /* invlpg a16/32 (486+) */
                case 7:
                    requiresCPUOfAtLeast(CPUType.i486);
                    if(m_mod == 3)
                        throw CPUException.getInvalidOpcode();

                    return new INVLPG(m_cpu, buildADDR(0));

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0x02] = ( /* 0x0f lar r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new LAR(m_cpu, buildREG32(), buildRM32(), true) :
                                       new LAR(m_cpu, buildREG16(), buildRM16(), false);
        };

        m_decoderTable0F[0x03] = ( /* 0x0f lsl r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new LSL(m_cpu, buildREG32(), buildRM32()) :
                                       new LSL(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTable0F[0x04] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x05] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x06] = ( /* 0x0f clts */ ) -> {

            return new CLTS(m_cpu);
        };

        m_decoderTable0F[0x07] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x08] = ( /* 0x0f invd (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);
            return new INVD(m_cpu);
        };

        m_decoderTable0F[0x09] = ( /* 0x0f wbinvd (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);
            return new WBINVD(m_cpu);
        };

        m_decoderTable0F[0x0a] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x0b] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x0c] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x0d] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x0e] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x0f] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x10] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x11] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x12] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x13] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x14] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x15] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x16] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x17] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x18] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x19] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x1a] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x1b] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x1c] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x1d] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x1e] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x1f] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x20] = ( /* 0x0f mov r32, cr0/2/3 */ ) -> {

            decodeMODRM();

            if(m_mod != 3)
                throw CPUException.getInvalidOpcode();

            switch(m_reg) {

                case 0: return new MOV(m_cpu, buildRM32(), buildCR0());
                case 2: return new MOV(m_cpu, buildRM32(), buildCR2());
                case 3: return new MOV(m_cpu, buildRM32(), buildCR3());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0x21] = ( /* 0x0f mov r32, dr0-3..6-7 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                case 0: return new MOVSpecial(m_cpu, buildRM32(), buildDR0());
                case 1: return new MOVSpecial(m_cpu, buildRM32(), buildDR1());
                case 2: return new MOVSpecial(m_cpu, buildRM32(), buildDR2());
                case 3: return new MOVSpecial(m_cpu, buildRM32(), buildDR3());
                case 6: return new MOVSpecial(m_cpu, buildRM32(), buildDR6());
                case 7: return new MOVSpecial(m_cpu, buildRM32(), buildDR7());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0x22] = ( /* 0x0f mov cr0/2/3, r32 */ ) -> {

            decodeMODRM();

            if(m_mod != 3)
                throw CPUException.getInvalidOpcode();

            switch(m_reg) {

                case 0: return finishBlock(new MOVSpecial(m_cpu, buildCR0(), buildRM32()));
                case 2: return new MOVSpecial(m_cpu, buildCR2(), buildRM32());
                case 3: return finishBlock(new MOVSpecial(m_cpu, buildCR3(), buildRM32()));

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0x23] = ( /* 0x0f  mov dr0-3..6-7, r32 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                case 0: return new MOVSpecial(m_cpu, buildDR0(), buildRM32());
                case 1: return new MOVSpecial(m_cpu, buildDR1(), buildRM32());
                case 2: return new MOVSpecial(m_cpu, buildDR2(), buildRM32());
                case 3: return new MOVSpecial(m_cpu, buildDR3(), buildRM32());
                case 6: return new MOVSpecial(m_cpu, buildDR6(), buildRM32());
                case 7: return new MOVSpecial(m_cpu, buildDR7(), buildRM32());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0x24] = ( /* 0x0f mov t6-7, r32 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                case 6: return new MOVSpecial(m_cpu, buildTR6(), buildRM32());
                case 7: return new MOVSpecial(m_cpu, buildTR7(), buildRM32());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0x25] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x26] = ( /* 0x0f mov r32, t6-7 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                case 6: return new MOVSpecial(m_cpu, buildRM32(), buildTR6());
                case 7: return new MOVSpecial(m_cpu, buildRM32(), buildTR7());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0x27] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x28] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x29] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x2a] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x2b] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x2c] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x2d] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x2e] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x2f] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x30] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x31] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x32] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x33] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x34] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x35] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x36] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x37] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x38] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x39] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x3a] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x3b] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x3c] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x3d] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x3e] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x3f] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x40] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x41] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x42] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x43] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x44] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x45] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x46] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x47] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x48] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x49] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x4a] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x4b] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x4c] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x4d] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x4e] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x4f] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x50] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x51] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x52] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x53] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x54] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x55] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x56] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x57] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x58] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x59] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x5a] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x5b] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x5c] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x5d] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x5e] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x5f] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x60] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x61] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x62] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x63] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x64] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x65] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x66] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x67] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x68] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x69] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x6a] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x6b] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x6c] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x6d] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x6e] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x6f] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x70] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x71] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x72] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x73] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x74] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x75] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x76] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x77] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x78] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x79] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x7a] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x7b] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x7c] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x7d] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x7e] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x7f] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0x80] = ( /* 0x0f jo rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionOverflow()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionOverflow()));
        };

        m_decoderTable0F[0x81] = ( /* 0x0f jno rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionNotOverflow()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionNotOverflow()));
        };

        m_decoderTable0F[0x82] = ( /* 0x0f jb rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionBellow()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionBellow()));
        };

        m_decoderTable0F[0x83] = ( /* 0x0f jnb rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionNotBellow()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionNotBellow()));
        };

        m_decoderTable0F[0x84] = ( /* 0x0f jz rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionZero()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionZero()));
        };

        m_decoderTable0F[0x85] = ( /* 0x0f jnz rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionNotZero()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionNotZero()));
        };

        m_decoderTable0F[0x86] = ( /* 0x0f jbe rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionBellowOrEqual()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionBellowOrEqual()));
        };

        m_decoderTable0F[0x87] = ( /* 0x0f jnbe rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionNotBellowOrEqual()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionNotBellowOrEqual()));
        };

        m_decoderTable0F[0x88] = ( /* 0x0f js rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionSign()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionSign()));
        };

        m_decoderTable0F[0x89] = ( /* 0x0f jns rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionNotSign()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionNotSign()));
        };

        m_decoderTable0F[0x8a] = ( /* 0x0f jp rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionParity()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionParity()));
        };

        m_decoderTable0F[0x8b] = ( /* 0x0f jnp rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionNotParity()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionNotParity()));
        };

        m_decoderTable0F[0x8c] = ( /* 0x0f jl rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionLess()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionLess()));
        };

        m_decoderTable0F[0x8d] = ( /* 0x0f jnl rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionNotLess()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionNotLess()));
        };

        m_decoderTable0F[0x8e] = ( /* 0x0f jle rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionLessOrEqual()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionLessOrEqual()));
        };

        m_decoderTable0F[0x8f] = ( /* 0x0f jnle rel16/32 */ ) -> {

            return finishBlock(isOperandSize32() ? new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM32(), buildConditionNotLessOrEqual()) :
                                              new JMP_CONDITIONAL(m_cpu, buildIPRelativeIMM16(), buildConditionNotLessOrEqual()));
        };

        m_decoderTable0F[0x90] = ( /* 0x0f seto rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionOverflow());
        };

        m_decoderTable0F[0x91] = ( /* 0x0f setno rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionNotOverflow());
        };

        m_decoderTable0F[0x92] = ( /* 0x0f setb rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionBellow());
        };

        m_decoderTable0F[0x93] = ( /* 0x0f setnb rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionNotBellow());
        };

        m_decoderTable0F[0x94] = ( /* 0x0f setz rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionZero());
        };

        m_decoderTable0F[0x95] = ( /* 0x0f setnz rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionNotZero());
        };

        m_decoderTable0F[0x96] = ( /* 0x0f setbe rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionBellowOrEqual());
        };

        m_decoderTable0F[0x97] = ( /* 0x0f setnbe rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionNotBellowOrEqual());
        };

        m_decoderTable0F[0x98] = ( /* 0x0f sets rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionSign());
        };

        m_decoderTable0F[0x99] = ( /* 0x0f setns rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionNotSign());
        };

        m_decoderTable0F[0x9a] = ( /* 0x0f setp rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionParity());
        };

        m_decoderTable0F[0x9b] = ( /* 0x0f setnp rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionNotParity());
        };

        m_decoderTable0F[0x9c] = ( /* 0x0f setl rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionLess());
        };

        m_decoderTable0F[0x9d] = ( /* 0x0f setnl rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionNotLess());
        };

        m_decoderTable0F[0x9e] = ( /* 0x0f setle rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionLessOrEqual());
        };

        m_decoderTable0F[0x9f] = ( /* 0x0f setnle rm8 */ ) -> {

            decodeMODRM();

            return new SET(m_cpu, buildRM8(), buildConditionNotLessOrEqual());
        };

        m_decoderTable0F[0xa0] = ( /* 0x0f push fs */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildFS()) :
                                       new PUSH16(m_cpu, buildFS());
        };

        m_decoderTable0F[0xa1] = ( /* 0x0f pop fs */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildFS()) :
                                       new POP16(m_cpu, buildFS());
        };

        m_decoderTable0F[0xa2] = ( /* 0x0f m_cpuid (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new CPUID(m_cpu);
        };

        m_decoderTable0F[0xa3] = ( /* 0x0f bt rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3) {

                return isOperandSize32() ? new BT32_REG(m_cpu, buildRM32(), buildREG32()) :
                                           new BT16_REG(m_cpu, buildRM16(), buildREG16());
            }
            else {

                return isOperandSize32() ? new BT32_MEM(m_cpu, buildMEM32(0), buildREG32()) :
                                           new BT16_MEM(m_cpu, buildMEM16(0), buildREG16());
            }
        };

        m_decoderTable0F[0xa4] = ( /* 0x0f shld rm16/32, r16/32, imm8 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new SHLD32(m_cpu, buildRM32(), buildREG32(), buildIMM8()) :
                                       new SHLD16(m_cpu, buildRM16(), buildREG16(), buildIMM8());
        };

        m_decoderTable0F[0xa5] = ( /* 0x0f shld rm16/32, r16/32, cl */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new SHLD32(m_cpu, buildRM32(), buildREG32(), buildCL()) :
                                       new SHLD16(m_cpu, buildRM16(), buildREG16(), buildCL());
        };

        m_decoderTable0F[0xa6] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xa7] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xa8] = ( /* 0x0f push gs */ ) -> {

            return isOperandSize32() ? new PUSH32(m_cpu, buildGS()) :
                                       new PUSH16(m_cpu, buildGS());
        };

        m_decoderTable0F[0xa9] = ( /* 0x0f pop gs */ ) -> {

            return isOperandSize32() ? new POP32(m_cpu, buildGS()) :
                                       new POP16(m_cpu, buildGS());
        };

        m_decoderTable0F[0xaa] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xab] = ( /* 0x0f bts rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3) {

                return isOperandSize32() ? new BTS32_REG(m_cpu, buildRM32(), buildREG32()) :
                                           new BTS16_REG(m_cpu, buildRM16(), buildREG16());
            }
            else {

                return isOperandSize32() ? new BTS32_MEM(m_cpu, buildMEM32(0), buildREG32()) :
                                           new BTS16_MEM(m_cpu, buildMEM16(0), buildREG16());
            }
        };

        m_decoderTable0F[0xac] = ( /* 0x0f shrd rm16/32, r16/32, imm8 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new SHRD32(m_cpu, buildRM32(), buildREG32(), buildIMM8()) :
                                       new SHRD16(m_cpu, buildRM16(), buildREG16(), buildIMM8());
        };

        m_decoderTable0F[0xad] = ( /* 0x0f shrd rm16/32, r16/32, cl */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new SHRD32(m_cpu, buildRM32(), buildREG32(), buildCL()) :
                                       new SHRD16(m_cpu, buildRM16(), buildREG16(), buildCL());
        };

        m_decoderTable0F[0xae] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xaf] = ( /* 0x0f imul r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new IMUL32_2(m_cpu, buildREG32(), buildREG32(), buildRM32()) :
                                       new IMUL16_2(m_cpu, buildREG16(), buildREG16(), buildRM16());
        };

        m_decoderTable0F[0xb0] = ( /* 0x0f cmpxchg rm8, r8 (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            decodeMODRM();

            return new CMPXCHG8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTable0F[0xb1] = ( /* 0x0f cmpxchg rm16/32, r16/32 (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            decodeMODRM();

            return isOperandSize32() ? new CMPXCHG32(m_cpu, buildRM32(), buildREG32()) :
                                       new CMPXCHG16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTable0F[0xb2] = ( /* 0x0f lss r16/32, m16:16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3)
                throw CPUException.getInvalidOpcode();

            return isOperandSize32() ? new LSEG(m_cpu, buildSS(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                       new LSEG(m_cpu, buildSS(), buildREG16(), buildMEM16(2), buildMEM16(0));
        };

        m_decoderTable0F[0xb3] = ( /* 0x0f btr rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3) {

                return isOperandSize32() ? new BTR32_REG(m_cpu, buildRM32(), buildREG32()) :
                                           new BTR16_REG(m_cpu, buildRM16(), buildREG16());
            }
            else {

                return isOperandSize32() ? new BTR32_MEM(m_cpu, buildMEM32(0), buildREG32()) :
                                           new BTR16_MEM(m_cpu, buildMEM16(0), buildREG16());
            }
        };

        m_decoderTable0F[0xb4] = ( /* 0x0f lfs r16/32, m16:16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3)
                throw CPUException.getInvalidOpcode();

            return isOperandSize32() ? new LSEG(m_cpu, buildFS(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                       new LSEG(m_cpu, buildFS(), buildREG16(), buildMEM16(2), buildMEM16(0));
        };

        m_decoderTable0F[0xb5] = ( /* 0x0f lgs r16/32, m16:16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3)
                throw CPUException.getInvalidOpcode();

            return isOperandSize32() ? new LSEG(m_cpu, buildGS(), buildREG32(), buildMEM16(4), buildMEM32(0)) :
                                       new LSEG(m_cpu, buildGS(), buildREG16(), buildMEM16(2), buildMEM16(0));
        };

        m_decoderTable0F[0xb6] = ( /* 0x0f movzx r16/32, rm8 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new MOVZX(m_cpu, buildREG32(), buildRM8()) :
                                       new MOVZX(m_cpu, buildREG16(), buildRM8());
        };

        m_decoderTable0F[0xb7] = ( /* 0x0f movzx r32, rm16 */ ) -> {

            decodeMODRM();

            return new MOVZX(m_cpu, buildREG32(), buildRM16());
        };

        m_decoderTable0F[0xb8] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xb9] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xba] = ( /* 0x0f btx rm16/32, imm8 */ ) -> {

            decodeMODRM();

            switch(m_reg) {

                case 4: return isOperandSize32() ? new BT32_REG(m_cpu, buildRM32(), buildIMM8()) :
                                                   new BT16_REG(m_cpu, buildRM16(), buildIMM8());

                case 5: return isOperandSize32() ? new BTS32_REG(m_cpu, buildRM32(), buildIMM8()) :
                                                   new BTS16_REG(m_cpu, buildRM16(), buildIMM8());

                case 6: return isOperandSize32() ? new BTR32_REG(m_cpu, buildRM32(), buildIMM8()) :
                                                   new BTR16_REG(m_cpu, buildRM16(), buildIMM8());

                case 7: return isOperandSize32() ? new BTC32_REG(m_cpu, buildRM32(), buildIMM8()) :
                                                   new BTC16_REG(m_cpu, buildRM16(), buildIMM8());

                default:
                    throw CPUException.getInvalidOpcode();
            }
        };

        m_decoderTable0F[0xbb] = ( /* 0x0f btc rm16/32, r16/32 */ ) -> {

            decodeMODRM();

            if(m_mod == 3) {

                return isOperandSize32() ? new BTC32_REG(m_cpu, buildRM32(), buildREG32()) :
                                           new BTC16_REG(m_cpu, buildRM16(), buildREG16());
            }
            else {

                return isOperandSize32() ? new BTC32_MEM(m_cpu, buildMEM32(0), buildREG32()) :
                                           new BTC16_MEM(m_cpu, buildMEM16(0), buildREG16());
            }
        };

        m_decoderTable0F[0xbc] = ( /* 0x0f bsf r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new BSF32(m_cpu, buildREG32(), buildRM32()) :
                                       new BSF16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTable0F[0xbd] = ( /* 0x0f bsr r16/32, rm16/32 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new BSR32(m_cpu, buildREG32(), buildRM32()) :
                                       new BSR16(m_cpu, buildREG16(), buildRM16());
        };

        m_decoderTable0F[0xbe] = ( /* 0x0f movsx r16/32, rm8 */ ) -> {

            decodeMODRM();

            return isOperandSize32() ? new MOVSX8TO32(m_cpu, buildREG32(), buildRM8()) :
                                       new MOVSX8TO16(m_cpu, buildREG16(), buildRM8());
        };

        m_decoderTable0F[0xbf] = ( /* 0x0f movsx r32, rm16 */ ) -> {

            decodeMODRM();

            return new MOVSX16TO32(m_cpu, buildREG32(), buildRM16());
        };

        m_decoderTable0F[0xc0] = ( /* 0x0f xadd rm8, r8 (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            decodeMODRM();

            return new XADD8(m_cpu, buildRM8(), buildREG8());
        };

        m_decoderTable0F[0xc1] = ( /* 0x0f xadd rm16/32, r16/32 (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            decodeMODRM();

            return isOperandSize32() ? new XADD32(m_cpu, buildRM32(), buildREG32()) :
                                       new XADD16(m_cpu, buildRM16(), buildREG16());
        };

        m_decoderTable0F[0xc2] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xc3] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xc4] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xc5] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xc6] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xc7] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xc8] = ( /* 0x0f bswap eax (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new BSWAP(m_cpu, buildEAX());
        };

        m_decoderTable0F[0xc9] = ( /* 0x0f bswap ecx (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new BSWAP(m_cpu, buildECX());
        };

        m_decoderTable0F[0xca] = ( /* 0x0f bswap edx (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new BSWAP(m_cpu, buildEDX());
        };

        m_decoderTable0F[0xcb] = ( /* 0x0f bswap ebx (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new BSWAP(m_cpu, buildEBX());
        };

        m_decoderTable0F[0xcc] = ( /* 0x0f bswap esp (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new BSWAP(m_cpu, buildESP());
        };

        m_decoderTable0F[0xcd] = ( /* 0x0f bswap ebp (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new BSWAP(m_cpu, buildEBP());
        };

        m_decoderTable0F[0xce] = ( /* 0x0f bswap esi (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new BSWAP(m_cpu, buildESI());
        };

        m_decoderTable0F[0xcf] = ( /* 0x0f bswap edi (486+) */ ) -> {

            requiresCPUOfAtLeast(CPUType.i486);

            return new BSWAP(m_cpu, buildEDI());
        };

        m_decoderTable0F[0xd0] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd1] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd2] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd3] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd4] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd5] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd6] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd7] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd8] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xd9] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xda] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xdb] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xdc] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xdd] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xde] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xdf] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe0] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe1] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe2] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe3] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe4] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe5] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe6] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe7] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe8] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xe9] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xea] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xeb] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xec] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xed] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xee] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xef] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf0] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf1] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf2] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf3] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf4] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf5] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf6] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf7] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf8] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xf9] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xfa] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xfb] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xfc] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xfd] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xfe] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };

        m_decoderTable0F[0xff] = ( /* 0x0f Invalid */ ) -> {

            throw CPUException.getInvalidOpcode();
        };
    }
    
    // </editor-fold>
    
    // </editor-fold>
}
