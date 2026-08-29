package com.smartpigs.overlay

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

class PigEngine {
    var width = 1080f
    var height = 1920f
    var pigWidth = 78f
    var maxPigs = 12
    var maxItems = 12
    var imageAspect = 1.35f
    var imageUrl = "https://static.wikia.nocookie.net/neutral-characters/images/d/d0/PeppaPig.webp/revision/latest?cb=20250701022738"
    var running = false
    var pigCountTarget = 2
    val modifiers = defaultModifiers()
    val pigs = mutableListOf<Pig>()
    val treats = mutableListOf<WorldItem>()
    val toys = mutableListOf<WorldItem>()
    var pointerX = 200f
    var pointerY = 400f
    var pointerActive = false
    var pointerLastMove = 0L
    var bubbleX = 900f
    var bubbleY = 1600f
    private var nextId = 0
    private var lastTime = 0L

    fun pigScale(): Float {
        var s = 1f
        if (modifiers["giant"] == true) s *= 1.7f
        if (modifiers["tiny"] == true) s *= 0.52f
        return s
    }
    fun pigW() = pigWidth * pigScale()
    fun pigH() = pigW() * imageAspect
    fun floorY() = max(20f, height - pigH() - 12f)

    fun setScreen(w: Int, h: Int) {
        width = w.toFloat()
        height = h.toFloat()
    }

    fun start(options: JSONObject = JSONObject()): JSONObject {
        if (options.has("imageUrl")) imageUrl = options.optString("imageUrl", imageUrl)
        if (options.has("pigCount")) pigCountTarget = max(1, options.optInt("pigCount", 2))
        pigCountTarget = pigCountTarget.coerceIn(1, maxPigs)
        if (options.has("pigWidth")) pigWidth = options.optDouble("pigWidth", pigWidth.toDouble()).toFloat().coerceIn(28f, 260f)
        options.optJSONObject("modifiers")?.let { applyModifierObject(it) }
        if (running) {
            while (pigs.size < pigCountTarget) addPig(null)
            while (pigs.size > pigCountTarget) removeLastPig()
            applyModifiersNow()
            return ok("Already running")
        }
        pigs.clear(); treats.clear(); toys.clear(); nextId = 0
        val preset = options.optJSONObject("preset")
        repeat(pigCountTarget) { i -> pigs += createPig(if (i == 0) preset else null) }
        pigs.forEach { it.y = floorY() }
        running = true
        lastTime = 0L
        applyModifiersNow()
        return ok("Pigs started!")
    }

    fun stop(): JSONObject {
        running = false
        pigs.clear(); treats.clear(); toys.clear()
        return ok("Pigs removed.")
    }

    fun step(now: Long) {
        if (!running) return
        if (lastTime == 0L) lastTime = now
        val deltaMs = min(50L, now - lastTime).toFloat()
        lastTime = now
        val dt = deltaMs / 16.67f
        val dtS = deltaMs / 1000f
        for (pig in pigs) {
            if (!pig.dragging && modifiers["frozen"] != true && !pig.ragdoll && modifiers["blank"] != true && !pig.goo) {
                pig.stateTime -= deltaMs
                pig.decisionTimer -= deltaMs
                if (pig.decisionTimer <= 0 || pig.stateTime <= 0) {
                    pig.decisionTimer = rand(900f, 1600f)
                    decide(pig)
                }
            }
            updateNeeds(pig, dtS)
            updateState(pig, dt)
            updatePhysics(pig, dt)
        }
        resolvePigs()
        if (treats.isNotEmpty() || toys.isNotEmpty()) updateItems(dt)
        for (pig in pigs) {
            if (pig.grounded && !pig.dragging && modifiers["noGravity"] != true && modifiers["blank"] != true && !pig.goo) {
                pig.y = floorY()
            }
            updateVisuals(pig, deltaMs)
        }
    }

    fun handle(action: String, extra: JSONObject): JSONObject {
        return try {
            when (action) {
                "ping" -> JSONObject()
                    .put("success", true).put("running", running).put("pigCount", pigs.size)
                    .put("version", 19).put("modifiers", modifiersJson()).put("pigWidth", pigWidth)
                "start" -> start(extra.optJSONObject("options") ?: extra)
                "stop", "remove" -> stop()
                "getInfo" -> getInfo()
                "think" -> think(extra.optInt("index", 0))
                "addPig" -> addPig(extra.optJSONObject("preset"))
                "removePig" -> removeLastPig()
                "setImage" -> setImage(extra.optString("url"))
                "setMaxPigs" -> {
                    maxPigs = extra.optInt("maxPigs", maxPigs).coerceIn(1, 25)
                    while (pigs.size > maxPigs) removeLastPig()
                    JSONObject().put("success", true).put("maxPigs", maxPigs)
                }
                "setPigCount" -> {
                    val target = max(0, extra.optInt("count", pigs.size))
                    if (!running && target > 0) start(JSONObject().put("pigCount", target))
                    else {
                        while (pigs.size < target) addPig(null)
                        while (pigs.size > target) removeLastPig()
                        JSONObject().put("success", true).put("pigCount", pigs.size)
                    }
                }
                "setModifiers" -> {
                    extra.optJSONObject("modifiers")?.let { applyModifierObject(it) }
                    applyModifiersNow()
                    JSONObject().put("success", true).put("modifiers", modifiersJson())
                }
                "setSize", "setPigSize" -> {
                    pigWidth = extra.optDouble("size", extra.optDouble("pigWidth", pigWidth.toDouble())).toFloat().coerceIn(28f, 260f)
                    JSONObject().put("success", true).put("pigWidth", pigWidth)
                }
                "dropTreats" -> dropTreats(extra.optInt("count", 5))
                "addBall" -> addToy("ball", listOf("⚽", "🏀", "🎾", "🏐", "⚾"), 28f)
                "addTeddy" -> addToy("teddy", listOf("🧸", "🎀", "⭐", "🎁"), 30f)
                "callPigs" -> {
                    pigs.forEach { if (it.ragdoll) exitRagdoll(it); setState(it, "come", 2500f) }
                    ok("Pigs are coming!")
                }
                "setRig" -> ok("Rig saved. Overlay uses the pig image.")
                else -> JSONObject().put("success", false).put("message", "Unknown action")
            }
        } catch (e: Exception) {
            JSONObject().put("success", false).put("message", e.message ?: "error")
        }
    }

