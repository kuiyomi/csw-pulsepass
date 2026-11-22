package br.pucrs.pulsepass.realtime;

import br.pucrs.pulsepass.security.RealmTokens;
import com.fasterxml.jackson.databind.*;
import java.util.*;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.*;
import org.springframework.messaging.handler.annotation.*;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.config.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.config.annotation.*;

@Configuration
@EnableWebSocketMessageBroker
class StompConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtDecoder jwt;
    StompConfig(JwtDecoder jwt){this.jwt=jwt;}
    @Override public void registerStompEndpoints(StompEndpointRegistry registry){
        registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:5173");
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry){
        registry.enableSimpleBroker("/topic","/queue");registry.setApplicationDestinationPrefixes("/app");
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration){
        registration.interceptors(new ChannelInterceptor(){
            @Override public Message<?> preSend(Message<?> message,MessageChannel channel){
                var header=MessageHeaderAccessor.getAccessor(message,StompHeaderAccessor.class);
                if(header==null)return message;
                var command=header.getCommand();
                if(command==StompCommand.CONNECT){
                    String bearer=header.getFirstNativeHeader("Authorization");
                    if(bearer==null||!bearer.startsWith("Bearer "))throw new AccessDeniedException("Token obrigatório.");
                    var token=jwt.decode(bearer.substring(7));
                    if(header.getUser()==null||!header.getUser().getName().equals(token.getSubject()))throw new AccessDeniedException("Sessão inválida.");
                    header.setUser(RealmTokens.converter().convert(token));
                }
                if(command==StompCommand.SEND)throw new AccessDeniedException("Publicação exclusiva do servidor.");
                if(command==StompCommand.SUBSCRIBE){
                    var destination=header.getDestination();
                    if(header.getUser()==null||destination==null||!(destination.equals("/user/queue/orders")||
                            destination.matches("/topic/events/[0-9a-fA-F-]{36}/seats")))throw new AccessDeniedException("Destino inválido.");
                }
                return message;
            }
        });
    }
}
@Component
public class LiveUpdates {
    private final SimpMessagingTemplate ws;
    private final ObjectMapper json;
    public LiveUpdates(SimpMessagingTemplate ws,ObjectMapper json){this.ws=ws;this.json=json;}
    @KafkaListener(topics={"inventory.seats-reserved","inventory.seats-released","inventory.reservation-expired","order.expired","order.paid"},groupId="realtime-service")
    public void receive(String raw) throws Exception{
        var record=json.readTree(raw);var p=record.required("payload");String type=record.required("eventType").asText();
        if(type.startsWith("order.")){
            boolean paid=type.equals("order.paid");
            ws.convertAndSendToUser(p.required("usuarioId").asText(),"/queue/orders",Map.of("orderId",p.required("pedidoId").asText(),
                    "type",paid?"ORDER_PAID":"ORDER_EXPIRED","status",paid?"PAGO":"EXPIRADO","occurredAt",record.required("occurredAt").asText()));
        } else {
            var ids=new ArrayList<String>();boolean reserved=type.equals("inventory.seats-reserved");
            if(reserved)p.required("assentos").forEach(i->ids.add(i.required("assentoId").asText()));
            else p.required("assentoIds").forEach(i->ids.add(i.asText()));
            var event=p.required("eventoId").asText();
            ws.convertAndSend("/topic/events/"+event+"/seats",Map.of("eventId",event,"seatIds",ids,
                    "type",reserved?"SEATS_RESERVED":"SEATS_RELEASED","status",reserved?"RESERVADO":"LIVRE","occurredAt",record.required("occurredAt").asText()));
        }
    }
}
