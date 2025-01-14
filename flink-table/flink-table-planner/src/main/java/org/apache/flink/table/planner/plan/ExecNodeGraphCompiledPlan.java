/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.planner.plan;

import org.apache.flink.annotation.Internal;
import org.apache.flink.table.api.CompiledPlan;
import org.apache.flink.table.api.ExplainDetail;
import org.apache.flink.table.api.TableException;
import org.apache.flink.table.api.config.TableConfigOptions;
import org.apache.flink.table.api.internal.CompiledPlanInternal;
import org.apache.flink.table.catalog.ObjectIdentifier;
import org.apache.flink.table.planner.delegation.PlannerBase;
import org.apache.flink.table.planner.plan.nodes.exec.ExecNodeGraph;
import org.apache.flink.table.planner.plan.nodes.exec.stream.StreamExecSink;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.stream.Collectors;

/** Implementation of {@link CompiledPlan} backed by an {@link ExecNodeGraph}. */
@Internal
public class ExecNodeGraphCompiledPlan implements CompiledPlanInternal {

    private final PlannerBase planner;
    private final String serializedPlan;
    private final ExecNodeGraph execNodeGraph;

    public ExecNodeGraphCompiledPlan(
            PlannerBase planner, String serializedPlan, ExecNodeGraph execNodeGraph) {
        this.planner = planner;
        this.serializedPlan = serializedPlan;
        this.execNodeGraph = execNodeGraph;
    }

    public ExecNodeGraph getExecNodeGraph() {
        return execNodeGraph;
    }

    @Override
    public String asJsonString() {
        return serializedPlan;
    }

    @Override
    public void writeToFile(File file, boolean ignoreIfExists) {
        if (file.exists()) {
            if (ignoreIfExists) {
                return;
            }

            if (!planner.getConfiguration().get(TableConfigOptions.PLAN_FORCE_RECOMPILE)) {
                throw new TableException(
                        String.format(
                                "Cannot overwrite the plan file '%s'. "
                                        + "Either manually remove the file or, "
                                        + "if you're debugging your job, "
                                        + "set the option '%s' to true.",
                                file, TableConfigOptions.PLAN_FORCE_RECOMPILE.key()));
            }
        }
        try {
            Files.write(
                    file.toPath(),
                    serializedPlan.getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE,
                    StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw new TableException("Cannot write the compiled plan to file '" + file + "'.", e);
        }
    }

    @Override
    public String getFlinkVersion() {
        return this.execNodeGraph.getFlinkVersion();
    }

    @Override
    public String explain(ExplainDetail... explainDetails) {
        return planner.explainPlan(this, explainDetails);
    }

    @Override
    public List<String> getSinkIdentifiers() {
        return this.execNodeGraph.getRootNodes().stream()
                .filter(execNode -> execNode instanceof StreamExecSink)
                .map(
                        execNode ->
                                ((StreamExecSink) execNode)
                                        .getTableSinkSpec()
                                        .getContextResolvedTable()
                                        .getIdentifier())
                .map(ObjectIdentifier::asSummaryString)
                .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return explain();
    }
}
