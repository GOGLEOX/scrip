package com.gogleox.scrip.api;
import com.gogleox.scrip.payment.PaymentPreview;
import java.util.*;
public record TransferExecutionRequest(UUID actorId, PaymentPreview preview) { public TransferExecutionRequest { Objects.requireNonNull(actorId);Objects.requireNonNull(preview); } }
