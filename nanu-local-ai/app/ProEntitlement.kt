package com.example.llama

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

/** Cache the signed Play receipt, never an editable "isPro" flag. */
object ProEntitlement {
    const val PRODUCT = "nanu_pro_lifetime"
    fun publicKey(context: Context) = context.getString(R.string.nanu_play_public_key).trim()
    fun enabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences("nanu_purchase", 0)
        return verify(publicKey(context), prefs.getString("receipt", "").orEmpty(),
            prefs.getString("signature", "").orEmpty(), context.packageName)
    }
    fun save(context: Context, receipt: String, signature: String) {
        require(verify(publicKey(context), receipt, signature, context.packageName)) { "Purchase could not be verified" }
        check(context.getSharedPreferences("nanu_purchase", 0).edit()
            .putString("receipt", receipt).putString("signature", signature).commit()) { "Could not save purchase. Restore it when storage is available." }
    }
    fun clear(context: Context) { context.getSharedPreferences("nanu_purchase", 0).edit().clear().commit() }
    internal fun verify(key: String, receipt: String, signature: String, packageName: String): Boolean = runCatching {
        if (key.isBlank() || receipt.isBlank() || signature.isBlank()) return false
        val verifier = Signature.getInstance("SHA1withRSA")
        verifier.initVerify(KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(Base64.decode(key, Base64.DEFAULT))))
        verifier.update(receipt.toByteArray(Charsets.UTF_8))
        if (!verifier.verify(Base64.decode(signature, Base64.DEFAULT))) return false
        val json = JSONObject(receipt)
        // Raw Play JSON uses 0 for purchased, 4 for pending (not BillingClient's enum).
        json.optString("packageName") == packageName && json.optString("productId") == PRODUCT &&
            json.has("purchaseState") && json.getInt("purchaseState") == 0 &&
            json.optString("purchaseToken", json.optString("token")).isNotBlank()
    }.getOrDefault(false)
}
