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

import Hardware.ROM.ET4000.ET4000ROM;
import Hardware.HardwareComponent;
import Hardware.Video.VGA.VGAAdapter;
import java.util.ArrayList;
import Hardware.Video.GraphicsCardListener;
import IOMap.IOMap;



/**
 * TODO: Implement all special purpose registers and behaviours of the
 *       Tseng ET4000. For now the graphics card is just vga "compatible" :)
 * 
 * @see https://archive.org/details/bitsavers_tsengLabsTicsController1990_11230195
 */
public class TsengET4000 extends VGAAdapter {
    
    /* ----------------------------------------------------- *
     * Current RAM bank                                      *
     * ----------------------------------------------------- */
    private int m_gdcSegmentOffset;
    
    
    
    public TsengET4000(GraphicsCardListener listener) {
        
        super(listener, 1 * 1024 * 1024);
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public ArrayList<HardwareComponent> getSubComponents() {
        
        ArrayList<HardwareComponent> c;
        
        c = new ArrayList<>(super.getSubComponents());
        c.add(new ET4000ROM());
        
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
        
        if((m_atc[0x16] & 0x20) != 0)
            return super.getCRTCHorizontalDisplayEnd() << 1;
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
}