    fun getInfo(): JSONObject {
        val arr = JSONArray()
        pigs.forEachIndexed { i, pig ->
            arr.put(
                JSONObject()
                    .put("index", i).put("id", pig.id).put("name", pig.name)
                    .put("state", if (pig.dragging) "held" else if (pig.ragdoll) "ragdoll" else pig.state)
                    .put("mood", pig.mood).put("feeling", pig.feeling).put("reason", pig.reason)
                    .put("thought", pig.thought ?: "").put("platform", "floor")
                    .put("energy", pig.energy.toInt()).put("boredom", pig.boredom.toInt())
                    .put("curiosity", pig.curiosityNeed.toInt()).put("happiness", pig.happiness.toInt())
                    .put("social", pig.social.toInt()).put("comfort", pig.comfort.toInt())
                    .put("maxPigs", maxPigs)
                    .put("maxItems", maxItems)
                    .put("personality", JSONObject().put("playfulness", pig.play).put("sociability", pig.socialNeed).put("laziness", pig.lazy).put("bravery", pig.brave)),
            )
        }
        return JSONObject()
            .put("success", true).put("running", running).put("pigs", arr)
            .put("modifiers", modifiersJson()).put("version", 19)
            .put("config", JSONObject().put("imageUrl", imageUrl).put("pigCount", pigs.size).put("pigWidth", pigWidth))
    }

    fun hitPig(x: Float, y: Float): Pig? {
        val w = pigW(); val h = pigH()
        for (i in pigs.indices.reversed()) {
            val p = pigs[i]
            if (p.goo) {
                if (p.blobs.any { hypot(it.x - x, it.y - y) < it.r * 2.4f }) return p
            } else if (x >= p.x - 8 && x <= p.x + w + 8 && y >= p.y - 8 && y <= p.y + h + 8) return p
        }
        return null
    }

    fun hitItem(x: Float, y: Float): WorldItem? {
        return (treats + toys).lastOrNull { hypot(it.x + 14 - x, it.y + 14 - y) < 28 }
    }

    fun beginDragPig(pig: Pig, x: Float, y: Float) {
        pig.dragging = true; pig.dragMoved = false; pig.grounded = false; pig.vx = 0f; pig.vy = 0f
        pig.dragOffX = x - pig.x; pig.dragOffY = y - pig.y
        pig.samples.clear(); pig.samples += Sample(now(), x, y)
        if (pig.goo) { pig.gooSleep = false; grabGoo(pig, x, y) }
    }
    fun moveDragPig(pig: Pig, x: Float, y: Float) {
        val nx = x - pig.dragOffX; val ny = y - pig.dragOffY
        if (hypot(nx - pig.x, ny - pig.y) > 2) pig.dragMoved = true
        pig.x = nx; pig.y = ny
        pig.samples += Sample(now(), x, y)
        if (pig.samples.size > 10) pig.samples.removeAt(0)
    }
    fun endDragPig(pig: Pig) {
        if (!pig.dragging) return
        pig.dragging = false
        val fling = fling(pig.samples)
        val speed = hypot(fling.first, fling.second)
        if (pig.dragMoved || speed > 1) {
            pig.vx = fling.first.coerceIn(-42f, 42f)
            pig.vy = fling.second.coerceIn(-42f, 42f)
            pig.spin = (fling.first * 0.14f).coerceIn(-0.6f, 0.6f)
            pig.grounded = false
            if (modifiers["noGravity"] == true || (modifiers["ragdoll"] == true && speed > 6)) enterRagdoll(pig)
            showThought(pig, pick("weeee!", "whoosh!", "flying!"))
            if (pig.goo) pig.blobs.forEach { it.held = false; it.vx = pig.vx; it.vy = pig.vy }
        } else {
            pig.happiness = (pig.happiness + 12).coerceIn(0f, 100f)
            pig.clicked = true; pig.clickTimer = 900f
            if (modifiers["noGravity"] != true) pig.vy = -7f
            setState(pig, "hop", 700f)
            showThought(pig, pick("boop!", "hi!", "hehe"))
            pig.heartTimer = 700f
        }
        pig.samples.clear(); pig.dragMoved = false
    }

    fun beginDragItem(it: WorldItem, x: Float, y: Float) {
        it.dragging = true; it.dragOffX = x - it.x; it.dragOffY = y - it.y; it.vx = 0f; it.vy = 0f
        it.samples.clear(); it.samples += Sample(now(), x, y)
    }
    fun moveDragItem(it: WorldItem, x: Float, y: Float) {
        it.x = x - it.dragOffX; it.y = y - it.dragOffY
        it.samples += Sample(now(), x, y)
        if (it.samples.size > 8) it.samples.removeAt(0)
    }
    fun endDragItem(it: WorldItem) {
        it.dragging = false
        if (it.samples.size >= 2) {
            val last = it.samples.last(); val first = it.samples[max(0, it.samples.size - 4)]
            val d = max(8f, (last.t - first.t).toFloat())
            it.vx = ((last.x - first.x) / d) * 16.67f * 0.35f
            it.vy = ((last.y - first.y) / d) * 16.67f * 0.35f
        }
        it.samples.clear()
    }

    fun pointer(x: Float, y: Float) {
        pointerX = x; pointerY = y; pointerActive = true; pointerLastMove = now()
    }

