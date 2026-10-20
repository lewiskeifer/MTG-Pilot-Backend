package keifer.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import keifer.configuration.JwtFilter;
import keifer.service.model.YAMLConfig;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who the caller is, as every permission check asks it.
 *
 * Only ever from a token JwtFilter has checked. This used to decode the token's body straight
 * from the header, so a hand-made token could claim to be anyone on a route the filter missed.
 */
public class TokenParsingServiceImplTest {

    private static final String SECRET = "test-secret";

    @Test
    public void readsTheIdFromATokenTheFilterChecked() throws Exception {

        MockHttpServletRequest request = request("Bearer " + token(SECRET, 42L));

        // Through the filter, as a real request goes
        YAMLConfig yamlConfig = new YAMLConfig();
        yamlConfig.setSecretKey(SECRET);
        new JwtFilter(yamlConfig).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(new TokenParsingServiceImpl(request).getUserId()).isEqualTo(42L);
    }

    @Test
    public void ignoresATokenNothingHasChecked() {

        // A forged token: the right shape, claiming user 1, signed with a key we never issued
        MockHttpServletRequest request = request("Bearer " + token("forged", 1L));

        assertThat(new TokenParsingServiceImpl(request).getUserId()).isNull();
    }

    @Test
    public void knowsNoOneWithoutAToken() {

        assertThat(new TokenParsingServiceImpl(new MockHttpServletRequest()).getUserId()).isNull();
    }

    @Test
    public void knowsNoOneWhereTheCheckedTokenCarriesNoId() {

        MockHttpServletRequest request = new MockHttpServletRequest();
        Claims claims = Jwts.claims().setSubject("ann");
        request.setAttribute("claims", claims);

        assertThat(new TokenParsingServiceImpl(request).getUserId()).isNull();
    }

    private static MockHttpServletRequest request(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/sealed/1");
        request.addHeader("authorization", authorization);
        return request;
    }

    private static String token(String secret, Long userId) {
        return Jwts.builder().setSubject("ann").claim("id", userId)
                .signWith(SignatureAlgorithm.HS256, secret).compact();
    }

}
