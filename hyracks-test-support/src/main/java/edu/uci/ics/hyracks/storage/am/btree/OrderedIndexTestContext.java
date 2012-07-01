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

package edu.uci.ics.hyracks.storage.am.btree;

import java.util.Collection;
import java.util.TreeSet;

import edu.uci.ics.hyracks.api.dataflow.value.ISerializerDeserializer;
import edu.uci.ics.hyracks.storage.am.common.CheckTuple;
import edu.uci.ics.hyracks.storage.am.common.TreeIndexTestContext;
import edu.uci.ics.hyracks.storage.am.common.api.ITreeIndex;

@SuppressWarnings("rawtypes")
public abstract class OrderedIndexTestContext extends TreeIndexTestContext<CheckTuple> {

    protected final TreeSet<CheckTuple> checkTuples = new TreeSet<CheckTuple>();

    public OrderedIndexTestContext(ISerializerDeserializer[] fieldSerdes, ITreeIndex treeIndex) {
        super(fieldSerdes, treeIndex);
    }

    public void upsertCheckTuple(CheckTuple checkTuple, Collection<CheckTuple> checkTuples) {
    	if (checkTuples.contains(checkTuple)) {
            checkTuples.remove(checkTuple);
        }
        checkTuples.add(checkTuple);
    }
    
    @Override
    public TreeSet<CheckTuple> getCheckTuples() {
        return checkTuples;
    }

}