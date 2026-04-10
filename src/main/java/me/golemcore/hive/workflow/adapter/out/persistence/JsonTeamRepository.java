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
import me.golemcore.hive.domain.model.Team;
import me.golemcore.hive.infrastructure.storage.StoragePort;
import me.golemcore.hive.workflow.application.port.out.TeamRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JsonTeamRepository implements TeamRepository {

    private static final String TEAMS_DIR = "teams";

    private final StoragePort storagePort;
    private final ObjectMapper objectMapper;

    @Override
    public List<Team> list() {
        List<Team> teams = new ArrayList<>();
        for (String path : storagePort.listObjects(TEAMS_DIR, "")) {
            String content = storagePort.getText(TEAMS_DIR, path);
            if (content == null) {
                continue;
            }
            teams.add(readTeam(content, path));
        }
        return teams;
    }

    @Override
    public Optional<Team> findById(String teamId) {
        String content = storagePort.getText(TEAMS_DIR, teamId + ".json");
        if (content == null) {
            return Optional.empty();
        }
        return Optional.of(readTeam(content, teamId));
    }

    @Override
    public void save(Team team) {
        try {
            storagePort.putTextAtomic(TEAMS_DIR, team.getId() + ".json", objectMapper.writeValueAsString(team));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize team " + team.getId(), exception);
        }
    }

    private Team readTeam(String content, String teamRef) {
        try {
            return objectMapper.readValue(content, Team.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize team " + teamRef, exception);
        }
    }
}
