/*
 * Copyright (C) 2021-2024 Hedera Hashgraph, LLC
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

package com.swirlds.virtualmap.internal.reconnect;

import java.util.BitSet;
import java.util.LinkedList;
import java.util.NoSuchElementException;

/**
 * A simple and fast implementation of a boolean queue, backed by one or more {@link BitSet}s. This queue can
 * handle a tremendous number of elements, even far exceeding {@link Long#MAX_VALUE}. You'll run out of RAM first.
 * This is not a thread-safe data structure.
 */
class BooleanBitSetQueue {
    /**
     * The number of bits in each {@link BitSet}.
     */
    private final int bitsPerSet;

    /**
     * A {@link LinkedList} of {@link BitSet}s. The older bitsets are removed from the front of the list
     * after they have been fully read, and newly added bitsets are added to the end of the queue when they
     * are created. This is a backlog of bit sets that the reader has yet to get to.
     */
    private final LinkedList<BitSet> writeBacklog;

    /**
     * The active {@link BitSet} used for writing. Once it is filled up, a new one is created.
     */
    private BitSet writeBitSet;

    /**
     * The active {@link BitSet} used for reading. Once it is fully read, a new one is removed from
     * the head of {@link #writeBacklog} and used for reading.
     */
    private BitSet readBitSet;

    /**
     * The current index within {@link #writeBitSet} into which we will write the next boolean value.
     * When a new {@link #writeBitSet} is created, this is reset to 0.
     */
    private int writeIndex = 0;

    /**
     * The current index within {@link #readBitSet} from which we will read the next boolean value.
     * When a new {@link #readBitSet} is created, this is reset to 0.
     */
    private int readIndex = 0;

    /**
     * Create a new {@link BooleanBitSetQueue}.
     *
     * @param capacity
     * 		The minimum amount of memory to set aside, and the amount by which to increment when the
     * 		queue needs to be expanded. This must be a positive value.
     */
    BooleanBitSetQueue(final int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("initialCapacity must be positive");
        }

        this.bitsPerSet = capacity;
        this.writeBacklog = new LinkedList<>();
        this.writeBitSet = new BitSet(bitsPerSet);
        this.readBitSet = writeBitSet;
    }

    /**
     * Adds the given boolean value to the queue.
     *
     * @param value
     * 		The value to add
     */
    void add(final boolean value) {
        if (writeIndex >= bitsPerSet) {
            writeBitSet = new BitSet(bitsPerSet);
            writeBacklog.add(writeBitSet);
            writeIndex = 0;
        }
        writeBitSet.set(writeIndex++, value);
    }

    /**
     * Removes the next boolean value from the head of the queue.
     *
     * @return
     * 		the next boolean value
     * @throws
     * 		NoSuchElementException if the queue is empty
     */
    boolean remove() {
        if (isEmpty()) {
            throw new NoSuchElementException("Cannot remove from an empty queue");
        }

        if (readIndex >= bitsPerSet) {
            // What happens if remove returns null? It really shouldn't be able to reach this code in that condition...
            readBitSet = writeBacklog.remove();
            readIndex = 0;
        }

        return readBitSet.get(readIndex++);
    }

    /**
     * Gets whether the queue is empty.
     *
     * @return
     * 		{@code true} if the queue is empty.
     */
    boolean isEmpty() {
        return writeBitSet == readBitSet && readIndex == writeIndex;
    }
}
