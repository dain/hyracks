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
package edu.uci.ics.hyracks.coreops;

import edu.uci.ics.hyracks.api.comm.IConnectionDemultiplexer;
import edu.uci.ics.hyracks.api.comm.IFrameReader;
import edu.uci.ics.hyracks.api.comm.IFrameWriter;
import edu.uci.ics.hyracks.api.dataflow.IEndpointDataWriterFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ITuplePartitionComputerFactory;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.job.JobPlan;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.comm.NonDeterministicFrameReader;
import edu.uci.ics.hyracks.context.HyracksContext;
import edu.uci.ics.hyracks.coreops.base.AbstractConnectorDescriptor;

public class MToNHashPartitioningConnectorDescriptor extends AbstractConnectorDescriptor {
    private static final long serialVersionUID = 1L;
    private ITuplePartitionComputerFactory tpcf;

    public MToNHashPartitioningConnectorDescriptor(JobSpecification spec, ITuplePartitionComputerFactory tpcf) {
        super(spec);
        this.tpcf = tpcf;
    }

    @Override
    public IFrameWriter createSendSideWriter(HyracksContext ctx, JobPlan plan, IEndpointDataWriterFactory edwFactory,
        int index, int nProducerPartitions, int nConsumerPartitions) throws HyracksDataException {
        JobSpecification spec = plan.getJobSpecification();
        final HashDataWriter hashWriter = new HashDataWriter(ctx, nConsumerPartitions, edwFactory, spec
            .getConnectorRecordDescriptor(this), tpcf.createPartitioner());
        return hashWriter;
    }

    @Override
    public IFrameReader createReceiveSideReader(HyracksContext ctx, JobPlan plan, IConnectionDemultiplexer demux,
        int index, int nProducerPartitions, int nConsumerPartitions) throws HyracksDataException {
        return new NonDeterministicFrameReader(ctx, demux);
    }
}