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

package com.swirlds.demo.consistency;

import static com.swirlds.common.utility.ByteUtils.byteArrayToLong;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.STARTUP;
import static com.swirlds.platform.test.fixtures.state.FakeStateLifecycles.FAKE_MERKLE_STATE_LIFECYCLES;

import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.config.StateCommonConfig;
import com.swirlds.common.constructable.ConstructableIgnored;
import com.swirlds.common.utility.NonCryptographicHashing;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.PlatformMerkleStateRoot;
import com.swirlds.platform.state.PlatformStateModifier;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.state.merkle.singleton.StringLeaf;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * State for the Consistency Testing Tool
 */
@ConstructableIgnored
public class ConsistencyTestingToolState extends PlatformMerkleStateRoot {
    private static final Logger logger = LogManager.getLogger(ConsistencyTestingToolState.class);
    private static final long CLASS_ID = 0xda03bb07eb897d82L;

    private static class ClassVersion {
        public static final int ORIGINAL = 1;
    }

    // Nodes at indices 0, 1, and 2 are used by the PlatformState, RosterMap, and RosterState.
    private static final int STATE_LONG_INDEX = 3;
    private static final int ROUND_HANDLED_INDEX = 4;

    /**
     * The history of transactions that have been handled by this app.
     * <p>
     * A deep copy of this object is NOT created when this state is copied. This object does not affect the hash of this
     * node.
     */
    private final TransactionHandlingHistory transactionHandlingHistory;

    /**
     * The true "state" of this app. This long value is updated with every transaction, and with every round.
     * <p>
     * Affects the hash of this node.
     */
    private long stateLong = 0;

    /**
     * The number of rounds handled by this app. Is incremented each time
     * {@link #handleConsensusRound(Round, PlatformStateModifier)} is called. Note that this may not actually equal the round
     * number, since we don't call {@link #handleConsensusRound(Round, PlatformStateModifier)} for rounds with no events.
     *
     * <p>
     * Affects the hash of this node.
     */
    private long roundsHandled = 0;

    /**
     * If not zero, and we are handling the first round after genesis, configure a freeze this duration later.
     * <p>
     * Does not affect the hash of this node (although actions may be taken based on this info that DO affect the
     * hash).
     */
    private Duration freezeAfterGenesis = null;

    /**
     * The set of transactions that have been preconsensus-handled by this app, but haven't yet been
     * postconsensus-handled. This is used to ensure that transactions are prehandled exactly 1 time, prior to
     * posthandling.
     * <p>
     * Does not affect the hash of this node.
     */
    private Set<Long> transactionsAwaitingPostHandle = ConcurrentHashMap.newKeySet();

    /**
     * Constructor
     */
    public ConsistencyTestingToolState(
            @NonNull final StateLifecycles lifecycles,
            @NonNull final Function<SemanticVersion, SoftwareVersion> versionFactory) {
        super(lifecycles, versionFactory);
        logger.info(STARTUP.getMarker(), "New State Constructed.");

        this.transactionHandlingHistory = new TransactionHandlingHistory();
    }

