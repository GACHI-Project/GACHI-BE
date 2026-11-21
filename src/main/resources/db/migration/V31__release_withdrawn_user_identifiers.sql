-- 탈퇴 계정의 데이터는 유지하되 인증 식별자는 새 가입에 재사용할 수 있게 해제한다.
DELETE FROM social_accounts s
WHERE s.user_id IN (SELECT id FROM users WHERE status = 'WITHDRAWN')
  AND NOT (
    s.provider = 'KAKAO'
    AND s.connection_status = 'DISCONNECT_PENDING'
    AND EXISTS (
      SELECT 1 FROM kakao_unlink_outbox o
      WHERE o.user_id = s.user_id
        AND o.provider_user_id = s.provider_user_id
        AND o.processed_at IS NULL
    )
  );

UPDATE users
SET email = 'withdrawn-' || id || '@deleted.gachi.invalid',
    login_id = NULL,
    password_hash = NULL,
    phone_number = NULL,
    email_verified_at = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE status = 'WITHDRAWN';
