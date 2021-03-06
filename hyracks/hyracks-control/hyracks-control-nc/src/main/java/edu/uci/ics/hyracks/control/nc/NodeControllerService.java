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
package edu.uci.ics.hyracks.control.nc;

import java.io.File;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.uci.ics.hyracks.api.context.IHyracksRootContext;
import edu.uci.ics.hyracks.api.io.IODeviceHandle;
import edu.uci.ics.hyracks.api.job.JobId;
import edu.uci.ics.hyracks.control.common.AbstractRemoteService;
import edu.uci.ics.hyracks.control.common.base.IClusterController;
import edu.uci.ics.hyracks.control.common.context.ServerContext;
import edu.uci.ics.hyracks.control.common.controllers.NCConfig;
import edu.uci.ics.hyracks.control.common.controllers.NodeParameters;
import edu.uci.ics.hyracks.control.common.controllers.NodeRegistration;
import edu.uci.ics.hyracks.control.common.heartbeat.HeartbeatData;
import edu.uci.ics.hyracks.control.common.heartbeat.HeartbeatSchema;
import edu.uci.ics.hyracks.control.common.ipc.CCNCFunctions;
import edu.uci.ics.hyracks.control.common.ipc.ClusterControllerRemoteProxy;
import edu.uci.ics.hyracks.control.common.job.profiling.om.JobProfile;
import edu.uci.ics.hyracks.control.common.work.FutureValue;
import edu.uci.ics.hyracks.control.common.work.WorkQueue;
import edu.uci.ics.hyracks.control.nc.application.NCApplicationContext;
import edu.uci.ics.hyracks.control.nc.io.IOManager;
import edu.uci.ics.hyracks.control.nc.net.NetworkManager;
import edu.uci.ics.hyracks.control.nc.partitions.PartitionManager;
import edu.uci.ics.hyracks.control.nc.runtime.RootHyracksContext;
import edu.uci.ics.hyracks.control.nc.work.AbortTasksWork;
import edu.uci.ics.hyracks.control.nc.work.BuildJobProfilesWork;
import edu.uci.ics.hyracks.control.nc.work.CleanupJobletWork;
import edu.uci.ics.hyracks.control.nc.work.CreateApplicationWork;
import edu.uci.ics.hyracks.control.nc.work.DestroyApplicationWork;
import edu.uci.ics.hyracks.control.nc.work.ReportPartitionAvailabilityWork;
import edu.uci.ics.hyracks.control.nc.work.StartTasksWork;
import edu.uci.ics.hyracks.ipc.api.IIPCHandle;
import edu.uci.ics.hyracks.ipc.api.IIPCI;
import edu.uci.ics.hyracks.ipc.api.IPCPerformanceCounters;
import edu.uci.ics.hyracks.ipc.impl.IPCSystem;
import edu.uci.ics.hyracks.net.protocols.muxdemux.MuxDemuxPerformanceCounters;

public class NodeControllerService extends AbstractRemoteService {
    private static Logger LOGGER = Logger.getLogger(NodeControllerService.class.getName());

    private NCConfig ncConfig;

    private final String id;

    private final IHyracksRootContext ctx;

    private final IPCSystem ipc;

    private final PartitionManager partitionManager;

    private final NetworkManager netManager;

    private final WorkQueue queue;

    private final Timer timer;

    private boolean registrationPending;

    private Exception registrationException;

    private IClusterController ccs;

    private final Map<JobId, Joblet> jobletMap;

    private final Executor executor;

    private NodeParameters nodeParameters;

    private HeartbeatTask heartbeatTask;

    private final ServerContext serverCtx;

    private final Map<String, NCApplicationContext> applications;

    private final MemoryMXBean memoryMXBean;

    private final List<GarbageCollectorMXBean> gcMXBeans;

    private final ThreadMXBean threadMXBean;

    private final RuntimeMXBean runtimeMXBean;

    private final OperatingSystemMXBean osMXBean;

