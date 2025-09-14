package ticketing.catalog.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity @Table(name = "seats")
@Getter @NoArgsConstructor
public class Seat {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id")
    private Long eventId;

    @Column(name = "row_no")
    private int rowNo;

    @Column(name = "col_no")
    private int colNo;

    private int price;

    @Enumerated(EnumType.STRING)
    private Status status;

    public enum Status { AVAILABLE, HELD, SOLD }
}