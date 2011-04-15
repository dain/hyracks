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
package edu.uci.ics.hyracks.control.cc.scheduler;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import edu.uci.ics.hyracks.api.constraints.expressions.LValueConstraintExpression;
import edu.uci.ics.hyracks.api.constraints.expressions.PartitionLocationExpression;
import edu.uci.ics.hyracks.api.dataflow.OperatorDescriptorId;
import edu.uci.ics.hyracks.api.dataflow.TaskAttemptId;
import edu.uci.ics.hyracks.api.dataflow.TaskId;
import edu.uci.ics.hyracks.api.exceptions.HyracksException;
import edu.uci.ics.hyracks.api.job.JobActivityGraph;
import edu.uci.ics.hyracks.api.util.JavaSerializationUtils;
import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.cc.NodeControllerState;
import edu.uci.ics.hyracks.control.cc.job.ActivityCluster;
import edu.uci.ics.hyracks.control.cc.job.JobRun;
import edu.uci.ics.hyracks.control.cc.job.Task;
import edu.uci.ics.hyracks.control.cc.job.TaskAttempt;
import edu.uci.ics.hyracks.control.cc.job.TaskCluster;
import edu.uci.ics.hyracks.control.cc.job.TaskClusterAttempt;
import edu.uci.ics.hyracks.control.common.job.TaskAttemptDescriptor;

public class DefaultActivityClusterStateMachine implements IActivityClusterStateMachine {
    private static final Logger LOGGER = Logger.getLogger(DefaultActivityClusterStateMachine.class.getName());

    private final ClusterControllerService ccs;

    private final DefaultJobRunStateMachine jsm;

    private final ActivityCluster ac;

    private final Set<TaskCluster> inProgressTaskClusters;

    public DefaultActivityClusterStateMachine(ClusterControllerService ccs, DefaultJobRunStateMachine jsm,
            ActivityCluster ac) {
        this.ccs = ccs;
        this.jsm = jsm;
        this.ac = ac;
        inProgressTaskClusters = new HashSet<TaskCluster>();
    }

    @Override
    public void schedule() throws HyracksException {
        startRunnableTaskClusters();
    }

    private void assignTaskLocations(TaskCluster tc, Map<String, List<TaskAttemptDescriptor>> taskAttemptMap)
            throws HyracksException {
        Task[] tasks = tc.getTasks();
        List<TaskClusterAttempt> tcAttempts = tc.getAttempts();
        int attempts = tcAttempts.size();
        TaskClusterAttempt tcAttempt = new TaskClusterAttempt(tc, attempts);
        TaskAttempt[] taskAttempts = new TaskAttempt[tasks.length];
        Map<TaskId, LValueConstraintExpression> locationMap = new HashMap<TaskId, LValueConstraintExpression>();
        for (int i = 0; i < tasks.length; ++i) {
            Task ts = tasks[i];
            TaskId tid = ts.getTaskId();
            TaskAttempt taskAttempt = new TaskAttempt(tcAttempt, new TaskAttemptId(new TaskId(tid.getActivityId(),
                    tid.getPartition()), attempts), ts);
            taskAttempt.setStatus(TaskAttempt.TaskStatus.INITIALIZED, null);
            locationMap.put(tid,
                    new PartitionLocationExpression(tid.getActivityId().getOperatorDescriptorId(), tid.getPartition()));
            taskAttempts[i] = taskAttempt;
        }
        tcAttempt.setTaskAttempts(taskAttempts);
        PartitionConstraintSolver solver = jsm.getSolver();
        solver.solve(locationMap.values());
        Map<OperatorDescriptorId, String> operatorLocationAssignmentMap = jsm.getOperatorLocationAssignmentMap();
        for (int i = 0; i < tasks.length; ++i) {
            Task ts = tasks[i];
            TaskId tid = ts.getTaskId();
            TaskAttempt taskAttempt = taskAttempts[i];
            String nodeId = operatorLocationAssignmentMap.get(tid.getActivityId().getOperatorDescriptorId());
            if (nodeId == null) {
                LValueConstraintExpression pLocationExpr = locationMap.get(tid);
                Object location = solver.getValue(pLocationExpr);
                Set<String> liveNodes = ccs.getNodeMap().keySet();
                if (location == null) {
                    // pick any
                    nodeId = liveNodes.toArray(new String[liveNodes.size()])[Math.abs(new Random().nextInt())
                            % liveNodes.size()];
                } else if (location instanceof String) {
                    nodeId = (String) location;
                    if (!liveNodes.contains(nodeId)) {
                        throw new HyracksException("Node " + nodeId + " not live");
                    }
                } else if (location instanceof String[]) {
                    for (String choice : (String[]) location) {
                        if (liveNodes.contains(choice)) {
                            nodeId = choice;
                            break;
                        }
                    }
                    if (nodeId == null) {
                        throw new HyracksException("No satisfiable location found for "
                                + taskAttempt.getTaskAttemptId());
                    }
                } else {
                    throw new HyracksException("Unknown type of value for " + pLocationExpr + ": " + location + "("
                            + location.getClass() + ")");
                }
                operatorLocationAssignmentMap.put(tid.getActivityId().getOperatorDescriptorId(), nodeId);
            }
            taskAttempt.setNodeId(nodeId);
            taskAttempt.setStatus(TaskAttempt.TaskStatus.RUNNING, null);
            List<TaskAttemptDescriptor> tads = taskAttemptMap.get(nodeId);
            if (tads == null) {
                tads = new ArrayList<TaskAttemptDescriptor>();
                taskAttemptMap.put(nodeId, tads);
            }
            ActivityPartitionDetails apd = ts.getActivityPartitionDetails();
            tads.add(new TaskAttemptDescriptor(taskAttempt.getTaskAttemptId(), apd.getPartitionCount(), apd
                    .getInputPartitionCounts(), apd.getOutputPartitionCounts()));
        }
        tcAttempt.initializePendingTaskCounter();
        tcAttempts.add(tcAttempt);
        tcAttempt.setStatus(TaskClusterAttempt.TaskClusterStatus.RUNNING);
        inProgressTaskClusters.add(tc);
    }

