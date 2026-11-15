package com.gogleox.scrip.tax;

import java.util.concurrent.CompletionStage;

/** Version 1 optional, loader-neutral payment boundary for a property-tax authority. */
public interface TaxPaymentPort {
    int API_VERSION = 1;

    int apiVersion();

    CompletionStage<TaxPaymentResult> pay(TaxPaymentRequest request);
}
