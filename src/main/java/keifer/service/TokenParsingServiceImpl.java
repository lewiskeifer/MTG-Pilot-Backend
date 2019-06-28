package keifer.service;

import com.google.gson.Gson;
import keifer.service.model.DecodedToken;
import org.apache.commons.codec.binary.Base64;
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

    @Override
    public Long getUserId() {
        String token = httpServletRequest.getHeader("authorization");
        String[] split_string = token.split("\\.");
        String base64EncodedBody = split_string[1];
        Base64 base64Url = new Base64(true);
        String body = new String(base64Url.decode(base64EncodedBody));
        DecodedToken decodedToken = new Gson().fromJson(body, DecodedToken.class);
        return decodedToken.getId();
    }

}
