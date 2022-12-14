package dev.epegasus.baseproject.helper.managers

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import dev.epegasus.baseproject.ui.activities.MainActivity
import java.lang.ref.WeakReference

@SuppressLint("InlinedApi")
class PermissionManager(activity: WeakReference<MainActivity>, private val context: Context) {

    // User Side Attributes
    private var permission: String? = null
    private var permissionList = ArrayList<String>()
    private var rationaleMessage = DEFAULT_RATIONALE_MESSAGE
    private var rationaleDialog: AlertDialog? = null
    private var permissionCheck: PermissionCheck? = null

    // PermissionManager Attributes
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var editor: SharedPreferences.Editor
    private val builtInPermissionList = ArrayList<String>()
    private var isManageExternalStorage = false
    private var isLooping = false

    private var resultPermissionLauncher = activity.get()?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            checkMultiplePermissions()
        }
    }

    private var requestPermissionLauncher = activity.get()?.registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            permissionCheck?.onPermissionGranted()
        } else {
            permissionCheck?.onPermissionDenied()
        }
    } as ActivityResultLauncher<String>

    private val requestMultiplePermissions = activity.get()?.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach lit@{
            Log.d("MyTag", "${it.key} = ${it.value}")
            if (!it.value) {
                if (it.key == MANAGE_EXTERNAL_STORAGE) {
                    return@lit
                }
                permissionCheck?.onPermissionDenied()
                return@registerForActivityResult
            }
        }
        permissionCheck?.onPermissionGranted()
    }

    private var resultSettingLauncher = activity.get()?.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { _ ->
        permission?.let { requestPermissionLauncher.launch(it) } ?: kotlin.run {
            if (permissionList.isNotEmpty()) {
                requestMultiplePermissions?.launch(permissionList.toTypedArray())
            }
        }
    }

    companion object {
        fun from(activity: MainActivity) = PermissionManager(WeakReference(activity), activity)

        // Camera & Audio Permission
        const val CAMERA = Manifest.permission.CAMERA
        const val RECORD_AUDIO = Manifest.permission.RECORD_AUDIO

        // Location Permissions
        const val ACCESS_FINE_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION
        const val ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION

        // Storage Permissions
        const val MANAGE_EXTERNAL_STORAGE = Manifest.permission.MANAGE_EXTERNAL_STORAGE
        const val READ_EXTERNAL_STORAGE = Manifest.permission.READ_EXTERNAL_STORAGE
        const val WRITE_EXTERNAL_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE


        const val DEFAULT_RATIONALE_MESSAGE = "By denying this feature, you will not be able to use its feature"
        const val DEFAULT_SETTING_MESSAGE = "Allow permission from 'Setting' to proceed"
    }

    init {
        builtInPermissionList.add(CAMERA)
        builtInPermissionList.add(RECORD_AUDIO)
        builtInPermissionList.add(ACCESS_FINE_LOCATION)
        builtInPermissionList.add(ACCESS_COARSE_LOCATION)
        builtInPermissionList.add(MANAGE_EXTERNAL_STORAGE)
        builtInPermissionList.add(READ_EXTERNAL_STORAGE)
        builtInPermissionList.add(WRITE_EXTERNAL_STORAGE)
    }

    /**
     * TODO
     * @param permission : Pass Manifest Permission Type
     * @return
     */

    fun request(permission: String): PermissionManager {
        this.permission = permission
        return this
    }

    fun requestMultiple(permissionList: ArrayList<String>): PermissionManager {
        this.permissionList = permissionList
        return this
    }

    /**
     * TODO (Optional)
     * @param rationaleMessage : Message to be displayed in Built-in MaterialAlertDialog
     * @return
     */

    fun setRationale(rationaleMessage: String = DEFAULT_RATIONALE_MESSAGE): PermissionManager {
        this.rationaleMessage = rationaleMessage
        return this
    }

    /**
     * TODO (Optional)
     * @param dialog : Pass your custom dialog to be displayed if you do not want default dialog (Type: AlertDialog)
     *                  Handle it's click listener on your side.
     * @return
     */

    fun setRationaleDialog(dialog: AlertDialog?): PermissionManager {
        rationaleDialog = dialog
        return this
    }

    /**
     * TODO
     * @param permissionCheck : Interface returns result either permission granted or denied
     */

    fun hasPermission(permissionCheck: PermissionCheck) {
        this.permissionCheck = permissionCheck
        // Single Permission
        if (permissionList.isEmpty()) {
            permission?.let {
                if (it in builtInPermissionList) {
                    requirePermission(it)
                } else
                    throw IllegalArgumentException("Unknown permission: $it")
            } ?: throw IllegalArgumentException("Permission Type Missing {call request() method for any permission}")
        } else {
            isLooping = false
            if (builtInPermissionList.containsAll(permissionList.toList())) {
                checkMultiplePermissions()
            } else
                throw IllegalArgumentException("Unknown permission: in List: $permissionList")
        }
    }

    /**
     * TODO
     * SharedPreference is "isFirstTime"
     * @param permissionName
     */

    private fun requirePermission(permissionName: String) {
        sharedPreferences = context.getSharedPreferences("permission_preferences", Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()

        if (ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED) {
            permissionCheck?.onPermissionGranted()
        } else {
            if (shouldShowRequestPermissionRationale(context as Activity, permissionName))
                rationaleDialog?.show() ?: showPermissionDialog(permissionName, true)
            else {
                if (sharedPreferences.getBoolean(permissionName, true)) {
                    editor.putBoolean(permissionName, false)
                    editor.apply()
                    requestPermission(permissionName)
                } else {
                    showSettingDialog()
                }
            }
        }
    }

    private fun showPermissionDialog(permissionName: String, isSingle: Boolean) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Permission required")
            .setMessage(rationaleMessage)
            .setCancelable(false)
            .setPositiveButton("Enable") { dialogInterface, _ ->
                dialogInterface.dismiss()
                if (isSingle)
                    requestPermission(permissionName)
                else
                    requestMultiplePermissions?.launch(permissionList.toTypedArray())
            }
            .setNegativeButton("Cancel") { dialogInterface, _ ->
                dialogInterface.dismiss()
                permissionCheck?.onPermissionDenied()
            }
            .show()
    }

    private fun showSettingDialog() {
        if (!(context as Activity).isFinishing)
            MaterialAlertDialogBuilder(context)
                .setTitle("Permission required")
                .setMessage(DEFAULT_SETTING_MESSAGE)
                .setCancelable(false)
                .setPositiveButton("Setting") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                    openSettingPage()
                }
                .setNegativeButton("Cancel") { dialogInterface, _ ->
                    dialogInterface.dismiss()
                    permissionCheck?.onPermissionDenied()
                }
                .show()
    }

    private fun requestPermission(permissionName: String) {
        requestPermissionLauncher.launch(permissionName)
    }

    private fun openSettingPage() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri: Uri = Uri.fromParts("package", context.packageName, null)
        intent.data = uri
        resultSettingLauncher?.launch(intent)
    }

    private fun checkMultiplePermissions() {
        sharedPreferences = context.getSharedPreferences("permission_preferences", Context.MODE_PRIVATE)
        editor = sharedPreferences.edit()

        val removeList = ArrayList<String>()
        for (permission in permissionList) {
            if (permission == MANAGE_EXTERNAL_STORAGE) {
                isManageExternalStorage = true
            }
        }

        if (isManageExternalStorage) {
            for (permission in permissionList) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    if (permission == MANAGE_EXTERNAL_STORAGE) {
                        removeList.add(permission)
                    }
                } else {
                    if (permission == READ_EXTERNAL_STORAGE || permission == WRITE_EXTERNAL_STORAGE) {
                        removeList.add(permission)
                    }
                }
            }
        }

        permissionList.removeAll(removeList.toSet())

        for (permission in permissionList) {
            if (permission == MANAGE_EXTERNAL_STORAGE) {
                if (!Environment.isExternalStorageManager()) {
                    if (!isLooping) {
                        isLooping = true
                        requestHigherAndroidSettingPermission()
                    } else
                        permissionCheck?.onPermissionDenied()
                    return
                }
                continue
            }

            if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                if (shouldShowRequestPermissionRationale(context as Activity, permission)) {
                    rationaleDialog?.show() ?: showPermissionDialog(permission, false)
                    return
                } else {
                    if (sharedPreferences.getBoolean(permission, true)) {
                        editor.putBoolean(permission, false)
                        editor.apply()
                        requestMultiplePermissions?.launch(permissionList.toTypedArray())
                        return
                    } else {
                        showSettingDialog()
                        return
                    }
                }
            }
        }
        permissionCheck?.onPermissionGranted()
    }

    private fun requestHigherAndroidSettingPermission() {
        try {
            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
            intent.addCategory("android.intent.category.DEFAULT")
            intent.data = Uri.parse(String.format("package:%s", context.packageName))
            resultPermissionLauncher?.launch(intent)
        } catch (ex: Exception) {
            val intent = Intent()
            intent.action = Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
            resultPermissionLauncher?.launch(intent)
        }
    }

    fun checkPermission(permissionName: String, permissionCheck: PermissionCheck) {
        if (ContextCompat.checkSelfPermission(context, permissionName) == PackageManager.PERMISSION_GRANTED)
            permissionCheck.onPermissionGranted()
        else
            permissionCheck.onPermissionDenied()
    }

    /* ----------------------------------------- Interfaces ----------------------------------------- */

    /**
     * TODO
     *  onPermissionGranted() : User has granted access, now you can perform your operation (e.g. opening gallery)
     *  onPermissionDenied() : User has denied access, call "askPermissionMethod()" in this case for user to ask permission
     */
    interface PermissionCheck {
        fun onPermissionGranted()
        fun onPermissionDenied()
    }
}