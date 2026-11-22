package br.pucrs.pulsepass.order.domain;

public final class InvalidOrderTransitionException extends IllegalStateException {
    public InvalidOrderTransitionException(OrderStatus current, OrderStatus target) {
        super("Transição de pedido inválida de " + current + " para " + target);
    }
}
