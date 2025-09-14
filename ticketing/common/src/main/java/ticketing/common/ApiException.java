package ticketing.common;

public class ApiException extends RuntimeException {
    private final String code;
    public ApiException(String code, String message) { super(message); this.code = code; }
    public String code() { return code; }
}