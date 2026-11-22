package br.pucrs.pulsepass.order.domain;

public enum OrderStatus {
    CRIADO, RESERVADO, AGUARDANDO_PAGAMENTO, PAGO, EXPIRADO;

    public OrderStatus transitionTo(OrderStatus target) {
        boolean allowed = switch (this) {
            case CRIADO -> target == RESERVADO || target == EXPIRADO;
            case RESERVADO -> target == AGUARDANDO_PAGAMENTO || target == EXPIRADO;
            case AGUARDANDO_PAGAMENTO -> target == PAGO || target == RESERVADO || target == EXPIRADO;
            case PAGO, EXPIRADO -> false;
        };
        if (!allowed) {
            throw new InvalidOrderTransitionException(this, target);
        }
        return target;
    }
}
