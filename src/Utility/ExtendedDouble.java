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
package Utility;



public final class ExtendedDouble {
    
    public static class Float80 {
        
        public int low;
        public int mid;
        public int high;
    }
    private static final Float80 FP80 = new Float80();
    
    
    
    private ExtendedDouble() {
    }
    
    
    
    public static final double fromFloat80(int low, int mid, int high) {
        
        long sgn = (high & 0x8000l) >>> 4;
        long exp = high & 0x7fffl;
        long man = ((((long)mid) & 0xffffffffl) << 32) | (((long)low) & 0xffffffffl);
        
        boolean isInfiniteOrNaN = exp == 0x7fff;
        boolean isZero = exp == 0 && man == 0;
        
        
        // Convert exponent and mantissa
        if(isInfiniteOrNaN) {
            
            exp = 0x07ff;
        }
        else if(isZero) {
            
            exp = 0x0000;
        }
        else {
            
            exp = (exp - 0x3fff) + 0x03ff;
            man >>>= 11;
        }
        
        
        // Return the 64 bit double value
        return Double.longBitsToDouble(((exp | sgn) << 52) | (man & 0x000fffffffffffffl));
    }
    
    
    
    public static final Float80 toFloat80(double value) {
        
        long raw = Double.doubleToRawLongBits(value);
        long sgn = (raw & 0x8000000000000000l) >>> 48;
        long exp = (raw & 0x7ff0000000000000l) >>> 52;
        long man = (raw & 0x000fffffffffffffl);
        
        boolean isInfiniteOrNaN = !Double.isFinite(value);
        boolean isZero = value == 0.0 || value == -0.0;
        
        
        // Convert exponent and mantissa
        if(isInfiniteOrNaN) {
            
            exp = 0x7fff;
        }
        else if(!isZero) {
            
            exp = (exp - 0x03ff) + 0x3fff;
            man <<= 11;
        }
        
        // Return the 80 bit double extended value
        FP80.low = (int)(man & 0xffffffffl);
        FP80.mid = (int)((man >>> 32) & 0xffffffffl);
        FP80.high = (int)((exp | sgn) & 0xffffl);
        
        return FP80;
    }
}