    @Override
    public void notifyTaskComplete(TaskAttempt ta) throws HyracksException {
        TaskAttemptId taId = ta.getTaskAttemptId();
        TaskCluster tc = ta.getTaskState().getTaskCluster();
        List<TaskClusterAttempt> tcAttempts = tc.getAttempts();
        int lastAttempt = tcAttempts.size() - 1;
        if (taId.getAttempt() == lastAttempt) {
            TaskClusterAttempt tcAttempt = tcAttempts.get(lastAttempt);
            TaskAttempt.TaskStatus taStatus = ta.getStatus();
            if (taStatus == TaskAttempt.TaskStatus.RUNNING) {
                ta.setStatus(TaskAttempt.TaskStatus.COMPLETED, null);
                if (tcAttempt.decrementPendingTasksCounter() == 0) {
                    tcAttempt.setStatus(TaskClusterAttempt.TaskClusterStatus.COMPLETED);
                    inProgressTaskClusters.remove(tc);
                    startRunnableTaskClusters();
                }
            } else {
                LOGGER.warning("Spurious task complete notification: " + taId + " Current state = " + taStatus);
            }
        } else {
            LOGGER.warning("Ignoring task complete notification: " + taId + " -- Current last attempt = " + lastAttempt);
        }
    }

    private void startRunnableTaskClusters() throws HyracksException {
        TaskCluster[] taskClusters = ac.getTaskClusters();

        Map<String, List<TaskAttemptDescriptor>> taskAttemptMap = new HashMap<String, List<TaskAttemptDescriptor>>();
        for (TaskCluster tc : taskClusters) {
            Set<TaskCluster> dependencies = tc.getDependencies();
            List<TaskClusterAttempt> attempts = tc.getAttempts();
            if (!attempts.isEmpty()) {
                TaskClusterAttempt lastAttempt = attempts.get(attempts.size() - 1);
                if (lastAttempt.getStatus() == TaskClusterAttempt.TaskClusterStatus.COMPLETED
                        || lastAttempt.getStatus() == TaskClusterAttempt.TaskClusterStatus.RUNNING) {
                    continue;
                }
            }
            boolean runnable = true;
            for (TaskCluster depTC : dependencies) {
                List<TaskClusterAttempt> tcAttempts = depTC.getAttempts();
                if (tcAttempts.isEmpty()) {
                    runnable = false;
                    break;
                }
                TaskClusterAttempt tcAttempt = tcAttempts.get(tcAttempts.size() - 1);
                if (tcAttempt.getStatus() != TaskClusterAttempt.TaskClusterStatus.COMPLETED) {
                    runnable = false;
                    break;
                }
            }
            if (runnable) {
                if (LOGGER.isLoggable(Level.INFO)) {
                    LOGGER.info("Found runnable TC: " + Arrays.toString(tc.getTasks()));
                    LOGGER.info("Attempts so far:" + attempts.size());
                    for (TaskClusterAttempt tcAttempt : attempts) {
                        LOGGER.info("Status: " + tcAttempt.getStatus());
                    }
                }
                assignTaskLocations(tc, taskAttemptMap);
            }
        }

        if (taskAttemptMap.isEmpty()) {
            if (inProgressTaskClusters.isEmpty()) {
                ac.notifyActivityClusterComplete();
            }
            return;
        }

        startTasks(taskAttemptMap);
    }

