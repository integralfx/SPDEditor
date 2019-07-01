import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class SPDEditor {
    private byte[] bytes;
    // 0x06: 1.25v, 1.35v, not 1.50v
    private LinkedHashMap<String, Boolean> voltages;
    private byte tCKmin;            // 0x0C
    // 0x0E: 11 to 4, 0x0F: 18 to 12
    private LinkedHashMap<Integer, Boolean> supportedCLs;

    private byte tCLmin;            // 0x10
    private byte tWRmin;            // 0x11
    private byte tRCDmin;           // 0x12
    private byte tRRDmin;           // 0x13
    private byte tRPmin;            // 0x14
    private short tRASmin;          // (0x15 bits[3:0]) | (0x16 bits[7:0])
    private short tRCmin;           // (0x15 bits[7:4]) | (0x17 bits[7:0]) 
    private short tRFCmin;          // (0x19 bits[7:0]) | (0x18 bits[7:0])
    private byte tWTRmin;           // 0x1A
    private byte tRTPmin;           // 0x1B
    private short tFAWmin;          // (0x1C bits[3:0]) | (0x1D bits[7:0])

    // all in picoseconds (1 thousandth of a ns)
    private byte tCKminCorrection;  // 0x22
    private byte tCLminCorrection;  // 0x23
    private byte tRCDminCorrection; // 0x24
    private byte tRPminCorrection;  // 0x25
    private byte tRCminCorrection;  // 0x26

    private XMP xmp;                // 0xB0, can be null if no XMP

    public static void main(String[] args) {
        /*
        try {
            File f = new File("c-die_2133C11-13-12.bin");
            SPDEditor spd = new SPDEditor(Files.readAllBytes(f.toPath()));

            spd.setFrequency(800);
            spd.setTiming("tCL", 10);
            spd.setTiming("tRCD", 10);
            spd.setTiming("tRP", 10);
            spd.setTiming("tRAS", 30);
            spd.setCLSupported(11, false);
            spd.save("test.bin");
        }
        catch (Exception e) {
            System.out.println(e.getMessage());
            e.printStackTrace();
        }
        */
    }

    public SPDEditor(byte[] bytes) throws IllegalArgumentException {
        if (bytes.length < 128) {
            throw new IllegalArgumentException("Expected 128 bytes. Got " + bytes.length + " bytes.");
        }

        this.bytes = Arrays.copyOf(bytes, bytes.length);

        voltages = new LinkedHashMap<>();
        voltages.put("1.25v", (((bytes[0x06] >> 2) & 0x1) == 0x1));
        voltages.put("1.35v", (((bytes[0x06] >> 1) & 0x1) == 0x1));
        voltages.put("1.50v", ((bytes[0x06]) & 0x1) != 0x1); // not 1.50v

        tCKmin = bytes[0x0C];

        supportedCLs = new LinkedHashMap<>();
        for (int i = 4; i <= 11; i++) {
            int index = i - 4;
            supportedCLs.put(i, ((bytes[0x0E] >> index) & 0x1) == 0x1);
        }
        for (int i = 12; i <= 18; i++) {
            int index = i - 12;
            supportedCLs.put(i, ((bytes[0x0F] >> index) & 0x1) == 0x1);
        }

        tCLmin = bytes[0x10];
        tWRmin = bytes[0x11];
        tRCDmin = bytes[0x12];
        tRRDmin = bytes[0x13];
        tRPmin = bytes[0x14];
        int msb = bytes[0x15] & 0xF;
        tRASmin = (short)((msb << 8) | Byte.toUnsignedInt(bytes[0x16]));
        msb = (bytes[0x15] >> 4) & 0xF;
        tRCmin = (short)((msb << 8) | Byte.toUnsignedInt(bytes[0x17]));
        tRFCmin = (short)((bytes[0x19] << 8) | Byte.toUnsignedInt(bytes[0x18]));
        tWTRmin = bytes[0x1A];
        tRTPmin = bytes[0x1B];
        msb = bytes[0x1C] & 0xF;
        tFAWmin = (short)((msb << 8) | Byte.toUnsignedInt(bytes[0x1D]));

        tCKminCorrection = bytes[0x22];
        tCLminCorrection = bytes[0x23];
        tRCDminCorrection = bytes[0x24];
        tRPminCorrection = bytes[0x25];
        tRCminCorrection = bytes[0x26];

        try {
            xmp = new XMP(Arrays.copyOfRange(bytes, 0xB0, 0xB0 + XMP.SIZE));
        }
        catch (IllegalArgumentException e) {
            xmp = null;
            System.out.println("No XMP found.");
        }
    }

    public XMP getXMP() { return xmp; }

    public LinkedHashMap<String, Boolean> getVoltages() { return voltages; }

    public boolean setVoltage(String voltage, boolean enabled) {
        if (!voltages.containsKey(voltage)) return false;

        voltages.put(voltage, enabled);
        return true;
    }

    public double getFrequency() { 
        double tCKns = tCKmin/8.0 + tCKminCorrection/1000.0;
        tCKns = getMorePrecisetCKns(tCKns);
        return 1000/tCKns; 
    }

    public void setFrequency(double freq) {
        double tCKns = 1000/freq;
        int value = (int)(tCKns*8);
        double correction = Math.round((1000 * (tCKns*8 - value)/8));

        tCKmin = (byte)value;
        tCKminCorrection = (byte)correction;
    }

    public LinkedHashMap<Integer, Boolean> getSupportedCLs() { return supportedCLs; }

    public boolean setCLSupported(int cl, boolean supported) {
        if (!supportedCLs.containsKey(cl)) return false;

        supportedCLs.put(cl, supported);
        return true;
    }

    public LinkedHashMap<String, Integer> getTimings() {
        LinkedHashMap<String, Integer> timings = new LinkedHashMap<>();

        double tCKns = tCKmin/8.0 + tCKminCorrection/1000.0;
        tCKns = getMorePrecisetCKns(tCKns);

        double tCLns = Byte.toUnsignedInt(tCLmin)/8.0 + tCLminCorrection/1000.0,
               tCL = Math.round(tCLns/tCKns),
               tRCDns = Byte.toUnsignedInt(tRCDmin)/8.0 + tRCDminCorrection/1000.0,
               tRCD = Math.round(tRCDns/tCKns),
               tRPns = Byte.toUnsignedInt(tRPmin)/8.0 + tRPminCorrection/1000.0,
               tRP = Math.round(tRPns/tCKns),
               tRASns = Integer.toUnsignedLong(tRASmin)/8.0,
               tRAS = Math.round(tRASns/tCKns),
               tRCns = Integer.toUnsignedLong(tRCmin)/8.0 + tRCminCorrection/1000.0,
               tRC = Math.round(tRCns/tCKns),
               tRFCns = Integer.toUnsignedLong(tRFCmin)/8.0,
               tRFC = Math.round(tRFCns/tCKns),
               tRRDns = Byte.toUnsignedInt(tRRDmin)/8.0,
               tRRD = Math.round(tRRDns/tCKns),
               tFAWns = Integer.toUnsignedLong(tFAWmin)/8.0,
               tFAW = Math.round(tFAWns/tCKns),
               tWRns = Byte.toUnsignedInt(tWRmin)/8.0,
               tWR = Math.round(tWRns/tCKns),
               tWTRns = Byte.toUnsignedInt(tWTRmin)/8.0,
               tWTR = Math.round(tWTRns/tCKns),
               tRTPns = Byte.toUnsignedInt(tRTPmin)/8.0,
               tRTP = Math.round(tRTPns/tCKns);

        timings.put("tCL", (int)tCL);
        timings.put("tRCD", (int)tRCD);
        timings.put("tRP", (int)tRP);
        timings.put("tRAS", (int)tRAS);
        timings.put("tRC", (int)tRC);
        timings.put("tRFC", (int)tRFC);
        timings.put("tRRD", (int)tRRD);
        timings.put("tFAW", (int)tFAW);
        timings.put("tWR", (int)tWR);
        timings.put("tWTR", (int)tWTR);
        timings.put("tRTP", (int)tRTP);

        return timings;
    }

    public boolean setTiming(String timing, int ticks) {
        if (ticks < 1) return false;

        double tCKns = tCKmin/8.0 + tCKminCorrection/1000.0;
        tCKns = getMorePrecisetCKns(tCKns);

        double ns = ticks*tCKns;
        int value = (int)(ns*8);
        double correction = Math.round((1000 * (ns*8 - value)/8));

        if (timing.equals("tCL")) {
            if (!supportedCLs.containsKey(ticks)) return false;
            supportedCLs.put(value, true);

            tCLmin = (byte)value;
            tCLminCorrection = (byte)correction;
        }
        else if (timing.equals("tRCD")) {
            tRCDmin = (byte)value;
            tRCDminCorrection = (byte)correction;
        }
        else if (timing.equals("tRP")) {
            tRPmin = (byte)value;
            tRPminCorrection = (byte)correction;
        }
        else if (timing.equals("tRAS")) {
            tRASmin = (short)value;
        }
        else if (timing.equals("tRC")) {
            tRCmin = (short)value;
            tRCminCorrection = (byte)correction;
        }
        else if (timing.equals("tRFC")) {
            tRFCmin = (short)value;
        }
        else if (timing.equals("tRRD")) {
            tRRDmin = (byte)value;
        }
        else if (timing.equals("tFAW")) {
            tFAWmin = (short)value;
        }
        else if (timing.equals("tWR")) {
            tWRmin = (byte)value;
        }
        else if (timing.equals("tWTR")) {
            tWTRmin = (byte)value;
        }
        else if (timing.equals("tRTP")) {
            tRTPmin = (byte)value;
        }
        else return false;

        return true;
    }

    public void setTimings(LinkedHashMap<String, Integer> timings) {
        for (Map.Entry<String, Integer> e : timings.entrySet())
            setTiming(e.getKey(), e.getValue());
    }

    public void printTimings() {
        for (Map.Entry<String, Integer> e : getTimings().entrySet()) {
            System.out.println(e.getKey() + ": " + e.getValue());
        }
    }

    public boolean save(String filename) {
        updateBytes();

        short crc = crc16XModem();
        // little endian - LSB goes last
        bytes[0x7F] = (byte)((crc >> 8) & 0xFF);
        bytes[0x7E] = (byte)(crc & 0xFF);

        try (FileOutputStream fos = new FileOutputStream(filename)) {
            fos.write(bytes);
            return true;
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    private double getMorePrecisetCKns(double tCKns) {
        int value = (int)Math.round(1000*tCKns);
        // 1066.666...
        if (value == 938) return 0.9375;
        // 933.333...
        else if (value == 1071) return 1000 / (2800/3.0);
        else return tCKns;
    }

    // source: http://mdfs.net/Info/Comp/Comms/CRC16.htm
    private short crc16XModem() {
        int crc = 0;
        for (int i = 0; i <= 0x74; i++) {
            crc ^= bytes[i] << 8;

            for (int j = 0; j < 8; j++) {
                crc <<= 1;
                if ((crc & 0x10000) == 0x10000)
                    crc = (crc ^ 0x1021) & 0xFFFF;
            }
        }

        return (short)crc;
    }

    private void updateBytes() {
        int n = 0;
        if (voltages.get("1.25v")) n |= (1 << 2);
        if (voltages.get("1.35v")) n |= (1 << 1);
        if (!voltages.get("1.50v")) n |= 1;
        bytes[0x06] = (byte)n;

        bytes[0x0C] = tCKmin;

        n = 0;
        for (int i = 4; i <= 11; i++) {
            int index = i - 4;
            if (supportedCLs.get(i)) n |= (1 << index);
        }
        bytes[0x0E] = (byte)n;
        n = 0;
        for (int i = 12; i <= 18; i++) {
            int index = i - 12;
            if (supportedCLs.get(i)) n |= (1 << index);
        }
        bytes[0x0F] = (byte)n;

        bytes[0x10] = tCLmin;
        bytes[0x11] = tWRmin;
        bytes[0x12] = tRCDmin;
        bytes[0x13] = tRRDmin;
        bytes[0x14] = tRPmin;
        int msb = (tRASmin >> 8) & 0xF;
        bytes[0x15] = (byte)msb;
        bytes[0x16] = (byte)(tRASmin & 0xFF);
        msb = (tRCmin >> 8) & 0xFF;
        bytes[0x15] |= (byte)(msb << 4);
        bytes[0x17] = (byte)(tRCmin & 0xFF);
        bytes[0x18] = (byte)(tRFCmin & 0xFF);
        bytes[0x19] = (byte)((tRFCmin >> 8) & 0xF);
        bytes[0x1A] = tWTRmin;
        bytes[0x1B] = tRTPmin;
        bytes[0x1C] = (byte)((tFAWmin >> 8) & 0xF);
        bytes[0x1D] = (byte)(tFAWmin & 0xFF);

        bytes[0x22] = tCKminCorrection;
        bytes[0x23] = tCLminCorrection;
        bytes[0x24] = tRCDminCorrection;
        bytes[0x25] = tRPminCorrection;
        bytes[0x26] = tRCminCorrection;

        if (xmp != null)
            System.arraycopy(xmp.getBytes(), 0, bytes, 0xB0, XMP.SIZE);
    }
}