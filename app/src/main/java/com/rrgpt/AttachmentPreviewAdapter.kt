package com.rrgpt

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.GlideContext

class AttachmentPreviewAdapter(
    private val items: MutableList<Uri>,   // ✅ specify type
    private val onRemove: (uri: Uri, position: Int) -> Unit,
    private val context: Context
) : RecyclerView.Adapter<AttachmentPreviewAdapter.AttViewHolder>() {

    class AttViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivThumb: ImageView = view.findViewById(R.id.ivThumb)
        val tvFileName: TextView = view.findViewById(R.id.tvFileName)
        val btnRemove: ImageButton = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AttViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_attachment_preview, parent, false)
        return AttViewHolder(view)
    }

    override fun onBindViewHolder(holder: AttViewHolder, position: Int) {
        val uri = items[position]
        val mime = context.contentResolver.getType(uri) ?: ""

        if (mime.startsWith("image/")) {
            holder.ivThumb.visibility = View.VISIBLE
            holder.tvFileName.visibility = View.GONE
            Glide.with(holder.itemView.context)
                .load(uri)
                .into(holder.ivThumb)

            holder.ivThumb.setOnClickListener {
                val intent = android.content.Intent(context, FullScreenImageActivity::class.java)
                intent.putExtra("image_uri", uri.toString())
                context.startActivity(intent)
            }

        } else {
            holder.ivThumb.visibility = View.GONE
            holder.tvFileName.visibility = View.VISIBLE
            holder.tvFileName.text = uri.lastPathSegment ?: "File"
            holder.tvFileName.setOnClickListener {
                // Optional: open file using Intent
                try {
                    val intent = android.content.Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, mime)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(context, "Cannot open file", Toast.LENGTH_SHORT).show()
                }
            }
        }

        holder.btnRemove.setOnClickListener {
            onRemove(uri, holder.bindingAdapterPosition)
        }
    }

    override fun getItemCount(): Int = items.size
}
