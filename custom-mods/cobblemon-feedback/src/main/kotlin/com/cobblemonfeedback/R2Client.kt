package com.cobblemonfeedback

import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Cloudflare R2 upload via the S3-compatible API. PutObject only — that's the
 * one verb /feedback needs. Uploads happen on a worker thread, same pattern as
 * [GitHubIssuesClient].
 *
 * R2 uses standard AWS SigV4 with `region = "auto"` and `service = "s3"`. We
 * implement signing inline rather than pulling the AWS SDK (~15 MB of jars) for
 * one verb. See:
 * https://developers.cloudflare.com/r2/api/s3/api/
 *
 * Returns the public URL on success (composed from `r2PublicUrlBase + "/" + key`),
 * or null on failure (logged). Caller decides whether to fall back to a
 * text-only issue or surface the failure to the player.
 */
internal object R2Client {
    private val log = LoggerFactory.getLogger("cobblemon-feedback/r2")
    private val http: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }
    private val amzDateFmt = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val dateStampFmt = DateTimeFormatter.ofPattern("yyyyMMdd")

    /**
     * @param key object key (e.g. "1234-abc.png"). Must not start with `/`.
     * @param body raw bytes to upload.
     * @param contentType MIME type sent as Content-Type and signed.
     * @return public URL on success, null on failure.
     */
    fun putObject(key: String, body: ByteArray, contentType: String = "image/png"): String? {
        val cfg = CobblemonFeedback.config
        if (cfg.r2Endpoint.isBlank() || cfg.r2Bucket.isBlank() ||
            cfg.r2AccessKeyId.isBlank() || cfg.r2SecretAccessKey.isBlank()
        ) {
            log.warn("R2 not configured — skipping upload of {}", key)
            return null
        }
        require(!key.startsWith("/")) { "object key must not start with /" }

        val endpointHost = URI.create(cfg.r2Endpoint).host
            ?: run { log.warn("r2Endpoint has no host: ${cfg.r2Endpoint}"); return null }
        val now = ZonedDateTime.now(ZoneOffset.UTC)
        val amzDate = amzDateFmt.format(now)
        val dateStamp = dateStampFmt.format(now)
        val region = "auto"
        val service = "s3"
        val payloadHash = sha256Hex(body)

        // Canonical URI: "/<bucket>/<encoded-key>". Slashes between segments
        // are preserved; each segment is RFC 3986 percent-encoded.
        val canonicalUri = "/" + uriEncodePathSegment(cfg.r2Bucket) +
            "/" + key.split('/').joinToString("/") { uriEncodePathSegment(it) }

        // Headers must be sorted by lowercase name in the canonical request.
        val headers = sortedMapOf(
            "content-type" to contentType,
            "host" to endpointHost,
            "x-amz-content-sha256" to payloadHash,
            "x-amz-date" to amzDate,
        )
        val canonicalHeaders = headers.entries.joinToString("") { "${it.key}:${it.value.trim()}\n" }
        val signedHeaders = headers.keys.joinToString(";")

        val canonicalRequest = listOf(
            "PUT",
            canonicalUri,
            "", // empty query string
            canonicalHeaders,
            signedHeaders,
            payloadHash,
        ).joinToString("\n")

        val credentialScope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = listOf(
            "AWS4-HMAC-SHA256",
            amzDate,
            credentialScope,
            sha256Hex(canonicalRequest.toByteArray(Charsets.UTF_8)),
        ).joinToString("\n")

        val signingKey = hmac(
            hmac(
                hmac(
                    hmac(("AWS4" + cfg.r2SecretAccessKey).toByteArray(Charsets.UTF_8), dateStamp),
                    region,
                ),
                service,
            ),
            "aws4_request",
        )
        val signature = hex(hmac(signingKey, stringToSign))
        val authorization = "AWS4-HMAC-SHA256 Credential=${cfg.r2AccessKeyId}/$credentialScope," +
            " SignedHeaders=$signedHeaders, Signature=$signature"

        val url = cfg.r2Endpoint.trimEnd('/') + canonicalUri
        val req = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", authorization)
            .header("Content-Type", contentType)
            .header("x-amz-content-sha256", payloadHash)
            .header("x-amz-date", amzDate)
            .header("User-Agent", "cobblemon-feedback/1.0")
            .PUT(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        return try {
            val resp = http.send(req, HttpResponse.BodyHandlers.ofString())
            if (resp.statusCode() in 200..299) {
                cfg.r2PublicUrlBase.trimEnd('/') + "/" + key
            } else {
                log.warn("R2 PUT $key returned ${resp.statusCode()}: ${resp.body().take(500)}")
                null
            }
        } catch (e: Exception) {
            log.warn("R2 PUT $key failed: ${e.message}", e)
            null
        }
    }

    private fun sha256Hex(bytes: ByteArray): String =
        hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hmac(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    /** RFC 3986 unreserved set: ALPHA / DIGIT / "-" / "_" / "." / "~". */
    private fun uriEncodePathSegment(segment: String): String {
        val sb = StringBuilder(segment.length)
        for (b in segment.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xff
            if ((c in 0x30..0x39) || (c in 0x41..0x5a) || (c in 0x61..0x7a) ||
                c == 0x2d || c == 0x2e || c == 0x5f || c == 0x7e
            ) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append("%02X".format(c))
            }
        }
        return sb.toString()
    }
}
