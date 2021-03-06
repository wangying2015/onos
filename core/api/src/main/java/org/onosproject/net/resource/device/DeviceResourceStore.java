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
package org.onosproject.net.resource.device;

import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.intent.IntentId;

import java.util.Set;

public interface DeviceResourceStore {
    Set<Port> getFreePorts(DeviceId deviceId);

    /**
     * Allocates the given ports to the given intent.
     * @param ports set of ports to allocate
     * @param intentId intent ID
     * @return true if allocation was successful, false otherwise
     */
    boolean allocatePorts(Set<Port> ports, IntentId intentId);

    /**
     * Returns set of ports allocated for an intent.
     *
     * @param intentId the intent ID
     * @return set of allocated ports
     */
    Set<Port> getAllocations(IntentId intentId);

    /**
     * Returns intent allocated to a port.
     *
     * @param port the port
     * @return intent ID allocated to the port
     */
    IntentId getAllocations(Port port);

    /**
     * Allocates the mapping between the given intents.
     *
     * @param keyIntentId key intent ID
     * @param valIntentId value intent ID
     * @return true if mapping was successful, false otherwise
     */
    boolean allocateMapping(IntentId keyIntentId, IntentId valIntentId);

    /**
     * Returns the set of intents mapped to a lower intent.
     *
     * @param intentId intent ID
     * @return set of intent IDs
     */
    Set<IntentId> getMapping(IntentId intentId);

    /**
     * Releases the mapping between the given intents.
     *
     * @param keyIntentId key intent ID
     * @param valIntentId value intent ID
     */
    void releaseMapping(IntentId keyIntentId, IntentId valIntentId);

    /**
     * Releases the ports allocated to the given intent.
     *
     * @param intentId intent ID
     * @return true if release was successful, false otherwise
     */
    boolean releasePorts(IntentId intentId);
}
