-- Dados fictícios para testar o catálogo local. Rodar após a migração do inventário.
BEGIN;
INSERT INTO venue_event (id, organizer_id, name, description, starts_at, venue, status)
VALUES
 ('ad000001-0000-4000-8000-000000000001', 'ad000000-0000-4000-8000-000000000001',
  'Festival Orla', 'Festival de música ao ar livre com dois setores de ingressos.',
  '2027-02-20 18:00:00-03', 'Orla do Guaíba, Porto Alegre', 'PUBLICADO'),
 ('ad000001-0000-4000-8000-000000000002', 'ad000000-0000-4000-8000-000000000001',
  'Noite Instrumental', 'Apresentação de música instrumental em formato de teatro.',
  '2027-03-06 20:00:00-03', 'Teatro Central, Porto Alegre', 'PUBLICADO')
ON CONFLICT (id) DO NOTHING;

INSERT INTO sector (id, event_id, name, price, accessible)
VALUES
 ('ad000002-0000-4000-8000-000000000001','ad000001-0000-4000-8000-000000000001','Pista',6000,true),
 ('ad000002-0000-4000-8000-000000000002','ad000001-0000-4000-8000-000000000001','Arquibancada',9000,false),
 ('ad000002-0000-4000-8000-000000000003','ad000001-0000-4000-8000-000000000002','Plateia',4500,true)
ON CONFLICT (id) DO NOTHING;

INSERT INTO seat (id, sector_id, row_label, seat_number, status)
SELECT gen_random_uuid(), sector.id, row_label, seat_number, 'LIVRE'
FROM sector
CROSS JOIN (VALUES ('A'), ('B')) AS rows(row_label)
CROSS JOIN generate_series(1,8) AS seats(seat_number)
WHERE sector.id IN ('ad000002-0000-4000-8000-000000000001',
                    'ad000002-0000-4000-8000-000000000002',
                    'ad000002-0000-4000-8000-000000000003')
ON CONFLICT (sector_id, row_label, seat_number) DO NOTHING;
COMMIT;
