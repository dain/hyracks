package edu.uci.ics.hyracks.storage.am.btree.util;

import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.storage.am.btree.exceptions.BTreeException;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeFieldPrefixNSMLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeLeafFrameType;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeNSMInteriorFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.frames.BTreeNSMLeafFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.impls.BTree;
import edu.uci.ics.hyracks.storage.am.common.api.IFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndexTupleWriterFactory;
import edu.uci.ics.hyracks.storage.am.common.frames.LIFOMetaDataFrameFactory;
import edu.uci.ics.hyracks.storage.am.common.freepage.LinkedListFreePageManager;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;
import edu.uci.ics.hyracks.storage.am.common.tuples.TypeAwareTupleWriterFactory;
import edu.uci.ics.hyracks.storage.common.buffercache.IBufferCache;

public class BTreeUtils {
    public static BTree createBTree(IBufferCache bufferCache, int btreeFileId, ITypeTraits[] typeTraits, IBinaryComparator[] cmps, BTreeLeafFrameType leafType) throws BTreeException {
    	MultiComparator cmp = new MultiComparator(cmps);
        TypeAwareTupleWriterFactory tupleWriterFactory = new TypeAwareTupleWriterFactory(typeTraits);
        ITreeIndexFrameFactory leafFrameFactory = getLeafFrameFactory(tupleWriterFactory, leafType);
        ITreeIndexFrameFactory interiorFrameFactory = new BTreeNSMInteriorFrameFactory(tupleWriterFactory);
        ITreeIndexMetaDataFrameFactory metaFrameFactory = new LIFOMetaDataFrameFactory();
        IFreePageManager freePageManager = new LinkedListFreePageManager(bufferCache, btreeFileId, 0, metaFrameFactory);
        BTree btree = new BTree(bufferCache, typeTraits.length, cmp, freePageManager, interiorFrameFactory, leafFrameFactory);
        return btree;
    }
    
    public static MultiComparator getSearchMultiComparator(MultiComparator btreeCmp, ITupleReference searchKey) {
        if (searchKey == null) {
        	return btreeCmp;
        }
    	if (btreeCmp.getKeyFieldCount() == searchKey.getFieldCount()) {
            return btreeCmp;
        }
        IBinaryComparator[] cmps = new IBinaryComparator[searchKey.getFieldCount()];
        for (int i = 0; i < searchKey.getFieldCount(); i++) {
            cmps[i] = btreeCmp.getComparators()[i];
        }
        return new MultiComparator(cmps);
    }
    
    public static ITreeIndexFrameFactory getLeafFrameFactory(ITreeIndexTupleWriterFactory tupleWriterFactory, BTreeLeafFrameType leafType) throws BTreeException {
        switch(leafType) {
            case REGULAR_NSM: {
                return new BTreeNSMLeafFrameFactory(tupleWriterFactory);                
            }
            case FIELD_PREFIX_COMPRESSED_NSM: {
                return new BTreeFieldPrefixNSMLeafFrameFactory(tupleWriterFactory);
            }
            default: {
                throw new BTreeException("Unknown BTreeLeafFrameType: " + leafType.toString());
            }
        }
    }
}
