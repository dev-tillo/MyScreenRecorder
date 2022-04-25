package uz.devtillo.myscreenrecorder

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.util.DisplayMetrics
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import android.widget.ToggleButton
import android.widget.VideoView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.hbisoft.hbrecorder.HBRecorderListener
import uz.devtillo.myscreenrecorder.databinding.ActivityMainBinding
import java.io.File
import java.util.jar.Manifest
import android.content.ContentResolver
import android.content.ContentValues
import android.widget.Button
import com.hbisoft.hbrecorder.HBRecorder
import android.os.Build
import androidx.annotation.RequiresApi
import android.graphics.Bitmap

import android.graphics.BitmapFactory

import androidx.annotation.DrawableRes

import android.media.MediaScannerConnection
import android.media.MediaScannerConnection.OnScanCompletedListener

import android.provider.MediaStore
import android.util.Log
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : AppCompatActivity(), HBRecorderListener {

    private val SCREEN_RECORD_REQUEST_CODE = 100
    private val PERMISSION_REQ_ID_RECORD_AUDIO = 101
    private val PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE = 102
    lateinit var hbRecorder: HBRecorder
    lateinit var btnStart: Button
    lateinit var btnStop:Button
    var hasPermissions = false
    lateinit var contentValues: ContentValues
    lateinit var resolver: ContentResolver
    lateinit var mUri: Uri

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        hbRecorder = HBRecorder(this, this)
        btnStart = findViewById(R.id.btnStart)
        btnStop = findViewById(R.id.btnStop)
        hbRecorder!!.setVideoEncoder("H264")

        btnStart.setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                if (checkSelfPermission(
                        android.Manifest.permission.RECORD_AUDIO,
                        PERMISSION_REQ_ID_RECORD_AUDIO
                    ) && checkSelfPermission(
                        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        PERMISSION_REQ_ID_WRITE_EXTERNAL_STORAGE
                    )
                ) {
                    hasPermissions = true
                }
                if (hasPermissions) {
                    startRecordingScreen()
                }
            }
        }

        btnStop.setOnClickListener{
            hbRecorder.stopScreenRecording()
        }

   }

    override fun HBRecorderOnStart() {
        Toast.makeText(this, "Started", Toast.LENGTH_SHORT).show();
    }

    override fun HBRecorderOnComplete() {
        Toast.makeText(this, "Completed", Toast.LENGTH_SHORT).show();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            //Update gallery depending on SDK Level
            if (hbRecorder.wasUriSet()) {
                updateGalleryUri();
            }else{
                refreshGalleryFile();
            }
        }    }

    override fun HBRecorderOnError(errorCode: Int, reason: String?) {
        Toast.makeText(this, "$errorCode $reason ", Toast.LENGTH_SHORT).show();
    }
    private fun startRecordingScreen() {
        val mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val permissionIntent = mediaProjectionManager?.createScreenCaptureIntent()
        startActivityForResult(permissionIntent, SCREEN_RECORD_REQUEST_CODE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_RECORD_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                //Start screen recording
                hbRecorder.startScreenRecording(data, resultCode, this)
            }
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun setOutputPath() {
        val filename = generateFileName()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver = contentResolver
            contentValues = ContentValues()
            contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "SpeedTest/" + "SpeedTest")
            contentValues.put(MediaStore.Video.Media.TITLE, filename)
            contentValues.put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
            contentValues.put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            mUri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)!!
            //FILE NAME SHOULD BE THE SAME
            hbRecorder.fileName = filename
            hbRecorder.setOutputUri(mUri)
        } else {
            createFolder()
            hbRecorder.setOutputPath(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    .toString() + "/HBRecorder"
            )
        }
    }

    //Check if permissions was granted
    private fun checkSelfPermission(permission: String, requestCode: Int): Boolean {
        if (ContextCompat.checkSelfPermission(
                this,
                permission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(this, arrayOf(permission), requestCode)
            return false
        }
        return true
    }

    private fun updateGalleryUri() {
        contentValues.clear()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
        }
        contentResolver.update(mUri, contentValues, null, null)
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private fun refreshGalleryFile() {
        MediaScannerConnection.scanFile(
            this, arrayOf(hbRecorder.filePath), null
        ) { path, uri ->
            Log.i("ExternalStorage", "Scanned $path:")
            Log.i("ExternalStorage", "-> uri=$uri")
        }
    }

    //Generate a timestamp to be used as a file name
    private fun generateFileName(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.getDefault())
        val curDate = Date(System.currentTimeMillis())
        return formatter.format(curDate).replace(" ", "")
    }

    //drawable to byte[]
    private fun drawable2ByteArray(@DrawableRes drawableId: Int): ByteArray? {
        val icon = BitmapFactory.decodeResource(resources, drawableId)
        val stream = ByteArrayOutputStream()
        icon.compress(Bitmap.CompressFormat.PNG, 100, stream)
        return stream.toByteArray()
    }

    //Create Folder
    //Only call this on Android 9 and lower (getExternalStoragePublicDirectory is deprecated)
    //This can still be used on Android 10> but you will have to add android:requestLegacyExternalStorage="true" in your Manifest
    private fun createFolder() {
        val f1 = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "SpeedTest"
        )
        if (!f1.exists()) {
            if (f1.mkdirs()) {
                Log.i("Folder ", "created")
            }
        }
    }
}