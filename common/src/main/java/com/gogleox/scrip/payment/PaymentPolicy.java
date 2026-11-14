package com.gogleox.scrip.payment;

public record PaymentPolicy(FeeMode feeMode, long feeValue, int confirmationSeconds, long revision) {
    public PaymentPolicy {
        if (feeMode == null || feeValue < 0 || confirmationSeconds < 1 || confirmationSeconds > 3600 || revision < 0) throw new IllegalArgumentException("Invalid payment policy");
        if (feeMode == FeeMode.DISABLED && feeValue != 0) throw new IllegalArgumentException("Disabled fees require zero value");
        if (feeMode == FeeMode.PERCENTAGE_BASIS_POINTS && feeValue > 10_000) throw new IllegalArgumentException("Percentage fee is out of range");
    }
    public static PaymentPolicy defaults() { return new PaymentPolicy(FeeMode.DISABLED,0,300,0); }
    public long feeFor(long amount) {
        if (amount <= 0) throw new IllegalArgumentException("Payment amount must be positive");
        return switch(feeMode) {
            case DISABLED -> 0;
            case FLAT -> feeValue;
            case PERCENTAGE_BASIS_POINTS -> {
                long quotient=amount/10_000, remainder=amount%10_000;
                long base=Math.multiplyExact(quotient,feeValue);
                long fractional=(remainder*feeValue+9_999)/10_000;
                yield Math.addExact(base,fractional);
            }
        };
    }
}