    private fun createPig(preset: JSONObject? = null): Pig {
        val used = pigs.map { it.name }.toSet()
        val name = preset?.optString("name")?.ifBlank { null }
            ?: NAMES.firstOrNull { it !in used }
            ?: "Pig${nextId + 1}"
        return Pig(
            id = nextId++,
            name = name,
            imageUrl = preset?.optString("imageUrl")?.ifBlank { imageUrl } ?: imageUrl,
            x = rand(20f, max(30f, width - pigW() - 20f)),
            y = floorY(),
            direction = if (chance(0.5f)) 1 else -1,
            energy = rand(55f, 100f),
            boredom = rand(8f, 40f),
            curiosityNeed = rand(35f, 80f),
            happiness = rand(60f, 100f),
            social = rand(40f, 85f),
            play = rand(0.2f, 1f),
            curious = rand(0.2f, 1f),
            lazy = rand(0.08f, 0.85f),
            brave = rand(0.2f, 1f),
            socialNeed = rand(0.2f, 1f),
            pep = rand(0.3f, 1f),
        ).also { pig ->
            preset?.optJSONObject("personality")?.let {
                pig.play = it.optDouble("playfulness", pig.play.toDouble()).toFloat()
                pig.curious = it.optDouble("curiosity", pig.curious.toDouble()).toFloat()
                pig.lazy = it.optDouble("laziness", pig.lazy.toDouble()).toFloat()
                pig.brave = it.optDouble("bravery", pig.brave.toDouble()).toFloat()
                pig.socialNeed = it.optDouble("sociability", pig.socialNeed.toDouble()).toFloat()
            }
            preset?.optJSONArray("words")?.let { arr ->
                pig.words = (0 until arr.length()).map { arr.getString(it) }
            }
        }
    }

