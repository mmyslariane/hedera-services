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

package com.hedera.node.app.service.consensus.impl.test.handlers;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.COMPLEX_KEY_ACCOUNT_KT;
import static com.hedera.test.utils.TxnUtils.payerSponsoredTransfer;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.*;
import static com.hederahashgraph.api.proto.java.ResponseType.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.consensus.impl.handlers.ConsensusGetTopicInfoHandler;
import com.hedera.node.app.service.mono.state.merkle.MerkleTopic;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.spi.fixtures.state.MapReadableKVState;
import com.hederahashgraph.api.proto.java.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ConsensusGetTopicInfoHandlerTest extends ConsensusHandlerTestBase {
    private ConsensusGetTopicInfoHandler subject;

    @BeforeEach
    void setUp() {
        subject = new ConsensusGetTopicInfoHandler();
    }

    @Test
    void emptyConstructor() {
        assertNotNull(new ConsensusGetTopicInfoHandler());
    }

    @Test
    void extractsHeader() throws Throwable {
        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var header = subject.extractHeader(query);
        assertEquals(query.getConsensusGetTopicInfo().getHeader(), header);
    }

    @Test
    void createsEmptyResponse() {
        final var responseHeader = ResponseHeader.newBuilder()
                .setNodeTransactionPrecheckCode(FAIL_FEE)
                .build();
        final var response = subject.createEmptyResponse(responseHeader);
        final var expectedResponse = Response.newBuilder()
                .setConsensusGetTopicInfo(ConsensusGetTopicInfoResponse.newBuilder()
                        .setHeader(responseHeader)
                        .build())
                .build();
        assertEquals(expectedResponse, response);
    }

    @Test
    void requiresPayment() {
        assertTrue(subject.requiresNodePayment(ANSWER_ONLY));
        assertTrue(subject.requiresNodePayment(ANSWER_STATE_PROOF));
        assertFalse(subject.requiresNodePayment(COST_ANSWER));
        assertFalse(subject.requiresNodePayment(COST_ANSWER_STATE_PROOF));
    }

    @Test
    void needsAnswerOnlyCostForCostAnswer() {
        assertFalse(subject.needsAnswerOnlyCost(ANSWER_ONLY));
        assertFalse(subject.needsAnswerOnlyCost(ANSWER_STATE_PROOF));
        assertTrue(subject.needsAnswerOnlyCost(COST_ANSWER));
        assertFalse(subject.needsAnswerOnlyCost(COST_ANSWER_STATE_PROOF));
    }

    @Test
    void validatesQueryWhenValidTopic() throws Throwable {
        givenValidTopic();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.validate(query, readableStore);
        assertEquals(OK, response);
    }

    @Test
    void validatesQueryIfInvalidTopic() throws Throwable {
        readableTopicState.reset();
        final var state =
                MapReadableKVState.<Long, MerkleTopic>builder("TOPICS").build();
        given(readableStates.<Long, MerkleTopic>get(TOPICS)).willReturn(state);
        final var store = new ReadableTopicStore(readableStates);

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.validate(query, store);
        assertEquals(INVALID_TOPIC_ID, response);
    }

    @Test
    void validatesQueryIfDeletedTopic() throws Throwable {
        givenValidTopic(autoRenewId.getAccountNum(), true);
        readableTopicState = readableTopicState();
        given(readableStates.<EntityNum, com.hedera.hapi.node.state.consensus.Topic>get(TOPICS))
                .willReturn(readableTopicState);
        readableStore = new ReadableTopicStore(readableStates);

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.validate(query, readableStore);
        assertEquals(INVALID_TOPIC_ID, response);
    }

    @Test
    void getsResponseIfFailedResponse() throws Throwable {
        final var responseHeader = ResponseHeader.newBuilder()
                .setNodeTransactionPrecheckCode(FAIL_FEE)
                .build();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.findResponse(query, responseHeader, readableStore, queryContext);
        assertEquals(FAIL_FEE, response.getConsensusGetTopicInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(
                ConsensusTopicInfo.newBuilder().build(),
                response.getConsensusGetTopicInfo().getTopicInfo());
    }

    @Test
    void getsResponseIfOkResponse() throws Throwable {
        givenValidTopic();
        given(queryContext.getLedgerId()).willReturn(ledgerId);
        final var responseHeader =
                ResponseHeader.newBuilder().setNodeTransactionPrecheckCode(OK).build();
        final var expectedInfo = getExpectedInfo();

        final var query = createGetTopicInfoQuery(topicEntityNum.intValue());
        final var response = subject.findResponse(query, responseHeader, readableStore, queryContext);
        assertEquals(OK, response.getConsensusGetTopicInfo().getHeader().getNodeTransactionPrecheckCode());
        assertEquals(expectedInfo, response.getConsensusGetTopicInfo().getTopicInfo());
    }

    private ConsensusTopicInfo getExpectedInfo() {
        return ConsensusTopicInfo.newBuilder()
                .setMemo(topic.memo())
                .setAdminKey(key)
                .setRunningHash(ByteString.copyFrom("runningHash".getBytes()))
                .setSequenceNumber(topic.sequenceNumber())
                .setExpirationTime(Timestamp.newBuilder().setSeconds(topic.expiry()))
                .setSubmitKey(key)
                .setAutoRenewAccount(AccountID.newBuilder().setAccountNum(topic.autoRenewAccountNumber()))
                .setAutoRenewPeriod(Duration.newBuilder().setSeconds(100L).build())
                .setLedgerId(ledgerId)
                .build();
    }

    private Query createGetTopicInfoQuery(int topicId) throws Throwable {
        final var payment = payerSponsoredTransfer(payerId, COMPLEX_KEY_ACCOUNT_KT, beneficiaryIdStr, paymentAmount);
        final var data = ConsensusGetTopicInfoQuery.newBuilder()
                .setTopicID(TopicID.newBuilder().setTopicNum(topicId).build())
                .setHeader(QueryHeader.newBuilder().setPayment(payment).build())
                .build();

        return Query.newBuilder().setConsensusGetTopicInfo(data).build();
    }
}
