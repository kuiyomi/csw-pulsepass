CREATE TABLE organizer_request (
    id uuid PRIMARY KEY, user_id uuid NOT NULL, company text NOT NULL, document text NOT NULL, justification text NOT NULL,
    status text NOT NULL CHECK(status IN ('PENDENTE','APROVADA','REJEITADA')), created_at timestamptz NOT NULL DEFAULT now(),
    decided_by uuid, decided_at timestamptz, reason text
);
CREATE UNIQUE INDEX active_request ON organizer_request(user_id) WHERE status IN ('PENDENTE','APROVADA');
CREATE TABLE organizer_audit (
    request_id uuid PRIMARY KEY REFERENCES organizer_request, actor uuid NOT NULL, action text NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(), reason text
);
