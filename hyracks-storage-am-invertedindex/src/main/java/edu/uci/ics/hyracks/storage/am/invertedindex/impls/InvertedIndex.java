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

package edu.uci.ics.hyracks.storage.am.invertedindex.impls;

import java.nio.ByteBuffer;

import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTrait;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ByteArrayAccessibleOutputStream;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAccessor;
import edu.uci.ics.hyracks.dataflow.common.comm.io.FrameTupleAppender;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.FrameTupleReference;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.IntegerSerializerDeserializer;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.btree.impls.RangePredicate;
import edu.uci.ics.hyracks.storage.am.common.api.IIndexBulkLoadContext;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexAccessor;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexCursor;
import edu.uci.ics.hyracks.storage.am.common.api.PageAllocationException;
import edu.uci.ics.hyracks.storage.am.common.api.TreeIndexException;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndex;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;
import edu.uci.ics.hyracks.storage.am.invertedindex.api.IInvertedListBuilder;
import edu.uci.ics.hyracks.storage.am.invertedindex.api.IInvertedListCursor;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.buffercache.ICachedPage;
import edu.uci.ics.hyracks.storage.common.file.BufferedFileHandle;

/**
 * An inverted index consists of two files: 1. a file storing (paginated)
 * inverted lists 2. a BTree-file mapping from tokens to inverted lists
 * 
 * Implemented features: bulk loading and searching (based on T-Occurrence) Not
 * implemented features: updates (insert/update/delete) Limitations: a query
 * cannot exceed the size of a Hyracks frame
 */
public class InvertedIndex implements IIndex {

    private BTree btree;
    private int rootPageId = 0;
    private IBufferCache bufferCache;
    private int fileId;
    private final ITypeTrait[] invListTypeTraits;
    private final MultiComparator invListCmp;
    private final int numTokenFields;
    private final int numInvListKeys;

    public InvertedIndex(IBufferCache bufferCache, BTree btree, ITypeTrait[] invListTypeTraits, MultiComparator invListCmp) {
        this.bufferCache = bufferCache;
        this.btree = btree;
        this.invListCmp = invListCmp;
        this.invListTypeTraits = invListTypeTraits;
        this.numTokenFields = btree.getMultiComparator().getKeyFieldCount();
        this.numInvListKeys = invListCmp.getKeyFieldCount();
    }

    @Override
    public void open(int fileId) {
        this.fileId = fileId;
    }

    @Override
    public void create(int indexFileId) throws HyracksDataException {
    }
    
    public void close() {
        this.fileId = -1;
    }

    public BulkLoadContext beginBulkLoad(IInvertedListBuilder invListBuilder, int hyracksFrameSize,
            float btreeFillFactor) throws HyracksDataException, TreeIndexException, PageAllocationException {
        BulkLoadContext ctx = new BulkLoadContext(invListBuilder, hyracksFrameSize, btreeFillFactor);
        ctx.init(rootPageId, fileId);
        return ctx;
    }

