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
package edu.uci.ics.hyracks.tests.integration;

import java.io.File;

import org.junit.Test;

import edu.uci.ics.hyracks.api.constraints.PartitionConstraintHelper;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.dataflow.value.RecordDescriptor;
import edu.uci.ics.hyracks.api.io.FileReference;
import edu.uci.ics.hyracks.api.job.JobSpecification;
import edu.uci.ics.hyracks.data.std.accessors.PointableBinaryComparatorFactory;
import edu.uci.ics.hyracks.data.std.accessors.PointableBinaryHashFunctionFactory;
import edu.uci.ics.hyracks.data.std.primitive.UTF8StringPointable;
import edu.uci.ics.hyracks.dataflow.common.data.marshalling.UTF8StringSerializerDeserializer;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.IValueParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.parsers.UTF8StringParserFactory;
import edu.uci.ics.hyracks.dataflow.common.data.partition.FieldHashPartitionComputerFactory;
import edu.uci.ics.hyracks.dataflow.std.connectors.MToNPartitioningMergingConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.connectors.OneToOneConnectorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.ConstantFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.file.DelimitedDataTupleParserFactory;
import edu.uci.ics.hyracks.dataflow.std.file.FileScanOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.file.FileSplit;
import edu.uci.ics.hyracks.dataflow.std.file.IFileSplitProvider;
import edu.uci.ics.hyracks.dataflow.std.misc.LimitOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.misc.PrinterOperatorDescriptor;
import edu.uci.ics.hyracks.dataflow.std.sort.OptimizedExternalSortOperatorDescriptor;

public class OptimizedSortMergeTest extends AbstractIntegrationTest {

    @Test
    public void optimizedSortMergeTest01() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileSplit[] ordersSplits = new FileSplit[] {
                new FileSplit(NC1_ID, new FileReference(new File("data/tpch0.001/orders-part1.tbl"))),
                new FileSplit(NC2_ID, new FileReference(new File("data/tpch0.001/orders-part2.tbl"))) };
        IFileSplitProvider ordersSplitProvider = new ConstantFileSplitProvider(ordersSplits);
        RecordDescriptor ordersDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE });

        FileScanOperatorDescriptor ordScanner = new FileScanOperatorDescriptor(spec, ordersSplitProvider,
                new DelimitedDataTupleParserFactory(new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE }, '|'), ordersDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, ordScanner, NC1_ID, NC2_ID);

        OptimizedExternalSortOperatorDescriptor sorter = new OptimizedExternalSortOperatorDescriptor(spec, 4,
                new int[] { 1, 0 }, new IBinaryComparatorFactory[] {
                        PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY),
                        PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY) }, ordersDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, sorter, NC1_ID, NC2_ID);

        PrinterOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, NC1_ID);

        spec.connect(new OneToOneConnectorDescriptor(spec), ordScanner, 0, sorter, 0);

        spec.connect(
                new MToNPartitioningMergingConnectorDescriptor(spec, new FieldHashPartitionComputerFactory(new int[] {
                        1, 0 }, new IBinaryHashFunctionFactory[] {
                        PointableBinaryHashFunctionFactory.of(UTF8StringPointable.FACTORY),
                        PointableBinaryHashFunctionFactory.of(UTF8StringPointable.FACTORY) }), new int[] { 1, 0 },
                        new IBinaryComparatorFactory[] {
                                PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY),
                                PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY) }), sorter, 0,
                printer, 0);

        runTest(spec);
    }

    @Test
    public void optimizedSortMergeTest02() throws Exception {
        JobSpecification spec = new JobSpecification();

        FileSplit[] ordersSplits = new FileSplit[] {
                new FileSplit(NC1_ID, new FileReference(new File("data/tpch0.001/orders-part1.tbl"))),
                new FileSplit(NC2_ID, new FileReference(new File("data/tpch0.001/orders-part2.tbl"))) };
        IFileSplitProvider ordersSplitProvider = new ConstantFileSplitProvider(ordersSplits);
        RecordDescriptor ordersDesc = new RecordDescriptor(new ISerializerDeserializer[] {
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE, UTF8StringSerializerDeserializer.INSTANCE,
                UTF8StringSerializerDeserializer.INSTANCE });

        FileScanOperatorDescriptor ordScanner = new FileScanOperatorDescriptor(spec, ordersSplitProvider,
                new DelimitedDataTupleParserFactory(new IValueParserFactory[] { UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE,
                        UTF8StringParserFactory.INSTANCE, UTF8StringParserFactory.INSTANCE }, '|'), ordersDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, ordScanner, NC1_ID, NC2_ID);

        int outputLimit = 200;
        OptimizedExternalSortOperatorDescriptor sorter = new OptimizedExternalSortOperatorDescriptor(spec, 4,
                outputLimit, new int[] { 1, 0 }, new IBinaryComparatorFactory[] {
                        PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY),
                        PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY) }, ordersDesc);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, sorter, NC1_ID, NC2_ID);

        LimitOperatorDescriptor filter = new LimitOperatorDescriptor(spec, ordersDesc, outputLimit);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, filter, NC1_ID);

        PrinterOperatorDescriptor printer = new PrinterOperatorDescriptor(spec);
        PartitionConstraintHelper.addAbsoluteLocationConstraint(spec, printer, NC1_ID);

        spec.connect(new OneToOneConnectorDescriptor(spec), ordScanner, 0, sorter, 0);

        spec.connect(
                new MToNPartitioningMergingConnectorDescriptor(spec, new FieldHashPartitionComputerFactory(new int[] {
                        1, 0 }, new IBinaryHashFunctionFactory[] {
                        PointableBinaryHashFunctionFactory.of(UTF8StringPointable.FACTORY),
                        PointableBinaryHashFunctionFactory.of(UTF8StringPointable.FACTORY) }), new int[] { 1, 0 },
                        new IBinaryComparatorFactory[] {
                                PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY),
                                PointableBinaryComparatorFactory.of(UTF8StringPointable.FACTORY) }), sorter, 0, filter,
                0);

        spec.connect(new OneToOneConnectorDescriptor(spec), filter, 0, printer, 0);

        runTest(spec);
    }

}