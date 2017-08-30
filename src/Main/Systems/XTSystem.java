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
package Main.Systems;

import Hardware.ROM.Basic.BasicROM;
import Hardware.ROM.System.XTClone.XTCloneBios;
import Hardware.DMAController.Intel8237;
import Hardware.CPU.Intel8086.Intel8086;
import Hardware.Keyboard.XT.XTKeyboard;
import Hardware.Timer.Intel8253;
import Hardware.PPI.Intel8255;
import Hardware.SystemRAM.XTSystemRAM;
import Hardware.InterruptController.PICs;
import Hardware.Mouse.Serial.SerialMouse;
import Hardware.Serial.UART16450;
import Hardware.Speaker.Speaker;
import Hardware.Video.VGA.TsengET4000.TsengET4000;
import Hardware.Video.GraphicsCardListener;
import Hardware.XTIDE.XTIDE;



public final class XTSystem extends JPCSystem {
    
    public static final String SYSTEM_NAME = "XT System";
    
    public XTSystem(GraphicsCardListener gfxListener) {
        
        super(SYSTEM_NAME, 20);
        
        addComponent(new Intel8086(getIOMap(), getMemoryMap(), getScheduler()));
        addComponent(new TsengET4000(gfxListener));
        addComponent(new XTKeyboard());
        addComponent(new Speaker());
        addComponent(new UART16450(1));
        addComponent(new SerialMouse());
        addComponent(new XTIDE());
        addComponent(new PICs(true));
        addComponent(new Intel8253());
        addComponent(new Intel8255());
        addComponent(new Intel8237());
        addComponent(new XTSystemRAM());
        addComponent(new XTCloneBios());
        addComponent(new BasicROM());
    }
}
