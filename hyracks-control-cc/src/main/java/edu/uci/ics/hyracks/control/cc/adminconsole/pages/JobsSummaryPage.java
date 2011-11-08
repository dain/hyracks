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
package edu.uci.ics.hyracks.control.cc.adminconsole.pages;

import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.uci.ics.hyracks.control.cc.ClusterControllerService;
import edu.uci.ics.hyracks.control.cc.web.util.JSONUtils;
import edu.uci.ics.hyracks.control.cc.work.GetJobSummariesJSONWork;

public class JobsSummaryPage extends AbstractPage {
    private static final long serialVersionUID = 1L;

    public JobsSummaryPage() throws Exception {
        ClusterControllerService ccs = getAdminConsoleApplication().getClusterControllerService();

        GetJobSummariesJSONWork gjse = new GetJobSummariesJSONWork(ccs);
        ccs.getWorkQueue().scheduleAndSync(gjse);
        JSONArray summaries = gjse.getSummaries();
        ListView<JSONObject> jobList = new ListView<JSONObject>("job-list", JSONUtils.toList(summaries)) {
            private static final long serialVersionUID = 1L;

            @Override
            protected void populateItem(ListItem<JSONObject> item) {
                JSONObject o = item.getModelObject();
                try {
                    item.add(new Label("job-id", o.getString("job-id")));
                    item.add(new Label("application-name", o.getString("application-name")));
                    item.add(new Label("status", o.getString("status")));
                    item.add(new Label("create-time", o.getString("create-time")));
                    item.add(new Label("start-time", o.getString("start-time")));
                    item.add(new Label("end-time", o.getString("end-time")));
                } catch (JSONException e) {
                    throw new RuntimeException(e);
                }
            }
        };
        add(jobList);
    }
}