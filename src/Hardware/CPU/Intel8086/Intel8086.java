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
package Hardware.CPU.Intel8086;

import Hardware.CPU.CPU;
import Hardware.HardwareComponent;
import IOMap.IOMap;
import Hardware.CPU.Intel8086.Codeblock.CodeBlock;
import Hardware.CPU.Intel8086.Decoder.Decoder;
import Hardware.CPU.Intel8086.Exceptions.*;
import Hardware.CPU.Intel8086.Register.*;
import Hardware.CPU.Intel8086.Segments.Segment;
import Hardware.InterruptController.PICs;
import Main.Systems.ComponentConfig;
import MemoryMap.MemoryMap;
import Scheduler.Scheduler;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map.Entry;



public final class Intel8086 implements HardwareComponent,
                                        CPU {
    
    /* ----------------------------------------------------- *
     * Intel 8086 register set                               *
     * ----------------------------------------------------- */
    public final Reg16 AX, BX, CX, DX;
    public final Reg16 SP, BP, SI, DI;
    public final Reg16 IP;
    public final Reg8 AH, BH, CH, DH;
    public final Reg8 AL, BL, CL, DL;
    public final Segment CS, DS, ES, SS;
    public final Flags FLAGS;
    public boolean HALTED;
    
    /* ----------------------------------------------------- *
     * Code block decoder                                    *
     * ----------------------------------------------------- */
    private final Decoder m_decoder;
    
    /* ----------------------------------------------------- *
     * Code block cache                                      *
     * ----------------------------------------------------- */
    private final class CodeBlockCache {
        
        public final HashMap<Integer, CodeBlock> map = new HashMap<>();
        public final boolean[] isParagraphInvalidated = new boolean[0x100];
        public boolean isInvalidated;
    }
    private final CodeBlockCache[] m_codeBlockCache;
    private final Integer[] m_integerLUT;
    private long m_timeNextCacheClear;
    private CodeBlock m_currentBlock;
    
    /* ----------------------------------------------------- *
     * Reference to the i/o map and interrupt controller     *
     * ----------------------------------------------------- */
    private final IOMap m_ioMap;
    private final MemoryMap m_memMap;
    private final Scheduler m_scheduler;
    
    /* ----------------------------------------------------- *
     * Reference to the interrupt controller                 *
     * ----------------------------------------------------- */
    private PICs m_pic;
    
    
    
    public Intel8086(IOMap ioMap,
                     MemoryMap memMap,
                     Scheduler scheduler) {
        
        m_ioMap = ioMap;
        m_memMap = memMap;
        m_scheduler = scheduler;
        
        // Initialize register set
        AX = new Reg16("ax"); BX = new Reg16("bx");
        CX = new Reg16("cx"); DX = new Reg16("dx");
        SP = new Reg16("sp"); BP = new Reg16("bp");
        SI = new Reg16("si"); DI = new Reg16("di");
        IP = new Reg16("ip");
        
        AH = new Reg8Hi("ah", AX); BH = new Reg8Hi("bh", BX);
        CH = new Reg8Hi("ch", CX); DH = new Reg8Hi("dh", DX);
        AL = new Reg8Lo("al", AX); BL = new Reg8Lo("bl", BX);
        CL = new Reg8Lo("cl", CX); DL = new Reg8Lo("dl", DX);
        
        CS = new Segment("cs"); DS = new Segment("ds");
        ES = new Segment("es"); SS = new Segment("ss");
        
        FLAGS = new Flags();
        
        // Initialize code block decoder
        m_decoder = new Decoder(this);
        
        // Initialize code block cache
        m_codeBlockCache = new CodeBlockCache[memMap.MAP_PAGE_COUNT];
        for(int i = 0; i < m_codeBlockCache.length; i++)
            m_codeBlockCache[i] = new CodeBlockCache();
        
        // Initialize Integer lookup table. This is needed in order to bypass
        // the Integer autoboxing in findBlock()
        m_integerLUT = new Integer[0x1000];
        for(int i = 0; i < m_integerLUT.length; i++)
            m_integerLUT[i] = i;
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        // Reset registers
        AX.reset(); BX.reset(); CX.reset(); DX.reset();
        SP.reset(); BP.reset(); SI.reset(); DI.reset();
        IP.reset(); DS.reset(); ES.reset(); SS.reset();
        CS.setSelector(0xffff); FLAGS.reset(); HALTED = false;
        
        // Reset code block cache
        for(CodeBlockCache codeBlockCache : m_codeBlockCache) {
            
            codeBlockCache.map.clear();
            
            Arrays.fill(codeBlockCache.isParagraphInvalidated, false);
            codeBlockCache.isInvalidated = false;
        }
        
        // Time when the cache gets cleared from blocks that were not used for
        // a certain amount of time
        m_timeNextCacheClear = System.currentTimeMillis() + 10000L;
    }

    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof PICs)
            m_pic = (PICs)component;
    }
    
    @Override
    public String getConfigCategory() {
        
        return "CPU";
    }

    @Override
    public void provideConfigValues(ComponentConfig.Builder builder) {
        
        builder.value("Frequency", "16000000", ComponentConfig.Type.ToggleGroup, this::setFrequency)
               .option("4.77 MHz", "4772727")
               .option("8 MHz", "8000000")
               .option("10 MHz", "10000000")
               .option("12 MHz", "12000000")
               .option("16 MHz", "16000000")
               .build();
    }
    
    private boolean setFrequency(String value) {
        
        m_scheduler.setBaseFrequency(Float.valueOf(value), true);
        return true;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of CPU">
    
    @Override
    public void run(int numBlocks) {
            
        while(numBlocks-- > 0) {

            if(isInterruptPending()) {

                HALTED = false;
                handleInterrupt(m_pic.getInterrupt(), true);

                updateClock(Scheduler.toFixedPoint(61));
            }
            if(HALTED) {

                updateClock(Scheduler.toFixedPoint(2));
            }
            else {

                try {

                    m_currentBlock = findBlock(CS.getBase(), IP.getValue());
                    m_currentBlock.run();
                }
                catch(DivisionException ex) {

                    // This exception is caused by div/idiv/aam instructions
                    handleInterrupt(0, true);
                }
                catch(InterruptException ex) {

                    // This exception is caused by an hw interrupt during
                    // the execution of a rep, repnz or repz instruction
                }
            }
        }
        
        // Remove all blocks that were not used since the last 10 seconds
        if(System.currentTimeMillis() >= m_timeNextCacheClear) {
            
            m_timeNextCacheClear = System.currentTimeMillis() + 10000L;
            
            removeUnusedBlocks();
        }
    }
    
    public void updateClock(int cycles) {
        
        m_scheduler.updateClock(cycles);
    }
        
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Interrupt handling">
    
    public boolean isInterruptPending() {
        
        return FLAGS.IF && m_pic.isPending();
    }
    
    public void handleInterrupt(int number, boolean clearIFlag) {
        
        // Push return address and flags
        pushStack(FLAGS.getValue());
        pushStack(CS.getSelector());
        pushStack(IP.getValue());
        
        // Set new flags and clear interrupt flag (if needed)
        FLAGS.TF = false;
        if(clearIFlag)
            FLAGS.IF = false;
        
        // Load new CS:IP
        int vectorOffset = number << 2;
        
        IP.setValue(readMEM16(0x0000, vectorOffset));
        CS.setSelector(readMEM16(0x0000, vectorOffset + 2));
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Codeblock cache">
    
    private void removeUnusedBlocks() {
        
        long minTime = System.currentTimeMillis() - 10000L;
        
        for(CodeBlockCache blockCache : m_codeBlockCache)
            blockCache.map.values().removeIf(block -> block.getLastExecutionTime() < minTime);
    }
    
    private CodeBlock findBlock(int base, int offset) throws DecoderException {
        
        // Get the code block cache of the current page
        int addr = (base + offset) & m_memMap.MAP_ADDR_MASK;
        int page = (addr >>> m_memMap.MAP_PAGE_BITS) & 0xff;
        CodeBlockCache blockCache = m_codeBlockCache[page];
        
        //
        // All invalidated blocks in this page must be removed from the cache
        // TODO: There must be a better solution for this
        //
        if(blockCache.isInvalidated) {
            
            blockCache.isInvalidated = false;
            
            // Check all 256 paragraphs of this page (1 page = 4k of memory, 1 paragraph = 16 byte)
            for(int i = 0; i < 256; i++) {
                
                if(blockCache.isParagraphInvalidated[i]) {
                    
                    blockCache.isParagraphInvalidated[i] = false;
                    
                    // I intentionally don't use blockCache.map.values().removeIf(...) here!
                    int pAddr = (addr & 0xff000) | (i << 4);
                    
                    Iterator<Entry<Integer, CodeBlock>> it = blockCache.map.entrySet().iterator();
                    while(it.hasNext()) {
                        
                        if(it.next().getValue().isInsideParagraph(pAddr))
                            it.remove();
                    }
                }
            }
        }
        
        // Return the cached code block. If this block is not yet in the cache
        // then decode and store it there...
        CodeBlock block;
        
        if((block = blockCache.map.get(m_integerLUT[addr & 0xfff])) == null) {

            block = m_decoder.decodeCodeBlock(base, offset);
            blockCache.map.put(m_integerLUT[addr & 0xfff], block);
        }
        
        return block;
    }
    
    private void invalidateBlocks(int address) {
        
        CodeBlockCache blockCache = m_codeBlockCache[(address >>> m_memMap.MAP_PAGE_BITS) & 0xff];
        
        blockCache.isParagraphInvalidated[(address >>> 4) & 0xff] = true;
        blockCache.isInvalidated = true;
        
        // In order for self-modifying code to work correctly, the current code
        // block must be aborted as soon as a memory access overwrites its code
        if(m_currentBlock.isInsideParagraph(address & 0xffff0))
            m_currentBlock.abort();
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Methods for I/O access">
    
    public int readIO8(int port) {
        
        return m_ioMap.readIO8(port);
    }
    
    public void writeIO8(int port, int data) {
        
        m_ioMap.writeIO8(port, data & 0xff);
    }
    
    public int readIO16(int port) {
        
        return m_ioMap.readIO8(port) |
              (m_ioMap.readIO8(port + 1) << 8);
    }
    
    public void writeIO16(int port, int data) {
        
        m_ioMap.writeIO8(port, data & 0xff);
        m_ioMap.writeIO8(port + 1, (data >>> 8) & 0xff);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Methods for memory access">
    
    public int readMEM8(int base, int offset) {
        
        return m_memMap.readMEM8(base + (offset & 0xffff));
    }
    
    public void writeMEM8(int base, int offset, int data) {
        
        int addr = base + (offset & 0xffff);
        
        m_memMap.writeMEM8(addr, data & 0xff);
        
        invalidateBlocks(addr);
    }
    
    public int readMEM16(int base, int offset) {
        
        if((offset & 0x01) == 0) {
            
            return m_memMap.readMEM16(base + (offset & 0xffff));
        }
            
        return m_memMap.readMEM8(base + (offset & 0xffff)) |
              (m_memMap.readMEM8(base + ((offset + 1) & 0xffff)) << 8);
    }
    
    public void writeMEM16(int base, int offset, int data) {
        
        int addrA = base + (offset & 0xffff);
        
        if((offset & 0x01) == 0) {
            
            invalidateBlocks(addrA);
            
            m_memMap.writeMEM16(addrA, data & 0xffff);
        }
        else {
            
            int addrB = base + ((offset + 1) & 0xffff);
            
            m_memMap.writeMEM8(addrA, data & 0xff);
            m_memMap.writeMEM8(addrB, (data >>> 8) & 0xff);
            
            invalidateBlocks(addrA);
            invalidateBlocks(addrB);
        }
    }
    
    public void pushStack(int data) {
        
        SP.setValue(SP.getValue() - 2);
        
        writeMEM16(SS.getBase(), SP.getValue(), data);
    }
    
    public int popStack() {
        
        int data = readMEM16(SS.getBase(), SP.getValue());
        
        SP.setValue(SP.getValue() + 2);
        
        return data;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Dump of the current cpu state">
    
    @Override
    public String toString() {
        
        String regs = String.format("AX:%04X, BX:%04X, CX:%04X, DX:%04X, SP:%04X, BP:%04X, SI:%04X, DI:%04X, IP:%04X",
                
            AX.getValue(),
            BX.getValue(),
            CX.getValue(),
            DX.getValue(),
            SP.getValue(),
            BP.getValue(),
            SI.getValue(),
            DI.getValue(),
            IP.getValue()
        );
        String segs = String.format("CS:%04X, DS:%04X, ES:%04X, SS:%04X",
                
            CS.getSelector(),
            DS.getSelector(),
            ES.getSelector(),
            SS.getSelector()
        );
        String flags = String.format("FLAGS: %04X (%s)",
                
            FLAGS.getValue(),
            FLAGS.toString()
        );
        String block = m_currentBlock != null ? m_currentBlock.toString() : "No information";
        
        return "CPU State:\n" + regs + "\n" + segs + "\n" + flags + "\n\n" + "Dump of the current code block:\n" + block;
    }
    
    // </editor-fold>
}
