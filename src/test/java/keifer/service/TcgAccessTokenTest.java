package keifer.service;

import org.junit.Test;
import org.springframework.http.HttpStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Reading the access token out of TCGplayer's token response.
 *
 * This used to be substring(17, 343), which is right only while access_token is the first field
 * and the token is exactly 326 characters. Any other shape produced a plausible looking string
 * that was then rejected on every call made with it, so a bad token read here surfaced as a 401
 * somewhere else entirely, hours later. These pin down that a token is either read correctly or
 * refused outright.
 */
public class TcgAccessTokenTest {

    /** Long enough to be realistic; TCGplayer's are around 326 characters. */
    private static final String TOKEN = repeat("aA1", 110);

    @Test
    public void readsTheTokenFromTheUsualResponse() {

        String body = "{\"access_token\":\"" + TOKEN + "\",\"token_type\":\"bearer\","
                + "\"expires_in\":1209599,\".issued\":\"Fri, 12 Sep 2026 00:00:00 GMT\"}";

        assertEquals(TOKEN, TcgServiceImpl.readAccessToken(body));
    }

    @Test
    public void readsTheTokenWhereverItSits() {

        // The offset the old code assumed only held while this field came first
        String body = "{\"token_type\":\"bearer\",\"expires_in\":1209599,\"access_token\":\"" + TOKEN + "\"}";

        assertEquals(TOKEN, TcgServiceImpl.readAccessToken(body));
    }

    @Test
    public void readsATokenOfAnyLength() {

        String shortToken = "abc123";

        assertEquals(shortToken, TcgServiceImpl.readAccessToken("{\"access_token\":\"" + shortToken + "\"}"));
    }

    @Test
    public void refusesAResponseCarryingNoToken() {
        // What TCGplayer answers when the API keys are wrong
        expectRefusal("{\"error\":\"invalid_client\"}", "invalid_client");
    }

    @Test
    public void refusesAnEmptyToken() {
        expectRefusal("{\"access_token\":\"\"}", "access_token");
    }

    @Test
    public void refusesSomethingThatIsNotJson() {
        expectRefusal("<html>Service Unavailable</html>", "html");
    }

    @Test
    public void refusesAnEmptyBody() {
        expectRefusal("", "empty");
    }

    /*
     * Every refusal has to be loud and has to carry enough of the answer to recognise it. A
     * token that is quietly wrong is the failure this whole class exists to prevent.
     */
    private void expectRefusal(String body, String expectedInMessage) {

        try {
            String token = TcgServiceImpl.readAccessToken(body);
            fail("Read a token of \"" + token + "\" out of: " + body);
        } catch (TcgServiceException e) {
            assertEquals(HttpStatus.BAD_GATEWAY, e.getStatus());
            assertTrue("Message does not mention " + expectedInMessage + ": " + e.getMessage(),
                    e.getMessage().toLowerCase().contains(expectedInMessage.toLowerCase()));
        }
    }

    private static String repeat(String text, int times) {

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < times; ++i) {
            builder.append(text);
        }

        return builder.toString();
    }

}