    /**
     * Copy constructor
     *
     * @param that the state to copy
     */
    private ConsistencyTestingToolState(@NonNull final ConsistencyTestingToolState that) {
        super(Objects.requireNonNull(that));

        this.transactionHandlingHistory = that.transactionHandlingHistory;
        this.stateLong = that.stateLong;
        this.roundsHandled = that.roundsHandled;
        this.freezeAfterGenesis = that.freezeAfterGenesis;
        this.transactionsAwaitingPostHandle = that.transactionsAwaitingPostHandle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(
            @NonNull final Platform platform,
            @NonNull final InitTrigger trigger,
            @Nullable final SoftwareVersion previousSoftwareVersion) {

        Objects.requireNonNull(platform);
        Objects.requireNonNull(trigger);

        final StateCommonConfig stateConfig =
                platform.getContext().getConfiguration().getConfigData(StateCommonConfig.class);
        final ConsistencyTestingToolConfig testingToolConfig =
                platform.getContext().getConfiguration().getConfigData(ConsistencyTestingToolConfig.class);

        final Path logFileDirectory = stateConfig
                .savedStateDirectory()
                .resolve(testingToolConfig.logfileDirectory())
                .resolve(Long.toString(platform.getSelfId().id()));
        try {
            Files.createDirectories(logFileDirectory);
        } catch (final IOException e) {
            throw new UncheckedIOException("unable to set up file system for consistency data", e);
        }
        final Path logFilePath = logFileDirectory.resolve("ConsistencyTestLog.csv");

        this.freezeAfterGenesis = testingToolConfig.freezeAfterGenesis();

        final StringLeaf stateLongLeaf = getChild(STATE_LONG_INDEX);
        if (stateLongLeaf != null && stateLongLeaf.getLabel() != null) {
            this.stateLong = Long.parseLong(stateLongLeaf.getLabel());
            logger.info(STARTUP.getMarker(), "State initialized with state long {}.", stateLong);
        }
        final StringLeaf roundsHandledLeaf = getChild(ROUND_HANDLED_INDEX);
        if (roundsHandledLeaf != null && roundsHandledLeaf.getLabel() != null) {
            this.roundsHandled = Long.parseLong(roundsHandledLeaf.getLabel());
            logger.info(STARTUP.getMarker(), "State initialized with {} rounds handled.", roundsHandled);
        }

        transactionHandlingHistory.init(logFilePath);
        FAKE_MERKLE_STATE_LIFECYCLES.initStates(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getMinimumSupportedVersion() {
        return ClassVersion.ORIGINAL;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    @NonNull
    public synchronized ConsistencyTestingToolState copy() {
        throwIfImmutable();
        setImmutable(true);
        return new ConsistencyTestingToolState(this);
    }

    /**
     * Sets the new {@link #stateLong} to the non-cryptographic hash of the existing state, and the contents of the
     * transaction being handled
     *
     * @param transaction the transaction to apply to the state
     */
    private void applyTransactionToState(final @NonNull ConsensusTransaction transaction) {
        Objects.requireNonNull(transaction);
        if (transaction.isSystem()) {
            return;
        }

        final long transactionContents =
                byteArrayToLong(transaction.getApplicationTransaction().toByteArray(), 0);

        if (!transactionsAwaitingPostHandle.remove(transactionContents)) {
            logger.error(EXCEPTION.getMarker(), "Transaction {} was not prehandled.", transactionContents);
        }

        stateLong = NonCryptographicHashing.hash64(stateLong, transactionContents);
    }

    /**
     * Keeps track of which transactions have been prehandled.
     */
    @Override
    public void preHandle(
            @NonNull final Event event,
            @NonNull final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction) {
        event.forEachTransaction(transaction -> {
            if (transaction.isSystem()) {
                return;
            }
            final long transactionContents =
                    byteArrayToLong(transaction.getApplicationTransaction().toByteArray(), 0);

            if (!transactionsAwaitingPostHandle.add(transactionContents)) {
                logger.error(
                        EXCEPTION.getMarker(), "Transaction {} was prehandled more than once.", transactionContents);
            }
        });
    }

    /**
     * Modifies the state based on each transaction in the round
     * <p>
     * Writes the round and its contents to a log on disk
     */
    @Override
    public void handleConsensusRound(
            final @NonNull Round round,
            final @NonNull PlatformStateModifier platformState,
            @NonNull final Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransaction) {
        Objects.requireNonNull(round);
        Objects.requireNonNull(platformState);

        if (roundsHandled == 0 && !freezeAfterGenesis.equals(Duration.ZERO)) {
            // This is the first round after genesis.
            logger.info(
                    STARTUP.getMarker(),
                    "Setting freeze time to {} seconds after genesis.",
                    freezeAfterGenesis.getSeconds());
            platformState.setFreezeTime(round.getConsensusTimestamp().plus(freezeAfterGenesis));
        }

        roundsHandled++;

        round.forEachTransaction(this::applyTransactionToState);
        stateLong = NonCryptographicHashing.hash64(stateLong, round.getRoundNum());

        transactionHandlingHistory.processRound(ConsistencyTestingToolRound.fromRound(round, stateLong));

        setChild(ROUND_HANDLED_INDEX, new StringLeaf(Long.toString(roundsHandled)));
        setChild(STATE_LONG_INDEX, new StringLeaf(Long.toString(stateLong)));
    }
}
