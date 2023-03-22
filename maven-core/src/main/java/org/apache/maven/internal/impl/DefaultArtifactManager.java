/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.internal.impl;

import javax.inject.Inject;
import javax.inject.Named;

import java.io.File;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.maven.SessionScoped;
import org.apache.maven.api.Artifact;
import org.apache.maven.api.annotations.Nonnull;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.project.MavenProject;

@Named
@SessionScoped
public class DefaultArtifactManager implements ArtifactManager {

    @Nonnull
    private final DefaultSession session;

    private final Map<String, Path> paths = new ConcurrentHashMap<>();

    @Inject
    public DefaultArtifactManager(@Nonnull DefaultSession session) {
        this.session = session;
    }

    @Nonnull
    @Override
    public Optional<Path> getPath(@Nonnull Artifact artifact) {
        String id = id(artifact);
        if (session.getMavenSession().getAllProjects() != null) {
            for (MavenProject project : session.getMavenSession().getAllProjects()) {
                if (id.equals(id(project.getArtifact()))
                        && project.getArtifact().getFile() != null) {
                    return Optional.of(project.getArtifact().getFile().toPath());
                }
            }
        }
        Path path = paths.get(id);
        if (path == null && artifact instanceof DefaultArtifact) {
            File file = ((DefaultArtifact) artifact).getArtifact().getFile();
            if (file != null) {
                path = file.toPath();
            }
        }
        return Optional.ofNullable(path);
    }

    @Override
    public void setPath(@Nonnull Artifact artifact, Path path) {
        String id = id(artifact);
        if (session.getMavenSession().getAllProjects() != null) {
            for (MavenProject project : session.getMavenSession().getAllProjects()) {
                if (id.equals(id(project.getArtifact()))) {
                    project.getArtifact().setFile(path != null ? path.toFile() : null);
                    break;
                }
            }
        }
        if (path == null) {
            paths.remove(id);
        } else {
            paths.put(id, path);
        }
    }

    private String id(org.apache.maven.artifact.Artifact artifact) {
        return artifact.getGroupId()
                + ":" + artifact.getArtifactId()
                + ":" + artifact.getType()
                + (artifact.getClassifier() == null || artifact.getClassifier().isEmpty()
                        ? ""
                        : ":" + artifact.getClassifier())
                + ":" + artifact.getVersion();
    }

    private String id(Artifact artifact) {
        return artifact.key();
    }
}
