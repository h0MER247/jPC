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
package Hardware.ROM;

import Hardware.HardwareComponent;
import MemoryMap.MemoryReadable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import javax.xml.bind.DatatypeConverter;



public abstract class ROM implements HardwareComponent,
                                     MemoryReadable {
    
    /* ----------------------------------------------------- *
     * ROM data                                              *
     * ----------------------------------------------------- */
    private int m_data[];
    
    /* ----------------------------------------------------- *
     * ROM mapping information                               *
     * ----------------------------------------------------- */
    private final int m_startAddress;
    
    
    
    public ROM(String resource, String md5Hash, int startAddress, int length, boolean isOptional) {
        
        m_startAddress = startAddress;
        
        //
        // Get input stream
        //
        URL url;
        File file;
        InputStream in = null;
        
        try {

            // Try to read the rom image from a resource
            if((url = getClass().getResource(resource)) != null) {
                
                in = url.openConnection().getInputStream();
            }
            // Try to read the rom image from a file
            else if((file = new File(resource)).exists()) {

                in = new FileInputStream(file);
            }
            // Report error
            else if(!isOptional) {

                throw new IOException(String.format("%s could not be found", resource));
            }
        }
        catch(IOException ex) {

            if(!isOptional)
                throw new IllegalArgumentException("An error occured while reading the rom image", ex);
        }
        
        //
        // Initialize rom data
        //
        m_data = new int[length];
        Arrays.fill(m_data, 0xff);
        
        //
        // Read rom data
        //
        if(in != null) {
            
            try {
                
                // Initialize MD5 calculation
                MessageDigest md = null;
                try {

                    md = MessageDigest.getInstance("MD5");
                }
                catch(NoSuchAlgorithmException ex) {
                }

                // Read file
                byte[] buffer = new byte[32768];
                int offset = 0;
                
                while(in.available() > 0) {
                    
                    int n;
                    if((n = in.read(buffer, 0, Math.min(Math.min(in.available(), buffer.length), m_data.length))) <= 0)
                        throw new IllegalStateException("What happened?");
                    
                    for(int i = 0; i < n; i++, offset++)
                        m_data[offset] = buffer[i] & 0xff;

                    if(md != null)
                        md.update(buffer, 0, n);
                }
                
                // Check MD5
                if(md != null) {
                    
                    String computedHash = DatatypeConverter.printHexBinary(md.digest());
                    if(md5Hash != null && !md5Hash.equals(computedHash))
                        throw new IllegalArgumentException("The calculated MD5 hash doesn't match the expected value");
                    
                    //System.out.printf("%s: %s\n", resource, computedHash);
                }
            }
            catch(IOException ex) {

                throw new IllegalArgumentException("An error occured while reading the rom image", ex);
            }
        }
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of MemoryReadable">
    
    @Override
    public int[][] getReadableMemoryAddresses() {
        
        return new int[][] {
            
            new int[] { m_startAddress, m_data.length, 0x0000 }
        };
    }
    
    @Override
    public int readMEM8(int address) {
        
        return m_data[address];
    }

    @Override
    public int readMEM16(int address) {
        
        return m_data[address] |
              (m_data[address + 1] << 8);
    }
    
    @Override
    public int readMEM32(int address) {
        
        return m_data[address] |
              (m_data[address + 1] << 8) |
              (m_data[address + 2] << 16) |
              (m_data[address + 3] << 24);
    }
    
    // </editor-fold>
}
