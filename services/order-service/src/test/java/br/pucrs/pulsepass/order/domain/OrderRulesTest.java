package br.pucrs.pulsepass.order.domain;

import static br.pucrs.pulsepass.order.domain.OrderStatus.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class OrderRulesTest {
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = Map.of(
            CRIADO, Set.of(RESERVADO, EXPIRADO),
            RESERVADO, Set.of(AGUARDANDO_PAGAMENTO, EXPIRADO),
            AGUARDANDO_PAGAMENTO, Set.of(PAGO, RESERVADO, EXPIRADO));

    static Stream<Arguments> transitions() {
        return Stream.of(OrderStatus.values()).flatMap(from ->
                Stream.of(OrderStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest
    @MethodSource("transitions")
    void enforcesStateMachine(OrderStatus from, OrderStatus to) {
        if (ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            assertEquals(to, from.transitionTo(to));
        } else {
            assertThrows(InvalidOrderTransitionException.class, () -> from.transitionTo(to));
        }
    }

    @Test
    void roundsFeeOnceForTheWholeOrder() {
        OrderPrice price = new OrderPrice(List.of(1001L, 1001L));
        assertEquals(2002, price.subtotal());
        assertEquals(201, price.fee());
        assertEquals(2203, price.total());
        assertEquals(100, new OrderPrice(List.of(1000L)).fee());
    }

    @Test
    void enforcesPriceAndSeatLimits() {
        assertThrows(IllegalArgumentException.class, () -> new OrderPrice(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new OrderPrice(List.of(0L)));
        assertThrows(IllegalArgumentException.class, () -> new OrderPrice(Collections.nCopies(9, 1L)));
        assertEquals(9, new OrderPrice(Collections.nCopies(8, 1L)).total());
        assertThrows(ArithmeticException.class, () -> new OrderPrice(List.of(Long.MAX_VALUE)));
    }
}
