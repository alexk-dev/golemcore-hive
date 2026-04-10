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

package me.golemcore.hive.governance.adapter.config;

import me.golemcore.hive.config.HiveProperties;
import me.golemcore.hive.governance.application.GovernanceSettings;
import me.golemcore.hive.governance.application.port.in.ApprovalWorkflowUseCase;
import me.golemcore.hive.governance.application.port.in.AuditLogUseCase;
import me.golemcore.hive.governance.application.port.in.BudgetSnapshotUseCase;
import me.golemcore.hive.governance.application.port.in.NotificationUseCase;
import me.golemcore.hive.governance.application.port.out.ApprovalCommandStatePort;
import me.golemcore.hive.governance.application.port.out.ApprovalGolemProfilePort;
import me.golemcore.hive.governance.application.port.out.ApprovalRepositoryPort;
import me.golemcore.hive.governance.application.port.out.ApprovalThreadPort;
import me.golemcore.hive.governance.application.port.out.AuditEventRepositoryPort;
import me.golemcore.hive.governance.application.port.out.BudgetProjectionSourcePort;
import me.golemcore.hive.governance.application.port.out.BudgetSnapshotRepositoryPort;
import me.golemcore.hive.governance.application.port.out.NotificationDeliveryPort;
import me.golemcore.hive.governance.application.port.out.PolicyBindingRepositoryPort;
import me.golemcore.hive.governance.application.port.out.PolicyGolemPort;
import me.golemcore.hive.governance.application.port.out.PolicyGroupRepositoryPort;
import me.golemcore.hive.governance.application.port.out.PolicyGroupVersionRepositoryPort;
import me.golemcore.hive.governance.application.port.out.PolicySpecCodecPort;
import me.golemcore.hive.governance.application.port.out.PolicySyncDispatchPort;
import me.golemcore.hive.governance.application.port.out.SystemHealthPort;
import me.golemcore.hive.governance.application.service.ApprovalWorkflowApplicationService;
import me.golemcore.hive.governance.application.service.AuditLogApplicationService;
import me.golemcore.hive.governance.application.service.BudgetSnapshotApplicationService;
import me.golemcore.hive.governance.application.service.GovernanceOperationsApplicationService;
import me.golemcore.hive.governance.application.service.NotificationApplicationService;
import me.golemcore.hive.governance.application.service.PolicyGroupAdministrationApplicationService;
import me.golemcore.hive.governance.application.service.PolicyLifecycleApplicationService;
import me.golemcore.hive.governance.application.service.PolicyRolloutApplicationService;
import me.golemcore.hive.governance.application.service.SystemAdministrationApplicationService;
import me.golemcore.hive.execution.application.port.out.OperatorUpdatePublisherPort;
import me.golemcore.hive.shared.approval.ApprovedCommandDispatchPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GovernanceApplicationConfiguration {

    @Bean
    public AuditLogApplicationService auditLogApplicationService(AuditEventRepositoryPort auditEventRepositoryPort) {
        return new AuditLogApplicationService(auditEventRepositoryPort);
    }

    @Bean
    public NotificationApplicationService notificationApplicationService(
            GovernanceSettings governanceSettings,
            NotificationDeliveryPort notificationDeliveryPort,
            me.golemcore.hive.governance.application.port.out.NotificationRepositoryPort notificationRepositoryPort) {
        return new NotificationApplicationService(
                governanceSettings,
                notificationRepositoryPort,
                notificationDeliveryPort);
    }

    @Bean
    public ApprovalWorkflowApplicationService approvalWorkflowApplicationService(
            GovernanceSettings governanceSettings,
            AuditLogUseCase auditLogUseCase,
            NotificationUseCase notificationUseCase,
            OperatorUpdatePublisherPort operatorUpdatePublisherPort,
            ApprovalRepositoryPort approvalRepositoryPort,
            ApprovalCommandStatePort approvalCommandStatePort,
            ApprovedCommandDispatchPort approvedCommandDispatchPort,
            ApprovalThreadPort approvalThreadPort,
            ApprovalGolemProfilePort approvalGolemProfilePort) {
        return new ApprovalWorkflowApplicationService(
                governanceSettings,
                auditLogUseCase,
                notificationUseCase,
                operatorUpdatePublisherPort,
                approvalRepositoryPort,
                approvalCommandStatePort,
                approvedCommandDispatchPort,
                approvalThreadPort,
                approvalGolemProfilePort);
    }

    @Bean
    public BudgetSnapshotApplicationService budgetSnapshotApplicationService(
            BudgetSnapshotRepositoryPort budgetSnapshotRepositoryPort,
            BudgetProjectionSourcePort budgetProjectionSourcePort) {
        return new BudgetSnapshotApplicationService(budgetSnapshotRepositoryPort, budgetProjectionSourcePort);
    }

    @Bean
    public PolicyGroupAdministrationApplicationService policyGroupAdministrationApplicationService(
            PolicyGroupRepositoryPort policyGroupRepositoryPort,
            PolicyGroupVersionRepositoryPort policyGroupVersionRepositoryPort,
            PolicyBindingRepositoryPort policyBindingRepositoryPort,
            PolicyGolemPort policyGolemPort,
            PolicySpecCodecPort policySpecCodecPort,
            AuditLogUseCase auditLogUseCase) {
        return new PolicyGroupAdministrationApplicationService(
                policyGroupRepositoryPort,
                policyGroupVersionRepositoryPort,
                policyBindingRepositoryPort,
                policyGolemPort,
                policySpecCodecPort,
                auditLogUseCase);
    }

    @Bean
    public PolicyRolloutApplicationService policyRolloutApplicationService(
            me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase,
            PolicyGolemPort policyGolemPort,
            PolicySyncDispatchPort policySyncDispatchPort) {
        return new PolicyRolloutApplicationService(
                policyGroupAdministrationUseCase,
                policyGolemPort,
                policySyncDispatchPort);
    }

    @Bean
    public PolicyLifecycleApplicationService policyLifecycleApplicationService(
            me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase,
            me.golemcore.hive.governance.application.port.in.PolicyRolloutUseCase policyRolloutUseCase) {
        return new PolicyLifecycleApplicationService(policyGroupAdministrationUseCase, policyRolloutUseCase);
    }

    @Bean
    public GovernanceOperationsApplicationService governanceOperationsApplicationService(
            AuditLogUseCase auditLogUseCase,
            BudgetSnapshotUseCase budgetSnapshotUseCase,
            ApprovalWorkflowUseCase approvalWorkflowUseCase,
            me.golemcore.hive.governance.application.port.in.PolicyGroupAdministrationUseCase policyGroupAdministrationUseCase,
            me.golemcore.hive.governance.application.port.in.PolicyLifecycleUseCase policyLifecycleUseCase) {
        return new GovernanceOperationsApplicationService(
                auditLogUseCase,
                budgetSnapshotUseCase,
                approvalWorkflowUseCase,
                policyGroupAdministrationUseCase,
                policyLifecycleUseCase);
    }

    @Bean
    public GovernanceSettings governanceSettings(HiveProperties properties) {
        return new GovernanceSettings(
                properties.getDeployment().isProductionMode(),
                properties.getStorage().getBasePath(),
                properties.getSecurity().getCookie().isSecure(),
                properties.getGovernance().getApprovals().getHighCostThresholdMicros(),
                properties.getGovernance().getRetention().getApprovalsDays(),
                properties.getGovernance().getRetention().getAuditDays(),
                properties.getGovernance().getRetention().getNotificationsDays(),
                properties.getGovernance().getNotifications().isApprovalRequested(),
                properties.getGovernance().getNotifications().isBlockerRaised(),
                properties.getGovernance().getNotifications().isGolemOffline(),
                properties.getGovernance().getNotifications().isCommandFailed());
    }

    @Bean
    public SystemAdministrationApplicationService systemAdministrationApplicationService(
            GovernanceSettings governanceSettings,
            SystemHealthPort systemHealthPort,
            NotificationUseCase notificationUseCase) {
        return new SystemAdministrationApplicationService(
                governanceSettings,
                systemHealthPort,
                notificationUseCase);
    }
}
