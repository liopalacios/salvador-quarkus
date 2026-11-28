-- Parámetros y feriados del motor de crédito para los tests (H2; en producción los carga V14__credit_engine.sql)
INSERT INTO credit_parameter (code, param_value, valid_from, valid_to, description) VALUES
    ('TEA_TOPE', 1.1468, DATE '2026-11-01', DATE '2027-04-30', 'test'),
    ('TEA_MORA_FACTOR', 0.15, DATE '2026-01-01', NULL, 'test'),
    ('COMISION_PCT', 0.30, DATE '2026-01-01', NULL, 'test'),
    ('APIS_MONTO', 5, DATE '2026-01-01', NULL, 'test'),
    ('BASE_DIAS', 360, DATE '2026-01-01', NULL, 'test');

INSERT INTO holiday (holiday_date, description) VALUES
    (DATE '2026-11-01', 'Todos los Santos'), (DATE '2026-12-08', 'Inmaculada Concepción'),
    (DATE '2026-12-09', 'Batalla de Ayacucho'), (DATE '2026-12-25', 'Navidad'), (DATE '2027-01-01', 'Año Nuevo'),
    (DATE '2027-03-25', 'Jueves Santo'), (DATE '2027-03-26', 'Viernes Santo'), (DATE '2027-05-01', 'Día del Trabajo'),
    (DATE '2027-06-07', 'Batalla de Arica'), (DATE '2027-06-29', 'San Pedro y San Pablo'),
    (DATE '2027-07-23', 'Día de la Fuerza Aérea'), (DATE '2027-07-28', 'Fiestas Patrias'),
    (DATE '2027-07-29', 'Fiestas Patrias'), (DATE '2027-08-06', 'Batalla de Junín'),
    (DATE '2027-08-30', 'Santa Rosa de Lima'), (DATE '2027-10-08', 'Combate de Angamos'),
    (DATE '2027-11-01', 'Todos los Santos'), (DATE '2027-12-08', 'Inmaculada Concepción'),
    (DATE '2027-12-09', 'Batalla de Ayacucho'), (DATE '2027-12-25', 'Navidad');
