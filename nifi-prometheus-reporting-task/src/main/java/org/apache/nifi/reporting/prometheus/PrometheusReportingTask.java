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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import com.yammer.metrics.core.VirtualMachineMetrics;
import io.prometheus.client.exporter.BasicAuthHttpConnectionFactory;
import io.prometheus.client.exporter.PushGateway;
import org.apache.nifi.annotation.configuration.DefaultSchedule;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.components.PropertyValue;
import org.apache.nifi.controller.status.ProcessGroupStatus;
import org.apache.nifi.expression.ExpressionLanguageScope;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.reporting.AbstractReportingTask;
import org.apache.nifi.reporting.ReportingContext;
import org.apache.nifi.reporting.prometheus.api.PrometheusMetricsFactory;
import org.apache.nifi.scheduling.SchedulingStrategy;

/**
 * ReportingTask to send metrics from Nifi and JVM to Prometheus PushGateway.
 *
 * Author: Daniel-Seifert
 */
@Tags({"reporting", "prometheus", "metrics"})
@CapabilityDescription("Sends JVM-metrics as well as Nifi-metrics to a Prometheus PushGateway." +
        "Nifi-metrics can be either configured global or on process-group level.")
@DefaultSchedule(strategy = SchedulingStrategy.TIMER_DRIVEN, period = "1 min")
public class PrometheusReportingTask extends AbstractReportingTask {

    private static final String JVM_JOB_NAME = "jvm_global";

    static final PropertyDescriptor METRICS_COLLECTOR_URL = new PropertyDescriptor.Builder()
            .name("Prometheus PushGateway")
            .description("The URL of the Prometheus PushGateway Service")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .defaultValue("http://localhost:9091")
            .addValidator(StandardValidators.URL_VALIDATOR)
            .build();
    static final PropertyDescriptor APPLICATION_ID = new PropertyDescriptor.Builder()
            .name("Application ID")
            .description("The Application ID to be included in the metrics sent to Prometheus")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .defaultValue("nifi")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    static final PropertyDescriptor INSTANCE_ID = new PropertyDescriptor.Builder()
            .name("Instance ID")
            .description("Id of this NiFi instance to be included in the metrics sent to Prometheus")
            .required(true)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .defaultValue("${hostname(true)}")
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    static final PropertyDescriptor PROCESS_GROUP_IDS = new PropertyDescriptor.Builder()
            .name("Process group ID(s)")
            .description("If specified, the reporting task will send metrics the configured ProcessGroup(s) only. Multiple IDs should be separated by a comma. If"
                    + " none of the group-IDs could be found or no IDs are defined, the Nifi-Flow-ProcessGroup is used and global metrics are sent.")
            .required(false)
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .addValidator(StandardValidators
                    .createListValidator(true, true
                            , StandardValidators.createRegexMatchingValidator(Pattern.compile("[0-9a-z-]+"))))
            .build();
    static final PropertyDescriptor JOB_NAME = new PropertyDescriptor.Builder()
            .name("The job name")
            .description("The name of the exporting job")
            .defaultValue("nifi_reporting_job")
            .expressionLanguageSupported(ExpressionLanguageScope.VARIABLE_REGISTRY)
            .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
            .build();
    static final PropertyDescriptor SEND_JVM_METRICS = new PropertyDescriptor.Builder()
            .name("Send JVM-metrics")
            .description("Send JVM-metrics in addition to the Nifi-metrics")
            .allowableValues("true", "false")
            .defaultValue("false")
            .required(true)
            .build();
    static final PropertyDescriptor USE_AUTHENTICATION = new PropertyDescriptor.Builder()
            .name("Use URL authentication")
            .description("Whether to use URL authentication or not")
            .allowableValues("true", "false")
            .defaultValue("false")
            .required(true)
            .build();
    static final PropertyDescriptor AUTH_USERNAME = new PropertyDescriptor.Builder()
            .name("Auth username")
            .description("Username that is used for URL authentication.")
            .required(false)
            .build();
    static final PropertyDescriptor AUTH_PASSWORD = new PropertyDescriptor.Builder()
            .name("Auth password")
            .description("Password that is used for URL authentication.")
            .required(false)
            .sensitive(true)
            .build();

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        final List<PropertyDescriptor> properties = new ArrayList<>();
        properties.add(METRICS_COLLECTOR_URL);
        properties.add(APPLICATION_ID);
        properties.add(INSTANCE_ID);
        properties.add(PROCESS_GROUP_IDS);
        properties.add(JOB_NAME);
        properties.add(SEND_JVM_METRICS);
        properties.add(USE_AUTHENTICATION);
        properties.add(AUTH_USERNAME);
        properties.add(AUTH_PASSWORD);
        return properties;
    }

    @Override
    public void onTrigger(final ReportingContext context) {
        final String metricsCollectorUrl = context.getProperty(METRICS_COLLECTOR_URL)
                .evaluateAttributeExpressions().getValue()
                .replace("http://", "");

        final String applicationId = context.getProperty(APPLICATION_ID).evaluateAttributeExpressions().getValue();
        final String jobName = context.getProperty(JOB_NAME).getValue();
        final String instance = context.getProperty(INSTANCE_ID).evaluateAttributeExpressions().getValue();
        final String username = context.getProperty(AUTH_USERNAME).getValue();
        final String password = context.getProperty(AUTH_PASSWORD).getValue();
        final Map<String,String> groupingKey = Collections.singletonMap("instance", instance);

        // Init PushGateway
        final PushGateway pushGateway = new PushGateway(metricsCollectorUrl);
        if(context.getProperty(USE_AUTHENTICATION).asBoolean()){
            pushGateway.setConnectionFactory(new BasicAuthHttpConnectionFactory(username, password));
        }

        try {
            if (context.getProperty(SEND_JVM_METRICS).asBoolean()) {
                pushGateway.pushAdd(PrometheusMetricsFactory.createJvmMetrics(VirtualMachineMetrics.getInstance()), JVM_JOB_NAME, groupingKey);
            }
        } catch (IOException e) {
            getLogger().error("Failed pushing JVM-metrics to Prometheus PushGateway due to {}; routing to failure", e);
        }

        for (ProcessGroupStatus status : searchProcessGroups(context, context.getProperty(PROCESS_GROUP_IDS))) {
            try {
                pushGateway.pushAdd(PrometheusMetricsFactory.createNifiMetrics(status, applicationId), jobName, groupingKey);
            } catch (IOException e) {
                getLogger().error("Failed pushing Nifi-metrics to Prometheus PushGateway due to {}; routing to failure", e);
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
