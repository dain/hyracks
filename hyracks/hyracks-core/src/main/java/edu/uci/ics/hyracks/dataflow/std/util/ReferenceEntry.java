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
package edu.uci.ics.hyracks.dataflow.std.util;

import edu.uci.ics.hyracks.comm.io.FrameTupleAccessor;

public class ReferenceEntry {
    private final int runid;
    private FrameTupleAccessor acccessor;
    private int tupleIndex;

    public ReferenceEntry(int runid, FrameTupleAccessor fta, int tupleIndex) {
        super();
        this.runid = runid;
        this.acccessor = fta;
        this.tupleIndex = tupleIndex;
    }

    public int getRunid() {
        return runid;
    }

    public FrameTupleAccessor getAccessor() {
        return acccessor;
    }

    public void setAccessor(FrameTupleAccessor fta) {
        this.acccessor = fta;
    }

    public int getTupleIndex() {
        return tupleIndex;
    }

    public void setTupleIndex(int tupleIndex) {
        this.tupleIndex = tupleIndex;
    }
}