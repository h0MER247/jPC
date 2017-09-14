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
package Hardware.Video.VGA.TsengET4000;

import Hardware.ROM.Peripherals.ET4000.ET4000Bios;
import Hardware.HardwareComponent;
import Hardware.Video.VGA.VGAAdapter;
import java.util.ArrayList;
import Hardware.Video.GraphicsCardListener;
import Hardware.Video.VGA.VGARenderer;
import IOMap.IOMap;



/**
 * TODO: Implement all special purpose registers and behaviours of the
 *       Tseng ET4000. For now the graphics card is just vga "compatible" :)
 * 
 * @see https://archive.org/details/bitsavers_tsengLabsTicsController1990_11230195
 */
public final class TsengET4000 extends VGAAdapter {
    
    /* ----------------------------------------------------- *
     * Current RAM bank                                      *
     * ----------------------------------------------------- */
    private int m_gdcSegmentOffset;
    
    /* ----------------------------------------------------- *
     * HiColor DAC                                           *
     * ----------------------------------------------------- */
    private int m_dacCounter;
    private int m_dacControl;
    
    
    
    public TsengET4000(GraphicsCardListener listener) {
        
        super(listener, 1 * 1024 * 1024);
        
        addRenderer(new TsengET4000Renderer15bpp());
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public ArrayList<HardwareComponent> getSubComponents() {
        
        ArrayList<HardwareComponent> c;
        
        c = new ArrayList<>(super.getSubComponents());
        c.add(new ET4000Bios());
        
        return c;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return IOMap.extendPorts(
                
            super.getReadableIOPorts(),
            new int[] { 0x3cd }
        );
    }
    
    @Override
    public int readIO8(int port) {
        
        switch(port) {
            
            // HiColor DAC magic access sequence
            case 0x3c6:
                if(m_dacCounter == 4) {
                    
                    m_dacCounter = 0;
                    return m_dacControl;
                }
                m_dacCounter++;
                return super.readIO8(port);
                
            case 0x3c7:
            case 0x3c8:
            case 0x3c9:
                m_dacCounter = 0;
                return super.readIO8(port);
                
            // GDC segment offset
            case 0x3cd:
                return m_gdcSegmentOffset;
                
            default:
                return super.readIO8(port);
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return IOMap.extendPorts(
                
            super.getWritableIOPorts(),
            new int[] { 0x3cd }
        );
    }
    
    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // HiColor DAC magic access sequence
            case 0x3c6:
                if(m_dacCounter == 4) {
                    
                    m_dacControl = data;
                    updateTimings();
                }
                else
                    super.writeIO8(port, data);
                m_dacCounter = 0;
                break;
                
            case 0x3c7:
            case 0x3c8:
            case 0x3c9:
                m_dacCounter = 0;
                super.writeIO8(port, data);
                break;
            
            case 0x3cd:
                m_gdcSegmentOffset = data;
                m_vram.setRamBankOffsetRead((data & 0xf0) << 12);
                m_vram.setRamBankOffsetWrite((data & 0x0f) << 16);
                break;
                
            default:
                super.writeIO8(port, data);
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Handling of changes in certain register values">
    
    // <editor-fold defaultstate="collapsed" desc="CRT Controller">
    
    @Override
    protected int getCRTCHorizontalDisplayEnd() {
        
        if((m_atc[0x16] & 0x30) == 0x30)
            return super.getCRTCHorizontalDisplayEnd() >>> 1;
        else
            return super.getCRTCHorizontalDisplayEnd();
    }
    
    @Override
    protected int getCRTCVerticalTotal() {
        
        return super.getCRTCVerticalTotal() + ((m_crtc[0x35] & 0x02) << 9);
    }
        
    @Override
    protected int getCRTCVerticalDisplayEnd() {
        
        return super.getCRTCVerticalDisplayEnd() + ((m_crtc[0x35] & 0x04) << 8);
    }
    
    @Override
    protected int getCRTCVerticalRetraceStart() {
        
        return super.getCRTCVerticalRetraceStart() + ((m_crtc[0x35] & 0x08) << 7);
    }
    
    @Override
    protected int getCRTCLineCompare() {
        
        return super.getCRTCLineCompare() + ((m_crtc[0x35] & 0x10) << 6);
    }
    
    @Override
    protected int getCRTCHorizontalTotal() {
        
        return super.getCRTCHorizontalTotal() + ((m_crtc[0x3f] & 0x01) << 8);
    }
    
    @Override
    protected int getCRTCVideoAddress() {
        
        return super.getCRTCVideoAddress() + ((m_crtc[0x33] & 0x03) << 16);
    }
    
    private int getCRTCClockSelect2() {
        
        return m_crtc[0x34] & 0x02;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Attribute Controller">
    
    @Override
    protected void updateATC(int regIndex, int changedData) {
        
        // ATC: High-color 16-bit select
        if(regIndex == 0x16 & ((changedData & 0x30) != 0))
            updateTimings();
        
        super.updateATC(regIndex, changedData);
    }
    
    // </editor-fold>
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Recalculation of VGA timing parameters">
    
    @Override
    protected float getVGAClockFrequency() {
        
        switch((getCRTCClockSelect2() << 1) | getMISCClockSelect()) {
            
            case 0:
                return 25175000.0f;
                
            case 1:
                return 28322000.0f;
                
            case 2:
                return 32514000.0f;
                
            case 3:
                return 40000000.0f;
                
            case 5:
                return 65000000.0f;
                
            default:
                return 36000000.0f;
        }
    }
    
    // </editor-fold>
    
    
    
    
    // <editor-fold defaultstate="collapsed" desc="HiColor DAC">
    
    private int getHiColorDACMode() {
        
        return (m_dacControl & 0xe0) >> 4 | (m_dacControl & 0x01);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Custom renderers">
    
    // <editor-fold defaultstate="collapsed" desc="Graphic 15bpp renderer">
    
    private final class TsengET4000Renderer15bpp implements VGARenderer {

        private final int[] m_colorLUT;
        
        public TsengET4000Renderer15bpp() {
            
            m_colorLUT = new int[0x10000];
            
            for(int i = 0; i < 0x10000; i++) {
                
                int r = (i >>> 10) & 0x1f;
                int g = (i >>> 5) & 0x1f;
                int b = i & 0x1f;
                
                r = (r * 255) / 31;
                g = (g * 255) / 31;
                b = (b * 255) / 31;
                
                m_colorLUT[i] = 0xff000000 | (r << 16) | (g << 8) | b;
            }
        }
        
        @Override
        public boolean isSuitableRenderer() {
            
            return isScreenVisible() &&
                   getGDCGraphicModeEnable() &&
                   getGDC256ColorModeEnable() &&
                   !getATCPELClockDividedByTwo() &&
                   ((getHiColorDACMode() & 0x0c) == 0x08);
        }
        
        @Override
        public void drawLine(int offset) {
            
            int addr = m_vramAddr;
            int p1, p2;
            
            for(int x = 0; x < m_frameWidth; x += 2, addr += 4, offset += 2) {
                
                p1 = m_vram.getData(addr) |
                    (m_vram.getData(addr + 1) << 8);
                
                p2 = m_vram.getData(addr + 2) |
                    (m_vram.getData(addr + 3) << 8);
                
                m_frameData[offset] = m_colorLUT[p1];
                m_frameData[offset + 1] = m_colorLUT[p2];
            }
        }
    }
    
    // </editor-fold>
    
    // </editor-fold>
}
