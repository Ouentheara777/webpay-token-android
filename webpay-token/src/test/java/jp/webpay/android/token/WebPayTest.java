package jp.webpay.android.token;

import org.apache.http.HttpEntity;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.message.BasicHeader;
import org.apache.maven.artifact.ant.shaded.IOUtil;
import org.apache.tools.ant.filters.StringInputStream;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;
import org.robolectric.tester.org.apache.http.HttpResponseStub;
import org.robolectric.tester.org.apache.http.TestHttpResponse;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jp.webpay.android.token.model.AccountAvailability;
import jp.webpay.android.token.model.CardType;
import jp.webpay.android.token.model.ErrorResponse;
import jp.webpay.android.token.model.RawCard;
import jp.webpay.android.token.model.StoredCard;
import jp.webpay.android.token.model.Token;
import jp.webpay.android.token.ui.RobolectricTestRunnerWithDummyResources;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

@Config(manifest = "./src/main/AndroidManifestTest.xml", emulateSdk = 18)
@RunWith(RobolectricTestRunnerWithDummyResources.class)
public class WebPayTest {
    private WebPay webpay;

    @Before
    public void prepareWebPay() {
        webpay = new WebPay("test_public_dummykey");
    }

    @Rule
    public final ExpectedException thrown = ExpectedException.none();

    @Test
    public void createTokenReturnsTokenObject() throws Exception {
        Robolectric.addPendingHttpResponse(ApiSample.tokenResponse);
        Token token = createToken(ApiSample.testCard);
        assertEquals("tok_3ybc93ckR01qeKx", token.id);
        assertEquals("token", token.object);
        assertEquals(false, token.livemode);
        assertEquals(1396007350L, (long)token.created);
        assertEquals(false, token.used);
        StoredCard card = token.card;
        assertEquals("card", card.object);
        assertEquals(2020, (int)card.expYear);
        assertEquals(8, (int)card.expMonth);
        assertEquals("0000000000000000000000000000000000000000", card.fingerprint);
        assertEquals("TEST USER", card.name);
        assertEquals("JP", card.country);
        assertEquals(CardType.VISA, card.type);
        assertEquals("pass", card.cvcCheck);
        assertEquals("0123", card.last4);
    }

    @Test
    public void createTokenSendCorrectRequest() throws Exception {
        Robolectric.addPendingHttpResponse(ApiSample.tokenResponse);
        createToken(ApiSample.testCard);

        HttpRequest request = Robolectric.getSentHttpRequest(0);
        assertEquals("POST", request.getRequestLine().getMethod());
        assertEquals("https://api.webpay.jp/v1/tokens", request.getRequestLine().getUri());
        assertEquals("application/json", request.getFirstHeader("Content-Type").getValue());
        assertEquals("Bearer test_public_dummykey", request.getFirstHeader("Authorization").getValue());
        assertEquals("WebPayTokenAndroid/" + BuildConfig.VERSION_NAME + " Android/unknown", request.getFirstHeader("User-Agent").getValue());

        String requestBody = IOUtil.toString(((HttpPost) request).getEntity().getContent(), "UTF-8");
        JSONObject value = (JSONObject) new JSONTokener(requestBody).nextValue();
        JSONObject card = value.getJSONObject("card");
        assertEquals("4242-4242-4242-0123", card.getString("number"));
        assertEquals("012", card.getString("cvc"));
        assertEquals("TEST USER", card.getString("name"));
        assertEquals(8, card.getInt("exp_month"));
        assertEquals(2020, card.getInt("exp_year"));
    }

    @Test
    public void createTokenSendsRequestInSpecifiedLanguage() throws Exception {
        Robolectric.addPendingHttpResponse(ApiSample.tokenResponse);
        createToken(ApiSample.testCard);
        HttpRequest request = Robolectric.getSentHttpRequest(0);
        assertEquals("en", request.getFirstHeader("Accept-Language").getValue());

        Robolectric.addPendingHttpResponse(ApiSample.tokenResponse);
        this.webpay.setLanguage("ja");
        createToken(ApiSample.testCard);
        request = Robolectric.getSentHttpRequest(1);
        assertEquals("ja", request.getFirstHeader("Accept-Language").getValue());
    }

    @Test
    public void createTokenReturnsCardErrorResponse() throws Exception {
        Robolectric.addPendingHttpResponse(ApiSample.cardErrorResponse);
        Throwable throwable = createTokenThenError(ApiSample.testCard);
        assertThat(throwable, instanceOf(ErrorResponseException.class));
        ErrorResponse error = ((ErrorResponseException) throwable).getResponse();
        assertEquals(error.statusCode, 402);
        assertEquals(error.message, "The security code provided is invalid. For Visa, MasterCard, JCB, and Diners Club, enter the last 3 digits on the back of your card. For American Express, enter the 4 digits printed above your number.");
        assertEquals(error.causedBy, "buyer");
        assertEquals(error.param, "cvc");
        assertEquals(error.type, "card_error");
        assertEquals(error.code, "invalid_cvc");
    }

    @Test
    public void createTokenReturnsServerErrorResponse() throws Exception {
        Robolectric.addPendingHttpResponse(ApiSample.serverErrorResponse);
        Throwable throwable = createTokenThenError(ApiSample.testCard);
        assertThat(throwable, instanceOf(ErrorResponseException.class));
        ErrorResponse error = ((ErrorResponseException) throwable).getResponse();
        assertEquals(error.statusCode, 500);
        assertEquals(error.message, "API server is currently unavailable");
        assertEquals(error.causedBy, "service");
        assertEquals(error.param, null);
        assertEquals(error.type, "api_error");
        assertEquals(error.code, null);
    }

