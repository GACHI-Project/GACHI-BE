-- 가정통신문 여러 장 업로드 지원.
-- 기존 file_key(단일 대표 키)는 그대로 두고, 페이지 순서를 유지한 전체 키 목록을 JSONB로 보관한다.
-- 기존 레코드는 file_keys가 NULL이며, Newsletter.resolveFileKeys()가 file_key 단건으로 대체 반환한다.
-- date_candidates / title_i18n과 동일한 JSONB 보관 방식을 따른다.
ALTER TABLE newsletter
    ADD COLUMN IF NOT EXISTS file_keys JSONB NULL;
