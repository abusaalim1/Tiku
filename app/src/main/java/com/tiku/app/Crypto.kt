package com.tiku.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import javax.crypto.KeyAgreement
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Tiku crypto — java.security / javax.crypto ONLY.
 * - Identity: EC P-256 keypair per user. The private key lives on-device wrapped by
 *   an Android Keystore AES-256-GCM master key (alias "tiku_master"), and is ALSO
 *   backed up wrapped under a PBKDF2 key derived from the user's backup password,
 *   stored at Firestore users/{uid}.keyBackup {salt, iv, ct}.
 * - Conversation key: ECDH(myPrivate, peerPublic) -> HKDF-SHA256
 *   (salt = sorted uid pair joined, info = "tiku-chat-v1") -> 32-byte AES key.
 * - Messages/media: AES-256-GCM with a fresh random 12-byte IV per payload.
 */
object Crypto {
    private val random = SecureRandom()
    private const val MASTER_ALIAS = "tiku_master"
    private const val GCM_TAG_BITS = 128
    private const val PBKDF2_ITERS = 310_000
    private const val APP_LOCK_ITERS = 210_000

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    // ---------- Identity keypair (software EC P-256; bytes are what we wrap) ----------
    fun generateIdentity(): KeyPair {
        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"))
        return kpg.generateKeyPair()
    }

    fun publicKeyB64(kp: KeyPair): String = b64(kp.public.encoded)

    fun peerPublicKey(b64x509: String) =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(b64x509)))

    fun privateKeyFromPkcs8(bytes: ByteArray) =
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(bytes))

    // ---------- Android Keystore master key (device binding) ----------
    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(MASTER_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                MASTER_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    /** Wrap bytes with the device master key. Returns Pair(ivB64, ctB64). */
    fun deviceWrap(plain: ByteArray): Pair<String, String> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return b64(cipher.iv) to b64(cipher.doFinal(plain))
    }

    fun deviceUnwrap(ivB64: String, ctB64: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(GCM_TAG_BITS, unb64(ivB64)))
        return cipher.doFinal(unb64(ctB64))
    }

    // ---------- Backup wrap: PBKDF2WithHmacSHA256(310k) -> AES-256-GCM ----------
    fun pbkdf2(password: CharArray, salt: ByteArray, iters: Int, keyLenBits: Int = 256): ByteArray {
        val spec = PBEKeySpec(password, salt, iters, keyLenBits)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
    }

    /** Returns map {salt, iv, ct} (all Base64) for Firestore users/{uid}.keyBackup. */
    fun backupWrap(plain: ByteArray, password: String): Map<String, String> {
        val salt = randomBytes(16)
        val key = SecretKeySpec(pbkdf2(password.toCharArray(), salt, PBKDF2_ITERS), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return mapOf("salt" to b64(salt), "iv" to b64(cipher.iv), "ct" to b64(cipher.doFinal(plain)))
    }

    /**
     * Unwrap a password-backed blob. Throws (AEADBadTagException) when the password
     * is wrong — without the backup password the backup is unrecoverable, by design.
     */
    fun backupUnwrap(blob: Map<String, String>, password: String): ByteArray {
        val key = SecretKeySpec(pbkdf2(password.toCharArray(), unb64(blob.getValue("salt")), PBKDF2_ITERS), "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, unb64(blob.getValue("iv"))))
        return cipher.doFinal(unb64(blob.getValue("ct")))
    }

    // ---------- App-lock password hash (verification only, never the password itself) ----------
    fun passwordHash(password: String): String {
        val salt = randomBytes(16)
        val hash = pbkdf2(password.toCharArray(), salt, APP_LOCK_ITERS)
        return "$APP_LOCK_ITERS:${b64(salt)}:${b64(hash)}"
    }

    fun passwordVerify(password: String, stored: String): Boolean = try {
        val (iters, saltB64, hashB64) = stored.split(":")
        val candidate = pbkdf2(password.toCharArray(), unb64(saltB64), iters.toInt())
        MessageDigest.isEqual(candidate, unb64(hashB64))
    } catch (e: Exception) {
        false
    }

    /**
     * Structural check of a stored verifier: "iters:b64(16-byte salt):b64(32-byte
     * hash)" with a sane iteration count. The gate uses this to tell a GENUINELY
     * wrong password apart from missing/corrupted lock data (owner Bug 0 —
     * corruption must never be reported as "wrong password").
     */
    fun hashWellFormed(stored: String?): Boolean {
        if (stored.isNullOrEmpty()) return false
        return try {
            val parts = stored.split(":")
            parts.size == 3 && parts[0].toInt() in 10_000..2_000_000 &&
                unb64(parts[1]).size == 16 && unb64(parts[2]).size == 32
        } catch (e: Exception) {
            false
        }
    }

    // ---------- Conversation key: ECDH + HKDF-SHA256 ----------
    fun conversationKey(
        myPrivatePkcs8: ByteArray,
        peerPublicB64: String,
        myUid: String,
        peerUid: String,
    ): SecretKey {
        val priv = privateKeyFromPkcs8(myPrivatePkcs8)
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(priv)
        agreement.doPhase(peerPublicKey(peerPublicB64), true)
        val shared = agreement.generateSecret()
        val salt = listOf(myUid, peerUid).sorted().joinToString("|").toByteArray(Charsets.UTF_8)
        val okm = hkdfSha256(shared, salt, "tiku-chat-v1".toByteArray(Charsets.UTF_8), 32)
        return SecretKeySpec(okm, "AES")
    }

    /** RFC 5869 HKDF-SHA256 with Mac/HmacSHA256. */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        fun hmac(key: ByteArray, data: ByteArray): ByteArray {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(key, "HmacSHA256"))
            return mac.doFinal(data)
        }
        val prk = hmac(if (salt.isEmpty()) ByteArray(32) else salt, ikm)
        val out = ByteArray(length)
        var t = ByteArray(0)
        var produced = 0
        var counter = 1
        while (produced < length) {
            t = hmac(prk, t + info + byteArrayOf(counter.toByte()))
            val take = minOf(t.size, length - produced)
            System.arraycopy(t, 0, out, produced, take)
            produced += take
            counter++
        }
        return out
    }

    // ---------- AES-256-GCM payload encryption ----------
    /** Returns Pair(ivB64, ctB64) under the conversation key, fresh random IV. */
    fun encrypt(key: SecretKey, plain: ByteArray): Pair<String, String> {
        val iv = randomBytes(12)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        return b64(iv) to b64(cipher.doFinal(plain))
    }

    fun decrypt(key: SecretKey, ivB64: String, ctB64: String): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, unb64(ivB64)))
        return cipher.doFinal(unb64(ctB64))
    }

    fun encryptText(key: SecretKey, text: String): Pair<String, String> =
        encrypt(key, text.toByteArray(Charsets.UTF_8))

    fun decryptText(key: SecretKey, ivB64: String, ctB64: String): String =
        String(decrypt(key, ivB64, ctB64), Charsets.UTF_8)
}
