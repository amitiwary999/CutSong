package com.example.meeera.cutsong.Activity

import android.Manifest
import android.content.DialogInterface
import android.os.Bundle
import android.os.Handler
import com.airbnb.lottie.LottieAnimationView
import com.example.meeera.cutsong.R
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


/**
 * Created by meeera on 28/10/17.
 */
class SplashScreen : AppCompatActivity() {

    private var lottieAnim : LottieAnimationView ?= null
    val REQUEST_ID_MULTIPLE_PERMISSIONS = 1
    private val SPLASH_TIME_OUT = 3000L
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        lottieAnim = findViewById(R.id.lottieanim) as LottieAnimationView
        lottieAnim?.setAnimation("gears.json")
        lottieAnim?.loop(true)
        lottieAnim?.playAnimation()
        Handler().postDelayed({
            checkAndRequestPermission()
        }, SPLASH_TIME_OUT)

    }

    private fun checkAndRequestPermission() {
        if(Build.VERSION.SDK_INT>= Build.VERSION_CODES.M) {
            var permissionReadEStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            var permissionWriteEStorage = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            var permissionReader = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            var listPermissionNeeded : ArrayList<String> = ArrayList<String>()

            if(permissionReadEStorage != PackageManager.PERMISSION_GRANTED) {
                listPermissionNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }

            if(permissionWriteEStorage != PackageManager.PERMISSION_GRANTED) {
                listPermissionNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }

            if(permissionReader != PackageManager.PERMISSION_GRANTED) {
                listPermissionNeeded.add(Manifest.permission.RECORD_AUDIO)
            }
            if(!listPermissionNeeded.isEmpty()){
                ActivityCompat.requestPermissions(this, listPermissionNeeded.toArray(arrayOfNulls(listPermissionNeeded.size)), REQUEST_ID_MULTIPLE_PERMISSIONS)
            } else {
                var intent = Intent(this, MainActivity::class.java)
                startActivity(intent)
                lottieAnim?.cancelAnimation()
                finish()
            }
        } else {
            var intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            lottieAnim?.cancelAnimation()
            finish()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_ID_MULTIPLE_PERMISSIONS -> {
                val perms: HashMap<String, Int> = HashMap()
                perms.put(Manifest.permission.READ_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED)
                perms.put(Manifest.permission.WRITE_EXTERNAL_STORAGE, PackageManager.PERMISSION_GRANTED)
                perms.put(Manifest.permission.RECORD_AUDIO, PackageManager.PERMISSION_GRANTED)
                if (grantResults.size > 0) {
                    if (perms.get(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                            perms.get(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED &&
                            perms.get(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                        var intent = Intent(this, MainActivity::class.java)
                        startActivity(intent)
                        lottieAnim?.cancelAnimation()
                        finish()
                    } else {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE) || ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ||
                                ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO)) {
                            showDialogOK(" Read and write external storage and record audio permission needed", DialogInterface.OnClickListener { _, which ->
                                when (which) {
                                    DialogInterface.BUTTON_POSITIVE -> {
                                        checkAndRequestPermission()
                                    }

                                    DialogInterface.BUTTON_NEGATIVE -> {
                                        Toast.makeText(this, "Go to settings and enable permissions", Toast.LENGTH_SHORT).show()
                                        finish()
                                    }
                                }
                            })
                        } else {
                            Toast.makeText(this, "Go to settings and enable permissions", Toast.LENGTH_SHORT).show()
                            finish()
                        }
                    }
                }
            }
        }
    }

    fun showDialogOK(message : String, okListener : DialogInterface.OnClickListener) {
        AlertDialog.Builder(this)
                .setMessage(message)
                .setPositiveButton("OK", okListener)
                .setNegativeButton("Cancel", okListener)
                .create()
                .show()
    }

}