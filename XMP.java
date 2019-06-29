import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public class XMP {
    // starts at 0x176 of SPD
    public static final int SIZE = 79;
    private byte[] bytes;
    private boolean[] profileEnabled;   // 0x178, bits 0 and 1
    private byte[] dimmsPerChannel;     // 0x178, bits[2:3] and bits[4:5]
    private byte version;               // 0x179, bits[4:7] | bits[3:0]
    private MTB[] mtb;                  // (0x180, 0x181), (0x182, 0x183)
    private Profile[] profile;          // [0x185, 0x219], [0x220, 0x254]

    public XMP(byte[] bytes) throws IllegalArgumentException {
        if (bytes.length < SIZE) {
            String msg = String.format("Expected %d bytes. Got %d bytes.",
                                       SIZE, bytes.length);
            throw new IllegalArgumentException(msg);
        }

        if (bytes[0] != 0x0C || bytes[1] != 0x4A)
            throw new IllegalArgumentException("Invalid XMP header.");

        this.bytes = Arrays.copyOf(bytes, bytes.length);

        profileEnabled = new boolean[] {
            (bytes[2] & 0x1) == 0x1,
            (bytes[2] >> 1 & 0x1) == 0x1
        };

        dimmsPerChannel = new byte[] {
            (byte)(bytes[2] >> 2 & 0x3),
            (byte)(bytes[2] >> 4 & 0x3)
        };

        version = bytes[3];

        mtb = new MTB[] {
            new MTB(bytes[4], bytes[5]),
            new MTB(bytes[5], bytes[6])
        };

        if (profileEnabled[0] || profileEnabled[1]) {
            profile = new Profile[2];

            if (profileEnabled[0]) {
                byte[] b = Arrays.copyOfRange(bytes, 0x185, 0x220);
                profile[0] = new Profile(b, mtb[0]);
            }

            if (profileEnabled[1]) {
                byte[] b = Arrays.copyOfRange(bytes, 0x220, 0x255);
                profile[1] = new Profile(b, mtb[1]);
            }
        }
    }

    class MTB {
        public byte dividend, divisor;

        public MTB(byte dividend, byte divisor) {
            this.dividend = dividend;
            this.divisor = divisor;
        }

        // returns the MTB time in ns
        public double getTime() {
            return 1.0 * Byte.toUnsignedInt(dividend) /
                         Byte.toUnsignedInt(divisor);
        }
    }

    class Profile {
        private byte[] bytes;
        private MTB mtb;
        private int voltage;    // in mV. 185, 220
        private byte tCKmin;    // 186, 221
        private byte tCLmin;    // 187, 222
        // 188 223: 4 to 11, 189 224: 12 to 18
        private LinkedHashMap<Integer, Boolean> supportedCLs;
        private byte tCWLmin;   // 190, 225
        private byte tRPmin;    // 191, 226
        private byte tRCDmin;   // 192, 227
        private byte tWRmin;    // 193, 228
        // (194 229 bits[3:0]) | (195 230 bits[7:0])
        private short tRASmin;
        // (194 229 bits[7:4]) | (196 231 bits[7:0])
        private short tRCmin;
        // (198 233 bits[7:0]) | (197 232 bits[7:0])
        private short tREFImax;
        // (200 235 bits[7:0]) | (199 234 bits[7:0])
        private short tRFCmin;
        private byte tRTPmin;   // 201, 236
        private byte tRRDmin;   // 202, 237
        // (203 238 bits[3:0]) | (204 239 bits[7:0])
        private short tFAWmin;
        private byte tWTRmin;   // 205, 240

        public Profile(byte[] bytes, MTB mtb) throws IllegalArgumentException {
            if (bytes.length < 33) {
                String msg = "Profile must be at least 33 bytes.";
                throw new IllegalArgumentException(msg);
            }

            this.bytes = Arrays.copyOf(bytes, bytes.length);
            this.mtb = mtb;

            voltage = (bytes[0] & 0x1) * 5 +
                      (bytes[0] >> 1 & 0xF) * 10 +
                      (bytes[0] >> 5 & 0x3) * 100;

            tCKmin = bytes[1];
            tCLmin = bytes[2];
            supportedCLs = new LinkedHashMap<>();
            for (int i = 4; i <= 11; i++) {
                int index = i - 4;
                supportedCLs.put(i, ((bytes[3] >> index) & 0x1) == 0x1);
            }
            for (int i = 12; i <= 18; i++) {
                int index = i - 12;
                supportedCLs.put(i, ((bytes[3] >> index) & 0x1) == 0x1);
            }
            tCWLmin = bytes[5];
            tRPmin = bytes[6];
            tRCDmin = bytes[7];
            tWRmin = bytes[8];
            tRASmin = (short)(((bytes[9] & 0xF) << 8) | bytes[10]);
            tRCmin = (short)(((bytes[9] >> 4 & 0xF) << 8) | bytes[11]);
            tREFImax = (short)((bytes[13] << 8) | bytes[12]);
            tRFCmin = (short)((bytes[15] << 8) | bytes[14]);
            tRTPmin = bytes[16];
            tRRDmin = bytes[17];
            tFAWmin = (short)(((bytes[18] & 0xF) << 8) | bytes[19]);
            tWTRmin = bytes[20];
        }

        public MTB getMTB() { return mtb; }

        public void setMTB(byte dividend, byte divisor) {
            mtb.dividend = dividend;
            mtb.divisor = divisor;
        }

        public int getVoltage() { return voltage; }

        public void setVoltage(int v) { voltage = v; }

        public double getFrequency() {
            double mtbns = 1.0 * Byte.toUnsignedInt(mtb.dividend) / 
                                 Byte.toUnsignedInt(mtb.divisor);
            return 1000.0 / (Byte.toUnsignedInt(tCKmin) * mtbns);
        }

        public LinkedHashMap<Integer, Boolean> getSupportedCLs() {
            return supportedCLs;
        }

        public boolean setSupportedCL(int cl, boolean supported) {
            if (!supportedCLs.containsKey(cl)) return false;
    
            supportedCLs.put(cl, supported);
            return true;
        }

        public LinkedHashMap<String, Integer> getTimings() {
            LinkedHashMap<String, Integer> timings = new LinkedHashMap<>();

            double tCKns = Byte.toUnsignedInt(tCKmin) * mtb.getTime(),
                   tCLns = Byte.toUnsignedInt(tCLmin) * mtb.getTime(),
                   tCL = Math.round(tCLns / tCKns),
                   tCWLns = Byte.toUnsignedInt(tCWLmin) * mtb.getTime(),
                   tCWL = Math.round(tCWLns / tCKns),
                   tRPns = Byte.toUnsignedInt(tRPmin) * mtb.getTime(),
                   tRP = Math.round(tRPns / tCKns),
                   tRCDns = Byte.toUnsignedInt(tRCDmin) * mtb.getTime(),
                   tRCD = Math.round(tRCDns / tCKns),
                   tWRns = Byte.toUnsignedInt(tWRmin) * mtb.getTime(),
                   tWR = Math.round(tWRns / tCKns),
                   tRASns = Short.toUnsignedInt(tRASmin) * mtb.getTime(),
                   tRAS = Math.round(tRASns / tCKns),
                   tRCns = Short.toUnsignedInt(tRCmin) * mtb.getTime(),
                   tRC = Math.round(tRCns / tCKns),
                   tREFIns = Short.toUnsignedInt(tREFImax) * mtb.getTime(),
                   tREFI = Math.round(tREFIns / tCKns),
                   tRFCns = Short.toUnsignedInt(tRFCmin) * mtb.getTime(),
                   tRFC = Math.round(tRFCns / tCKns),
                   tRTPns = Byte.toUnsignedInt(tRTPmin) * mtb.getTime(),
                   tRTP = Math.round(tRTPns / tCKns),
                   tRRDns = Byte.toUnsignedInt(tRRDmin) * mtb.getTime(),
                   tRRD = Math.round(tRRDns / tCKns),
                   tFAWns = Short.toUnsignedInt(tFAWmin) * mtb.getTime(),
                   tFAW = Math.round(tFAWns / tCKns),
                   tWTRns = Byte.toUnsignedInt(tWTRmin) * mtb.getTime(),
                   tWTR = Math.round(tWTRns / tCKns);

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
            timings.put("tCWL", (int)tCWL);
            timings.put("tREFI", (int)tREFI);

            return timings;
        }

        public boolean setTiming(String timing, int ticks) {
            if (ticks < 1) return false;

            int value = (int)Math.round(ticks * Byte.toUnsignedInt(tCKmin));

            if (timing.equals("tCL")) {
                if (!supportedCLs.containsKey(ticks)) return false;
                supportedCLs.put(value, true);
    
                tCLmin = (byte)value;
            }
            else if (timing.equals("tRCD")) {
                tRCDmin = (byte)value;
            }
            else if (timing.equals("tRP")) {
                tRPmin = (byte)value;
            }
            else if (timing.equals("tRAS")) {
                tRASmin = (short)value;
            }
            else if (timing.equals("tRC")) {
                tRCmin = (short)value;
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
            else if (timing.equals("tCWL")) {
                tCWLmin = (byte)value;
            }
            else if (timing.equals("tREFI")) {
                tREFImax = (byte)value;
            }
            else return false;
    
            return true;
        }

        public void setTimings(LinkedHashMap<String, Integer> timings) {
            for (Map.Entry<String, Integer> e : timings.entrySet())
                setTiming(e.getKey(), e.getValue());
        }

        public byte[] getBytes() { return bytes; }

        private void updateBytes() {
            
        }
    }
}