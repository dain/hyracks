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
package edu.uci.ics.hyracks.storage.am.common.util;

import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparator;
import edu.uci.ics.hyracks.api.dataflow.value.IBinaryComparatorFactory;
import edu.uci.ics.hyracks.storage.am.common.ophelpers.MultiComparator;

public class IndexUtils {
	public static MultiComparator createMultiComparator(IBinaryComparatorFactory[] cmpFactories) {
    	IBinaryComparator[] cmps = new IBinaryComparator[cmpFactories.length];
    	for (int i = 0; i < cmpFactories.length; i++) {
    		cmps[i] = cmpFactories[i].createBinaryComparator(); 
    	}
    	return new MultiComparator(cmps);
    }
}
