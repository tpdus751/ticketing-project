// 없으면 새로 만들고, 있으면 아래 케이스만 추가해줘
export function mapErrorToMessage(err: any): string {
  const code = err?.code ?? err?.body?.code;
  const status = err?.status;

  if (code === "RESERVATION_CONFLICT" || status === 409)
    return "이미 다른 사용자가 선점했습니다. 다른 좌석을 선택하세요.";

  if (code === "RESERVATION_EXPIRED" || status === 410)
    return "선점이 만료되었어요. 다시 선택해주세요.";

  if (code === "VALIDATION_FAILED" || status === 422)
    return "판매완료 좌석입니다.";

  return "예약 처리 중 오류가 발생했습니다. 잠시 후 다시 시도하세요.";
}
