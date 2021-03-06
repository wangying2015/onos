/*
 * Copyright 2015 Open Networking Laboratory
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
 */
package org.onlab.stc;

import com.google.common.collect.Sets;

import java.io.File;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static org.onlab.stc.Coordinator.Directive.*;
import static org.onlab.stc.Coordinator.Status.*;

/**
 * Coordinates execution of a scenario process flow.
 */
public class Coordinator {

    private static final int MAX_THREADS = 16;

    private final ExecutorService executor = newFixedThreadPool(MAX_THREADS);

    private final Scenario scenario;
    private final ProcessFlow processFlow;

    private final StepProcessListener delegate;
    private final CountDownLatch latch;
    private final ScenarioStore store;

    private final Set<StepProcessListener> listeners = Sets.newConcurrentHashSet();
    private File logDir;

    /**
     * Represents action to be taken on a test step.
     */
    public enum Directive {
        NOOP, RUN, SKIP
    }

    /**
     * Represents processor state.
     */
    public enum Status {
        WAITING, IN_PROGRESS, SUCCEEDED, FAILED
    }

    /**
     * Creates a process flow coordinator.
     *
     * @param scenario    test scenario to coordinate
     * @param processFlow process flow to coordinate
     * @param logDir      scenario log directory
     */
    public Coordinator(Scenario scenario, ProcessFlow processFlow, File logDir) {
        this.scenario = scenario;
        this.processFlow = processFlow;
        this.logDir = logDir;
        this.store = new ScenarioStore(processFlow, logDir, scenario.name());
        this.delegate = new Delegate();
        this.latch = new CountDownLatch(store.getSteps().size());
    }

    /**
     * Starts execution of the process flow graph.
     */
    public void start() {
        executeRoots(null);
    }

    /**
     * Wants for completion of the entire process flow.
     *
     * @return exit code to use
     * @throws InterruptedException if interrupted while waiting for completion
     */
    public int waitFor() throws InterruptedException {
        latch.await();
        return store.hasFailures() ? 1 : 0;
    }

    /**
     * Returns set of all test steps.
     *
     * @return set of steps
     */
    public Set<Step> getSteps() {
        return store.getSteps();
    }

    /**
     * Returns the status of the specified test step.
     *
     * @param step test step or group
     * @return step status
     */
    public Status getStatus(Step step) {
        return store.getStatus(step);
    }

    /**
     * Adds the specified listener.
     *
     * @param listener step process listener
     */
    public void addListener(StepProcessListener listener) {
        listeners.add(checkNotNull(listener, "Listener cannot be null"));
    }

    /**
     * Removes the specified listener.
     *
     * @param listener step process listener
     */
    public void removeListener(StepProcessListener listener) {
        listeners.remove(checkNotNull(listener, "Listener cannot be null"));
    }

    /**
     * Executes the set of roots in the scope of the specified group or globally
     * if no group is given.
     *
     * @param group optional group
     */
    private void executeRoots(Group group) {
        Set<Step> steps =
                group != null ? group.children() : processFlow.getVertexes();
        steps.forEach(step -> {
            if (processFlow.getEdgesFrom(step).isEmpty() && step.group() == group) {
                execute(step);
            }
        });
    }

    /**
     * Executes the specified step.
     *
     * @param step step to execute
     */
    private synchronized void execute(Step step) {
        Directive directive = nextAction(step);
        if (directive == RUN || directive == SKIP) {
            store.updateStatus(step, IN_PROGRESS);
            if (step instanceof Group) {
                Group group = (Group) step;
                delegate.onStart(group);
                if (directive == RUN) {
                    executeRoots(group);
                } else {
                    group.children().forEach(child -> delegate.onCompletion(child, 1));
                }
            } else {
                executor.execute(new StepProcessor(step, directive == SKIP,
                                                   logDir, delegate));
            }
        }
    }

    /**
     * Determines the state of the specified step.
     *
     * @param step test step
     * @return state of the step process
     */
    private Directive nextAction(Step step) {
        Status status = store.getStatus(step);
        if (status != WAITING) {
            return NOOP;
        }

        for (Dependency dependency : processFlow.getEdgesFrom(step)) {
            Status depStatus = store.getStatus(dependency.dst());
            if (depStatus == WAITING || depStatus == IN_PROGRESS) {
                return NOOP;
            } else if (depStatus == FAILED && !dependency.isSoft()) {
                return SKIP;
            }
        }
        return RUN;
    }

    /**
     * Executes the successors to the specified step.
     *
     * @param step step whose successors are to be executed
     */
    private void executeSucessors(Step step) {
        processFlow.getEdgesTo(step).forEach(dependency -> execute(dependency.src()));
        completeParentIfNeeded(step.group());
    }

    /**
     * Checks whether the specified parent group, if any, should be marked
     * as complete.
     *
     * @param group parent group that should be checked
     */
    private synchronized void completeParentIfNeeded(Group group) {
        if (group != null && getStatus(group) == IN_PROGRESS) {
            boolean done = true;
            boolean failed = false;
            for (Step child : group.children()) {
                Status status = store.getStatus(child);
                done = done && (status == SUCCEEDED || status == FAILED);
                failed = failed || status == FAILED;
            }
            if (done) {
                delegate.onCompletion(group, failed ? 1 : 0);
            }
        }
    }

    /**
     * Prints formatted output.
     *
     * @param format printf format string
     * @param args   arguments to be printed
     */
    public static void print(String format, Object... args) {
        System.out.println(String.format(format, args));
    }

    /**
     * Internal delegate to monitor the process execution.
     */
    private class Delegate implements StepProcessListener {

        @Override
        public void onStart(Step step) {
            listeners.forEach(listener -> listener.onStart(step));
        }

        @Override
        public void onCompletion(Step step, int exitCode) {
            store.updateStatus(step, exitCode == 0 ? SUCCEEDED : FAILED);
            listeners.forEach(listener -> listener.onCompletion(step, exitCode));
            executeSucessors(step);
            latch.countDown();
        }

        @Override
        public void onOutput(Step step, String line) {
            listeners.forEach(listener -> listener.onOutput(step, line));
        }

    }

}
