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

package me.golemcore.hive.workflow.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import me.golemcore.hive.domain.model.Card;
import me.golemcore.hive.domain.model.CardAssignmentPolicy;
import me.golemcore.hive.domain.model.CardKind;
import me.golemcore.hive.domain.model.CardReviewStatus;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.workflow.application.port.out.CardRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonCardRepository implements CardRepository {

    private static final String CARDS_DIR = "cards";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<Card> list() {
        List<Card> cards = new ArrayList<>();
        for (String path : storagePort.listObjects(CARDS_DIR, "")) {
            String content = storagePort.getText(CARDS_DIR, path);
            if (content == null) {
                continue;
            }
            cards.add(readCard(content, path));
        }
        return cards;
    }

    @Override
    public Optional<Card> findById(String cardId) {
        String content = storagePort.getText(CARDS_DIR, cardId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readCard(content, cardId));
    }

    @Override
    public void save(Card card) {
        try {
            Card normalized = normalizeCard(card);
            storagePort.putTextAtomic(CARDS_DIR, normalized.getId() + ".json",
                    objectMapper.writeValueAsString(normalized));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize card " + card.getId(), exception);
        }
    }

    private Card readCard(String content, String cardRef) {
        try {
            return normalizeCard(objectMapper.readValue(content, Card.class));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize card " + cardRef, exception);
        }
    }

    private Card normalizeCard(Card card) {
        if (card == null) {
            return null;
        }
        if (card.getKind() == null) {
            card.setKind(CardKind.TASK);
        }
        if (card.getDependsOnCardIds() == null) {
            card.setDependsOnCardIds(new ArrayList<>());
        }
        if (card.getReviewerGolemIds() == null) {
            card.setReviewerGolemIds(new ArrayList<>());
        }
        if (card.getReviewStatus() == null) {
            card.setReviewStatus(CardReviewStatus.NOT_REQUIRED);
        }
        if (card.getAssignmentPolicy() == null) {
            card.setAssignmentPolicy(CardAssignmentPolicy.MANUAL);
        }
        return card;
    }
}
