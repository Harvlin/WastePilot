ALTER TABLE auth_users
    ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'OPERATOR';

UPDATE auth_users au
JOIN user_settings us
    ON UNHEX(REPLACE(us.user_id, '-', '')) = au.id
SET au.role = 'SUPERVISOR'
WHERE LOWER(TRIM(us.role)) = 'supervisor';

-- Fail loudly instead of silently downgrading supervisors if the id join missed any row.
DELIMITER //
CREATE PROCEDURE wp_v13_assert_supervisors_migrated()
BEGIN
    DECLARE expected INT;
    DECLARE actual INT;
    SELECT COUNT(*) INTO expected FROM user_settings WHERE LOWER(TRIM(role)) = 'supervisor';
    SELECT COUNT(*) INTO actual FROM auth_users WHERE role = 'SUPERVISOR';
    IF actual < expected THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'V13: supervisor roles in user_settings could not all be matched to auth_users';
    END IF;
END//
DELIMITER ;
CALL wp_v13_assert_supervisors_migrated();
DROP PROCEDURE wp_v13_assert_supervisors_migrated;

ALTER TABLE user_settings
    DROP COLUMN role;
