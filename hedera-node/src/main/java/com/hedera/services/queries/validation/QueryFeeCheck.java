package com.hedera.services.queries.validation;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
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

import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountAmount;

import static com.hedera.services.state.merkle.MerkleEntityId.fromAccountId;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;

import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hedera.services.state.merkle.MerkleEntityId;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransferList;
import com.swirlds.fcmap.FCMap;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public class QueryFeeCheck {
	private final Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts;

	public QueryFeeCheck(Supplier<FCMap<MerkleEntityId, MerkleAccount>> accounts) {
		this.accounts = accounts;
	}

	public ResponseCodeEnum nodePaymentValidity(List<AccountAmount> transfers, long fee, AccountID node) {
		var plausibility = transfersPlausibility(transfers);
		if (plausibility != OK) {
			return plausibility;
		}

		long netPayment = -1 * transfers.stream()
				.mapToLong(AccountAmount::getAmount)
				.filter(amount -> amount < 0)
				.sum();
		if (netPayment < fee) {
			return INSUFFICIENT_TX_FEE;
		}

		var numBeneficiaries = transfers.stream()
				.filter(adjustment -> adjustment.getAmount() > 0)
				.count();
		if (numBeneficiaries != 1) {
			return INVALID_RECEIVING_NODE_ACCOUNT;
		}
		if (transfers.stream().noneMatch(adj -> adj.getAmount() == netPayment && adj.getAccountID().equals(node))) {
			return INVALID_RECEIVING_NODE_ACCOUNT;
		}

		return OK;
	}

	ResponseCodeEnum transfersPlausibility(List<AccountAmount> transfers) {
		if (Optional.ofNullable(transfers).map(List::size).orElse(0) == 0) {
			return INVALID_ACCOUNT_AMOUNTS;
		}

		var basicPlausibility = transfers
				.stream()
				.map(this::adjustmentPlausibility)
				.filter(status -> status != OK)
				.findFirst()
				.orElse(OK);
		if (basicPlausibility != OK) {
			return basicPlausibility;
		}

		try {
			long net = transfers.stream()
					.mapToLong(AccountAmount::getAmount)
					.reduce(0L, Math::addExact);
			return (net == 0) ? OK : INVALID_ACCOUNT_AMOUNTS;
		} catch (ArithmeticException ignore) {
			return INVALID_ACCOUNT_AMOUNTS;
		}
	}

	ResponseCodeEnum adjustmentPlausibility(AccountAmount adjustment) {
		var id = adjustment.getAccountID();
		var key = MerkleEntityId.fromAccountId(id);
		long amount = adjustment.getAmount();

		if (amount == Long.MIN_VALUE) {
			return INVALID_ACCOUNT_AMOUNTS;
		}

		if (amount < 0) {
			var balanceStatus = Optional.ofNullable(accounts.get().get(key))
					.filter(account -> account.getBalance() >= Math.abs(amount))
					.map(ignore -> OK)
					.orElse(INSUFFICIENT_PAYER_BALANCE);
			if (balanceStatus != OK) {
				return balanceStatus;
			}
		} else {
			if (!accounts.get().containsKey(key)) {
				return ACCOUNT_ID_DOES_NOT_EXIST;
			}
		}

		return OK;
	}

	public ResponseCodeEnum validateQueryPaymentTransaction(TransactionBody txn) {
		long suppliedFee = txn.getTransactionFee();
		long transferAmount = 0;
		if (txn.getTransactionID().hasAccountID()) {
			AccountID payerAccount = txn.getTransactionID().getAccountID();
			Long payerAccountBalance = Optional.ofNullable(accounts.get().get(fromAccountId(payerAccount)))
					.map(MerkleAccount::getBalance)
					.orElse(null);

			TransferList transferList = txn.getCryptoTransfer().getTransfers();
			if (transferList.getAccountAmountsCount() != 2) {
				return INVALID_QUERY_PAYMENT_ACCOUNT_AMOUNTS;
			}

			List<AccountAmount> transfers = transferList.getAccountAmountsList();
			for (AccountAmount entry : transfers) {
				if (entry.getAmount() < 0) {
					transferAmount += -1 * entry.getAmount();
					if (!entry.getAccountID().equals(payerAccount)) {
						return INVALID_PAYER_ACCOUNT_ID;
					}
				}

				if (entry.getAmount() > 0) {
					if (!entry.getAccountID().equals(txn.getNodeAccountID())) {
						return INVALID_RECEIVING_NODE_ACCOUNT;
					}
				}
			}
			try {
				if (payerAccountBalance < Math.addExact(transferAmount, suppliedFee)) {
					return INSUFFICIENT_PAYER_BALANCE;
				}
			} catch (ArithmeticException e) {
				return INSUFFICIENT_PAYER_BALANCE;
			}
		}
		return OK;
	}
}
