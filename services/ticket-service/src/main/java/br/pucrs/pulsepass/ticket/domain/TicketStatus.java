package br.pucrs.pulsepass.ticket.domain;

public enum TicketStatus {
    EMITIDO, UTILIZADO;

    public TicketStatus admit() {
        if (this == UTILIZADO) {
            throw new IllegalStateException("Ingresso já utilizado.");
        }
        return UTILIZADO;
    }
}
