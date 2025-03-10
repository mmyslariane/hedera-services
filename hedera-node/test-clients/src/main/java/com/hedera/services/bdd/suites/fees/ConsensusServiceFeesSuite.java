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

package com.hedera.services.bdd.suites.fees;

import static com.hedera.services.bdd.spec.HapiSpec.hapiTest;
import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTopicInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.createTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.deleteTopic;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.submitMessageTo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.updateTopic;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.sleepFor;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsd;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.validateChargedUsdWithin;
import static com.hedera.services.bdd.suites.HapiSuite.ONE_HUNDRED_HBARS;
import static com.hedera.services.bdd.suites.HapiSuite.THREE_MONTHS_IN_SECONDS;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.BUSY;

import com.hedera.services.bdd.junit.HapiTest;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;

public class ConsensusServiceFeesSuite {
    private static final double BASE_FEE_TOPIC_CREATE = 0.01;
    private static final double BASE_FEE_TOPIC_UPDATE = 0.00022;
    private static final double BASE_FEE_TOPIC_DELETE = 0.005;
    private static final double BASE_FEE_TOPIC_SUBMIT_MESSAGE = 0.0001;
    private static final double BASE_FEE_TOPIC_GET_INFO = 0.0001;

    private static final String PAYER = "payer";
    private static final String TOPIC_NAME = "testTopic";

    @HapiTest
    @DisplayName("Topic create base USD fee as expected")
    final Stream<DynamicTest> topicCreateBaseUSDFee() {
        return hapiTest(
                cryptoCreate(PAYER).balance(ONE_HUNDRED_HBARS),
                createTopic(TOPIC_NAME).blankMemo().payingWith(PAYER).via("topicCreate"),
                validateChargedUsd("topicCreate", BASE_FEE_TOPIC_CREATE));
    }

    @HapiTest
    @DisplayName("Topic update base USD fee as expected")
    final Stream<DynamicTest> topicUpdateBaseUSDFee() {
        return hapiTest(
                cryptoCreate("autoRenewAccount"),
                cryptoCreate(PAYER),
                createTopic(TOPIC_NAME)
                        .autoRenewAccountId("autoRenewAccount")
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS - 1)
                        .adminKeyName(PAYER),
                updateTopic(TOPIC_NAME)
                        .payingWith(PAYER)
                        .autoRenewPeriod(THREE_MONTHS_IN_SECONDS)
                        .via("updateTopic"),
                validateChargedUsdWithin("updateTopic", BASE_FEE_TOPIC_UPDATE, 3.0));
    }

    @HapiTest
    @DisplayName("Topic delete base USD fee as expected")
    final Stream<DynamicTest> topicDeleteBaseUSDFee() {
        return hapiTest(
                cryptoCreate(PAYER),
                createTopic(TOPIC_NAME).adminKeyName(PAYER),
                deleteTopic(TOPIC_NAME).blankMemo().payingWith(PAYER).via("topicDelete"),
                validateChargedUsd("topicDelete", BASE_FEE_TOPIC_DELETE));
    }

    @HapiTest
    @DisplayName("Topic submit message base USD fee as expected")
    final Stream<DynamicTest> topicSubmitMessageBaseUSDFee() {
        final byte[] messageBytes = new byte[100]; // 4k
        Arrays.fill(messageBytes, (byte) 0b1);
        return hapiTest(
                cryptoCreate(PAYER).hasRetryPrecheckFrom(BUSY),
                createTopic(TOPIC_NAME).submitKeyName(PAYER).hasRetryPrecheckFrom(BUSY),
                submitMessageTo(TOPIC_NAME)
                        .blankMemo()
                        .payingWith(PAYER)
                        .message(new String(messageBytes))
                        .hasRetryPrecheckFrom(BUSY)
                        .via("submitMessage"),
                sleepFor(1000),
                validateChargedUsd("submitMessage", BASE_FEE_TOPIC_SUBMIT_MESSAGE));
    }

    @HapiTest
    @DisplayName("Topic get info base USD fee as expected")
    final Stream<DynamicTest> tokenGetTopicInfoBaseUSDFee() {
        return hapiTest(
                cryptoCreate(PAYER),
                createTopic(TOPIC_NAME).adminKeyName(PAYER),
                getTopicInfo(TOPIC_NAME).payingWith(PAYER).via("getTopic"),
                sleepFor(1000),
                validateChargedUsd("getTopic", BASE_FEE_TOPIC_GET_INFO));
    }
}
