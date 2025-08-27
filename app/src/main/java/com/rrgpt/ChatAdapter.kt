package com.rrgpt

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat.getSystemService
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.flexbox.FlexboxLayout
import io.noties.markwon.Markwon

class ChatAdapter(
    private val messages: MutableList<ChatMessage>,
    private val markwon: Markwon
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Segment {
        data class Text(val markdown: String) : Segment()
        data class Code(val language: String?, val code: String) : Segment()
    }

    companion object {
        private const val VIEW_USER = 1
        private const val VIEW_ASSISTANT = 2
    }

    override fun getItemViewType(position: Int): Int =
        if (messages[position].isUser) VIEW_USER else VIEW_ASSISTANT

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_USER) {
            UserViewHolder(inflater.inflate(R.layout.item_chat_user, parent, false))
        } else {
            AssistantViewHolder(inflater.inflate(R.layout.item_chat_assistant, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val msg = messages[position]
        if (holder is UserViewHolder) {
            bindCommon(holder.contentContainer, holder.imageContainer, msg, true)
        } else if (holder is AssistantViewHolder) {
            bindCommon(holder.contentContainer, holder.imageContainer, msg, false)
        }
    }

    private fun bindCommon(
        contentContainer: LinearLayout,
        imageContainer: FlexboxLayout,
        msg: ChatMessage,
        isUser: Boolean
    ) {
        clearTextViews(contentContainer, imageContainer)

        val text = msg.text.orEmpty()
        if (text.isNotBlank()) {
            val segments = parseMarkdownSegments(text)
            for (seg in segments) {
                when (seg) {
                    is Segment.Text -> {
                        val tv = TextView(contentContainer.context).apply {
                            layoutParams = LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            )
                            setTextColor(if (isUser) 0xFFFFFFFF.toInt() else 0xFF111111.toInt())
                            textSize = 16f
                            movementMethod = LinkMovementMethod.getInstance()
                        }
                        markwon.setMarkdown(tv, seg.markdown.trim())

                        // ✅ Long press dialog for text
                        if (!isUser){
                            tv.setOnLongClickListener {
                                showMessageOptionsDialog(it.context, msg)
                                true
                            }
                        }else{
                            tv.setOnLongClickListener {
                                showMessageOptionsDialogUser(it.context, msg)
                                true
                            }
                        }


                        contentContainer.addView(tv)
                    }
                    is Segment.Code -> {
                        val v = LayoutInflater.from(contentContainer.context)
                            .inflate(R.layout.item_code_block, contentContainer, false)
                        val tvCode = v.findViewById<TextView>(R.id.tvCode)
                        val btnCopy = v.findViewById<ImageButton>(R.id.btnCopy)

                        tvCode.text = seg.code

                        // ✅ Long press dialog for code block
//                        tvCode.setOnLongClickListener {
//                            showMessageOptionsDialog(it.context, msg)
//                            true
//                        }

                        btnCopy.setOnClickListener {
                            val ctx = v.context
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText(seg.language ?: "code", seg.code))
                            android.widget.Toast.makeText(ctx, "Copied", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        contentContainer.addView(v)
                    }
                }
            }
        }

        // Images
        imageContainer.removeAllViews()
        if (msg.images.isEmpty()) {
            imageContainer.visibility = View.GONE
        } else {
            imageContainer.visibility = View.VISIBLE
            val context = imageContainer.context
            val sizePx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 120f, context.resources.displayMetrics
            ).toInt()
            val marginPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 4f, context.resources.displayMetrics
            ).toInt()

            for (uri: Uri in msg.images) {
                val iv = ImageView(context).apply {
                    layoutParams = FlexboxLayout.LayoutParams(sizePx, sizePx).apply {
                        setMargins(marginPx, marginPx, marginPx, marginPx)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    clipToOutline = true
                    setOnClickListener {
                        val intent = android.content.Intent(context, FullScreenImageActivity::class.java)
                        intent.putExtra("image_uri", uri.toString())
                        context.startActivity(intent)
                    }
                    // ✅ Long press dialog for image

                    if (!isUser){
                        setOnLongClickListener {
                            showMessageOptionsDialog(context, msg)
                            true
                        }
                    }else{
                        setOnLongClickListener {
                            showMessageOptionsDialogUser(context, msg)
                            true
                        }
                    }

                }
                Glide.with(context).load(uri).into(iv)
                imageContainer.addView(iv)
            }
        }
    }

    // ✅ Helper for dialog
    private fun showMessageOptionsDialog(ctx: Context, msg: ChatMessage) {
        val options = arrayOf("Copy Message","Report message", "View message")
        android.app.AlertDialog.Builder(ctx)
            .setItems(options) { _, which ->
                when (which) {
                    0-> {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("text", msg.text))
                        Toast.makeText(ctx, "Copied ✅", Toast.LENGTH_SHORT).show()
                    }
                    1 -> android.widget.Toast.makeText(ctx, "Message reported", android.widget.Toast.LENGTH_SHORT).show()
                    2 -> {
                        val intent = Intent(ctx, ViewMessageActivity::class.java)
                        intent.putExtra("message", msg)   // ✅ direct poora object bhej diya
                        ctx.startActivity(intent)

                    }
                }
            }
            .show()
    }

    private fun showMessageOptionsDialogUser(ctx: Context, msg: ChatMessage) {
        val options = arrayOf("Copy Message","View message")
        android.app.AlertDialog.Builder(ctx)
            .setItems(options) { _, which ->
                when (which) {
                    0-> {
                        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("text", msg.text))
                        Toast.makeText(ctx, "Copied ✅", Toast.LENGTH_SHORT).show()
                    }
                    1 -> {
                        val intent = Intent(ctx, ViewMessageActivity::class.java)
                        intent.putExtra("message", msg)   // ✅ direct poora object bhej diya
                        ctx.startActivity(intent)

                    }
                }
            }
            .show()
    }

    private fun clearTextViews(contentContainer: LinearLayout, imageContainer: View) {
        if (imageContainer.parent === contentContainer) {
            for (i in contentContainer.childCount - 1 downTo 0) {
                val child = contentContainer.getChildAt(i)
                if (child !== imageContainer) contentContainer.removeViewAt(i)
            }
        } else {
            contentContainer.removeAllViews()
        }
    }

    override fun getItemCount() = messages.size

    class UserViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contentContainer: LinearLayout = view.findViewById(R.id.contentContainer)
        val imageContainer: FlexboxLayout = view.findViewById(R.id.imageContainer)
    }

    class AssistantViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val contentContainer: LinearLayout = view.findViewById(R.id.contentContainer)
        val imageContainer: FlexboxLayout = view.findViewById(R.id.imageContainer)
    }

    // Parser remains same
    private fun parseMarkdownSegments(input: String): List<Segment> {
        val out = ArrayList<Segment>()
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
                out.add(Segment.Text(input.substring(i)))
                break
            }

            val start = pair.first
            val fence = pair.second

            if (start > i) out.add(Segment.Text(input.substring(i, start)))

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
                out.add(Segment.Text(input.substring(start)))
                break
            }

            val code = input.substring(codeStart, close).trimEnd('\n', '\r')
            out.add(Segment.Code(language, code))

            i = close + fence.length
            if (i < n && input[i] == '\r') i++
            if (i < n && input[i] == '\n') i++
        }
        return out
    }
}
