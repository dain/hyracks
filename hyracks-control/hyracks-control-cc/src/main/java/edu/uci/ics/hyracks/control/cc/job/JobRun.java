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

import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.uci.ics.hyracks.api.dataflow.ActivityId;
import edu.uci.ics.hyracks.api.dataflow.ConnectorDescriptorId;
import edu.uci.ics.hyracks.api.dataflow.TaskId;
import edu.uci.ics.hyracks.api.dataflow.connectors.IConnectorPolicy;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.job.ActivityCluster;
import edu.uci.ics.hyracks.api.job.ActivityClusterGraph;
import edu.uci.ics.hyracks.api.job.ActivityClusterId;
import edu.uci.ics.hyracks.api.job.IActivityClusterGraphGenerator;
import edu.uci.ics.hyracks.api.job.JobFlag;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.api.job.JobStatus;
import edu.uci.ics.hyracks.api.partitions.PartitionId;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.cc.partitions.PartitionMatchMaker;
import edu.uci.ics.hyracks.control.cc.scheduler.ActivityPartitionDetails;
import edu.uci.ics.hyracks.control.cc.scheduler.JobScheduler;
import edu.uci.ics.hyracks.control.common.job.profiling.om.JobProfile;

public class JobRun implements IJobStatusConditionVariable {
    private final JobId jobId;

    private final String applicationName;

    private final IActivityClusterGraphGenerator acgg;

    private final ActivityClusterGraph acg;

    private final JobScheduler scheduler;

    private final EnumSet<JobFlag> jobFlags;

    private final Map<ActivityClusterId, ActivityClusterPlan> activityClusterPlanMap;

    private final PartitionMatchMaker pmm;

    private final Set<String> participatingNodeIds;

    private final Set<String> cleanupPendingNodeIds;

    private final JobProfile profile;

    private final Map<ConnectorDescriptorId, IConnectorPolicy> connectorPolicyMap;

    private long createTime;

    private long startTime;

    private long endTime;

    private JobStatus status;

    private Exception exception;

    private JobStatus pendingStatus;

    private Exception pendingException;

    public JobRun(ClusterControllerService ccs, JobId jobId, String applicationName,
            IActivityClusterGraphGenerator acgg, EnumSet<JobFlag> jobFlags) {
        this.jobId = jobId;
        this.applicationName = applicationName;
        this.acgg = acgg;
        this.acg = acgg.initialize();
        this.scheduler = new JobScheduler(ccs, this, acgg.getConstraints());
        this.jobFlags = jobFlags;
        activityClusterPlanMap = new HashMap<ActivityClusterId, ActivityClusterPlan>();
        pmm = new PartitionMatchMaker();
        participatingNodeIds = new HashSet<String>();
        cleanupPendingNodeIds = new HashSet<String>();
        profile = new JobProfile(jobId);
        connectorPolicyMap = new HashMap<ConnectorDescriptorId, IConnectorPolicy>();
    }

