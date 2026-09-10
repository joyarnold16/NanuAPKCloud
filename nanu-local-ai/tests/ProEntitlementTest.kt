package com.example.llama

import android.util.Base64
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.security.KeyPairGenerator
import java.security.Signature

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class ProEntitlementTest {
    @Test fun onlySignedCompletedPurchaseForThisAppUnlocksPro() {
        val pair=KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val key=Base64.encodeToString(pair.public.encoded,Base64.NO_WRAP)
        fun verify(state:Int=0,product:String=ProEntitlement.PRODUCT,pkg:String="com.nanu.localai",tamper:Boolean=false):Boolean {
            val receipt=JSONObject().put("packageName",pkg).put("productId",product).put("purchaseState",state).put("purchaseToken","test-token").toString()
            val signer=Signature.getInstance("SHA1withRSA").apply { initSign(pair.private); update(receipt.toByteArray()) }
            return ProEntitlement.verify(key,receipt+if(tamper) " " else "",Base64.encodeToString(signer.sign(),Base64.NO_WRAP),"com.nanu.localai")
        }
        assertTrue(verify())
        assertFalse(verify(state=4))
        assertFalse(verify(state=1))
        assertFalse(verify(product="another_product"))
        assertFalse(verify(pkg="another.app"))
        assertFalse(verify(tamper=true))
        assertFalse(ProEntitlement.verify("","{}","","com.nanu.localai"))
    }
}
