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

package me.golemcore.hive.domain.service;

import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class SignalResolutionService {

    private final BoardService boardService;
    private final CardService cardService;
    private final ThreadService threadService;
    private final OperatorUpdatesService operatorUpdatesService;

    public CardLifecycleSignal resolve(CardLifecycleSignal signal) {
        Card card = cardService.getCard(signal.getCardId());
        Board board = boardService.getBoard(card.getBoardId());
        ThreadRecord thread = threadService.getThreadByCardId(card.getId());
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
            cardService.moveCard(card.getId(), targetColumnId, null, CardTransitionOrigin.BOARD_AUTOMATION,
                    signal.getGolemId(), signal.getGolemId(),
                    "Auto-applied from " + signal.getSignalType().name() + ": " + signal.getSummary());
            signal.setResolutionOutcome(SignalResolutionOutcome.AUTO_APPLIED);
            signal.setResolutionSummary("Moved card to " + targetColumnId);
        }

        threadService.appendMessage(thread, signal.getCommandId(), signal.getRunId(), signal.getId(),
                ThreadMessageType.SIGNAL, ThreadParticipantType.SYSTEM, signal.getGolemId(), signal.getGolemId(),
                buildSignalMessage(signal), signal.getCreatedAt());

        operatorUpdatesService.publish(OperatorUpdate.builder()
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
            return boardService.isTransitionReachable(board, currentColumnId, targetColumnId);
        }
        return boardService.isTransitionAllowed(board, currentColumnId, targetColumnId);
    }

    private String buildSignalMessage(CardLifecycleSignal signal) {
        StringBuilder builder = new StringBuilder();
        builder.append(signal.getSignalType().name()).append(": ").append(signal.getSummary());
        if (signal.getResolutionSummary() != null && !signal.getResolutionSummary().isBlank()) {
            builder.append(" (").append(signal.getResolutionSummary()).append(')');
        }
        return builder.toString();
    }
}
