/*
 * Copyright (C) 2017 homer
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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.xml.bind.DatatypeConverter;



public final class FileResource {
    
    private static final int BUFFER_SIZE = 32767;
    
    
    
    private static String getMD5Hash(File file) throws IOException,
                                                       NoSuchAlgorithmException {
        
        try(InputStream in = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
            
            MessageDigest md = MessageDigest.getInstance("MD5");

            byte[] data = new byte[BUFFER_SIZE];
            while(in.available() > 0) {

                int n = in.read(data);
                md.update(data, 0, n);
            }
            
            return DatatypeConverter.printHexBinary(md.digest());
        }
    }
    
    
    
    public static void read(int[] dst, File src) throws IOException {
        
        read(dst, src, null);
    }
    
    public static void read(int[] dst, File src, String expectedMD5Hash) throws IOException {
        
        // Check MD5 hash
        if(expectedMD5Hash != null) {
            
            try {

                if(!getMD5Hash(src).equals(expectedMD5Hash))
                    throw new IOException("The MD5 hash for the file " + src + " doesn't match the expected value of " + expectedMD5Hash);
            }
            catch(NoSuchAlgorithmException ex) {

                ex.printStackTrace(System.err);
            }
        }
        
        // Read file
        if(src.length() > dst.length)
            throw new IOException("The destination buffer is too small to fit the file " + src);
        
        try(InputStream in = new BufferedInputStream(new FileInputStream(src), BUFFER_SIZE)) {
        
            for(int i = 0; i < dst.length; i++)
                dst[i] = in.read() & 0xff;
        }
    }
    
    public static void write(int[] src, File dst) throws IOException {
        
        if(!dst.canWrite())
            return;
        
        // Write file
        try(OutputStream out = new BufferedOutputStream(new FileOutputStream(dst), BUFFER_SIZE)) {
        
            for(int i = 0; i < src.length; i++)
                out.write(src[i]);
        }
    }
}
