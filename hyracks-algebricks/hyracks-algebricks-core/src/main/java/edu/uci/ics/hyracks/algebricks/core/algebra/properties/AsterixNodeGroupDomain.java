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
package edu.uci.ics.hyracks.algebricks.core.algebra.properties;

public class AsterixNodeGroupDomain implements INodeDomain {

    private String groupName;

    public AsterixNodeGroupDomain(String groupName) {
        this.groupName = groupName;
    }

    @Override
    public boolean sameAs(INodeDomain domain) {
        if (!(domain instanceof AsterixNodeGroupDomain)) {
            return false;
        }
        AsterixNodeGroupDomain dom2 = (AsterixNodeGroupDomain) domain;
        return groupName.equals(dom2.groupName);
    }

    @Override
    public String toString() {
        return "AsterixDomain(" + groupName + ")";
    }

    @Override
    public Integer cardinality() {
        return null;
    }
}
