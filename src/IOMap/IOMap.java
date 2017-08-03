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
package IOMap;

import java.util.Arrays;
import java.util.HashMap;
import java.util.stream.IntStream;



public final class IOMap {
    
    /* ----------------------------------------------------- *
     * List of registered i/o devices                        *
     * ----------------------------------------------------- */
    private final HashMap<IOMapped, Runnable> m_devices;
    
    /* ----------------------------------------------------- *
     * Unmapped i/o device                                   *
     * ----------------------------------------------------- */
    private class UnmappedIODevice implements IOReadable,
                                              IOWritable {

        private final boolean VERBOSE = false;
        
        @Override public int[] getReadableIOPorts() { return null; }
        @Override public int readIO8(int port) { if(VERBOSE) System.out.printf("Read 8 bit from unmapped i/o port 0x%04x\n", port); return 0xff; }
        @Override public int readIO16(int port) { if(VERBOSE) System.out.printf("Read 16 bit from unmapped i/o port 0x%04x\n", port); return 0xffff; }
        @Override public int readIO32(int port) { if(VERBOSE) System.out.printf("Read 32 bit from unmapped i/o port 0x%04x\n", port); return 0xffffffff; }
        
        @Override public int[] getWritableIOPorts() { return null; }
        @Override public void writeIO8(int port, int data) { if(VERBOSE) System.out.printf("Write 8 bit to unmapped i/o port 0x%04x: 0x%02x\n", port, data); }
        @Override public void writeIO16(int port, int data) { if(VERBOSE) System.out.printf("Write 16 bit to unmapped i/o port 0x%04x: 0x%04x\n", port, data); }
        @Override public void writeIO32(int port, int data) { if(VERBOSE) System.out.printf("Write 32 bit to unmapped i/o port 0x%04x: 0x%08x\n", port, data); }
    }
    private final UnmappedIODevice m_unmapped;
    
    /* ----------------------------------------------------- *
     * I/O mapping                                           *
     * ----------------------------------------------------- */
    private final IOReadable[] m_read;
    private final IOWritable[] m_write;
    
    
    
    public IOMap() {
        
        m_devices = new HashMap<>();
        
        m_read = new IOReadable[0x10000];
        m_write = new IOWritable[0x10000];
        m_unmapped = new UnmappedIODevice();
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Reset">
    
    public void reset() {
        
        Arrays.fill(m_read, m_unmapped);
        Arrays.fill(m_write, m_unmapped);
        
        m_devices.forEach((ioDevice, delegate) -> {
            
            ioDevice.offerIOMapperDelegate(delegate);
            delegate.run();
        });
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Adding of devices that implement IOMapped">
    
    public void addDevice(IOMapped ioDevice) {
        
        if(ioDevice == null)
            throw new IllegalArgumentException("The given i/o device reference was null");
        
        boolean isReadable = ioDevice instanceof IOReadable;
        boolean isWritable = ioDevice instanceof IOWritable;
        
        if(!isReadable && !isWritable)
            throw new IllegalArgumentException("The i/o device has to implement IOReadable or IOWritable");
        
        
        Runnable mapperDelegate = () -> {
            
            if(isReadable)
                mapDeviceRead((IOReadable)ioDevice);
            
            if(isWritable)
                mapDeviceWrite((IOWritable)ioDevice);
        };
        
        m_devices.put(ioDevice, mapperDelegate);
    }
    
    // </editor-fold>
     
    // <editor-fold defaultstate="collapsed" desc="I/O mapping">
    
    private void mapDeviceRead(IOReadable ioDevice) {
        
        for(int i = 0; i < m_read.length; i++) {
            
            if(m_read[i] == ioDevice)
                m_read[i] = m_unmapped;
        }
        
        int[] mapping = ioDevice.getReadableIOPorts();
        
        if(mapping != null) {
            
            for(int port : mapping)
                performMapping(ioDevice, port, m_read);
        }
    }
    
    private void mapDeviceWrite(IOWritable ioDevice) {
        
        for(int i = 0; i < m_write.length; i++) {
            
            if(m_write[i] == ioDevice)
                m_write[i] = m_unmapped;
        }

        int[] mapping = ioDevice.getWritableIOPorts();
        
        if(mapping != null) {
            
            for(int port : mapping)
                performMapping(ioDevice, port, m_write);
        }
    }
    
    private void performMapping(IOMapped ioDevice, int port, IOMapped[] dstMap) {
        
        if(port < 0 || port > 0xffff)
            throw new IllegalArgumentException("Illegal port number specified");

        dstMap[port] = ioDevice;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="I/O port access">
    
    public int readIO8(int port) {
        
        return m_read[port].readIO8(port);
    }
    
    public void writeIO8(int port, int data) {
        
        m_write[port].writeIO8(port, data);
    }
    
    public int readIO16(int port) {
        
        return m_read[port].readIO16(port);
    }
    
    public void writeIO16(int port, int data) {
        
        m_write[port].writeIO16(port, data);
    }
    
    public int readIO32(int port) {
        
        return m_read[port].readIO32(port);
    }
    
    public void writeIO32(int port, int data) {
        
        m_write[port].writeIO32(port, data);
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="Extending I/O ports">
    
    public static int[] extendPorts(int[] ports, int[] additionalPorts) {
        
        return IntStream.concat(Arrays.stream(ports),
                                Arrays.stream(additionalPorts)).toArray();
    }
    
    // </editor-fold>
}
