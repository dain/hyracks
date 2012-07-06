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
package edu.uci.ics.hyracks.control.nc.work;

import java.util.Map;

import edu.uci.ics.hyracks.control.common.application.ApplicationContext;
import edu.uci.ics.hyracks.control.common.application.ApplicationStatus;
import edu.uci.ics.hyracks.control.common.work.AbstractWork;
import edu.uci.ics.hyracks.control.nc.NodeControllerService;
import edu.uci.ics.hyracks.control.nc.application.NCApplicationContext;

public class DestroyApplicationWork extends AbstractWork {
    private final NodeControllerService ncs;

    private final String appName;

    public DestroyApplicationWork(NodeControllerService ncs, String appName) {
        this.ncs = ncs;
        this.appName = appName;
    }

    @Override
    public void run() {
        try {
            Map<String, NCApplicationContext> applications = ncs.getApplications();
            ApplicationContext appCtx = applications.remove(appName);
            if (appCtx != null) {
                appCtx.deinitialize();
            }
            ncs.getClusterController().notifyApplicationStateChange(ncs.getId(), appName,
                    ApplicationStatus.DEINITIALIZED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}