    private fun addPig(preset: JSONObject?): JSONObject {
        if (pigs.size >= maxPigs) {
            return JSONObject().put("success", false).put("message", "Pig limit reached ($maxPigs)")
        }
        val usedMb = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024L * 1024L)
        if (usedMb > 75L && pigs.size >= 4) {
            return JSONObject().put("success", false).put("message", "Memory limit — remove some pigs")
        }
        if (!running) return start(JSONObject().put("pigCount", 1).put("preset", preset ?: JSONObject.NULL))
        val pig = createPig(preset)
        if (modifiers["noGravity"] == true) {
            pig.x = rand(40f, width - 80f); pig.y = rand(40f, height - 80f); enterRagdoll(pig)
        } else pig.y = floorY()
        pigs += pig
        pigCountTarget = pigs.size
        return ok("Added ${pig.name}!")
    }

    private fun removeLastPig(): JSONObject {
        if (pigs.isEmpty()) return JSONObject().put("success", false).put("message", "No pigs to remove.")
        val pig = pigs.removeAt(pigs.lastIndex)
        pigCountTarget = pigs.size
        if (pigs.isEmpty()) return stop()
        return ok("Removed ${pig.name}.")
    }

    private fun setImage(url: String): JSONObject {
        if (url.isBlank()) return JSONObject().put("success", false).put("message", "Please enter an image URL")
        imageUrl = url
        pigs.forEach { it.imageUrl = url }
        return JSONObject().put("success", true).put("message", "Image updated.")
    }

    private fun dropTreats(count: Int): JSONObject {
        if (!running) return JSONObject().put("success", false).put("message", "Start pigs first.")
        val room = (maxItems - treats.size - toys.size).coerceAtLeast(0)
        if (room == 0) return JSONObject().put("success", false).put("message", "Item limit reached ($maxItems)")
        val n = count.coerceIn(1, room)
        repeat(n) { treats += makeItem("snack", pick("🍪", "🍎", "🍩", "🥕", "🧁"), 26f) }
        return ok("Dropped $n snacks!")
    }

    private fun addToy(kind: String, emojis: List<String>, size: Float): JSONObject {
        if (!running) return JSONObject().put("success", false).put("message", "Start pigs first.")
        if (treats.size + toys.size >= maxItems) {
            return JSONObject().put("success", false).put("message", "Item limit reached ($maxItems)")
        }
        toys += makeItem(kind, emojis.random(), size)
        return ok(if (kind == "ball") "Ball added!" else "Toy added!")
    }

    private fun makeItem(kind: String, emoji: String, size: Float) = WorldItem(
        id = nextId++,
        kind = kind, emoji = emoji, r = size * 0.38f,
        x = rand(40f, width - 50f), y = rand(30f, height * 0.55f),
        vx = rand(-3f, 3f), vy = rand(-5f, -1f), rot = rand(0f, 360f), spin = rand(-6f, 6f),
    )

    private fun think(index: Int): JSONObject {
        val pig = pigs.getOrNull(index) ?: return JSONObject().put("success", false).put("message", "No pig with that index.")
        if (pig.ragdoll) exitRagdoll(pig)
        decide(pig); updateMood(pig); showThought(pig, pig.reason.ifBlank { pig.state }, 2200f)
        return JSONObject().put("success", true).put("message", "${pig.name}: ${pig.reason}")
    }

    private fun decide(pig: Pig) {
        if (pig.dragging || pig.ragdoll || pig.goo || modifiers["frozen"] == true || modifiers["blank"] == true) return
        if (modifiers["noGravity"] == true && chance(0.35f)) {
            setState(pig, if (chance(0.5f)) "float" else "wander", rand(1200f, 2800f)); return
        }
        val snack = nearestTreat(pig)
        if (snack != null) { pig.treat = snack; setState(pig, "eat", 4000f); return }
        val toy = toys.firstOrNull { !it.dragging }
        if (toy != null && pig.play > 0.45f && chance(0.28f)) { pig.treat = toy; setState(pig, "play_toy", 3500f); return }
        if (modifiers["party"] == true && chance(0.3f)) { setState(pig, "dance", 1600f); return }
        if (pig.energy < 16) { setState(pig, "nap", 4000f); return }
        if (pointerActive && now() - pointerLastMove < 1400 && pig.curious > 0.55f && chance(0.2f)) {
            setState(pig, "watch_mouse", 1800f); return
        }
        val near = pigs.filter { it !== pig }.minByOrNull { hypot(it.x - pig.x, it.y - pig.y) }
        if (near != null && hypot(near.x - pig.x, near.y - pig.y) < 280 && pig.socialNeed > 0.4f && chance(0.24f)) {
            pig.target = near; setState(pig, if (chance(0.5f)) "play" else "follow", 2200f); return
        }
        if (pig.brave < 0.3f && chance(0.08f)) { setState(pig, "peek", 1800f); return }
        setState(pig, weighted(listOf(
            "wander" to 22f + pig.pep * 10f,
            "run" to 6f + pig.play * 8f,
            "idle" to 6f + pig.lazy * 10f,
            "sit" to 4f,
            "dance" to 4f + pig.play * 4f,
            "sniff" to 6f + pig.curious * 6f,
        )))
    }

    private fun updateState(pig: Pig, dt: Float) {
        if (pig.dragging || pig.ragdoll || modifiers["frozen"] == true) return
        if (modifiers["noGravity"] == true && pig.state == "float") {
            pig.vx += (pig.direction * 0.04f - pig.vx * 0.02f) * dt
            pig.vy += sin(pig.anim) * 0.05f * dt
            if (chance(0.008f)) pig.direction *= -1
            return
        }
        val speedMul = if (modifiers["superSpeed"] == true) 2.3f else 1f
        when (pig.state) {
            "wander" -> pig.vx = pig.direction * (0.4f + pig.pep * 0.65f) * speedMul
            "run" -> pig.vx = pig.direction * (1.4f + pig.pep) * speedMul
            "come" -> moveToward(pig, pointerX - pigW() / 2, 1.6f * speedMul)
            "idle", "sit", "nap" -> pig.vx *= 0.7f.pow(dt)
            "dance" -> pig.vx = sin(pig.anim * 3f) * 0.5f
            "hop" -> pig.vx = pig.direction * 0.55f * speedMul
            "eat" -> {
                val t = pig.treat?.takeIf { treats.contains(it) } ?: nearestTreat(pig)
                if (t == null) setState(pig, "wander")
                else {
                    moveToward(pig, t.x - pigW() / 2, 1.3f * speedMul)
                    if (hypot(t.x - pig.x, t.y - pig.y) < 40) {
                        treats.remove(t); pig.happiness = (pig.happiness + 16).coerceIn(0f, 100f); setState(pig, "dance", 800f)
                    }
                }
            }
            "peek" -> { pig.vx *= 0.7f.pow(dt); pig.x = pig.x.coerceIn(-pigW() * 0.35f, width - pigW() * 0.65f) }
            "play_toy" -> {
                val t = pig.treat ?: toys.firstOrNull()
                if (t == null) setState(pig, "wander")
                else {
                    moveToward(pig, t.x - pigW() / 2, 1.35f * speedMul)
                    if (hypot(t.x - pig.x, t.y - pig.y) < 50 && !t.dragging) { t.vx += pig.direction * 3.2f; t.vy = -4.5f }
                }
            }
            "follow", "play" -> {
                val t = pig.target
                if (t == null) setState(pig, "wander") else moveToward(pig, t.x, (if (pig.state == "play") 1.1f else 0.8f) * speedMul)
            }
            "watch_mouse" -> {
                if (abs(pointerX - pig.x) > 10) pig.direction = if (pointerX > pig.x) 1 else -1
                pig.vx *= 0.65f.pow(dt)
            }
            else -> pig.vx = pig.direction * (0.35f + pig.pep * 0.4f) * speedMul
        }
    }

    private fun updatePhysics(pig: Pig, dt: Float) {
        if (pig.dragging) {
            if (pig.goo) dragGoo(pig, dt)
            return
        }
        if (modifiers["frozen"] == true) { pig.vx = 0f; pig.vy = 0f; return }
        if (pig.goo) { updateGoo(pig, dt, false); return }
        if (modifiers["blank"] == true) { updateBlank(pig, dt); return }

        var gravity = 0.42f
        if (modifiers["noGravity"] == true) gravity = 0f
        else if (modifiers["moon"] == true) gravity = 0.12f
        else if (modifiers["floaty"] == true) gravity = 0.18f
        var bounce = 0.12f
        if (modifiers["noGravity"] == true) bounce = if (modifiers["bouncy"] == true) 0.98f else 0.9f
        else if (modifiers["bouncy"] == true) bounce = 0.86f
        else if (modifiers["moon"] == true) bounce = 0.55f

        if (modifiers["noGravity"] == true) {
            pig.ragdoll = true; pig.grounded = false
            applyRope(pig, dt)
            pig.x += pig.vx * dt; pig.y += pig.vy * dt
            pig.angle += pig.spin * dt; pig.spin *= 0.997f.pow(dt)
            if (hypot(pig.vx, pig.vy) < 0.35f) {
                pig.vx += rand(-0.08f, 0.08f); pig.vy += rand(-0.08f, 0.08f)
                if (abs(pig.spin) < 0.03f) pig.spin = rand(-0.1f, 0.1f)
            }
            bounceBox(pig, bounce); return
        }
        if (pig.ragdoll) {
            pig.ragdollTimer -= dt * 16.67f
            pig.x += pig.vx * dt; pig.vy += gravity * dt; pig.y += pig.vy * dt
            pig.angle += pig.spin * dt; pig.spin *= 0.985f.pow(dt)
            bounceBox(pig, bounce)
            if (pig.y >= floorY() && hypot(pig.vx, pig.vy) < 1.2f) {
                pig.y = floorY(); exitRagdoll(pig); pig.vy = 0f; pig.grounded = true
            } else if (pig.ragdollTimer <= 0 || modifiers["ragdoll"] != true) exitRagdoll(pig)
            return
        }
        applyRope(pig, dt)
        if (modifiers["magnet"] == true && pointerActive && modifiers["rope"] != true) {
            pig.vx += (pointerX - pigW() / 2 - pig.x) * 0.016f * dt
            pig.vy += (pointerY - pigH() / 2 - pig.y) * 0.016f * dt
        }
        if (modifiers["chaos"] == true && chance(0.016f)) { pig.vx += rand(-4f, 4f); pig.vy += rand(-5f, 2f) }
        pig.x += pig.vx * dt; pig.vy += gravity * dt; pig.y += pig.vy * dt
        pig.angle = 0f; pig.spin = 0f
        if (pig.x < 0) { pig.x = 0f; pig.vx = abs(pig.vx) * bounce; pig.direction = 1 }
        if (pig.x > width - pigW()) { pig.x = width - pigW(); pig.vx = -abs(pig.vx) * bounce; pig.direction = -1 }
        if (pig.y < -40) { pig.y = -40f; pig.vy = abs(pig.vy) * 0.3f }
        val fy = floorY()
        if (pig.y >= fy) {
            pig.y = fy
            if (modifiers["bouncy"] == true && abs(pig.vy) > 1.5f) { pig.vy = -abs(pig.vy) * bounce; pig.grounded = false }
            else if (pig.vy > 1) { pig.vy *= -0.12f; pig.grounded = true }
            else { pig.vy = 0f; pig.grounded = true }
        } else pig.grounded = false
        if (pig.clicked) {
            pig.vy = if (modifiers["noGravity"] == true) rand(-2f, 2f) else -7f
            pig.clicked = false; pig.grounded = false
        }
    }

    private fun bounceBox(pig: Pig, bounce: Float) {
        val w = pigW(); val h = pigH(); val before = hypot(pig.vx, pig.vy)
        if (pig.x < 0) { pig.x = 0f; pig.vx = abs(pig.vx) * bounce; pig.direction = 1; tryMelt(pig, before) }
        if (pig.x > width - w) { pig.x = width - w; pig.vx = -abs(pig.vx) * bounce; pig.direction = -1; tryMelt(pig, before) }
        if (pig.y < 0) { pig.y = 0f; pig.vy = abs(pig.vy) * bounce; tryMelt(pig, before) }
        if (pig.y > height - h) { pig.y = height - h; pig.vy = -abs(pig.vy) * bounce; tryMelt(pig, before) }
    }

    private fun applyRope(pig: Pig, dt: Float) {
        if (modifiers["rope"] != true || pig.dragging) return
        val mx = if (pointerActive) pointerX else bubbleX
        val my = if (pointerActive) pointerY else bubbleY
        val px = pig.x + pigW() / 2; val py = pig.y + pigH() * 0.28f
        val dx = mx - px; val dy = my - py
        val d = hypot(dx, dy).coerceAtLeast(1f)
        val rest = 72f + (pig.id % 6) * 16f
        val pull = (d - rest) / d
        val k = if (modifiers["noGravity"] == true) 0.09f else 0.16f
        if (d > rest) { pig.vx += dx * pull * k * dt; pig.vy += dy * pull * k * dt; pig.grounded = false }
        else { pig.vx += dx * 0.004f * dt; pig.vy += dy * 0.004f * dt }
        pig.vx *= 0.98f; pig.vy *= if (modifiers["noGravity"] == true) 0.98f else 0.995f
    }

    private fun tryMelt(pig: Pig, speed: Float) {
        if (modifiers["wetPile"] != true || pig.goo || pig.dragging) return
        if (speed < 9.5f) return
        if (now() - pig.lastImpact < 240) return
        pig.lastImpact = now(); melt(pig)
    }

    private fun melt(pig: Pig) {
        pig.goo = true; pig.gooSleep = false; pig.ragdoll = false; pig.angle = 0f; pig.spin = 0f; pig.grounded = false; pig.thought = null
        val cx = pig.x + pigW() / 2; val cy = pig.y + pigH() * 0.45f
        val r = max(5.5f, pigW() * 0.095f)
        pig.blobs.clear()
        repeat(22) { i ->
            val a = (i / 22f) * Math.PI.toFloat() * 2f
            val spread = r * (0.35f + (i % 3) * 0.18f)
            pig.blobs += GooBlob(cx + cos(a) * spread, cy + sin(a) * spread * 0.75f, pig.vx * 0.85f + rand(-1.2f, 1.2f), pig.vy * 0.85f + rand(-1.2f, 1.2f), r)
        }
    }

    private fun reform(pig: Pig) {
        if (!pig.goo) return
        if (pig.blobs.isNotEmpty()) {
            pig.x = pig.blobs.map { it.x }.average().toFloat() - pigW() / 2
            pig.y = pig.blobs.map { it.y }.average().toFloat() - pigH() * 0.4f
        }
        pig.goo = false; pig.blobs.clear(); pig.gooSleep = false
    }

    private fun grabGoo(pig: Pig, mx: Float, my: Float) {
        if (pig.blobs.isEmpty()) melt(pig)
        var any = false
        pig.blobs.forEach { it.held = hypot(it.x - mx, it.y - my) < it.r * 3.2f; if (it.held) any = true }
        if (!any) pig.blobs.sortedBy { hypot(it.x - mx, it.y - my) }.take(4).forEach { it.held = true }
    }

    private fun dragGoo(pig: Pig, dt: Float) {
        val mx = pig.x + pigW() / 2; val my = pig.y + pigH() * 0.35f
        if (pig.blobs.isEmpty()) melt(pig)
        pig.gooSleep = false
        val grabR = pigW() * 0.72f
        for (b in pig.blobs) {
            val d = hypot(mx - b.x, my - b.y).coerceAtLeast(1f)
            if (b.held && d > grabR * 1.85f) b.held = false
            if (b.held) {
                val pull = min(0.55f, 18f / d)
                b.vx = (b.vx + (mx - b.x) * 0.45f) * 0.7f
                b.vy = (b.vy + (my - b.y) * 0.45f) * 0.7f
                b.x += (mx - b.x) * pull; b.y += (my - b.y) * pull
            }
        }
        updateGoo(pig, dt, true)
    }

    private fun updateGoo(pig: Pig, dt: Float, grabbing: Boolean) {
        if (modifiers["wetPile"] != true) { reform(pig); return }
        var gravity = if (modifiers["noGravity"] == true) 0f else if (modifiers["moon"] == true) 0.12f else if (modifiers["floaty"] == true) 0.18f else 0.38f
        val bounce = if (modifiers["noGravity"] == true) (if (modifiers["bouncy"] == true) 0.98f else 0.9f) else if (modifiers["bouncy"] == true) 0.72f else 0.35f
        val blobs = pig.blobs
        if (blobs.isEmpty()) { melt(pig); return }
        if (pig.gooSleep && !grabbing) {
            pig.x = blobs.map { it.x }.average().toFloat() - pigW() / 2
            pig.y = blobs.map { it.y }.average().toFloat() - pigH() * 0.4f
            return
        }
        var moving = grabbing
        for (b in blobs) {
            if (grabbing && b.held) { b.x += b.vx * dt * 0.35f; b.y += b.vy * dt * 0.35f; moving = true; continue }
            b.vy += gravity * dt; b.vx *= 0.992f; b.vy *= 0.992f; b.x += b.vx * dt; b.y += b.vy * dt
            if (b.x < b.r) { b.x = b.r; b.vx = abs(b.vx) * bounce; moving = true }
            if (b.x > width - b.r) { b.x = width - b.r; b.vx = -abs(b.vx) * bounce; moving = true }
            if (b.y < b.r) { b.y = b.r; b.vy = abs(b.vy) * bounce; moving = true }
            if (b.y > height - b.r) { b.y = height - b.r; b.vy = -abs(b.vy) * bounce; moving = true }
            if (hypot(b.vx, b.vy) > 0.22f) moving = true
        }
        for (i in blobs.indices) for (j in i + 1 until blobs.size) {
            val a = blobs[i]; val c = blobs[j]
            val dx = c.x - a.x; val dy = c.y - a.y
            val d = hypot(dx, dy).coerceAtLeast(0.001f)
            val minD = a.r + c.r - 8
            if (d < minD) {
                val push = (minD - d) / d * 0.45f
                if (!(grabbing && a.held)) { a.x -= dx * push; a.y -= dy * push }
                if (!(grabbing && c.held)) { c.x += dx * push; c.y += dy * push }
            }
        }
        val cx = blobs.map { it.x }.average().toFloat(); val cy = blobs.map { it.y }.average().toFloat()
        val hold = pigW() * 0.55f
        for (b in blobs) {
            if (grabbing && b.held) continue
            val dx = b.x - cx; val dy = b.y - cy; val d = hypot(dx, dy)
            if (d > hold) {
                val t = (d - hold) / d * (if (modifiers["noGravity"] == true) 0.28f else 0.08f)
                b.x -= dx * t * 0.15f; b.y -= dy * t * 0.15f; b.vx -= dx * t; b.vy -= dy * t
            }
        }
        if (!grabbing) { pig.x = cx - pigW() / 2; pig.y = cy - pigH() * 0.4f }
        pig.vx = blobs.map { it.vx }.average().toFloat(); pig.vy = blobs.map { it.vy }.average().toFloat()
        pig.gooSleep = !grabbing && !moving && hypot(pig.vx, pig.vy) < 0.22f
    }

    private fun updateBlank(pig: Pig, dt: Float) {
        pig.angle = 0f; pig.spin = 0f; pig.ragdoll = false; pig.grounded = false; pig.thought = null
        applyRope(pig, dt)
        if (hypot(pig.vx, pig.vy) < 0.25f) { pig.vx += rand(-0.08f, 0.08f); pig.vy += rand(-0.08f, 0.08f) }
        pig.x += pig.vx * dt; pig.y += pig.vy * dt
        if (pig.blankLock > 0) pig.blankLock -= dt * 16.67f
        val w = pigW(); val h = pigH(); val speed = max(2.4f, hypot(pig.vx, pig.vy))
        fun bounce(nx: Float, ny: Float, incoming: Float) {
            if (pig.blankLock > 0 || incoming <= 0) return
            pig.x += nx * 8; pig.y += ny * 8; pig.vx = nx * speed; pig.vy = ny * speed; pig.blankLock = 90f
            tryMelt(pig, speed)
        }
        if (pig.x < 0) { pig.x = 0f; bounce(1f, 0f, -pig.vx) }
        if (pig.x > width - w) { pig.x = width - w; bounce(-1f, 0f, pig.vx) }
        if (pig.y < 0) { pig.y = 0f; bounce(0f, 1f, -pig.vy) }
        if (pig.y > height - h) { pig.y = height - h; bounce(0f, -1f, pig.vy) }
    }

    private fun resolvePigs() {
        if (pigs.size < 2 || modifiers["ghost"] == true) return
        val minDist = pigW() * (if (modifiers["blank"] == true) 0.78f else 0.68f)
        for (i in pigs.indices) for (j in i + 1 until pigs.size) {
            val a = pigs[i]; val b = pigs[j]
            if (a.dragging || b.dragging) continue
            val ax = a.x + pigW() / 2; val ay = a.y + pigH() * 0.4f
            val bx = b.x + pigW() / 2; val by = b.y + pigH() * 0.4f
            val dx = bx - ax; val dy = by - ay
            val d = hypot(dx, dy).coerceAtLeast(0.001f)
            if (d >= minDist) continue
            val nx = dx / d; val ny = dy / d
            val overlap = (minDist - d) / 2
            if (!a.goo) { a.x -= nx * overlap; a.y -= ny * overlap }
            if (!b.goo) { b.x += nx * overlap; b.y += ny * overlap }
            val impact = (a.vx - b.vx) * nx + (a.vy - b.vy) * ny
            if (impact > 0) {
                val bounce = if (modifiers["noGravity"] == true) 0.92f else 0.35f
                a.vx -= impact * bounce * nx; a.vy -= impact * bounce * ny
                b.vx += impact * bounce * nx; b.vy += impact * bounce * ny
                tryMelt(a, hypot(a.vx, a.vy) + abs(impact))
                tryMelt(b, hypot(b.vx, b.vy) + abs(impact))
            }
        }
    }

    private fun updateItems(dt: Float) {
        val gravity = if (modifiers["noGravity"] == true) 0f else if (modifiers["moon"] == true) 0.14f else if (modifiers["floaty"] == true) 0.2f else 0.38f
        val bounce = if (modifiers["bouncy"] == true) 0.86f else 0.58f
        for (it in treats + toys) {
            if (it.dragging) continue
            it.vy += gravity * dt; it.x += it.vx * dt * 6; it.y += it.vy * dt * 6; it.rot += it.spin * dt
            if (it.x < 0) { it.x = 0f; it.vx = abs(it.vx) * bounce; it.spin *= -1 }
            if (it.x > width - 24) { it.x = width - 24; it.vx = -abs(it.vx) * bounce; it.spin *= -1 }
            if (it.y < 0) { it.y = 0f; it.vy = abs(it.vy) * bounce }
            if (it.y > height - 28) { it.y = height - 28; it.vy = -abs(it.vy) * bounce; it.vx *= 0.96f; if (abs(it.vy) < 0.4f) it.vy = 0f }
        }
        for (pig in pigs) {
            val px = pig.x + pigW() / 2; val py = pig.y + pigH() * 0.7f
            for (ball in toys) {
                if (hypot(px - (ball.x + 14), py - (ball.y + 14)) < pigW() * 0.45f + ball.r) {
                    ball.vx += pig.vx * 0.9f + pig.direction * 1.8f
                    ball.vy = min(ball.vy, -3.2f - abs(pig.vx) * 0.15f)
                }
            }
            if (pig.dragging || pig.ragdoll) continue
            val eaten = treats.filter { hypot(px - (it.x + 12), py - (it.y + 12)) < pigW() * 0.42f + it.r }
            if (eaten.isNotEmpty()) {
                treats.removeAll(eaten.toSet())
                pig.happiness = (pig.happiness + 14).coerceIn(0f, 100f)
                pig.energy = (pig.energy + 8).coerceIn(0f, 100f)
                setState(pig, "dance", 700f); showThought(pig, "yum!")
            }
        }
    }

    private fun updateVisuals(pig: Pig, deltaMs: Float) {
        pig.anim += 0.12f
        if (pig.clickTimer > 0) pig.clickTimer -= deltaMs
        if (pig.thoughtTimer > 0) { pig.thoughtTimer -= deltaMs; if (pig.thoughtTimer <= 0) pig.thought = null }
        if (pig.heartTimer > 0) pig.heartTimer -= deltaMs
        var bob = 0f; var sx = 1f; var sy = 1f
        if (pig.dragging) { sx = 1.06f; sy = 0.94f }
        else if (!pig.ragdoll && pig.state in listOf("wander", "run", "follow", "play", "come", "eat")) {
            bob = sin(pig.anim * if (pig.state == "run") 2.5f else 1.5f) * 2.2f
        }
        if (pig.state == "sniff" && !pig.ragdoll) { bob = sin(pig.anim * 4f) * 1.3f; sx = 1.03f; sy = 0.97f }
        if ((pig.state == "nap" || pig.state == "sit") && !pig.ragdoll) { bob = sin(pig.anim * 0.8f) * 1.2f; sx = 1.04f; sy = 0.96f }
        if (pig.y < floorY() - 2 && !pig.dragging && !pig.ragdoll) { sx = 1.05f; sy = 0.95f }
        if (pig.state == "dance" && !pig.ragdoll) bob = sin(pig.anim * 5f) * 3f
        if (modifiers["blank"] == true) { bob = 0f; sx = 1f; sy = 1f; pig.angle = 0f }
        pig.bob = bob; pig.squashX = sx; pig.squashY = sy
    }

    private fun updateNeeds(pig: Pig, dt: Float) {
        pig.boredom = (pig.boredom + dt * 1.1f).coerceIn(0f, 100f)
        pig.happiness = (pig.happiness - dt * 0.12f).coerceIn(0f, 100f)
        pig.energy = (pig.energy + if (pig.state == "nap") dt * 8 else -dt * 0.15f).coerceIn(0f, 100f)
        pig.social = (pig.social + if (pigs.size > 1) dt * 0.4f else -dt * 0.5f).coerceIn(0f, 100f)
        pig.curiosityNeed = (pig.curiosityNeed + pig.curious * dt * 2).coerceIn(0f, 100f)
        updateMood(pig)
        if (pig.thought == null && chance(0.035f)) {
            val pool = pig.words.ifEmpty {
                when (pig.state) {
                    "nap" -> listOf("zzz", "five more minutes")
                    "eat" -> listOf("yum!", "snack time")
                    "dance", "play" -> listOf("hehe", "tag!")
                    else -> listOf("just walking", "oink", "looking around")
                }
            }
            showThought(pig, pool.random(), 1600f)
        }
    }

    private fun updateMood(pig: Pig) {
        when {
            pig.dragging -> { pig.mood = "loved"; pig.feeling = "being held" }
            pig.ragdoll -> { pig.mood = "dizzy"; pig.feeling = "wheee" }
            pig.energy < 18 -> { pig.mood = "sleepy"; pig.feeling = "needs a nap" }
            pig.boredom > 78 -> { pig.mood = "restless"; pig.feeling = "wants an adventure" }
            pig.happiness > 85 -> { pig.mood = "joyful"; pig.feeling = "everything is great" }
            pig.social < 30 -> { pig.mood = "lonely"; pig.feeling = "wants a friend" }
            else -> { pig.mood = "content"; pig.feeling = "calm" }
        }
        pig.reason = when (pig.state) {
            "nap" -> "Energy is low, so a nap comes first"
            "eat" -> "A snack smells better than walking"
            "come" -> "Someone called!"
            "follow" -> "${pig.target?.name ?: "a friend"} looks like good company"
            else -> "Choosing to ${pig.state} based on mood"
        }
    }

    private fun applyModifiersNow() {
        if (modifiers["giant"] == true && modifiers["tiny"] == true) modifiers["tiny"] = false
        for (pig in pigs) {
            if (modifiers["wetPile"] != true && pig.goo) reform(pig)
            if (modifiers["blank"] == true) { pig.angle = 0f; pig.spin = 0f; pig.ragdoll = false; pig.thought = null }
            if (modifiers["noGravity"] == true) {
                pig.grounded = false
                if (!pig.dragging && !pig.goo) {
                    if (hypot(pig.vx, pig.vy) < 0.2f) { pig.vx = rand(-1.6f, 1.6f); pig.vy = rand(-1.6f, 1.6f) }
                    if (modifiers["blank"] != true) { if (abs(pig.spin) < 0.01f) pig.spin = rand(-0.25f, 0.25f); enterRagdoll(pig) }
                }
            } else if (pig.ragdoll && modifiers["ragdoll"] != true) { exitRagdoll(pig); pig.angle = 0f }
        }
    }

    private fun applyModifierObject(obj: JSONObject) {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            modifiers[k] = obj.optBoolean(k)
        }
    }

    private fun enterRagdoll(pig: Pig) {
        pig.ragdoll = true
        pig.ragdollTimer = if (modifiers["noGravity"] == true) 1e9f else 2800f
        if (abs(pig.spin) < 0.04f) pig.spin = pig.vx * 0.08f + rand(-0.12f, 0.12f)
    }
    private fun exitRagdoll(pig: Pig) {
        pig.ragdoll = false
        if (modifiers["noGravity"] != true) { pig.angle = 0f; pig.spin = 0f }
    }

    private fun setState(pig: Pig, state: String, duration: Float? = null) {
        pig.state = state; pig.stateTime = duration ?: rand(800f, 3000f)
    }
    private fun showThought(pig: Pig, text: String, duration: Float = 1500f) {
        if (modifiers["blank"] == true) return
        pig.thought = text; pig.thoughtTimer = duration
    }
    private fun moveToward(pig: Pig, targetX: Float, speed: Float) {
        val dx = targetX - pig.x
        if (abs(dx) < 6) { pig.vx *= 0.8f; return }
        pig.direction = if (dx > 0) 1 else -1
        pig.vx = pig.direction * speed
    }
    private fun nearestTreat(pig: Pig) = treats.minByOrNull { hypot(it.x - pig.x, it.y - pig.y) }?.takeIf { hypot(it.x - pig.x, it.y - pig.y) < 900 }
    private fun fling(samples: List<Sample>): Pair<Float, Float> {
        if (samples.size < 2) return 0f to 0f
        val last = samples.last()
        val first = samples.firstOrNull { last.t - it.t <= 90 } ?: samples.first()
        val d = max(8f, (last.t - first.t).toFloat())
        return (((last.x - first.x) / d) * 16.67f) to (((last.y - first.y) / d) * 16.67f)
    }
    private fun modifiersJson(): JSONObject {
        val o = JSONObject()
        modifiers.forEach { (k, v) -> o.put(k, v) }
        return o
    }
    private fun ok(message: String) = JSONObject()
        .put("success", true).put("message", message).put("pigCount", pigs.size)
        .put("modifiers", modifiersJson()).put("version", 19).put("pigWidth", pigWidth)
    private fun now() = System.currentTimeMillis()
    private fun rand(a: Float, b: Float) = a + Random.nextFloat() * (b - a)
    private fun chance(p: Float) = Random.nextFloat() < p
    private fun pick(vararg s: String) = s.random()
    private fun weighted(options: List<Pair<String, Float>>): String {
        val total = options.sumOf { it.second.toDouble() }.toFloat()
        var roll = Random.nextFloat() * total
        for (o in options) { roll -= o.second; if (roll <= 0) return o.first }
        return options.last().first
    }

    companion object {
        val NAMES = listOf("Pip","Poppy","Bacon","Pudding","Muffin","Peaches","Waffles","Beans","Pickle","Noodle","Hammy","Truffle","Squeak","Clover","Butter","Biscuit","Maple","Sunny","Pebble","Cloud","Daisy","Cocoa","Honey","Olive","Mochi","Bounce","Cookie","Puff")
        fun defaultModifiers() = mutableMapOf(
            "noGravity" to false, "bouncy" to false, "floaty" to false, "moon" to false,
            "magnet" to false, "superSpeed" to false, "giant" to false, "tiny" to false,
            "frozen" to false, "chaos" to false, "ghost" to false, "nameTags" to true,
            "party" to false, "ragdoll" to true, "rope" to false, "blank" to false, "wetPile" to false,
        )
    }
}

