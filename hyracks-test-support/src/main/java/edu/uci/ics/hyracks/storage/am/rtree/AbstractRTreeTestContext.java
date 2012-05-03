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

import java.util.ArrayList;
import java.util.Collection;

import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.storage.am.common.TreeIndexTestContext;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;

@SuppressWarnings("rawtypes")
public abstract class AbstractRTreeTestContext extends TreeIndexTestContext<RTreeCheckTuple> {
    private final ArrayList<RTreeCheckTuple> checkTuples = new ArrayList<RTreeCheckTuple>();

    public AbstractRTreeTestContext(ISerializerDeserializer[] fieldSerdes, ITreeIndex treeIndex) {
        super(fieldSerdes, treeIndex);
    }

    @Override
    public Collection<RTreeCheckTuple> getCheckTuples() {
        return checkTuples;
    }

}