/*
 * Copyright 2014-2015 Open Networking Laboratory
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
package org.onosproject.event.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onlab.util.SharedExecutors;
import org.onosproject.event.AbstractEvent;
import org.onosproject.event.DefaultEventSinkRegistry;
import org.onosproject.event.Event;
import org.onosproject.event.EventDeliveryService;
import org.onosproject.event.EventSink;
import org.slf4j.Logger;

import java.util.TimerTask;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.concurrent.Executors.newSingleThreadExecutor;
import static org.onlab.util.Tools.groupedThreads;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Simple implementation of an event dispatching service.
 */
@Component(immediate = true)
@Service
public class CoreEventDispatcher extends DefaultEventSinkRegistry
        implements EventDeliveryService {

    private final Logger log = getLogger(getClass());

    // Default number of millis a sink can take to process an event.
    private static final long DEFAULT_EXECUTE_MS = 2_000; // ms
    private static final long WATCHDOG_MS = 250; // ms

    private final BlockingQueue<Event> events = new LinkedBlockingQueue<>();

    private final ExecutorService executor =
            newSingleThreadExecutor(groupedThreads("onos/event", "dispatch-%d"));

    @SuppressWarnings("unchecked")
    private static final Event KILL_PILL = new AbstractEvent(null, 0) {
    };

    private DispatchLoop dispatchLoop;
    private long maxProcessMillis = DEFAULT_EXECUTE_MS;

    // Means to detect long-running sinks
    private TimerTask watchdog;
    private EventSink lastSink;
    private long lastStart = 0;
    private Future<?> dispatchFuture;

    @Override
    public void post(Event event) {
        if (!events.add(event)) {
            log.error("Unable to post event {}", event);
        }
    }

    @Activate
    public void activate() {
        dispatchLoop = new DispatchLoop();
        dispatchFuture = executor.submit(dispatchLoop);
        watchdog = new Watchdog();
        SharedExecutors.getTimer().schedule(watchdog, WATCHDOG_MS, WATCHDOG_MS);
        log.info("Started");
    }

    @Deactivate
    public void deactivate() {
        dispatchLoop.stop();
        watchdog.cancel();
        post(KILL_PILL);
        log.info("Stopped");
    }

    @Override
    public void setDispatchTimeLimit(long millis) {
        checkArgument(millis >= WATCHDOG_MS,
                      "Time limit must be greater than %s", WATCHDOG_MS);
        maxProcessMillis = millis;
    }

    @Override
    public long getDispatchTimeLimit() {
        return maxProcessMillis;
    }

    // Auxiliary event dispatching loop that feeds off the events queue.
    private class DispatchLoop implements Runnable {
        private volatile boolean stopped;

        @Override
        public void run() {
            stopped = false;
            log.info("Dispatch loop initiated");
            while (!stopped) {
                try {
                    // Fetch the next event and if it is the kill-pill, bail
                    Event event = events.take();
                    if (event == KILL_PILL) {
                        break;
                    }
                    process(event);
                } catch (Exception e) {
                    log.warn("Error encountered while dispatching event:", e);
                }
            }
            log.info("Dispatch loop terminated");
        }

        // Locate the sink for the event class and use it to process the event
        @SuppressWarnings("unchecked")
        private void process(Event event) {
            EventSink sink = getSink(event.getClass());
            if (sink != null) {
                lastSink = sink;
                lastStart = System.currentTimeMillis();
                sink.process(event);
                lastStart = 0;
            } else {
                log.warn("No sink registered for event class {}",
                         event.getClass().getName());
            }
        }

        void stop() {
            stopped = true;
        }
    }

    // Monitors event sinks to make sure none take too long to execute.
    private class Watchdog extends TimerTask {
        @Override
        public void run() {
            long delta = System.currentTimeMillis() - lastStart;
            if (lastStart > 0 && delta > maxProcessMillis) {
                log.error("Event sink {} exceeded execution time limit: {} ms",
                          lastSink.getClass().getName(), delta);

                // Notify the sink that it has exceeded its time limit.
                lastSink.onProcessLimit();

                // Cancel the old dispatch loop and submit a new one.
                dispatchLoop.stop();
                dispatchLoop = new DispatchLoop();
                dispatchFuture.cancel(true);
                dispatchFuture = executor.submit(dispatchLoop);
            }
        }
    }
}
