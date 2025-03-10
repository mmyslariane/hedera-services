/*
 * Copyright (C) 2023-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.steps;

import static com.hedera.node.app.fixtures.AppTestBase.DEFAULT_CONFIG;
import static com.hedera.node.app.service.addressbook.impl.schemas.V053AddressBookSchema.NODES_KEY;
import static com.hedera.node.app.service.token.impl.handlers.staking.StakePeriodManager.DEFAULT_STAKING_PERIOD_MINS;
import static com.hedera.node.config.types.StreamMode.BLOCKS;
import static com.hedera.node.config.types.StreamMode.RECORDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.state.addressbook.Node;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.transaction.ExchangeRateSet;
import com.hedera.node.app.fees.ExchangeRateManager;
import com.hedera.node.app.records.ReadableBlockRecordStore;
import com.hedera.node.app.service.addressbook.AddressBookService;
import com.hedera.node.app.service.token.impl.handlers.staking.EndOfStakingPeriodUpdater;
import com.hedera.node.app.service.token.records.TokenContext;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.workflows.handle.Dispatch;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.node.config.data.StakingConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.swirlds.config.api.Configuration;
import com.swirlds.state.spi.WritableKVState;
import com.swirlds.state.spi.WritableStates;
import java.time.Duration;
import java.time.Instant;
import java.util.function.BiConsumer;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class NodeStakeUpdatesTest {
    private static final Instant CONSENSUS_TIME_1234567 = Instant.ofEpochSecond(1_234_5670L, 1357);

    @Mock
    private EndOfStakingPeriodUpdater stakingPeriodCalculator;

    @Mock
    private ExchangeRateManager exchangeRateManager;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private TokenContext context;

    @Mock
    private ReadableBlockRecordStore blockStore;

    @Mock
    private SavepointStackImpl stack;

    @Mock
    private Dispatch dispatch;

    @Mock
    private WritableStates writableStates;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private WritableKVState<EntityNumber, Node> nodesState;

    private StakePeriodChanges subject;

    @BeforeEach
    void setUp() {
        given(context.readableStore(ReadableBlockRecordStore.class)).willReturn(blockStore);

        subject = new StakePeriodChanges(stakingPeriodCalculator, exchangeRateManager, storeMetricsService);
    }

    @Test
    void processUpdateSkippedForPreviousPeriod() {
        verifyNoInteractions(stakingPeriodCalculator);
        verifyNoInteractions(exchangeRateManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processUpdateCalledForGenesisTxn() {
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);
        given(context.configuration()).willReturn(DEFAULT_CONFIG);
        given(stack.getWritableStates(AddressBookService.NAME)).willReturn(writableStates);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodesState);
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(Timestamp.newBuilder().seconds(1_234_567L))
                        .build());
        given(context.consensusTime()).willReturn(CONSENSUS_TIME_1234567);

        subject.process(dispatch, stack, context, RECORDS, true, Instant.EPOCH);

        verify(stakingPeriodCalculator).updateNodes(eq(context), eq(ExchangeRateSet.DEFAULT), any(BiConsumer.class));
        verify(exchangeRateManager).updateMidnightRates(stack);
    }

    @Test
    void processUpdateSkippedForPreviousConsensusTime() {
        final var beforeLastConsensusTime = CONSENSUS_TIME_1234567.minusSeconds(1);
        given(context.consensusTime()).willReturn(beforeLastConsensusTime);
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(Timestamp.newBuilder()
                                .seconds(CONSENSUS_TIME_1234567.getEpochSecond())
                                .nanos(CONSENSUS_TIME_1234567.getNano()))
                        .build());

        subject.process(dispatch, stack, context, RECORDS, false, Instant.EPOCH);

        verifyNoInteractions(stakingPeriodCalculator);
        verifyNoInteractions(exchangeRateManager);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processUpdateCalledForNextPeriodWithRecordsStreamMode() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());
        // Use any number of seconds that gets isNextPeriod(...) to return true
        final var currentConsensusTime = CONSENSUS_TIME_1234567.plusSeconds(500_000);
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(Timestamp.newBuilder()
                                .seconds(CONSENSUS_TIME_1234567.getEpochSecond())
                                .nanos(CONSENSUS_TIME_1234567.getNano()))
                        .build());
        given(context.consensusTime()).willReturn(currentConsensusTime);
        given(stack.getWritableStates(AddressBookService.NAME)).willReturn(writableStates);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodesState);

        // Pre-condition check
        Assertions.assertThat(
                        StakePeriodChanges.isNextStakingPeriod(currentConsensusTime, CONSENSUS_TIME_1234567, context))
                .isTrue();
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);

        subject.process(dispatch, stack, context, RECORDS, false, Instant.EPOCH);

        verify(stakingPeriodCalculator)
                .updateNodes(
                        argThat(stakingContext -> currentConsensusTime.equals(stakingContext.consensusTime())),
                        eq(ExchangeRateSet.DEFAULT),
                        any(BiConsumer.class));
        verify(exchangeRateManager).updateMidnightRates(stack);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processUpdateCalledForNextPeriodWithBlocksStreamMode() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());
        // Use any number of seconds that gets isNextPeriod(...) to return true
        final var currentConsensusTime = CONSENSUS_TIME_1234567.plusSeconds(500_000);
        given(context.consensusTime()).willReturn(currentConsensusTime);

        // Pre-condition check
        Assertions.assertThat(
                        StakePeriodChanges.isNextStakingPeriod(currentConsensusTime, CONSENSUS_TIME_1234567, context))
                .isTrue();
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);
        given(stack.getWritableStates(AddressBookService.NAME)).willReturn(writableStates);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodesState);

        subject.process(dispatch, stack, context, BLOCKS, false, CONSENSUS_TIME_1234567);

        verify(stakingPeriodCalculator)
                .updateNodes(
                        argThat(stakingContext -> currentConsensusTime.equals(stakingContext.consensusTime())),
                        eq(ExchangeRateSet.DEFAULT),
                        any(BiConsumer.class));
        verify(exchangeRateManager).updateMidnightRates(stack);
    }

    @Test
    @SuppressWarnings("unchecked")
    void processUpdateExceptionIsCaught() {
        given(exchangeRateManager.exchangeRates()).willReturn(ExchangeRateSet.DEFAULT);
        doThrow(new RuntimeException("test exception"))
                .when(stakingPeriodCalculator)
                .updateNodes(any(), eq(ExchangeRateSet.DEFAULT), any(BiConsumer.class));
        given(blockStore.getLastBlockInfo())
                .willReturn(BlockInfo.newBuilder()
                        .consTimeOfLastHandledTxn(new Timestamp(CONSENSUS_TIME_1234567.getEpochSecond(), 0))
                        .build());
        given(context.consensusTime()).willReturn(CONSENSUS_TIME_1234567.plus(Duration.ofDays(2)));
        given(context.configuration()).willReturn(DEFAULT_CONFIG);
        given(stack.getWritableStates(AddressBookService.NAME)).willReturn(writableStates);
        given(writableStates.<EntityNumber, Node>get(NODES_KEY)).willReturn(nodesState);

        Assertions.assertThatNoException()
                .isThrownBy(() -> subject.process(dispatch, stack, context, RECORDS, false, Instant.EPOCH));
        verify(stakingPeriodCalculator).updateNodes(eq(context), eq(ExchangeRateSet.DEFAULT), any(BiConsumer.class));
        verify(exchangeRateManager).updateMidnightRates(stack);
    }

    @Test
    void isNextStakingPeriodNowConsensusTimeBeforeThenConsensusTimeUtcDay() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());

        final var earlierNowConsensus =
                CONSENSUS_TIME_1234567.minusSeconds(Duration.ofDays(1).toSeconds());
        final var result = StakePeriodChanges.isNextStakingPeriod(earlierNowConsensus, CONSENSUS_TIME_1234567, context);

        Assertions.assertThat(result).isFalse();
    }

    @Test
    void isNextStakingPeriodNowConsensusTimeInSameThenConsensusTimeUtcDay() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());

        final var result =
                StakePeriodChanges.isNextStakingPeriod(CONSENSUS_TIME_1234567, CONSENSUS_TIME_1234567, context);

        Assertions.assertThat(result).isFalse();
    }

    @Test
    void isNextStakingPeriodNowConsensusTimeAfterThenConsensusTimeUtcDay() {
        given(context.configuration()).willReturn(newPeriodMinsConfig());

        final var laterNowConsensus =
                CONSENSUS_TIME_1234567.plusSeconds(Duration.ofDays(1).toSeconds());
        final var result = StakePeriodChanges.isNextStakingPeriod(laterNowConsensus, CONSENSUS_TIME_1234567, context);

        Assertions.assertThat(result).isTrue();
    }

    @Test
    void isNextStakingPeriodNowCustomStakingPeriodIsEarlier() {
        final var periodMins = 990;
        given(context.configuration()).willReturn(newPeriodMinsConfig(periodMins));

        final var earlierStakingPeriodTime = CONSENSUS_TIME_1234567.minusSeconds(
                // 1000 min * 60 seconds/min
                1000 * 60);
        final var result =
                StakePeriodChanges.isNextStakingPeriod(earlierStakingPeriodTime, CONSENSUS_TIME_1234567, context);
        Assertions.assertThat(result).isFalse();
    }

    @Test
    void isNextStakingPeriodNowCustomStakingPeriodIsLater() {
        final var periodMins = 990;
        given(context.configuration()).willReturn(newPeriodMinsConfig(periodMins));

        final var laterStakingPeriodTime = CONSENSUS_TIME_1234567.plusSeconds(
                // 1000 min * 60 seconds/min
                1000 * 60);
        final var result =
                StakePeriodChanges.isNextStakingPeriod(laterStakingPeriodTime, CONSENSUS_TIME_1234567, context);
        Assertions.assertThat(result).isTrue();
    }

    private Configuration newPeriodMinsConfig() {
        return newPeriodMinsConfig(DEFAULT_STAKING_PERIOD_MINS);
    }

    private Configuration newPeriodMinsConfig(final long periodMins) {
        return newConfig(periodMins, false);
    }

    private Configuration newConfig(final long periodMins, final boolean keyCandidateRoster) {
        return HederaTestConfigBuilder.create()
                .withConfigDataType(StakingConfig.class)
                .withValue("staking.periodMins", periodMins)
                .withValue("tss.keyCandidateRoster", keyCandidateRoster)
                .getOrCreateConfig();
    }
}
