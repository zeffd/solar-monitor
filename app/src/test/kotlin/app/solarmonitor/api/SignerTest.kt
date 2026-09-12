package app.solarmonitor.api

import org.junit.Assert.assertEquals
import org.junit.Test

class SignerTest {
    private val salt = 1_700_000_000_000L
    private val pwdSha1 = "5baa61e4c9b93f3f0682250b6cf8331b7ee68fd8" // sha1("password")

    @Test
    fun sha1HexOfKnownStrings() {
        assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", Signer.sha1Hex("abc"))
        assertEquals("da39a3ee5e6b4b0d3255bfef95601890afd80709", Signer.sha1Hex(""))
        assertEquals(pwdSha1, Signer.sha1Hex("password"))
    }

    @Test
    fun encodeMatchesEncodeUriComponentForSpacePlusAndQuote() {
        assertEquals("a%20b%2Bc%27d", Signer.encode("a b+c'd"))
        assertEquals("alice", Signer.encode("alice"))
    }

    @Test
    fun loginActionHasUserAndCompanyKey() {
        assertEquals("&action=auth&usr=alice&company-key=bnrl_frRFjEz8Mkn", Signer.loginAction("alice"))
    }

    @Test
    fun loginUrlIsSignedWithSaltPasswordHashAndAction() {
        val url = Signer.loginUrl("web.shinemonitor.com", "alice", pwdSha1, salt)
        assertEquals(
            "https://web.shinemonitor.com/public/?sign=205b174ad1e81b87fad7f9379fab6f243138b194" +
                "&salt=1700000000000&action=auth&usr=alice&company-key=bnrl_frRFjEz8Mkn",
            url,
        )
    }

    @Test
    fun loginUrlSignsTheEncodedUsername() {
        val url = Signer.loginUrl("web.shinemonitor.com", "a b+c'd", pwdSha1, salt)
        assertEquals(
            "https://web.shinemonitor.com/public/?sign=a920285e9805f736e34d948193790532d4f6dfc4" +
                "&salt=1700000000000&action=auth&usr=a%20b%2Bc%27d&company-key=bnrl_frRFjEz8Mkn",
            url,
        )
    }

    @Test
    fun signedUrlIsSignedWithSaltSecretTokenAndAction() {
        val action = "&action=webQueryPlants&page=0&pagesize=10&i18n=en_US"
        val url = Signer.signedUrl("web.shinemonitor.com", "t0ken", "s3cret", action, salt)
        assertEquals(
            "https://web.shinemonitor.com/public/?sign=7e484ededf514f709f5b5eff68d4b23af7153d2a" +
                "&salt=1700000000000&token=t0ken&action=webQueryPlants&page=0&pagesize=10&i18n=en_US",
            url,
        )
    }
}
