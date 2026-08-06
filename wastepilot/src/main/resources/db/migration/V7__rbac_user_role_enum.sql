-- Normalize existing user_settings.role rows to OPERATOR / SUPERVISOR
-- Default anything not matching SUPERVISOR (case-insensitive) to OPERATOR.
UPDATE user_settings 
SET role = CASE 
    WHEN LOWER(TRIM(role)) = 'supervisor' THEN 'SUPERVISOR' 
    ELSE 'OPERATOR' 
END;

-- Shrink column constraint to VARCHAR(16) to match enum max length
ALTER TABLE user_settings MODIFY role VARCHAR(16) NOT NULL;
