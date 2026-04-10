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
import me.golemcore.hive.domain.model.Board;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.workflow.application.port.out.BoardRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonBoardRepository implements BoardRepository {

    private static final String BOARDS_DIR = "boards";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<Board> list() {
        List<Board> boards = new ArrayList<>();
        for (String path : storagePort.listObjects(BOARDS_DIR, "")) {
            String content = storagePort.getText(BOARDS_DIR, path);
            if (content == null) {
                continue;
            }
            boards.add(readBoard(content, path));
        }
        return boards;
    }

    @Override
    public Optional<Board> findById(String boardId) {
        String content = storagePort.getText(BOARDS_DIR, boardId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readBoard(content, boardId));
    }

    @Override
    public void save(Board board) {
        try {
            storagePort.putTextAtomic(BOARDS_DIR, board.getId() + ".json", objectMapper.writeValueAsString(board));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize board " + board.getId(), exception);
        }
    }

    private Board readBoard(String content, String boardRef) {
        try {
            return objectMapper.readValue(content, Board.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize board " + boardRef, exception);
        }
    }
}
