CREATE TABLE venue_event (
    id uuid PRIMARY KEY,
    organizer_id uuid NOT NULL,
    name text NOT NULL,
    description text NOT NULL,
    starts_at timestamptz NOT NULL,
    venue text NOT NULL,
    status text NOT NULL CHECK (status IN ('RASCUNHO', 'PUBLICADO')),
    sales_open_at timestamptz
);
CREATE TABLE sector (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES venue_event,
    name text NOT NULL,
    price bigint NOT NULL CHECK (price > 0),
    accessible boolean NOT NULL
);
CREATE TABLE reservation (
    id uuid PRIMARY KEY,
    event_id uuid NOT NULL REFERENCES venue_event,
    user_id uuid NOT NULL,
    status text NOT NULL CHECK (status IN ('ATIVA','CONFIRMADA','EXPIRADA','CANCELADA')),
    created_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL CHECK (expires_at > created_at)
);
CREATE INDEX active_expiration ON reservation(expires_at) WHERE status = 'ATIVA';
CREATE TABLE seat (
    id uuid PRIMARY KEY,
    sector_id uuid NOT NULL REFERENCES sector,
    row_label text NOT NULL,
    seat_number integer NOT NULL CHECK (seat_number > 0),
    status text NOT NULL CHECK (status IN ('LIVRE','RESERVADO','VENDIDO')),
    reservation_id uuid REFERENCES reservation,
    UNIQUE (sector_id, row_label, seat_number),
    CHECK ((status = 'LIVRE') = (reservation_id IS NULL))
);
CREATE INDEX seat_reservation ON seat(reservation_id) WHERE reservation_id IS NOT NULL;
