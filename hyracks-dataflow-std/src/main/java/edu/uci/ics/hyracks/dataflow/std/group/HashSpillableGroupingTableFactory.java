/*
 * Copyright 2009-2010 by The Regents of the University of California
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * you may obtain a copy of the License from
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package edu.uci.ics.hyracks.dataflow.std.group;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksStageletContext;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.INormalizedKeyComputer;
import edu.uci.ics.hyracks.api.dataflow.value.INormalizedKeyComputerFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITuplePartitionComputer;
import edu.uci.ics.hyracks.api.dataflow.value.ITuplePartitionComputerFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTrait;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTuplePairComparator;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.std.aggregators.IAggregatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.aggregators.IAggregatorDescriptorFactory;
import edu.uci.ics.hyracks.dataflow.std.structures.ISerializableTable;
import edu.uci.ics.hyracks.dataflow.std.structures.SerializableHashTable;
import edu.uci.ics.hyracks.dataflow.std.structures.TuplePointer;

public class HashSpillableGroupingTableFactory implements ISpillableTableFactory {

    private static final long serialVersionUID = 1L;
    private final ITuplePartitionComputerFactory tpcf;
    private final int tableSize;

    public HashSpillableGroupingTableFactory(ITuplePartitionComputerFactory tpcf, int tableSize) {
        this.tpcf = tpcf;
        this.tableSize = tableSize;
    }

    @Override
    public ISpillableTable buildSpillableTable(final IHyracksStageletContext ctx, final int[] keyFields,
            final IBinaryComparatorFactory[] comparatorFactories,
            final INormalizedKeyComputerFactory firstKeyNormalizerFactory,
            final IAggregatorDescriptorFactory aggregateDescriptorFactory, final RecordDescriptor inRecordDescriptor,
            final RecordDescriptor outRecordDescriptor, final int framesLimit) throws HyracksDataException {
        final int[] storedKeys = new int[keyFields.length];
        @SuppressWarnings("rawtypes")
        ISerializerDeserializer[] storedKeySerDeser = new ISerializerDeserializer[keyFields.length];
        for (int i = 0; i < keyFields.length; i++) {
            storedKeys[i] = i;
            storedKeySerDeser[i] = inRecordDescriptor.getFields()[keyFields[i]];
        }

        RecordDescriptor internalRecordDescriptor = outRecordDescriptor;
        final FrameTupleAccessor storedKeysAccessor1;
        final FrameTupleAccessor storedKeysAccessor2;
        if (keyFields.length >= outRecordDescriptor.getFields().length) {
            // for the case of zero-aggregations
            ISerializerDeserializer<?>[] fields = outRecordDescriptor.getFields();
            ITypeTrait[] types = outRecordDescriptor.getTypeTraits();
            ISerializerDeserializer<?>[] newFields = new ISerializerDeserializer[fields.length + 1];
            for (int i = 0; i < fields.length; i++)
                newFields[i] = fields[i];
            ITypeTrait[] newTypes = null;
            if (types != null) {
                newTypes = new ITypeTrait[types.length + 1];
                for (int i = 0; i < types.length; i++)
                    newTypes[i] = types[i];
            }
            internalRecordDescriptor = new RecordDescriptor(newFields, newTypes);
        }
        storedKeysAccessor1 = new FrameTupleAccessor(ctx.getFrameSize(), internalRecordDescriptor);
        storedKeysAccessor2 = new FrameTupleAccessor(ctx.getFrameSize(), internalRecordDescriptor);

        final IBinaryComparator[] comparators = new IBinaryComparator[comparatorFactories.length];
        for (int i = 0; i < comparatorFactories.length; ++i) {
            comparators[i] = comparatorFactories[i].createBinaryComparator();
        }

        final FrameTuplePairComparator ftpcPartial = new FrameTuplePairComparator(keyFields, storedKeys, comparators);
        final FrameTuplePairComparator ftpcTuple = new FrameTuplePairComparator(storedKeys, storedKeys, comparators);
        final FrameTupleAppender appender = new FrameTupleAppender(ctx.getFrameSize());
        final ITuplePartitionComputer tpc = tpcf.createPartitioner();
        final ByteBuffer outFrame = ctx.allocateFrame();

        final ArrayTupleBuilder internalTupleBuilder;
        if (keyFields.length < outRecordDescriptor.getFields().length)
            internalTupleBuilder = new ArrayTupleBuilder(outRecordDescriptor.getFields().length);
        else
            internalTupleBuilder = new ArrayTupleBuilder(outRecordDescriptor.getFields().length + 1);
        final ArrayTupleBuilder outputTupleBuilder = new ArrayTupleBuilder(outRecordDescriptor.getFields().length);
        final INormalizedKeyComputer nkc = firstKeyNormalizerFactory == null ? null : firstKeyNormalizerFactory
                .createNormalizedKeyComputer();

        return new ISpillableTable() {
            private int dataFrameCount;
            private final ISerializableTable table = new SerializableHashTable(tableSize, ctx);;
            private final TuplePointer storedTuplePointer = new TuplePointer();
            private final List<ByteBuffer> frames = new ArrayList<ByteBuffer>();
            private int groupSize = 0;
            private IAggregatorDescriptor aggregator = aggregateDescriptorFactory.createAggregator(ctx,
                    inRecordDescriptor, outRecordDescriptor, keyFields);

            /**
             * A tuple is "pointed" to by 3 entries in the tPointers array. [0]
             * = Frame index in the "Frames" list, [1] = Tuple index in the
             * frame, [2] = Poor man's normalized key for the tuple.
             */
            private int[] tPointers;

            @Override
            public void reset() {
                groupSize = 0;
                dataFrameCount = -1;
                tPointers = null;
                table.reset();
                aggregator.close();
            }

            @Override
            public boolean insert(FrameTupleAccessor accessor, int tIndex) throws HyracksDataException {
                if (dataFrameCount < 0)
                    nextAvailableFrame();
                int entry = tpc.partition(accessor, tIndex, tableSize);
                boolean foundGroup = false;
                int offset = 0;
                do {
                    table.getTuplePointer(entry, offset++, storedTuplePointer);
                    if (storedTuplePointer.frameIndex < 0)
                        break;
                    storedKeysAccessor1.reset(frames.get(storedTuplePointer.frameIndex));
                    int c = ftpcPartial.compare(accessor, tIndex, storedKeysAccessor1, storedTuplePointer.tupleIndex);
                    if (c == 0) {
                        foundGroup = true;
                        break;
                    }
                } while (true);

                if (!foundGroup) {
                    // If no matching group is found, create a new aggregator
                    // Create a tuple for the new group
                    internalTupleBuilder.reset();
                    for (int i = 0; i < keyFields.length; i++) {
                        internalTupleBuilder.addField(accessor, tIndex, keyFields[i]);
                    }
                    aggregator.init(accessor, tIndex, internalTupleBuilder);
                    if (!appender.append(internalTupleBuilder.getFieldEndOffsets(),
                            internalTupleBuilder.getByteArray(), 0, internalTupleBuilder.getSize())) {
                        if (!nextAvailableFrame()) {
                            return false;
                        } else {
                            if (!appender.append(internalTupleBuilder.getFieldEndOffsets(),
                                    internalTupleBuilder.getByteArray(), 0, internalTupleBuilder.getSize())) {
                                throw new IllegalStateException("Failed to init an aggregator");
                            }
                        }
                    }

                    storedTuplePointer.frameIndex = dataFrameCount;
                    storedTuplePointer.tupleIndex = appender.getTupleCount() - 1;
                    table.insert(entry, storedTuplePointer);
                    groupSize++;
                } else {
                    // If there is a matching found, do aggregation directly
                    int tupleOffset = storedKeysAccessor1.getTupleStartOffset(storedTuplePointer.tupleIndex);
                    int aggFieldOffset = storedKeysAccessor1.getFieldStartOffset(storedTuplePointer.tupleIndex,
                            keyFields.length);
                    int tupleLength = storedKeysAccessor1.getFieldLength(storedTuplePointer.tupleIndex,
                            keyFields.length);
                    aggregator.aggregate(accessor, tIndex, storedKeysAccessor1.getBuffer().array(), tupleOffset
                            + storedKeysAccessor1.getFieldSlotsLength() + aggFieldOffset, tupleLength);
                }
                return true;
            }

            @Override
            public List<ByteBuffer> getFrames() {
                return frames;
            }

            @Override
            public int getFrameCount() {
                return dataFrameCount;
            }

            @Override
            public void flushFrames(IFrameWriter writer, boolean isPartial) throws HyracksDataException {
                FrameTupleAppender appender = new FrameTupleAppender(ctx.getFrameSize());
                writer.open();
                appender.reset(outFrame, true);
                if (tPointers == null) {
                    // Not sorted
                    for (int i = 0; i < tableSize; ++i) {
                        int entry = i;
                        int offset = 0;
                        do {
                            table.getTuplePointer(entry, offset++, storedTuplePointer);
                            if (storedTuplePointer.frameIndex < 0)
                                break;
                            int bIndex = storedTuplePointer.frameIndex;
                            int tIndex = storedTuplePointer.tupleIndex;
                            storedKeysAccessor1.reset(frames.get(bIndex));
                            // Reset the tuple for the partial result
                            outputTupleBuilder.reset();
                            for (int k = 0; k < keyFields.length; k++) {
                                outputTupleBuilder.addField(storedKeysAccessor1, tIndex, k);
                            }
                            if (isPartial)
                                aggregator.outputPartialResult(storedKeysAccessor1, tIndex, outputTupleBuilder);
                            else
                                aggregator.outputResult(storedKeysAccessor1, tIndex, outputTupleBuilder);
                            while (!appender.append(outputTupleBuilder.getFieldEndOffsets(),
                                    outputTupleBuilder.getByteArray(), 0, outputTupleBuilder.getSize())) {
                                FrameUtils.flushFrame(outFrame, writer);
                                appender.reset(outFrame, true);
                            }
                        } while (true);
                    }
                    if (appender.getTupleCount() != 0) {
                        FrameUtils.flushFrame(outFrame, writer);
                    }
                    aggregator.close();
                    return;
                }
                int n = tPointers.length / 3;
                for (int ptr = 0; ptr < n; ptr++) {
                    int tableIndex = tPointers[ptr * 3];
                    int rowIndex = tPointers[ptr * 3 + 1];
                    table.getTuplePointer(tableIndex, rowIndex, storedTuplePointer);
                    int frameIndex = storedTuplePointer.frameIndex;
                    int tupleIndex = storedTuplePointer.tupleIndex;
                    // Get the frame containing the value
                    ByteBuffer buffer = frames.get(frameIndex);
                    storedKeysAccessor1.reset(buffer);

                    outputTupleBuilder.reset();
                    for (int k = 0; k < keyFields.length; k++) {
                        outputTupleBuilder.addField(storedKeysAccessor1, tupleIndex, k);
                    }
                    if (isPartial)
                        aggregator.outputPartialResult(storedKeysAccessor1, tupleIndex, outputTupleBuilder);
                    else
                        aggregator.outputResult(storedKeysAccessor1, tupleIndex, outputTupleBuilder);
                    if (!appender.append(outputTupleBuilder.getFieldEndOffsets(), outputTupleBuilder.getByteArray(), 0,
                            outputTupleBuilder.getSize())) {
                        FrameUtils.flushFrame(outFrame, writer);
                        appender.reset(outFrame, true);
                        if (!appender.append(outputTupleBuilder.getFieldEndOffsets(),
                                outputTupleBuilder.getByteArray(), 0, outputTupleBuilder.getSize())) {
                            throw new IllegalStateException();
                        }
                    }
                }
                if (appender.getTupleCount() > 0) {
                    FrameUtils.flushFrame(outFrame, writer);
                }
                aggregator.close();
            }

            /**
             * Set the working frame to the next available frame in the frame
             * list. There are two cases:<br>
             * 1) If the next frame is not initialized, allocate a new frame. 2)
             * When frames are already created, they are recycled.
             * 
             * @return Whether a new frame is added successfully.
             */
            private boolean nextAvailableFrame() {
                // Return false if the number of frames is equal to the limit.
                if (dataFrameCount + 1 >= framesLimit)
                    return false;

                if (frames.size() < framesLimit) {
                    // Insert a new frame
                    ByteBuffer frame = ctx.allocateFrame();
                    frame.position(0);
                    frame.limit(frame.capacity());
                    frames.add(frame);
                    appender.reset(frame, true);
                    dataFrameCount = frames.size() - 1;
                } else {
                    // Reuse an old frame
                    dataFrameCount++;
                    ByteBuffer frame = frames.get(dataFrameCount);
                    frame.position(0);
                    frame.limit(frame.capacity());
                    appender.reset(frame, true);
                }
                return true;
            }

            @Override
            public void sortFrames() {
                int sfIdx = storedKeys[0];
                int totalTCount = table.getTupleCount();
                tPointers = new int[totalTCount * 3];
                int ptr = 0;

                for (int i = 0; i < tableSize; i++) {
                    int entry = i;
                    int offset = 0;
                    do {
                        table.getTuplePointer(entry, offset, storedTuplePointer);
                        if (storedTuplePointer.frameIndex < 0)
                            break;
                        tPointers[ptr * 3] = entry;
                        tPointers[ptr * 3 + 1] = offset;
                        table.getTuplePointer(entry, offset, storedTuplePointer);
                        int fIndex = storedTuplePointer.frameIndex;
                        int tIndex = storedTuplePointer.tupleIndex;
                        storedKeysAccessor1.reset(frames.get(fIndex));
                        int tStart = storedKeysAccessor1.getTupleStartOffset(tIndex);
                        int f0StartRel = storedKeysAccessor1.getFieldStartOffset(tIndex, sfIdx);
                        int f0EndRel = storedKeysAccessor1.getFieldEndOffset(tIndex, sfIdx);
                        int f0Start = f0StartRel + tStart + storedKeysAccessor1.getFieldSlotsLength();
                        tPointers[ptr * 3 + 2] = nkc == null ? 0 : nkc.normalize(storedKeysAccessor1.getBuffer()
                                .array(), f0Start, f0EndRel - f0StartRel);
                        ptr++;
                        offset++;
                    } while (true);
                }
                // Sort using quick sort
                if (tPointers.length > 0) {
                    sort(tPointers, 0, totalTCount);
                }
            }

            private void sort(int[] tPointers, int offset, int length) {
                int m = offset + (length >> 1);
                // Get table index
                int mTable = tPointers[m * 3];
                int mRow = tPointers[m * 3 + 1];
                int mNormKey = tPointers[m * 3 + 2];
                // Get frame and tuple index
                table.getTuplePointer(mTable, mRow, storedTuplePointer);
                int mFrame = storedTuplePointer.frameIndex;
                int mTuple = storedTuplePointer.tupleIndex;
                storedKeysAccessor1.reset(frames.get(mFrame));

                int a = offset;
                int b = a;
                int c = offset + length - 1;
                int d = c;
                while (true) {
                    while (b <= c) {
                        int bTable = tPointers[b * 3];
                        int bRow = tPointers[b * 3 + 1];
                        int bNormKey = tPointers[b * 3 + 2];
                        int cmp = 0;
                        if (bNormKey != mNormKey) {
                            cmp = ((((long) bNormKey) & 0xffffffffL) < (((long) mNormKey) & 0xffffffffL)) ? -1 : 1;
                        } else {
                            table.getTuplePointer(bTable, bRow, storedTuplePointer);
                            int bFrame = storedTuplePointer.frameIndex;
                            int bTuple = storedTuplePointer.tupleIndex;
                            storedKeysAccessor2.reset(frames.get(bFrame));
                            cmp = ftpcTuple.compare(storedKeysAccessor2, bTuple, storedKeysAccessor1, mTuple);
                        }
                        if (cmp > 0) {
                            break;
                        }
                        if (cmp == 0) {
                            swap(tPointers, a++, b);
                        }
                        ++b;
                    }
                    while (c >= b) {
                        int cTable = tPointers[c * 3];
                        int cRow = tPointers[c * 3 + 1];
                        int cNormKey = tPointers[c * 3 + 2];
                        int cmp = 0;
                        if (cNormKey != mNormKey) {
                            cmp = ((((long) cNormKey) & 0xffffffffL) < (((long) mNormKey) & 0xffffffffL)) ? -1 : 1;
                        } else {
                            table.getTuplePointer(cTable, cRow, storedTuplePointer);
                            int cFrame = storedTuplePointer.frameIndex;
                            int cTuple = storedTuplePointer.tupleIndex;
                            storedKeysAccessor2.reset(frames.get(cFrame));
                            cmp = ftpcTuple.compare(storedKeysAccessor2, cTuple, storedKeysAccessor1, mTuple);
                        }
                        if (cmp < 0) {
                            break;
                        }
                        if (cmp == 0) {
                            swap(tPointers, c, d--);
                        }
                        --c;
                    }
                    if (b > c)
                        break;
                    swap(tPointers, b++, c--);
                }

                int s;
                int n = offset + length;
                s = Math.min(a - offset, b - a);
                vecswap(tPointers, offset, b - s, s);
                s = Math.min(d - c, n - d - 1);
                vecswap(tPointers, b, n - s, s);

                if ((s = b - a) > 1) {
                    sort(tPointers, offset, s);
                }
                if ((s = d - c) > 1) {
                    sort(tPointers, n - s, s);
                }
            }

            private void swap(int x[], int a, int b) {
                for (int i = 0; i < 3; ++i) {
                    int t = x[a * 3 + i];
                    x[a * 3 + i] = x[b * 3 + i];
                    x[b * 3 + i] = t;
                }
            }

            private void vecswap(int x[], int a, int b, int n) {
                for (int i = 0; i < n; i++, a++, b++) {
                    swap(x, a, b);
                }
            }

            @Override
            public void close() {
                groupSize = 0;
                dataFrameCount = -1;
                tPointers = null;
                table.close();
                frames.clear();
            }
        };
    }
}
