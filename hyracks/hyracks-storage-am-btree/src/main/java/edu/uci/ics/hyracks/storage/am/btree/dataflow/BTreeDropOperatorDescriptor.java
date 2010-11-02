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

package edu.uci.ics.hyracks.storage.am.btree.dataflow;

import edu.uci.ics.hyracks.api.context.IHyracksContext;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.job.IOperatorEnvironment;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.std.base.AbstractSingleActivityOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;

public class BTreeDropOperatorDescriptor extends AbstractSingleActivityOperatorDescriptor {
	
	private static final long serialVersionUID = 1L;
	
	private IBufferCacheProvider bufferCacheProvider;
	private IBTreeRegistryProvider btreeRegistryProvider;	
	private IFileMappingProviderProvider fileMappingProviderProvider;
	private IFileSplitProvider fileSplitProvider;
	
	public BTreeDropOperatorDescriptor(JobSpecification spec,			
			IBufferCacheProvider bufferCacheProvider,
			IBTreeRegistryProvider btreeRegistryProvider,
			IFileSplitProvider fileSplitProvider, IFileMappingProviderProvider fileMappingProviderProvider) {
		super(spec, 0, 0);
		this.fileMappingProviderProvider = fileMappingProviderProvider;
		this.bufferCacheProvider = bufferCacheProvider;
		this.btreeRegistryProvider = btreeRegistryProvider;
		this.fileSplitProvider = fileSplitProvider;
	}
	
	@Override
	public IOperatorNodePushable createPushRuntime(IHyracksContext ctx,
			IOperatorEnvironment env,
			IRecordDescriptorProvider recordDescProvider, int partition,
			int nPartitions) {
		return new BTreeDropOperatorNodePushable(bufferCacheProvider, btreeRegistryProvider, fileSplitProvider, partition, fileMappingProviderProvider);
	}	
}
