/*
 * Copyright (C) 2026 jinlongliao (https://github.com/JinlongLiao/)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.jinlongliao.ossbrowser.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * 配置备份的加密打包格式。
 *
 * 输出为二进制 blob，结构：
 * ```
 * magic[4]   = "ALOS"
 * version[1] = 0x01
 * mode[1]    = 0 (app-key) / 1 (password)
 * saltLen[1] = 16
 * salt[16]   = PBKDF2 盐（mode=0 时仍写随机字节占位）
 * ivLen[1]   = 12
 * iv[12]     = AES-GCM nonce
 * cipher[n]  = AES-256-GCM(JSON(profiles))
 * ```
 *
 * 其中：
 * - mode=0：密钥 = SHA-256(APP_SECRET || salt)，即任何本 APP 的安装都能解出；
 * - mode=1：密钥 = PBKDF2WithHmacSHA256(password, salt, 200_000, 256)。
 *
 * GCM 自带完整性校验（tag 长度 128 bit），密码错/文件损坏会直接抛 `AEADBadTagException`。
 */
object ProfileBackupCodec {

    private const val MAGIC = "ALOS"
    private const val VERSION: Byte = 1
    private const val MODE_APP: Byte = 0
    private const val MODE_PASSWORD: Byte = 1
    private const val SALT_LEN = 16
    private const val IV_LEN = 12
    private const val TAG_BITS = 128

    /**
     * APP 内置对称密钥（仅用于"只有本 APP 能解码"的软约束；并非真正抵御逆向）。
     * 如需更高强度请让用户设置密码。
     */
    private val APP_SECRET: ByteArray = byteArrayOf(
        0x41, 0x6C, 0x69, 0x4F, 0x53, 0x53, 0x34, 0x44,
        0x72, 0x6F, 0x69, 0x64, 0x2D, 0x6B, 0x65, 0x79,
        0x76, 0x31, 0x2E, 0x30, 0x2D, 0x6A, 0x69, 0x6E,
        0x6C, 0x6F, 0x6E, 0x67, 0x6C, 0x69, 0x61, 0x6F
    )

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Serializable
    private data class BackupPayload(
        val version: Int = 1,
        val exportedAtMs: Long,
        val profiles: List<OssProfile>
    )

    /** 将配置加密为字节数组。`password` 为空 → 使用 APP 内置密钥。 */
    fun encode(profiles: List<OssProfile>, password: String?): ByteArray {
        val payload = BackupPayload(
            exportedAtMs = System.currentTimeMillis(),
            profiles = profiles
        )
        val plain = json.encodeToString(BackupPayload.serializer(), payload).toByteArray(Charsets.UTF_8)

        val rng = SecureRandom()
        val salt = ByteArray(SALT_LEN).also(rng::nextBytes)
        val iv = ByteArray(IV_LEN).also(rng::nextBytes)
        val mode = if (password.isNullOrEmpty()) MODE_APP else MODE_PASSWORD
        val key = deriveKey(mode, password, salt)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val ct = cipher.doFinal(plain)

        val buf = ByteBuffer.allocate(4 + 1 + 1 + 1 + SALT_LEN + 1 + IV_LEN + ct.size)
        buf.put(MAGIC.toByteArray(Charsets.US_ASCII))
        buf.put(VERSION)
        buf.put(mode)
        buf.put(SALT_LEN.toByte())
        buf.put(salt)
        buf.put(IV_LEN.toByte())
        buf.put(iv)
        buf.put(ct)
        return buf.array()
    }

    /**
     * 解密字节数组得到配置列表。
     *
     * @throws BadFormatException 魔数/版本不匹配；
     * @throws WrongPasswordException 密钥错误或内容损坏（GCM tag 校验失败）。
     */
    fun decode(bytes: ByteArray, password: String?): List<OssProfile> {
        if (bytes.size < 4 + 1 + 1 + 1 + SALT_LEN + 1 + IV_LEN) {
            throw BadFormatException("file too short")
        }
        val buf = ByteBuffer.wrap(bytes)
        val magic = ByteArray(4).also { buf.get(it) }
        if (String(magic, Charsets.US_ASCII) != MAGIC) throw BadFormatException("bad magic")
        val version = buf.get()
        if (version != VERSION) throw BadFormatException("unsupported version=$version")
        val mode = buf.get()
        val saltLen = buf.get().toInt() and 0xFF
        if (saltLen != SALT_LEN) throw BadFormatException("bad salt len=$saltLen")
        val salt = ByteArray(saltLen).also { buf.get(it) }
        val ivLen = buf.get().toInt() and 0xFF
        if (ivLen != IV_LEN) throw BadFormatException("bad iv len=$ivLen")
        val iv = ByteArray(ivLen).also { buf.get(it) }
        val ct = ByteArray(buf.remaining()).also { buf.get(it) }

        val key = deriveKey(mode, password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val plain = try {
            cipher.doFinal(ct)
        } catch (t: Throwable) {
            throw WrongPasswordException(t)
        }
        return runCatching {
            val payload = json.decodeFromString(BackupPayload.serializer(), String(plain, Charsets.UTF_8))
            payload.profiles
        }.getOrElse { throw BadFormatException("bad json: ${it.message}") }
    }

    private fun deriveKey(mode: Byte, password: String?, salt: ByteArray): SecretKey {
        return when (mode) {
            MODE_APP -> {
                val md = MessageDigest.getInstance("SHA-256")
                md.update(APP_SECRET)
                md.update(salt)
                SecretKeySpec(md.digest(), "AES")
            }
            MODE_PASSWORD -> {
                val pwd = password ?: throw WrongPasswordException(IllegalArgumentException("password required"))
                val spec = PBEKeySpec(pwd.toCharArray(), salt, 200_000, 256)
                val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
            }
            else -> throw BadFormatException("unknown mode=$mode")
        }
    }

    class BadFormatException(msg: String) : RuntimeException(msg)
    class WrongPasswordException(cause: Throwable) : RuntimeException(cause)
}
