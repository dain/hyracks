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
package edu.uci.ics.hyracks.server.drivers;

import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import edu.uci.ics.hyracks.control.common.controllers.CCConfig;
import edu.uci.ics.hyracks.control.common.controllers.NCConfig;
import edu.uci.ics.hyracks.server.process.HyracksCCProcess;
import edu.uci.ics.hyracks.server.process.HyracksNCProcess;

public class VirtualClusterDriver {
    private static class Options {
        @Option(name = "-n", required = false, usage = "Number of node controllers (default: 2)")
        public int n = 2;

        @Option(name = "-cc-port", required = false, usage = "CC Port (default: 1099)")
        public int ccPort = 1099;

        @Option(name = "-cc-http-port", required = false, usage = "CC Port (default: 0)")
        public int ccHttpPort = 0;
    }

    public static void main(String[] args) throws Exception {
        Options options = new Options();
        CmdLineParser cp = new CmdLineParser(options);
        try {
            cp.parseArgument(args);
        } catch (Exception e) {
            System.err.println(e.getMessage());
            cp.printUsage(System.err);
            return;
        }

        CCConfig ccConfig = new CCConfig();
        ccConfig.port = options.ccPort;
        ccConfig.httpPort = options.ccHttpPort;
        HyracksCCProcess ccp = new HyracksCCProcess(ccConfig);
        ccp.start();

        Thread.sleep(2000);

        HyracksNCProcess ncps[] = new HyracksNCProcess[options.n];
        for (int i = 0; i < options.n; ++i) {
            NCConfig ncConfig = new NCConfig();
            ncConfig.ccHost = "localhost";
            ncConfig.nodeId = "nc" + i;
            ncConfig.dataIPAddress = "127.0.0.1";
            ncps[i] = new HyracksNCProcess(ncConfig);
            ncps[i].start();
        }

        while (true) {
            Thread.sleep(10000);
        }
    }
}