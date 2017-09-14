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
import IOMap.IOReadable;
import IOMap.IOWritable;
import Scheduler.Scheduler;
import Hardware.Video.GraphicsCard;
import java.util.ArrayList;
import java.util.Arrays;
import Scheduler.Schedulable;
import Hardware.Video.GraphicsCardListener;
import java.util.LinkedList;



/**
 * http://www.osdever.net/FreeVGA/vga/portidx.htm
 * http://wiki.osdev.org/VGA_Hardware
 * https://www-user.tu-chemnitz.de/~kzs/tools/whatvga/vga.txt
 */
public abstract class VGAAdapter extends GraphicsCard
                                 implements Schedulable,
                                            IOReadable,
                                            IOWritable {
    
    /* ----------------------------------------------------- *
     * CRT Controller                                        *
     * ----------------------------------------------------- */
    protected final int[] m_crtc;
    protected int m_crtcIndex;
    
    /* ----------------------------------------------------- *
     * Attribute Controller Register                         *
     * ----------------------------------------------------- */
    protected final int[] m_atc;
    protected int m_atcIndex;
    protected boolean m_atcFlipFlop;
    protected int[] m_atcPaletteCache;
    
    /* ----------------------------------------------------- *
     * Graphic Data Controller                               *
     * ----------------------------------------------------- */
    protected final int[] m_gdc;
    protected int m_gdcIndex;
    
    /* ----------------------------------------------------- *
     * Sequencer                                             *
     * ----------------------------------------------------- */
    protected final int[] m_seq;
    protected int m_seqIndex;
    
    /* ----------------------------------------------------- *
     * Digital to Analog Converter                           *
     * ----------------------------------------------------- */
    protected final int DAC_STATE_WRITE = 0b00;
    protected final int DAC_STATE_READ = 0b11;
    protected final int DAC_ENTRY_RED = 0x00;
    protected final int DAC_ENTRY_GREEN = 0x01;
    protected final int DAC_ENTRY_BLUE = 0x02;
    protected final int[][] m_dac;
    protected int m_dacAddrRead;
    protected int m_dacAddrWrite;
    protected int m_dacEntry;
    protected int m_dacPixelMask;
    protected int m_dacState;
    protected int[] m_dacPaletteCache;
    
    /* ----------------------------------------------------- *
     * Miscellaneous Output                                  *
     * ----------------------------------------------------- */
    protected final int MISC_IO_SELECT = 0x01;
    protected int m_miscOut;
    
    /* ----------------------------------------------------- *
     * Input Status 0/1                                      *
     * ----------------------------------------------------- */
    protected final int STAT1_DISPLAY_DISABLE = 0x01;
    protected final int STAT1_VERTICAL_RETRACE = 0x08;
    protected int m_status0;
    protected int m_status1;
    
    /* ----------------------------------------------------- *
     * Current scanline                                      *
     * ----------------------------------------------------- */
    private boolean m_scanlineDoublingFlipFlop;
    private int m_screenScanline;
    private int m_charScanline;
    
    /* ----------------------------------------------------- *
     * Rendering                                             *
     * ----------------------------------------------------- */
    private final LinkedList<VGARenderer> m_renderer;
    private VGARenderer m_currentRenderer;
    private final VGARenderer m_blankRenderer;
    protected int m_vramAddr;
    protected int m_pixelShift;
    
    /* ----------------------------------------------------- *
     * Scheduling                                            *
     * ----------------------------------------------------- */
    private float m_cpuFrequency;
    private boolean m_isLineOnScreen;
    private int m_cyclesRemaining;
    private int m_cyclesLineOnScreen;
    private int m_cyclesLineOffScreen;
    
    /* ----------------------------------------------------- *
     * Video RAM                                             *
     * ----------------------------------------------------- */
    protected final VGARam m_vram;
    
    
    
    public VGAAdapter(GraphicsCardListener listener, int ramSize) {
        
        super(listener);
        
        // Initialize video ram
        m_vram = new VGARam(ramSize);
        
        // Initialize registers
        m_crtc = new int[256];
        m_atc = new int[256];
        m_gdc = new int[256];
        m_seq = new int[256];
        m_dac = new int[256][3];
        
        // Initialize palette cache
        m_atcPaletteCache = new int[16];
        m_dacPaletteCache = new int[256];
        
        // Initialize renderer
        m_renderer = new LinkedList<>();
        m_renderer.add(m_blankRenderer = new VGABlankRenderer());
        m_renderer.add(new VGATextRendererLo());
        m_renderer.add(new VGATextRendererHi());
        m_renderer.add(new VGAGraphicRenderer2bppLo());
        m_renderer.add(new VGAGraphicRenderer2bppHi());
        m_renderer.add(new VGAGraphicRenderer4bppLo());
        m_renderer.add(new VGAGraphicRenderer4bppHi());
        m_renderer.add(new VGAGraphicRenderer8bppLo());
        m_renderer.add(new VGAGraphicRenderer8bppHi());
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        // Reset ATC
        Arrays.fill(m_atc, 0x00);
        Arrays.fill(m_atcPaletteCache, 0x00);
        m_atcIndex = 0x00;
        m_atcFlipFlop = false;
        
        // Reset CRTC
        Arrays.fill(m_crtc, 0x00);
        m_crtc[0x06] = 0xff;
        m_crtcIndex = 0x00;
        
        // Reset GDC
        Arrays.fill(m_gdc, 0x00);
        m_gdcIndex = 0x00;
        
        // Reset SEQ
        Arrays.fill(m_seq, 0x00);
        m_seqIndex = 0x00;
        
        // Reset DAC
        Arrays.fill(m_dacPaletteCache, 0x00);
        for(int i = 0; i < 256; i++) {
            
            m_dac[i][DAC_ENTRY_RED] = 0x00;
            m_dac[i][DAC_ENTRY_GREEN] = 0x00;
            m_dac[i][DAC_ENTRY_BLUE] = 0x00;
        }
        m_dacAddrRead = 0x00;
        m_dacAddrWrite = 0x00;
        m_dacEntry = 0x00;
        m_dacPixelMask = 0x00;
        m_dacState = DAC_STATE_WRITE;
        
        // Reset MISC
        m_miscOut = 0x01;
        
        // Reset STATUS
        m_status0 = 0x10;
        m_status1 = 0x00;
        
        // Reset some cached values
        m_cyclesLineOnScreen = m_cyclesLineOffScreen = 32768;
        m_cyclesRemaining = m_cyclesLineOnScreen;
        m_screenScanline = m_charScanline = 0;
        m_vramAddr = 0;
        m_isLineOnScreen = true;
        m_scanlineDoublingFlipFlop = true;
    }

    @Override
    public ArrayList<HardwareComponent> getSubComponents() {
        
        ArrayList<HardwareComponent> subComponents = new ArrayList<>();
        subComponents.add(m_vram);
        
        return subComponents;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return new int[] {
            
            0x3b4, 0x3b5, 0x3ba, 0x3c0,
            0x3c1, 0x3c2, 0x3c4, 0x3c5,
            0x3c6, 0x3c7, 0x3c8, 0x3c9,
            0x3cc, 0x3ce, 0x3cf, 0x3d4,
            0x3d5, 0x3da
        };
    }

    @Override
    public int readIO8(int port) {
        
        int data;
        
        switch(port) {
                
            // CRT Controller Index Register
            case 0x3b4:
            case 0x3d4:
                return m_crtcIndex;
                
            // CRT Controller Data Register
            case 0x3b5:
            case 0x3d5:
                return m_crtc[m_crtcIndex];
                
            // VGA Input Status Register 1
            case 0x3ba:
            case 0x3da:
                m_atcFlipFlop = false;
                if((m_status1 & 0x01) != 0)
                    m_status1 &= ~0x30;
                else
                    m_status1 ^= 0x30;
                return m_status1;
                
            // Attribute Controller Index Register 
            case 0x3c0:
                return m_atcIndex;

            // Attribute Controller Data Register 
            case 0x3c1:
                return m_atc[m_atcIndex & 0x1f];

            // VGA Input Status Register 0 
            case 0x3c2:
                return m_status0;
                
            // VGA Sequencer Index register
            case 0x3c4:
                return m_seqIndex;

            // VGA Sequencer Data register
            case 0x3c5:
                return m_seq[m_seqIndex];
                
            // DAC Pixel Data Mask Register
            case 0x3c6:
                return m_dacPixelMask;
            
            // DAC State Register
            case 0x3c7:
                return m_dacState;
            
            // DAC Palette Write Index Register 
            case 0x3c8:
                return m_dacAddrWrite;
            
            // DAC Palette Data Register
            case 0x3c9:
                m_dacState = DAC_STATE_READ;
                data = m_dac[m_dacAddrRead][m_dacEntry++];
                if(m_dacEntry > DAC_ENTRY_BLUE) {
                    
                    m_dacEntry = DAC_ENTRY_RED;
                    m_dacAddrRead = (m_dacAddrRead + 1) & 0xff;
                }
                return data;
                
            // VGA Miscellaneous Output Register
            case 0x3cc:
                return m_miscOut;
                
            // Graphics Controller Index Register
            case 0x3ce:
                return m_gdcIndex;
                
            // Graphics Controller Data Register
            case 0x3cf:
                return m_gdc[m_gdcIndex];
                
                
            default:
                return 0xff;
                //throw new IllegalArgumentException(String.format("Illegal access while reading port %04xh", port));
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return new int[] {
            
            0x3b4, 0x3d4, 0x3b5, 0x3d5,
            0x3c0, 0x3c2, 0x3c4, 0x3c5,
            0x3c6, 0x3c7, 0x3c8, 0x3c9,
            0x3ce, 0x3cf
        };
    }
    
    @Override
    public void writeIO8(int port, int data) {
        
        int oldData;
        
        switch(port) {
            
            // CRT Controller Index Register
            case 0x3b4:
            case 0x3d4:
                m_crtcIndex = data;
                break;
                
            // CRT Controller Data Register
            case 0x3b5:
            case 0x3d5:
                oldData = m_crtc[m_crtcIndex];
                if(oldData != data) {
                    
                    if(((m_crtc[0x11] & 0x80) != 0) && m_crtcIndex <= 7) {
                        
                        if(m_crtcIndex == 0x07)
                            m_crtc[0x07] = (m_crtc[0x07] & ~0x10) | (data & 0x10);
                    }
                    else {

                        m_crtc[m_crtcIndex] = data;
                    }
                    
                    updateCRTC(m_crtcIndex, oldData ^ m_crtc[m_crtcIndex]);
                }
                break;
                
            // Attribute Controller Index Register 
            case 0x3c0:
                if(m_atcFlipFlop ^= true) {
                    
                    oldData = m_atcIndex;
                    m_atcIndex = data;
                    
                    if(((m_atcIndex ^ oldData) & 0x20) != 0)
                        updateRenderer();
                }
                else {
                    
                    int index = m_atcIndex & 0x1f;
                    
                    oldData = m_atc[index];
                    if(oldData != data) {
                    
                        m_atc[index] = data;
                        updateATC(index, oldData ^ data);
                    }
                }
                break;
            
            // VGA Miscellaneous Output Register    
            case 0x3c2:
                oldData = m_miscOut;
                if(oldData != data) {
                    
                    m_miscOut = data;
                    updateMISC(oldData ^ data);
                }
                break;
                
            // VGA Sequencer Index register    
            case 0x3c4:
                m_seqIndex = data;
                break;
                
            // VGA Sequencer Data register    
            case 0x3c5:
                oldData = m_seq[m_seqIndex];
                if(oldData != data) {
                    
                    m_seq[m_seqIndex] = data;
                    updateSEQ(m_seqIndex, oldData ^ data);
                }
                break;
                
            // DAC Pixel Data Mask Register 
            case 0x3c6:
                m_dacPixelMask = data;
                break;

            // DAC Palette Read Index Register 
            case 0x3c7:
                m_dacAddrRead = data;
                m_dacEntry = DAC_ENTRY_RED;
                break;

            // DAC Palette Write Index Register 
            case 0x3c8:
                m_dacAddrWrite = data;
                m_dacEntry = DAC_ENTRY_RED;
                break;

            case 0x3c9:
                m_dacState = DAC_STATE_WRITE;
                m_dac[m_dacAddrWrite][m_dacEntry++] = data;
                
                if(m_dacEntry > DAC_ENTRY_BLUE) {
                    
                    updateDACPaletteCacheEntry(m_dacAddrWrite);
                    
                    m_dacEntry = DAC_ENTRY_RED;
                    m_dacAddrWrite = (m_dacAddrWrite + 1) & 0xff;
                }
                break;
                
            // Graphics Controller Index Register
            case 0x3ce:
                m_gdcIndex = data;
                break;
                
            // Graphics Controller Data Register
            case 0x3cf:
                oldData = m_gdc[m_gdcIndex];
                if(oldData != data) {
                    
                    m_gdc[m_gdcIndex] = data;
                    updateGDC(m_gdcIndex, oldData ^ data);
                }
                break;
                
            
            default:
                throw new IllegalArgumentException(String.format("Illegal access while writing %02xh to port %04xh", data, port));
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of Schedulable">
    
    @Override
    public void setBaseFrequency(float frequency) {
        
        m_cpuFrequency = frequency;
        updateTimings();
    }
    
    @Override
    public void updateClock(int cycles) {
        
        m_cyclesRemaining -= cycles;
        while(m_cyclesRemaining <= 0) {
            
            if(m_isLineOnScreen) {

                m_isLineOnScreen = false;
                m_cyclesRemaining += m_cyclesLineOffScreen;
                

                // Draw current scanline
                if(m_screenScanline < m_frameHeight)
                    m_currentRenderer.drawLine(m_screenScanline * m_frameWidth);

                // Update status register            
                m_status1 |= STAT1_DISPLAY_DISABLE;
                if((m_status1 & STAT1_VERTICAL_RETRACE) != 0) {
                    
                    if((m_screenScanline & 0x0f) == getCRTCVerticalRetraceEnd())
                        m_status1 &= ~STAT1_VERTICAL_RETRACE;
                }
            }
            else {

                m_isLineOnScreen = true;
                m_cyclesRemaining += m_cyclesLineOnScreen;
                
                
                // Update status register and vram address of the next scanline
                if(m_screenScanline < m_frameHeight) {

                    m_status1 &= ~STAT1_DISPLAY_DISABLE;
                    
                    m_scanlineDoublingFlipFlop ^= true;
                    if(!getCRTCScanDoublingEnable() || m_scanlineDoublingFlipFlop) {
                    
                        if(m_charScanline == getCRTCMaximumScanline()) {

                            m_charScanline = 0;
                            m_vramAddr += getCRTCRowOffset() << 3;
                        }
                        else {

                            m_charScanline = (m_charScanline + 1) & 0x1f;
                        }
                    }
                }
                else {

                    m_status1 |= STAT1_DISPLAY_DISABLE;
                }
                
                // Advance to the next scanline on the screen
                m_screenScanline = (m_screenScanline + 1) & 0xfff;
                
                if(m_screenScanline == getCRTCLineCompare()) {
                    
                    m_vramAddr = 0;
                    if(getATCPixelPanningModeEnable())
                        m_pixelShift = 0;
                }
                if(m_screenScanline == getCRTCVerticalRetraceStart()) {

                    m_status1 |= STAT1_VERTICAL_RETRACE;
                    
                    drawOutput();
                }
                if(m_screenScanline >= getCRTCVerticalTotal()) {

                    m_screenScanline = 0;
                    m_scanlineDoublingFlipFlop = true;
                    
                    m_charScanline = getCRTCPresetRowScan();
                    m_pixelShift = getATCPixelShiftCount();
                    m_vramAddr = getCRTCVideoAddress() << 2;
                }
            }
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Handling of changes in certain register values">
    
    // <editor-fold defaultstate="collapsed" desc="CRT Controller">
    
    protected void updateCRTC(int regIndex, int changedData) {
        
        switch(regIndex) {
            
            case 0x00: case 0x01: case 0x06:
            case 0x10: case 0x12: case 0x15:
                updateTimings();
                break;
                
            case 0x07:
                if((changedData & 0xef) != 0)
                    updateTimings();
                break;
                
            case 0x09:
                if((changedData & 0x20) != 0)
                    updateTimings();
                break;
        }
    }
    
    protected int getCRTCHorizontalTotal() {
        
        return m_crtc[0x00] + 5;
    }
    
    protected int getCRTCHorizontalDisplayEnd() {
        
        return m_crtc[0x01] + 1;
    }
    
    protected int getCRTCVerticalTotal() {
        
        return (m_crtc[0x06] |
              ((m_crtc[0x07] << 8) & 0x100) |
              ((m_crtc[0x07] << 4) & 0x200)) + 2;
    }
    
    protected int getCRTCVerticalRetraceStart() {
        
        return (m_crtc[0x10] |
              ((m_crtc[0x07] << 6) & 0x100) |
              ((m_crtc[0x07] << 2) & 0x200)) + 1;
    }
    
    protected int getCRTCVerticalRetraceEnd() {
        
        return m_crtc[0x11] & 0x0f;
    }
    
    protected int getCRTCVerticalDisplayEnd() {
        
        return (m_crtc[0x12] |
              ((m_crtc[0x07] << 7) & 0x100) |
              ((m_crtc[0x07] << 3) & 0x200)) + 1;
    }
    
    protected int getCRTCVerticalBlankStart() {
        
        return (m_crtc[0x15] |
              ((m_crtc[0x07] << 5) & 0x100) |
              ((m_crtc[0x09] << 4) & 0x200)) + 1;
    }
    
    protected int getCRTCLineCompare() {
        
        return (m_crtc[0x18] |
              ((m_crtc[0x07] & 0x10) << 4) |
              ((m_crtc[0x09] & 0x40) << 3)) + 1;
    }
    
    protected int getCRTCPresetRowScan() {
        
        return m_crtc[0x08] & 0x1f;
    }
    
    protected int getCRTCBytePanning() {
        
        return (m_crtc[0x08] >> 5) & 0x03;
    }
    
    protected int getCRTCMaximumScanline() {
            
        return m_crtc[0x09] & 0x1f;
    }
    
    protected boolean getCRTCScanDoublingEnable() {
        
        return (m_crtc[0x09] & 0x80) != 0;
    }
    
    protected int getCRTCCursorScanlineStart() {
        
        return m_crtc[0x0a] & 0x1f;
    }
    
    protected boolean getCRTCCursorEnable() {
        
        return (m_crtc[0x0a] & 0x20) == 0;
    }
    
    protected int getCRTCCursorScanlineEnd() {
        
        return m_crtc[0x0b] & 0x1f;
    }
    
    protected int getCRTCVideoAddress() {
        
        return (m_crtc[0x0c] << 8) | m_crtc[0x0d];
    }
    
    protected int getCRTCCursorAddress() {
        
        return (m_crtc[0x0e] << 8) | m_crtc[0x0f];
    }
    
    protected boolean getCRTCAddressBit13Mapping() {
        
        return (m_crtc[0x17] & 0x01) != 0;
    }
    
    protected int getCRTCRowOffset() {
        
        return (m_crtc[0x13] == 0) ? 0x100 : m_crtc[0x13];
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Attribute Controller">
    
    protected void updateATC(int regIndex, int changedData) {
        
        if(regIndex <= 0x0f) {
            
            if((changedData & 0x3f) != 0)
                updateATCPaletteCacheEntry(regIndex);
        }
        else {
            
            switch(regIndex) {
                
                case 0x10:
                    if((changedData & 0x40) != 0)
                        updateRenderer();
                    if((changedData & 0x80) != 0)
                        for(int i = 0; i < 0x10; i++)
                            updateATCPaletteCacheEntry(i);
                    break;
                    
                case 0x14:
                    if((changedData & 0x0f) != 0)
                        for(int i = 0; i < 0x10; i++)
                            updateATCPaletteCacheEntry(i);
                    break;
            }
        }
    }
    
    protected void updateATCPaletteCacheEntry(int index) {
        
        if((m_atc[0x10] & 0x80) != 0)
            m_atcPaletteCache[index] = (m_atc[index] & 0x0f) | ((m_atc[0x14] << 4) & 0xf0);
        else
            m_atcPaletteCache[index] = (m_atc[index] & 0x3f) | ((m_atc[0x14] << 4) & 0xc0);
    }
    
    protected boolean getATCPaletteEnable() {
        
        return (m_atcIndex & 0x20) != 0;
    }
    
    protected boolean getATCLineGraphicsEnable() {
        
        return (m_atc[0x10] & 0x04) != 0;
    }
    
    protected boolean getATCBlinkingEnable() {
        
        return (m_atc[0x10] & 0x08) != 0;
    }
    
    protected boolean getATCPixelPanningModeEnable() {
        
        return (m_atc[0x10] & 0x20) != 0;
    }
    
    protected boolean getATCPELClockDividedByTwo() {
        
        return (m_atc[0x10] & 0x40) != 0;
    }
    
    protected int getATCPlaneEnableMask() {
        
        return m_atc[0x12] & 0x0f;
    }
    
    protected int getATCPixelShiftCount() {
        
        return m_atc[0x13] & 0x07;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Sequencer">
    
    protected void updateSEQ(int regIndex, int changedData) {
        
        switch(regIndex) {
            
            case 0x01:
                if((changedData & 0x09) != 0)
                    updateTimings();
                if((changedData & 0x20) != 0)
                    updateRenderer();
                break;
                
            case 0x02:
                if((changedData & 0x0f) != 0)
                    m_vram.setWritePlaneMask(getSEQWritePlaneMask());
                break;
                
            case 0x04:
                if((changedData & 0x04) != 0)
                    m_vram.setOddEvenWrite(getSEQOddEvenWriteEnable());
                if((changedData & 0x08) != 0)
                    m_vram.setChain4(getSEQChain4Enable());
                break;
        }
    }
    
    protected int getSEQCharacterWidth() {
        
        return (m_seq[0x01] & 0x01) != 0 ? 8 : 9;
    }
    
    protected boolean getSEQDOTClockDividedByTwo() {
        
        return (m_seq[0x01] & 0x08) != 0;
    }
    
    protected boolean getSEQScreenEnable() {
        
        return (m_seq[0x01] & 0x20) == 0;
    }
    
    protected int getSEQWritePlaneMask() {
        
        return m_seq[0x02] & 0x0f;
    }
    
    protected int getSEQFontAddressA() {
        
        int address = (m_seq[0x03] << 12) & 0xc000;
        if((m_seq[0x03] & 0x20) != 0)
            address += 0x2000;
        
        return address << 2;
    }
    
    protected int getSEQFontAddressB() {
        
        int address = (m_seq[0x03] << 14) & 0xc000;
        if((m_seq[0x03] & 0x10) != 0)
            address += 0x2000;
        
        return address << 2;
    }
    
    protected boolean getSEQOddEvenWriteEnable() {
        
        return (m_seq[0x04] & 0x04) == 0;
    }
    
    protected boolean getSEQChain4Enable() {
        
        return (m_seq[0x04] & 0x08) != 0;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Digital Analog Converter (6 Bit)">
    
    protected void updateDACPaletteCacheEntry(int regIndex) {
        
        int r = (m_dac[regIndex][DAC_ENTRY_RED] & 0x3f) << 2;
        int g = (m_dac[regIndex][DAC_ENTRY_GREEN] & 0x3f) << 2;
        int b = (m_dac[regIndex][DAC_ENTRY_BLUE] & 0x3f) << 2;
        
        m_dacPaletteCache[regIndex] = 0xff000000 | (r << 16) | (g << 8) | b;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Graphics Data Controller">
    
    protected void updateGDC(int regIndex, int changedData) {
        
        switch(regIndex) {
                
            case 0x00:
                if((changedData & 0x0f) != 0)
                    m_vram.setSetResetMask(getGDCSetResetMask());
                break;
                
            case 0x01:
                if((changedData & 0x0f) != 0)
                    m_vram.setSetResetEnableMask(getGDCSetResetEnableMask());
                break;
             
            case 0x02:
                if((changedData & 0x0f) != 0)
                    m_vram.setColorCompareMask(getGDCColorCompareMask());
                break;
                
            case 0x03:
                if((changedData & 0x07) != 0)
                    m_vram.setALURotateCount(getGDCALURotateCount());
                if((changedData & 0x18) != 0)
                    m_vram.setALUOperation(getGDCALUOperation());
                break;
                
            case 0x04:
                if((changedData & 0x03) != 0)
                    m_vram.setReadPlaneSelect(getGDCReadPlaneSelect());
                break;
                
            case 0x05:
                if((changedData & 0x03) != 0)
                    m_vram.setWriteMode(getGDCWriteMode());
                if((changedData & 0x08) != 0)
                    m_vram.setReadMode(getGDCReadMode());
                if((changedData & 0x10) != 0)
                    m_vram.setOddEvenRead(getGDCOddEvenReadEnable());
                if((changedData & 0x60) != 0)
                    updateRenderer();
                break;
                
            case 0x06:
                if((changedData & 0x01) != 0)
                    updateRenderer();
                if((changedData & 0x0c) != 0)
                    m_vram.setMemoryMappingMode(getGDCMemoryMappingMode());
                break;
                
            case 0x07:
                if((changedData & 0x0f) != 0)
                    m_vram.setColorCareMask(getGDCColorCareMask());
                break;
                
            case 0x08:
                m_vram.setALUBitMask(getGDCALUBitMask());
                break;
        }
    }
    
    protected int getGDCSetResetMask() {
        
        return m_gdc[0x00] & 0x0f;
    }
    
    protected int getGDCSetResetEnableMask() {
        
        return m_gdc[0x01] & 0x0f;
    }
            
    protected int getGDCColorCompareMask() { 
        
        return m_gdc[0x02] & 0x0f;
    }
                    
    protected int getGDCALURotateCount() {
        
        return m_gdc[0x03] & 0x07;
    }
    
    protected int getGDCALUOperation() {
        
        return (m_gdc[0x03] >> 3) & 0x03;
    }
    
    protected int getGDCReadPlaneSelect() {

        return m_gdc[0x04] & 0x03;
    }
    
    protected int getGDCWriteMode() {
        
        return m_gdc[0x05] & 0x03;
    }
    
    protected int getGDCReadMode() {
        
        return (m_gdc[0x05] >> 3) & 0x01;
    }
    
    protected boolean getGDCOddEvenReadEnable() {
        
        return (m_gdc[0x05] & 0x10) != 0;
    }
    
    protected boolean getGDCShiftInterleaveModeEnable() {

        return (m_gdc[0x05] & 0x20) != 0;
    }
    
    protected boolean getGDC256ColorModeEnable() {
        
        return (m_gdc[0x05] & 0x40) != 0;
    }
    
    protected boolean getGDCGraphicModeEnable() {
           
        return (m_gdc[0x06] & 0x01) != 0;
    }
    
    protected int getGDCMemoryMappingMode() {
        
        return (m_gdc[0x06] >> 2) & 0x03;
    }
    
    protected int getGDCColorCareMask() {
        
        return m_gdc[0x07] & 0x0f;
    }
    
    protected int getGDCALUBitMask() {
        
        return m_gdc[0x08];
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Miscellaneous">
    
    protected void updateMISC(int changedData) {
        
        if((changedData & 0x0c) != 0)
            updateTimings();
    }
    
    protected boolean getMISCIOAddressSelect() {
        
        return (m_miscOut & MISC_IO_SELECT) != 0;
    }
    
    protected int getMISCClockSelect() {
        
        return (m_miscOut >> 2) & 0x03;
    }
    
    protected boolean getMISCEvenPageHiSelect() {
        
        return (m_miscOut & 0x20) != 0;
    }
    
    // </editor-fold>
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Recalculation of VGA timing parameters">
    
    protected void updateTimings() {
        
        int horizontalDisplayEnd = getCRTCHorizontalDisplayEnd();
        int verticalDisplayEnd = getCRTCVerticalDisplayEnd();
        int horizontalTotal = getCRTCHorizontalTotal();
        int verticalBlankStart = getCRTCVerticalBlankStart();
        int charWidth = getSEQCharacterWidth() * (getSEQDOTClockDividedByTwo() ? 2 : 1);
        
        // Calculate number of cycles for a whole line (on- and offscreen)
        float charClock = (m_cpuFrequency / getVGAClockFrequency()) * charWidth;
        float cyclesLineOnScreen = charClock * horizontalDisplayEnd;
        float cyclesLineOffScreen = charClock * (horizontalTotal - horizontalDisplayEnd);
        
        // Set new resolution
        int width = horizontalDisplayEnd * charWidth;
        int height = (verticalBlankStart < verticalDisplayEnd) ? verticalBlankStart : verticalDisplayEnd;
        setResolution(width, height);
        
        // Find a renderer that can render line in the current operating mode
        updateRenderer();
        
        // Update timings
        m_cyclesLineOnScreen = Scheduler.toFixedPoint(cyclesLineOnScreen);
        m_cyclesLineOffScreen = Scheduler.toFixedPoint(cyclesLineOffScreen);
        
        /*
        int verticalTotal = getCRTCVerticalTotal();
        float horizontalRefreshRate = getVGAClockFrequency() / (horizontalTotal * charWidth);
        float verticalRefreshRate = horizontalRefreshRate / verticalTotal;

        System.out.println("VGAAdapter::updateTimings():");
        System.out.printf("  Resolution ...........: %dx%d [h:%f Hz, v:%f Hz]\n", m_frameWidth, m_frameHeight, horizontalRefreshRate, verticalRefreshRate);
        System.out.printf("  Renderer .............: %s\n", m_currentRenderer.getClass().getName());
        System.out.printf("  CPU Clock ............: %.2f MHz\n", m_cpuFrequency / 1000000.0f);
        System.out.printf("  VGA Clock ............: %.2f MHz\n", getVGAClockFrequency() / 1000000.0f);
        System.out.printf("  Char Clock ...........: %f Hz\n", charClock);
        System.out.printf("  Timing \"On Screen\" ...: %f cycles\n", cyclesLineOnScreen);
        System.out.printf("  Timing \"Off Screen\" ..: %f cycles\n", cyclesLineOffScreen);
        System.out.printf("  HDispEnd .............: %d characters\n", horizontalDisplayEnd);
        System.out.printf("  HTotal ...............: %d characters\n", horizontalTotal);
        System.out.printf("  VDispEnd .............: %d scanlines\n", verticalDisplayEnd);
        System.out.printf("  VTotal ...............: %d scanlines\n", verticalTotal);
        System.out.printf("  VRetrace .............: %d scanline (ends on %d)\n", getCRTCVerticalRetraceStart(), getCRTCVerticalRetraceEnd());
        System.out.printf("  Max Char Scanline ....: %d scanlines\n", getCRTCMaximumScanline());
        System.out.println();
        */
    }
    
    protected float getVGAClockFrequency() {
        
        switch(getMISCClockSelect()) {
            
            case 0:
                return 25175000.0f;
                
            default:
                return 28322000.0f;
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Rendering">
    
    protected boolean isScreenVisible() {
        
        return getSEQScreenEnable() && getATCPaletteEnable();
    }
    
    protected final void updateRenderer() {

        // Set the current renderer to the blank renderer
        m_currentRenderer = m_blankRenderer;
        
        // Try to find a renderer that can draw a line in the current operating mode
        for(VGARenderer renderer : m_renderer) {
            
            if(renderer.isSuitableRenderer()) {
                
                m_currentRenderer = renderer;
                break;
            }
        }
    }
    
    protected void addRenderer(VGARenderer renderer) {
        
        m_renderer.push(renderer);
    }
    
    // <editor-fold defaultstate="collapsed" desc="Blank renderer">
    
    private final class VGABlankRenderer implements VGARenderer {
        
        @Override
        public boolean isSuitableRenderer() {
            
            return !isScreenVisible();
        }
        
        @Override
        public void drawLine(int offset) {
            
            for(int x = 0; x < m_frameWidth; x++, offset++)
                m_frameData[offset] = 0xff000000;
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Text Mode">
    
    // <editor-fold defaultstate="collapsed" desc="Text renderer (low resolution)">
    
    private final class VGATextRendererLo implements VGARenderer {

        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() &&
                   !getGDCGraphicModeEnable() &&
                   getSEQDOTClockDividedByTwo();
        }

        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr;
            int charWidth = getSEQCharacterWidth() << 1;
            boolean isLineGraphicsEnabled = getATCLineGraphicsEnable() && charWidth == 18;
            boolean isCursorVisible = getCRTCCursorEnable() && m_charScanline >= getCRTCCursorScanlineStart() && m_charScanline <= getCRTCCursorScanlineEnd() && (m_frameNumber & 0x3f) >= 0x20;
            int cursorAddr = getCRTCCursorAddress() << 2;
            boolean isBlinking = getATCBlinkingEnable();
            int fontAddressA = getSEQFontAddressA();
            int fontAddressB = getSEQFontAddressB();
            int fontRowOffset = m_charScanline << 2;
            
            for(int x = 0; x < m_frameWidth; x += charWidth, addr += 4, offset += charWidth) {
                
                int chr = m_vram.getData(addr << 1);
                int att = m_vram.getData((addr << 1) + 1);
                int fntAddr = (((att & 0x08) != 0) ? fontAddressA : fontAddressB) + (chr << 7) + fontRowOffset;
                int fnt = m_vram.getData(fntAddr + 2);
                
                
                int fgColor, bgColor;
                
                if(isCursorVisible && addr == cursorAddr) {
                    
                    bgColor = att & 0x0f; fgColor = att >> 4;
                }
                else {
                    
                    fgColor = att & 0x0f; bgColor = att >> 4;
                }
                if(isBlinking) {
                    
                    bgColor &= 0x07;
                    
                    if(((att & 0x80) != 0) && ((m_frameNumber & 0x3f) >= 0x20))
                        fgColor = bgColor;
                }
                fgColor = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[fgColor]];
                bgColor = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[bgColor]];
                
                
                for(int cx = 0; cx < charWidth; cx += 2)
                    m_frameData[offset + cx] = m_frameData[offset + cx + 1] = ((fnt & (0x80 >> (cx >> 1))) != 0) ? fgColor : bgColor;
                
                if(isLineGraphicsEnabled && ((chr & 0xe0) == 0xc0))
                    m_frameData[offset + 16] = m_frameData[offset + 17] = ((fnt & 0x01) != 0) ? fgColor : bgColor;
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Text renderer (high resolution)">
    
    private final class VGATextRendererHi implements VGARenderer {

        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() &&
                   !getGDCGraphicModeEnable() &&
                   !getSEQDOTClockDividedByTwo();
        }

        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr;
            int charWidth = getSEQCharacterWidth();
            boolean isLineGraphicsEnabled = getATCLineGraphicsEnable() && charWidth == 9;
            boolean isCursorVisible = getCRTCCursorEnable() && m_charScanline >= getCRTCCursorScanlineStart() && m_charScanline <= getCRTCCursorScanlineEnd() && (m_frameNumber & 0x3f) >= 0x20;
            int cursorAddr = getCRTCCursorAddress() << 2;
            boolean isBlinking = getATCBlinkingEnable();
            int fontAddressA = getSEQFontAddressA();
            int fontAddressB = getSEQFontAddressB();
            
            int fontRowOffset = m_charScanline << 2;
            
            for(int x = 0; x < m_frameWidth; x += charWidth, addr += 4, offset += charWidth) {
                
                int chr = m_vram.getData(addr << 1);
                int att = m_vram.getData((addr << 1) + 1);
                int fntAddr = (((att & 0x08) != 0) ? fontAddressA : fontAddressB) + (chr << 7) + fontRowOffset;
                int fnt = m_vram.getData(fntAddr + 2);
                
                
                int fgColor, bgColor;
                
                if(isCursorVisible && addr == cursorAddr) {
                    
                    bgColor = att & 0x0f;
                    fgColor = att >> 4;
                }
                else {
                    
                    fgColor = att & 0x0f;
                    bgColor = att >> 4;
                }
                if(isBlinking) {
                    
                    bgColor &= 0x07;
                    
                    if(((att & 0x80) != 0) && ((m_frameNumber & 0x3f) >= 0x20))
                        fgColor = bgColor;
                }
                fgColor = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[fgColor]];
                bgColor = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[bgColor]];
                
                
                for(int cx = 0; cx < charWidth; cx++)
                    m_frameData[offset + cx] = ((fnt & (0x80 >> cx)) != 0) ? fgColor : bgColor;
                
                if(isLineGraphicsEnabled && ((chr & 0xe0) == 0xc0))
                    m_frameData[offset + 8] = ((fnt & 0x01) != 0) ? fgColor : bgColor;
            }
        }
    }
    
    // </editor-fold>
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Graphic Mode">
    
    // <editor-fold defaultstate="collapsed" desc="Graphic 2bpp renderer (low resolution)">
    
    private final class VGAGraphicRenderer2bppLo implements VGARenderer {

        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() &&
                   getGDCGraphicModeEnable() &&
                   getGDCShiftInterleaveModeEnable() &&
                   getSEQDOTClockDividedByTwo();
        }

        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr;
            if(!getCRTCAddressBit13Mapping() && ((m_charScanline & 0x01) != 0))
                addr |= 0x4000;
            
            // TODO: Does pel scrolling work with CGA display modes?
            
            for(int x = 0; x < m_frameWidth; x += 16, addr += 4, offset += 16) {
                
                int p1 = m_vram.getData(addr << 1);
                int p2 = m_vram.getData((addr << 1) + 1);
                
                m_frameData[offset] = m_frameData[offset + 1] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p1 >> 6) & 0x03]];
                m_frameData[offset + 2] = m_frameData[offset + 3] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p1 >> 4) & 0x03]];
                m_frameData[offset + 4] = m_frameData[offset + 5] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p1 >> 2) & 0x03]];
                m_frameData[offset + 6] = m_frameData[offset + 7] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[p1 & 0x03]];
                
                m_frameData[offset + 8] = m_frameData[offset + 9] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p2 >> 6) & 0x03]];
                m_frameData[offset + 10] = m_frameData[offset + 11] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p2 >> 4) & 0x03]];
                m_frameData[offset + 12] = m_frameData[offset + 13] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p2 >> 2) & 0x03]];
                m_frameData[offset + 14] = m_frameData[offset + 15] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[p2 & 0x03]];
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Graphic 2bpp renderer (high resolution)">
    
    private final class VGAGraphicRenderer2bppHi implements VGARenderer {

        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() &&
                   getGDCGraphicModeEnable() &&
                   getGDCShiftInterleaveModeEnable() &&
                   !getSEQDOTClockDividedByTwo();
        }

        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr;
            if(!getCRTCAddressBit13Mapping() && ((m_charScanline & 0x01) != 0))
                addr |= 0x4000;
            
            // TODO: Does pel scrolling work with CGA display modes?
            
            for(int x = 0; x < m_frameWidth; x += 8, addr += 4, offset += 8) {
                
                int p1 = m_vram.getData(addr << 1);
                int p2 = m_vram.getData((addr << 1) + 1);
                
                m_frameData[offset] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p1 >> 6) & 0x03]];
                m_frameData[offset + 1] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p1 >> 4) & 0x03]];
                m_frameData[offset + 2] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p1 >> 2) & 0x03]];
                m_frameData[offset + 3] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[p1 & 0x03]];
                
                m_frameData[offset + 4] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p2 >> 6) & 0x03]];
                m_frameData[offset + 5] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p2 >> 4) & 0x03]];
                m_frameData[offset + 6] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[(p2 >> 2) & 0x03]];
                m_frameData[offset + 7] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[p2 & 0x03]];
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Graphic 4bpp renderer (low resolution)">
    
    private final class VGAGraphicRenderer4bppLo implements VGARenderer {
        
        private final int[][][] m_pixelLUT;
        
        public VGAGraphicRenderer4bppLo() {
            
            m_pixelLUT = new int[256][8][4];
            
            for(int i = 0; i < 256; i++)
                for(int j = 0; j < 8; j++)
                    for(int k = 0; k < 4; k++)
                        m_pixelLUT[i][j][k] = ((i & (0x80 >> j)) != 0) ? 1 << k : 0;
        }
        
        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() && getGDCGraphicModeEnable() && !(getGDCShiftInterleaveModeEnable() || getGDC256ColorModeEnable()) && getSEQDOTClockDividedByTwo();
        }

        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr;
            
            int enableMask = getATCPlaneEnableMask();
            int p0 = m_vram.getData(addr + 0);
            int p1 = m_vram.getData(addr + 1);
            int p2 = m_vram.getData(addr + 2);
            int p3 = m_vram.getData(addr + 3);
            
            for(int x = 0, px = m_pixelShift; x < m_frameWidth; x += 2, offset += 2, px++) {
                
                if(px > 7) {
                    
                    px = 0; addr += 4;
                    p0 = m_vram.getData(addr + 0);
                    p1 = m_vram.getData(addr + 1);
                    p2 = m_vram.getData(addr + 2);
                    p3 = m_vram.getData(addr + 3);
                }
                
                int pixel = (m_pixelLUT[p0][px][0] |
                             m_pixelLUT[p1][px][1] |
                             m_pixelLUT[p2][px][2] |
                             m_pixelLUT[p3][px][3]) & enableMask;
                
                m_frameData[offset] = m_frameData[offset + 1] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[pixel]];
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Graphic 4bpp renderer (high resolution)">
    
    private final class VGAGraphicRenderer4bppHi implements VGARenderer {
        
        private final int[][][] m_pixelLUT;
        
        public VGAGraphicRenderer4bppHi() {
            
            m_pixelLUT = new int[256][8][4];
            
            for(int i = 0; i < 256; i++)
                for(int j = 0; j < 8; j++)
                    for(int k = 0; k < 4; k++)
                        m_pixelLUT[i][j][k] = ((i & (0x80 >> j)) != 0) ? 1 << k : 0;
        }
        
        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() &&
                   getGDCGraphicModeEnable() &&
                   !(getGDCShiftInterleaveModeEnable() || getGDC256ColorModeEnable()) &&
                   !getSEQDOTClockDividedByTwo();
        }

        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr;
            if(!getCRTCAddressBit13Mapping() && ((m_charScanline & 0x01) != 0))
                addr |= 0x8000;
            
            int enableMask = getATCPlaneEnableMask();
            int p0 = m_vram.getData(addr + 0);
            int p1 = m_vram.getData(addr + 1);
            int p2 = m_vram.getData(addr + 2);
            int p3 = m_vram.getData(addr + 3);
            
            for(int x = 0, px = m_pixelShift; x < m_frameWidth; x++, offset++, px++) {
                
                if(px > 7) {
                    
                    px = 0; addr += 4;
                    p0 = m_vram.getData(addr + 0);
                    p1 = m_vram.getData(addr + 1);
                    p2 = m_vram.getData(addr + 2);
                    p3 = m_vram.getData(addr + 3);
                }
                
                int pixel = (m_pixelLUT[p0][px][0] |
                             m_pixelLUT[p1][px][1] |
                             m_pixelLUT[p2][px][2] |
                             m_pixelLUT[p3][px][3]) & enableMask;
                
                m_frameData[offset] = m_dacPaletteCache[m_dacPixelMask & m_atcPaletteCache[pixel]];
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Graphic 8bpp renderer (low resolution)">
    
    private final class VGAGraphicRenderer8bppLo implements VGARenderer {

        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() &&
                   getGDCGraphicModeEnable() &&
                   getGDC256ColorModeEnable() &&
                   getATCPELClockDividedByTwo();
        }

        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr + ((m_pixelShift & 0x06) >> 1);
            
            for(int x = 0; x < m_frameWidth; x += 8, addr += 4, offset += 8) {
                
                int p0 = m_vram.getData(addr);
                int p1 = m_vram.getData(addr + 1);
                int p2 = m_vram.getData(addr + 2);
                int p3 = m_vram.getData(addr + 3);
                
                m_frameData[offset] = m_frameData[offset + 1] = m_dacPaletteCache[m_dacPixelMask & p0];
                m_frameData[offset + 2] = m_frameData[offset + 3] = m_dacPaletteCache[m_dacPixelMask & p1];
                m_frameData[offset + 4] = m_frameData[offset + 5] = m_dacPaletteCache[m_dacPixelMask & p2];
                m_frameData[offset + 6] = m_frameData[offset + 7] = m_dacPaletteCache[m_dacPixelMask & p3];
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Graphic 8bpp renderer (high resolution)">
    
    private final class VGAGraphicRenderer8bppHi implements VGARenderer {

        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() &&
                   getGDCGraphicModeEnable() &&
                   getGDC256ColorModeEnable() &&
                   !getATCPELClockDividedByTwo();
        }
        
        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr + ((m_pixelShift & 0x06) >> 1);
            
            for(int x = 0; x < m_frameWidth; x += 4, addr += 4, offset += 4) {
                
                int p0 = m_vram.getData(addr);
                int p1 = m_vram.getData(addr + 1);
                int p2 = m_vram.getData(addr + 2);
                int p3 = m_vram.getData(addr + 3);
                
                m_frameData[offset] = m_dacPaletteCache[m_dacPixelMask & p0];
                m_frameData[offset + 1] = m_dacPaletteCache[m_dacPixelMask & p1];
                m_frameData[offset + 2] = m_dacPaletteCache[m_dacPixelMask & p2];
                m_frameData[offset + 3] = m_dacPaletteCache[m_dacPixelMask & p3];
            }
        }
    }
    
    // </editor-fold>
    
    // </editor-fold>
    
    // </editor-fold>
}
