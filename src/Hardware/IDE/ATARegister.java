package Hardware.IDE;



public final class ATARegister {
    
    /* ----------------------------------------------------- *
     * Status register                                       *
     * ----------------------------------------------------- */
    public static final int ATA_SR_BSY = 0x80;    // Busy
    public static final int ATA_SR_DRDY = 0x40;   // Drive ready
    public static final int ATA_SR_DF = 0x20;     // Drive write fault
    public static final int ATA_SR_DSC = 0x10;    // Drive seek complete
    public static final int ATA_SR_DRQ = 0x08;    // Data request ready
    public static final int ATA_SR_CORR = 0x04;   // Corrected data
    public static final int ATA_SR_IDX = 0x02;    // Index
    public static final int ATA_SR_ERR = 0x01;    // Error
    public int status;
    
    /* ----------------------------------------------------- *
     * Error register                                        *
     * ----------------------------------------------------- */
    public static final int ATA_ER_BBK = 0x80;    // Bad sector
    public static final int ATA_ER_UNC = 0x40;    // Uncorrectable data
    public static final int ATA_ER_MC = 0x20;     // No media
    public static final int ATA_ER_IDNF = 0x10;   // ID mark not found
    public static final int ATA_ER_MCR = 0x08;    // No media
    public static final int ATA_ER_ABRT = 0x04;   // Command aborted
    public static final int ATA_ER_TK0NF = 0x02;  // Track 0 not found
    public static final int ATA_ER_AMNF = 0x01;   // No address mark
    public int error;
    
    /* ----------------------------------------------------- *
     * Control register                                      *
     * ----------------------------------------------------- */
    public static final int ATA_CTRL_IDIS = 0x02; // Interrupt disable
    public static final int ATA_CTRL_SRST = 0x04; // Software reset for all ATA drives
    public int control;
    
    /* ----------------------------------------------------- *
     * Command register                                      *
     * ----------------------------------------------------- */
    public static final int ATA_CMD_RECALIBRATE = 0x10;
    public static final int ATA_CMD_READ_PIO = 0x20;
    public static final int ATA_CMD_WRITE_PIO = 0x30;
    public static final int ATA_CMD_READ_VERIFY_PIO = 0x40;
    public static final int ATA_CMD_SEEK = 0x70;
    public static final int ATA_CMD_DRIVE_DIAGNOSTIC = 0x90;
    public static final int ATA_CMD_INIT_DRIVE_PARAMS = 0x91;
    public static final int ATA_CMD_ATAPI_IDENTIFY = 0xa1;
    public static final int ATA_CMD_STANDBY_IMMEDIATE = 0xe0;
    public static final int ATA_CMD_IDENTIFY = 0xec;
    public static final int ATA_CMD_SET_FEATURES = 0xef;
    public int command;
    
    /* ----------------------------------------------------- *
     * Features register                                     *
     * ----------------------------------------------------- */
    public int features;
    
    /* ----------------------------------------------------- *
     * Cylinder, head and sector register                    *
     * ----------------------------------------------------- */
    public int sectorCount;
    public int sectorNumber;
    public int cylinderLow;
    public int cylinderHigh;
    public int driveAndHead;
    
    /* ----------------------------------------------------- *
     * ATA device connected / disconnected                   *
     * ----------------------------------------------------- */
    public boolean isDisconnected;
    
    
    
    public ATARegister() {
        
        isDisconnected = true;
    }
    
    
    
    public void reset() {

        status = ATA_SR_DRDY | ATA_SR_DSC;
        error = ATA_ER_AMNF;
        sectorCount = 0x01;
        sectorNumber = 0x01;
        cylinderLow = isDisconnected ? 0xff : 0x00;
        cylinderHigh = isDisconnected ? 0xff : 0x00;
        driveAndHead = 0x00;
    }
    
    
    
    public int getSectorCount() {
        
        return sectorCount;
    }
    
    public int getSector() {
        
        return sectorNumber;
    }
    
    public void setSector(int value) {
        
        sectorNumber = value & 0xff;
    }
    
    public int getHead() {
        
        return driveAndHead & 0x0f;
    }
    
    public void setHead(int value) {
    
        driveAndHead &= 0xf0;
        driveAndHead |= value & 0x0f;
    }
    
    public int getCylinder() {
        
        return (cylinderHigh << 8) | cylinderLow;
    }
    
    public void setCylinder(int value) {
        
        cylinderHigh = (value >>> 8) & 0xff;
        cylinderLow = value & 0xff;
    }
}
