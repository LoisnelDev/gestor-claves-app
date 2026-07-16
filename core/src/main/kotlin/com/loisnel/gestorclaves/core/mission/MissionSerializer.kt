package com.loisnel.gestorclaves.core.mission

/**
 * MissionSerializer — Serialización JSON sin org.json (compatible JVM pura)
 *
 * Usa construcción manual de JSON para evitar dependencia de org.json
 * que solo está disponible en Android SDK y no en JVM de tests unitarios.
 */
object MissionSerializer {

    fun toJson(packet: MissionPacket): String {
        val assetsJson = packet.assets.joinToString(",", "[", "]") { a ->
            """{"id":"${a.id}","label":"${esc(a.label)}","payload":"${a.payload}","nonce":"${a.nonce}"}"""
        }
        return """{"version":"${packet.version}","mission_id":"${packet.mission_id}","tenant_id":"${packet.tenant_id}","issued_by":"${packet.issued_by}","issued_to":"${packet.issued_to}","expiry":${packet.expiry},"clock_tolerance_ms":${packet.clock_tolerance_ms},"assets":${assetsJson},"signature":"${packet.signature}"}"""
    }

    fun fromJson(json: String): MissionPacket {
        fun str(key: String): String {
            val pattern = Regex(""""$key"\s*:\s*"((?:[^"\\]|\\.)*)"""")
            return pattern.find(json)?.groupValues?.get(1)
                ?: throw IllegalArgumentException("Missing field: $key")
        }
        fun lng(key: String): Long {
            val pattern = Regex(""""$key"\s*:\s*(\d+)""")
            return pattern.find(json)?.groupValues?.get(1)?.toLong()
                ?: throw IllegalArgumentException("Missing field: $key")
        }
        // Parse assets array
        val assetsRaw = Regex(""""assets"\s*:\s*(\[.*?])\s*,\s*"signature"""", RegexOption.DOT_MATCHES_ALL)
            .find(json)?.groupValues?.get(1) ?: "[]"
        val assets = Regex("""\{[^{}]+}""").findAll(assetsRaw).map { m ->
            val a = m.value
            fun aStr(k: String) = Regex(""""$k"\s*:\s*"([^"]*)"""""  ).find(a)?.groupValues?.get(1) ?: ""
            MissionAsset(id = aStr("id"), label = aStr("label"), payload = aStr("payload"), nonce = aStr("nonce"))
        }.toList()

        return MissionPacket(
            version            = str("version"),
            mission_id         = str("mission_id"),
            tenant_id          = str("tenant_id"),
            issued_by          = str("issued_by"),
            issued_to          = str("issued_to"),
            expiry             = lng("expiry"),
            clock_tolerance_ms = lng("clock_tolerance_ms"),
            assets             = assets,
            signature          = str("signature")
        )
    }

    private fun esc(s: String) = s.replace("\\", "\\\\").replace("\"", "\\\"")
}
