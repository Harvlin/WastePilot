ALTER TABLE batches ADD COLUMN planned_input_kg DECIMAL(14, 3) NULL;

UPDATE batches b
SET planned_input_kg = COALESCE(
    (
        SELECT SUM(tl.quantity)
        FROM templates t
        JOIN template_lines tl ON tl.template_id = t.id
        WHERE LOWER(t.name) = LOWER(b.template_name)
    ),
    GREATEST(b.output_units * 0.08, 12)
);

ALTER TABLE batches MODIFY planned_input_kg DECIMAL(14, 3) NOT NULL;
