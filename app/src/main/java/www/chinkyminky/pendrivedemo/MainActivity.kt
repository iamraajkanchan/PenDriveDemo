package www.chinkyminky.pendrivedemo

import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    private var selectedImageUri: Uri? = null
    private var usbDirectoryUri: Uri? = null

    private lateinit var progressBar: ProgressBar

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            Toast.makeText(this, "Image Selected!", Toast.LENGTH_SHORT).show()
        }
    }

    private val pickUsbFolderLauncher = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            usbDirectoryUri = uri
            contentResolver.takePersistableUriPermission(uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            Toast.makeText(this, "Pen Drive Selected!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        val btnSelectImage = findViewById<Button>(R.id.btnSelectImage)
        val btnSelectUsb = findViewById<Button>(R.id.btnSelectUsb)
        val btnCopy = findViewById<Button>(R.id.btnCopy)
        progressBar = findViewById(R.id.progressBar)

        // Set click listeners
        btnSelectImage.setOnClickListener { pickImageLauncher.launch("image/*") }
        btnSelectUsb.setOnClickListener { pickUsbFolderLauncher.launch(null) }

        btnCopy.setOnClickListener {
            if (selectedImageUri != null && usbDirectoryUri != null) {
                copyFileWithProgress(selectedImageUri!!, usbDirectoryUri!!)
            } else {
                Toast.makeText(this, "Please select both image and pen drive first.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun copyFileWithProgress(sourceUri: Uri, destinationDirUri: Uri) {
        val usbDir = DocumentFile.fromTreeUri(this, destinationDirUri)
        if (usbDir == null || !usbDir.canWrite()) {
            Toast.makeText(this, "Cannot write to the selected USB folder.", Toast.LENGTH_SHORT).show()
            return
        }

        val newFile = usbDir.createFile("image/jpeg", "copied_image_${System.currentTimeMillis()}.jpg")
        if (newFile == null) {
            Toast.makeText(this, "Failed to create file on USB.", Toast.LENGTH_SHORT).show()
            return
        }

        val sourceFile = DocumentFile.fromSingleUri(this, sourceUri)
        val fileSize = sourceFile?.length() ?: 0L
        progressBar.progress = 0

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream? = contentResolver.openInputStream(sourceUri)
                val outputStream: OutputStream? = contentResolver.openOutputStream(newFile.uri)

                if (inputStream != null && outputStream != null) {
                    val buffer = ByteArray(8192) // 8KB chunks
                    var bytesCopied = 0L
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                        bytesCopied += bytesRead

                        if (fileSize > 0) {
                            val progress = ((bytesCopied * 100) / fileSize).toInt()
                            withContext(Dispatchers.Main) {
                                progressBar.progress = progress
                            }
                        }
                    }

                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@MainActivity, "Copy Successful!", Toast.LENGTH_SHORT).show()
                        progressBar.progress = 100
                    }
                }

                inputStream?.close()
                outputStream?.close()

            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@MainActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}