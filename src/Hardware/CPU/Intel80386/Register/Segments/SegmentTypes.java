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
package Hardware.CPU.Intel80386.Register.Segments;



public final class SegmentTypes {
    
    public static final class SegmentType {
        
        private final String m_description;
        private final boolean m_isLDT;
        private final boolean m_isTaskGate;
        private final boolean m_isTaskStateSegment;
        private final boolean m_isCallGate;
        private final boolean m_isInterruptGate;
        private final boolean m_isTrapGate;
        private final boolean m_is286AvailableTSS;
        private final boolean m_is286BusyTSS;
        private final boolean m_is286CallGate;
        private final boolean m_is286InterruptGate;
        private final boolean m_is286TrapGate;
        private final boolean m_is386AvailableTSS;
        private final boolean m_is386BusyTSS;
        private final boolean m_is386CallGate;
        private final boolean m_is386InterruptGate;
        private final boolean m_is386TrapGate;
        private final boolean m_isGate;
        private final boolean m_is32BitGate;
        private final boolean m_isBusyTaskStateSegment;
        private final boolean m_isAvailableTaskStateSegment;
        private final boolean m_is286TaskStateSegment;
        private final boolean m_is386TaskStateSegment;
        private final boolean m_isAccessed;
        private final boolean m_isDataSegment;
        private final boolean m_isWritableDataSegment;
        private final boolean m_isNotWritableDataSegment;
        private final boolean m_isExpandDownDataSegment;
        private final boolean m_isReadableCodeSegment;
        private final boolean m_isNonConformingCodeSegment;
        private final boolean m_isConformingCodeSegment;
        private final boolean m_isCodeSegment;
        private final boolean m_isReadable;
        private final boolean m_isWritable;
        
        public SegmentType(int type, String description) {
            
            m_description = description;
            
            m_isLDT = type == 0x02;
            m_isTaskGate = type == 0x05;
            
            m_is286AvailableTSS = type == 0x01;
            m_is286BusyTSS = type == 0x03;
            m_is286CallGate = type == 0x04;
            m_is286InterruptGate = type == 0x06;
            m_is286TrapGate = type == 0x07;
            
            m_is386AvailableTSS = type == 0x09;
            m_is386BusyTSS = type == 0x0b;
            m_is386CallGate = type == 0x0c;
            m_is386InterruptGate = type == 0x0e;
            m_is386TrapGate = type == 0x0f;
            
            m_isBusyTaskStateSegment = m_is286BusyTSS || m_is386BusyTSS;
            m_isAvailableTaskStateSegment = m_is286AvailableTSS || m_is386AvailableTSS;
            m_is286TaskStateSegment = m_is286BusyTSS || m_is286AvailableTSS;
            m_is386TaskStateSegment = m_is386BusyTSS || m_is386AvailableTSS;
            m_isTaskStateSegment = m_is286TaskStateSegment || m_is386TaskStateSegment;
            
            m_isCallGate = m_is286CallGate || m_is386CallGate;
            m_isInterruptGate = m_is286InterruptGate || m_is386InterruptGate;
            m_isTrapGate = m_is286TrapGate || m_is386TrapGate;
            
            m_isDataSegment = type >= 0x10 && type <= 0x17;
            m_isCodeSegment = type >= 0x18 && type <= 0x1f;
            m_isAccessed = (m_isDataSegment || m_isCodeSegment) && ((type & 0x01) != 0);
            
            m_isGate = m_isTaskGate || m_isCallGate || m_isTrapGate || m_isInterruptGate;
            
            m_isNonConformingCodeSegment = type >= 0x18 && type <= 0x1b;
            m_isConformingCodeSegment = type >= 0x1c && type <= 0x1f;
            m_isReadableCodeSegment = type == 0x1a || type == 0x1b || type == 0x1e || type == 0x1f;
            m_isWritableDataSegment = type == 0x12 || type == 0x13 || type == 0x16 || type == 0x17;
            m_isNotWritableDataSegment = type == 0x10 || type == 0x11 || type == 0x14 || type == 0x15;
            m_isExpandDownDataSegment = type >= 0x14 && type <= 0x17;
            
            m_isReadable = ((type & 0x10) == 0) || m_isDataSegment || m_isReadableCodeSegment;
            m_isWritable = m_isWritableDataSegment;
            m_is32BitGate = type == 0x0c || type == 0x0e || type == 0x0f;
        }
        
        
        
        public boolean isGate() {
            
            return m_isGate;
        }
        
        public boolean isLDT() {
            
            return m_isLDT;
        }
        
        public boolean isTaskGate() {
            
            return m_isTaskGate;
        }
        
