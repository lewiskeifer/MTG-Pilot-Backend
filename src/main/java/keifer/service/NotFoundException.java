package keifer.service;

/**
 * Something asked for by id that this user does not have.
 *
 * These used to be thrown as java.lang.Error, which is the JVM's word for a problem no
 * application should try to handle - an exhausted heap, a missing class. A deck id that is not
 * in the database is an ordinary answer to an ordinary question, and it belongs on the wire as a
 * 404 rather than reaching the browser as a 500 that reads like the server fell over.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }

}
