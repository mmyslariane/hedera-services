package com.hedera.services.sigs.metadata.lookups;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.ledger.accounts.BackingStore;
import com.hedera.services.sigs.metadata.AccountSigningMetadata;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.AccountID;

import static com.hedera.services.sigs.order.KeyOrderingFailure.MISSING_ACCOUNT;
import static com.hedera.services.utils.EntityIdUtils.isAlias;

public class BackedAccountLookup implements AccountSigMetaLookup {
	private final AliasManager aliasManager;
	private final BackingStore<AccountID, MerkleAccount> accounts;

	public BackedAccountLookup(
			final BackingStore<AccountID, MerkleAccount> accounts,
			final AliasManager aliasManager
	) {
		this.accounts = accounts;
		this.aliasManager = aliasManager;
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> safeLookup(final AccountID id) {
		return lookupById(id);
	}

	@Override
	public SafeLookupResult<AccountSigningMetadata> aliasableSafeLookup(final AccountID idOrAlias) {
		if (isAlias(idOrAlias)) {
			final var explicitId = aliasManager.lookupIdBy(idOrAlias.getAlias());
			return (explicitId == EntityNum.MISSING_NUM)
					? SafeLookupResult.failure(MISSING_ACCOUNT)
					: lookupById(explicitId.toGrpcAccountId());
		} else {
			return lookupById(idOrAlias);
		}
	}

	private SafeLookupResult<AccountSigningMetadata> lookupById(final AccountID id) {
		if (!accounts.contains(id)) {
			return SafeLookupResult.failure(MISSING_ACCOUNT);
		} else {
			final var account = accounts.getImmutableRef(id);
			return new SafeLookupResult<>(
					new AccountSigningMetadata(
							account.getAccountKey(), account.isReceiverSigRequired()));
		}
	}
}
