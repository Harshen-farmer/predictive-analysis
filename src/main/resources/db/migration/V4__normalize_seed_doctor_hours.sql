UPDATE doctor
SET work_end = TIME '23:00:00'
WHERE name IN ('Casey Rivera', 'Quinn Taylor')
  AND work_start >= work_end;
