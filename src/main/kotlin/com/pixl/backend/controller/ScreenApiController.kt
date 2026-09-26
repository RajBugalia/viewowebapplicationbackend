package com.pixl.backend.controller

import com.pixl.backend.model.Screen
import com.pixl.backend.model.ProofOfPlayLog
import com.pixl.backend.repository.ScreenRepository
import com.pixl.backend.repository.CampaignRepository
import com.pixl.backend.repository.MediaRepository
import com.pixl.backend.repository.ProofOfPlayRepository
import com.pixl.backend.repository.PlaylistRepository
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.*
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.springframework.transaction.annotation.Transactional

@RestController
@RequestMapping("/api/tv")
class ScreenApiController(
    private val screenRepository: ScreenRepository,
    private val campaignRepository: CampaignRepository,
    private val mediaRepository: MediaRepository,
    private val proofOfPlayRepository: ProofOfPlayRepository,
    private val playlistRepository: PlaylistRepository,
    private val jdbcTemplate: JdbcTemplate
) {
    data class RegisterRequest(val pairingCode: String, val screenId: Long? = null)

    // 1. Register a new screen or update pairing code for existing screen
    @PostMapping("/register")
    fun registerScreen(@RequestBody request: RegisterRequest): ResponseEntity<*> {
        val code = request.pairingCode
        
        var screen = request.screenId?.let { screenRepository.findById(it).orElse(null) }
        
        if (screen != null) {
            // Update existing screen's pairing code if it is unpaired
            if (screen.assignedAdmin == null) {
                screen.pairingCode = code
                val updatedScreen = screenRepository.save(screen)
                return ResponseEntity.ok(mapOf(
                    "pairingCode" to updatedScreen.pairingCode,
                    "screenId" to updatedScreen.id
                ))
            } else {
                // If it's already paired, don't change the code, just return it
                return ResponseEntity.ok(mapOf(
                    "pairingCode" to screen.pairingCode,
                    "screenId" to screen.id
                ))
            }
        } else {
            // Create a new screen
            val newScreen = Screen(
                name = "New TV Display",
                location = "Unassigned",
                pairingCode = code,
                status = "OFFLINE"
            )
            val saved = screenRepository.save(newScreen)
            
            return ResponseEntity.ok(mapOf(
                "pairingCode" to saved.pairingCode,
                "screenId" to saved.id
            ))
        }
    }

    @GetMapping("/debug-relations")
    fun debugRelations(): ResponseEntity<*> {
        val list = jdbcTemplate.queryForList("SELECT * FROM campaign_screens")
        return ResponseEntity.ok(list)
    }

    @GetMapping("/debug-screens")
    fun debugScreens(): ResponseEntity<*> {
        val list = jdbcTemplate.queryForList("SELECT id, pairing_code, status FROM screens")
        return ResponseEntity.ok(list)
    }

    @GetMapping("/debug-campaigns")
    @Transactional
    fun debugCampaigns(): ResponseEntity<*> {
        val campaigns = campaignRepository.findAll()
        val now = Instant.now()
        val res = campaigns.map { c ->
            mapOf(
                "id" to c.id,
                "name" to c.name,
                "startDate" to c.startDate.toString(),
                "endDate" to c.endDate.toString(),
                "now" to now.toString(),
                "isActive" to (now >= c.startDate && now <= c.endDate),
                "targetScreenIds" to c.targetScreens.map { it.id }
            )
        }
        return ResponseEntity.ok(res)
    }

    // 2. Poll for campaign updates
    @Transactional
    @GetMapping("/{screenId}/poll")
    fun pollScreen(@PathVariable screenId: Long): ResponseEntity<*> {
        val screen = screenRepository.findById(screenId).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        // Update heartbeat
        screen.lastPingAt = LocalDateTime.now()
        if (screen.assignedAdmin != null && screen.status == "OFFLINE") {
            screen.status = "ONLINE"
        }
        screenRepository.save(screen)

        // Check if unpaired
        if (screen.assignedAdmin == null) {
            return ResponseEntity.ok(mapOf("status" to "UNPAIRED", "activeCampaign" to null))
        }

        // Find active campaign for this screen
        val now = Instant.now()
        val campaigns = campaignRepository.findAll()
        val activeCampaign = campaigns.firstOrNull { campaign ->
            campaign.targetScreens.any { it.id == screen.id } &&
            now >= campaign.startDate && now <= campaign.endDate
        }

        if (activeCampaign == null) {
            return ResponseEntity.ok(mapOf("status" to "PAIRED_NO_CAMPAIGN", "activeCampaign" to null))
        }

        // Construct DTO
        val campaignDto = buildCampaignDto(activeCampaign)

        return ResponseEntity.ok(mapOf(
            "status" to "ACTIVE",
            "activeCampaign" to campaignDto
        ))
    }

    @Transactional
    @GetMapping("/player/{pairingCode}/assignment")
    fun getPlayerAssignment(@PathVariable pairingCode: String): ResponseEntity<*> {
        val screen = screenRepository.findByPairingCode(pairingCode).orElse(null)
            ?: return ResponseEntity.status(404).body(mapOf("error" to "Screen not found"))

        // Update heartbeat
        screen.lastPingAt = LocalDateTime.now()
        if (screen.assignedAdmin != null && screen.status == "OFFLINE") {
            screen.status = "ONLINE"
        }
        screenRepository.save(screen)

        val isPaired = screen.assignedAdmin != null

        // Find active campaign for this screen
        val now = Instant.now()
        val campaigns = campaignRepository.findAll()
        val activeCampaign = campaigns.firstOrNull { campaign ->
            campaign.targetScreens.any { it.id == screen.id } &&
            now >= campaign.startDate && now <= campaign.endDate
        }

        if (activeCampaign == null || !isPaired) {
            return ResponseEntity.ok(mapOf(
                "isPaired" to isPaired,
                "campaign" to null,
                "playlist" to null
            ))
        }

        val campaignDto = buildCampaignDto(activeCampaign)
        val defaultPlaylist = campaignDto["playlist"]

        return ResponseEntity.ok(mapOf(
            "isPaired" to true,
            "campaign" to campaignDto,
            "playlist" to defaultPlaylist
        ))
    }

    private fun buildCampaignDto(activeCampaign: com.pixl.backend.model.Campaign): Map<String, Any?> {
        val campaignDto = mutableMapOf<String, Any?>(
            "id" to activeCampaign.id,
            "name" to activeCampaign.name,
            "layoutType" to activeCampaign.layoutType,
            "splitRows" to activeCampaign.splitRows,
            "splitCols" to activeCampaign.splitCols
        )

        if (activeCampaign.layoutType == "SPLIT" && !activeCampaign.zoneConfigJson.isNullOrBlank()) {
            val zonesList = mutableListOf<Map<String, Any?>>()
            try {
                val regex = Regex("""\{"zoneIndex":(\d+),"row":(\d+),"col":(\d+),"playlistId":(\d+)\}""")
                val matches = regex.findAll(activeCampaign.zoneConfigJson!!)
                for (m in matches) {
                    val zIdx = m.groupValues[1].toInt()
                    val r = m.groupValues[2].toInt()
                    val c = m.groupValues[3].toInt()
                    val pId = m.groupValues[4].toLong()
                    val p = playlistRepository.findById(pId).orElse(null)
                    val pMap = p?.let { pl ->
                        mapOf(
                            "id" to pl.id,
                            "name" to pl.name,
                            "mediaItems" to pl.mediaItems.map { media ->
                                mapOf(
                                    "id" to media.id,
                                    "name" to media.filename,
                                    "url" to media.publicUrl,
                                    "publicUrl" to media.publicUrl,
                                    "type" to media.type,
                                    "durationSeconds" to media.durationSeconds
                                )
                            }
                        )
                    }
                    zonesList.add(mapOf(
                        "zoneIndex" to zIdx,
                        "row" to r,
                        "col" to c,
                        "playlist" to pMap
                    ))
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            campaignDto["zones"] = zonesList
            if (zonesList.isNotEmpty() && zonesList[0]["playlist"] != null) {
                campaignDto["playlist"] = zonesList[0]["playlist"]
            }
        } else if (activeCampaign.playlist != null) {
            campaignDto["playlist"] = mapOf(
                "id" to activeCampaign.playlist!!.id,
                "name" to activeCampaign.playlist!!.name,
                "mediaItems" to activeCampaign.playlist!!.mediaItems.map { media ->
                    mapOf(
                        "id" to media.id,
                        "name" to media.filename,
                        "url" to media.publicUrl,
                        "publicUrl" to media.publicUrl,
                        "type" to media.type,
                        "durationSeconds" to media.durationSeconds
                    )
                }
            )
        }
        return campaignDto
    }

    // 3. Receive Proof of Play logs
    @PostMapping("/{screenId}/proof-of-play")
    fun uploadProofOfPlay(@PathVariable screenId: Long, @RequestBody logs: List<Map<String, Any>>): ResponseEntity<*> {
        val screen = screenRepository.findById(screenId).orElse(null)
            ?: return ResponseEntity.notFound().build<Any>()

        // Update heartbeat
        screen.lastPingAt = LocalDateTime.now()
        if (screen.assignedAdmin != null && screen.status == "OFFLINE") {
            screen.status = "ONLINE"
        }
        screenRepository.save(screen)

        val entitiesToSave = mutableListOf<ProofOfPlayLog>()
        for (log in logs) {
            val mediaId = (log["mediaId"] as? Number)?.toLong() ?: continue
            val timestampMs = (log["timestamp"] as? Number)?.toLong() ?: continue
            val durationSeconds = (log["durationSeconds"] as? Number)?.toInt()
            val status = log["status"] as? String
            val campaignId = log["campaignId"] as? String
            
            val media = mediaRepository.findById(mediaId).orElse(null) ?: continue
            val campaign = campaignId?.toLongOrNull()?.let { campaignRepository.findById(it).orElse(null) }
            
            val playedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(timestampMs), ZoneId.systemDefault())
            entitiesToSave.add(ProofOfPlayLog(
                screen = screen, 
                media = media, 
                campaign = campaign,
                duration = durationSeconds,
                status = status,
                playedAt = playedAt
            ))
        }

        proofOfPlayRepository.saveAll(entitiesToSave)
        return ResponseEntity.ok(mapOf("status" to "ok", "saved" to entitiesToSave.size))
    }

    @GetMapping("/debug-db")
    fun debugDb(): ResponseEntity<*> {
        return try {
            val tables = jdbcTemplate.queryForList("SELECT table_schema, table_name FROM information_schema.tables WHERE table_type = 'BASE TABLE' AND table_schema NOT IN ('pg_catalog', 'information_schema');")
            val currentUser = jdbcTemplate.queryForObject("SELECT current_user", String::class.java)
            val currentSchema = jdbcTemplate.queryForObject("SELECT current_schema", String::class.java)
            val schemas = jdbcTemplate.queryForList("SELECT schema_name FROM information_schema.schemata")
            ResponseEntity.ok(mapOf("tables" to tables, "currentUser" to currentUser, "currentSchema" to currentSchema, "schemas" to schemas))
        } catch (e: Exception) {
            ResponseEntity.status(500).body(mapOf("error" to e.message))
        }
    }

    @org.springframework.web.bind.annotation.RequestMapping(value = ["/init-db"], method = [org.springframework.web.bind.annotation.RequestMethod.GET, org.springframework.web.bind.annotation.RequestMethod.POST])
    fun initDb(): ResponseEntity<*> {
        return try {
            val statements = listOf(
                """CREATE TABLE IF NOT EXISTS users (
                    id BIGSERIAL PRIMARY KEY,
                    email VARCHAR(255) NOT NULL UNIQUE,
                    name VARCHAR(255) NOT NULL,
                    password_hash VARCHAR(255) NOT NULL,
                    role VARCHAR(255) NOT NULL
                )""",
                """CREATE TABLE IF NOT EXISTS screens (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    location VARCHAR(255) NOT NULL,
                    pairing_code VARCHAR(255) NOT NULL UNIQUE,
                    status VARCHAR(255) NOT NULL,
                    assigned_admin_id BIGINT REFERENCES users(id),
                    group_name VARCHAR(255)
                )""",
                """ALTER TABLE screens ADD COLUMN IF NOT EXISTS group_name VARCHAR(255)""",
                """CREATE TABLE IF NOT EXISTS playlists (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP,
                    creator_id BIGINT REFERENCES users(id)
                )""",
                """CREATE TABLE IF NOT EXISTS media (
                    id BIGSERIAL PRIMARY KEY,
                    filename VARCHAR(255) NOT NULL,
                    original_filename VARCHAR(255) NOT NULL,
                    public_url VARCHAR(255) NOT NULL,
                    type VARCHAR(255) NOT NULL,
                    duration_seconds INT NOT NULL,
                    created_at TIMESTAMP,
                    uploader_id BIGINT REFERENCES users(id)
                )""",
                """CREATE TABLE IF NOT EXISTS playlist_media (
                    playlist_id BIGINT NOT NULL REFERENCES playlists(id),
                    media_id BIGINT NOT NULL REFERENCES media(id),
                    media_order INT NOT NULL
                )""",
                """CREATE TABLE IF NOT EXISTS campaigns (
                    id BIGSERIAL PRIMARY KEY,
                    name VARCHAR(255) NOT NULL,
                    start_date TIMESTAMP,
                    end_date TIMESTAMP,
                    status VARCHAR(255) NOT NULL,
                    created_at TIMESTAMP,
                    creator_id BIGINT REFERENCES users(id),
                    playlist_id BIGINT REFERENCES playlists(id),
                    layout_type VARCHAR(50) DEFAULT 'SINGLE',
                    split_rows INT DEFAULT 1,
                    split_cols INT DEFAULT 1,
                    zone_config_json TEXT
                )""",
                """ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS layout_type VARCHAR(50) DEFAULT 'SINGLE'""",
                """ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS split_rows INT DEFAULT 1""",
                """ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS split_cols INT DEFAULT 1""",
                """ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS zone_config_json TEXT""",
                """CREATE TABLE IF NOT EXISTS campaign_screens (
                    campaign_id BIGINT NOT NULL REFERENCES campaigns(id),
                    screen_id BIGINT NOT NULL REFERENCES screens(id)
                )""",
                """CREATE TABLE IF NOT EXISTS proof_of_play_logs (
                    id BIGSERIAL PRIMARY KEY,
                    screen_id BIGINT NOT NULL,
                    campaign_id BIGINT NOT NULL,
                    media_id BIGINT NOT NULL,
                    played_at TIMESTAMP NOT NULL,
                    duration_played_seconds INT NOT NULL
                )"""
            )
            
            for (sql in statements) {
                jdbcTemplate.execute(sql)
            }
            
            // Insert default admin if none exists
            val count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Int::class.java)
            if (count == 0) {
                jdbcTemplate.execute("INSERT INTO users (email, name, password_hash, role) VALUES ('pixladmin@pixl.com', 'Admin User', '\$2a\$10\$0iz3XiqKw0uJFfTDtAXNheMbTiNBD9qP.XavXr.xyrGyac194oKeG', 'ROLE_MASTER')")
            }
            
            ResponseEntity.ok(mapOf("status" to "success", "message" to "Database initialized manually!"))
        } catch (e: Exception) {
            e.printStackTrace()
            val causeMessage = e.cause?.message
            val rootCauseMessage = e.cause?.cause?.message
            ResponseEntity.status(500).body(mapOf("status" to "error", "error" to e.message, "cause" to causeMessage, "rootCause" to rootCauseMessage))
        }
    }

    @GetMapping("/hash/{password}")
    fun hashPassword(@PathVariable password: String): ResponseEntity<*> {
        return ResponseEntity.ok(mapOf("hash" to org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password)))
    }

    @GetMapping("/fix-admin")
    fun fixAdmin(): ResponseEntity<*> {
        val encoder = org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS theme VARCHAR(50) DEFAULT 'light'")
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS timezone VARCHAR(50) DEFAULT 'UTC'")
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_alerts BOOLEAN DEFAULT true")
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_reports BOOLEAN DEFAULT true")
        jdbcTemplate.execute("ALTER TABLE users ADD COLUMN IF NOT EXISTS notify_updates BOOLEAN DEFAULT false")
        
        // admin@pixl.com -> admin123
        val adminHash = encoder.encode("admin123")
        val adminCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'admin@pixl.com'", Int::class.java) ?: 0
        if (adminCount == 0) {
            jdbcTemplate.execute("INSERT INTO users (email, name, password_hash, role, theme, timezone, notify_alerts, notify_reports, notify_updates) VALUES ('admin@pixl.com', 'Admin PixL', '$adminHash', 'ROLE_ADMIN', 'light', 'UTC', true, true, false)")
        } else {
            jdbcTemplate.execute("UPDATE users SET password_hash = '$adminHash', role = 'ROLE_ADMIN' WHERE email = 'admin@pixl.com'")
        }

        // master@pixl.com -> master123
        val masterHash = encoder.encode("master123")
        val masterCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'master@pixl.com'", Int::class.java) ?: 0
        if (masterCount == 0) {
            jdbcTemplate.execute("INSERT INTO users (email, name, password_hash, role, theme, timezone, notify_alerts, notify_reports, notify_updates) VALUES ('master@pixl.com', 'Master PixL', '$masterHash', 'ROLE_MASTER', 'light', 'UTC', true, true, false)")
        } else {
            jdbcTemplate.execute("UPDATE users SET password_hash = '$masterHash', role = 'ROLE_MASTER' WHERE email = 'master@pixl.com'")
        }

        // pixladmin@pixl.com -> admin
        val pixlHash = encoder.encode("admin")
        val pixlCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users WHERE email = 'pixladmin@pixl.com'", Int::class.java) ?: 0
        if (pixlCount == 0) {
            jdbcTemplate.execute("INSERT INTO users (email, name, password_hash, role, theme, timezone, notify_alerts, notify_reports, notify_updates) VALUES ('pixladmin@pixl.com', 'Admin User', '$pixlHash', 'ROLE_MASTER', 'light', 'UTC', true, true, false)")
        } else {
            jdbcTemplate.execute("UPDATE users SET password_hash = '$pixlHash', role = 'ROLE_MASTER' WHERE email = 'pixladmin@pixl.com'")
        }

        return ResponseEntity.ok(mapOf(
            "status" to "success", 
            "message" to "Credentials updated: admin@pixl.com (admin123), master@pixl.com (master123), pixladmin@pixl.com (admin)"
        ))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(e: Exception): ResponseEntity<*> {
        e.printStackTrace()
        return ResponseEntity.status(500).body(mapOf(
            "error" to e.javaClass.simpleName,
            "message" to e.message,
            "cause" to e.cause?.message,
            "stackTrace" to e.stackTraceToString()
        ))
    }
}