    @Test
    public void createTokenReturnsJSONException() throws Exception {
        TestHttpResponse response = new TestHttpResponse(201, "{:}",
                new BasicHeader("Content-Type", "application/json"));
        Robolectric.addPendingHttpResponse(response);
        Throwable throwable = createTokenThenError(ApiSample.testCard);
        assertThat(throwable, instanceOf(JSONException.class));
        assertEquals(throwable.getMessage(), "Expected literal value at character 1 of {:}");
    }

    @Test
    public void createTokenReturnsConnectionError() throws Exception {
        HttpResponseStub response = new TestHttpResponse(200, "") {
            @Override
            public HttpEntity getEntity() {
             return new InputStreamEntity(new StringInputStream("foo"), 3) {
                 @Override
                 public InputStream getContent() throws IOException {
                     throw new IOException("Test exception");
                 }
             };
            }
        };
        Robolectric.addPendingHttpResponse(response);
        Throwable throwable = createTokenThenError(ApiSample.testCard);
        assertThat(throwable, instanceOf(IOException.class));
        assertEquals(throwable.getMessage(), "Test exception");
    }

    @Test
    public void createTokenRaiseErrorForNullCard() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        createToken(null);
    }

    @Test
    public void createTokenRaiseErrorForNullListener() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        webpay.createToken(ApiSample.testCard, null);
    }

    protected Token createToken(RawCard card) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Token[] result = new Token[1];
        WebPayListener<Token> listener = new WebPayListener<Token>() {
            @Override
            public void onCreate(Token token) {
                result[0] = token;
                latch.countDown();
            }

            @Override
            public void onException(Throwable cause) {
                fail("Error is not acceptable " + cause.getMessage());
            }
        };
        webpay.createToken(card, listener);
        latch.await(1, TimeUnit.SECONDS);
        return result[0];
    }

    protected Throwable createTokenThenError(RawCard card) throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] result = new Throwable[1];
        WebPayListener<Token> listener = new WebPayListener<Token>() {
            @Override
            public void onCreate(Token token) {
                fail("Token response is not acceptable");
            }

            @Override
            public void onException(Throwable cause) {
                result[0] = cause;
                latch.countDown();
            }
        };
        webpay.createToken(card, listener);
        latch.await(1, TimeUnit.SECONDS);
        return result[0];
    }

    @Test
    public void retrieveAvailabilityReturnsAccountAvailability() throws Exception {
        Robolectric.addPendingHttpResponse(ApiSample.availabilityResponse);
        AccountAvailability availability = retrieveAvailabilityThenSuccess();
        assertThat(availability.currenciesSupported, contains("jpy"));
        assertThat(availability.cardTypesSupported,
                contains(CardType.VISA, CardType.MASTERCARD, CardType.JCB, CardType.AMERICAN_EXPRESS, CardType.DINERS_CLUB));
    }

    @Test
    public void retrieveAvailabilityRequestsCorrectPath() throws Exception {
        Robolectric.addPendingHttpResponse(ApiSample.availabilityResponse);
        retrieveAvailabilityThenSuccess();
        HttpRequest request = Robolectric.getSentHttpRequest(0);
        assertEquals("GET", request.getRequestLine().getMethod());
        assertEquals("https://api.webpay.jp/v1/account/availability", request.getRequestLine().getUri());
        assertEquals("Bearer test_public_dummykey", request.getFirstHeader("Authorization").getValue());
    }

    @Test
    public void retrieveAvailabilityHandlesServerError() throws Exception {
        Robolectric.addPendingHttpResponse(ApiSample.serverErrorResponse);
        Throwable throwable = retrieveAvailabilityThenError();
        assertThat(throwable, instanceOf(ErrorResponseException.class));
        ErrorResponse error = ((ErrorResponseException) throwable).getResponse();
        assertEquals(error.statusCode, 500);
        assertEquals(error.message, "API server is currently unavailable");
        assertEquals(error.causedBy, "service");
        assertEquals(error.param, null);
        assertEquals(error.type, "api_error");
        assertEquals(error.code, null);
    }

    @Test
    public void retrieveAvailabilityRaiseErrorForNullListener() throws Exception {
        thrown.expect(IllegalArgumentException.class);
        webpay.retrieveAvailability(null);
    }

    protected AccountAvailability retrieveAvailabilityThenSuccess() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final AccountAvailability[] result = new AccountAvailability[1];
        WebPayListener<AccountAvailability> listener = new WebPayListener<AccountAvailability>() {
            @Override
            public void onCreate(AccountAvailability availability) {
                result[0] = availability;
                latch.countDown();
            }

            @Override
            public void onException(Throwable cause) {
                fail("Error is not acceptable " + cause.getMessage());
            }
        };
        webpay.retrieveAvailability(listener);
        latch.await(1, TimeUnit.SECONDS);
        return result[0];
    }

    protected Throwable retrieveAvailabilityThenError() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final Throwable[] result = new Throwable[1];
        WebPayListener<AccountAvailability> listener = new WebPayListener<AccountAvailability>() {
            @Override
            public void onCreate(AccountAvailability availability) {
                fail("AccountAvailability response is not acceptable");
            }

            @Override
            public void onException(Throwable cause) {
                result[0] = cause;
                latch.countDown();
            }
        };
        webpay.retrieveAvailability(listener);
        latch.await(1, TimeUnit.SECONDS);
        return result[0];
    }

}