    // ASSUMPTIONS:
    // the first btree.getMultiComparator().getKeyFieldCount() fields in tuple
    // are btree keys (e.g., a string token)
    // the next invListCmp.getKeyFieldCount() fields in tuple are keys of the
    // inverted list (e.g., primary key)
    // key fields of inverted list are fixed size
    public void bulkLoadAddTuple(BulkLoadContext ctx, ITupleReference tuple) throws HyracksDataException, PageAllocationException {

        // first inverted list, copy token to baaos and start new list
        if (ctx.currentInvListTokenBaaos.size() == 0) {
            ctx.currentInvListStartPageId = ctx.currentPageId;
            ctx.currentInvListStartOffset = ctx.invListBuilder.getPos();

            // remember current token
            ctx.currentInvListTokenBaaos.reset();
            for (int i = 0; i < numTokenFields; i++) {
                ctx.currentInvListTokenBaaos.write(tuple.getFieldData(i), tuple.getFieldStart(i),
                        tuple.getFieldLength(i));
            }

            if (!ctx.invListBuilder.startNewList(tuple, numTokenFields)) {
                ctx.pinNextPage();
                ctx.invListBuilder.setTargetBuffer(ctx.currentPage.getBuffer().array(), 0);
                if (!ctx.invListBuilder.startNewList(tuple, numTokenFields)) {
                    throw new IllegalStateException("Failed to create first inverted list.");
                }
            }
        }

        // create new inverted list?
        ctx.currentInvListToken.reset(ctx.currentInvListTokenBaaos.getByteArray(), 0);
        if (ctx.tokenCmp.compare(tuple, ctx.currentInvListToken) != 0) {

            // create entry in btree for last inverted list
            createAndInsertBTreeTuple(ctx);

            // remember new token
            ctx.currentInvListTokenBaaos.reset();
            for (int i = 0; i < numTokenFields; i++) {
                ctx.currentInvListTokenBaaos.write(tuple.getFieldData(i), tuple.getFieldStart(i),
                        tuple.getFieldLength(i));
            }

            // start new list
            if (!ctx.invListBuilder.startNewList(tuple, numTokenFields)) {
                ctx.pinNextPage();
                ctx.invListBuilder.setTargetBuffer(ctx.currentPage.getBuffer().array(), 0);
                if (!ctx.invListBuilder.startNewList(tuple, numTokenFields)) {
                    throw new IllegalStateException("Failed to start new inverted list after switching to a new page.");
                }
            }

            ctx.currentInvListStartPageId = ctx.currentPageId;
            ctx.currentInvListStartOffset = ctx.invListBuilder.getPos();
        }

        // append to current inverted list
        if (!ctx.invListBuilder.appendElement(tuple, numTokenFields, numInvListKeys)) {
            ctx.pinNextPage();
            ctx.invListBuilder.setTargetBuffer(ctx.currentPage.getBuffer().array(), 0);
            if (!ctx.invListBuilder.appendElement(tuple, numTokenFields, numInvListKeys)) {
                throw new IllegalStateException(
                        "Failed to append element to inverted list after switching to a new page.");
            }
        }
    }

    public boolean openCursor(ITreeIndexCursor btreeCursor, RangePredicate btreePred, ITreeIndexAccessor btreeAccessor,
            IInvertedListCursor invListCursor) throws Exception {
    	btreeAccessor.search(btreeCursor, btreePred);

        boolean ret = false;
        if (btreeCursor.hasNext()) {

            btreeCursor.next();
            ITupleReference frameTuple = btreeCursor.getTuple();

            // hardcoded mapping of btree fields
            int startPageId = IntegerSerializerDeserializer.getInt(frameTuple.getFieldData(1),
                    frameTuple.getFieldStart(1));
            int endPageId = IntegerSerializerDeserializer.getInt(frameTuple.getFieldData(2),
                    frameTuple.getFieldStart(2));
            int startOff = IntegerSerializerDeserializer
                    .getInt(frameTuple.getFieldData(3), frameTuple.getFieldStart(3));
            int numElements = IntegerSerializerDeserializer.getInt(frameTuple.getFieldData(4),
                    frameTuple.getFieldStart(4));

            invListCursor.reset(startPageId, endPageId, startOff, numElements);
            ret = true;
        } else {
            invListCursor.reset(0, 0, 0, 0);
        }

        btreeCursor.close();
        btreeCursor.reset();

        return ret;
    }

    public void createAndInsertBTreeTuple(BulkLoadContext ctx) throws HyracksDataException, PageAllocationException {
        // build tuple
        ctx.btreeTupleBuilder.reset();
        ctx.btreeTupleBuilder.addField(ctx.currentInvListTokenBaaos.getByteArray(), 0,
                ctx.currentInvListTokenBaaos.size());
        ctx.btreeTupleBuilder.addField(IntegerSerializerDeserializer.INSTANCE, ctx.currentInvListStartPageId);
        ctx.btreeTupleBuilder.addField(IntegerSerializerDeserializer.INSTANCE, ctx.currentPageId);
        ctx.btreeTupleBuilder.addField(IntegerSerializerDeserializer.INSTANCE, ctx.currentInvListStartOffset);
        ctx.btreeTupleBuilder.addField(IntegerSerializerDeserializer.INSTANCE, ctx.invListBuilder.getListSize());

        // append to buffer
        ctx.btreeTupleAppender.reset(ctx.btreeTupleBuffer, true);
        ctx.btreeTupleAppender.append(ctx.btreeTupleBuilder.getFieldEndOffsets(), ctx.btreeTupleBuilder.getByteArray(),
                0, ctx.btreeTupleBuilder.getSize());

        // reset tuple reference
        ctx.btreeFrameTupleReference.reset(ctx.btreeFrameTupleAccessor, 0);

        btree.bulkLoadAddTuple(ctx.btreeFrameTupleReference, ctx.btreeBulkLoadCtx);
    }

