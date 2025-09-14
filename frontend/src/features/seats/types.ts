export type Seat = {
  id: number;
  r: number;
  c: number;
  price: number;
  status: "AVAILABLE" | "HELD" | "SOLD";
};

export type SeatMap = {
  rows: number;
  cols: number;
  seats: Seat[];
  // BE가 더 주는 필드(eventId, version)는 있어도 무관(타입에 없어도 동작)
};
