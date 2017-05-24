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



/**
 * TODO: Implement all special purpose registers and behaviours of the
 *       Tseng ET4000. For now the graphics card is just vga "compatible" :)
 * 
 * @see https://archive.org/details/bitsavers_tsengLabsTicsController1990_11230195
 */
public class TsengET4000 extends VGAAdapter {
    
    public TsengET4000(GraphicsCardListener listener) {
        
        super(listener, 1 * 1024 * 1024);
    }
    
    @Override
    public ArrayList<HardwareComponent> getSubComponents() {
        
        ArrayList<HardwareComponent> c;
        
        c = new ArrayList<>(super.getSubComponents());
        c.add(new ET4000ROM());
        
        return c;
    }
}
