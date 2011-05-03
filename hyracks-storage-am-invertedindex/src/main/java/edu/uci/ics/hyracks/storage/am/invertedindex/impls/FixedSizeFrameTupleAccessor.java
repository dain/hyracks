package edu.uci.ics.hyracks.storage.am.invertedindex.impls;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.comm.FrameHelper;
import edu.uci.ics.hyracks.api.comm.IFrameTupleAccessor;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTrait;

public class FixedSizeFrameTupleAccessor implements IFrameTupleAccessor {

    private final int frameSize;
    private ByteBuffer buffer;
    
    private final ITypeTrait[] fields;
    private final int[] fieldStartOffsets;
    private final int tupleSize;
    
    public FixedSizeFrameTupleAccessor(int frameSize, ITypeTrait[] fields) {
        this.frameSize = frameSize;
        this.fields = fields;
        this.fieldStartOffsets = new int[fields.length];
        this.fieldStartOffsets[0] = 0;
        for(int i = 1; i < fields.length; i++) {
            fieldStartOffsets[i] = fieldStartOffsets[i-1] + fields[i-1].getStaticallyKnownDataLength();
        }
        
        int tmp = 0;
        for(int i = 0; i < fields.length; i++) {
            tmp += fields[i].getStaticallyKnownDataLength();
        }
        tupleSize = tmp;
    }
    
    @Override
    public ByteBuffer getBuffer() {
        return buffer;
    }

    @Override
    public int getFieldCount() {
        return fields.length;
    }

    @Override
    public int getFieldEndOffset(int tupleIndex, int fIdx) {
        return getTupleStartOffset(tupleIndex) + fieldStartOffsets[fIdx] + fields[fIdx].getStaticallyKnownDataLength();
    }

    @Override
    public int getFieldLength(int tupleIndex, int fIdx) {
        return fields[fIdx].getStaticallyKnownDataLength();
    }

    @Override
    public int getFieldSlotsLength() {
        return 0;
    }

    @Override
    public int getFieldStartOffset(int tupleIndex, int fIdx) {
        return tupleIndex * tupleSize + fieldStartOffsets[fIdx];
    }

    @Override
    public int getTupleCount() {
        return buffer.getInt(FrameHelper.getTupleCountOffset(frameSize));
    }

    @Override
    public int getTupleEndOffset(int tupleIndex) {
        return getFieldEndOffset(tupleIndex, fields.length-1);
    }

    @Override
    public int getTupleStartOffset(int tupleIndex) {
        return tupleIndex * tupleSize;
    }

    @Override
    public void reset(ByteBuffer buffer) {
        this.buffer = buffer;
    }
}
