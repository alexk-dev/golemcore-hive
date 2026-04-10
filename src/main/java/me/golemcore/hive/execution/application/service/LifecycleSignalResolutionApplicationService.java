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

package me.golemcore.hive.execution.application.service;

import java.time.Instant;
import java.util.List;
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.domain.model.BoardSignalDecision;
import me.golemcore.hive.domain.model.BoardSignalMapping;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardLifecycleSignal;
import me.golemcore.hive.domain.model.CardTransitionOrigin;
import me.golemcore.hive.domain.model.LifecycleSignalType;
import me.golemcore.hive.domain.model.OperatorUpdate;
import me.golemcore.hive.domain.model.SignalResolutionOutcome;
import me.golemcore.hive.domain.model.ThreadMessageType;
import me.golemcore.hive.domain.model.ThreadParticipantType;
import me.golemcore.hive.domain.model.ThreadRecord;
import me.golemcore.hive.execution.application.port.in.LifecycleSignalResolutionUseCase;
import me.golemcore.hive.execution.application.port.out.OperatorUpdatePublisherPort;
import me.golemcore.hive.fleet.application.port.in.GolemDirectoryUseCase;
import me.golemcore.hive.workflow.application.port.in.BoardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.CardWorkflowUseCase;
import me.golemcore.hive.workflow.application.port.in.ThreadWorkflowUseCase;

public class LifecycleSignalResolutionApplicationService implements LifecycleSignalResolutionUseCase {

    private final BoardWorkflowUseCase boardWorkflowUseCase;
    private final CardWorkflowUseCase cardWorkflowUseCase;
    private final ThreadWorkflowUseCase threadWorkflowUseCase;
    private final GolemDirectoryUseCase golemDirectoryUseCase;
    private final OperatorUpdatePublisherPort operatorUpdatePublisherPort;

    public LifecycleSignalResolutionApplicationService(
            BoardWorkflowUseCase boardWorkflowUseCase,
            CardWorkflowUseCase cardWorkflowUseCase,
            ThreadWorkflowUseCase threadWorkflowUseCase,
            GolemDirectoryUseCase golemDirectoryUseCase,
            OperatorUpdatePublisherPort operatorUpdatePublisherPort) {
        this.boardWorkflowUseCase = boardWorkflowUseCase;
        this.cardWorkflowUseCase = cardWorkflowUseCase;
        this.threadWorkflowUseCase = threadWorkflowUseCase;
        this.golemDirectoryUseCase = golemDirectoryUseCase;
        this.operatorUpdatePublisherPort = operatorUpdatePublisherPort;
    }

    @Override
    public CardLifecycleSignal resolve(CardLifecycleSignal signal) {
        Card card = cardWorkflowUseCase.getCard(signal.getCardId());
        Board board = boardWorkflowUseCase.getBoard(card.getBoardId());
        ThreadRecord thread = threadWorkflowUseCase.getThreadByCardId(card.getId());
        String golemDisplayName = resolveGolemDisplayName(signal.getGolemId());
        BoardSignalMapping mapping = resolveMapping(board, signal.getSignalType());
        BoardSignalDecision decision = mapping != null ? mapping.getDecision()
                : defaultDecision(signal.getSignalType());
        String targetColumnId = mapping != null ? mapping.getTargetColumnId() : null;

        signal.setDecision(decision);
        signal.setResolvedTargetColumnId(targetColumnId);
        signal.setResolvedAt(Instant.now());

        if (decision == BoardSignalDecision.IGNORE) {
            signal.setResolutionOutcome(SignalResolutionOutcome.IGNORED);
            signal.setResolutionSummary("Signal recorded without column change");
        } else if (targetColumnId == null || targetColumnId.isBlank()) {
            signal.setResolutionOutcome(SignalResolutionOutcome.REJECTED);
            signal.setResolutionSummary("Signal policy has no target column");
        } else if (!isSignalTransitionApplicable(board, card.getColumnId(), targetColumnId, decision)) {
            signal.setResolutionOutcome(decision == BoardSignalDecision.SUGGEST_ONLY
                    ? SignalResolutionOutcome.SUGGESTED
                    : SignalResolutionOutcome.REJECTED);
            signal.setResolutionSummary(decision == BoardSignalDecision.SUGGEST_ONLY
                    ? "Suggested transition requires operator review"
                    : "Board flow rejected the target transition");
        } else if (decision == BoardSignalDecision.SUGGEST_ONLY) {
            signal.setResolutionOutcome(SignalResolutionOutcome.SUGGESTED);
            signal.setResolutionSummary("Suggested transition to " + targetColumnId);
        } else {
            cardWorkflowUseCase.moveCard(card.getId(), targetColumnId, null, CardTransitionOrigin.BOARD_AUTOMATION,
                    signal.getGolemId(), golemDisplayName,
                    "Auto-applied from " + signal.getSignalType().name() + ": " + signal.getSummary());
            signal.setResolutionOutcome(SignalResolutionOutcome.AUTO_APPLIED);
            signal.setResolutionSummary("Moved card to " + targetColumnId);
        }

        threadWorkflowUseCase.appendMessage(thread, signal.getCommandId(), signal.getRunId(), signal.getId(),
                ThreadMessageType.SIGNAL, ThreadParticipantType.SYSTEM, signal.getGolemId(), golemDisplayName,
                buildSignalMessage(signal), signal.getCreatedAt());

        operatorUpdatePublisherPort.publish(OperatorUpdate.builder()
                .eventType("thread_updated")
                .cardId(card.getId())
                .threadId(thread.getId())
                .commandId(signal.getCommandId())
                .runId(signal.getRunId())
                .signalId(signal.getId())
                .kinds(List.of("signal", "thread_message", "card"))
                .createdAt(signal.getCreatedAt() != null ? signal.getCreatedAt() : Instant.now())
                .build());
        return signal;
    }

    private BoardSignalMapping resolveMapping(Board board, LifecycleSignalType signalType) {
        return board.getFlow().getSignalMappings().stream()
                .filter(mapping -> signalType.name().equals(mapping.getSignalType()))
                .findFirst()
                .orElse(null);
    }

    private BoardSignalDecision defaultDecision(LifecycleSignalType signalType) {
        if (signalType == LifecycleSignalType.PROGRESS_REPORTED) {
            return BoardSignalDecision.IGNORE;
        }
        return BoardSignalDecision.SUGGEST_ONLY;
    }

    private boolean isSignalTransitionApplicable(
            Board board,
            String currentColumnId,
            String targetColumnId,
            BoardSignalDecision decision) {
        if (decision == BoardSignalDecision.AUTO_APPLY) {
            return boardWorkflowUseCase.isTransitionReachable(board, currentColumnId, targetColumnId);
        }
        return boardWorkflowUseCase.isTransitionAllowed(board, currentColumnId, targetColumnId);
    }

    private String buildSignalMessage(CardLifecycleSignal signal) {
        StringBuilder builder = new StringBuilder();
        builder.append(signal.getSignalType().name()).append(": ").append(signal.getSummary());
        if (signal.getResolutionSummary() != null && !signal.getResolutionSummary().isBlank()) {
            builder.append(" (").append(signal.getResolutionSummary()).append(')');
        }
        return builder.toString();
    }

    private String resolveGolemDisplayName(String golemId) {
        if (golemId == null || golemId.isBlank()) {
            return "";
        }
        return golemDirectoryUseCase.findGolem(golemId)
                .map(golem -> golem.getDisplayName() != null && !golem.getDisplayName().isBlank()
                        ? golem.getDisplayName()
                        : golemId)
                .orElse(golemId);
    }
}