    public NodeControllerService(NCConfig ncConfig) throws Exception {
        this.ncConfig = ncConfig;
        id = ncConfig.nodeId;
        executor = Executors.newCachedThreadPool();
        NodeControllerIPCI ipci = new NodeControllerIPCI();
        ipc = new IPCSystem(new InetSocketAddress(ncConfig.clusterNetIPAddress, 0), ipci,
                new CCNCFunctions.SerializerDeserializer());
        this.ctx = new RootHyracksContext(ncConfig.frameSize, new IOManager(getDevices(ncConfig.ioDevices), executor));
        if (id == null) {
            throw new Exception("id not set");
        }
        partitionManager = new PartitionManager(this);
        netManager = new NetworkManager(ctx, getIpAddress(ncConfig), partitionManager, ncConfig.nNetThreads);

        queue = new WorkQueue();
        jobletMap = new Hashtable<JobId, Joblet>();
        timer = new Timer(true);
        serverCtx = new ServerContext(ServerContext.ServerType.NODE_CONTROLLER, new File(new File(
                NodeControllerService.class.getName()), id));
        applications = new Hashtable<String, NCApplicationContext>();
        memoryMXBean = ManagementFactory.getMemoryMXBean();
        gcMXBeans = ManagementFactory.getGarbageCollectorMXBeans();
        threadMXBean = ManagementFactory.getThreadMXBean();
        runtimeMXBean = ManagementFactory.getRuntimeMXBean();
        osMXBean = ManagementFactory.getOperatingSystemMXBean();
        registrationPending = true;
    }

    public IHyracksRootContext getRootContext() {
        return ctx;
    }

    private static List<IODeviceHandle> getDevices(String ioDevices) {
        List<IODeviceHandle> devices = new ArrayList<IODeviceHandle>();
        StringTokenizer tok = new StringTokenizer(ioDevices, ",");
        while (tok.hasMoreElements()) {
            String devPath = tok.nextToken().trim();
            devices.add(new IODeviceHandle(new File(devPath), "."));
        }
        return devices;
    }

    private synchronized void setNodeRegistrationResult(NodeParameters parameters, Exception exception) {
        this.nodeParameters = parameters;
        this.registrationException = exception;
        this.registrationPending = false;
        notifyAll();
    }

    @Override
    public void start() throws Exception {
        LOGGER.log(Level.INFO, "Starting NodeControllerService");
        ipc.start();
        netManager.start();
        IIPCHandle ccIPCHandle = ipc.getHandle(new InetSocketAddress(ncConfig.ccHost, ncConfig.ccPort));
        this.ccs = new ClusterControllerRemoteProxy(ccIPCHandle);
        HeartbeatSchema.GarbageCollectorInfo[] gcInfos = new HeartbeatSchema.GarbageCollectorInfo[gcMXBeans.size()];
        for (int i = 0; i < gcInfos.length; ++i) {
            gcInfos[i] = new HeartbeatSchema.GarbageCollectorInfo(gcMXBeans.get(i).getName());
        }
        HeartbeatSchema hbSchema = new HeartbeatSchema(gcInfos);
        ccs.registerNode(new NodeRegistration(ipc.getSocketAddress(), id, ncConfig, netManager.getNetworkAddress(),
                osMXBean.getName(), osMXBean.getArch(), osMXBean.getVersion(), osMXBean.getAvailableProcessors(),
                runtimeMXBean.getVmName(), runtimeMXBean.getVmVersion(), runtimeMXBean.getVmVendor(), runtimeMXBean
                        .getClassPath(), runtimeMXBean.getLibraryPath(), runtimeMXBean.getBootClassPath(),
                runtimeMXBean.getInputArguments(), runtimeMXBean.getSystemProperties(), hbSchema));

        synchronized (this) {
            while (registrationPending) {
                wait();
            }
        }
        if (registrationException != null) {
            throw registrationException;
        }

        queue.start();

        heartbeatTask = new HeartbeatTask(ccs);

        // Schedule heartbeat generator.
        timer.schedule(heartbeatTask, 0, nodeParameters.getHeartbeatPeriod());

        if (nodeParameters.getProfileDumpPeriod() > 0) {
            // Schedule profile dump generator.
            timer.schedule(new ProfileDumpTask(ccs), 0, nodeParameters.getProfileDumpPeriod());
        }

        LOGGER.log(Level.INFO, "Started NodeControllerService");
    }

