package br.pucrs.pulsepass.ticket.domain;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class TicketStatusTest {
    @Test
    void usedTicketRefusesASecondAdmission() {
        var admitted = TicketStatus.EMITIDO.admit();
        assertEquals(TicketStatus.UTILIZADO, admitted);
        assertThrows(IllegalStateException.class, admitted::admit);
    }
}
