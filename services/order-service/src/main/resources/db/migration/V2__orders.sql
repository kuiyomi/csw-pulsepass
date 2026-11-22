CREATE TABLE purchase_order (
    id uuid PRIMARY KEY, user_id uuid NOT NULL, event_id uuid NOT NULL,
    status text NOT NULL CHECK (status IN ('CRIADO','RESERVADO','AGUARDANDO_PAGAMENTO','PAGO','EXPIRADO')),
    items jsonb NOT NULL DEFAULT '[]', subtotal bigint NOT NULL DEFAULT 0,
    fee bigint NOT NULL DEFAULT 0, total bigint NOT NULL DEFAULT 0,
    expires_at timestamptz, paid_at timestamptz, card_suffix char(4)
);
CREATE TABLE http_request (
    user_id uuid NOT NULL, operation text NOT NULL, request_key uuid NOT NULL,
    body_hash text NOT NULL, order_id uuid NOT NULL, response_status integer, response_body jsonb,
    PRIMARY KEY(user_id,operation,request_key)
);
