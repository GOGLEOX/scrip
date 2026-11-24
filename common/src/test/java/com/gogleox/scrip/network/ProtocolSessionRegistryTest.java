package com.gogleox.scrip.network;
import java.util.*;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class ProtocolSessionRegistryTest {
 @Test void negotiatesBoundsRatesAndReplays(){ProtocolSessionRegistry r=new ProtocolSessionRegistry();UUID p=UUID.randomUUID();assertEquals(WalletProtocol.Error.INCOMPATIBLE_PROTOCOL,r.negotiate(p,2,0,0).orElseThrow());assertTrue(r.negotiate(p,1,1,0).isEmpty());assertEquals(WalletProtocol.Error.OVERSIZED,r.accept(p,1,8193,1).orElseThrow());assertTrue(r.accept(p,1,1,1).isEmpty());assertEquals(WalletProtocol.Error.REPLAYED,r.accept(p,1,1,2).orElseThrow());for(long id=2;id<=20;id++)assertTrue(r.accept(p,id,1,3).isEmpty());assertEquals(WalletProtocol.Error.RATE_LIMITED,r.accept(p,21,1,3).orElseThrow());assertTrue(r.accept(p,22,1,10_001).isEmpty());}
}
