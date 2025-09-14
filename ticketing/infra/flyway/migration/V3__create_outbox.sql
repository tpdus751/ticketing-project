-- Outbox 테이블 (Kafka로 내보낼 이벤트를 임시 저장)
CREATE TABLE outbox (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,             -- 고유 ID
    event_type VARCHAR(100) NOT NULL,                 -- 이벤트 종류 (예: ORDER_CREATED)
    payload JSON NOT NULL,                            -- 이벤트 본문(JSON 직렬화)
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',    -- 상태 (PENDING, SENT, FAILED)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,   -- 생성 시각
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP -- 갱신 시각
);