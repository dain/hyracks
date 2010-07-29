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
package edu.uci.ics.hyracks.config;

import org.kohsuke.args4j.Option;

public class CCConfig {
    @Option(name = "-port", usage = "Sets the port to listen for connections from node controllers (default 1099)")
    public int port = 1099;

    @Option(name = "-http-port", usage = "Sets the http port for the admin console")
    public int httpPort;

    @Option(name = "-heartbeat-period", usage = "Sets the time duration between two heartbeats from each node controller in milliseconds (default: 10000)")
    public int heartbeatPeriod = 10000;

    @Option(name = "-max-heartbeat-lapse-periods", usage = "Sets the maximum number of missed heartbeats before a node is marked as dead (default: 5)")
    public int maxHeartbeatLapsePeriods = 5;

    @Option(name = "-use-jol", usage = "Forces Hyracks to use the JOL based scheduler (default: false)")
    public boolean useJOL = false;
}