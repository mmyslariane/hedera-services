/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.platform.gossip.config;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.net.InetAddress;
import java.util.Objects;

public record NetworkEndpoint(@NonNull Long nodeId, @NonNull InetAddress hostname, int port) {
    public NetworkEndpoint {
        Objects.requireNonNull(nodeId, "nodeId must not be null");
        Objects.requireNonNull(hostname, "hostname must not be null");
        if (port < 0 || port > 65535) {
            throw new IllegalArgumentException("port must be in the range [0, 65535]");
        }
    }
}
