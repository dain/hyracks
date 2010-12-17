package edu.uci.ics.hyracks.storage.am.btree.dataflow;

import edu.uci.ics.hyracks.api.context.IHyracksContext;
import edu.uci.ics.hyracks.api.dataflow.IOperatorNodePushable;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IRecordDescriptorProvider;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTrait;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.api.job.IOperatorEnvironment;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeInteriorFrameFactory;
import edu.uci.ics.hyracks.storage.am.btree.api.IBTreeLeafFrameFactory;
import edu.uci.ics.hyracks.storage.common.IStorageManagerInterface;

// re-create in-memory state for a btree that has already been built (i.e., the file exists):
// 1. register files in file manager (FileManager)
// 2. create file mappings (FileMappingProvider)
// 3. register btree instance (BTreeRegistry)

public class BTreeFileEnlistmentOperatorDescriptor extends AbstractBTreeOperatorDescriptor {

	private static final long serialVersionUID = 1L;
	
	public BTreeFileEnlistmentOperatorDescriptor(JobSpecification spec,
			RecordDescriptor recDesc,
			IStorageManagerInterface storageManager,
			IBTreeRegistryProvider btreeRegistryProvider,
			IFileSplitProvider fileSplitProvider,
			IBTreeInteriorFrameFactory interiorFactory,
			IBTreeLeafFrameFactory leafFactory, ITypeTrait[] typeTraits,
			IBinaryComparatorFactory[] comparatorFactories) {
		super(spec, 0, 0, recDesc, storageManager,
				btreeRegistryProvider, fileSplitProvider,
				interiorFactory, leafFactory, typeTraits, comparatorFactories);		
	}
	
	@Override
	public IOperatorNodePushable createPushRuntime(IHyracksContext ctx,
			IOperatorEnvironment env,
			IRecordDescriptorProvider recordDescProvider, int partition,
			int partitions) throws HyracksDataException {
		return new BTreeFileEnlistmentOperatorNodePushable(this, ctx, partition);
	}
	
}
