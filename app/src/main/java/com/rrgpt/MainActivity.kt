package com.rrgpt

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class MainActivity : AppCompatActivity() {

    // ===== UI =====
    private lateinit var btnCamera: ImageButton
    private lateinit var btnCapture: ImageButton
    private lateinit var btnSend: ImageButton
    private lateinit var btnAttach: ImageButton
    private lateinit var etMessage: EditText
    private lateinit var chatRecyclerView: RecyclerView
    private lateinit var attachmentsRecycler: RecyclerView
    private lateinit var previewView: PreviewView
    private lateinit var cameraContainer: FrameLayout
    private lateinit var emptyState: LinearLayout

    // ===== Chat data =====
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var previewAdapter: AttachmentPreviewAdapter
    private lateinit var markwon: Markwon
    private val messages = mutableListOf<ChatMessage>()
    private val attachedImages = mutableListOf<Uri>()
    private val attachedImagesP = mutableListOf<Uri>()
    private val apiMessagesForModel = mutableListOf<JSONObject>()
    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .writeTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            .build()
    }

    // ===== CameraX =====
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var isCapturing = false

    // ===== Azure OpenAI (your values) =====
    private val AZURE_OPENAI_KEY =""
    private val AZURE_ENDPOINT =""
    private val DEPLOYMENT = ""
    private val API_VERSION = ""

    private val CLOUDINARY_CLOUD=""

    // ===== Permission =====
    private val CAMERA_PERMISSION_REQ = 101

    // ==== User Settings ====
    private var userEndpoint: String = ""
    private var userApiKey: String = ""
    private var userApiVersion: String = ""
    private var userModel: String = ""
    private var userCloud : String=""

    private val DEFAULT_SYSTEM_PROMPT = "You are a helpful assistant. Keep answers concise unless asked otherwise."

    private val CHAT_PREFS = "chat_history"
    private val KEY_MESSAGES_JSON = "messages_json"

    private var currentCall: Call? = null
    private var isStreaming = false
    private var streamingMessageIndex: Int = -1


    private val currentPdfB64List = mutableListOf<String>()

    private val filePicker = registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (!uris.isNullOrEmpty()) {
                for (uri in uris) {
                    val mime = contentResolver.getType(uri)
                    if (mime == "application/pdf") {
                        val pdfFile = File(cacheDir, "temp_${System.currentTimeMillis()}.pdf")
                        contentResolver.openInputStream(uri)?.use { input ->
                            FileOutputStream(pdfFile).use { output -> input.copyTo(output) }
                        }
                        pdfToCloudinaryLinks(pdfFile) { urls ->
                            urls.forEach { url ->
                                // sirf preview ke liye dummy Uri (original local file), lekin API ke liye hum url store karenge
                                attachedImages.add(Uri.parse(url))
                                attachedImagesP.add(Uri.parse(pdfFile.toString()))
                            }
                            previewAdapter.notifyDataSetChanged()
                            updateAttachmentsVisibility()
                            updateSendEnabled()
                        }
                    } else {
                        uploadToCloudinary(uri) { url ->
                            if (url != null) {
                                attachedImages.add(Uri.parse(url)) // ✅ Cloudinary ka HTTPS URL
                                attachedImagesP.add(Uri.parse(uri.toString()))
                                previewAdapter.notifyDataSetChanged()
                                updateAttachmentsVisibility()
                                updateSendEnabled()
                            } else {
                                Toast.makeText(this, "Image upload failed", Toast.LENGTH_SHORT).show()
                            }
                        }

                    }
                }
                previewAdapter.notifyDataSetChanged()
                updateAttachmentsVisibility()
                updateSendEnabled()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val settingsBtn=findViewById<ImageButton>(R.id.btnSettings)
        settingsBtn.setOnClickListener {
            showSettingsDialog()
        }

        val clearBtn = findViewById<ImageButton>(R.id.btnClear)
        clearBtn.setOnClickListener { showClearConfirmDialog() }

        // Markdown styling
        markwon = Markwon.builder(this)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder
                        .codeTextColor(android.graphics.Color.parseColor("#111827"))
                        .codeBackgroundColor(android.graphics.Color.parseColor("#E5E7EB"))
                        .codeBlockBackgroundColor(android.graphics.Color.parseColor("#0F172A"))
                }
            })
            .build()

        // Bind views
        btnCamera = findViewById(R.id.btnCamera)
        btnCapture = findViewById(R.id.btnCapture)
        btnSend = findViewById(R.id.btnSend)
        btnAttach = findViewById(R.id.btnAttach)
        etMessage = findViewById(R.id.etMessage)
        chatRecyclerView = findViewById(R.id.chatRecyclerView)
        attachmentsRecycler = findViewById(R.id.attachmentsRecycler)
        previewView = findViewById(R.id.previewView)
        cameraContainer = findViewById(R.id.cameraContainer)
        emptyState=findViewById(R.id.emptyState)

        // Chat list
        chatAdapter = ChatAdapter(messages, markwon)
        chatRecyclerView.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        chatRecyclerView.adapter = chatAdapter

        // Attachments strip
        attachmentsRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        previewAdapter = AttachmentPreviewAdapter(attachedImagesP, { _, position ->
            attachedImages.removeAt(position)
            attachedImagesP.removeAt(position)
            previewAdapter.notifyItemRemoved(position)
            updateAttachmentsVisibility()
            updateSendEnabled()
        }, this) // pass context

        attachmentsRecycler.adapter = previewAdapter

        // Listeners
        btnAttach.setOnClickListener {
            filePicker.launch("*/*")  // image bhi select kar sakte ho + PDF/any file
        }

        etMessage.addTextChangedListener { updateSendEnabled() }

        if (loadUserSettings()) {
            // Force the user to fill settings
            Toast.makeText(this, "Please fill in your Azure API details", Toast.LENGTH_LONG).show()
            showSettingsDialog()
            btnSend.isEnabled = false // prevent sending until filled
        }

        btnSend.setOnClickListener {
            if (isStreaming) {
                currentCall?.cancel()
                stopStreamingUI()
            } else {
                val text = etMessage.text.toString().trim()
                if (text.isEmpty() && attachedImages.isEmpty()) return@setOnClickListener

                val imagesForThisTurn = attachedImages.toList()
                val userMsg = ChatMessage(text, imagesForThisTurn, true)
                messages.add(userMsg)
                chatAdapter.notifyItemInserted(messages.size - 1)
                chatRecyclerView.scrollToPosition(messages.size - 1)

                updateEmptyState()

                apiMessagesForModel.add(buildUserMessageForApi(text, imagesForThisTurn))

                saveChatHistory()

                etMessage.text.clear()
                attachedImages.clear()
                attachedImagesP.clear()
                previewAdapter.notifyDataSetChanged()
                updateAttachmentsVisibility()
                updateSendEnabled()

                btnSend.setImageResource(android.R.drawable.ic_media_pause)
                isStreaming = true

                // Call GPT based on selection
                val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                val gptMode = prefs.getString("gpt_mode", "normal")
                if (gptMode == "azure") callAzureOpenAI()
                else callGithubOpenAI()
            }
        }

        // Camera UI toggle
        btnCamera.setOnClickListener {
            val view = this.currentFocus
            if (view != null) {
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(view.windowToken, 0)
            }
            if (cameraContainer.isVisible) {
                closeCameraUI()
            } else {
                requestCameraPermissionThenOpen()
            }
        }

        // Shutter
        btnCapture.setOnClickListener { takePhoto() }
    }

    private fun loadUserSettings(): Boolean {
        loadChatHistory()
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        userEndpoint = prefs.getString("endpoint", AZURE_ENDPOINT) ?: AZURE_ENDPOINT
        userApiKey = prefs.getString("api_key", AZURE_OPENAI_KEY) ?: AZURE_OPENAI_KEY
        userApiVersion = prefs.getString("api_version", API_VERSION) ?: API_VERSION
        userModel = prefs.getString("selected_model", DEPLOYMENT) ?: DEPLOYMENT
        userCloud=prefs.getString("cloud_name",CLOUDINARY_CLOUD)?:CLOUDINARY_CLOUD

        // Update top bar title
        val tvModelTitle = findViewById<TextView>(R.id.tvModelName)
        tvModelTitle.text = userModel

        // Return true if any required field is empty
        return userEndpoint.isBlank() || userApiKey.isBlank() || userModel.isBlank() || userCloud.isBlank()
    }

    // ===== Permissions =====
    private fun requestCameraPermissionThenOpen() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED

        if (granted) {
            openCameraUI()
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQ
            )
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQ) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCameraUI()
            } else {
                Toast.makeText(this, "Camera permission is required", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSettingsDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_settings, null)
        val etEndpoint = dialogView.findViewById<EditText>(R.id.etEndpoint)
        val etApiKey = dialogView.findViewById<EditText>(R.id.etApiKey)
        val etApiVersion = dialogView.findViewById<EditText>(R.id.etApiVersion)
        val spinner = dialogView.findViewById<Spinner>(R.id.spinnerModels)
        val etCustomModel = dialogView.findViewById<EditText>(R.id.etCustomModel)
        val radioGroupGPT = dialogView.findViewById<RadioGroup>(R.id.radioGroupGPT)
        val radioNormalGPT = dialogView.findViewById<RadioButton>(R.id.radioNormalGPT)
        val radioAzureGPT = dialogView.findViewById<RadioButton>(R.id.radioAzureGPT)
        val etCloudName = dialogView.findViewById<EditText>(R.id.etCloudName)


        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)

        // ==== Prefill values ====
        etEndpoint.setText(prefs.getString("endpoint", ""))
        etApiKey.setText(prefs.getString("api_key", ""))
        etApiVersion.setText(prefs.getString("api_version", ""))
        etCloudName.setText(prefs.getString("cloud_name", "")) // Prefill if saved

        val gptMode = prefs.getString("gpt_mode", "normal") // normal or azure
        if (gptMode == "azure") radioAzureGPT.isChecked = true
        else radioNormalGPT.isChecked = true

        val savedModel = prefs.getString("selected_model", "")

        // Models spinner
        val models = prefs.getStringSet(
            "models",
            setOf("gpt-4o","gpt-4.1","o4-mini","gpt-5","gpt-5-chat","o3","Custom")
        )!!.toMutableList()

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter

        // Prefill spinner selection
        val selectedIndex = models.indexOf(savedModel).takeIf { it >= 0 } ?: 0
        spinner.setSelection(selectedIndex)
        etCustomModel.visibility = if (models[selectedIndex] == "Custom") View.VISIBLE else View.GONE

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                etCustomModel.visibility = if (models[position] == "Custom") View.VISIBLE else View.GONE
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        val dialog = AlertDialog.Builder(this)
            .setTitle("Settings")
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.btnSaveSettings).setOnClickListener {
            val endpoint = etEndpoint.text.toString()
            val apiKey = etApiKey.text.toString()
            val apiVersion = etApiVersion.text.toString()
            var selectedModel = spinner.selectedItem.toString()
            val cloudName=etCloudName.text.toString()

            if (selectedModel == "Custom") {
                val customName = etCustomModel.text.toString()
                if (customName.isNotEmpty()) {
                    models.add(customName)
                    selectedModel = customName
                    prefs.edit().putStringSet("models", models.toSet()).apply()
                }
            }

            // Save GPT mode
            val selectedGPT = if (radioAzureGPT.isChecked) "azure" else "normal"

            prefs.edit()
                .putString("endpoint", endpoint)
                .putString("api_key", apiKey)
                .putString("api_version", apiVersion)
                .putString("selected_model", selectedModel)
                .putString("gpt_mode", selectedGPT)
                .putString("cloud_name", cloudName)

                .apply()

            loadUserSettings()
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun pdfToTempFiles(file: File): List<Uri> {
        val uris = mutableListOf<Uri>()
        val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        val scale = 2f

        for (i in 0 until renderer.pageCount) {
            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt(),
                (page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888
            )

            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // Save bitmap as temp PNG file
            val tempFile = File(cacheDir, "pdf_${System.currentTimeMillis()}_$i.png")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            uris.add(FileProvider.getUriForFile(this, "$packageName.fileprovider", tempFile))
        }

        renderer.close()
        return uris
    }

    // ===== Camera UI control =====
    private fun openCameraUI() {
        cameraContainer.isVisible = true
        startCamera()
    }

    private fun closeCameraUI(afterCapture: Boolean = false) {
        cameraContainer.isVisible = false
        // Agar capture ke turant baad band kar rahe ho, thoda delay safe hai
        val delayMs = if (afterCapture) 150L else 0L
        cameraContainer.postDelayed({ stopCamera() }, delayMs)
    }

    // ===== CameraX lifecycle =====
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()
            imageCapture = capture

            val selector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, capture)
            } catch (exc: Exception) {
                Toast.makeText(this, "Camera init failed: ${exc.message}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun stopCamera() {
        try {
            cameraProvider?.unbindAll()
        } catch (_: Exception) {
        } finally {
            imageCapture = null
        }
    }

    private fun takePhoto() {
        val capture = imageCapture
        if (capture == null) {
            Toast.makeText(this, "Camera not ready", Toast.LENGTH_SHORT).show()
            return
        }
        if (isCapturing) return
        isCapturing = true

        val photoFile = File(externalCacheDir, "photo_${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    isCapturing = false

                    // Use the original photo file without compression
                    val savedUri = Uri.fromFile(photoFile)

//                    attachedImages.add(savedUri)
                    uploadToCloudinary(savedUri) { url ->
                        if (url != null) {
                            attachedImages.add(Uri.parse(url)) // ✅ Cloudinary ka HTTPS URL
                            attachedImagesP.add(Uri.parse(savedUri.toString()))
                            previewAdapter.notifyItemInserted(attachedImages.size - 1)
                            updateAttachmentsVisibility()
                            updateSendEnabled()
                        } else {
                            Toast.makeText(this@MainActivity, "Image upload failed", Toast.LENGTH_SHORT).show()
                        }
                    }
//                    previewAdapter.notifyItemInserted(attachedImages.size - 1)
//                    updateAttachmentsVisibility()
//                    updateSendEnabled()

                    closeCameraUI(afterCapture = true)
                }



                override fun onError(exc: ImageCaptureException) {
                    isCapturing = false
                    Toast.makeText(
                        this@MainActivity,
                        "Capture failed: ${exc.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }

    // ===== Chat helpers =====
    private fun updateAttachmentsVisibility() {
        attachmentsRecycler.isVisible = attachedImages.isNotEmpty()
    }

    private fun updateSendEnabled() {
//        btnSend.isEnabled = etMessage.text?.isNotBlank() == true || attachedImages.isNotEmpty()
        btnSend.isEnabled = true
    }

    private fun addSystemMessageOnce(systemText: String) {
        if (apiMessagesForModel.isEmpty() ||
            apiMessagesForModel.first().optString("role") != "system"
        ) {
            apiMessagesForModel.add(
                JSONObject().apply {
                    put("role", "system")
                    put("content", systemText)
                }
            )
        }
    }

    private fun buildUserMessageForApi(text: String, imageUris: List<Uri>): JSONObject {
        val contentArray = JSONArray()

        if (text.isNotBlank()) {
            contentArray.put(JSONObject().apply {
                put("type", "text")
                put("text", text)
            })
        }

        imageUris.forEach { uri ->
            val url = uri.toString()   // <-- yaha pe Cloudinary URL hi hona chahiye
            contentArray.put(JSONObject().apply {
                put("type", "image_url")
                put("image_url", JSONObject().apply { put("url", url) })
            })
        }

        return JSONObject().apply {
            put("role", "user")
            put("content", contentArray)
        }
    }

    private fun uriToDataUrl(uri: Uri): String? {
        return try {
            val mime = contentResolver.getType(uri) ?: "image/jpeg"
            contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                "data:$mime;base64,$b64"
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun stopStreamingUI() {
        if (streamingMessageIndex != -1) {
            // Streaming ko finalize karo
            val finalText = messages[streamingMessageIndex].text
            // Replace last assistant message in apiMessagesForModel
            val lastAssistantIndex = apiMessagesForModel.indexOfLast { it.optString("role") == "assistant" }
            if (lastAssistantIndex != -1) {
                apiMessagesForModel.removeAt(lastAssistantIndex)
            }
            apiMessagesForModel.add(JSONObject().apply {
                put("role", "assistant")
                put("content", finalText)
            })
            streamingMessageIndex = -1
        }

        isStreaming = false
        currentCall?.cancel() // CANCEL the network call
        currentCall = null
        btnSend.setImageResource(android.R.drawable.ic_menu_send) // revert to send icon
    }

    private fun updateStreamingMessage(text: String) {
        val shouldScroll = chatRecyclerView.isAtBottom()

        if (streamingMessageIndex == -1) {
            val msg = ChatMessage(text, emptyList(), false)
            messages.add(msg)
            streamingMessageIndex = messages.size - 1
            chatAdapter.notifyItemInserted(streamingMessageIndex)
        } else {
            messages[streamingMessageIndex].text = text
            chatAdapter.notifyItemChanged(streamingMessageIndex)
        }

        if (shouldScroll) {
            chatRecyclerView.scrollToPosition(messages.size - 1)
        }

        saveChatHistory()
    }

    private fun RecyclerView.isAtBottom(): Boolean {
        val layoutManager = layoutManager as? LinearLayoutManager ?: return true
        val lastVisible = layoutManager.findLastCompletelyVisibleItemPosition()
        val lastItem = adapter?.itemCount?.minus(1) ?: 0
        return lastVisible >= lastItem
    }

    private fun finalizeStreamingMessage(finalText: String) {
        if (streamingMessageIndex != -1) {
            messages[streamingMessageIndex].text = finalText
            chatAdapter.notifyItemChanged(streamingMessageIndex)
            streamingMessageIndex = -1
            apiMessagesForModel.add(JSONObject().apply {
                put("role", "assistant")
                put("content", finalText)
            })

            if (chatRecyclerView.isAtBottom()) {
                chatRecyclerView.scrollToPosition(messages.size - 1)
            }

            stopStreamingUI()
        }
    }

    private fun callAzureOpenAI() {
        val url = "$userEndpoint/openai/deployments/$userModel/chat/completions?api-version=$userApiVersion"

        Log.d("Json",apiMessagesForModel.toString())
        val payload = JSONObject().apply {
            put("messages", JSONArray().apply { apiMessagesForModel.forEach { put(it) } })
//            put("temperature", 0.7)
            put("stream", true)
        }

        val body = payload.toString().toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("api-key", userApiKey)
            .post(body)
            .build()

        // Set streaming state
        isStreaming = true
        btnSend.setImageResource(android.R.drawable.ic_media_pause)

        currentCall = client.newCall(request)
        currentCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    val aiMsg = ChatMessage("Error: ${e}", emptyList(), false)
                    messages.add(aiMsg)
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    chatRecyclerView.scrollToPosition(messages.size - 1)
                    stopStreamingUI()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onFailure(call, IOException("HTTP ${it.code}"))
                        return
                    }
                    val reader = it.body?.charStream() ?: return
                    val sb = StringBuilder()

                    try {
                        reader.forEachLine { line ->
                            if (!isStreaming) throw IOException("Streaming paused") // <--- stop immediately
                            if (line.startsWith("data: ")) {
                                val jsonStr = line.removePrefix("data: ").trim()
                                if (jsonStr == "[DONE]") return@forEachLine
                                try {
                                    val obj = JSONObject(jsonStr)
                                    val delta = obj.optJSONArray("choices")
                                        ?.getJSONObject(0)?.optJSONObject("delta")
                                    val content = delta?.optString("content")
                                    if (!content.isNullOrEmpty()) {
                                        sb.append(content)
                                        runOnUiThread { updateStreamingMessage(sb.toString()) }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {
                        // Stream interrupted (paused) — exit safely
                    }

                    // Only finalize if still streaming (user didn't pause)
                    runOnUiThread {
                        if (isStreaming) finalizeStreamingMessage(sb.toString())
                    }
                }
            }
        })
    }

    private fun callGithubOpenAI() {
        val url = userEndpoint

        val payload = JSONObject().apply {
            put("model", userModel) // Or "openai/gpt-5"
            put("messages", JSONArray().apply {
                apiMessagesForModel.forEach { put(it) }
            })
//            put("temperature", 0.7)
            put("stream", true)
        }

        val body = payload.toString()
            .toRequestBody("application/json".toMediaTypeOrNull())

        val request = Request.Builder()
            .url(url)
            .addHeader("Content-Type", "application/json")
            .addHeader("Authorization", "Bearer $userApiKey") // GITHUB_TOKEN from env
            .post(body)
            .build()

        isStreaming = true
        btnSend.setImageResource(android.R.drawable.ic_media_pause)

        currentCall = client.newCall(request)
        currentCall?.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    val aiMsg = ChatMessage("Error: ${e.message}", emptyList(), false)
                    messages.add(aiMsg)
                    chatAdapter.notifyItemInserted(messages.size - 1)
                    chatRecyclerView.scrollToPosition(messages.size - 1)
                    stopStreamingUI()
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!it.isSuccessful) {
                        onFailure(call, IOException("HTTP ${it.code}"))
                        return
                    }

                    val reader = it.body?.charStream() ?: return
                    val bufferedReader = reader.buffered()
                    val sb = StringBuilder()

                    try {
                        while (true) {
                            if (!isStreaming) break
                            val line = bufferedReader.readLine() ?: break
                            if (line.startsWith("data: ")) {
                                val jsonStr = line.removePrefix("data: ").trim()
                                if (jsonStr == "[DONE]") break
                                try {
                                    val obj = JSONObject(jsonStr)
                                    val delta = obj.optJSONArray("choices")
                                        ?.optJSONObject(0)
                                        ?.optJSONObject("delta")
                                    val content = delta?.optString("content")
                                    if (!content.isNullOrEmpty()) {
                                        sb.append(content)
                                        runOnUiThread {
                                            updateStreamingMessage(sb.toString())
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    } catch (_: Exception) {
                        // Stream interrupted or paused
                    }

                    runOnUiThread {
                        if (isStreaming) {
                            finalizeStreamingMessage(sb.toString())
                        }
                    }
                }
            }
        })
    }

    // ===== Back press =====
    override fun onBackPressed() {
        if (cameraContainer.isVisible) {
            closeCameraUI()
        } else {
            super.onBackPressed()
        }
    }

    // ===== Lifecycle cleanup =====
    override fun onPause() {
        super.onPause()
        // Agar UI hidden nahi hai to bhi pause pe camera free kar do
        if (!isCapturing) stopCamera()
    }

    // ===== Chat persistence =====
    private fun saveChatHistory() {
        try {
            val prefs = getSharedPreferences(CHAT_PREFS, MODE_PRIVATE)
            val messagesJson = JSONArray()
            messages.forEach { msg ->
                val obj = JSONObject().apply {
                    put("text", msg.text)
                    put("isUser", msg.isUser)
                    val imagesArr = JSONArray()
                    msg.images.forEach { uri -> imagesArr.put(uri.toString()) }
                    put("images", imagesArr)
                }
                messagesJson.put(obj)
            }
            prefs.edit().putString(KEY_MESSAGES_JSON, messagesJson.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun loadChatHistory() {
        val prefs = getSharedPreferences(CHAT_PREFS, MODE_PRIVATE)
        val messagesStr = prefs.getString(KEY_MESSAGES_JSON, null)
        messages.clear()
        if (!messagesStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(messagesStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val text = obj.optString("text", "")
                    val isUser = obj.optBoolean("isUser", true)
                    val imagesArr = obj.optJSONArray("images") ?: JSONArray()
                    val uris = mutableListOf<Uri>()
                    for (j in 0 until imagesArr.length()) {
                        val s = imagesArr.optString(j)
                        try { uris.add(Uri.parse(s)) } catch (_: Exception) {}
                    }
                    messages.add(ChatMessage(text, uris, isUser))
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
        chatAdapter.notifyDataSetChanged()
        if (messages.isNotEmpty()) chatRecyclerView.scrollToPosition(messages.size - 1)
        updateAttachmentsVisibility()
        updateSendEnabled()
        rebuildApiMessagesFromChat()

        updateEmptyState()
    }

    private fun rebuildApiMessagesFromChat() {
        apiMessagesForModel.clear()
        addSystemMessageOnce(DEFAULT_SYSTEM_PROMPT)

        messages.forEach { msg ->
            if (msg.isUser) {
                apiMessagesForModel.add(buildUserMessageForApi(msg.text.toString(), msg.images))
            } else {
                if (!msg.text?.startsWith("Error", true)!! &&
                    !msg.text?.startsWith("Parsing error", true)!!
                ) {
                    apiMessagesForModel.add(JSONObject().apply {
                        put("role", "assistant")
                        put("content", msg.text)
                    })
                }
            }
        }
    }

    // ===== Clear Chat =====
    private fun showClearConfirmDialog() {
        AlertDialog.Builder(this)
            .setTitle("Clear chat?")
            .setMessage("This will delete all messages. Do you want to continue?")
            .setPositiveButton("Yes") { _, _ -> clearChat() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun clearChat() {
        messages.clear()
        chatAdapter.notifyDataSetChanged()
        chatRecyclerView.scrollToPosition(0)

        apiMessagesForModel.clear()
        attachedImages.clear()
        previewAdapter.notifyDataSetChanged()
        updateAttachmentsVisibility()
        updateSendEnabled()

        getSharedPreferences(CHAT_PREFS, MODE_PRIVATE).edit().remove(KEY_MESSAGES_JSON).apply()
        addSystemMessageOnce(DEFAULT_SYSTEM_PROMPT)

        updateEmptyState()

        Toast.makeText(this, "Chat cleared", Toast.LENGTH_SHORT).show()

    }

    // ===== Save on app background =====
    override fun onStop() {
        super.onStop()
        saveChatHistory()
    }

    // ===== Cloudinary =====
    private fun uploadToCloudinary(uri: Uri, onResult: (String?) -> Unit) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val file = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out -> inputStream?.copyTo(out) }

            if (file.length() > 9 * 1024 * 1024) {
                Toast.makeText(this, "Image size > 9MB, not allowed!", Toast.LENGTH_LONG).show()
                onResult(null)
                return
            }

            val requestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", file.name, file.asRequestBody("image/*".toMediaTypeOrNull()))
                .addFormDataPart("upload_preset", "ml_default") // 👈 tera preset
                .build()

            val request = Request.Builder()
                .url("https://api.cloudinary.com/v1_1/${userCloud}/image/upload") // 👈 tera cloud name
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    Log.e("CloudinaryUpload", "Upload failed", e)
                    runOnUiThread { onResult(null) }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use {
                        if (!it.isSuccessful) {
                            Log.e("CloudinaryUpload", "Response error: ${it.code} ${it.message}")
                            runOnUiThread { onResult(null) }
                            return
                        }
                        val json = JSONObject(it.body?.string() ?: "")
                        val url = json.optString("secure_url", null)
                        Log.d("CloudinaryUpload", "Uploaded URL: $url")
                        runOnUiThread { onResult(url) }
                    }
                }
            })
        } catch (e: Exception) {
            Log.e("CloudinaryUpload", "Exception", e)
            onResult(null)
        }
    }

    private fun pdfToCloudinaryLinks(file: File, onDone: (List<String>) -> Unit) {
        val renderer = PdfRenderer(ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY))
        val urls = mutableListOf<String>()
        val scale = 2f

        fun processPage(i: Int) {
            if (i >= renderer.pageCount) {
                renderer.close()
                onDone(urls)
                return
            }

            val page = renderer.openPage(i)
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).toInt(),
                (page.height * scale).toInt(),
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bitmap)
            canvas.drawColor(android.graphics.Color.WHITE)
            page.render(bitmap, null, Matrix().apply { setScale(scale, scale) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            page.close()

            // Save to temp file
            val tempFile = File(cacheDir, "pdf_${System.currentTimeMillis()}_$i.png")
            FileOutputStream(tempFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
            }

            // Upload to Cloudinary
            uploadToCloudinary(Uri.fromFile(tempFile)) { url ->
                if (url != null) urls.add(url)
                processPage(i + 1) // recursion for next page
            }
        }

        processPage(0)
    }

    private fun updateEmptyState() {
        if (chatAdapter.itemCount == 0) {
            emptyState.visibility = View.VISIBLE
            chatRecyclerView.visibility = View.GONE
        } else {
            emptyState.visibility = View.GONE
            chatRecyclerView.visibility = View.VISIBLE
        }
    }


}
