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
package Hardware.IDE;

import java.nio.charset.Charset;
import java.util.Arrays;



public final class PIOBuffer {
    
    /* ----------------------------------------------------- *
     * Byte buffer backing                                   *
     * ----------------------------------------------------- */
    private final byte[] m_buffer;
    private int m_pos;
    
    
    
    public PIOBuffer(int size) {
        
        m_buffer = new byte[size];
    }
    
    
    
    public void reset() {
        
        Arrays.fill(m_buffer, (byte)0);
        m_pos = 0;
    }
    
    public void setPosition(int pos) {
        
        m_pos = pos;
    }
    
    public int getSize() {
        
        return m_buffer.length;
    }
    
    public int getPosition() {
        
        return m_pos;
    }
    
    public byte[] getArray() {
        
        return m_buffer;
    }
    
    
    
    private int getInt8(int pos) {
        
        return m_buffer[pos] & 0xff;
    }
    
    public int getInt8() {
        
        int data = getInt8(m_pos);
        
        m_pos++;
        return data;
    }
    
    public int getInt16() {
        
        int data = getInt8(m_pos) |
                  (getInt8(m_pos + 1) << 8);
        
        m_pos += 2;
        return data;
    }
    
    public int getInt32() {
        
        int data = getInt8(m_pos) |
                  (getInt8(m_pos + 1) << 8) |
                  (getInt8(m_pos + 2) << 16) |
                  (getInt8(m_pos + 3) << 24);
        
        m_pos += 4;
        return data;
    }
    
    
    
    public void putInt8(int value) {
        
        m_buffer[m_pos++] = (byte)(value & 0xff);
    }
    
    public void putInt16(int value) {
        
        m_buffer[m_pos++] = (byte)(value & 0xff);
        m_buffer[m_pos++] = (byte)((value >>> 8) & 0xff);
    }
    
    public void putInt32(int value) {
        
        m_buffer[m_pos++] = (byte)(value & 0xff);
        m_buffer[m_pos++] = (byte)((value >>> 8) & 0xff);
        m_buffer[m_pos++] = (byte)((value >>> 16) & 0xff);
        m_buffer[m_pos++] = (byte)((value >>> 24) & 0xff);
    }
    
    
    
    public void setInt8(int offset, int value) {
        
        m_buffer[offset] = (byte)(value & 0xff);
    }
    
    public void setInt16(int offset, int value) {
        
        setInt8(offset, value);
        setInt8(offset + 1, value >>> 8);
    }
    
    public void setInt32(int offset, int value) {
        
        setInt8(offset, value);
        setInt8(offset + 1, value >>> 8);
        setInt8(offset + 2, value >>> 16);
        setInt8(offset + 3, value >>> 24);
    }
    
    private void setStr(int offset, int width, String data, Charset charset) {
        
        byte[] b = data.getBytes(charset);
        
        for(int i = 0, j = (width >> 3) - 1; i < b.length; i++)
            m_buffer[(offset + i) ^ j] = b[i];
    }
    
    public void setStr8(int offset, String str, Charset charset) {
        
        setStr(offset, 8, str, charset);
    }
    
    public void setStr16(int offset, String str, Charset charset) {
        
        setStr(offset, 16, str, charset);
    }
    
    public void setStr32(int offset, String str, Charset charset) {
        
        setStr(offset, 32, str, charset);
    }
    
    public void setLPadStr8(int offset, int length, String str, Charset charset) {
        
        setStr8(offset, getPaddedString(length, true, str), charset);
    }
    
    public void setLPadStr16(int offset, int length, String str, Charset charset) {
        
        setStr16(offset, getPaddedString(length, true, str), charset);
    }
    
    public void setLPadStr32(int offset, int length, String str, Charset charset) {
        
        setStr32(offset, getPaddedString(length, true, str), charset);
    }
    
    public void setRPadStr8(int offset, int length, String str, Charset charset) {
        
        setStr8(offset, getPaddedString(length, false, str), charset);
    }
    
    public void setRPadStr16(int offset, int length, String str, Charset charset) {
        
        setStr16(offset, getPaddedString(length, false, str), charset);
    }
    
    public void setRPadStr32(int offset, int length, String str, Charset charset) {
        
        setStr32(offset, getPaddedString(length, false, str), charset);
    }
    
    public void setBool(int offset, int bit, boolean bool) {
        
        offset += bit >> 3;
        bit = 1 << (bit & 0x07);
        
        if(bool)
            m_buffer[offset] |= bit;
        else
            m_buffer[offset] &= ~bit;
    }
    
    private String getPaddedString(int length, boolean justifyLeft, String data) {
        
        if(data.length() > length)
            data = data.substring(0, length);
        
        if(data.length() < length) {
            
            String format = justifyLeft ? "%%-%ds" : "%%%ds";
            data = String.format(String.format(format, length), data);
        }
        
        return data;
    }
}
