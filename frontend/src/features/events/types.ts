export type EventSummary = {
  id: number;
  title: string;
  description: string | null;
  dateTime: string; // ISO
};

// src/features/seats/types.ts
export type SeatStatus = "AVAILABLE" | "HELD" | "SOLD";
export type Seat = { id:number; r:number; c:number; price:number; status:SeatStatus };
export type SeatMap = { rows:number; cols:number; seats:Seat[] };
