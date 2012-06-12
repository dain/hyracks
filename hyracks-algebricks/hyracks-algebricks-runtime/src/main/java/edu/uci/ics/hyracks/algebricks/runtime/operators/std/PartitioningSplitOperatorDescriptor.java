package edu.uci.ics.hyracks.algebricks.runtime.operators.std;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.algebricks.common.exceptions.AlgebricksException;
import edu.uci.ics.hyracks.algebricks.data.IBinaryBooleanInspector;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluator;
import edu.uci.ics.hyracks.algebricks.runtime.base.IEvaluatorFactory;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.job.IOperatorDescriptorRegistry;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ArrayBackedValueStorage;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.FrameTupleReference;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryInputOperatorNodePushable;

public class PartitioningSplitOperatorDescriptor extends AbstractSingleActivityOperatorDescriptor {
    private static final long serialVersionUID = 1L;

    private final IEvaluatorFactory[] evalFactories;
    private final IBinaryBooleanInspector boolInspector;
    private final boolean hasDefaultBranch;
    
    public PartitioningSplitOperatorDescriptor(IOperatorDescriptorRegistry spec, IEvaluatorFactory[] evalFactories, IBinaryBooleanInspector boolInspector, boolean hasDefaultBranch, RecordDescriptor rDesc) {        
    	super(spec, 1, evalFactories.length);
        for (int i = 0; i < evalFactories.length; i++) {
            recordDescriptors[i] = rDesc;
        }
        this.evalFactories = evalFactories;
        this.boolInspector = boolInspector;
        this.hasDefaultBranch = hasDefaultBranch;
    }

    @Override
    public IOperatorNodePushable createPushRuntime(final IHyracksTaskContext ctx,
            final IRecordDescriptorProvider recordDescProvider, int partition, int nPartitions)
            throws HyracksDataException {
        return new AbstractUnaryInputOperatorNodePushable() {
            private final IFrameWriter[] writers = new IFrameWriter[outputArity];
            private final ByteBuffer[] writeBuffers = new ByteBuffer[outputArity];
            private final IEvaluator[] evals = new IEvaluator[outputArity];
            private final ArrayBackedValueStorage evalBuf = new ArrayBackedValueStorage();
            private final RecordDescriptor inOutRecDesc = recordDescProvider.getInputRecordDescriptor(getOperatorId(), 0);
            private final FrameTupleAccessor accessor = new FrameTupleAccessor(ctx.getFrameSize(), inOutRecDesc);
            private final FrameTupleReference frameTuple = new FrameTupleReference();
            
            private final FrameTupleAppender tupleAppender = new FrameTupleAppender(ctx.getFrameSize());
            private final ArrayTupleBuilder tupleBuilder = new ArrayTupleBuilder(inOutRecDesc.getFieldCount());
            private final DataOutput tupleDos = tupleBuilder.getDataOutput();
            
            @Override
            public void close() throws HyracksDataException {
                // Flush (possibly not full) buffers that have data, and close writers.
                for (int i = 0; i < outputArity; i++) {
                    tupleAppender.reset(writeBuffers[i], false);
                    if (tupleAppender.getTupleCount() > 0) {
                        FrameUtils.flushFrame(writeBuffers[i], writers[i]);
                    }
                    writers[i].close();
                }
            }

            @Override
            public void fail() throws HyracksDataException {
                for (IFrameWriter writer : writers) {
                    writer.fail();
                }
            }

            @Override
            public void nextFrame(ByteBuffer buffer) throws HyracksDataException {
                accessor.reset(buffer);
                int tupleCount = accessor.getTupleCount();
                for (int i = 0; i < tupleCount; i++) {
                    frameTuple.reset(accessor, i);
                    boolean found = false;
                    for (int j = 0; j < evals.length; j++) {
                        try {
                            evals[j].evaluate(frameTuple);
                        } catch (AlgebricksException e) {
                            throw new HyracksDataException(e);
                        }
                        found = boolInspector.getBooleanValue(evalBuf.getBytes(), 0, 1);
                        if (found) {
                            // Copy tuple into tuple builder.
                            try {
                                for (int k = 0; k < frameTuple.getFieldCount(); k++) {
                                    tupleDos.write(frameTuple.getFieldData(k), frameTuple.getFieldStart(k),
                                            frameTuple.getFieldLength(k));
                                    tupleBuilder.addFieldEndOffset();
                                }
                            } catch (IOException e) {
                                throw new HyracksDataException(e);
                            }
                            // Append to frame.
                            tupleAppender.reset(writeBuffers[j], false);
                            if (!tupleAppender.append(tupleBuilder.getFieldEndOffsets(), tupleBuilder.getByteArray(), 0, tupleBuilder.getSize())) {
                                FrameUtils.flushFrame(writeBuffers[j], writers[j]);
                                tupleAppender.reset(writeBuffers[j], true);
                                if (!tupleAppender.append(tupleBuilder.getFieldEndOffsets(), tupleBuilder.getByteArray(), 0, tupleBuilder.getSize())) {
                                    throw new IllegalStateException();
                                }
                            }
                        }
                    }
                    // Optionally write to default partition.
                    if (!found && hasDefaultBranch) {
                        tupleAppender.reset(writeBuffers[0], true);
                    }
                }
            }

            @Override
            public void open() throws HyracksDataException {
                for (IFrameWriter writer : writers) {
                    writer.open();
                }
                // Create write buffers.
                for (int i = 0; i < outputArity; i++) {
                    writeBuffers[i] = ctx.allocateFrame();
                    // Make sure to clear all buffers.
                    tupleAppender.reset(writeBuffers[i], true);
                }
                // Create evaluators for partitioning.
				try {
					for (int i = 0; i < evalFactories.length; i++) {
						evals[i] = evalFactories[i].createEvaluator(evalBuf);
					}
				} catch (AlgebricksException e) {
					throw new HyracksDataException(e);
				}
				
				
            }

            @Override
            public void setOutputFrameWriter(int index, IFrameWriter writer, RecordDescriptor recordDesc) {
                writers[index] = writer;
            }
        };
    }
}

