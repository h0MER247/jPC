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
package Hardware.CPU.Intel80386.Exceptions;



public final class CPUException extends RuntimeException {
    
    /* ----------------------------------------------------- *
     * Exception that gets thrown when REP is interrupted    *
     * ----------------------------------------------------- */
    private static final CPUException REP_INTERRUPTED = new CPUException("Interrupted REP", -1, false, PushedIP.Faulted, ErrorClass.Internal); // TODO: Find another way... Implement this properly
    
    /* ----------------------------------------------------- *
     * Preinitialized i386 exceptions                        *
     * ----------------------------------------------------- */
    private static final CPUException DIVIDE_ERROR = new CPUException("Divide Error", 0x00, false, PushedIP.Faulted, ErrorClass.Contributory);
    private static final CPUException BREAKPOINT_REACHED = new CPUException("Breakpoint Reached", 0x03, false, PushedIP.Next, ErrorClass.Benign);
    private static final CPUException OVERFLOW_OCCURRED = new CPUException("Overflow occurred", 0x04, false, PushedIP.Next, ErrorClass.Benign);
    private static final CPUException BOUND_CHECK_FAILED = new CPUException("Bound check failed", 0x05, false, PushedIP.Faulted, ErrorClass.Benign);
    private static final CPUException INVALID_OPCODE = new CPUException("Invalid opcode", 0x06, false, PushedIP.Faulted, ErrorClass.Benign);
    private static final CPUException COPROCESSOR_NOT_AVAILABLE = new CPUException("Coprocessor not available", 0x07, false, PushedIP.Faulted, ErrorClass.Benign);
    private static final CPUException DOUBLE_FAULT = new CPUException("Double fault", 0x08, true, PushedIP.Faulted, ErrorClass.DoubleFault);
    private static final CPUException COPROCESSOR_SEG_OVERRUN = new CPUException("Coprocessor segment overrun", 0x09, false, PushedIP.Next, ErrorClass.Contributory);
    private static final CPUException INVALID_TSS = new CPUException("Invalid Task State Segment", 0x0a, true, PushedIP.Faulted, ErrorClass.Contributory);
    private static final CPUException SEGMENT_NOT_PRESENT = new CPUException("Segment not present", 0x0b, true, PushedIP.Faulted, ErrorClass.Contributory);
    private static final CPUException STACK_FAULT = new CPUException("Stack fault", 0x0c, true, PushedIP.Faulted, ErrorClass.Contributory);
    private static final CPUException GENERAL_PROTECTION_FAULT = new CPUException("General protection fault", 0x0d, true, PushedIP.Faulted, ErrorClass.Contributory);
    private static final CPUException PAGE_FAULT = new CPUException("Page fault", 0x0e, true, PushedIP.Faulted, ErrorClass.PageFault);
    private static final CPUException COPROCESSOR_ERROR = new CPUException("Coprocessor error", 0x0f, false, PushedIP.Faulted, ErrorClass.Benign);
    
    /* ----------------------------------------------------- *
     * State of an exception                                 *
     * ----------------------------------------------------- */
    public enum PushedIP {
        
        Faulted, Next
    }
    public enum ErrorClass {
        
        Internal, Benign, Contributory, PageFault, DoubleFault
    }
    private final String m_name;
    private final int m_vector;
    private final boolean m_isPointingToFaultedInstruction;
    private final boolean m_isNotInternal;
    private final boolean m_isContributory;
    private final boolean m_isPageFault;
    private final boolean m_hasErrorCode;
    private int m_errorCode;
    
    
    
    public CPUException(String name,
                        int vector,
                        boolean hasErrorCode,
                        PushedIP pushedIP,
                        ErrorClass errorClass) {
        
        m_name = name;
        m_vector = vector;
        
        m_isPointingToFaultedInstruction = pushedIP == PushedIP.Faulted;
        m_isContributory = errorClass == ErrorClass.Contributory;
        m_isPageFault = errorClass == ErrorClass.PageFault;
        m_isNotInternal = errorClass != ErrorClass.Internal;
        
        m_hasErrorCode = hasErrorCode;
        m_errorCode = 0x0000;
    }
    
    
    
    public boolean combinesToDoubleFaultWith(CPUException otherException) {
       
        return (m_isContributory && otherException.m_isContributory) ||
               (m_isPageFault && otherException.m_isContributory) ||
               (m_isPageFault && otherException.m_isPageFault);
    }
    
    
    
    public int getVector() {
        
        return m_vector;
    }
    
    public boolean hasErrorCode() {
        
        return m_hasErrorCode;
    }
    
    public int getErrorCode() {
        
        return m_errorCode;
    }
    
    public void setErrorCode(int errorCode) {
        
        m_errorCode = errorCode;
    }
    
    public boolean isPointingToFaultedInstruction() {
        
        return m_isPointingToFaultedInstruction;
    }
    
    public boolean isNotInternalException() {
        
        return m_isNotInternal;
    }
    
    
    
    @Override
    public String toString() {
        
        return m_name;
    }
    
    

    public static CPUException getREPInterrupted() {
        
        return REP_INTERRUPTED;
    }
    
    public static CPUException getDivideError() {
        
        return DIVIDE_ERROR;
    }
    
    public static CPUException getBreakpointReached() {
        
        return BREAKPOINT_REACHED;
    }
    
    public static CPUException getOverflowOccurred() {
        
        return OVERFLOW_OCCURRED;
    }
    
    public static CPUException getBoundCheckFailed() {
        
        return BOUND_CHECK_FAILED;
    }
    
    public static CPUException getInvalidOpcode() {
        
        return INVALID_OPCODE;
    }
    
    public static CPUException getCoprocessorNotAvailable() {
        
        return COPROCESSOR_NOT_AVAILABLE;
    }
    
    public static CPUException getDoubleFault() {
        
        return DOUBLE_FAULT;
    }
    
    public static CPUException getCoprocessorSegmentOverrun() {
        
        return COPROCESSOR_SEG_OVERRUN;
    }
    
    public static CPUException getInvalidTSS(int data) {
        
        INVALID_TSS.setErrorCode(data);
        return INVALID_TSS;
    }
    
    public static CPUException getSegmentNotPresent(int data) {
        
        SEGMENT_NOT_PRESENT.setErrorCode(data);
        return SEGMENT_NOT_PRESENT;
    }
    
    public static CPUException getStackFault(int data) {
        
        STACK_FAULT.setErrorCode(data);
        return STACK_FAULT;
    }
    
    public static CPUException getGeneralProtectionFault(int data) {
        
        GENERAL_PROTECTION_FAULT.setErrorCode(data);
        return GENERAL_PROTECTION_FAULT;
    }
    
    public static CPUException getPageProtectionViolation(boolean isWriteAccess, boolean isUserAccess) {
        
        int errorCode = 0x01;
        
        if(isWriteAccess)
            errorCode |= 0x02;
        if(isUserAccess)
            errorCode |= 0x04;
        
        PAGE_FAULT.setErrorCode(errorCode);
        return PAGE_FAULT;
    }
    
    public static CPUException getPageNotPresent(boolean isWriteAccess,  boolean isUserAccess) {
        
        int errorCode = 0x00;
        
        if(isWriteAccess)
            errorCode |= 0x02;
        if(isUserAccess)
            errorCode |= 0x04;
        
        PAGE_FAULT.setErrorCode(errorCode);
        return PAGE_FAULT;
    }   
    
    public static CPUException getCoprocessorError() {
        
        return COPROCESSOR_ERROR;
    }
}
