package com.gogleox.scrip.forge;

import com.gogleox.scrip.client.WalletClient;
import com.gogleox.scrip.network.WalletResponse;

/** Isolates physical-client presentation from the dedicated-server network bootstrap. */
final class ScripForgeClientResponseSink { private ScripForgeClientResponseSink(){} static void accept(WalletResponse response){WalletClient.receive(response);} }
