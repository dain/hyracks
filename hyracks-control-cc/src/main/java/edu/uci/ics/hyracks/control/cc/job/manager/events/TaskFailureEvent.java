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
package edu.uci.ics.hyracks.control.cc.job.manager.events;

import java.util.UUID;

import edu.uci.ics.hyracks.api.dataflow.TaskAttemptId;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.cc.job.ActivityCluster;
import edu.uci.ics.hyracks.control.cc.job.TaskAttempt;

public class TaskFailureEvent extends AbstractTaskLifecycleEvent {
    private final Exception exception;

    public TaskFailureEvent(ClusterControllerService ccs, UUID jobId, TaskAttemptId taId, String nodeId,
            Exception exception) {
        super(ccs, jobId, taId, nodeId);
        this.exception = exception;
    }

    @Override
    protected void performEvent(TaskAttempt ta) {
        try {
            ActivityCluster ac = ta.getTaskState().getTaskCluster().getActivityCluster();
            ac.getJobRun().getScheduler().notifyTaskFailure(ta, ac, exception);
        } catch (HyracksException e) {
            e.printStackTrace();
        }
    }

    @Override
    public String toString() {
        return "TaskFailureEvent[" + jobId + ":" + taId + ":" + nodeId + "]";
    }
}