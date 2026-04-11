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

package me.golemcore.hive.workflow.adapter.config;

import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.service.AssignmentWorkflowApplicationService;
import me.golemcore.hive.workflow.application.service.DecompositionPlanningApplicationService;
import me.golemcore.hive.workflow.application.service.BoardWorkflowApplicationService;
import me.golemcore.hive.workflow.application.service.CardWorkflowApplicationService;
import me.golemcore.hive.workflow.application.service.FlowRemapApplicationService;
import me.golemcore.hive.workflow.application.service.ReviewWorkflowApplicationService;
import me.golemcore.hive.workflow.application.service.ObjectiveService;
import me.golemcore.hive.workflow.application.service.OrganizationService;
import me.golemcore.hive.workflow.application.service.TeamService;
import me.golemcore.hive.workflow.application.service.ThreadWorkflowApplicationService;
import me.golemcore.hive.workflow.application.port.out.BoardRepository;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import me.golemcore.hive.workflow.application.port.out.DecompositionPlanRepository;
import me.golemcore.hive.workflow.application.port.out.ObjectiveRepository;
import me.golemcore.hive.workflow.application.port.out.OrganizationRepository;
import me.golemcore.hive.workflow.application.port.out.TeamRepository;
import me.golemcore.hive.workflow.application.port.out.ThreadRepository;
import me.golemcore.hive.workflow.application.port.out.WorkflowAssignmentPort;
import me.golemcore.hive.workflow.application.port.out.WorkflowAuditPort;
import me.golemcore.hive.workflow.application.port.out.WorkflowPolicyPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WorkflowApplicationConfiguration {

    @Bean
    public AssignmentWorkflowApplicationService assignmentWorkflowApplicationService(
            GolemDirectoryUseCase golemDirectoryUseCase) {
        return new AssignmentWorkflowApplicationService(golemDirectoryUseCase);
    }

    @Bean
    public FlowRemapApplicationService flowRemapApplicationService(CardRepository cardRepository) {
        return new FlowRemapApplicationService(cardRepository);
    }

    @Bean
    public BoardWorkflowApplicationService boardWorkflowApplicationService(
            BoardRepository boardRepository,
            FlowRemapApplicationService flowRemapApplicationService,
            WorkflowAuditPort workflowAuditPort) {
        return new BoardWorkflowApplicationService(boardRepository, flowRemapApplicationService, workflowAuditPort);
    }

    @Bean
    public OrganizationService organizationService(
            OrganizationRepository organizationRepository,
            WorkflowAuditPort workflowAuditPort) {
        return new OrganizationService(organizationRepository, workflowAuditPort);
    }

    @Bean
    public TeamService teamService(
            TeamRepository teamRepository,
            GolemDirectoryUseCase golemDirectoryUseCase,
            BoardWorkflowApplicationService boardWorkflowApplicationService,
            WorkflowAuditPort workflowAuditPort) {
        return new TeamService(
                teamRepository,
                golemDirectoryUseCase,
                boardWorkflowApplicationService,
                workflowAuditPort);
    }

    @Bean
    public ObjectiveService objectiveService(
            ObjectiveRepository objectiveRepository,
            TeamService teamService,
            BoardWorkflowApplicationService boardWorkflowApplicationService,
            WorkflowAuditPort workflowAuditPort) {
        return new ObjectiveService(
                objectiveRepository,
                teamService,
                boardWorkflowApplicationService,
                workflowAuditPort);
    }

    @Bean
    public CardWorkflowApplicationService cardWorkflowApplicationService(
            CardRepository cardRepository,
            ThreadRepository threadRepository,
            BoardWorkflowApplicationService boardWorkflowApplicationService,
            WorkflowAssignmentPort workflowAssignmentPort,
            GolemDirectoryUseCase golemDirectoryUseCase,
            TeamService teamService,
            ObjectiveService objectiveService,
            WorkflowAuditPort workflowAuditPort) {
        return new CardWorkflowApplicationService(
                cardRepository,
                threadRepository,
                boardWorkflowApplicationService,
                workflowAssignmentPort,
                golemDirectoryUseCase,
                teamService,
                objectiveService,
                workflowAuditPort);
    }

    @Bean
    public ThreadWorkflowApplicationService threadWorkflowApplicationService(
            ThreadRepository threadRepository,
            CardRepository cardRepository) {
        return new ThreadWorkflowApplicationService(threadRepository, cardRepository);
    }

    @Bean
    public ReviewWorkflowApplicationService reviewWorkflowApplicationService(
            CardRepository cardRepository,
            CardWorkflowApplicationService cardWorkflowApplicationService,
            BoardWorkflowApplicationService boardWorkflowApplicationService,
            TeamService teamService,
            GolemDirectoryUseCase golemDirectoryUseCase,
            ThreadWorkflowApplicationService threadWorkflowApplicationService,
            WorkflowAuditPort workflowAuditPort) {
        return new ReviewWorkflowApplicationService(
                cardRepository,
                cardWorkflowApplicationService,
                boardWorkflowApplicationService,
                teamService,
                golemDirectoryUseCase,
                threadWorkflowApplicationService,
                workflowAuditPort);
    }

    @Bean
    public DecompositionPlanningApplicationService decompositionPlanningApplicationService(
            DecompositionPlanRepository decompositionPlanRepository,
            CardWorkflowApplicationService cardWorkflowApplicationService,
            BoardWorkflowApplicationService boardWorkflowApplicationService,
            ReviewWorkflowApplicationService reviewWorkflowApplicationService,
            WorkflowAuditPort workflowAuditPort,
            WorkflowPolicyPort workflowPolicyPort) {
        return new DecompositionPlanningApplicationService(
                decompositionPlanRepository,
                cardWorkflowApplicationService,
                boardWorkflowApplicationService,
                reviewWorkflowApplicationService,
                workflowAuditPort,
                workflowPolicyPort);
    }
}
