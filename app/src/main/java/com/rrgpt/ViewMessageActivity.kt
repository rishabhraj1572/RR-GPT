package com.rrgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bumptech.glide.Glide
import com.google.android.flexbox.FlexboxLayout
import io.noties.markwon.Markwon

class ViewMessageActivity : AppCompatActivity() {

    private lateinit var markwon: Markwon

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        markwon = Markwon.create(this)

        // ---------- ScrollView ----------
        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT
            )
            fitsSystemWindows = true
        }

        // ---------- Root LinearLayout ----------
        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(16, 16, 16, 16) // compact padding
            fitsSystemWindows = true
        }

        scrollView.addView(rootLayout)
        setContentView(scrollView)

        // ---------- WindowInsets for status + nav bar ----------
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                16,
                16 + systemBars.top,    // status bar upar space
                16,
                16 + systemBars.bottom  // nav bar niche space
            )
            insets
        }

        val msg = intent.getSerializableExtra("message") as? ChatMessage

        if (msg == null) {
            val tv = TextView(this).apply {
                text = "⚠️ No message to display"
                setTextColor(0xFF888888.toInt())
                textSize = 16f
                textAlignment = View.TEXT_ALIGNMENT_CENTER
            }
            rootLayout.addView(tv)
            return
        }

        // ---------- TEXT SEGMENTS ----------
        val segments = parseMarkdownSegments(msg.text.orEmpty())
        if (segments.isEmpty()) {
            if (!msg.text.isNullOrBlank()) {
                rootLayout.addView(makeCard {
                    val tv = TextView(this).apply {
                        text = msg.text
                        textSize = 16f
                        setTextColor(0xFF111111.toInt())
                        setTextIsSelectable(true)
                        movementMethod = LinkMovementMethod.getInstance()
                    }
                    it.addView(tv)
                })
            }
        } else {
            segments.forEach { seg ->
                when (seg) {
                    is ChatAdapter.Segment.Text -> {
                        rootLayout.addView(makeCard {
                            val tv = TextView(this).apply {
                                textSize = 16f
                                setTextColor(0xFF111111.toInt())
                                setTextIsSelectable(true)
                                movementMethod = LinkMovementMethod.getInstance()
                            }
                            markwon.setMarkdown(tv, seg.markdown)
                            it.addView(tv)
                        })
                    }
                    is ChatAdapter.Segment.Code -> {
                        rootLayout.addView(makeCard {
                            val codeLayout = layoutInflater.inflate(
                                R.layout.item_code_block,
                                it,
                                false
                            )
                            val tvCode = codeLayout.findViewById<TextView>(R.id.tvCode)
                            val btnCopy = codeLayout.findViewById<ImageButton>(R.id.btnCopy)

                            tvCode.text = seg.code
                            tvCode.setTextIsSelectable(true)

                            btnCopy.setOnClickListener {
                                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText(seg.language ?: "code", seg.code))
                                Toast.makeText(this, "Copied ✅", Toast.LENGTH_SHORT).show()
                            }

                            it.addView(codeLayout)
                        })
                    }
                }
            }
        }

        // ---------- IMAGES ----------
        if (!msg.images.isNullOrEmpty()) {
            rootLayout.addView(makeCard {
                val imageContainer = FlexboxLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                }

                msg.images.forEach { uri ->
                    val sizePx = (120 * resources.displayMetrics.density).toInt()
                    val iv = ImageView(this).apply {
                        layoutParams = FlexboxLayout.LayoutParams(sizePx, sizePx).apply {
                            setMargins(8, 8, 8, 8)
                        }
                        scaleType = ImageView.ScaleType.CENTER_CROP
                        clipToOutline = true
                    }
                    Glide.with(this).load(uri).into(iv)
                    imageContainer.addView(iv)
                }

                it.addView(imageContainer)
            })
        }
    }

    // ---------- CardView Helper ----------
    private fun makeCard(content: (LinearLayout) -> Unit): CardView {
        val card = CardView(this).apply {
            radius = 20f
            cardElevation = 4f
            useCompatPadding = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 16)
            }
        }
        val inner = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        card.addView(inner)
        content(inner)
        return card
    }

    // ---------- Markdown Parser ----------
    private fun parseMarkdownSegments(input: String): List<ChatAdapter.Segment> {
        val out = ArrayList<ChatAdapter.Segment>()
        var i = 0
        val n = input.length

        fun nextFence(from: Int): Pair<Int, String>? {
            val a = input.indexOf("```", from)
            val b = input.indexOf("~~~", from)
            if (a == -1 && b == -1) return null
            return if (a == -1) b to "~~~"
            else if (b == -1) a to "```"
            else if (a < b) a to "```" else b to "~~~"
        }

        while (i < n) {
            val pair = nextFence(i)
            if (pair == null) {
                out.add(ChatAdapter.Segment.Text(input.substring(i)))
                break
            }

            val start = pair.first
            val fence = pair.second

            if (start > i) out.add(ChatAdapter.Segment.Text(input.substring(i, start)))

            val langStart = start + fence.length
            var lineEnd = input.indexOf('\n', langStart)
            if (lineEnd == -1) lineEnd = n
            val languageRaw = input.substring(langStart, lineEnd).trim()
            val language = if (languageRaw.isEmpty()) null else languageRaw

            var codeStart = lineEnd + 1
            if (codeStart <= n && lineEnd + 1 < n && input[lineEnd] == '\r') codeStart++

            var search = codeStart
            var close = -1
            while (search < n) {
                val candidate = input.indexOf(fence, search)
                if (candidate == -1) break
                val lineStart = input.lastIndexOf('\n', candidate - 1) + 1
                val prefix = input.substring(lineStart, candidate)
                if (prefix.trim().isEmpty()) {
                    close = candidate
                    break
                } else {
                    search = candidate + fence.length
                }
            }

            if (close == -1) {
                out.add(ChatAdapter.Segment.Text(input.substring(start)))
                break
            }

            val code = input.substring(codeStart, close).trimEnd('\n', '\r')
            out.add(ChatAdapter.Segment.Code(language, code))

            i = close + fence.length
            if (i < n && input[i] == '\r') i++
            if (i < n && input[i] == '\n') i++
        }
        return out
    }
}