        public boolean isTaskStateSegment() {
            
            return m_isTaskStateSegment;
        }
        
        public boolean isCallGate() {
            
            return m_isCallGate;
        }
        
        public boolean isInterruptGate() {
            
            return m_isInterruptGate;
        }
        
        public boolean isTrapGate() {
            
            return m_isTrapGate;
        }
        
        public boolean isDataSegment() {
            
            return m_isDataSegment;
        }
        
        public boolean isWritableDataSegment() {
            
            return m_isWritableDataSegment;
        }
        
        public boolean isNotWritableDataSegment() {
            
            return m_isNotWritableDataSegment;
        }
        
        public boolean isReadableCodeSegment() {
            
            return m_isReadableCodeSegment;
        }
        
        public boolean isCodeSegment() {
            
            return m_isCodeSegment;
        }
        
        public boolean isAccessed() {
            
            return m_isAccessed;
        }
        
        public boolean isExpandDownDataSegment() {
            
            return m_isExpandDownDataSegment;
        }
        
        public boolean isReadable() {
            
            return m_isReadable;
        }
        
        public boolean isWritable() {
            
            return m_isWritable;
        }
        
        public boolean isNonConformingCodeSegment() {
            
            return m_isNonConformingCodeSegment;
        }
        
        public boolean isConformingCodeSegment() {
            
            return m_isConformingCodeSegment;
        }
        
        public boolean is32BitGate() {
            
            return m_is32BitGate;
        }
        
        public boolean isAvailableTaskStateSegment() {
            
            return m_isAvailableTaskStateSegment;
        }
        
        public boolean isBusyTaskStateSegment() {
            
            return m_isBusyTaskStateSegment;
        }
        
        public boolean is286TaskStateSegment() {
            
            return m_is286TaskStateSegment;
        }
        
        public boolean is386TaskStateSegment() {
            
            return m_is386TaskStateSegment;
        }
        
        
        
        @Override
        public String toString() {
            
            return m_description;
        }
    }
    
    
    
    public static final SegmentType[] SEGMENT_TYPES = {
        
        // System Segments
        new SegmentType(0x00, "Invalid"),
        new SegmentType(0x01, "286 Available TSS"),
        new SegmentType(0x02, "Local Descriptor Table"),
        new SegmentType(0x03, "286 Busy TSS"),
        new SegmentType(0x04, "286 Call Gate"),
        new SegmentType(0x05, "Task Gate"),
        new SegmentType(0x06, "286 Interrupt Gate"),
        new SegmentType(0x07, "286 Trap Gate"),
        new SegmentType(0x08, "Invalid"),
        new SegmentType(0x09, "386 Available TSS"),
        new SegmentType(0x0a, "Invalid"),
        new SegmentType(0x0b, "386 Busy TSS"),
        new SegmentType(0x0c, "386 Call Gate"),
        new SegmentType(0x0d, "Invalid"),
        new SegmentType(0x0e, "386 Interrupt Gate"),
        new SegmentType(0x0f, "386 Trap Gate"),
        
        // Application Segments
        new SegmentType(0x10, "Datasegment (Expand Up, Not Writable, Not Accessed)"),
        new SegmentType(0x11, "Datasegment (Expand Up, Not Writable, Accessed)"),
        new SegmentType(0x12, "Datasegment (Expand Up, Writable, Not Accessed)"),
        new SegmentType(0x13, "Datasegment (Expand Up, Writable, Accessed)"),
        new SegmentType(0x14, "Datasegment (Expand Down, Not Writable, Not Accessed)"),
        new SegmentType(0x15, "Datasegment (Expand Down, Not Writable, Accessed)"),
        new SegmentType(0x16, "Datasegment (Expand Down, Writable, Not Accessed)"),
        new SegmentType(0x17, "Datasegment (Expand Down, Writable, Accessed)"),
        
        new SegmentType(0x18, "Codesegment (Non Conforming, Not Readable, Not Accessed)"),
        new SegmentType(0x19, "Codesegment (Non Conforming, Not Readable, Accessed)"),
        new SegmentType(0x1a, "Codesegment (Non Conforming, Readable, Not Accessed)"),
        new SegmentType(0x1b, "Codesegment (Non Conforming, Readable, Accessed)"),
        new SegmentType(0x1c, "Codesegment (Conforming, Not Readable, Not Accessed)"),
        new SegmentType(0x1d, "Codesegment (Conforming, Not Readable, Accessed)"),
        new SegmentType(0x1e, "Codesegment (Conforming, Readable, Not Accessed)"),
        new SegmentType(0x1f, "Codesegment (Conforming, Readable, Accessed)"),
    };
}