    @Override
    public void stop() throws Exception {
        LOGGER.log(Level.INFO, "Stopping NodeControllerService");
        partitionManager.close();
        heartbeatTask.cancel();
        netManager.stop();
        queue.stop();
        LOGGER.log(Level.INFO, "Stopped NodeControllerService");
    }

    public String getId() {
        return id;
    }

    public ServerContext getServerContext() {
        return serverCtx;
    }

    public Map<String, NCApplicationContext> getApplications() {
        return applications;
    }

    public Map<JobId, Joblet> getJobletMap() {
        return jobletMap;
    }

    public NetworkManager getNetworkManager() {
        return netManager;
    }

    public PartitionManager getPartitionManager() {
        return partitionManager;
    }

    public IClusterController getClusterController() {
        return ccs;
    }

    public NodeParameters getNodeParameters() {
        return nodeParameters;
    }

    public Executor getExecutor() {
        return executor;
    }

    public NCConfig getConfiguration() throws Exception {
        return ncConfig;
    }

    public WorkQueue getWorkQueue() {
        return queue;
    }

    private static InetAddress getIpAddress(NCConfig ncConfig) throws Exception {
        String ipaddrStr = ncConfig.dataIPAddress;
        ipaddrStr = ipaddrStr.trim();
        Pattern pattern = Pattern.compile("(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})\\.(\\d{1,3})");
        Matcher m = pattern.matcher(ipaddrStr);
        if (!m.matches()) {
            throw new Exception(MessageFormat.format(
                    "Connection Manager IP Address String %s does is not a valid IP Address.", ipaddrStr));
        }
        byte[] ipBytes = new byte[4];
        ipBytes[0] = (byte) Integer.parseInt(m.group(1));
        ipBytes[1] = (byte) Integer.parseInt(m.group(2));
        ipBytes[2] = (byte) Integer.parseInt(m.group(3));
        ipBytes[3] = (byte) Integer.parseInt(m.group(4));
        return InetAddress.getByAddress(ipBytes);
    }

    private class HeartbeatTask extends TimerTask {
        private IClusterController cc;

        private final HeartbeatData hbData;

        public HeartbeatTask(IClusterController cc) {
            this.cc = cc;
            hbData = new HeartbeatData();
            hbData.gcCollectionCounts = new long[gcMXBeans.size()];
            hbData.gcCollectionTimes = new long[gcMXBeans.size()];
        }

