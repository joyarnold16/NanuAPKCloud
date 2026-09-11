package com.nanu.aitradingbot;

import org.junit.Test;
import org.json.JSONArray;
import org.json.JSONObject;
import static org.junit.Assert.*;

public class DexDataClientTest {
    private JSONObject pair(DexCandidate c) throws Exception {
        return new JSONObject().put("chainId", c.chain).put("pairAddress", c.pairAddress).put("priceUsd", "1")
            .put("baseToken", new JSONObject().put("address", c.tokenAddress)).put("liquidity", new JSONObject().put("usd", 80000))
            .put("volume", new JSONObject().put("h24", 60000)).put("priceChange", new JSONObject().put("h1", 2).put("h24", 9))
            .put("txns", new JSONObject().put("h24", new JSONObject().put("buys", 100).put("sells", 100)));
    }
    @Test public void exactPairLookupDoesNotUseDiscovery() throws Exception {
        DexCandidate c = PaperFixtures.good(System.currentTimeMillis());
        String response = new JSONObject().put("pairs", new JSONArray().put(pair(c))).toString();
        DexDataClient client = new DexDataClient(url -> {
            assertTrue(url.endsWith("/latest/dex/pairs/" + c.chain + "/" + c.pairAddress)); return response;
        });
        assertEquals(c.pairAddress, client.pair(c.chain, c.pairAddress, c.tokenAddress).pairAddress);
    }
    @Test public void mismatchedBaseTokenAndProviderFailureCannotYieldQuote() throws Exception {
        DexCandidate c = PaperFixtures.good(System.currentTimeMillis());
        JSONObject other = pair(c); other.getJSONObject("baseToken").put("address", c.pairAddress);
        String response = new JSONObject().put("pairs", new JSONArray().put(other)).toString();
        DexDataClient client = new DexDataClient(url -> response);
        assertThrows(IllegalStateException.class, () -> client.pair(c.chain, c.pairAddress, c.tokenAddress));
        DexDataClient offline = new DexDataClient(url -> { throw new Exception("offline"); });
        assertThrows(Exception.class, () -> offline.pair(c.chain, c.pairAddress, c.tokenAddress));
    }
    @Test public void malformedPricesFailClosed() throws Exception {
        DexCandidate c = PaperFixtures.good(System.currentTimeMillis());
        JSONObject o = pair(c).put("priceUsd", "NaN");
        assertThrows(Exception.class, () -> DexDataClient.parsePair(o, System.currentTimeMillis()));
    }
}
