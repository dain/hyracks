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

package edu.uci.ics.hyracks.tests.am.rtree;

import java.io.DataOutput;
import java.io.File;

import org.junit.After;
import org.junit.Before;

import edu.uci.ics.hyracks.api.constraints.PartitionConstraintHelper;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.ITypeTraits;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.data.std.accessors.PointableBinaryComparatorFactory;
import edu.uci.ics.hyracks.data.std.primitive.DoublePointable;
import edu.uci.ics.hyracks.data.std.primitive.UTF8StringPointable;
import edu.uci.ics.hyracks.dataflow.common.comm.io.ArrayTupleBuilder;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.DoubleSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.DoubleParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IValueParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.UTF8StringParserFactory;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.ConstantFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.DelimitedDataTupleParserFactory;
import edu.uci.ics.hyracks.dataflow.std.file.FileScanOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.misc.ConstantTupleSourceOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.misc.NullSinkOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.sort.ExternalSortOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.btree.dataflow.BTreeDataflowHelperFactory;
import edu.uci.ics.hyracks.storage.am.btree.dataflow.BTreeSearchOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndex;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexDataflowHelperFactory;
import edu.uci.ics.hyracks.storage.am.common.dataflow.IIndexRegistryProvider;
import edu.uci.ics.hyracks.storage.am.common.dataflow.TreeIndexBulkLoadOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.common.dataflow.TreeIndexInsertUpdateDeleteOperatorDescriptor;
import edu.uci.ics.hyracks.storage.am.common.impls.NoOpOperationCallbackProvider;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.IndexOp;
import edu.uci.ics.hyracks.storage.am.rtree.util.RTreeUtils;
import edu.uci.ics.hyracks.storage.common.IStorageManagerInterface;
import edu.uci.ics.hyracks.test.support.TestIndexRegistryProvider;
import edu.uci.ics.hyracks.test.support.TestStorageManagerComponentHolder;
import edu.uci.ics.hyracks.test.support.TestStorageManagerInterface;
import edu.uci.ics.hyracks.tests.am.common.ITreeIndexOperatorTestHelper;
import edu.uci.ics.hyracks.tests.integration.AbstractIntegrationTest;

public abstract class AbstractRTreeOperatorTest extends AbstractIntegrationTest {
    static {
        TestStorageManagerComponentHolder.init(8192, 20, 20);
    }

    protected final IStorageManagerInterface storageManager = new TestStorageManagerInterface();
    protected final IIndexRegistryProvider<IIndex> indexRegistryProvider = new TestIndexRegistryProvider();
    protected IIndexDataflowHelperFactory rtreeDataflowHelperFactory;
    protected IIndexDataflowHelperFactory btreeDataflowHelperFactory = new BTreeDataflowHelperFactory();

    // field, type and key declarations for primary index
    protected final int primaryFieldCount = 10;
    protected final ITypeTraits[] primaryTypeTraits = new ITypeTraits[primaryFieldCount];
    protected final int primaryKeyFieldCount = 1;
    protected final IBinaryComparatorFactory[] primaryComparatorFactories = new IBinaryComparatorFactory[primaryKeyFieldCount];

    protected final RecordDescriptor primaryRecDesc = new RecordDescriptor(new ISerializerDeserializer[] {
            UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
            UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
            UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
            DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
            DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE });

    // to be set by subclasses
    protected String primaryFileName;
    protected IFileSplitProvider primarySplitProvider;

    // field, type and key declarations for secondary indexes
    protected final int secondaryFieldCount = 5;
    protected final ITypeTraits[] secondaryTypeTraits = new ITypeTraits[secondaryFieldCount];
    protected final int secondaryKeyFieldCount = 4;
    protected final IBinaryComparatorFactory[] secondaryComparatorFactories = new IBinaryComparatorFactory[secondaryKeyFieldCount];

    protected final RecordDescriptor secondaryRecDesc = new RecordDescriptor(new ISerializerDeserializer[] {
            DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
            DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
            UTF8StringSerializerDeserializer.INSTANCE });

    // This is only used for the LSMRTree. We need a comparator Factories for
    // the BTree component of the LSMRTree.
    protected final int btreeKeyFieldCount = 5;
    protected final IBinaryComparatorFactory[] btreeComparatorFactories = new IBinaryComparatorFactory[btreeKeyFieldCount];

    protected String secondaryFileName;
    protected IFileSplitProvider secondarySplitProvider;

    protected ITreeIndexOperatorTestHelper testHelper;

    protected ITreeIndexOperatorTestHelper createTestHelper() throws HyracksException {
        return new RTreeOperatorTestHelper();
    }

