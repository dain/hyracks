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
package edu.uci.ics.hyracks.yarn.am;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import org.apache.commons.io.FileUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.yarn.api.protocolrecords.AllocateRequest;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterRequest;
import org.apache.hadoop.yarn.api.protocolrecords.RegisterApplicationMasterResponse;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnRemoteException;
import org.apache.hadoop.yarn.util.Records;
import org.kohsuke.args4j.CmdLineParser;

import edu.uci.ics.hyracks.yarn.common.protocols.amrm.AMRMConnection;

public class HyracksYarnApplicationMaster {
    private final Options options;

    private final Timer timer;

    private YarnConfiguration config;

    private AMRMConnection amrmc;

    private RegisterApplicationMasterResponse registration;

    private HyracksYarnApplicationMaster(Options options) {
        this.options = options;
        timer = new Timer(true);
    }

    private void run() throws Exception {
        Configuration conf = new Configuration();
        config = new YarnConfiguration(conf);
        amrmc = new AMRMConnection(config);

        performRegistration();

        setupHeartbeats();

        parseManifest();

        while (true) {
            Thread.sleep(1000);
        }
    }

    private void parseManifest() throws Exception {
        String str = FileUtils.readFileToString(new File("manifest.xml"));
    }

    private void setupHeartbeats() {
        long heartbeatInterval = config.getLong(YarnConfiguration.RM_AM_EXPIRY_INTERVAL_MS,
                YarnConfiguration.DEFAULT_RM_AM_EXPIRY_INTERVAL_MS);
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                AllocateRequest hb = Records.newRecord(AllocateRequest.class);
                hb.setApplicationAttemptId(amrmc.getApplicationAttemptId());
                hb.setProgress(0);
                try {
                    amrmc.getAMRMProtocol().allocate(hb);
                } catch (YarnRemoteException e) {
                    e.printStackTrace();
                }
            }
        }, 0, heartbeatInterval);
    }

    private void performRegistration() throws YarnRemoteException {
        RegisterApplicationMasterRequest appMasterRequest = Records.newRecord(RegisterApplicationMasterRequest.class);
        appMasterRequest.setApplicationAttemptId(amrmc.getApplicationAttemptId());

        registration = amrmc.getAMRMProtocol().registerApplicationMaster(appMasterRequest);
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        CmdLineParser parser = new CmdLineParser(options);
        try {
            parser.parseArgument(args);
        } catch (Exception e) {
            parser.printUsage(System.err);
            return;
        }
        new HyracksYarnApplicationMaster(options).run();
    }

    private static class Options {
    }
}