package br.pucrs.pulsepass.order.domain;

import java.util.List;

public final class OrderPrice {
    private final long subtotal;
    private final long fee;
    private final long total;

    public OrderPrice(List<Long> seatPrices) {
        if (seatPrices.isEmpty() || seatPrices.size() > 8) {
            throw new IllegalArgumentException("Selecione entre um e oito assentos.");
        }
        long sum = 0;
        for (long price : seatPrices) {
            if (price <= 0) {
                throw new IllegalArgumentException("O preço do assento deve ser positivo.");
            }
            sum = Math.addExact(sum, price);
        }
        subtotal = sum;
        fee = Math.ceilDiv(sum, 10L);
        total = Math.addExact(subtotal, fee);
    }

    public long subtotal() { return subtotal; }
    public long fee() { return fee; }
    public long total() { return total; }
}
