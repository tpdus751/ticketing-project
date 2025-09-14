-- Orders 테이블 (사용자 주문 정보)
CREATE TABLE orders (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,              -- 주문 PK
    event_id BIGINT NOT NULL,                          -- 공연 ID
    seat_ids JSON NOT NULL,                            -- 좌석 ID 리스트(JSON 배열)
    status VARCHAR(20) NOT NULL DEFAULT 'CREATED',     -- 주문 상태 (CREATED, CONFIRMED 등)
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,      -- Idempotency 보장 키
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,    -- 생성 시각
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP -- 갱신 시각
);
