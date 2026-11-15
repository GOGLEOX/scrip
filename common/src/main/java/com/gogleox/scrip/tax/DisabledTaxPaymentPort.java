package com.gogleox.scrip.tax;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Explicit no-op wiring option when the separate tax authority is disabled or absent. */
public final class DisabledTaxPaymentPort implements TaxPaymentPort {
    @Override public int apiVersion() { return API_VERSION; }
    @Override public CompletionStage<TaxPaymentResult> pay(TaxPaymentRequest request) {
        return CompletableFuture.completedFuture(new TaxPaymentResult(TaxPaymentResult.Status.INTEGRATION_DISABLED, Optional.empty()));
    }
}
