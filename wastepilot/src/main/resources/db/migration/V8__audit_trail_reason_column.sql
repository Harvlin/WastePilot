-- Add dedicated reason column to audit_trail.
-- Background: A prior workaround stored reason text appended to the actor field
-- as "|reason=<text>". This caused truncation risk at the 160-char actor limit
-- and conflated two unrelated concerns into one column.
ALTER TABLE audit_trail ADD COLUMN reason VARCHAR(500);

-- One-time data migration: extract reason from actor field for any existing rows
-- that used the "|reason=" encoding from the previous implementation.
-- After this runs, actor will contain only the actor identifier.
UPDATE audit_trail
  SET reason = SUBSTRING(actor, LOCATE('|reason=', actor) + 8),
      actor  = SUBSTRING(actor, 1, LOCATE('|reason=', actor) - 1)
WHERE actor LIKE '%|reason=%';
