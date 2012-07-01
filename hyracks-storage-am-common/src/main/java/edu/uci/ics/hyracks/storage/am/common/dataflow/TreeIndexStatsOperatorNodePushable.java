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
package edu.uci.ics.hyracks.storage.am.common.dataflow;

import java.io.DataOutput;
import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import edu.uci.ics.hyracks.dataflow.common.comm.util.FrameUtils;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryOutputSourceOperatorNodePushable;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.util.TreeIndexStats;
import edu.uci.ics.hyracks.storage.am.common.util.TreeIndexStatsGatherer;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;

public class TreeIndexStatsOperatorNodePushable extends AbstractUnaryOutputSourceOperatorNodePushable {
    private final TreeIndexDataflowHelper treeIndexHelper;
    private final IHyracksTaskContext ctx;
    private TreeIndexStatsGatherer statsGatherer;

    public TreeIndexStatsOperatorNodePushable(AbstractTreeIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx,
            int partition) {
        treeIndexHelper = (TreeIndexDataflowHelper) opDesc.getIndexDataflowHelperFactory().createIndexDataflowHelper(
                opDesc, ctx, partition);
        this.ctx = ctx;
    }

    @Override
    public void deinitialize() throws HyracksDataException {
    }

    @Override
    public IFrameWriter getInputFrameWriter(int index) {
        return null;
    }

    @Override
    public void initialize() throws HyracksDataException {
        try {
            writer.open();
            treeIndexHelper.init(false);
            ITreeIndex treeIndex = (ITreeIndex) treeIndexHelper.getIndex();
            IBufferCache bufferCache = treeIndexHelper.getOperatorDescriptor().getStorageManager().getBufferCache(ctx);
            statsGatherer = new TreeIndexStatsGatherer(bufferCache, treeIndex.getFreePageManager(),
                    treeIndexHelper.getIndexFileId(), treeIndex.getRootPageId());
            TreeIndexStats stats = statsGatherer.gatherStats(treeIndex.getLeafFrameFactory().createFrame(), treeIndex
                    .getInteriorFrameFactory().createFrame(), treeIndex.getFreePageManager().getMetaDataFrameFactory()
                    .createFrame());
            // Write the stats output as a single string field.
            ByteBuffer frame = ctx.allocateFrame();
            FrameTupleAppender appender = new FrameTupleAppender(ctx.getFrameSize());
            appender.reset(frame, true);
            ArrayTupleBuilder tb = new ArrayTupleBuilder(1);
            DataOutput dos = tb.getDataOutput();
            tb.reset();
            UTF8StringSerializerDeserializer.INSTANCE.serialize(stats.toString(), dos);
            tb.addFieldEndOffset();
            if (!appender.append(tb.getFieldEndOffsets(), tb.getByteArray(), 0, tb.getSize())) {
                throw new IllegalStateException();
            }
            FrameUtils.flushFrame(frame, writer);
        } catch (Exception e) {
            try {
                treeIndexHelper.deinit();
            } finally {
                writer.fail();
            }
        } finally {
            writer.close();
        }
    }
}