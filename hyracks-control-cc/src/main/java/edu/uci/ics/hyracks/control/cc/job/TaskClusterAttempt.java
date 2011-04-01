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
package edu.uci.ics.hyracks.control.cc.job;


public class TaskClusterAttempt {
    public enum TaskClusterStatus {
        INITIALIZED,
        RUNNING,
        COMPLETED,
        FAILED,
    }

    private final TaskAttempt[] taskAttempts;

    private TaskClusterStatus status;

    private int pendingTaskCounter;

    public TaskClusterAttempt(TaskAttempt[] taskAttempts) {
        this.taskAttempts = taskAttempts;
    }

    public TaskAttempt[] getTaskAttempts() {
        return taskAttempts;
    }

    public void setStatus(TaskClusterStatus status) {
        this.status = status;
    }

    public TaskClusterStatus getStatus() {
        return status;
    }

    public void initializePendingTaskCounter() {
        pendingTaskCounter = taskAttempts.length;
    }

    public int getPendingTaskCounter() {
        return pendingTaskCounter;
    }

    public int decrementPendingTasksCounter() {
        return --pendingTaskCounter;
    }
}