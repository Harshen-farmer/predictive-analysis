INSERT INTO department (name, total_beds, avg_stay_days, patients_per_doctor, created_at, updated_at, created_by)
VALUES
    ('Emergency', 12, 1.0, 18, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('ICU', 10, 7.0, 4, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('General Ward', 24, 4.5, 12, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('Pediatrics', 12, 3.0, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('Maternity', 12, 3.5, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'),
    ('Surgery', 12, 5.0, 8, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system');

INSERT INTO doctor (name, department_id, work_start, work_end, slot_minutes, on_duty, created_at, updated_at, created_by)
SELECT staff.name, department.id, staff.work_start, staff.work_end, staff.slot_minutes, TRUE,
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system'
FROM (
    SELECT 'Emergency' AS department_name, 'Avery Brooks' AS name, TIME '07:00:00' AS work_start, TIME '15:00:00' AS work_end, 20 AS slot_minutes
    UNION ALL SELECT 'Emergency', 'Jordan Lee', TIME '08:00:00', TIME '16:00:00', 20
    UNION ALL SELECT 'Emergency', 'Morgan Patel', TIME '15:00:00', TIME '23:00:00', 20
    UNION ALL SELECT 'ICU', 'Taylor Chen', TIME '07:00:00', TIME '19:00:00', 30
    UNION ALL SELECT 'ICU', 'Riley Morgan', TIME '07:00:00', TIME '19:00:00', 30
    UNION ALL SELECT 'ICU', 'Casey Rivera', TIME '19:00:00', TIME '07:00:00', 30
    UNION ALL SELECT 'General Ward', 'Jamie Wilson', TIME '08:00:00', TIME '16:00:00', 20
    UNION ALL SELECT 'General Ward', 'Cameron Davis', TIME '08:00:00', TIME '16:00:00', 20
    UNION ALL SELECT 'General Ward', 'Skyler Kim', TIME '12:00:00', TIME '20:00:00', 20
    UNION ALL SELECT 'General Ward', 'Quinn Taylor', TIME '16:00:00', TIME '00:00:00', 20
    UNION ALL SELECT 'Pediatrics', 'Rowan Garcia', TIME '08:00:00', TIME '16:00:00', 20
    UNION ALL SELECT 'Pediatrics', 'Emerson Clark', TIME '09:00:00', TIME '17:00:00', 20
    UNION ALL SELECT 'Pediatrics', 'Drew Martin', TIME '12:00:00', TIME '20:00:00', 20
    UNION ALL SELECT 'Maternity', 'Reese Ahmed', TIME '07:00:00', TIME '15:00:00', 30
    UNION ALL SELECT 'Maternity', 'Parker Evans', TIME '08:00:00', TIME '16:00:00', 30
    UNION ALL SELECT 'Maternity', 'Alex Johnson', TIME '15:00:00', TIME '23:00:00', 30
    UNION ALL SELECT 'Surgery', 'Robin Thomas', TIME '07:00:00', TIME '15:00:00', 30
    UNION ALL SELECT 'Surgery', 'Kendall White', TIME '08:00:00', TIME '16:00:00', 30
    UNION ALL SELECT 'Surgery', 'Sam Nguyen', TIME '12:00:00', TIME '20:00:00', 30
) AS staff
JOIN department ON department.name = staff.department_name;

INSERT INTO bed (bed_number, department_id, status, created_at, updated_at, created_by)
SELECT UPPER(SUBSTRING(department.name, 1, 3)) || '-' || CAST(bed_numbers.bed_num AS VARCHAR(3)),
       department.id,
       'AVAILABLE',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       'system'
FROM department
JOIN (VALUES
    (1), (2), (3), (4), (5), (6), (7), (8),
    (9), (10), (11), (12), (13), (14), (15), (16),
    (17), (18), (19), (20), (21), (22), (23), (24)
) AS bed_numbers(bed_num) ON bed_numbers.bed_num <= department.total_beds;

INSERT INTO daily_load (department_id, date, patient_count, created_at, updated_at, created_by)
SELECT department.id,
       CURRENT_DATE - load_dates.day_offset,
       CASE department.name
           WHEN 'Emergency' THEN 75
           WHEN 'ICU' THEN 18
           WHEN 'General Ward' THEN 82
           WHEN 'Pediatrics' THEN 30
           WHEN 'Maternity' THEN 24
           WHEN 'Surgery' THEN 38
       END
       + CASE WHEN EXTRACT(DOW FROM CURRENT_DATE - load_dates.day_offset) BETWEEN 1 AND 5 THEN 8 ELSE -6 END
       + MOD(load_dates.day_offset, 5),
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       'system'
FROM department
CROSS JOIN (VALUES
    (0), (1), (2), (3), (4), (5), (6), (7), (8), (9),
    (10), (11), (12), (13), (14), (15), (16), (17), (18), (19),
    (20), (21), (22), (23), (24), (25), (26), (27), (28), (29),
    (30), (31), (32), (33), (34), (35), (36), (37), (38), (39),
    (40), (41), (42), (43), (44), (45), (46), (47), (48), (49),
    (50), (51), (52), (53), (54), (55), (56), (57), (58), (59),
    (60), (61), (62), (63), (64), (65), (66), (67), (68), (69),
    (70), (71), (72), (73), (74), (75), (76), (77), (78), (79),
    (80), (81), (82), (83), (84), (85), (86), (87), (88), (89)
) AS load_dates(day_offset);
