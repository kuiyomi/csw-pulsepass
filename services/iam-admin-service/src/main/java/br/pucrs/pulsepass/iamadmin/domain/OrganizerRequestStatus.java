package br.pucrs.pulsepass.iamadmin.domain;

public enum OrganizerRequestStatus {
    PENDENTE, APROVADA, REJEITADA;

    public OrganizerRequestStatus approve() {
        if (this == REJEITADA) {
            throw new IllegalStateException("Solicitação já rejeitada.");
        }
        return APROVADA;
    }

    public OrganizerRequestStatus reject(String reason) {
        if (this == APROVADA) {
            throw new IllegalStateException("Solicitação já aprovada.");
        }
        if (reason == null || reason.strip().length() < 5 || reason.strip().length() > 300) {
            throw new IllegalArgumentException("O motivo deve ter de 5 a 300 caracteres.");
        }
        return REJEITADA;
    }
}
