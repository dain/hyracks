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
package edu.uci.ics.hyracks.api.dataflow;

import java.io.Serializable;

public final class TaskAttemptId implements Serializable {
    private static final long serialVersionUID = 1L;

    private final TaskId taskId;

    private final int attempt;

    public TaskAttemptId(TaskId taskId, int attempt) {
        this.taskId = taskId;
        this.attempt = attempt;
    }

    public TaskId getTaskId() {
        return taskId;
    }

    public int getAttempt() {
        return attempt;
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof TaskAttemptId)) {
            return false;
        }
        TaskAttemptId oTaskId = (TaskAttemptId) o;
        return oTaskId.attempt == attempt && oTaskId.taskId.equals(taskId);
    }

    @Override
    public int hashCode() {
        return taskId.hashCode() + attempt;
    }

    @Override
    public String toString() {
        return "TAID:" + taskId + ":" + attempt;
    }

    public static TaskAttemptId parse(String str) {
        if (str.startsWith("TAID:")) {
            str = str.substring(5);
            int idIdx = str.lastIndexOf(':');
            return new TaskAttemptId(TaskId.parse(str.substring(0, idIdx)), Integer.parseInt(str.substring(idIdx + 1)));
        }
        throw new IllegalArgumentException("Unable to parse: " + str);
    }
}