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

package edu.uci.ics.hyracks.storage.am.btree.api;

import edu.uci.ics.hyracks.dataflow.common.data.accessors.ITupleReference;
import edu.uci.ics.hyracks.storage.am.btree.impls.FindTupleMode;
import edu.uci.ics.hyracks.storage.am.btree.impls.FindTupleNoExactMatchPolicy;
import edu.uci.ics.hyracks.storage.am.btree.impls.MultiComparator;

public interface ISlotManager {
    public void setFrame(ITreeIndexFrame frame);

    public int findTupleIndex(ITupleReference searchKey, ITreeIndexTupleReference frameTuple, MultiComparator multiCmp,
            FindTupleMode mode, FindTupleNoExactMatchPolicy matchPolicy);

    public int insertSlot(int tupleIndex, int tupleOff);

    public int getSlotStartOff();

    public int getSlotEndOff();

    public int getTupleOff(int slotOff);

    public void setSlot(int slotOff, int value);

    public int getSlotOff(int tupleIndex);

    public int getSlotSize();
}