    private void startTasks(Map<String, List<TaskAttemptDescriptor>> taskAttemptMap) {
        Executor executor = ccs.getExecutor();
        JobRun jobRun = ac.getJobRun();
        final UUID jobId = jobRun.getJobId();
        final JobActivityGraph jag = jobRun.getJobActivityGraph();
        final String appName = jag.getApplicationName();
        for (Map.Entry<String, List<TaskAttemptDescriptor>> e : taskAttemptMap.entrySet()) {
            String nodeId = e.getKey();
            final List<TaskAttemptDescriptor> taskDescriptors = e.getValue();
            final NodeControllerState node = ccs.getNodeMap().get(nodeId);
            if (node != null) {
                jobRun.getParticipatingNodeIds().add(nodeId);
                executor.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            node.getNodeController().startTasks(appName, jobId, JavaSerializationUtils.serialize(jag),
                                    taskDescriptors);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }
    }

    private void abortTaskCluster(TaskClusterAttempt tcAttempt) {
        Map<String, List<TaskAttemptId>> abortTaskAttemptMap = new HashMap<String, List<TaskAttemptId>>();
        for (TaskAttempt ta2 : tcAttempt.getTaskAttempts()) {
            if (ta2.getStatus() == TaskAttempt.TaskStatus.RUNNING) {
                ta2.setStatus(TaskAttempt.TaskStatus.ABORTED, null);
                List<TaskAttemptId> abortTaskAttempts = abortTaskAttemptMap.get(ta2.getNodeId());
                if (abortTaskAttempts == null) {
                    abortTaskAttempts = new ArrayList<TaskAttemptId>();
                    abortTaskAttemptMap.put(ta2.getNodeId(), abortTaskAttempts);
                }
                abortTaskAttempts.add(ta2.getTaskAttemptId());
            }
        }
        JobRun jobRun = ac.getJobRun();
        final UUID jobId = jobRun.getJobId();
        for (Map.Entry<String, List<TaskAttemptId>> e : abortTaskAttemptMap.entrySet()) {
            final NodeControllerState node = ccs.getNodeMap().get(e.getKey());
            final List<TaskAttemptId> abortTaskAttempts = e.getValue();
            if (node != null) {
                LOGGER.info("Aborting: " + abortTaskAttempts + " at " + e.getKey());
                ccs.getExecutor().execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            node.getNodeController().abortTasks(jobId, abortTaskAttempts);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }

    }

    @Override
    public void notifyTaskFailure(TaskAttempt ta, Exception exception) throws HyracksException {
        TaskAttemptId taId = ta.getTaskAttemptId();
        TaskCluster tc = ta.getTaskState().getTaskCluster();
        List<TaskClusterAttempt> tcAttempts = tc.getAttempts();
        int lastAttempt = tcAttempts.size() - 1;
        if (taId.getAttempt() == lastAttempt) {
            TaskClusterAttempt tcAttempt = tcAttempts.get(lastAttempt);
            TaskAttempt.TaskStatus taStatus = ta.getStatus();
            if (taStatus == TaskAttempt.TaskStatus.RUNNING) {
                ta.setStatus(TaskAttempt.TaskStatus.FAILED, exception);
                abortTaskCluster(tcAttempt);
                tcAttempt.setStatus(TaskClusterAttempt.TaskClusterStatus.FAILED);
                ac.notifyTaskClusterFailure(tcAttempt, exception);
            } else {
                LOGGER.warning("Spurious task complete notification: " + taId + " Current state = " + taStatus);
            }
        } else {
            LOGGER.warning("Ignoring task complete notification: " + taId + " -- Current last attempt = " + lastAttempt);
        }
    }

    @Override
    public void abort() {
        TaskCluster[] taskClusters = ac.getTaskClusters();
        for (TaskCluster tc : taskClusters) {
            List<TaskClusterAttempt> tcAttempts = tc.getAttempts();
            if (!tcAttempts.isEmpty()) {
                TaskClusterAttempt tcAttempt = tcAttempts.get(tcAttempts.size() - 1);
                if (tcAttempt.getStatus() == TaskClusterAttempt.TaskClusterStatus.RUNNING) {
                    abortTaskCluster(tcAttempt);
                    tcAttempt.setStatus(TaskClusterAttempt.TaskClusterStatus.ABORTED);
                }
            }
        }
    }

    @Override
    public void notifyTaskClusterFailure(TaskClusterAttempt tcAttempt, Exception exception) throws HyracksException {
        TaskCluster tc = tcAttempt.getTaskCluster();
        if (tcAttempt.getAttempt() >= ac.getMaxTaskClusterAttempts()) {
            abort();
            ac.getJobRun().getStateMachine().notifyActivityClusterFailure(ac, exception);
            return;
        }
        Map<String, List<TaskAttemptDescriptor>> taskAttemptMap = new HashMap<String, List<TaskAttemptDescriptor>>();
        assignTaskLocations(tc, taskAttemptMap);
        startTasks(taskAttemptMap);
    }
}