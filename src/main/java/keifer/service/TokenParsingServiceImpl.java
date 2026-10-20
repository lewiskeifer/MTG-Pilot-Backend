package keifer.service;

import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;


@Service
public class TokenParsingServiceImpl implements TokenParsingService {

    private final HttpServletRequest httpServletRequest;

    @Autowired
    public TokenParsingServiceImpl(HttpServletRequest httpServletRequest) {
        this.httpServletRequest = httpServletRequest;
    }

    /*
     * The caller's id, from the claims JwtFilter left on the request once it had checked the
     * token's signature. This used to base64-decode the token's body straight from the header
     * without checking anything, so on any route the filter did not cover, a hand-made token
     * could name whichever user it liked.
     *
     * Null where no checked token came with the request, which every permission check reads as
     * "not this user" - so a route that slips out from behind the filter fails closed.
     */
    @Override
    public Long getUserId() {

        Object claims = httpServletRequest.getAttribute("claims");

        if (!(claims instanceof Claims)) {
            return null;
        }

        Object id = ((Claims) claims).get("id");

        return id instanceof Number ? ((Number) id).longValue() : null;
    }

}
