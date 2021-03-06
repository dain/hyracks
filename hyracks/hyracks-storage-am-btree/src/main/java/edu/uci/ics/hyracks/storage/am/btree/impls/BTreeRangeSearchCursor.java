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

package edu.uci.ics.hyracks.storage.am.btree.impls;

import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeLeafFrame;
import edu.uci.ics.hyracks.storage.am.common.api.ICursorInitialState;
import edu.uci.ics.hyracks.storage.am.common.api.ISearchPredicate;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexCursor;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexTupleReference;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.FindTupleMode;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.FindTupleNoExactMatchPolicy;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;
import edu.uci.ics.hyracks.storage.common.buffercache.ICachedPage;
import edu.uci.ics.hyracks.storage.common.file.BufferedFileHandle;

public class BTreeRangeSearchCursor implements ITreeIndexCursor {

    private int fileId = -1;
    private ICachedPage page = null;
    private IBufferCache bufferCache = null;

    private int tupleIndex = 0;
    private int stopTupleIndex;
    private int tupleIndexInc = 0;

    private FindTupleMode lowKeyFtm;
    private FindTupleMode highKeyFtm;

    private FindTupleNoExactMatchPolicy lowKeyFtp;
    private FindTupleNoExactMatchPolicy highKeyFtp;

    private final IBTreeLeafFrame frame;
    private final ITreeIndexTupleReference frameTuple;
    private final boolean exclusiveLatchNodes;

    private RangePredicate pred;
    private MultiComparator lowKeyCmp;
    private MultiComparator highKeyCmp;
    private ITupleReference lowKey;
    private ITupleReference highKey;

    public BTreeRangeSearchCursor(IBTreeLeafFrame frame, boolean exclusiveLatchNodes) {
        this.frame = frame;
        this.frameTuple = frame.createTupleReference();
        this.exclusiveLatchNodes = exclusiveLatchNodes;
    }

    @Override
    public void close() throws Exception {
        if (page != null) {
            if (exclusiveLatchNodes) {
                page.releaseWriteLatch();
            } else {
                page.releaseReadLatch();
            }
            bufferCache.unpin(page);
        }
        tupleIndex = 0;
        page = null;
        pred = null;
    }

    public ITupleReference getTuple() {
        return frameTuple;
    }

    @Override
    public ICachedPage getPage() {
        return page;
    }

    private void fetchNextLeafPage(int nextLeafPage) throws HyracksDataException {
        ICachedPage nextLeaf = bufferCache.pin(BufferedFileHandle.getDiskPageId(fileId, nextLeafPage), false);
        if (exclusiveLatchNodes) {
            nextLeaf.acquireWriteLatch();
            page.releaseWriteLatch();
        } else {
            nextLeaf.acquireReadLatch();
            page.releaseReadLatch();
        }
        bufferCache.unpin(page);
        page = nextLeaf;
        frame.setPage(page);
    }

    @Override
    public boolean hasNext() throws Exception {
        if (pred.isForward()) {
            if (tupleIndex >= frame.getTupleCount()) {
                int nextLeafPage = frame.getNextLeaf();
                if (nextLeafPage >= 0) {
                    fetchNextLeafPage(nextLeafPage);
                    tupleIndex = 0;

                    stopTupleIndex = getHighKeyIndex();
                    if (stopTupleIndex < 0)
                        return false;
                } else {
                    return false;
                }
            }

            frameTuple.resetByTupleIndex(frame, tupleIndex);
            if (highKey == null || tupleIndex <= stopTupleIndex) {
                return true;
            } else
                return false;
        } else {
            if (tupleIndex < 0) {
                int nextLeafPage = frame.getPrevLeaf();
                if (nextLeafPage >= 0) {
                    fetchNextLeafPage(nextLeafPage);
                    tupleIndex = frame.getTupleCount() - 1;

                    stopTupleIndex = getLowKeyIndex();
                    if (stopTupleIndex >= frame.getTupleCount())
                        return false;
                } else {
                    return false;
                }
            }

            frameTuple.resetByTupleIndex(frame, tupleIndex);
            if (lowKey == null || tupleIndex >= stopTupleIndex) {
                return true;
            } else
                return false;
        }
    }

    @Override
    public void next() throws Exception {
        tupleIndex += tupleIndexInc;
    }

    private int getLowKeyIndex() throws HyracksDataException {
        if (lowKey == null) {
            return 0;
        }
        int index = frame.findTupleIndex(lowKey, frameTuple, lowKeyCmp, lowKeyFtm, lowKeyFtp);
        if (pred.lowKeyInclusive) {
            index++;
        } else {
            if (index < 0) {
                index = frame.getTupleCount();
            }
        }
        return index;
    }

    private int getHighKeyIndex() throws HyracksDataException {
        if (highKey == null) {
            return frame.getTupleCount() - 1;
        }
        int index = frame.findTupleIndex(highKey, frameTuple, highKeyCmp, highKeyFtm, highKeyFtp);
        if (pred.highKeyInclusive) {
            if (index < 0) {
                index = frame.getTupleCount() - 1;
            }
            else {
                index--;
            }
        }
        return index;
    }

    @Override
    public void open(ICursorInitialState initialState, ISearchPredicate searchPred) throws HyracksDataException {
        // in case open is called multiple times without closing
        if (page != null) {
            if (exclusiveLatchNodes) {
                page.releaseWriteLatch();
            } else {
                page.releaseReadLatch();
            }
            bufferCache.unpin(page);
        }

        page = ((BTreeCursorInitialState) initialState).getPage();
        frame.setPage(page);

        pred = (RangePredicate) searchPred;
        lowKeyCmp = pred.getLowKeyComparator();
        highKeyCmp = pred.getHighKeyComparator();

        lowKey = pred.getLowKey();
        highKey = pred.getHighKey();

        // init
        lowKeyFtm = FindTupleMode.EXCLUSIVE;
        if (pred.lowKeyInclusive) {
            lowKeyFtp = FindTupleNoExactMatchPolicy.LOWER_KEY;
        } else {
            lowKeyFtp = FindTupleNoExactMatchPolicy.HIGHER_KEY;
        }

        highKeyFtm = FindTupleMode.EXCLUSIVE;
        if (pred.highKeyInclusive) {
            highKeyFtp = FindTupleNoExactMatchPolicy.HIGHER_KEY;
        } else {
            highKeyFtp = FindTupleNoExactMatchPolicy.LOWER_KEY;
        }

        if (pred.isForward()) {
            tupleIndex = getLowKeyIndex();
            stopTupleIndex = getHighKeyIndex();
            tupleIndexInc = 1;
        } else {
            tupleIndex = getHighKeyIndex();
            stopTupleIndex = getLowKeyIndex();
            tupleIndexInc = -1;
        }
    }

    @Override
    public void reset() {
        try {
            close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setBufferCache(IBufferCache bufferCache) {
        this.bufferCache = bufferCache;
    }

    @Override
    public void setFileId(int fileId) {
        this.fileId = fileId;
    }

    @Override
    public boolean exclusiveLatchNodes() {
        return exclusiveLatchNodes;
    }
}