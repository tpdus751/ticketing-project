import { test, expect } from "@playwright/test";

test("좌석 클릭 → 선점 배지 → 확정 → SOLD 반영", async ({ page }) => {
  // 스테이징/로컬 URL
  await page.goto("http://localhost:5173/events/3");

  // 좌석 그리드에서 첫 AVAILABLE 셀 클릭 (프로젝트 구조에 맞춰 data-testid나 role 사용 권장)
  const seatButton = page.locator('button[role="gridcell"]').first();
  await seatButton.click();

  // 선점 배지 노출
  await expect(page.getByText(/선점됨/)).toBeVisible();

  // 확정 버튼 클릭
  await page.getByRole("button", { name: "선점 좌석 확정" }).click();

  // SOLD 반영(클래스/툴팁 등 프로젝트 스타일에 맞게 조정)
  // 예: 버튼이 disabled가 되거나, SOLD 색상 클래스로 바뀜
  await expect(seatButton).toHaveClass(/bg-neutral-600|cursor-not-allowed/);
});
