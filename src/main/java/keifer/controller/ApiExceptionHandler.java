package keifer.controller;

import keifer.service.NotFoundException;
import keifer.service.TcgServiceException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import javax.security.sasl.AuthenticationException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One place where the exceptions this application raises are logged and answered.
 *
 * Before this, everything the services threw arrived at the browser as an unexplained 500 - a
 * card the catalogue does not hold, a password that does not match and TCGplayer refusing our
 * token were indistinguishable from each other and from a genuine bug. Each is now logged with
 * the request that caused it, at a level that matches whose problem it is, and answered with a
 * status that says which kind of failure it was.
 *
 * Only the types this application throws are handled here. Spring's own exceptions - an unknown
 * path, a method that does not apply - already map to the right statuses, and catching Exception
 * outright would take those over and answer 500 for all of them.
 */
@Slf4j
@RestControllerAdvice
public class ApiExceptionHandler {

    /** TCGplayer could not answer. The message names which of the two ways it failed. */
    @ExceptionHandler(TcgServiceException.class)
    public ResponseEntity<Map<String, Object>> onTcgFailure(TcgServiceException e, HttpServletRequest request) {

        /*
         * Already logged in detail where it was raised, with the upstream status attached. This
         * line ties that to the request that caused it, which the service layer cannot see.
         */
        log.warn("{} {} failed: {}", request.getMethod(), request.getRequestURI(), e.getMessage());

        return body(e.getStatus(), e.getMessage(), request);
    }

    /*
     * What the services throw to turn a caller away: an empty quantity, a username in use, a
     * password that does not match. The message is written to be read by whoever sent it, and
     * the frontend puts it straight on screen, so it is passed through as-is.
     */
    @ExceptionHandler(ServletException.class)
    public ResponseEntity<Map<String, Object>> onRejected(ServletException e, HttpServletRequest request) {

        log.warn("{} {} rejected: {}", request.getMethod(), request.getRequestURI(), e.getMessage());

        return body(HttpStatus.BAD_REQUEST, e.getMessage(), request);
    }

    /** An id that is not in the database. An ordinary answer, not a fault. */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, Object>> onNotFound(NotFoundException e, HttpServletRequest request) {

        log.info("{} {}: {}", request.getMethod(), request.getRequestURI(), e.getMessage());

        return body(HttpStatus.NOT_FOUND, e.getMessage(), request);
    }

    /** Asking after somebody else's decks. Logged at a level worth noticing. */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> onUnauthorized(AuthenticationException e, HttpServletRequest request) {

        log.warn("{} {} refused: {}", request.getMethod(), request.getRequestURI(), e.getMessage());

        return body(HttpStatus.FORBIDDEN, e.getMessage(), request);
    }

    /*
     * The fields Spring Boot's own error body carries, in its order. The frontend reads
     * `message` off this and shows it, so the shape is kept rather than improved on.
     */
    private ResponseEntity<Map<String, Object>> body(HttpStatus status, String message, HttpServletRequest request) {

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", new Date());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message == null ? status.getReasonPhrase() : message);
        body.put("path", request.getRequestURI());

        return ResponseEntity.status(status).body(body);
    }

}
