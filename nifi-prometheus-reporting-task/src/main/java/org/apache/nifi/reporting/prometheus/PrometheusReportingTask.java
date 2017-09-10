/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi.reporting.prometheus;

import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.Gauge;
import io.prometheus.client.exporter.PushGateway;
import org.apache.nifi.annotation.configuration.DefaultSchedule;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.controller.status.ProcessGroupStatus;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.AbstractReportingTask;
import org.apache.nifi.reporting.ReportingContext;
import org.apache.nifi.scheduling.SchedulingStrategy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

@Tags({"reporting", "prometheus", "metrics"})
@CapabilityDescription("")
@DefaultSchedule(strategy = SchedulingStrategy.TIMER_DRIVEN, period = "1 min")
public class PrometheusReportingTask extends AbstractReportingTask {

    static final PropertyDescriptor METRICS_COLLECTOR_URL = new PropertyDescriptor.Builder()
            .name("Prometheus PushGateway")
            .description("The URL of the Prometheus PushGateway Service")
            .required(true)
            .expressionLanguageSupported(true)
            .defaultValue("http://localhost:9091")
            .addValidator(StandardValidators.URL_VALIDATOR)
            .build();

    static final PropertyDescriptor APPLICATION_ID = new PropertyDescriptor.Builder()
            .name("Application ID")
            .description("The Application ID to be included in the metrics sent to Prometheus")
            .required(true)
            .expressionLanguageSupported(true)
            .defaultValue("nifi")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    static final PropertyDescriptor HOSTNAME = new PropertyDescriptor.Builder()
            .name("Hostname")
            .description("The Hostname of this NiFi instance to be included in the metrics sent to Prometheus")
            .required(true)
            .expressionLanguageSupported(true)
            .defaultValue("${hostname(true)}")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    static final PropertyDescriptor PROCESS_GROUP_IDS = new PropertyDescriptor.Builder()
            .name("Process Group ID(s)")
            .description("If specified, the reporting task will send metrics the configured ProcessGroup(s) only. Multiple IDs should be separated by a comma. If"
                    + " none of the group-IDs could be found or no IDs are defined, the Nifi-Flow-ProcessGroup is used and global metrics are sent.")
            .required(false)
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators
                    .createListValidator(true, true
                            , StandardValidators.createRegexMatchingValidator(Pattern.compile("[0-9a-z-]+"))))
            .build();

    static final PropertyDescriptor JOB_NAME = new PropertyDescriptor.Builder()
            .name("The Job Name")
            .description("The name of the exporting job")
            .defaultValue("nifi_reporting_job")
            .expressionLanguageSupported(true)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();

    private static final CollectorRegistry REGISTRY = new CollectorRegistry();

    private static final Gauge AMOUNT_FLOWFILES_TOTAL = Gauge.build()
            .name("process_group_amount_flowfiles_total")
            .help("Total number of FlowFiles in ProcessGroup")
            .labelNames("status", "server", "application", "process_group")
            .register(REGISTRY);
    private static final Gauge AMOUNT_BYTES_TOTAL = Gauge.build()
            .name("process_group_amount_bytes_total")
            .help("Total number of Bytes in ProcessGroup")
            .labelNames("status", "server", "application", "process_group")
            .register(REGISTRY);
    private static final Gauge AMOUNT_THREADS_TOTAL = Gauge.build()
            .name("process_group_amount_threads_total")
            .help("Total amount of threads in ProcessGroup")
            .labelNames("status", "server", "application", "process_group")
            .register(REGISTRY);

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(METRICS_COLLECTOR_URL);
        properties.add(APPLICATION_ID);
        properties.add(HOSTNAME);
        properties.add(PROCESS_GROUP_IDS);
        properties.add(JOB_NAME);
        return properties;
    }

    @Override
    public void onTrigger(final ReportingContext context) {
        final String metricsCollectorUrl = context.getProperty(METRICS_COLLECTOR_URL).evaluateAttributeExpressions().getValue().replace("http://", "");
        final String applicationId = context.getProperty(APPLICATION_ID).evaluateAttributeExpressions().getValue();
        final String jobName = context.getProperty(JOB_NAME).getValue();
        final String hostname = context.getProperty(HOSTNAME).evaluateAttributeExpressions().getValue();

        final PushGateway pg = new PushGateway(metricsCollectorUrl);

        for (ProcessGroupStatus status : searchProcessGroups(context, context.getProperty(PROCESS_GROUP_IDS))) {

            String processGroupID = status.getId();
            AMOUNT_FLOWFILES_TOTAL.labels("sent", hostname, applicationId, processGroupID).set(status.getFlowFilesSent());
            AMOUNT_FLOWFILES_TOTAL.labels("queued", hostname, applicationId, processGroupID).set(status.getFlowFilesSent());
            AMOUNT_FLOWFILES_TOTAL.labels("received", hostname, applicationId, processGroupID).set(status.getFlowFilesReceived());

            AMOUNT_BYTES_TOTAL.labels("sent", hostname, applicationId, processGroupID).set(status.getBytesSent());
            AMOUNT_BYTES_TOTAL.labels("read", hostname, applicationId, processGroupID).set(status.getBytesRead());
            AMOUNT_BYTES_TOTAL.labels("written", hostname, applicationId, processGroupID).set(status.getBytesWritten());
            AMOUNT_BYTES_TOTAL.labels("received", hostname, applicationId, processGroupID).set(status.getBytesReceived());
            AMOUNT_BYTES_TOTAL.labels("transferred", hostname, applicationId, processGroupID).set(status.getBytesTransferred());

            AMOUNT_THREADS_TOTAL.labels("nano", hostname, applicationId, processGroupID).set(status.getActiveThreadCount());

            try {
                pg.pushAdd(REGISTRY, jobName);
            } catch (IOException e) {
                getLogger().error("Failed pushing to Prometheus PushGateway due to {}; routing to failure", e);
            }
        }
    }

    /**
     * Searches all ProcessGroups defined in a PropertyValue as a comma-separated list of ProcessorGroup-IDs.
     * Therefore blanks are trimmed and new-line characters are removed! Processors that can not be found are ignored.
     *
     * @return List of all ProcessorGroups that were found.
     * If no groupIDs are defined or none of them could be found an array containing the root-DataFlow will be returned.
     */
    private ProcessGroupStatus[] searchProcessGroups(final ReportingContext context, PropertyValue value) {
        if (value.isSet()) {
            String content = value.evaluateAttributeExpressions().getValue();

            ProcessGroupStatus[] groups = Arrays
                    .stream(content.replace("\n", "").split(","))
                    .map(String::trim)
                    .map(context.getEventAccess()::getGroupStatus)
                    .filter(Objects::nonNull)
                    .toArray(ProcessGroupStatus[]::new);

            return groups.length > 0 ? groups : new ProcessGroupStatus[]{context.getEventAccess().getControllerStatus()};
        } else {
            return new ProcessGroupStatus[]{context.getEventAccess().getControllerStatus()};
        }
    }
}
