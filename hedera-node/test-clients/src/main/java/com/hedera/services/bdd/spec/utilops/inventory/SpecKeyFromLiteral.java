/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec.utilops.inventory;

import static com.hedera.services.bdd.spec.utilops.inventory.SpecKeyFromMnemonic.createAndLinkSimpleKey;

import com.google.common.base.MoreObjects;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.swirlds.common.utility.CommonUtils;
import java.util.Optional;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SpecKeyFromLiteral extends UtilOp {
    private static final Logger log = LogManager.getLogger(SpecKeyFromLiteral.class);

    private final String name;
    private final String hexEncodedPrivateKey;
    private Optional<String> linkedId = Optional.empty();

    public SpecKeyFromLiteral(String name, String hexEncodedPrivateKey) {
        this.name = name;
        this.hexEncodedPrivateKey = hexEncodedPrivateKey;
    }

    public SpecKeyFromLiteral linkedTo(String id) {
        linkedId = Optional.of(id);
        return this;
    }

    @Override
    protected boolean submitOp(HapiSpec spec) throws Throwable {
        byte[] privateKey = CommonUtils.unhex(hexEncodedPrivateKey);
        createAndLinkSimpleKey(spec, privateKey, name, linkedId, log);
        return false;
    }

    @Override
    protected MoreObjects.ToStringHelper toStringHelper() {
        var helper = super.toStringHelper();
        helper.add("name", name);
        linkedId.ifPresent(s -> helper.add("linkedId", s));
        return helper;
    }
}