    @Before
    public void setup() throws Exception {
        testHelper = createTestHelper();

        primaryFileName = testHelper.getPrimaryIndexName();
        primarySplitProvider = new ConstantFileSplitProvider(new FileSplit[] { new FileSplit(NC1_ID, new FileReference(
                new File(primaryFileName))) });
        secondaryFileName = testHelper.getSecondaryIndexName();
        secondarySplitProvider = new ConstantFileSplitProvider(new FileSplit[] { new FileSplit(NC1_ID,
                new FileReference(new File(secondaryFileName))) });

        // field, type and key declarations for primary index
        primaryTypeTraits[0] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[1] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[2] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[3] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[4] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[5] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[6] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[7] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[8] = UTF8StringPointable.TYPE_TRAITS;
        primaryTypeTraits[9] = UTF8StringPointable.TYPE_TRAITS;
        primaryComparatorFactories[0] = PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY);

        // field, type and key declarations for secondary indexes
        secondaryTypeTraits[0] = DoublePointable.TYPE_TRAITS;
        secondaryTypeTraits[1] = DoublePointable.TYPE_TRAITS;
        secondaryTypeTraits[2] = DoublePointable.TYPE_TRAITS;
        secondaryTypeTraits[3] = DoublePointable.TYPE_TRAITS;
        secondaryTypeTraits[4] = UTF8StringPointable.TYPE_TRAITS;
        secondaryComparatorFactories[0] = PointableBinaryComparatorFactory.of(DoublePointable.FACTORY);
        secondaryComparatorFactories[1] = PointableBinaryComparatorFactory.of(DoublePointable.FACTORY);
        secondaryComparatorFactories[2] = PointableBinaryComparatorFactory.of(DoublePointable.FACTORY);
        secondaryComparatorFactories[3] = PointableBinaryComparatorFactory.of(DoublePointable.FACTORY);

