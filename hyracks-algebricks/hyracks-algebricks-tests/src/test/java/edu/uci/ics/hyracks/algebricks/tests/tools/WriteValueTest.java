package edu.uci.ics.hyracks.algebricks.tests.tools;

import java.io.DataOutput;
import java.io.DataOutputStream;

import org.junit.Test;

import edu.uci.ics.hyracks.algebricks.core.utils.WriteValueTools;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ByteArrayAccessibleOutputStream;

public class WriteValueTest {

    private ByteArrayAccessibleOutputStream baaos = new ByteArrayAccessibleOutputStream();

    @Test
    public void writeIntegers() throws Exception {
        writeIntTest(6);
        writeIntTest(1234);
        writeIntTest(-1234);
        writeIntTest(Integer.MAX_VALUE);
        writeIntTest(Integer.MAX_VALUE - 1);
        writeIntTest(Integer.MIN_VALUE);
        writeIntTest(Integer.MIN_VALUE + 1);
    }

    @Test
    public void writeLongs() throws Exception {
        writeLongTest(Integer.MAX_VALUE);
        writeLongTest(Integer.MAX_VALUE - 1);
        writeLongTest(Integer.MIN_VALUE);
        writeLongTest(Integer.MIN_VALUE + 1);
        writeLongTest(0L);
        writeLongTest(1234567890L);
        writeLongTest(-1234567890L);
    }

    @Test
    public void writeUTF8Strings() throws Exception {
        ByteArrayAccessibleOutputStream interm = new ByteArrayAccessibleOutputStream();
        DataOutput dout = new DataOutputStream(interm);
        writeUTF8Test("abcdefABCDEF", dout, interm);
        writeUTF8Test("šťžľčěďňřůĺ", dout, interm);
        writeUTF8Test("ĂăŞşŢţ", dout, interm);
    }

    private void writeIntTest(int i) throws Exception {
        baaos.reset();
        WriteValueTools.writeInt(i, baaos);
        byte[] goal = Integer.toString(i).getBytes();
        if (baaos.size() != goal.length) {
            throw new Exception("Expecting to write " + i + " in " + goal.length + " bytes, but found " + baaos.size()
                    + " bytes.");
        }
        for (int k = 0; k < goal.length; k++) {
            if (goal[k] != baaos.getByteArray()[k]) {
                throw new Exception("Expecting to write " + i + " as " + goal + ", but found " + baaos.getByteArray()
                        + " instead.");
            }
        }
    }

    private void writeLongTest(long x) throws Exception {
        baaos.reset();
        WriteValueTools.writeLong(x, baaos);
        byte[] goal = Long.toString(x).getBytes();
        if (baaos.size() != goal.length) {
            throw new Exception("Expecting to write " + x + " in " + goal.length + " bytes, but found " + baaos.size()
                    + " bytes.");
        }
        for (int k = 0; k < goal.length; k++) {
            if (goal[k] != baaos.getByteArray()[k]) {
                throw new Exception("Expecting to write " + x + " as " + goal + ", but found " + baaos.getByteArray()
                        + " instead.");
            }
        }
    }

    private void writeUTF8Test(String str, DataOutput dout, ByteArrayAccessibleOutputStream interm) throws Exception {
        interm.reset();
        dout.writeUTF(str);
        baaos.reset();
        WriteValueTools.writeUTF8String(interm.getByteArray(), 0, interm.size(), baaos);
        byte[] b = str.getBytes("UTF-8");
        if (baaos.size() != b.length + 2) {
            throw new Exception("Expecting to write " + b + " in " + b.length + " bytes, but found " + baaos.size()
                    + " bytes.");
        }
        if (baaos.getByteArray()[0] != '\"' || baaos.getByteArray()[baaos.size() - 1] != '\"') {
            throw new Exception("Missing quotes.");
        }
        for (int k = 0; k < b.length; k++) {
            if (b[k] != baaos.getByteArray()[k + 1]) {
                throw new Exception("Expecting to write " + b + ", but found " + baaos.getByteArray() + " instead.");
            }
        }
    }
}
