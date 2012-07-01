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

package edu.uci.ics.hyracks.storage.am.rtree.dataflow;

import edu.uci.ics.hyracks.api.context.IHyracksTaskContext;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.storage.am.common.api.IOperationCallbackProvider;
import edu.uci.ics.hyracks.storage.am.common.api.ISearchPredicate;
import edu.uci.ics.hyracks.storage.am.common.dataflow.AbstractTreeIndexOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.common.dataflow.PermutingFrameTupleReference;
import edu.uci.ics.hyracks.storage.am.common.dataflow.TreeIndexSearchOperatorNodePushable;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;
import edu.uci.ics.hyracks.storage.am.rtree.impls.SearchPredicate;
import edu.uci.ics.hyracks.storage.am.rtree.util.RTreeUtils;

public class RTreeSearchOperatorNodePushable extends TreeIndexSearchOperatorNodePushable {
    protected PermutingFrameTupleReference searchKey;
    protected MultiComparator cmp;

    public RTreeSearchOperatorNodePushable(AbstractTreeIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx,
            IOperationCallbackProvider opCallbackProvider, int partition, IRecordDescriptorProvider recordDescProvider,
            int[] keyFields) {
        super(opDesc, ctx, partition, recordDescProvider);
        if (keyFields != null && keyFields.length > 0) {
            searchKey = new PermutingFrameTupleReference();
            searchKey.setFieldPermutation(keyFields);
        }
    }

    @Override
    protected ISearchPredicate createSearchPredicate() {
        cmp = RTreeUtils.getSearchMultiComparator(treeIndex.getComparatorFactories(), searchKey);
        return new SearchPredicate(searchKey, cmp);
    }

    @Override
    protected void resetSearchPredicate(int tupleIndex) {
        if (searchKey != null) {
            searchKey.reset(accessor, tupleIndex);
        }
    }
}