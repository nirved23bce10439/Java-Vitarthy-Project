package campus.storage;

import java.io.IOException;

public final class DataFormatException extends IOException {
    private static final long serialVersionUID = 1L;
    public DataFormatException(String message, Throwable cause) { super(message, cause); }
}
