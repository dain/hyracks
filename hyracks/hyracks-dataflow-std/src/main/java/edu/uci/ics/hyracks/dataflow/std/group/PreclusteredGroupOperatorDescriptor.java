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

import edu.uci.ics.hyracks.api.context.IHyracksContext;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.IComparator;
import edu.uci.ics.hyracks.api.dataflow.value.IComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.job.IOperatorEnvironment;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.util.DeserializedOperatorNodePushable;

public class PreclusteredGroupOperatorDescriptor extends AbstractSingleActivityOperatorDescriptor {
    private final int[] groupFields;
    private final IGroupAggregator aggregator;
    private final IComparatorFactory[] comparatorFactories;

    private static final long serialVersionUID = 1L;

    public PreclusteredGroupOperatorDescriptor(JobSpecification spec, int[] groupFields,
            IComparatorFactory[] comparatorFactories, IGroupAggregator aggregator, RecordDescriptor recordDescriptor) {
        super(spec, 1, 1);
        this.groupFields = groupFields;
        this.comparatorFactories = comparatorFactories;
        this.aggregator = aggregator;
        recordDescriptors[0] = recordDescriptor;
    }

    @Override
    public IOperatorNodePushable createPushRuntime(IHyracksContext ctx, IOperatorEnvironment env,
            IRecordDescriptorProvider recordDescProvider, int partition, int nPartitions) {
        IComparator[] comparators = new IComparator[comparatorFactories.length];
        for (int i = 0; i < comparatorFactories.length; ++i) {
            comparators[i] = comparatorFactories[i].createComparator();
        }
        return new DeserializedOperatorNodePushable(ctx, new PreclusteredGroupOperator(groupFields, comparators,
                aggregator), recordDescProvider.getInputRecordDescriptor(getOperatorId(), 0));
    }
}