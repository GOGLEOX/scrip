package com.gogleox.scrip.neoforge;

import com.gogleox.scrip.client.WalletClient;
import com.gogleox.scrip.network.WalletResponse;

/** Isolates physical-client presentation from the dedicated-server network bootstrap. */
final class ScripNeoForgeClientResponseSink { private ScripNeoForgeClientResponseSink(){} static void accept(WalletResponse response){WalletClient.receive(response);} }
