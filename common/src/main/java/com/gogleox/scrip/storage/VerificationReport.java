package com.gogleox.scrip.storage;

/** Result of a non-mutating verification pass over the checkpoint and journal. */
public record VerificationReport(long latestSequence, int replayEntryCount, boolean hasSnapshot) {}