        @Override
        public void run() {
            MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
            hbData.heapInitSize = heapUsage.getInit();
            hbData.heapUsedSize = heapUsage.getUsed();
            hbData.heapCommittedSize = heapUsage.getCommitted();
            hbData.heapMaxSize = heapUsage.getMax();
            MemoryUsage nonheapUsage = memoryMXBean.getNonHeapMemoryUsage();
            hbData.nonheapInitSize = nonheapUsage.getInit();
            hbData.nonheapUsedSize = nonheapUsage.getUsed();
            hbData.nonheapCommittedSize = nonheapUsage.getCommitted();
            hbData.nonheapMaxSize = nonheapUsage.getMax();
            hbData.threadCount = threadMXBean.getThreadCount();
            hbData.peakThreadCount = threadMXBean.getPeakThreadCount();
            hbData.totalStartedThreadCount = threadMXBean.getTotalStartedThreadCount();
            hbData.systemLoadAverage = osMXBean.getSystemLoadAverage();
            int gcN = gcMXBeans.size();
            for (int i = 0; i < gcN; ++i) {
                GarbageCollectorMXBean gcMXBean = gcMXBeans.get(i);
                hbData.gcCollectionCounts[i] = gcMXBean.getCollectionCount();
                hbData.gcCollectionTimes[i] = gcMXBean.getCollectionTime();
            }

            MuxDemuxPerformanceCounters netPC = netManager.getPerformanceCounters();
            hbData.netPayloadBytesRead = netPC.getPayloadBytesRead();
            hbData.netPayloadBytesWritten = netPC.getPayloadBytesWritten();
            hbData.netSignalingBytesRead = netPC.getSignalingBytesRead();
            hbData.netSignalingBytesWritten = netPC.getSignalingBytesWritten();

            IPCPerformanceCounters ipcPC = ipc.getPerformanceCounters();
            hbData.ipcMessagesSent = ipcPC.getMessageSentCount();
            hbData.ipcMessageBytesSent = ipcPC.getMessageBytesSent();
            hbData.ipcMessagesReceived = ipcPC.getMessageReceivedCount();
            hbData.ipcMessageBytesReceived = ipcPC.getMessageBytesReceived();

            try {
                cc.nodeHeartbeat(id, hbData);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private class ProfileDumpTask extends TimerTask {
        private IClusterController cc;

        public ProfileDumpTask(IClusterController cc) {
            this.cc = cc;
        }

        @Override
        public void run() {
            try {
                FutureValue<List<JobProfile>> fv = new FutureValue<List<JobProfile>>();
                BuildJobProfilesWork bjpw = new BuildJobProfilesWork(NodeControllerService.this, fv);
                queue.scheduleAndSync(bjpw);
                List<JobProfile> profiles = fv.get();
                if (!profiles.isEmpty()) {
                    cc.reportProfile(id, profiles);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private final class NodeControllerIPCI implements IIPCI {
        @Override
        public void deliverIncomingMessage(IIPCHandle handle, long mid, long rmid, Object payload, Exception exception) {
            CCNCFunctions.Function fn = (CCNCFunctions.Function) payload;
            switch (fn.getFunctionId()) {
                case START_TASKS: {
                    CCNCFunctions.StartTasksFunction stf = (CCNCFunctions.StartTasksFunction) fn;
                    queue.schedule(new StartTasksWork(NodeControllerService.this, stf.getAppName(), stf.getJobId(), stf
                            .getPlanBytes(), stf.getTaskDescriptors(), stf.getConnectorPolicies()));
                    return;
                }

                case ABORT_TASKS: {
                    CCNCFunctions.AbortTasksFunction atf = (CCNCFunctions.AbortTasksFunction) fn;
                    queue.schedule(new AbortTasksWork(NodeControllerService.this, atf.getJobId(), atf.getTasks()));
                    return;
                }

                case CLEANUP_JOBLET: {
                    CCNCFunctions.CleanupJobletFunction cjf = (CCNCFunctions.CleanupJobletFunction) fn;
                    queue.schedule(new CleanupJobletWork(NodeControllerService.this, cjf.getJobId(), cjf.getStatus()));
                    return;
                }

                case CREATE_APPLICATION: {
                    CCNCFunctions.CreateApplicationFunction caf = (CCNCFunctions.CreateApplicationFunction) fn;
                    queue.schedule(new CreateApplicationWork(NodeControllerService.this, caf.getAppName(), caf
                            .isDeployHar(), caf.getSerializedDistributedState()));
                    return;
                }

                case DESTROY_APPLICATION: {
                    CCNCFunctions.DestroyApplicationFunction daf = (CCNCFunctions.DestroyApplicationFunction) fn;
                    queue.schedule(new DestroyApplicationWork(NodeControllerService.this, daf.getAppName()));
                    return;
                }

                case REPORT_PARTITION_AVAILABILITY: {
                    CCNCFunctions.ReportPartitionAvailabilityFunction rpaf = (CCNCFunctions.ReportPartitionAvailabilityFunction) fn;
                    queue.schedule(new ReportPartitionAvailabilityWork(NodeControllerService.this, rpaf
                            .getPartitionId(), rpaf.getNetworkAddress()));
                    return;
                }

                case NODE_REGISTRATION_RESULT: {
                    CCNCFunctions.NodeRegistrationResult nrrf = (CCNCFunctions.NodeRegistrationResult) fn;
                    setNodeRegistrationResult(nrrf.getNodeParameters(), nrrf.getException());
                    return;
                }
            }
            throw new IllegalArgumentException("Unknown function: " + fn.getFunctionId());

        }
    }
}