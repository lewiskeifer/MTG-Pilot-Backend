package keifer.service;

import org.springframework.http.HttpStatus;

/**
 * A call to TCGplayer that could not be answered.
 *
 * Carries the status the caller should be told, because these fail two ways that want different
 * answers. TCGplayer refusing our credentials is ours to fix and reads as a bad gateway; TCGplayer
 * not holding a card is the caller's and reads as a bad request. Both used to arrive as the same
 * "failed to find card", which sends anyone debugging a stale token hunting for a typo in a card
 * name, and the 401 behind it was dropped rather than chained, so the only way to see the real
 * cause was to attach a debugger.
 */
public class TcgServiceException extends RuntimeException {

    private final HttpStatus status;

    public TcgServiceException(HttpStatus status, String message) {
        this(status, message, null);
    }

    public TcgServiceException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

}
