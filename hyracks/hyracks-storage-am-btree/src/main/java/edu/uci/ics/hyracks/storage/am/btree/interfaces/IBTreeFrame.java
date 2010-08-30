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

package edu.uci.ics.hyracks.storage.am.btree.interfaces;

import edu.uci.ics.hyracks.storage.am.btree.impls.MultiComparator;
import edu.uci.ics.hyracks.storage.am.btree.impls.SplitKey;

public interface IBTreeFrame extends IFrame {
	// TODO; what if records more than half-page size?
	public int split(IBTreeFrame rightFrame, byte[] data, MultiComparator cmp, SplitKey splitKey) throws Exception;		
	
	// TODO: check if we do something nicer than returning object
	public ISlotManager getSlotManager();
	
	// ATTENTION: in b-tree operations it may not always be possible to determine whether an ICachedPage is a leaf or interior node
	// a compatible interior and leaf implementation MUST return identical values when given the same ByteBuffer for the functions below	
	public boolean isLeaf();
	public byte getLevel();
	public void setLevel(byte level);	
	public boolean getSmFlag(); // structure modification flag
	public void setSmFlag(boolean smFlag);	
	
	//public int getNumPrefixRecords();
	//public void setNumPrefixRecords(int numPrefixRecords);
	
	public void insertSorted(byte[] data, MultiComparator cmp) throws Exception;
	
	// for debugging
	public int getFreeSpaceOff();
	public void setFreeSpaceOff(int freeSpace);
}
