package edu.uci.ics.hyracks.storage.am.btree.dataflow;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeLeafFrame;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTreeRangeSearchCursor;
import edu.uci.ics.hyracks.storage.am.common.api.ITupleUpdater;
import edu.uci.ics.hyracks.storage.am.common.dataflow.AbstractTreeIndexOperatorDescriptor;

public class BTreeUpdateSearchOperatorNodePushable extends BTreeSearchOperatorNodePushable {
    private final ITupleUpdater tupleUpdater;
    
	public BTreeUpdateSearchOperatorNodePushable(
			AbstractTreeIndexOperatorDescriptor opDesc,
			IHyracksTaskContext ctx, int partition,
			IRecordDescriptorProvider recordDescProvider, boolean isForward,
			int[] lowKeyFields, int[] highKeyFields, boolean lowKeyInclusive,
			boolean highKeyInclusive, boolean retainInput, ITupleUpdater tupleUpdater) {
		super(opDesc, ctx, partition, recordDescProvider, isForward, lowKeyFields,
				highKeyFields, lowKeyInclusive, highKeyInclusive, retainInput);
		this.tupleUpdater = tupleUpdater;
	}

	@Override
	protected void setCursor() {
        cursor = new BTreeRangeSearchCursor((IBTreeLeafFrame) cursorFrame, true);
    }
	
	@Override
	protected void writeSearchResults() throws Exception {
        while (cursor.hasNext()) {
            tb.reset();
            cursor.next();
            ITupleReference tuple = cursor.getTuple();
            tupleUpdater.updateTuple(tuple);
            
            if (retainInput) {
            	for (int i = 0; i < tuple.getFieldCount(); i++) {
            		dos.write(tuple.getFieldData(i), tuple.getFieldStart(i), tuple.getFieldLength(i));
                    tb.addFieldEndOffset();
            	}
            }
            for (int i = 0; i < tuple.getFieldCount(); i++) {
                dos.write(tuple.getFieldData(i), tuple.getFieldStart(i), tuple.getFieldLength(i));
                tb.addFieldEndOffset();
            }
            if (!appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize())) {
                FrameUtils.flushFrame(writeBuffer, writer);
                appender.reset(writeBuffer, true);
                if (!appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize())) {
                    throw new IllegalStateException();
                }
            }
        }
    }
}
