/*
 * Copyright 2026 Aleksei Kuleshov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contact: alex@kuleshov.tech
 */

package me.golemcore.hive.execution.adapter.config;

import me.golemcore.hive.execution.application.port.out.InspectionAuditPort;
import me.golemcore.hive.execution.application.port.out.InspectionDispatchPort;
import me.golemcore.hive.execution.application.port.out.ExecutionOperationsPort;
import me.golemcore.hive.execution.application.port.out.OperatorUpdatePublisherPort;
import me.golemcore.hive.execution.application.port.out.SelfEvolvingEventProjectionPort;
import me.golemcore.hive.execution.application.service.ExecutionOperationsApplicationService;
import me.golemcore.hive.execution.application.service.EventIngestionApplicationService;
import me.golemcore.hive.execution.application.service.GolemInspectionApplicationService;
import me.golemcore.hive.execution.application.service.LifecycleSignalResolutionApplicationService;
import me.golemcore.hive.execution.application.port.out.OperatorUpdateStreamPort;
import me.golemcore.hive.execution.application.service.OperatorUpdateStreamApplicationService;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.execution.application.port.in.ExecutionOperationsUseCase;
import me.golemcore.hive.execution.application.port.in.GolemInspectionResponseUseCase;
import me.golemcore.hive.execution.application.port.in.LifecycleSignalResolutionUseCase;
import me.golemcore.hive.execution.application.port.out.CardLifecycleSignalRepository;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutionApplicationConfiguration {

    @Bean
    public ExecutionOperationsApplicationService executionOperationsApplicationService(
            ExecutionOperationsPort executionOperationsPort) {
        return new ExecutionOperationsApplicationService(executionOperationsPort);
    }

    @Bean
    public EventIngestionApplicationService eventIngestionApplicationService(
            CardLifecycleSignalRepository cardLifecycleSignalRepository,
            ExecutionOperationsUseCase executionOperationsUseCase,
            LifecycleSignalResolutionUseCase lifecycleSignalResolutionUseCase,
            GolemInspectionResponseUseCase golemInspectionResponseUseCase,
            SelfEvolvingEventProjectionPort selfEvolvingEventProjectionPort) {
        return new EventIngestionApplicationService(
                cardLifecycleSignalRepository,
                executionOperationsUseCase,
                lifecycleSignalResolutionUseCase,
                golemInspectionResponseUseCase,
                selfEvolvingEventProjectionPort);
    }

    @Bean
    public LifecycleSignalResolutionApplicationService lifecycleSignalResolutionApplicationService(
            BoardWorkflowUseCase boardWorkflowUseCase,
            CardWorkflowUseCase cardWorkflowUseCase,
            ThreadWorkflowUseCase threadWorkflowUseCase,
            GolemDirectoryUseCase golemDirectoryUseCase,
            OperatorUpdatePublisherPort operatorUpdatePublisherPort) {
        return new LifecycleSignalResolutionApplicationService(
                boardWorkflowUseCase,
                cardWorkflowUseCase,
                threadWorkflowUseCase,
                golemDirectoryUseCase,
                operatorUpdatePublisherPort);
    }

    @Bean
    public GolemInspectionApplicationService golemInspectionApplicationService(
            GolemDirectoryUseCase golemDirectoryUseCase,
            InspectionDispatchPort inspectionDispatchPort,
            InspectionAuditPort inspectionAuditPort) {
        return new GolemInspectionApplicationService(
                golemDirectoryUseCase,
                inspectionDispatchPort,
                inspectionAuditPort);
    }

    @Bean
    public OperatorUpdateStreamApplicationService operatorUpdateStreamApplicationService(
            OperatorUpdateStreamPort operatorUpdateStreamPort) {
        return new OperatorUpdateStreamApplicationService(operatorUpdateStreamPort);
    }
}
