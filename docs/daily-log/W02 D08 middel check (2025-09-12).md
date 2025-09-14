# 중간점검 (~W02 D08) (2025-09-12)

## ✅ 오늘 한 일

### BE
- **Order 모듈 – SagaWorker/OutboxWorker 개선**  
  - 왜? → 주문 생성 후 결제, 좌석 확정까지 이어지는 흐름을 Jaeger에서 추적 가능하게 만들기 위해.  
  - 그래서 → `ObservationRegistry`와 `W3CTraceContextPropagator`를 적용하여 span 생성/traceparent 헤더 전파 로직 추가.  
  - 결과 → OutboxWorker → Kafka → ReservationConsumer 흐름까지 traceId를 이어주도록 수정. DB insert/update 단계도 Observation으로 감싸 Jaeger에 기록 시도.  

- **Reservation 모듈 – Kafka Consumer 개선**  
  - 왜? → Kafka 메시지 수신 시 Jaeger trace와 이어지도록 traceparent 헤더 복원 필요.  
  - 그래서 → `TextMapGetter<Headers>` 구현을 통해 Kafka Headers에서 traceparent 추출 후 Context 복원. Observation과 연결.  
  - 결과 → Consumer 로직에서 traceId 이어받아 좌석 SOLD/RELEASE 처리까지 Jaeger trace에 기록 시도.  

- **Payment 모듈 – @Observed 적용**  
  - 왜? → Order → Payment → Order 사이의 호출을 하나의 trace로 묶기 위해.  
  - 그래서 → `@Observed(name="payment.authorize")`를 컨트롤러에 적용하여 Micrometer/OTel이 span 자동 생성.  
  - 결과 → Jaeger에서 Payment API 호출이 trace로 기록됨.  

- **TraceIdFilter 개선**  
  - 왜? → FE나 외부 요청이 traceparent 헤더를 보낼 경우 이를 우선 반영하고, 없으면 UUID traceId를 새로 생성하기 위해.  
  - 그래서 → 기존 Trace-Id 기반 필터를 수정하여 traceparent 헤더 → 없으면 UUID 로직으로 변경.  
  - 결과 → FE/BE 요청, Kafka 메시지, 로그 traceId가 일관되게 유지.  

- **Flyway Migration (V6)**  
  - 왜? → Outbox 테이블에 trace_id 컬럼 누락으로 Hibernate 스키마 검증 실패 발생.  
  - 그래서 → `ALTER TABLE outbox ADD COLUMN trace_id VARCHAR(64)` 쿼리를 V6로 추가.  
  - 결과 → 애플리케이션 부팅 정상화.  

### FE
- **Jaeger 관찰**  
  - 왜? → 주문 → 결제 → 좌석 확정 → SSE까지 전체 플로우가 하나의 trace로 연결되는지 확인하기 위해.  
  - 그래서 → FE에서 결제 시나리오 실행 후 Jaeger UI에서 trace 검색.  
  - 결과 → 모듈별 trace는 보이나 아직 완전하게 한 화면에 통합된 trace로 묶이지는 않음.  

### Infra/테스트
- **Jaeger / Prometheus / Grafana 확인**  
  - 왜? → 추적/모니터링 데이터가 실제로 수집되고 있는지 확인하기 위해.  
  - 그래서 → 각 모듈 실행 후 로그 패턴(trace=...)과 Jaeger UI span 확인.  
  - 결과 → traceId는 로그와 Jaeger에서 동일하게 찍히나, trace가 완전히 합쳐지지 않고 모듈 단위로 분리되어 보임.  

---

## 📚 배운 점
- **Observation + Micrometer Tracing 한계**  
  단순히 `Observation.createNotStarted`만으로는 traceparent context가 완전하게 연결되지 않음. RestTemplate, KafkaTemplate, @KafkaListener 등과 Micrometer KafkaObservationHandler/RestTemplateCustomizer까지 같이 맞춰야 Jaeger에서 한 trace로 본다.  

- **스케줄러 기반 SagaWorker 한계**  
  HTTP 요청 context 밖에서 실행되기 때문에 trace가 끊기는 구조. traceId를 DB에 저장/복원하거나 parentCtx를 강제로 이어줘야 한다.  

- **Kafka Consumer traceparent 복원 난이도**  
  Kafka record.headers()에서 traceparent를 꺼내는 건 가능하나, 이를 Observation에 올바르게 연결하려면 Micrometer KafkaObservationHandler 또는 OpenTelemetry Kafka Instrumentation을 쓰는 게 안전하다.  

- **운영 대안**  
  Jaeger 통합 추적이 복잡하다면, traceId를 로그와 메시지에 심고 Kibana/Grafana에서 traceId 기반으로 검색하는 방식도 충분히 실용적이다.  

---

## 🔜 다음 할 일
- (선택) Micrometer Tracing Starter + KafkaObservationHandler를 전체 모듈에 통합 적용 → trace 자동 전파 확인.  
- (선택) RestTemplate 대신 WebClient + Reactor Context를 활용하여 trace context 전파 간소화.  
- (우선) 현재 상태 기준으로 traceId 로그 기반 모니터링 전략 확정 → Jaeger는 보조 지표로 활용.  
