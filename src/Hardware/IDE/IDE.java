package Hardware.IDE;

import Hardware.CMOS.CMOSMap;
import Hardware.HardwareComponent;
import static Hardware.IDE.ATARegister.*;
import Hardware.IDE.Commands.*;
import Hardware.InterruptController.PICs;
import IOMap.IOReadable;
import IOMap.IOWritable;
import Main.Systems.ComponentConfig;
import Main.Systems.ComponentConfig.Type;



/**
 * Known issues: - Only a few commands implemented
 *               - Public variables in ATARegister.java
 */
public final class IDE implements HardwareComponent,
                                  IOReadable,
                                  IOWritable {
    
    /* ----------------------------------------------------- *
     * Whether this ide channel is primary or secondary      *
     * ----------------------------------------------------- */
    private final ATADrive[] m_drives;
    private ATADrive m_currentDrive;
    private ATADrive m_otherDrive;
    
    /* ----------------------------------------------------- *
     * ATA commands                                          *
     * ----------------------------------------------------- */
    private final ATACommand m_cmdIdentify;
    private final ATACommand m_cmdReadPIO;
    private final ATACommand m_cmdWritePIO;
    private final ATACommand m_cmdInitDriveParams;
    private final ATACommand m_cmdDriveDiagnostic;
    private final ATACommand m_cmdRecalibrate;
    private final ATACommand m_cmdReadVerifyPIO;
    private final ATACommand m_cmdSetFeatures;
    private final ATACommand m_cmdSeek;
    private final ATACommand m_cmdPIdentify;
    private final ATACommand m_cmdStandbyImmediate;
    private ATACommand m_currentCommand;
    
    /* ----------------------------------------------------- *
     * Port mapping                                          *
     * ----------------------------------------------------- */
    private final int[] m_portMapping;
    private final boolean m_isPrimaryAdapter;
    
    
    
    public IDE(boolean isPrimaryAdapter, int irqNumber) {
        
        m_isPrimaryAdapter = isPrimaryAdapter;
        
        m_drives = new ATADrive[2];
        m_drives[0] = new ATADrive(irqNumber);
        m_drives[1] = new ATADrive(irqNumber);
        
        m_cmdIdentify = new Identify(this);
        m_cmdReadPIO = new ReadPIO(this);
        m_cmdWritePIO = new WritePIO(this);
        m_cmdInitDriveParams = new InitDriveParams(this);
        m_cmdDriveDiagnostic = new DriveDiagnostic(this);
        m_cmdRecalibrate = new Recalibrate(this);
        m_cmdReadVerifyPIO = new ReadVerifyPIO(this);
        m_cmdSetFeatures = new SetFeatures(this);
        m_cmdSeek = new Seek(this);
        m_cmdPIdentify = new AtapiIdentify(this);
        m_cmdStandbyImmediate = new StandbyImmediate(this);
        
        m_portMapping = isPrimaryAdapter ? new int[] { 0x1f0, 0x1f1, 0x1f2, 0x1f3, 0x1f4, 0x1f5, 0x1f6, 0x1f7, 0x3f6 } :
                                           new int[] { 0x170, 0x171, 0x172, 0x173, 0x174, 0x175, 0x176, 0x177, 0x376 };
    }
    
    
    
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of HardwareComponent">
    
    @Override
    public void reset() {
        
        m_drives[0].getRegister().reset();
        m_drives[1].getRegister().reset();
        
        m_currentDrive = m_drives[0];
        m_otherDrive = m_drives[1];
        
        m_currentCommand = null;
    }
    
    @Override
    public void wireWith(HardwareComponent component) {
        
        if(component instanceof PICs) {
            
            PICs pics = (PICs)component;
            m_drives[0].setPICs(pics);
            m_drives[1].setPICs(pics);
        }
    }
    
    @Override
    public String getConfigCategory() {
        
        return m_isPrimaryAdapter ? "IDE Pri." : "IDE Sec.";
    }
    
    @Override
    public void provideConfigValues(ComponentConfig.Builder builder) {
        
        builder.value("Master", "", Type.FileValue, value -> mountImage(0, value))
               .isResettingSystem()
               .build();
        
        builder.value("Slave", "", Type.FileValue, value -> mountImage(1, value))
               .isResettingSystem()
               .build();
    }
    
    @Override
    public void updateCMOS(CMOSMap map) {
        
        if(!isPrimaryAdapter())
            return;
        
        for(int i = 0; i < 2; i++) {

            if(m_drives[i].isDisconnected()) {

                map.setDriveGeometry(i, 0, 0, 0, 0);
            }
            else {

                map.setDriveGeometry(

                    i,
                    47,
                    m_drives[i].getCylindersDefault(),
                    m_drives[i].getHeadsDefault(),
                    m_drives[i].getSectorsDefault()
                );
            }
        }
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Interface implementation of IOReadable / IOWritable">
    
    @Override
    public int[] getReadableIOPorts() {
        
        return m_portMapping;
    }

    @Override
    public int readIO8(int port) {
        
        switch(port) {
            
            // Data register
            case 0x170:
            case 0x1f0:
                return m_currentDrive.isDisconnected() ? 0x00 : readPIO(1);
            
            // Error register
            case 0x171:
            case 0x1f1:
                return m_currentDrive.getRegister().error;
                
            // Sector count register
            case 0x172:
            case 0x1f2:
                return m_currentDrive.getRegister().sectorCount;
                
            // Sector number register
            case 0x173:
            case 0x1f3:
                return m_currentDrive.getRegister().sectorNumber;
                
            // Cylinder low register
            case 0x174:
            case 0x1f4:
                return m_currentDrive.getRegister().cylinderLow;
                
            // Cylinder high register
            case 0x175:
            case 0x1f5:
                return m_currentDrive.getRegister().cylinderHigh;
                
            // Drive / head select register
            case 0x1f6:
            case 0x176:
                return m_currentDrive.getRegister().driveAndHead;
            
            // Status register
            case 0x177:
            case 0x1f7:
                if(m_currentDrive.isDisconnected()) {
                    
                    return 0x00;
                }
                else {
                    
                    m_currentDrive.clearIRQ();
                    return m_currentDrive.getRegister().status;
                }
                
            // Alternate Status register
            case 0x376:
            case 0x3f6:
                return m_currentDrive.getRegister().status;
            
            default:
                throw new IllegalArgumentException(String.format("Illegal access to port %04xh", port));
        }
    }

    @Override
    public int readIO16(int port) {
        
        switch(port) {
            
            case 0x170:
            case 0x1f0:
                return readPIO(2);
            
            default:
                throw new IllegalArgumentException(String.format("Illegal access to port %04xh", port));
        }
    }

    @Override
    public int readIO32(int port) {
        
        switch(port) {
            
            case 0x170:
            case 0x1f0:
                return readPIO(4);
            
            default:
                throw new IllegalArgumentException(String.format("Illegal access to port %04xh", port));
        }
    }
    
    @Override
    public int[] getWritableIOPorts() {
        
        return m_portMapping;
    }

    @Override
    public void writeIO8(int port, int data) {
        
        switch(port) {
            
            // Data register
            case 0x170:
            case 0x1f0:
                writePIO(data, 1);
                break;
                
            // Feature register
            case 0x171:
            case 0x1f1:
                m_currentDrive.getRegister().features = data;
                m_otherDrive.getRegister().features = data;
                break;
                
            // Sector count register
            case 0x172:
            case 0x1f2:
                m_currentDrive.getRegister().sectorCount = data;
                m_otherDrive.getRegister().sectorCount = data;
                break;
                
            // Sector number register
            case 0x173:
            case 0x1f3:
                m_currentDrive.getRegister().sectorNumber = data;
                m_otherDrive.getRegister().sectorNumber = data;
                break;
                
            // Cylinder low register
            case 0x174:
            case 0x1f4:
                m_currentDrive.getRegister().cylinderLow = data;
                m_otherDrive.getRegister().cylinderLow = data;
                break;
                
            // Cylinder high register
            case 0x175:
            case 0x1f5:
                m_currentDrive.getRegister().cylinderHigh = data;
                m_otherDrive.getRegister().cylinderHigh = data;
                break;
                
            // Drive / head select register
            case 0x176:
            case 0x1f6:
                updateDrive(data);
                m_currentDrive.getRegister().driveAndHead = data;
                break;
                
            // Command register
            case 0x177:
            case 0x1f7:
                executeCommand(data);
                m_currentDrive.getRegister().command = data;
                break;
            
            // Control register
            case 0x376:
            case 0x3f6:
                updateControl(data);
                m_currentDrive.getRegister().control = data;
                break;
                
            default:
                throw new IllegalArgumentException(String.format("Illegal access to port %04xh", port));
        }
    }

    @Override
    public void writeIO16(int port, int data) {
        
        switch(port) {
            
            case 0x170:
            case 0x1f0:
                writePIO(data, 2);
                break;
            
            default:
                throw new IllegalArgumentException(String.format("Illegal access to port %04xh", port));
        }
    }

    @Override
    public void writeIO32(int port, int data) {
        
        switch(port) {
            
            case 0x170:
            case 0x1f0:
                writePIO(data, 4);
                break;
            
            default:
                throw new IllegalArgumentException(String.format("Illegal access to port %04xh", port));
        }
    }
    
    // </editor-fold>
    
    // <editor-fold defaultstate="collapsed" desc="PIO transfers">
    
    private void writePIO(int data, int size) {
        
        if(m_currentCommand == null)
            throw new IllegalStateException("Something is not right here");
        
        PIOBuffer pio = m_currentDrive.getPIOBuffer();
        
        switch(size) {
            
            case 1: pio.putInt16(data | (data << 8)); break;
            case 2: pio.putInt16(data); break;
            default: pio.putInt32(data); break;
        }
        if(pio.getPosition() >= 512)
            m_currentCommand.onPIOBufferEvent();
    }
    
    private int readPIO(int size) {
        
        if(m_currentCommand == null)
            throw new IllegalStateException("Something is not right here");
        
        PIOBuffer pio = m_currentDrive.getPIOBuffer();
        
        int data;
        switch(size) {
            
            case 1: data = pio.getInt16() & 0xff; break;
            case 2: data = pio.getInt16(); break;
            default: data = pio.getInt32(); break;
        }
        
        if(pio.getPosition() >= 512)
            m_currentCommand.onPIOBufferEvent();
        
        return data;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Some helper methods">
    
    public void updateDrive(int data) {
        
        boolean isLBAEnabled = (data & 0x40) != 0;
        int driveSelect = (data >>> 4) & 0x01;
        m_currentDrive = m_drives[driveSelect];
        m_otherDrive = m_drives[driveSelect ^ 0x01];
        
        m_otherDrive.setLBAEnable(isLBAEnabled);
        m_currentDrive.setLBAEnable(isLBAEnabled);
        m_currentDrive.updateIRQ();
    }
    
    private void updateControl(int data) {
        
        // Set BSY while reset is held high and reset the current drive if
        // reset is released
        if(((m_currentDrive.getRegister().control ^ data) & ATA_CTRL_SRST) != 0) {
            
            m_currentDrive.getRegister().status = ATA_SR_BSY;
            m_otherDrive.getRegister().status = ATA_SR_BSY;
            
            if((data & ATA_CTRL_SRST) == 0) {
                
                m_otherDrive.getRegister().reset();
                m_currentDrive.getRegister().reset();
                
                 // TODO: Find a better way
                updateDrive(m_currentDrive.getRegister().driveAndHead);
            }
        }
        
        // Update interrupts
        m_currentDrive.setInterruptEnable((data & ATA_CTRL_IDIS) == 0);
        m_currentDrive.updateIRQ();
    }
    
    private void executeCommand(int data) {
        
        switch(data) {
            
            case ATA_CMD_READ_PIO: m_currentCommand = m_cmdReadPIO; break;
            case ATA_CMD_WRITE_PIO: m_currentCommand = m_cmdWritePIO; break;
            case ATA_CMD_INIT_DRIVE_PARAMS: m_currentCommand = m_cmdInitDriveParams; break;
            case ATA_CMD_IDENTIFY: m_currentCommand = m_cmdIdentify; break;
            case ATA_CMD_DRIVE_DIAGNOSTIC: m_currentCommand = m_cmdDriveDiagnostic; break;
            case ATA_CMD_RECALIBRATE: m_currentCommand = m_cmdRecalibrate; break;
            case ATA_CMD_READ_VERIFY_PIO: m_currentCommand = m_cmdReadVerifyPIO; break;
            case ATA_CMD_SET_FEATURES: m_currentCommand = m_cmdSetFeatures; break;
            case ATA_CMD_SEEK: m_currentCommand = m_cmdSeek; break;
            case ATA_CMD_ATAPI_IDENTIFY: m_currentCommand = m_cmdPIdentify; break;
            case ATA_CMD_STANDBY_IMMEDIATE: m_currentCommand = m_cmdStandbyImmediate; break;
            
            default:
                throw new IllegalArgumentException(String.format("Unknown IDE Command: %02X", data));
        }
        
        if(m_currentCommand.init())
            m_currentCommand.onExecute();
        else
            m_currentCommand = null;
    }
    
    public boolean isPrimaryAdapter() {
        
        return m_isPrimaryAdapter;
    }
    
    public boolean isDriveIndicatorLit() {
        
        return m_drives[0].isDriveIndicatorLit() ||
               m_drives[1].isDriveIndicatorLit();
    }
    
    public ATADrive getCurrentDrive() {
        
        return m_currentDrive;
    }
    
    public ATADrive getOtherDrive() {
        
        return m_otherDrive;
    }
    
    // </editor-fold>
    // <editor-fold defaultstate="collapsed" desc="Image mounting">
    
    private boolean mountImage(int driveIdx, String image) {
        
        if(image.isEmpty())
            return m_drives[driveIdx].ejectImage();
        else
            return m_drives[driveIdx].mountImage(image);
    }
    
    // </editor-fold>
}
