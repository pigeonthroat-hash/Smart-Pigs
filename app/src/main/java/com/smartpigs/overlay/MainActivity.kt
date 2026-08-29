package com.smartpigs.overlay

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {

    private lateinit var status: TextView
    private lateinit var statusPill: TextView
    private lateinit var pigCountText: TextView
    private lateinit var presetCountText: TextView
    private lateinit var pigsContainer: LinearLayout
    private lateinit var presetsContainer: LinearLayout

    private lateinit var btnPermission: MaterialButton
    private lateinit var btnStart: MaterialButton
    private lateinit var btnStop: MaterialButton
    private lateinit var btnAddPig: MaterialButton
    private lateinit var btnAddFive: MaterialButton
    private lateinit var btnSnacks: MaterialButton

    private val store by lazy { SettingsStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        status = findViewById(R.id.statusText)
        statusPill = findViewById(R.id.statusPill)
        pigCountText = findViewById(R.id.pigCountText)
        presetCountText = findViewById(R.id.presetCountText)
        pigsContainer = findViewById(R.id.pigsContainer)
        presetsContainer = findViewById(R.id.presetsContainer)

        btnPermission = findViewById(R.id.btnPermission)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        btnAddPig = findViewById(R.id.btnAddPig)
        btnAddFive = findViewById(R.id.btnAddFive)
        btnSnacks = findViewById(R.id.btnSnacks)

        btnPermission.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        btnStart.setOnClickListener {
            startOverlay()
        }

        btnStop.setOnClickListener {
            stopOverlay()
        }

        btnAddPig.setOnClickListener {
            OverlayService.instance?.handleAction(
                "addPig",
                org.json.JSONObject()
            )
            refresh()
        }

        btnAddFive.setOnClickListener {
            val service = OverlayService.instance ?: return@setOnClickListener

            repeat(5) {
                service.handleAction(
                    "addPig",
                    org.json.JSONObject()
                )
            }

            refresh()
        }

        btnSnacks.setOnClickListener {
            OverlayService.instance?.handleAction(
                "dropTreats",
                org.json.JSONObject().put("count", 5)
            )
            refresh()
        }

        maybeAskNotifications()
        refresh()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            refresh()
        }
    }

    private fun refresh() {
        val overlay = Settings.canDrawOverlays(this)
        val service = OverlayService.instance
        val running = service != null && service.engine.running

        status.text = when {
            !overlay ->
                "Overlay permission is off. Allow it so pigs can appear above other apps."

            running ->
                "Overlay is running. Press Home and your pigs will stay on screen."

            else ->
                "Ready. Start the overlay to bring your pigs to life."
        }

        statusPill.text = if (running) "ON" else "OFF"

        btnPermission.isEnabled = !overlay
        btnStart.isEnabled = overlay && !running
        btnStop.isEnabled = running
        btnAddPig.isEnabled = running
        btnAddFive.isEnabled = running
        btnSnacks.isEnabled = running

        val pigCount = service?.engine?.pigs?.size ?: 0
        pigCountText.text = pigCount.toString()

        val presets = readPresets()
        presetCountText.text = presets.length().toString()

        renderPigs()
        renderPresets()
    }

    private fun renderPigs() {
        pigsContainer.removeAllViews()

        val service = OverlayService.instance
        val pigs = service?.engine?.pigs ?: emptyList()

        if (pigs.isEmpty()) {
            addEmptyMessage(
                pigsContainer,
                "No pigs are running right now."
            )
            return
        }

        for (pig in pigs) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = getDrawable(R.drawable.card_background)
            }

            val header = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }

            val name = TextView(this).apply {
                text = pig.name
                textSize = 18f
                setTextColor(getColor(R.color.ink))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            val mood = TextView(this).apply {
                text = pig.mood
                textSize = 13f
                setTextColor(getColor(R.color.rose_deep))
                gravity = Gravity.END
            }

            header.addView(
                name,
                LinearLayout.LayoutParams(0, dp(28), 1f)
            )

            header.addView(
                mood,
                LinearLayout.LayoutParams(
                    dp(100),
                    dp(28)
                )
            )

            card.addView(header)

            val feeling = TextView(this).apply {
                text = pig.feeling
                textSize = 13f
                setTextColor(getColor(R.color.muted))
            }

            card.addView(
                feeling,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(3)
                }
            )

            addStat(card, "Energy", pig.energy)
            addStat(card, "Happiness", pig.happiness)
            addStat(card, "Boredom", pig.boredom)
            addStat(card, "Curiosity", pig.curiosityNeed)

            val personality = TextView(this).apply {
                text = "Play ${percent(pig.play)}  •  Social ${percent(pig.socialNeed)}  •  Lazy ${percent(pig.lazy)}  •  Brave ${percent(pig.brave)}"
                textSize = 12f
                setTextColor(getColor(R.color.muted))
            }

            card.addView(
                personality,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(8)
                }
            )

            pigsContainer.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            )
        }
    }

    private fun addStat(
        parent: LinearLayout,
        label: String,
        value: Float
    ) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val text = TextView(this).apply {
            text = "$label ${value.roundToInt()}"
            textSize = 12f
            setTextColor(getColor(R.color.ink))
        }

        row.addView(
            text,
            LinearLayout.LayoutParams(
                dp(100),
                dp(26)
            )
        )

        val bar = TextView(this).apply {
            setBackgroundColor(getColor(R.color.rose))
            alpha = 0.35f
        }

        row.addView(
            bar,
            LinearLayout.LayoutParams(
                0,
                dp(8),
                (value.coerceIn(0f, 100f) / 100f)
            ).apply {
                marginStart = dp(4)
            }
        )

        parent.addView(row)
    }

    private fun renderPresets() {
        presetsContainer.removeAllViews()

        val presets = readPresets()

        if (presets.length() == 0) {
            addEmptyMessage(
                presetsContainer,
                "No saved presets yet. Create them from the floating pig menu."
            )
            return
        }

        for (i in 0 until presets.length()) {
            val preset = presets.optJSONObject(i) ?: continue

            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = getDrawable(R.drawable.card_background)
            }

            val name = TextView(this).apply {
                text = preset.optString("name", "Custom Pig")
                textSize = 18f
                setTextColor(getColor(R.color.ink))
                setTypeface(typeface, android.graphics.Typeface.BOLD)
            }

            card.addView(name)

            val personality = preset.optJSONObject("personality")

            val details = TextView(this).apply {
                text =
                    "Play ${percent(personality?.optDouble("playfulness", 0.5)?.toFloat() ?: 0.5f)}" +
                            "  •  Curious ${percent(personality?.optDouble("curiosity", 0.5)?.toFloat() ?: 0.5f)}" +
                            "  •  Social ${percent(personality?.optDouble("sociability", 0.5)?.toFloat() ?: 0.5f)}" +
                            "  •  Lazy ${percent(personality?.optDouble("laziness", 0.5)?.toFloat() ?: 0.5f)}" +
                            "  •  Brave ${percent(personality?.optDouble("bravery", 0.5)?.toFloat() ?: 0.5f)}"

                textSize = 12f
                setTextColor(getColor(R.color.muted))
            }

            card.addView(
                details,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(5)
                }
            )

            val words = preset.optJSONArray("words")

            if (words != null && words.length() > 0) {
                val wordList = mutableListOf<String>()

                for (w in 0 until minOf(words.length(), 5)) {
                    wordList += words.optString(w)
                }

                val wordsView = TextView(this).apply {
                    text = wordList.joinToString(" • ")
                    textSize = 12f
                    setTextColor(getColor(R.color.muted))
                }

                card.addView(
                    wordsView,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = dp(5)
                    }
                )
            }

            val add = MaterialButton(this).apply {
                text = "Add to screen"
                isAllCaps = false

                setOnClickListener {
                    OverlayService.instance?.handleAction(
                        "addPig",
                        org.json.JSONObject().put("preset", preset)
                    )
                    refresh()
                }
            }

            card.addView(
                add,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    dp(48)
                ).apply {
                    topMargin = dp(8)
                }
            )

            presetsContainer.addView(
                card,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dp(10)
                }
            )
        }
    }

    private fun readPresets(): JSONArray {
        return store.getJson().optJSONArray("pigPresets") ?: JSONArray()
    }

    private fun addEmptyMessage(
        parent: LinearLayout,
        message: String
    ) {
        parent.addView(
            TextView(this).apply {
                text = message
                textSize = 13f
                setTextColor(getColor(R.color.muted))
                setPadding(dp(16), dp(16), dp(16), dp(16))
            }
        )
    }

    private fun addStatCard() {
        // Intentionally unused; kept out so this screen stays lightweight.
    }

    private fun percent(value: Float): String {
        return "${(value.coerceIn(0f, 1f) * 100f).roundToInt()}%"
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private fun startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            refresh()
            return
        }

        ContextCompat.startForegroundService(
            this,
            Intent(this, OverlayService::class.java)
        )

        window.decorView.postDelayed({
            refresh()
        }, 350)
    }

    private fun stopOverlay() {
        stopService(
            Intent(this, OverlayService::class.java)
        )

        window.decorView.postDelayed({
            refresh()
        }, 350)
    }

    private fun maybeAskNotifications() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    42
                )
            }
        }
    }
}