package serverside.logic;

public class InvalidIpException extends RuntimeException {

    public InvalidIpException() {
    }

    public InvalidIpException(String message) {
        super(message);
    }

    public InvalidIpException(String message, Throwable cause) {
        super(message, cause);
    }

    public InvalidIpException(Throwable cause) {
        super(cause);
    }

    public InvalidIpException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
