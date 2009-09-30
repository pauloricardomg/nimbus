/*
 * Copyright 1999-2008 University of Chicago
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package org.nimbustools.auto_common.confmgr;

public class AddOrReplaceLogicalHost {

    // -------------------------------------------------------------------------
    // ADJUST
    // -------------------------------------------------------------------------

    public void addOrReplaceLogicalHost(String logicalHost,
                                        String configPath) throws Exception {

        if (logicalHost == null) {
            throw new IllegalArgumentException("logicalHost may not be null");
        }
        if (configPath == null) {
            throw new IllegalArgumentException("configPath may not be null");
        }

        new ServerConfigGlobalAdjust().addOrReplace(configPath,
                                                    "logicalHost",
                                                    logicalHost);
    }

    
    // -------------------------------------------------------------------------
    // MAIN
    // -------------------------------------------------------------------------

    public static void main(String[] args) {

        if (args == null || args.length != 2) {
            System.err.println("Needs these arguments:\n" +
                    "1 - the new logicalHost value\n" +
                    "2 - the server-config.wsdd path");
            System.exit(1);
        }

        try {
            new AddOrReplaceLogicalHost().
                    addOrReplaceLogicalHost(args[0], args[1]);
        } catch (Exception e) {
            System.err.println("Problem with logicalHost: " + e.getMessage());
            System.exit(1);
        }
    }

}