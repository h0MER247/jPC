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
package Hardware.Video.VGA;

import Hardware.HardwareComponent;
import MemoryMap.MemoryReadable;
import MemoryMap.MemoryWritable;
import java.util.Arrays;



public final class VGARam implements HardwareComponent,
                                     MemoryReadable,
                                     MemoryWritable {
    
    /* ----------------------------------------------------- *
     * RAM data                                              *
     * ----------------------------------------------------- */
    private final int[] m_ram;
    private final int[] m_ramLatches;
    private final int m_ramSizeMask;
    private int m_ramBankMask;
    
    /* ----------------------------------------------------- *
     * RAM bank offset                                       *
     * ----------------------------------------------------- */
    private int m_ramBankOffsetRead;
    private int m_ramBankOffsetWrite;
    
    /* ----------------------------------------------------- *
     * Memory mapping                                        *
     * ----------------------------------------------------- */    
    private Runnable m_memoryMapperDelegate;
    private int[][] m_memoryMapping;
    
    /* ----------------------------------------------------- *
     * ALU operations                                        *
     * ----------------------------------------------------- */
    private interface ALUOperation {
        
        int run(int data, int mask, int latch);
    }
    private final int[][] m_aluRotationLUT;
    private int m_aluRotationCount;
    private int m_aluBitMask;
    private final ALUOperation[] m_aluOPs;
    private ALUOperation m_aluOP;
    
    /* ----------------------------------------------------- *
     * Current read/write mode                               *
     * ----------------------------------------------------- */
    private int m_readMode;
    private int m_readPlane;
    private int m_writeMode;
    private int m_writePlane;
    private boolean m_chain4;
    private boolean m_oddEvenRead;
    private boolean m_oddEvenWrite;
    
    /* ----------------------------------------------------- *
     * Set/Reset value and enable                            *
     * ----------------------------------------------------- */
    private final boolean[] m_setResetEnable;
    private final int[] m_setResetValue;
    
    /* ----------------------------------------------------- *
     * Color Compare and Color Care                          *
     * ----------------------------------------------------- */
    private final int[] m_colorCompare;
    private final int[] m_colorCare;
    
    
    
    public VGARam(int size) {
        
        // Initialize ram
        if((size & 0x3ffff) != 0)
            throw new IllegalArgumentException("The size has to be a multiple of 256 kb");
        
        m_ram = new int[size];
        m_ramSizeMask = size - 1;
        
        // Initialize latches
        m_ramLatches = new int[4];
        
        // Initialize register value caches
        m_setResetEnable = new boolean[4];
        m_setResetValue = new int[4];
        m_colorCompare = new int[4];
        m_colorCare = new int[4];
        
        // Initialize rotate look-up-table
        m_aluRotationLUT = new int[256][8];
        for(int i = 0; i < 256; i++)
            for(int j = 0; j < 8; j++)
                m_aluRotationLUT[i][j] = ((i >>> j) | (i << (8 - j))) & 0xff;
        
        // Initialize ALU operations
        m_aluOPs = new ALUOperation[] {
            
            /* Mov: */ (data, mask, latch) -> (data & mask) | (latch & ~mask),
            /* And: */ (data, mask, latch) -> (data | ~mask) & latch,
            /*  Or: */ (data, mask, latch) -> (data & mask) | latch,
            /* Xor: */ (data, mask, latch) -> (data & mask) ^ latch
        };
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        // Clear VRAM
        Arrays.fill(m_ram, 0x00);
        
        // Clear latches
        Arrays.fill(m_ramLatches, 0x00);
        
        // Clear cached register values
        Arrays.fill(m_setResetEnable, false);
        Arrays.fill(m_setResetValue, 0x00);
        Arrays.fill(m_colorCompare, 0x00);
        Arrays.fill(m_colorCare, 0x00);
        
        // Reset ALU
        m_aluRotationCount = 0;
        m_aluBitMask = 0x00;
        m_aluOP = m_aluOPs[0];
        
        // Reset read / write mode specific stuff
        m_readMode = m_readPlane = 0;
        m_writeMode = m_writePlane = 0;
        m_chain4 = false;
        m_oddEvenRead = m_oddEvenWrite = true;
        
        // Reset bank offsets
        m_ramBankOffsetRead = m_ramBankOffsetWrite = 0;
        
        // Initialize memory mapping
        setMemoryMappingMode(0);
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of MemoryReadable and MemoryWritable">
    
    @Override
    public void offerMemoryMapperDelegate(Runnable delegate) {
        
        m_memoryMapperDelegate = delegate;
    }
    
    @Override
    public int[][] getReadableMemoryAddresses() {
        
        return m_memoryMapping;
    }
    
    @Override
    public int readMEM8(int address) {
        
        int readPlane;
        
        // Determine address and plane
        address &= m_ramBankMask;
        address += m_ramBankOffsetRead;
        
        // Fill latches
        for(int i = 0, lAddr = address << 2; i < 4; i++, lAddr++)
            m_ramLatches[i] = getData(lAddr);
        
        // Read data
        switch(m_readMode) {
            
            case 0:
                if(m_chain4) {

                    readPlane = address & 0x03;

                    address &= ~0x03;
                }
                else if(m_oddEvenRead) {

                    readPlane = (m_readPlane & 0x02) | (address & 0x01);

                    address &= ~0x01;
                    address <<= 2;
                }
                else {

                    readPlane = m_readPlane;

                    address <<= 2;
                }
                return getData(address | readPlane);
                
            case 1:
                return 0xff ^ (((m_colorCare[0] & m_ramLatches[0]) ^ m_colorCompare[0]) |
                               ((m_colorCare[1] & m_ramLatches[1]) ^ m_colorCompare[1]) |
                               ((m_colorCare[2] & m_ramLatches[2]) ^ m_colorCompare[2]) |
                               ((m_colorCare[3] & m_ramLatches[3]) ^ m_colorCompare[3]));
                
            default:
                throw new IllegalArgumentException(String.format("Illegal read mode: %d", m_readMode));
        }
    }

    @Override
    public int readMEM16(int address) {
        
        return readMEM8(address) |
              (readMEM8(address + 1) << 8);
    }

    @Override
    public int readMEM32(int address) {
        
        return readMEM8(address) |
              (readMEM8(address + 1) << 8) |
              (readMEM8(address + 2) << 16) |
              (readMEM8(address + 3) << 24);
    }
    
    @Override
    public int[][] getWritableMemoryAddresses() {
        
        return m_memoryMapping;
    }
    
    @Override
    public void writeMEM8(int address, int data) {
        
        int writePlane;
        
        // Determine address and plane
        address &= m_ramBankMask;
        address += m_ramBankOffsetWrite;
        
        if(m_chain4) {
            
            writePlane = 1 << (address & 0x03);
            
            address &= ~0x03;
        }
        else if(m_oddEvenWrite) {
            
            writePlane = m_writePlane & 0x05;
            if((address & 0x01) != 0)
                writePlane <<= 1;
            
            address &= ~0x01;
            address <<= 2;
        }
        else {
            
            writePlane = m_writePlane;
            
            address <<= 2;
        }
        
        // Write data
        switch(m_writeMode) {
            
            case 0:
                data = m_aluRotationLUT[data][m_aluRotationCount];
                if((writePlane & 0x01) != 0)
                    setData(address, m_aluOP.run(m_setResetEnable[0] ? m_setResetValue[0] : data, m_aluBitMask, m_ramLatches[0]));
                if((writePlane & 0x02) != 0)
                    setData(address + 1, m_aluOP.run(m_setResetEnable[1] ? m_setResetValue[1] : data, m_aluBitMask, m_ramLatches[1]));
                if((writePlane & 0x04) != 0)
                    setData(address + 2, m_aluOP.run(m_setResetEnable[2] ? m_setResetValue[2] : data, m_aluBitMask, m_ramLatches[2]));
                if((writePlane & 0x08) != 0)
                    setData(address + 3, m_aluOP.run(m_setResetEnable[3] ? m_setResetValue[3] : data, m_aluBitMask, m_ramLatches[3]));
                break;
                
            case 1:
                if((writePlane & 0x01) != 0)
                    setData(address, m_ramLatches[0]);
                if((writePlane & 0x02) != 0)
                    setData(address + 1, m_ramLatches[1]);
                if((writePlane & 0x04) != 0)
                    setData(address + 2, m_ramLatches[2]);
                if((writePlane & 0x08) != 0)
                    setData(address + 3, m_ramLatches[3]);
                break;
                
            case 2:
                if((writePlane & 0x01) != 0)
                    setData(address, m_aluOP.run((data & 0x01) != 0 ? 0xff : 0x00, m_aluBitMask, m_ramLatches[0]));
                if((writePlane & 0x02) != 0)
                    setData(address + 1, m_aluOP.run((data & 0x02) != 0 ? 0xff : 0x00, m_aluBitMask, m_ramLatches[1]));
                if((writePlane & 0x04) != 0)
                    setData(address + 2, m_aluOP.run((data & 0x04) != 0 ? 0xff : 0x00, m_aluBitMask, m_ramLatches[2]));
                if((writePlane & 0x08) != 0)
                    setData(address + 3, m_aluOP.run((data & 0x08) != 0 ? 0xff : 0x00, m_aluBitMask, m_ramLatches[3]));
                break;
                
            case 3:
                data = m_aluRotationLUT[data][m_aluRotationCount];
                if((writePlane & 0x01) != 0)
                    setData(address + 0, m_aluOP.run(m_setResetValue[0], m_aluBitMask & data, m_ramLatches[0]));
                if((writePlane & 0x02) != 0)
                    setData(address + 1, m_aluOP.run(m_setResetValue[1], m_aluBitMask & data, m_ramLatches[1]));
                if((writePlane & 0x04) != 0)
                    setData(address + 2, m_aluOP.run(m_setResetValue[2], m_aluBitMask & data, m_ramLatches[2]));
                if((writePlane & 0x08) != 0)
                    setData(address + 3, m_aluOP.run(m_setResetValue[3], m_aluBitMask & data, m_ramLatches[3]));
                break;
                
            default:
                throw new IllegalArgumentException(String.format("Illegal write mode: %d", m_writeMode));
        }
    }

    @Override
    public void writeMEM16(int address, int data) {
        
        writeMEM8(address, data & 0xff);
        writeMEM8(address + 1, (data >>> 8) & 0xff);
    }

    @Override
    public void writeMEM32(int address, int data) {
        
        writeMEM8(address, data & 0xff);
        writeMEM8(address + 1, (data >>> 8) & 0xff);
        writeMEM8(address + 2, (data >>> 16) & 0xff);
        writeMEM8(address + 3, (data >>> 24) & 0xff);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Memory mapping mode selection">
    
    public void setMemoryMappingMode(int mode) {
        
        switch(mode) {
            
            case 0: m_memoryMapping = new int[][] { new int[] { 0xa0000, 0x20000, 0x00000 } }; m_ramBankMask = 0xffff; break;
            case 1: m_memoryMapping = new int[][] { new int[] { 0xa0000, 0x10000, 0x00000 } }; m_ramBankMask = 0xffff; break;
            case 2: m_memoryMapping = new int[][] { new int[] { 0xb0000, 0x08000, 0x10000 } }; m_ramBankMask = 0x7fff; break;
            case 3: m_memoryMapping = new int[][] { new int[] { 0xb8000, 0x08000, 0x18000 } }; m_ramBankMask = 0x7fff; break;
            
            default:
                throw new IllegalArgumentException(String.format("%s: Illegal memory mapping mode specified (%d)", getClass().getName(), mode));
        }
        
        if(m_memoryMapperDelegate != null)
            m_memoryMapperDelegate.run();
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="RAM bank offsets for reads and writes">
    
    public void setRamBankOffsetRead(int offset) {
        
        m_ramBankOffsetRead = offset;
    }
    
    public void setRamBankOffsetWrite(int offset) {
        
        m_ramBankOffsetWrite = offset;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Read / Write mode specific stuff">
    
    public void setOddEvenRead(boolean oddEvenRead) {
        
        m_oddEvenRead = oddEvenRead;
    }
    public void setOddEvenWrite(boolean oddEvenWrite) {
        
        m_oddEvenWrite = oddEvenWrite;
    }
    
    public void setChain4(boolean chain4) {
        
        m_chain4 = chain4;
    }
    
    public void setReadMode(int mode) {
        
        m_readMode = mode;
    }
    
    public void setWriteMode(int mode) {
        
        m_writeMode = mode;
    }
    
    public void setALURotateCount(int count) {
        
        m_aluRotationCount = count;
    }
    
    public void setALUOperation(int op) {
        
        m_aluOP = m_aluOPs[op];
    }
    
    public void setALUBitMask(int mask) {
        
        m_aluBitMask = mask;
    }
    
    public void setReadPlaneSelect(int plane) {
        
        m_readPlane = plane;
    }
    
    public void setWritePlaneMask(int mask) {
        
        m_writePlane = mask;
    }
    
    public void setSetResetEnableMask(int mask) {
        
        m_setResetEnable[0] = (mask & 0x01) != 0;
        m_setResetEnable[1] = (mask & 0x02) != 0;
        m_setResetEnable[2] = (mask & 0x04) != 0;
        m_setResetEnable[3] = (mask & 0x08) != 0;
    }
    
    public void setSetResetMask(int mask) {
        
        m_setResetValue[0] = (mask & 0x01) != 0 ? 0xff : 0x00;
        m_setResetValue[1] = (mask & 0x02) != 0 ? 0xff : 0x00;
        m_setResetValue[2] = (mask & 0x04) != 0 ? 0xff : 0x00;
        m_setResetValue[3] = (mask & 0x08) != 0 ? 0xff : 0x00;
    }
    
    public void setColorCompareMask(int mask) {
        
        m_colorCompare[0] = (mask & 0x01) != 0 ? 0xff : 0x00;
        m_colorCompare[1] = (mask & 0x02) != 0 ? 0xff : 0x00;
        m_colorCompare[2] = (mask & 0x04) != 0 ? 0xff : 0x00;
        m_colorCompare[3] = (mask & 0x08) != 0 ? 0xff : 0x00;
    }
    
    public void setColorCareMask(int mask) {
        
        m_colorCare[0] = (mask & 0x01) != 0 ? 0xff : 0x00;
        m_colorCare[1] = (mask & 0x02) != 0 ? 0xff : 0x00;
        m_colorCare[2] = (mask & 0x04) != 0 ? 0xff : 0x00;
        m_colorCare[3] = (mask & 0x08) != 0 ? 0xff : 0x00;
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Access to the VRAM data in a linear fashion">
    
    private void setData(int address, int data) {
        
        m_ram[address & m_ramSizeMask] = data;
    }
    
    public int getData(int address) {
        
        return m_ram[address & m_ramSizeMask];
    }
    
    // </editor-fold>
}
