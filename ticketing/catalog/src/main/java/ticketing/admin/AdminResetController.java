// src/main/java/ticketing/api/admin/AdminResetController.java
package ticketing.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminResetController {

    private final AdminResetService resetService;

    // POST /admin/test-reset?eventId=1
    @PostMapping("/test-reset")
    public ResponseEntity<Void> reset(@RequestParam("eventId") Long eventId) {
        resetService.resetEvent(eventId);
        return ResponseEntity.ok().build();
    }
}