    public void endBulkLoad(BulkLoadContext ctx) throws HyracksDataException, PageAllocationException {
        // create entry in btree for last inverted list
        createAndInsertBTreeTuple(ctx);
        btree.endBulkLoad(ctx.btreeBulkLoadCtx);
        ctx.deinit();
    }

    public IBufferCache getBufferCache() {
        return bufferCache;
    }

    public int getInvListsFileId() {
        return fileId;
    }

    public MultiComparator getInvListElementCmp() {
        return invListCmp;
    }
    
    public ITypeTrait[] getTypeTraits() {
        return invListTypeTraits;
    }

    public BTree getBTree() {
        return btree;
    }

    public final class BulkLoadContext {
        private final ByteBuffer btreeTupleBuffer;
        private final ArrayTupleBuilder btreeTupleBuilder;
        private final FrameTupleAppender btreeTupleAppender;
        private final FrameTupleAccessor btreeFrameTupleAccessor;
        private final FrameTupleReference btreeFrameTupleReference = new FrameTupleReference();
        private final float btreeFillFactor;
        private IIndexBulkLoadContext btreeBulkLoadCtx;

        private int currentInvListStartPageId;
        private int currentInvListStartOffset;
        private final ByteArrayAccessibleOutputStream currentInvListTokenBaaos = new ByteArrayAccessibleOutputStream();
        private final FixedSizeTupleReference currentInvListToken = new FixedSizeTupleReference(invListTypeTraits);

        private int currentPageId;
        private ICachedPage currentPage;
        private final IInvertedListBuilder invListBuilder;
        private final MultiComparator tokenCmp;

        public BulkLoadContext(IInvertedListBuilder invListBuilder, int hyracksFrameSize, float btreeFillFactor) {
            this.invListBuilder = invListBuilder;
            this.tokenCmp = btree.getMultiComparator();
            this.btreeTupleBuffer = ByteBuffer.allocate(hyracksFrameSize);
            this.btreeTupleBuilder = new ArrayTupleBuilder(btree.getFieldCount());
            this.btreeTupleAppender = new FrameTupleAppender(hyracksFrameSize);
            // TODO: serde never used, only need correct number of fields
            // tuple contains (token, start page, end page, start offset, num
            // elements)
            RecordDescriptor recDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                    IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                    IntegerSerializerDeserializer.INSTANCE, IntegerSerializerDeserializer.INSTANCE,
                    IntegerSerializerDeserializer.INSTANCE });
            this.btreeFrameTupleAccessor = new FrameTupleAccessor(hyracksFrameSize, recDesc);
            this.btreeFillFactor = btreeFillFactor;
        }

        public void init(int startPageId, int fileId) throws HyracksDataException, TreeIndexException, PageAllocationException {
            btreeBulkLoadCtx = btree.beginBulkLoad(BTree.DEFAULT_FILL_FACTOR);
            currentPageId = startPageId;
            currentPage = bufferCache.pin(BufferedFileHandle.getDiskPageId(fileId, currentPageId), true);
            currentPage.acquireWriteLatch();
            invListBuilder.setTargetBuffer(currentPage.getBuffer().array(), 0);
            btreeFrameTupleAccessor.reset(btreeTupleBuffer);
        }

        public void deinit() throws HyracksDataException {
            if (currentPage != null) {
                currentPage.releaseWriteLatch();
                bufferCache.unpin(currentPage);
            }
        }

        public void pinNextPage() throws HyracksDataException {
            currentPage.releaseWriteLatch();
            bufferCache.unpin(currentPage);
            currentPageId++;
            currentPage = bufferCache.pin(BufferedFileHandle.getDiskPageId(fileId, currentPageId), true);
            currentPage.acquireWriteLatch();
        }
    }
}
