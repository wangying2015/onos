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
package org.onosproject.pcep.api;

/*
 * Represent to provider facing side of a switch
 */
public interface PcepSwitch extends PcepOperator {

    public static enum SubDeviceType {
        /* optical device */
        ROADM,

        /* electronic device */
        OTN,

        /* router */
        ROUTER,

        /* unkown type */
        UNKNOW,
    }

    /**
     * Gets a string version of the ID for this switch.
     * @return string version of the ID
     */
    public String getStringId();

    /**
     * Gets the datapathId of the switch.
     * @return the switch dpid in long format
     */
    public long getId();

    public long getNeId();

    /**
     * Gets the sub type of the device.
     * @return the sub type
     */
    public SubDeviceType getDeviceSubType();

    /**
     * fetch the manufacturer description.
     * @return the description
     */
    public String manufacturerDescription();

    /**
     * fetch the datapath description.
     * @return the description
     */
    public String datapathDescription();

    /**
     * fetch the hardware description.
     * @return the description
     */
    public String hardwareDescription();

    /**
     * fetch the software description.
     * @return the description
     */
    public String softwareDescription();

    /**
     * fetch the serial number.
     * @return the serial
     */
    public String serialNumber();

    /**
     * Indicates if this switch is optical.
     * @return true if optical
     */
    public boolean isOptical();
}
