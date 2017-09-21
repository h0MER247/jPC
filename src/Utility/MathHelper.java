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



public final class MathHelper {
    
    private MathHelper() {
    }
    
    
    
    public static double roundToNearestEven(double value) {
        
        return Math.rint(value);
    }
    
    public static double roundToZero(double value) {
        
        //return Math.floor(Math.abs(value)) * Math.signum(value);
        
        if(value < 0.0)
            return Math.ceil(value);
        else
            return Math.floor(value);
    }
    
    public static double roundToNegativeInfinity(double value) {
        
        return Math.floor(value);
    }
    
    public static double roundToPositiveInfinity(double value) {
        
        return Math.ceil(value);
    }
    
    public static boolean isDenormal(double value) {
        
        return (Double.doubleToRawLongBits(value) & 0xfff0000000000000l) == 0 && value != 0.0;
    }
    
    public static boolean isSingalingNaN(double value) {
        
        return (Double.doubleToRawLongBits(value) & 0x0008000000000000l) == 0;
    }
    
    public static boolean isQuietNaN(double value) {
        
        return (Double.doubleToRawLongBits(value) & 0x0008000000000000l) != 0;
    }
}
