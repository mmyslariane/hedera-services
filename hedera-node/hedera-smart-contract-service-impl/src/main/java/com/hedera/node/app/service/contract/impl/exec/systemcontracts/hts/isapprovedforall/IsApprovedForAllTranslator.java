/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall;

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.isapprovedforall.IsApprovedForAllCall.IS_APPROVED_FOR_ALL;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Translates {@code isApprovedForAll} calls to the HTS system contract.
 */
@Singleton
public class IsApprovedForAllTranslator extends AbstractHtsCallTranslator {
    @Inject
    public IsApprovedForAllTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), IS_APPROVED_FOR_ALL.selector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IsApprovedForAllCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var args = IS_APPROVED_FOR_ALL.decodeCall(attempt.input().toArrayUnsafe());
        return new IsApprovedForAllCall(attempt.enhancement(), attempt.redirectToken(), args.get(0), args.get(1));
    }
}
