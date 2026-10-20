package keifer.configuration;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import keifer.Application;
import keifer.service.model.YAMLConfig;
import org.junit.Before;
import org.junit.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.ServletException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The check every protected request passes through: a signed token, or no further.
 */
public class JwtFilterTest {

    private static final String SECRET = "test-secret";

    private JwtFilter filter;
    private MockFilterChain chain;
    private MockHttpServletResponse response;

    @Before
    public void setUp() {

        YAMLConfig yamlConfig = new YAMLConfig();
        yamlConfig.setSecretKey(SECRET);

        filter = new JwtFilter(yamlConfig);
        chain = new MockFilterChain();
        response = new MockHttpServletResponse();
    }

    @Test
    public void letsAPreflightThroughWithoutAToken() throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/manager/users/1/decks");

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    public void refusesARequestWithNoToken() {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/manager/users/1/decks");

        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isInstanceOf(ServletException.class);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    public void refusesAHeaderThatIsNotABearerToken() {

        MockHttpServletRequest request = get("Basic dXNlcjpwYXNz");

        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isInstanceOf(ServletException.class);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    public void refusesATokenSignedWithAnotherKey() {

        MockHttpServletRequest request = get("Bearer " + token("someone-elses-secret", 1L));

        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isInstanceOf(ServletException.class);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    public void refusesATokenThatIsNotAToken() {

        MockHttpServletRequest request = get("Bearer not.a.token");

        // Not a ServletException today, but refused all the same: the request goes no further
        assertThatThrownBy(() -> filter.doFilter(request, response, chain)).isInstanceOf(Exception.class);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    public void passesASignedTokenOnWithItsClaims() throws Exception {

        MockHttpServletRequest request = get("Bearer " + token(SECRET, 42L));

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isSameAs(request);
        Claims claims = (Claims) request.getAttribute("claims");
        assertThat(((Number) claims.get("id")).longValue()).isEqualTo(42L);
    }

    // Was /manager/* only, so nothing checked the token on the sealed routes
    @Test
    public void guardsTheSealedEndpointsToo() {

        FilterRegistrationBean registration = new Application().jwtFilter(new YAMLConfig());

        assertThat(registration.getUrlPatterns()).contains("/manager/*", "/sealed/*");
    }

    private static MockHttpServletRequest get(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/manager/users/1/decks");
        request.addHeader("authorization", authorization);
        return request;
    }

    private static String token(String secret, Long userId) {
        return Jwts.builder().setSubject("ann").claim("id", userId)
                .signWith(SignatureAlgorithm.HS256, secret).compact();
    }

}