        // This only used for LSMRTree
        btreeComparatorFactories[0] = PointableBinaryComparatorFactory.of(DoublePointable.FACTORY);
        btreeComparatorFactories[1] = PointableBinaryComparatorFactory.of(DoublePointable.FACTORY);
        btreeComparatorFactories[2] = PointableBinaryComparatorFactory.of(DoublePointable.FACTORY);
        btreeComparatorFactories[3] = PointableBinaryComparatorFactory.of(DoublePointable.FACTORY);
        btreeComparatorFactories[4] = PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY);

        IPrimitiveValueProviderFactory[] secondaryValueProviderFactories = RTreeUtils
                .createPrimitiveValueProviderFactories(secondaryComparatorFactories.length, DoublePointable.FACTORY);

        rtreeDataflowHelperFactory = createDataFlowHelperFactory(secondaryValueProviderFactories,
                btreeComparatorFactories);

    }

    protected abstract IIndexDataflowHelperFactory createDataFlowHelperFactory(
            IPrimitiveValueProviderFactory[] secondaryValueProviderFactories,
            IBinaryComparatorFactory[] btreeComparatorFactories);

    protected void loadPrimaryIndex() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileSplit[] ordersSplits = new FileSplit[] { new FileSplit(NC1_ID, new FileReference(new File(
                "data/orders-with-locations-part1.txt"))) };
        IFileSplitProvider ordersSplitProvider = new ConstantFileSplitProvider(ordersSplits);
        RecordDescriptor ordersDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
                DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
                DoubleSerializerDeserializer.INSTANCE });

        FileScanOperatorDescriptor ordScanner = new FileScanOperatorDescriptor(spec, ordersSplitProvider,
                new DelimitedDataTupleParserFactory(new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        DoubleParserFactory.INSTANCE, DoubleParserFactory.INSTANCE, DoubleParserFactory.INSTANCE,
                        DoubleParserFactory.INSTANCE }, '|'), ordersDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, ordScanner, NC1_ID);

        ExternalSortOperatorDescriptor sorter = new ExternalSortOperatorDescriptor(spec, 1000, new int[] { 0 },
                new IBinaryComparatorFactory[] { PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY) },
                ordersDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, sorter, NC1_ID);

        int[] fieldPermutation = { 0, 1, 2, 4, 5, 7, 9, 10, 11, 12 };
        TreeIndexBulkLoadOperatorDescriptor primaryBulkLoad = new TreeIndexBulkLoadOperatorDescriptor(spec,
                storageManager, indexRegistryProvider, primarySplitProvider, primaryTypeTraits,
                primaryComparatorFactories, fieldPermutation, 0.7f, btreeDataflowHelperFactory,
                NoOpOperationCallbackProvider.INSTANCE);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, primaryBulkLoad, NC1_ID);

        spec.connect(new OneToOneConnectorDescriptor(spec), ordScanner, 0, sorter, 0);

        spec.connect(new OneToOneConnectorDescriptor(spec), sorter, 0, primaryBulkLoad, 0);

        spec.addRoot(primaryBulkLoad);
        runTest(spec);
    }

    protected void loadSecondaryIndex() throws Exception {
        JobSpecification spec = new JobSpecification();

        // build dummy tuple containing nothing
        ArrayTupleBuilder tb = new ArrayTupleBuilder(primaryKeyFieldCount * 2);
        DataOutput dos = tb.getDataOutput();

        tb.reset();
        UTF8StringSerializerDeserializer.INSTANCE.serialize("0", dos);
        tb.addFieldEndOffset();

        ISerializerDeserializer[] keyRecDescSers = { UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE };
        RecordDescriptor keyRecDesc = new RecordDescriptor(keyRecDescSers);

        ConstantTupleSourceOperatorDescriptor keyProviderOp = new ConstantTupleSourceOperatorDescriptor(spec,
                keyRecDesc, tb.getFieldEndOffsets(), tb.getByteArray(), tb.getSize());
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, keyProviderOp, NC1_ID);

        int[] lowKeyFields = null; // - infinity
        int[] highKeyFields = null; // + infinity

        // scan primary index
        BTreeSearchOperatorDescriptor primarySearchOp = new BTreeSearchOperatorDescriptor(spec, primaryRecDesc,
                storageManager, indexRegistryProvider, primarySplitProvider, primaryTypeTraits,
                primaryComparatorFactories, lowKeyFields, highKeyFields, true, true, btreeDataflowHelperFactory,
                NoOpOperationCallbackProvider.INSTANCE);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, primarySearchOp, NC1_ID);

        // load secondary index
        int[] fieldPermutation = { 6, 7, 8, 9, 0 };
        TreeIndexBulkLoadOperatorDescriptor secondaryBulkLoad = new TreeIndexBulkLoadOperatorDescriptor(spec,
                storageManager, indexRegistryProvider, secondarySplitProvider, secondaryTypeTraits,
                secondaryComparatorFactories, fieldPermutation, 0.7f, rtreeDataflowHelperFactory,
                NoOpOperationCallbackProvider.INSTANCE);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, secondaryBulkLoad, NC1_ID);

        spec.connect(new OneToOneConnectorDescriptor(spec), keyProviderOp, 0, primarySearchOp, 0);
        spec.connect(new OneToOneConnectorDescriptor(spec), primarySearchOp, 0, secondaryBulkLoad, 0);

        spec.addRoot(secondaryBulkLoad);
        runTest(spec);
    }

    protected void insertPipeline() throws Exception {

        JobSpecification spec = new JobSpecification();

        FileSplit[] ordersSplits = new FileSplit[] { new FileSplit(NC1_ID, new FileReference(new File(
                "data/orders-with-locations-part2.txt"))) };
        IFileSplitProvider ordersSplitProvider = new ConstantFileSplitProvider(ordersSplits);
        RecordDescriptor ordersDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
                DoubleSerializerDeserializer.INSTANCE, DoubleSerializerDeserializer.INSTANCE,
                DoubleSerializerDeserializer.INSTANCE });

        FileScanOperatorDescriptor ordScanner = new FileScanOperatorDescriptor(spec, ordersSplitProvider,
                new DelimitedDataTupleParserFactory(new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        DoubleParserFactory.INSTANCE, DoubleParserFactory.INSTANCE, DoubleParserFactory.INSTANCE,
                        DoubleParserFactory.INSTANCE }, '|'), ordersDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, ordScanner, NC1_ID);

        // insert into primary index
        int[] primaryFieldPermutation = { 0, 1, 2, 4, 5, 7, 9, 10, 11, 12 };
        TreeIndexInsertUpdateDeleteOperatorDescriptor primaryInsertOp = new TreeIndexInsertUpdateDeleteOperatorDescriptor(
                spec, ordersDesc, storageManager, indexRegistryProvider, primarySplitProvider, primaryTypeTraits,
                primaryComparatorFactories, primaryFieldPermutation, IndexOp.INSERT, btreeDataflowHelperFactory,
                NoOpOperationCallbackProvider.INSTANCE);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, primaryInsertOp, NC1_ID);

        // secondary index
        int[] secondaryFieldPermutation = { 6, 7, 8, 9, 0 };
        TreeIndexInsertUpdateDeleteOperatorDescriptor secondaryInsertOp = new TreeIndexInsertUpdateDeleteOperatorDescriptor(
                spec, ordersDesc, storageManager, indexRegistryProvider, secondarySplitProvider, secondaryTypeTraits,
                secondaryComparatorFactories, secondaryFieldPermutation, IndexOp.INSERT, rtreeDataflowHelperFactory,
                NoOpOperationCallbackProvider.INSTANCE);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, secondaryInsertOp, NC1_ID);

        NullSinkOperatorDescriptor nullSink = new NullSinkOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, nullSink, NC1_ID);

        spec.connect(new OneToOneConnectorDescriptor(spec), ordScanner, 0, primaryInsertOp, 0);

        spec.connect(new OneToOneConnectorDescriptor(spec), primaryInsertOp, 0, secondaryInsertOp, 0);

        spec.connect(new OneToOneConnectorDescriptor(spec), secondaryInsertOp, 0, nullSink, 0);

        spec.addRoot(nullSink);
        runTest(spec);
    }

    @After
    public void cleanup() throws Exception {
        testHelper.cleanup(primaryFileName, secondaryFileName);
    }
}