package ticketing.order;

import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class OrderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void 주문_생성후_중복_IdempotencyKey로는_409반환() throws Exception {
        String body = """
            { "eventId": 1, "seatIds": [101, 102] }     
        """;

        String key = "dup-key-123";

        // 1차 호출 → 성공
        mockMvc.perform(post("/ticketing/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body)
                .header("Idempotency-Key", key))
                .andExpect(status().isOk());

        // 2차 호출 → 같은 키 사용 → 중복 에러 (DB unique constraint)
        mockMvc.perform(post("/ticketing/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Idempotency-Key", key))
                .andExpect(status().is4xxClientError()); // 409 Conflict 기대
    }

}
