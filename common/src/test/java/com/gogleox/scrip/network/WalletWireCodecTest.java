package com.gogleox.scrip.network;

import com.gogleox.scrip.account.*;
import com.gogleox.scrip.payment.PaymentPreview;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WalletWireCodecTest {
    @Test void roundTripsBoundedRequestAndResponse() throws Exception {
        UUID owner=UUID.randomUUID(),accountId=UUID.randomUUID(),other=UUID.randomUUID(),nonce=UUID.randomUUID();
        AccountId account=new AccountId(accountId), recipient=new AccountId(other);
        WalletRequest request=new WalletRequest(9,WalletProtocol.Request.PREVIEW_TRANSFER,Optional.of(account),Optional.of(recipient),2000,Optional.of(nonce),10);
        assertEquals(request,WalletWireCodec.decodeRequest(WalletWireCodec.encodeRequest(request)));
        Instant now=Instant.ofEpochMilli(System.currentTimeMillis()); AccountSnapshot snapshot=new AccountSnapshot(account,owner,AccountType.PERSONAL,new Balance(3000),now,now,now,AccountLifecycle.ACTIVE);
        PaymentPreview preview=new PaymentPreview(nonce,owner,account,recipient,2000,5,2005,1,now.plusSeconds(30),true);
        TransactionRecord record=new TransactionRecord(UUID.randomUUID(),now,TransactionDirection.OUTGOING,Optional.of(recipient),2000,"payment");
        WalletResponse response=new WalletResponse(9,WalletProtocol.Response.PREVIEW_RESULT,Optional.of(snapshot),Optional.of(preview),List.of(record),List.of(),Optional.empty(),Optional.of("PENDING"));
        assertEquals(response,WalletWireCodec.decodeResponse(WalletWireCodec.encodeResponse(response)));
    }
    @Test void rejectsOversizedAndTrailingWireData() {
        assertThrows(IOException.class,()->WalletWireCodec.decodeRequest(new byte[WalletProtocol.MAX_PACKET_BYTES+1]));
        byte[] valid=WalletWireCodec.encodeRequest(WalletRequest.open(1));
        assertThrows(IOException.class,()->WalletWireCodec.decodeRequest(Arrays.copyOf(valid,valid.length+1)));
    }
}
