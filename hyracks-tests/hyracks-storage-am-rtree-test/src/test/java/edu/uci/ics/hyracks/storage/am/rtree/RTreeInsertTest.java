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

package edu.uci.ics.hyracks.storage.am.rtree;

import java.util.Random;

import org.junit.After;
import org.junit.Before;

import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.api.exceptions.HyracksDataException;
import edu.uci.ics.hyracks.storage.am.common.api.IPrimitiveValueProviderFactory;
import edu.uci.ics.hyracks.storage.am.rtree.AbstractRTreeInsertTest;
import edu.uci.ics.hyracks.storage.am.rtree.AbstractRTreeTestContext;
import edu.uci.ics.hyracks.storage.am.rtree.utils.RTreeTestContext;
import edu.uci.ics.hyracks.storage.am.rtree.utils.RTreeTestHarness;

/**
 * Tests the BTree insert operation with strings and integer fields using
 * various numbers of key and payload fields.
 * 
 * Each tests first fills a BTree with randomly generated tuples. We compare the
 * following operations against expected results: 1. Point searches for all
 * tuples. 2. Ordered scan. 3. Disk-order scan. 4. Range search (and prefix
 * search for composite keys).
 * 
 */
@SuppressWarnings("rawtypes")
public class RTreeInsertTest extends AbstractRTreeInsertTest {

    private final RTreeTestHarness harness = new RTreeTestHarness();

    @Before
    public void setUp() throws HyracksDataException {
        harness.setUp();
    }

    @After
    public void tearDown() throws HyracksDataException {
        harness.tearDown();
    }

    @Override
    protected AbstractRTreeTestContext createTestContext(ISerializerDeserializer[] fieldSerdes,
            IPrimitiveValueProviderFactory[] valueProviderFactories, int numKeys) throws Exception {
        return RTreeTestContext.create(harness.getBufferCache(), harness.getTreeFileId(), fieldSerdes,
                valueProviderFactories, numKeys);
    }

    @Override
    protected Random getRandom() {
        return harness.getRandom();
    }
}
