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
package org.apache.maven.model.interpolation;

import javax.inject.Inject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.model.Model;
import org.apache.maven.model.building.ModelBuildingRequest;
import org.apache.maven.model.building.ModelProblemCollector;
import org.apache.maven.model.path.PathTranslator;
import org.apache.maven.model.path.UrlNormalizer;
import org.codehaus.plexus.interpolation.AbstractValueSource;
import org.codehaus.plexus.interpolation.InterpolationPostProcessor;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.PrefixAwareRecursionInterceptor;
import org.codehaus.plexus.interpolation.PrefixedObjectValueSource;
import org.codehaus.plexus.interpolation.PrefixedValueSourceWrapper;
import org.codehaus.plexus.interpolation.RecursionInterceptor;
import org.codehaus.plexus.interpolation.ValueSource;

/**
 * Use a regular expression search to find and resolve expressions within the POM.
 *
 * @author jdcasey Created on Feb 3, 2005
 */
public abstract class AbstractStringBasedModelInterpolator implements ModelInterpolator {
    private static final List<String> PROJECT_PREFIXES = Collections.singletonList("project.");

    private static final Collection<String> TRANSLATED_PATH_EXPRESSIONS;

    static {
        Collection<String> translatedPrefixes = new HashSet<>();

        // MNG-1927, MNG-2124, MNG-3355:
        // If the build section is present and the project directory is non-null, we should make
        // sure interpolation of the directories below uses translated paths.
        // Afterward, we'll double back and translate any paths that weren't covered during interpolation via the
        // code below...
        translatedPrefixes.add("build.directory");
        translatedPrefixes.add("build.outputDirectory");
        translatedPrefixes.add("build.testOutputDirectory");
        translatedPrefixes.add("build.sourceDirectory");
        translatedPrefixes.add("build.testSourceDirectory");
        translatedPrefixes.add("build.scriptSourceDirectory");
        translatedPrefixes.add("reporting.outputDirectory");

        TRANSLATED_PATH_EXPRESSIONS = translatedPrefixes;
    }

    private final PathTranslator pathTranslator;
    private final UrlNormalizer urlNormalizer;

    @Inject
    public AbstractStringBasedModelInterpolator(PathTranslator pathTranslator, UrlNormalizer urlNormalizer) {
        this.pathTranslator = pathTranslator;
        this.urlNormalizer = urlNormalizer;
    }

    @Override
    public org.apache.maven.model.Model interpolateModel(
            org.apache.maven.model.Model model,
            File projectDir,
            ModelBuildingRequest request,
            ModelProblemCollector problems) {
        return new org.apache.maven.model.Model(interpolateModel(model.getDelegate(), projectDir, request, problems));
    }

    protected List<ValueSource> createValueSources(
            final Model model, final File projectDir, final ModelBuildingRequest config) {
        Map<String, String> modelProperties = model.getProperties();

        ValueSource projectPrefixValueSource = new PrefixedObjectValueSource(PROJECT_PREFIXES, model, false);

        // NOTE: Order counts here!
        List<ValueSource> valueSources = new ArrayList<>(8);

        if (projectDir != null) {
            ValueSource basedirValueSource = new PrefixedValueSourceWrapper(
                    new AbstractValueSource(false) {
                        @Override
                        public Object getValue(String expression) {
                            if ("basedir".equals(expression)) {
                                return projectDir.getAbsolutePath();
                            }
                            return null;
                        }
                    },
                    PROJECT_PREFIXES,
                    true);
            valueSources.add(basedirValueSource);

            ValueSource baseUriValueSource = new PrefixedValueSourceWrapper(
                    new AbstractValueSource(false) {
                        @Override
                        public Object getValue(String expression) {
                            if ("baseUri".equals(expression)) {
                                return projectDir
                                        .getAbsoluteFile()
                                        .toPath()
                                        .toUri()
                                        .toASCIIString();
                            }
                            return null;
                        }
                    },
                    PROJECT_PREFIXES,
                    false);
            valueSources.add(baseUriValueSource);
            valueSources.add(new BuildTimestampValueSource(config.getBuildStartTime(), modelProperties));
        }

        valueSources.add(projectPrefixValueSource);

        valueSources.add(new MapBasedValueSource(config.getUserProperties()));

        valueSources.add(new MapBasedValueSource(modelProperties));

        valueSources.add(new MapBasedValueSource(config.getSystemProperties()));

        valueSources.add(new AbstractValueSource(false) {
            @Override
            public Object getValue(String expression) {
                return config.getSystemProperties().getProperty("env." + expression);
            }
        });

        return valueSources;
    }

    protected List<? extends InterpolationPostProcessor> createPostProcessors(
            final Model model, final File projectDir, final ModelBuildingRequest config) {
        List<InterpolationPostProcessor> processors = new ArrayList<>(2);
        if (projectDir != null) {
            processors.add(new PathTranslatingPostProcessor(
                    PROJECT_PREFIXES, TRANSLATED_PATH_EXPRESSIONS,
                    projectDir, pathTranslator));
        }
        processors.add(new UrlNormalizingPostProcessor(urlNormalizer));
        return processors;
    }

    protected RecursionInterceptor createRecursionInterceptor() {
        return new PrefixAwareRecursionInterceptor(PROJECT_PREFIXES);
    }
}