class Pig(
    val id: Int,
    var name: String,
    var imageUrl: String,
    var x: Float,
    var y: Float,
    var vx: Float = 0f,
    var vy: Float = 0f,
    var angle: Float = 0f,
    var spin: Float = 0f,
    var direction: Int,
    var state: String = "wander",
    var stateTime: Float = 1200f,
    var decisionTimer: Float = 400f,
    var anim: Float = 0f,
    var clicked: Boolean = false,
    var clickTimer: Float = 0f,
    var energy: Float,
    var boredom: Float,
    var curiosityNeed: Float,
    var happiness: Float,
    var social: Float,
    var comfort: Float = 70f,
    var mood: String = "content",
    var feeling: String = "ready",
    var reason: String = "",
    var thought: String? = null,
    var thoughtTimer: Float = 0f,
    var heartTimer: Float = 0f,
    var target: Pig? = null,
    var treat: WorldItem? = null,
    var grounded: Boolean = true,
    var dragging: Boolean = false,
    var dragMoved: Boolean = false,
    var dragOffX: Float = 0f,
    var dragOffY: Float = 0f,
    var ragdoll: Boolean = false,
    var ragdollTimer: Float = 0f,
    var goo: Boolean = false,
    var gooSleep: Boolean = false,
    var lastImpact: Long = 0,
    var blankLock: Float = 0f,
    var bob: Float = 0f,
    var squashX: Float = 1f,
    var squashY: Float = 1f,
    var play: Float,
    var curious: Float,
    var lazy: Float,
    var brave: Float,
    var socialNeed: Float,
    var pep: Float,
    var words: List<String> = emptyList(),
    val samples: MutableList<Sample> = mutableListOf(),
    val blobs: MutableList<GooBlob> = mutableListOf(),
)

class WorldItem(
    val id: Int,
    val kind: String,
    val emoji: String,
    val r: Float,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var rot: Float,
    var spin: Float,
    var dragging: Boolean = false,
    var dragOffX: Float = 0f,
    var dragOffY: Float = 0f,
    val samples: MutableList<Sample> = mutableListOf(),
)

class GooBlob(var x: Float, var y: Float, var vx: Float, var vy: Float, var r: Float, var held: Boolean = false)
class Sample(val t: Long, val x: Float, val y: Float)
