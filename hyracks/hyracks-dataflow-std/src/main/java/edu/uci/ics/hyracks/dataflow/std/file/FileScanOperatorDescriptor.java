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
package edu.uci.ics.hyracks.dataflow.std.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

import edu.uci.ics.hyracks.api.context.IHyracksStageletContext;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.job.IOperatorEnvironment;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractUnaryOutputSourceOperatorNodePushable;

public class FileScanOperatorDescriptor extends AbstractSingleActivityOperatorDescriptor {
    private static final long serialVersionUID = 1L;

    private final IFileSplitProvider fileSplitProvider;

    private final ITupleParserFactory tupleParserFactory;

    public FileScanOperatorDescriptor(JobSpecification spec, IFileSplitProvider fileSplitProvider,
            ITupleParserFactory tupleParserFactory, RecordDescriptor rDesc) {
        super(spec, 0, 1);
        this.fileSplitProvider = fileSplitProvider;
        this.tupleParserFactory = tupleParserFactory;
        recordDescriptors[0] = rDesc;
    }

    @Override
    public IOperatorNodePushable createPushRuntime(IHyracksStageletContext ctx, IOperatorEnvironment env,
            IRecordDescriptorProvider recordDescProvider, int partition, int nPartitions) {
        final FileSplit split = fileSplitProvider.getFileSplits()[partition];
        final ITupleParser tp = tupleParserFactory.createTupleParser(ctx);
        return new AbstractUnaryOutputSourceOperatorNodePushable() {
            @Override
            public void initialize() throws HyracksDataException {
                File f = split.getLocalFile().getFile();
                writer.open();
                try {
                    InputStream in;
                    try {
                        in = new FileInputStream(f);
                    } catch (FileNotFoundException e) {
                        throw new HyracksDataException(e);
                    }
                    tp.parse(in, writer);
                } finally {
                    writer.close();
                }
            }
        };
    }
}