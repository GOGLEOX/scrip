package com.gogleox.scrip.shop;
import java.util.*;
import java.util.concurrent.CompletionStage;
/** Version 1 provider contract for a separate server-side shop product. */
public interface ShopEconomyPort { int API_VERSION=1; int apiVersion(); CompletionStage<ShopQuoteResult> quote(ShopQuoteRequest request); CompletionStage<SettlementResult> reserve(String settlementId, ShopQuote quote, boolean buyerConfirmed); CompletionStage<SettlementResult> settle(String settlementId, boolean deliveryConfirmed); CompletionStage<SettlementResult> cancelOrRefund(String settlementId, String reason); CompletionStage<SettlementResult> getSettlement(String settlementId); }
