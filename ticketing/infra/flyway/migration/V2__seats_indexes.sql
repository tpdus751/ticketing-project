-- V2__seats_indexes.sql
-- 좌석 테이블 seat 조회 최적화를 위한 복합 인덱스 추가
CREATE INDEX idx_seats_eventid_rc
    ON seats (event_id, row_no, col_no);
