package br.pucrs.pulsepass.iamadmin.domain;

import static br.pucrs.pulsepass.iamadmin.domain.OrganizerRequestStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class OrganizerRequestStatusTest {
    @Test
    void decisionsCanBeReplayedButNotReversed() {
        assertEquals(APROVADA, PENDENTE.approve().approve());
        assertEquals(REJEITADA, PENDENTE.reject("Inválido").reject("Inválido"));
        assertThrows(IllegalStateException.class, REJEITADA::approve);
        assertThrows(IllegalStateException.class, () -> APROVADA.reject("Inválido"));
    }

    @Test
    void rejectionRequiresAnExplanation() {
        assertThrows(IllegalArgumentException.class, () -> PENDENTE.reject(null));
        assertThrows(IllegalArgumentException.class, () -> PENDENTE.reject("  abc  "));
        assertThrows(IllegalArgumentException.class, () -> PENDENTE.reject("a".repeat(301)));
    }
}
