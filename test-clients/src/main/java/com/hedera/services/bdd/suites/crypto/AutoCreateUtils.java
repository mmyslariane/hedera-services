package com.hedera.services.bdd.suites.crypto;

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.Key;
import org.apache.commons.lang3.RandomStringUtils;

public class AutoCreateUtils {
	public static ByteString randomValidEd25519Alias() {
		final var alias = RandomStringUtils.random(128, true, true);
		return Key.newBuilder().setEd25519(ByteString.copyFromUtf8(alias)).build().toByteString();
	}

	public static ByteString randomValidECDSAAlias() {
		final var alias = RandomStringUtils.random(128, true, true);
		return Key.newBuilder().setECDSASecp256K1(ByteString.copyFromUtf8(alias)).build().toByteString();
	}

	public static Key asKey(final ByteString alias) {
		Key aliasKey;
		try {
			aliasKey = Key.parseFrom(alias);
		} catch (InvalidProtocolBufferException ex) {
			return Key.newBuilder().build();
		}
		return aliasKey;
	}
}
