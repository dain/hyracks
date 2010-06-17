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

import edu.uci.ics.hyracks.api.dataflow.IOpenableDataWriter;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePullable;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.job.IOperatorEnvironment;
import edu.uci.ics.hyracks.api.job.JobPlan;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.context.HyracksContext;
import edu.uci.ics.hyracks.coreops.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.coreops.base.IOpenableDataWriterOperator;
import edu.uci.ics.hyracks.coreops.util.DeserializedOperatorNodePushable;

public class MapperOperatorDescriptor extends AbstractSingleActivityOperatorDescriptor {
    private class MapperOperator implements IOpenableDataWriterOperator {
        private IMapper mapper;
        private IOpenableDataWriter<Object[]> writer;

        @Override
        public void close() throws HyracksDataException {
            // writer.writeData(null);
            writer.close();
        }

        @Override
        public void open() throws HyracksDataException {
            mapper = mapperFactory.createMapper();
            writer.open();
        }

        @Override
        public void setDataWriter(int index, IOpenableDataWriter<Object[]> writer) {
            if (index != 0) {
                throw new IllegalArgumentException();
            }
            this.writer = writer;
        }

        @Override
        public void writeData(Object[] data) throws HyracksDataException {
            mapper.map(data, writer);
        }
    }

    private static final long serialVersionUID = 1L;

    private final IMapperFactory mapperFactory;

    public MapperOperatorDescriptor(JobSpecification spec, IMapperFactory mapperFactory,
            RecordDescriptor recordDescriptor) {
        super(spec, 1, 1);
        this.mapperFactory = mapperFactory;
        recordDescriptors[0] = recordDescriptor;
    }

    @Override
    public IOperatorNodePullable createPullRuntime(HyracksContext ctx, JobPlan plan, IOperatorEnvironment env,
            int partition) {
        throw new UnsupportedOperationException();
    }

    @Override
    public IOperatorNodePushable createPushRuntime(HyracksContext ctx, JobPlan plan, IOperatorEnvironment env,
            int partition) {
        return new DeserializedOperatorNodePushable(ctx, new MapperOperator(), plan, getActivityNodeId());
    }

    @Override
    public boolean supportsPullInterface() {
        return false;
    }

    @Override
    public boolean supportsPushInterface() {
        return true;
    }
}