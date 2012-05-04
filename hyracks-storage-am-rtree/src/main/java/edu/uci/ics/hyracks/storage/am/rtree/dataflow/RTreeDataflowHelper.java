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
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.common.api.IOperationCallbackProvider;
import edu.uci.ics.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.common.dataflow.TreeIndexDataflowHelper;
import edu.uci.ics.hyracks.storage.am.rtree.util.RTreeUtils;

public class RTreeDataflowHelper extends TreeIndexDataflowHelper {

    private final IPrimitiveValueProviderFactory[] valueProviderFactories;

    public RTreeDataflowHelper(IIndexOperatorDescriptor opDesc, IHyracksTaskContext ctx,
            IOperationCallbackProvider opCallbackProvider, int partition, boolean createIfNotExists,
            IPrimitiveValueProviderFactory[] valueProviderFactories) {
        super(opDesc, ctx, opCallbackProvider, partition, createIfNotExists);
        this.valueProviderFactories = valueProviderFactories;
    }

    @Override
    public ITreeIndex createIndexInstance() throws HyracksDataException {
        return RTreeUtils.createRTree(treeOpDesc.getStorageManager().getBufferCache(ctx),
                treeOpDesc.getTreeIndexTypeTraits(), valueProviderFactories,
                treeOpDesc.getTreeIndexComparatorFactories());
    }
}