    public JobId getJobId() {
        return jobId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public ActivityClusterGraph getActivityClusterGraph() {
        return acg;
    }

    public EnumSet<JobFlag> getFlags() {
        return jobFlags;
    }

    public Map<ActivityClusterId, ActivityClusterPlan> getActivityClusterPlanMap() {
        return activityClusterPlanMap;
    }

    public PartitionMatchMaker getPartitionMatchMaker() {
        return pmm;
    }

    public synchronized void setStatus(JobStatus status, Exception exception) {
        this.status = status;
        this.exception = exception;
        notifyAll();
    }

    public synchronized JobStatus getStatus() {
        return status;
    }

    public synchronized Exception getException() {
        return exception;
    }

    public void setPendingStatus(JobStatus status, Exception exception) {
        this.pendingStatus = status;
        this.pendingException = exception;
    }

    public JobStatus getPendingStatus() {
        return pendingStatus;
    }

    public synchronized Exception getPendingException() {
        return pendingException;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    @Override
    public synchronized void waitForCompletion() throws Exception {
        while (status != JobStatus.TERMINATED && status != JobStatus.FAILURE) {
            wait();
        }
        if (exception != null) {
            throw new HyracksException("Job Failed", exception);
        }
    }

    public Set<String> getParticipatingNodeIds() {
        return participatingNodeIds;
    }

    public Set<String> getCleanupPendingNodeIds() {
        return cleanupPendingNodeIds;
    }

    public JobProfile getJobProfile() {
        return profile;
    }

    public JobScheduler getScheduler() {
        return scheduler;
    }

    public Map<ConnectorDescriptorId, IConnectorPolicy> getConnectorPolicyMap() {
        return connectorPolicyMap;
    }

    public JSONObject toJSON() throws JSONException {
        JSONObject result = new JSONObject();

        result.put("job-id", jobId.toString());
        result.put("application-name", applicationName);
        result.put("status", getStatus());
        result.put("create-time", getCreateTime());
        result.put("start-time", getCreateTime());
        result.put("end-time", getCreateTime());

        JSONArray aClusters = new JSONArray();
        for (ActivityCluster ac : acg.getActivityClusterMap().values()) {
            JSONObject acJSON = new JSONObject();

            acJSON.put("activity-cluster-id", String.valueOf(ac.getId()));

            JSONArray activitiesJSON = new JSONArray();
            for (ActivityId aid : ac.getActivityMap().keySet()) {
                activitiesJSON.put(aid);
            }
            acJSON.put("activities", activitiesJSON);

            JSONArray dependenciesJSON = new JSONArray();
            for (ActivityCluster dependency : ac.getDependencies()) {
                dependenciesJSON.put(String.valueOf(dependency.getId()));
            }
            acJSON.put("dependencies", dependenciesJSON);

            ActivityClusterPlan acp = activityClusterPlanMap.get(ac.getId());
            if (acp == null) {
                acJSON.put("plan", (Object) null);
            } else {
                JSONObject planJSON = new JSONObject();

                JSONArray acTasks = new JSONArray();
                for (Map.Entry<ActivityId, ActivityPlan> e : acp.getActivityPlanMap().entrySet()) {
                    ActivityPlan acPlan = e.getValue();
                    JSONObject entry = new JSONObject();
                    entry.put("activity-id", e.getKey().toString());

                    ActivityPartitionDetails apd = acPlan.getActivityPartitionDetails();
                    entry.put("partition-count", apd.getPartitionCount());

                    JSONArray inPartCountsJSON = new JSONArray();
                    int[] inPartCounts = apd.getInputPartitionCounts();
                    if (inPartCounts != null) {
                        for (int i : inPartCounts) {
                            inPartCountsJSON.put(i);
                        }
                    }
                    entry.put("input-partition-counts", inPartCountsJSON);

                    JSONArray outPartCountsJSON = new JSONArray();
                    int[] outPartCounts = apd.getOutputPartitionCounts();
                    if (outPartCounts != null) {
                        for (int o : outPartCounts) {
                            outPartCountsJSON.put(o);
                        }
                    }
                    entry.put("output-partition-counts", outPartCountsJSON);

                    JSONArray tasks = new JSONArray();
                    for (Task t : acPlan.getTasks()) {
                        JSONObject task = new JSONObject();

                        task.put("task-id", t.getTaskId().toString());

                        JSONArray dependentTasksJSON = new JSONArray();
                        for (TaskId dependent : t.getDependents()) {
                            dependentTasksJSON.put(dependent.toString());
                        }
                        task.put("dependents", dependentTasksJSON);

                        JSONArray dependencyTasksJSON = new JSONArray();
                        for (TaskId dependency : t.getDependencies()) {
                            dependencyTasksJSON.put(dependency.toString());
                        }
                        task.put("dependencies", dependencyTasksJSON);

                        tasks.put(task);
                    }
                    entry.put("tasks", tasks);

                    acTasks.put(entry);
                }
                planJSON.put("activities", acTasks);

                JSONArray tClusters = new JSONArray();
                for (TaskCluster tc : acp.getTaskClusters()) {
                    JSONObject c = new JSONObject();
                    c.put("task-cluster-id", String.valueOf(tc.getTaskClusterId()));

                    JSONArray tasks = new JSONArray();
                    for (Task t : tc.getTasks()) {
                        tasks.put(t.getTaskId().toString());
                    }
                    c.put("tasks", tasks);

                    JSONArray prodParts = new JSONArray();
                    for (PartitionId p : tc.getProducedPartitions()) {
                        prodParts.put(p.toString());
                    }
                    c.put("produced-partitions", prodParts);

                    JSONArray reqdParts = new JSONArray();
                    for (PartitionId p : tc.getRequiredPartitions()) {
                        reqdParts.put(p.toString());
                    }
                    c.put("required-partitions", reqdParts);

                    JSONArray attempts = new JSONArray();
                    List<TaskClusterAttempt> tcAttempts = tc.getAttempts();
                    if (tcAttempts != null) {
                        for (TaskClusterAttempt tca : tcAttempts) {
                            JSONObject attempt = new JSONObject();
                            attempt.put("attempt", tca.getAttempt());
                            attempt.put("status", tca.getStatus());
                            attempt.put("start-time", tca.getStartTime());
                            attempt.put("end-time", tca.getEndTime());

                            JSONArray taskAttempts = new JSONArray();
                            for (TaskAttempt ta : tca.getTaskAttempts().values()) {
                                JSONObject taskAttempt = new JSONObject();
                                taskAttempt.put("task-id", ta.getTaskAttemptId().getTaskId());
                                taskAttempt.put("task-attempt-id", ta.getTaskAttemptId());
                                taskAttempt.put("status", ta.getStatus());
                                taskAttempt.put("node-id", ta.getNodeId());
                                taskAttempt.put("start-time", ta.getStartTime());
                                taskAttempt.put("end-time", ta.getEndTime());
                                String failureDetails = ta.getFailureDetails();
                                if (failureDetails != null) {
                                    taskAttempt.put("failure-details", failureDetails);
                                }
                                taskAttempts.put(taskAttempt);
                            }
                            attempt.put("task-attempts", taskAttempts);

                            attempts.put(attempt);
                        }
                    }
                    c.put("attempts", attempts);

                    tClusters.put(c);
                }
                planJSON.put("task-clusters", tClusters);

                acJSON.put("plan", planJSON);
            }
            aClusters.put(acJSON);
        }
        result.put("activity-clusters", aClusters);

        result.put("profile", profile.toJSON());

        return result;
    }
}