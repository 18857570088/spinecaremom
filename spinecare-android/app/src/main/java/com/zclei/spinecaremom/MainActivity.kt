package com.zclei.spinecaremom

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.SpannableString
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.zclei.spinecaremom.bluetooth.SpineBraceBluetoothCallback
import com.zclei.spinecaremom.bluetooth.SpineBraceBluetoothManager
import com.zclei.spinecaremom.bluetooth.SpineBraceDevice
import com.zclei.spinecaremom.bluetooth.SpineBraceHistorySnapshot
import com.zclei.spinecaremom.bluetooth.SpineBraceTelemetry
import com.zclei.spinecaremom.bluetooth.SpineBraceVersion
import com.zclei.spinecaremom.bluetooth.SpineBraceWearPoint
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.min
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {
    private enum class Stage {
        Login,
        Device,
        Profile,
        App,
    }

    private enum class MainTab {
        Home,
        Reports,
        Consult,
        Logs,
        Me,
        ProfileEdit,
        Alerts,
        Settings,
        BluetoothValidation,
        ChildMode,
        DataExport,
        DataDelete,
        HelpCenter,
    }

    private enum class HelpSection {
        Home,
        Manual,
        Faq,
        Agreement,
        Privacy,
        Contact,
    }

    private data class ChildProfile(
        var id: String = "demo-child",
        var nickname: String = "朵朵",
        var age: Int = 12,
        var gender: String = "女",
        var birthDate: String = "2014-03-18",
        var curveType: String = "胸腰弯",
        var cobb: Int = 25,
        var risser: String = "2",
        var prescribedHours: Int = 20,
        var braceType: String = "硬支具",
        var firstVisitDate: String = "2025-10-20",
    )

    private data class AiReply(
        val summary: String,
        val analysis: String,
        val advice: List<String>,
        val needDoctor: Boolean,
        val doctorReason: String,
        val category: String,
    )

    private data class AiReportItem(
        val title: String,
        val subtitle: String,
        val status: String,
        val color: Int,
    )

    private data class ArchivedReport(
        val id: String,
        val kind: String,
        val periodStart: LocalDate?,
        val periodEnd: LocalDate?,
        val payload: JSONObject,
        val pdfUrl: String?,
        val createdAt: String,
    )

    private data class AlertItem(
        val id: String,
        val type: String,
        val level: String,
        val title: String,
        val summary: String,
        val triggerDetail: String,
        val status: String,
        val createdAt: String,
    )

    private data class WearSummary(
        val avgHours: Double,
        val prescribedHours: Double,
        val complianceRate: Int,
        val daysCounted: Int,
        val longestStreak: Int,
    )

    private data class WearRecord(
        val date: LocalDate,
        val wornHours: Double,
        val lastReadAt: LocalDateTime? = null,
        val historyHead: Int? = null,
        val historyCount: Int? = null,
        val hourlyRows: List<BluetoothValidationHour> = emptyList(),
    )

    private data class GrowthLog(
        val id: String,
        val date: LocalDate,
        val heightCm: Double,
        val note: String,
    )

    private data class SkinLog(
        val id: String,
        val date: LocalDate,
        val region: String,
        val status: String,
        val note: String,
        val photos: List<String>,
    )

    private data class ImagingLog(
        val id: String,
        val imageType: String,
        val shotDate: LocalDate,
        val fileUrl: String?,
        val note: String,
    )

    private data class BluetoothValidationSample(
        val recordedAt: LocalDateTime,
        val worn: Boolean,
    )

    private data class BluetoothValidationHour(
        val hourStart: LocalDateTime,
        val wornHours: Double,
        val sampleCount: Int = 6,
        val wornCount: Int = (wornHours * 6).roundToInt(),
        val samples: List<BluetoothValidationSample> = emptyList(),
    )

    private data class DeviceWearUploadPackage(
        val childId: String,
        val payload: JSONObject,
        val hourlyRows: List<BluetoothValidationHour>,
        val expectedWearPoints: List<SpineBraceWearPoint>,
        val fetchedAt: LocalDateTime,
        val lastReadAt: LocalDateTime?,
        val historyHead: Int?,
        val historyCount: Int?,
        val dailyCount: Int,
        val totalWornHours: Double,
        val deviceName: String?,
    )

    private data class DeviceWearCloudVerification(
        val verified: Boolean,
        val expectedCount: Int,
        val matchedCount: Int,
        val missingCount: Int,
        val mismatchedCount: Int,
    )

    private data class DeviceWearLocalVerification(
        val verified: Boolean,
        val expectedCount: Int,
        val matchedCount: Int,
        val missingCount: Int,
        val mismatchedCount: Int,
    )

    private sealed class ChatMessage {
        data class User(val text: String) : ChatMessage()
        data class Ai(val reply: AiReply) : ChatMessage()
    }

    private object P {
        const val primary = 0xFF1F6F78.toInt()
        const val primaryDark = 0xFF15545B.toInt()
        const val primaryLight = 0xFFD7EBEE.toInt()
        const val success = 0xFF2E9E5B.toInt()
        const val warning = 0xFFE0A100.toInt()
        const val danger = 0xFFD7453B.toInt()
        const val bg = 0xFFF6F8F8.toInt()
        const val surface = 0xFFFFFFFF.toInt()
        const val surfaceAlt = 0xFFFBFDFD.toInt()
        const val text = 0xFF1A1A1A.toInt()
        const val secondary = 0xFF5A6A6C.toInt()
        const val muted = 0xFF7F8F91.toInt()
        const val line = 0xFFE2E9E9.toInt()
        const val softLine = 0xFFEEF3F3.toInt()
    }

    private var stage = Stage.Login
    private var currentTab = MainTab.Home
    private var profileStep = 1
    private var reportTab = 0
    private var logsTab = 0
    private var helpSection = HelpSection.Home
    private var helpFaqExpandedIndex: Int? = null
    private var consentChecked = false
    private var agreementReadConfirmed = false
    private var loginAgreementExpanded = false
    private var remindersOn = true
    private var skinReminderOn = true
    private var selectedLanguageIndex = 0
    private var loginPhone = "138 0000 2026"
    private var verificationCode = "062614"
    private var loginMethod = "sms"
    private var profileSyncStatusMessage: String? = null
    private var profileCloudLookupInProgress = false
    private var wearCloudLoading = false
    private var wearCloudLoadedChildId: String? = null
    private var wearCloudLastRequestChildId: String? = null
    private var wearCloudStatusMessage: String? = null
    private var wearCloudSummary: WearSummary? = null
    private var wearCloudRecords: List<WearRecord> = emptyList()
    private var visitWearCloudLoading = false
    private var visitWearCloudLoadedChildId: String? = null
    private var visitWearCloudLastRequestChildId: String? = null
    private var visitWearCloudStatusMessage: String? = null
    private var visitWearRecords: List<WearRecord> = emptyList()
    private var reportArchiveLoading = false
    private var reportArchiveLoadedChildId: String? = null
    private var reportArchiveStatusMessage: String? = null
    private var archivedReports: List<ArchivedReport> = emptyList()
    private var selectedArchivedReportId: String? = null
    private var alertsLoading = false
    private var alertsLoadedChildId: String? = null
    private var alertsLastRequestChildId: String? = null
    private var alertsStatusMessage: String? = null
    private var alertItems: List<AlertItem> = emptyList()
    private val handlingAlertIds = mutableSetOf<String>()
    private var alertsBackTarget = MainTab.Home
    private var dataExportInProgress = false
    private var dataExportStatusMessage: String? = null
    private var pendingDataExportJson: String? = null
    private var pendingDataExportFileName: String? = null
    private var lastDataExportSummary: String? = null
    private var dataDeleteLoading = false
    private var dataDeleteInProgress = false
    private var dataDeleteLoadedChildId: String? = null
    private var dataDeleteRequest: JSONObject? = null
    private var dataDeleteStatusMessage: String? = null
    private var dataDeleteBackupChecked = false
    private var dataDeleteIrreversibleChecked = false
    private var dataDeleteCurrentChildChecked = false
    private var dataDeleteNicknameInput = ""
    private var dataDeletePhraseInput = ""
    private var skinRegionInputs = linkedSetOf("左腰部")
    private var skinStatusInputs = linkedSetOf("发红")
    private var skinNoteInput = ""
    private var skinPhotoRefInput = ""
    private var skinCapturedPhoto: Bitmap? = null
    private var skinCloudLoading = false
    private var skinCloudLoadedChildId: String? = null
    private var skinCloudStatusMessage: String? = null
    private var skinLogs: List<SkinLog> = emptyList()
    private var selectedSkinLogId: String? = null
    private val skinPhotoCache = mutableMapOf<String, Bitmap?>()
    private val skinPhotoLoading = mutableSetOf<String>()
    private var pendingCameraAction: (() -> Unit)? = null
    private var growthHeightInput = "154.3"
    private var growthNoteInput = ""
    private var growthCloudLoading = false
    private var growthCloudLoadedChildId: String? = null
    private var growthCloudStatusMessage: String? = null
    private var growthLogs: List<GrowthLog> = emptyList()
    private var imagingTypeInput = "X光"
    private var imagingDateInput = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
    private var imagingFileUrlInput = ""
    private var imagingNoteInput = ""
    private var imagingCapturedPhoto: Bitmap? = null
    private var imagingSelectedPhotoUri: Uri? = null
    private var imagingCloudLoading = false
    private var imagingCloudLoadedChildId: String? = null
    private var imagingCloudStatusMessage: String? = null
    private var imagingLogs: List<ImagingLog> = emptyList()
    private var selectedImagingLogId: String? = null
    private val bluetoothDevices = mutableListOf<SpineBraceDevice>()
    private var selectedBluetoothDevice: SpineBraceDevice? = null
    private var connectedBluetoothDevice: SpineBraceDevice? = null
    private var bluetoothStatusMessage = "未连接设备，请扫描 WM-SP# 脊柱侧弯设备。"
    private var bluetoothHomeWarningMessage: String? = null
    private var autoConnectTargetDevice: SpineBraceDevice? = null
    private var autoConnectInProgress = false
    private var autoConnectTimeoutRunnable: Runnable? = null
    private var autoDisconnectRunnable: Runnable? = null
    private var pendingAutoDisconnectNotice = false
    private var latestBraceTelemetry: SpineBraceTelemetry? = null
    private var latestBraceVersion: SpineBraceVersion? = null
    private var latestHistorySnapshot: SpineBraceHistorySnapshot? = null
    private var latestHistoryReceivedAt: LocalDateTime? = null
    private var latestUploadValidationRows: List<BluetoothValidationHour> = emptyList()
    private var latestUploadFetchedAt: LocalDateTime? = null
    private var latestUploadLastReadAt: LocalDateTime? = null
    private var latestUploadHistoryHead: Int? = null
    private var latestUploadHistoryCount: Int? = null
    private var latestUploadCompletedAt: LocalDateTime? = null
    private var latestUploadDailyCount = 0
    private var latestUploadTotalWornHours = 0.0
    private var latestUploadDeviceName: String? = null
    private var latestUploadStatus = "暂无上传数据"
    private var pendingBluetoothAction: (() -> Unit)? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var pendingHistoryUploadRunnable: Runnable? = null
    private var historyReadTimeoutRunnable: Runnable? = null
    private var deviceSyncInProgress = false
    private var deviceSyncUploading = false
    private var deviceSyncCompleted = false
    private var deviceHistoryReadRetries = 0
    private var deviceSyncReconnectRetries = 0
    private var resumingDeviceSyncAfterDisconnect = false
    private val deviceReadingMessage = "正在读取设备数据 ，请稍候"
    private val deviceReadCompleteMessage = "数据读取完成"
    private val prefs by lazy { getSharedPreferences(PREFS_NAME, MODE_PRIVATE) }
    private val child = ChildProfile()
    private val languages = listOf("简体中文", "English", "日本語", "한국어", "Español", "Français", "Deutsch")
    private val disclaimer =
        "本回答仅供健康科普与参考，不替代医生的诊断与医嘱；如有疑虑请及时咨询主治医生或支具师。"
    private val emergencyKeywords =
        listOf("呼吸困难", "喘不上气", "胸闷气短", "无法呼吸", "皮肤破溃", "破皮", "流脓", "溃烂", "水泡破了", "疼痛持续", "疼了好几天", "夜里疼醒", "麻木", "无力", "晕倒", "高烧", "伤口感染")
    private val chatMessages =
        mutableListOf<ChatMessage>(
            ChatMessage.User("少戴2小时有影响吗？"),
            ChatMessage.Ai(
                AiReply(
                    summary = "少戴会影响矫正效果，建议尽量补足医嘱时长",
                    analysis = "正在结合云端佩戴、生长和皮肤记录生成个性化分析。",
                    advice = listOf("下午14点设一次佩戴提醒", "放学后先穿戴再写作业", "睡前检查支具是否压迫皮肤"),
                    needDoctor = false,
                    doctorReason = "",
                    category = "education",
                ),
            ),
        )

    private val spineBraceBluetooth by lazy {
        SpineBraceBluetoothManager(
            this,
            object : SpineBraceBluetoothCallback {
                override fun onStatus(message: String) {
                    bluetoothStatusMessage =
                        when {
                            deviceSyncInProgress && !deviceSyncCompleted -> deviceReadingMessage
                            deviceSyncCompleted -> deviceReadCompleteMessage
                            else -> message
                        }
                    refreshBluetoothUi()
                }

                override fun onDevicesChanged(devices: List<SpineBraceDevice>) {
                    bluetoothDevices.clear()
                    bluetoothDevices.addAll(devices)
                    maybeConnectAutoTargetDevice(devices)
                    if (autoConnectInProgress || connectedBluetoothDevice != null) {
                        refreshBluetoothUi()
                        return
                    }
                    if (connectedBluetoothDevice == null && selectedBluetoothDevice == null && devices.size == 1) {
                        selectedBluetoothDevice = devices.first()
                        bluetoothStatusMessage = "已自动选中 ${devices.first().name}"
                    } else if (devices.isNotEmpty() && connectedBluetoothDevice == null) {
                        bluetoothStatusMessage = "已发现 ${devices.size} 个设备，请选择后连接"
                    }
                    refreshBluetoothUi()
                }

                override fun onConnected(device: SpineBraceDevice) {
                    val resumingSync = resumingDeviceSyncAfterDisconnect
                    resumingDeviceSyncAfterDisconnect = false
                    connectedBluetoothDevice = device
                    selectedBluetoothDevice = device
                    saveLastBluetoothDevice(device)
                    cancelAutoConnectTimeout()
                    autoConnectTargetDevice = null
                    autoConnectInProgress = false
                    scheduleBluetoothAutoDisconnect()
                    if (bluetoothHomeWarningMessage == AUTO_CONNECT_NOT_FOUND_MESSAGE) {
                        setHomeBluetoothWarning(null)
                    }
                    val phase = prefs.getString(KEY_DEVICE_SYNC_PHASE, null)
                    if (deviceSyncUploading &&
                        pendingDeviceWearFile().exists() &&
                        phase in setOf(DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, DEVICE_SYNC_PHASE_CLOUD_UPLOADING)
                    ) {
                        bluetoothStatusMessage = "正在从手机本地上传云端，完成后再读取设备新数据"
                        refreshBluetoothUi()
                        return
                    }
                    latestBraceTelemetry = null
                    latestHistorySnapshot = null
                    latestHistoryReceivedAt = null
                    latestUploadValidationRows = emptyList()
                    latestUploadFetchedAt = null
                    latestUploadLastReadAt = null
                    latestUploadHistoryHead = null
                    latestUploadHistoryCount = null
                    latestUploadCompletedAt = null
                    latestUploadDailyCount = 0
                    latestUploadTotalWornHours = 0.0
                    latestUploadDeviceName = device.name
                    latestUploadStatus = "正在读取设备数据，准备上传云端"
                    startAutoDeviceDataSync(resetRetryCounters = !resumingSync)
                    refreshBluetoothUi()
                }

                override fun onDisconnected() {
                    if (handleDeviceSyncDisconnected()) {
                        return
                    }
                    val phase = prefs.getString(KEY_DEVICE_SYNC_PHASE, null)
                    val preserveLocalCloudUpload =
                        pendingDeviceWearFile().exists() &&
                            phase in setOf(DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, DEVICE_SYNC_PHASE_CLOUD_UPLOADING)
                    connectedBluetoothDevice = null
                    latestBraceTelemetry = null
                    pendingHistoryUploadRunnable?.let(mainHandler::removeCallbacks)
                    pendingHistoryUploadRunnable = null
                    cancelHistoryReadTimeout()
                    cancelAutoConnectTimeout()
                    autoConnectInProgress = false
                    autoConnectTargetDevice = null
                    if (!preserveLocalCloudUpload) {
                        deviceSyncInProgress = false
                        deviceSyncUploading = false
                        deviceSyncCompleted = false
                    }
                    cancelBluetoothAutoDisconnect()
                    if (bluetoothHomeWarningMessage == LOW_BATTERY_WARNING_MESSAGE) {
                        setHomeBluetoothWarning(null)
                    }
                    val disconnectedStatusMessage =
                        if (pendingAutoDisconnectNotice) {
                            pendingAutoDisconnectNotice = false
                            "蓝牙连续连接已超过10分钟，已自动断开"
                        } else {
                            "蓝牙已断开"
                        }
                    bluetoothStatusMessage =
                        if (preserveLocalCloudUpload) {
                            "蓝牙已断开，正在继续从手机本地上传云端"
                        } else {
                            disconnectedStatusMessage
                        }
                    refreshBluetoothUi()
                }

                override fun onTelemetry(telemetry: SpineBraceTelemetry) {
                    val hadTelemetry = latestBraceTelemetry != null
                    latestBraceTelemetry = telemetry
                    if (hadTelemetry) {
                        return
                    }
                    updateBatteryHomeWarning(telemetry)
                    if (deviceSyncInProgress && !deviceSyncCompleted) {
                        bluetoothStatusMessage = deviceReadingMessage
                        refreshBluetoothUi()
                        return
                    }
                    if (deviceSyncCompleted) {
                        bluetoothStatusMessage = deviceReadCompleteMessage
                        refreshBluetoothUi()
                        return
                    }
                    bluetoothStatusMessage = "收到电量数据：${telemetry.batteryText}"
                    refreshBluetoothUi()
                }

                override fun onVersion(version: SpineBraceVersion) {
                    latestBraceVersion = version
                    bluetoothStatusMessage = "收到版本号：${version.displayText}"
                    refreshBluetoothUi()
                }

                override fun onHistory(snapshot: SpineBraceHistorySnapshot) {
                    latestHistorySnapshot = snapshot
                    latestHistoryReceivedAt = LocalDateTime.now()
                    if (deviceSyncInProgress && !deviceSyncCompleted) {
                        saveDeviceHistorySessionTemp(snapshot)
                        bluetoothStatusMessage = deviceReadingMessage
                        if (snapshot.complete) {
                            scheduleDeviceHistoryUpload(snapshot)
                        } else {
                            scheduleHistoryReadTimeout()
                        }
                    } else if (deviceSyncCompleted) {
                        bluetoothStatusMessage = deviceReadCompleteMessage
                    } else {
                        bluetoothStatusMessage = snapshot.summaryText
                    }
                    refreshBluetoothUi()
                }
            },
        )
    }

    private val bluetoothPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
            val action = pendingBluetoothAction
            pendingBluetoothAction = null
            val granted = requiredBluetoothPermissions().all { permission -> grants[permission] == true }
            if (granted) {
                action?.invoke()
            } else {
                autoConnectInProgress = false
                autoConnectTargetDevice = null
                cancelAutoConnectTimeout()
                bluetoothStatusMessage = "未获得蓝牙权限，无法扫描或连接设备。"
                Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
                refreshBluetoothUi()
            }
        }

    private val cameraPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            val action = pendingCameraAction
            pendingCameraAction = null
            if (granted) {
                action?.invoke()
            } else {
                skinCloudStatusMessage = "未获得相机权限，无法拍照"
                Toast.makeText(this, skinCloudStatusMessage, Toast.LENGTH_SHORT).show()
                refreshRecordsUi()
            }
        }

    private val skinPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                skinCapturedPhoto = bitmap
                skinCloudStatusMessage = "已拍照，保存记录时会上传照片"
            } else {
                skinCloudStatusMessage = "未获取到照片"
            }
            refreshRecordsUi()
        }

    private val imagingPhotoLauncher =
        registerForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
            if (bitmap != null) {
                imagingCapturedPhoto = bitmap
                imagingSelectedPhotoUri = null
                imagingCloudStatusMessage = "已拍照，保存影像记录时会上传照片"
            } else {
                imagingCloudStatusMessage = "未获取到照片"
            }
            refreshRecordsUi()
        }

    private val imagingGalleryLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                imagingSelectedPhotoUri = uri
                imagingCapturedPhoto = null
                imagingCloudStatusMessage = "已选择图库照片，保存影像记录时会上传照片"
            } else {
                imagingCloudStatusMessage = "未选择照片"
            }
            refreshRecordsUi()
        }

    private val dataExportDocumentLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
            val payload = pendingDataExportJson
            if (uri == null || payload == null) {
                dataExportStatusMessage = "已取消保存，数据尚未备份"
                pendingDataExportJson = null
                pendingDataExportFileName = null
                if (stage == Stage.App && currentTab == MainTab.DataExport) {
                    render()
                }
                return@registerForActivityResult
            }
            runCatching {
                contentResolver.openOutputStream(uri)?.use { output ->
                    output.write(payload.toByteArray(Charsets.UTF_8))
                } ?: throw IllegalStateException("无法写入备份文件")
            }.onSuccess {
                val exportedAt = LocalDateTime.now().toString()
                prefs.edit().putString(KEY_LAST_DATA_EXPORT_AT, exportedAt).apply()
                dataExportStatusMessage = "备份文件已保存。确认文件可打开后，再执行删除数据。"
                Toast.makeText(this, "数据备份已保存", Toast.LENGTH_SHORT).show()
            }.onFailure {
                dataExportStatusMessage = "备份文件保存失败，请重新导出"
                Toast.makeText(this, dataExportStatusMessage, Toast.LENGTH_SHORT).show()
            }
            pendingDataExportJson = null
            pendingDataExportFileName = null
            if (stage == Stage.App && currentTab == MainTab.DataExport) {
                render()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selectedLanguageIndex = loadSelectedLanguageIndex()
        loadProfileFromLocal()
        recoverPendingDeviceSyncState()
        configureWindow(window)
        render()
        mainHandler.post {
            if (!resumePendingLocalWearUploadIfNeeded()) {
                autoConnectLastBluetoothDevice()
            }
        }
    }

    override fun onDestroy() {
        if ((deviceSyncInProgress || deviceSyncUploading) && !deviceSyncCompleted) {
            val phase = prefs.getString(KEY_DEVICE_SYNC_PHASE, null)
            if (pendingDeviceWearFile().exists() && phase in setOf(DEVICE_SYNC_PHASE_CLEARING, DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, DEVICE_SYNC_PHASE_CLOUD_UPLOADING)) {
                markDeviceSyncPhase(DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, "APP 已退出，手机本地数据已保留；下次打开后将继续上传云端。")
            } else {
                markDeviceSyncInterrupted("APP 已退出，本次数据获取未安全完成；设备历史数据不会被清理，下次连接后将重新读取。")
            }
        }
        pendingHistoryUploadRunnable?.let(mainHandler::removeCallbacks)
        pendingHistoryUploadRunnable = null
        cancelHistoryReadTimeout()
        cancelAutoConnectTimeout()
        cancelBluetoothAutoDisconnect()
        spineBraceBluetooth.close()
        super.onDestroy()
    }

    private fun configureWindow(window: Window) {
        window.statusBarColor = P.bg
        window.navigationBarColor = P.surface
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
    }

    private fun render() {
        val root = FrameLayout(this).apply {
            setBackgroundColor(P.bg)
        }

        when (stage) {
            Stage.Login -> root.addView(renderLogin())
            Stage.Device -> root.addView(renderDeviceBinding())
            Stage.Profile -> root.addView(renderProfileWizard())
            Stage.App -> root.addView(renderMainShell())
        }

        setContentView(root)
    }

    private fun renderLogin(): View {
        return ScrollView(this).apply {
            isFillViewport = true
            addView(
                vertical {
                    gravity = Gravity.CENTER
                    setPadding(dp(20), dp(28), dp(20), dp(28))
                    addView(
                        card {
                            setPadding(dp(22), dp(24), dp(22), dp(22))
                            gravity = Gravity.CENTER_HORIZONTAL
                            addView(brandMark())
                            addSpace(12)
                            addView(label("脊护妈妈助手", 26f, P.text, Typeface.BOLD, Gravity.CENTER))
                            addView(label("Spinecare Mom", 14f, P.primary, Typeface.BOLD, Gravity.CENTER))
                            addView(label("守护孩子的每一小时佩戴", 15f, P.secondary, Typeface.NORMAL, Gravity.CENTER))
                            addSpace(22)
                            addView(field("手机号", loginPhone, InputType.TYPE_CLASS_PHONE) { loginPhone = it })
                            addSpace(10)
                            addView(
                                horizontal {
                                    gravity = Gravity.BOTTOM
                                    addView(field("验证码", verificationCode, InputType.TYPE_CLASS_NUMBER) { verificationCode = it }, weightLp(1f))
                                    addSpace(8, horizontal = true)
                                    addView(secondaryButton("获取验证码") {}, widthLp(dp(116)))
                                },
                            )
                            addSpace(12)
                            addView(primaryButton("登录 / 注册") {
                                loginMethod = "sms"
                                continueAfterLogin()
                            }.apply {
                                styleLoginSubmitState(this)
                            }, matchLp())
                            addSpace(8)
                            addView(label("— 或 —", 13f, P.muted, Typeface.NORMAL, Gravity.CENTER))
                            addSpace(8)
                            addView(secondaryButton("微信一键登录") {
                                loginMethod = "wechat"
                                continueAfterLogin()
                            }.apply {
                                styleSecondarySubmitState(this)
                            }, matchLp())
                            addSpace(10)
                            addView(loginAgreementPanel())
                            addSpace(8)
                            addView(
                                CheckBox(this@MainActivity).apply {
                                    text = uiText("我已阅读并同意《用户协议》《隐私政策》")
                                    setTextColor(if (agreementReadConfirmed) P.secondary else P.muted)
                                    textSize = 13f
                                    isChecked = consentChecked
                                    buttonTintList = android.content.res.ColorStateList.valueOf(P.primary)
                                    setOnCheckedChangeListener { button, checked ->
                                        if (checked && !agreementReadConfirmed) {
                                            consentChecked = false
                                            loginAgreementExpanded = true
                                            button.isChecked = false
                                            Toast.makeText(this@MainActivity, uiText("请先阅读用户协议，阅读完毕后再勾选同意。"), Toast.LENGTH_SHORT).show()
                                            render()
                                        } else {
                                            consentChecked = checked
                                            saveLoginConsentState()
                                        }
                                    }
                                },
                            )
                        },
                        matchLp(),
                    )
                },
                matchLp(),
            )
        }
    }

    private fun loginAgreementPanel(): View =
        vertical {
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(
                horizontal {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label("用户协议与隐私政策", 15f, P.text, Typeface.BOLD), weightLp(1f))
                    addView(
                        chip(
                            if (agreementReadConfirmed) "已阅读" else "待阅读",
                            if (agreementReadConfirmed) P.success else P.warning,
                        ),
                    )
                },
            )
            addSpace(6)
            addView(label("登录前请先阅读用户协议和隐私政策。阅读完毕后勾选同意，才可以登录。", 13f, P.secondary))
            addSpace(8)
            addView(
                horizontal {
                    addView(secondaryButton(if (loginAgreementExpanded) "收起用户协议" else "查看用户协议") {
                        loginAgreementExpanded = !loginAgreementExpanded
                        render()
                    }, weightLp(1f))
                    if (loginAgreementExpanded) {
                        addSpace(8, horizontal = true)
                        addView(primaryButton("我已阅读完毕") {
                            markLoginAgreementRead()
                        }, weightLp(1f))
                    }
                },
            )
            if (loginAgreementExpanded) {
                addSpace(10)
                addView(label("用户协议", 15f, P.text, Typeface.BOLD))
                userAgreementSections().forEach { (heading, body) ->
                    addDocumentSection(heading, body)
                }
                addSpace(6)
                addView(label("隐私政策", 15f, P.text, Typeface.BOLD))
                privacyPolicySections().forEach { (heading, body) ->
                    addDocumentSection(heading, body)
                }
                addSpace(8)
                addView(primaryButton("我已阅读完毕") {
                    markLoginAgreementRead()
                }, matchLp())
            }
        }

    private fun markLoginAgreementRead() {
        agreementReadConfirmed = true
        consentChecked = false
        saveLoginConsentState()
        Toast.makeText(this, uiText("已阅读，请勾选同意后登录"), Toast.LENGTH_SHORT).show()
        render()
    }

    private fun saveLoginConsentState() {
        prefs.edit()
            .putBoolean(KEY_CONSENT_CHECKED, consentChecked)
            .putBoolean(KEY_LOGIN_AGREEMENT_READ, agreementReadConfirmed)
            .apply()
    }

    private fun canSubmitLogin(): Boolean = agreementReadConfirmed && consentChecked

    private fun ensureLoginConsentReady(): Boolean {
        if (!agreementReadConfirmed) {
            loginAgreementExpanded = true
            Toast.makeText(this, uiText("请先阅读用户协议，阅读完毕后再勾选同意。"), Toast.LENGTH_SHORT).show()
            render()
            return false
        }
        if (!consentChecked) {
            Toast.makeText(this, uiText("请勾选已阅读并同意后再登录。"), Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    private fun styleLoginSubmitState(button: Button) {
        val enabled = canSubmitLogin()
        button.alpha = if (enabled) 1f else 0.62f
        button.background = rounded(if (enabled) P.primary else P.muted, dp(8), null)
    }

    private fun styleSecondarySubmitState(button: Button) {
        val enabled = canSubmitLogin()
        button.alpha = if (enabled) 1f else 0.68f
        button.setTextColor(if (enabled) P.primary else P.muted)
        button.background = rounded(if (enabled) P.primaryLight else P.surfaceAlt, dp(8), if (enabled) adjustAlpha(P.primary, 0.18f) else P.softLine)
    }

    private fun renderDeviceBinding(): View {
        return screenPage(showBottomPadding = false) {
            addView(pageHeader("设备绑定", "请扫描并连接 WM-SP# 蓝牙设备"))
            addView(bluetoothConnectionPanel())
            addSpace(12)
            addView(
                card {
                    addView(infoStrip("连接成功后会保存为本机绑定设备，下次登录将自动跳过此页面。"))
                    addSpace(12)
                    addView(
                        horizontal {
                            addView(secondaryButton("暂时跳过") {
                                continueAfterDeviceBinding()
                            }, weightLp(1f))
                            addSpace(10, horizontal = true)
                            addView(primaryButton("继续建档") {
                                if (hasBoundBluetoothDevice()) {
                                    continueAfterDeviceBinding()
                                } else {
                                    Toast.makeText(this@MainActivity, "请先连接蓝牙设备，或选择暂时跳过。", Toast.LENGTH_SHORT).show()
                                }
                            }.apply {
                                setBluetoothButtonEnabled(this, hasBoundBluetoothDevice())
                            }, weightLp(1f))
                        },
                    )
                },
            )
        }
    }

    private fun continueAfterLogin() {
        if (!ensureLoginConsentReady()) {
            return
        }
        saveLoginConsentState()
        profileStep = 1
        if (isProfileCompleted()) {
            routeAfterLogin()
            render()
            return
        }
        if (profileCloudLookupInProgress) {
            Toast.makeText(this, "正在读取云端建档信息，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        profileCloudLookupInProgress = true
        Toast.makeText(this, "正在读取云端建档信息...", Toast.LENGTH_SHORT).show()
        thread(name = "SpinecareProfileFetch") {
            runCatching {
                getJson(ApiConfig.endpoint("/api/v1/children/${child.id}"))
            }.onSuccess { profile ->
                mainHandler.post {
                    profileCloudLookupInProgress = false
                    if (isRemoteProfileUsable(profile)) {
                        applyProfileFromJson(profile)
                        saveProfileLocally(completed = true)
                    }
                    routeAfterLogin()
                    render()
                }
            }.onFailure {
                mainHandler.post {
                    profileCloudLookupInProgress = false
                    routeAfterLogin()
                    render()
                }
            }
        }
    }

    private fun routeAfterLogin() {
        stage =
            when {
                !hasBoundBluetoothDevice() -> Stage.Device
                isProfileCompleted() -> Stage.App
                else -> Stage.Profile
            }
        currentTab = MainTab.Home
    }

    private fun continueAfterDeviceBinding() {
        stage = if (isProfileCompleted()) Stage.App else Stage.Profile
        currentTab = MainTab.Home
        render()
    }

    private fun profileAgeText(): String {
        val years =
            runCatching {
                Period.between(LocalDate.parse(child.birthDate), LocalDate.now()).years
            }.getOrDefault(child.age)
        val age = years.coerceAtLeast(0)
        return languageText(
            "${age}岁",
            "$age years old",
            "${age}歳",
            "${age}세",
            "$age años",
            "$age ans",
            "$age Jahre",
        )
    }

    private fun renderProfileWizard(): View {
        return screenPage(showBottomPadding = false) {
            val stepLabel = listOf("基础信息", "病情信息", "医嘱与支具")[profileStep - 1]
            addView(pageHeader("建档向导", "$profileStep/3 · $stepLabel"))
            addView(
                ProgressBar(this@MainActivity, null, android.R.attr.progressBarStyleHorizontal).apply {
                    max = 100
                    progress = (profileStep * 100f / 3f).roundToInt()
                    progressTintList = android.content.res.ColorStateList.valueOf(P.primary)
                    progressBackgroundTintList = android.content.res.ColorStateList.valueOf(0xFFDDE8E8.toInt())
                },
                matchHeightLp(dp(7)),
            )
            addSpace(12)
            addView(
                card {
                    when (profileStep) {
                        1 -> addProfileStepOne()
                        2 -> addProfileStepTwo()
                        else -> addProfileStepThree()
                    }
                },
            )
            addSpace(14)
            addView(
                horizontal {
                    if (profileStep > 1) {
                        addView(secondaryButton("上一步") {
                            profileStep = (profileStep - 1).coerceAtLeast(1)
                            render()
                        }, weightLp(1f))
                        addSpace(10, horizontal = true)
                    }
                    addView(primaryButton(if (profileStep < 3) "下一步" else "进入首页") {
                        if (profileStep < 3) {
                            profileStep += 1
                        } else {
                            saveProfileLocally(completed = true)
                            saveProfileToCloud()
                            stage = Stage.App
                            currentTab = MainTab.Home
                        }
                        render()
                    }, weightLp(1f))
                },
            )
        }
    }

    private fun LinearLayout.addProfileStepOne() {
        addView(field("昵称", child.nickname, InputType.TYPE_CLASS_TEXT) { child.nickname = it.ifBlank { "朵朵" } })
        addSpace(10)
        addView(choiceRow("性别", listOf("男", "女"), listOf("男", "女").indexOf(child.gender).coerceAtLeast(0)) {
            child.gender = it
            render()
        })
        addSpace(10)
        addView(field("出生日期", child.birthDate, InputType.TYPE_CLASS_DATETIME) { child.birthDate = it })
    }

    private fun LinearLayout.addProfileStepTwo() {
        addView(field("Cobb 角(初始)", child.cobb.toString(), InputType.TYPE_CLASS_NUMBER) {
            child.cobb = it.toIntOrNull()?.coerceIn(0, 120) ?: child.cobb
        })
        addSpace(10)
        val curveOptions = listOf("胸弯", "腰弯", "胸腰弯", "双弯")
        addView(choiceRow("弯曲部位", curveOptions, curveOptions.indexOf(child.curveType).coerceAtLeast(0)) {
            child.curveType = it
            render()
        })
        addSpace(10)
        val risserOptions = listOf("未知", "0", "1", "2", "3", "4", "5")
        addView(choiceRow("Risser 征", risserOptions, risserOptions.indexOf(child.risser).coerceAtLeast(0)) {
            child.risser = it
            render()
        })
        addSpace(10)
        addView(infoStrip("Cobb 角和 Risser 征通常能在影像报告或病历记录中找到。"))
    }

    private fun LinearLayout.addProfileStepThree() {
        addView(field("医嘱佩戴时长", child.prescribedHours.toString(), InputType.TYPE_CLASS_NUMBER) {
            child.prescribedHours = it.toIntOrNull()?.coerceIn(0, 24) ?: child.prescribedHours
        })
        addSpace(10)
        val braceOptions = listOf("硬支具", "软支具", "未知")
        addView(choiceRow("支具类型", braceOptions, braceOptions.indexOf(child.braceType).coerceAtLeast(0)) {
            child.braceType = it
            render()
        })
        addSpace(10)
        addView(field("初诊日期", child.firstVisitDate, InputType.TYPE_CLASS_DATETIME) { child.firstVisitDate = it })
        addSpace(10)
        profileSyncStatusMessage?.let {
            addView(infoStrip(it))
            addSpace(10)
        }
        addView(infoStrip("档案会用于看板、报告和 AI 个性化上下文，默认不包含真实姓名与证件信息。"))
    }

    private fun renderMainShell(): View {
        if (currentTab == MainTab.ChildMode) {
            return renderChildMode()
        }

        val shell = vertical {
            addView(
                FrameLayout(this@MainActivity).apply {
                    addView(
                        screenPage(showBottomPadding = true) {
                            when (currentTab) {
                                MainTab.Home -> addHomePage()
                                MainTab.Reports -> addReportsPage()
                                MainTab.Consult -> addConsultPage()
                                MainTab.Logs -> addLogsPage()
                                MainTab.Me -> addMePage()
                                MainTab.ProfileEdit -> addProfileEditPage()
                                MainTab.Alerts -> addAlertsPage()
                                MainTab.Settings -> addSettingsPage()
                                MainTab.BluetoothValidation -> addBluetoothValidationPage()
                                MainTab.ChildMode -> Unit
                                MainTab.DataExport -> addDataExportPage()
                                MainTab.DataDelete -> addDataDeletePage()
                                MainTab.HelpCenter -> addHelpCenterPage()
                            }
                        },
                        FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        ),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            if (currentTab != MainTab.Alerts && currentTab != MainTab.Settings && currentTab != MainTab.ProfileEdit && currentTab != MainTab.BluetoothValidation && currentTab != MainTab.DataExport && currentTab != MainTab.DataDelete && currentTab != MainTab.HelpCenter) {
                addView(bottomNavigation(), matchHeightLp(dp(76)))
            }
        }
        return shell
    }

    private fun LinearLayout.addHomePage() {
        loadAlerts()
        loadHomeWearData()
        loadGrowthLogs()
        loadSkinLogs()
        val thirtyDaySummary = currentThirtyDayWearSummary()
        val trendRecords = currentSevenDayWearRecords()
        val trendSummary = summaryFromWearRecords(trendRecords)
        val trendValues = trendChartHourValues(trendRecords)
        val trendLabels = trendChartLabels(trendRecords)
        val trendHourLabels = trendChartHourLabels(trendRecords)
        val thirtyDayProgress = thirtyDaySummary?.let { wearProgressPercent(it.avgHours, it.prescribedHours) } ?: 0
        val thirtyDayCompliance = thirtyDaySummary?.complianceRate ?: 0
        val thirtyDayChipColor = complianceLevelColor(thirtyDayCompliance)
        addView(appHeader(child.nickname, "Spinecare Mom", showBell = true, showSettings = true))
        bluetoothHomeWarningMessage?.let { warning ->
            addView(infoStrip(warning))
            addSpace(12)
        }
        wearCloudStatusMessage?.let { status ->
            addView(infoStrip(status))
            addSpace(12)
        }
        addView(
            card {
                addView(
                    cardHeader(
                        "近30天佩戴",
                        thirtyDaySummary?.let {
                            languageText(
                                "平均 ${formatHours(it.avgHours)}/${formatHours(it.prescribedHours)} h · 有数据${it.daysCounted}天",
                                "Avg ${formatHours(it.avgHours)}/${formatHours(it.prescribedHours)} h · ${it.daysCounted} data days",
                                "平均 ${formatHours(it.avgHours)}/${formatHours(it.prescribedHours)} h · データ${it.daysCounted}日",
                                "평균 ${formatHours(it.avgHours)}/${formatHours(it.prescribedHours)} h · 데이터 ${it.daysCounted}일",
                                "Media ${formatHours(it.avgHours)}/${formatHours(it.prescribedHours)} h · ${it.daysCounted} días con datos",
                                "Moy. ${formatHours(it.avgHours)}/${formatHours(it.prescribedHours)} h · ${it.daysCounted} jours avec données",
                                "Schnitt ${formatHours(it.avgHours)}/${formatHours(it.prescribedHours)} h · ${it.daysCounted} Datentage",
                            )
                        } ?: "正在读取云端数据",
                        chip("达标率 ${thirtyDayCompliance}%", thirtyDayChipColor),
                    ),
                )
                addView(
                    horizontal {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(ProgressRingView(this@MainActivity, thirtyDayCompliance, uiText("达标率"), thirtyDayChipColor), squareLp(dp(118)))
                        addSpace(14, horizontal = true)
                        addView(
                            vertical {
                                addView(rowText("目标完成", "${formatHours(thirtyDaySummary?.avgHours ?: 0.0)}h"))
                                addView(progressLine(thirtyDayProgress))
                                addSpace(12)
                                addView(
                                    metricGrid(
                                        listOf(
                                            dayCount(thirtyDaySummary?.longestStreak ?: 0) to "最长连续达标",
                                            dayCount(thirtyDaySummary?.daysCounted ?: 0) to "近30天有数据",
                                            dayCount(wearCloudRecords.size) to "历史记录",
                                        ),
                                        itemHeightDp = 92,
                                        valueSize = 18f,
                                        labelSize = 11f,
                                    ),
                                )
                            },
                            weightLp(1f),
                        )
                    },
                )
            },
        )
        addSpace(12)
        addView(
            card {
                addView(
                    cardHeader(
                        "近7天佩戴",
                        if (trendRecords.isEmpty()) {
                            "暂无云端数据"
                        } else {
                            languageText(
                                "平均${formatHours(trendSummary.avgHours)}h · 达标${trendRecords.count { isWearCompliant(it) }}/${trendRecords.size}天",
                                "Avg ${formatHours(trendSummary.avgHours)}h · met ${trendRecords.count { isWearCompliant(it) }}/${trendRecords.size} days",
                                "平均${formatHours(trendSummary.avgHours)}h · 達成${trendRecords.count { isWearCompliant(it) }}/${trendRecords.size}日",
                                "평균 ${formatHours(trendSummary.avgHours)}h · 달성 ${trendRecords.count { isWearCompliant(it) }}/${trendRecords.size}일",
                                "Media ${formatHours(trendSummary.avgHours)}h · cumple ${trendRecords.count { isWearCompliant(it) }}/${trendRecords.size} días",
                                "Moy. ${formatHours(trendSummary.avgHours)}h · atteint ${trendRecords.count { isWearCompliant(it) }}/${trendRecords.size} jours",
                                "Schnitt ${formatHours(trendSummary.avgHours)}h · erfüllt ${trendRecords.count { isWearCompliant(it) }}/${trendRecords.size} Tage",
                            )
                        },
                        chip(if (wearCloudLoadedChildId == child.id) "云端数据" else "读取中", if (trendRecords.isEmpty()) P.warning else P.primary),
                    ),
                )
                addView(
                    BarChartView(
                        this@MainActivity,
                        trendValues,
                        trendLabels,
                        trendHourLabels,
                        targetValue = prescribedWearHours(),
                        maxValue = 24.0,
                    ),
                    matchHeightLp(dp(172)),
                )
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("智能解读", null, chip("AI", P.primary)))
                homeCloudInsights().forEach { insight -> addInsight(insight) }
            },
        )
        addSpace(12)
        addView(
            grid(2) {
                addHomeQuick("皮肤记录", "肤", "问题拍照", P.danger) { currentTab = MainTab.Logs; logsTab = 0; render() }
                addHomeQuick("生长记录", "长", "身高趋势", P.success) { currentTab = MainTab.Logs; logsTab = 1; render() }
                addHomeQuick("影像档案", "片", "影像上传", 0xFF4C6FB8.toInt()) { currentTab = MainTab.Logs; logsTab = 2; render() }
                addHomeQuick("问AI", "AI", "智能咨询", 0xFF7A5BB8.toInt()) { currentTab = MainTab.Consult; render() }
                addHomeQuick("复诊报告", "报", "复诊材料", P.primary) { currentTab = MainTab.Reports; reportTab = 1; render() }
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("历史趋势", "佩戴统计", null))
                addView(metricGrid(listOf("17.5h" to "35天日均", "46%" to "总体达标率", dayCount(3) to "最长连续")))
            },
        )
    }

    private fun LinearLayout.addConsultPage() {
        loadHomeWearData()
        loadGrowthLogs()
        loadSkinLogs()
        addView(
            appHeader(
                "咨询",
                languageText(
                    "当前：${child.nickname}(已关联档案)",
                    "Current: ${child.nickname} (profile linked)",
                    "現在：${child.nickname}（プロフィール連携済み）",
                    "현재: ${child.nickname}(프로필 연결됨)",
                    "Actual: ${child.nickname} (perfil vinculado)",
                    "Actuel : ${child.nickname} (dossier lié)",
                    "Aktuell: ${child.nickname} (Profil verknüpft)",
                ),
                showBell = true,
            ),
        )
        addView(
            card {
                addView(cardHeader("常见问题", "近35天云端数据已注入", chip("个性化", P.success)))
                addView(
                    grid(2) {
                        listOf("少戴2h有影响吗", "皮肤红了怎么办", "能上体育课吗", "被同学笑话怎么办").forEach { question ->
                            addView(chipButton(question) {
                                sendQuestion(question)
                            })
                        }
                    },
                )
            },
        )
        addSpace(12)
        chatMessages.forEach { msg ->
            when (msg) {
                is ChatMessage.User -> addView(userBubble(msg.text))
                is ChatMessage.Ai -> addView(aiCard(msg.reply))
            }
            addSpace(8)
        }
        addView(
            card {
                val input = EditText(this@MainActivity).apply {
                    hint = uiText("输入问题...")
                    minLines = 1
                    maxLines = 3
                    textSize = 15f
                    setTextColor(P.text)
                    setHintTextColor(P.muted)
                    background = rounded(P.surfaceAlt, dp(8), P.line)
                    setPadding(dp(12), 0, dp(12), 0)
                }
                addView(
                    horizontal {
                        addView(input, weightHeightLp(1f, dp(46)))
                        addSpace(8, horizontal = true)
                        addView(primaryButton("发送") {
                            sendQuestion(input.text.toString())
                        }, widthLp(dp(76)))
                    },
                )
            },
        )
    }

    private fun LinearLayout.addReportsPage() {
        addView(appHeader("报告", "AI 周报月报与复诊材料", showBell = true))
        addView(segmented(listOf("AI报告", "复诊报告", "归档"), reportTab) { index ->
            reportTab = index
            render()
        })
        addSpace(12)
        when (reportTab) {
            0 -> addAiReports()
            1 -> addVisitReport()
            else -> addReportArchive()
        }
    }

    private fun LinearLayout.addAiReports() {
        loadHomeWearData()
        loadSkinLogs()
        loadGrowthLogs()
        loadImagingLogs()
        val reportItems = buildAiReportItems()
        val weeklyInsights = buildWeeklyAiSummary()
        addView(
            card {
                addView(cardHeader("依从性报告", "基于云端真实数据自动生成", chip(if (wearCloudLoading) "读取中" else "生成", if (wearCloudLoading) P.warning else P.primary)))
                if (reportItems.isEmpty()) {
                    addView(infoStrip(if (wearCloudLoading) "正在读取佩戴数据，读取完成后自动生成报告。" else "暂无佩戴数据，完成蓝牙读取并上传后自动生成报告。"))
                } else {
                    reportItems.forEachIndexed { index, item ->
                        if (index > 0) addSpace(8)
                        addView(reportRow(item.title, item.subtitle, item.status, item.color))
                    }
                }
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("本周摘要", null, chip("AI", P.primary)))
                weeklyInsights.forEach { insight -> addInsight(insight) }
                if (reportItems.isNotEmpty()) {
                    addSpace(10)
                    addView(secondaryButton("保存当前AI报告到归档") {
                        archiveAiReports()
                    }, matchLp())
                }
            },
        )
    }

    private fun buildAiReportItems(): List<AiReportItem> {
        val records35 = wearCloudRecords.takeLast(35)
        if (records35.isEmpty()) {
            return emptyList()
        }
        val weekRecords = records35.takeLast(7)
        val monthRecords = records35.takeLast(30)
        val weekSummary = summaryFromWearRecords(weekRecords)
        val monthSummary = summaryFromWearRecords(monthRecords)
        val weekStatus = reportComplianceStatus(weekSummary.complianceRate, weekRecords.size)
        val monthStatus = reportComplianceStatus(monthSummary.complianceRate, monthRecords.size)
        val weekGapText = reportGapText(weekRecords)

        return listOf(
            AiReportItem(
                title = "周报 · ${formatReportDateRange(weekRecords)}",
                subtitle = "达标率${weekSummary.complianceRate}% · 平均${formatHours(weekSummary.avgHours)}h · ${weekGapText}",
                status = weekStatus.first,
                color = weekStatus.second,
            ),
            AiReportItem(
                title = "30天报告 · ${formatReportDateRange(monthRecords)}",
                subtitle = "平均${formatHours(monthSummary.avgHours)}h · 达标${monthRecords.count { isWearCompliant(it) }}/${monthRecords.size}天 · 达标率${monthSummary.complianceRate}%",
                status = monthStatus.first,
                color = monthStatus.second,
            ),
            buildAiAbnormalReport(monthRecords),
        )
    }

    private fun buildAiAbnormalReport(records30: List<WearRecord>): AiReportItem {
        if (records30.isEmpty()) {
            return AiReportItem("异常报告 · 暂无周期", "暂无佩戴数据，暂不生成异常判断", "数据不足", P.warning)
        }
        val target = prescribedWearHours()
        val severeThreshold = roundOne(target * 0.6)
        val severeStreak = longestStreakWhere(records30) { target > 0.0 && it.wornHours < severeThreshold }
        val lowStreak = longestStreakWhere(records30) { target > 0.0 && it.wornHours < target * 0.8 }
        val skinProblems = recentSkinProblems(30)
        val growthDelta = growthDeltaWithinDays(31)
        val details = mutableListOf<String>()
        var color = P.success

        when {
            severeStreak >= 3 -> {
                details += "连续${severeStreak}天低于医嘱60%"
                color = P.danger
            }
            lowStreak >= 3 -> {
                details += "连续${lowStreak}天低于医嘱80%"
                color = P.warning
            }
        }
        if (skinProblems.isNotEmpty()) {
            details += "皮肤问题${skinProblems.size}条"
            if (color != P.danger) color = P.warning
        }
        growthDelta?.takeIf { it >= 1.3 }?.let { delta ->
            details += "近1个月身高增加${formatHours(delta)}cm"
            if (color != P.danger) color = P.warning
        }
        if (details.isEmpty()) {
            details += "未触发严重佩戴、皮肤或生长异常"
        }
        val status =
            when (color) {
                P.danger -> "红色"
                P.warning -> "需关注"
                else -> "正常"
            }
        return AiReportItem(
            title = "异常报告 · ${formatReportDateRange(records30)}",
            subtitle = details.joinToString(" · "),
            status = status,
            color = color,
        )
    }

    private fun buildWeeklyAiSummary(): List<String> {
        val records35 = wearCloudRecords.takeLast(35)
        if (records35.isEmpty()) {
            return listOf(if (wearCloudLoading) "正在读取佩戴数据，读取完成后自动生成本周摘要。" else "暂无佩戴数据，完成蓝牙读取并上传后自动生成本周摘要。")
        }
        val target = prescribedWearHours()
        val weekRecords = records35.takeLast(7)
        val previousWeekRecords = records35.dropLast(7).takeLast(7)
        val weekSummary = summaryFromWearRecords(weekRecords)
        val monthRecords = records35.takeLast(30)
        val severeThreshold = roundOne(target * 0.6)
        val severeStreak = longestStreakWhere(monthRecords) { target > 0.0 && it.wornHours < severeThreshold }
        val lowStreak = longestStreakWhere(monthRecords) { target > 0.0 && it.wornHours < target * 0.8 }
        val insights = mutableListOf<String>()

        val weekCompliantDays = weekRecords.count { isWearCompliant(it) }
        insights += languageText(
            "近7天平均${formatHours(weekSummary.avgHours)}h/天，医嘱${formatHours(target)}h/天，达标${weekCompliantDays}/${weekRecords.size}天，达标率${weekSummary.complianceRate}%。",
            "7-day average ${formatHours(weekSummary.avgHours)}h/day, prescribed ${formatHours(target)}h/day, met ${weekCompliantDays}/${weekRecords.size} days, compliance ${weekSummary.complianceRate}%.",
            "7日平均${formatHours(weekSummary.avgHours)}h/日、指示${formatHours(target)}h/日、達成${weekCompliantDays}/${weekRecords.size}日、達成率${weekSummary.complianceRate}%。",
            "7일 평균 ${formatHours(weekSummary.avgHours)}h/일, 처방 ${formatHours(target)}h/일, 달성 ${weekCompliantDays}/${weekRecords.size}일, 달성률 ${weekSummary.complianceRate}%.",
            "Media 7 días ${formatHours(weekSummary.avgHours)}h/día, prescrito ${formatHours(target)}h/día, cumple ${weekCompliantDays}/${weekRecords.size} días, cumplimiento ${weekSummary.complianceRate}%.",
            "Moyenne 7 jours ${formatHours(weekSummary.avgHours)}h/jour, prescription ${formatHours(target)}h/jour, atteint ${weekCompliantDays}/${weekRecords.size} jours, observance ${weekSummary.complianceRate}%.",
            "7-Tage-Schnitt ${formatHours(weekSummary.avgHours)}h/Tag, verordnet ${formatHours(target)}h/Tag, erfüllt ${weekCompliantDays}/${weekRecords.size} Tage, Erfüllung ${weekSummary.complianceRate}%.",
        )
        previousWeekRecords.takeIf { it.isNotEmpty() }?.let { previous ->
            val previousAvg = roundOne(previous.sumOf(WearRecord::wornHours) / previous.size)
            val delta = roundOne(weekSummary.avgHours - previousAvg)
            insights +=
                when {
                    delta >= 1.0 -> "较前7天增加${formatHours(delta)}h/天，近期佩戴有改善。"
                    delta <= -1.0 -> "较前7天下降${formatHours(-delta)}h/天，需要关注佩戴下降原因。"
                    else -> "较前7天变化小于1h/天，近期佩戴基本稳定。"
                }
        }
        topGapHours(weekRecords).takeIf { it.isNotEmpty() }?.let { gaps ->
            insights += "本周主要缺口集中在${gaps.joinToString("、")}。"
        }
        when {
            severeStreak >= 3 -> insights += "近30天存在连续${severeStreak}天低于医嘱60%，建议复诊时重点沟通。"
            lowStreak >= 3 -> insights += "近30天存在连续${lowStreak}天低于医嘱80%，建议排查作息、皮肤不适或抗拒原因。"
        }
        recentSkinProblems(30).takeIf { it.isNotEmpty() }?.let { problems ->
            insights += "近期皮肤记录：${problems.take(2).joinToString("；") { "${formatDateShort(it.date)} ${it.region}${it.status}" }}，已纳入复诊关注。"
        }
        growthDeltaWithinDays(31)?.takeIf { it >= 1.3 }?.let { delta ->
            insights += "近1个月身高增加${formatHours(delta)}cm，超过1.3cm，建议评估支具适配。"
        }
        imagingLogs.firstOrNull()?.let { latest ->
            insights += "最近影像记录为${formatDateShort(latest.shotDate)} ${latest.imageType}，可作为复诊材料补充。"
        }
        return insights.take(5).ifEmpty { listOf("本周未发现明显异常，建议继续保持医嘱佩戴和定期记录。") }
    }

    private fun reportComplianceStatus(rate: Int, days: Int): Pair<String, Int> =
        when {
            days < 3 -> "数据不足" to P.warning
            rate >= 80 -> "良好" to P.success
            rate >= 60 -> "需关注" to P.warning
            else -> "偏低" to P.danger
        }

    private fun reportGapText(records: List<WearRecord>): String {
        val gaps = topGapHours(records)
        return if (gaps.isEmpty()) "暂无明显缺口" else "缺口${gaps.joinToString("、")}"
    }

    private fun recentSkinProblems(days: Long): List<SkinLog> {
        val latestDate = skinLogs.maxOfOrNull { it.date } ?: return emptyList()
        val startDate = latestDate.minusDays(days - 1)
        return skinLogs.filter { item ->
            !item.date.isBefore(startDate) && item.status.split("、").any { status -> status !in listOf("正常", "无异常") }
        }
    }

    private fun formatReportDateRange(records: List<WearRecord>): String {
        val first = records.firstOrNull()?.date ?: return "暂无周期"
        val last = records.lastOrNull()?.date ?: first
        return if (first == last) formatDateCn(first) else "${formatDateCn(first)}-${formatDateCn(last)}"
    }

    private fun formatDateCn(date: LocalDate): String =
        languageText(
            "${date.monthValue}月${date.dayOfMonth}日",
            "${date.dayOfMonth}/${date.monthValue}",
            "${date.monthValue}月${date.dayOfMonth}日",
            "${date.monthValue}월 ${date.dayOfMonth}일",
            "${date.dayOfMonth}/${date.monthValue}",
            "${date.dayOfMonth}/${date.monthValue}",
            "${date.dayOfMonth}.${date.monthValue}.",
        )

    private fun dayCount(days: Int): String =
        languageText(
            "${days}天",
            "${days}d",
            "${days}日",
            "${days}일",
            "${days} d",
            "${days} j",
            "${days} T",
        )

    private fun LinearLayout.addVisitReport() {
        loadVisitReportWearData()
        loadSkinLogs()
        loadGrowthLogs()
        loadImagingLogs()
        val reportRecords = visitReportWearRecords()
        val reportPeriod = visitReportPeriodLabel(reportRecords)
        addView(
            card {
                addView(cardHeader("复诊报告预览", "周期：$reportPeriod", null))
                visitWearCloudStatusMessage?.let {
                    addView(infoStrip(it))
                    addSpace(10)
                }
                addView(
                    vertical {
                        background = rounded(P.surface, dp(8), P.line)
                        setPadding(dp(14), dp(14), dp(14), dp(14))
                        addView(label("Spinecare Mom 复诊报告", 17f, P.text, Typeface.BOLD, Gravity.CENTER))
                        addSpace(10)
                        addPaperRow("基本信息", visitReportBasicInfo())
                        addPaperRow("佩戴摘要", visitReportWearSummary(reportRecords))
                        addPaperRow("佩戴至今记录", visitReportWearAllSummary())
                        if (reportRecords.isNotEmpty()) {
                            addView(
                                BarChartView(
                                    this@MainActivity,
                                    trendChartHourValues(reportRecords),
                                    visitReportChartLabels(reportRecords),
                                    emptyMap(),
                                    targetValue = prescribedWearHours(),
                                    maxValue = 24.0,
                                ),
                                matchHeightLp(dp(118)),
                            )
                            addSpace(8)
                        }
                        addPaperRow("缺口时段", visitReportGapSummary(reportRecords))
                        addPaperRow("皮肤问题", visitReportSkinSummary())
                        addPaperRow("生长变化", visitReportGrowthSummary())
                        addPaperRow("影像资料", visitReportImagingSummary())
                        addPaperRow("复诊建议", visitReportAiAdvice(reportRecords))
                    },
                )
                addSpace(12)
                addView(primaryButton("生成PDF及微信分享") {
                    archiveVisitReport()
                }, matchLp())
            },
        )
    }

    private fun LinearLayout.addReportArchive() {
        loadReportArchives()
        addView(
            card {
                addView(cardHeader("归档逻辑", "保存报告生成当时的数据快照", chip("云端", P.primary)))
                addInsight("归档用于保存周报、30天报告、异常报告和复诊报告的生成结果，作为后续复诊、对比和追溯依据。")
                addInsight("归档保存的是生成当时的数据快照；后续佩戴、皮肤、生长或影像记录变化时，已归档报告不会被自动改写。")
            },
        )
        addSpace(12)
        addView(
            card {
                val trailing =
                    when {
                        reportArchiveLoading -> chip("读取中", P.warning)
                        archivedReports.isEmpty() -> chip("暂无", P.muted)
                        else -> chip("${archivedReports.size}条", P.primary)
                    }
                addView(cardHeader("归档列表", "来源：云端 reports 表", trailing))
                reportArchiveStatusMessage?.let {
                    addSpace(8)
                    addView(infoStrip(it))
                }
                if (archivedReports.isEmpty()) {
                    addSpace(8)
                    addView(infoStrip(if (reportArchiveLoading) "正在读取归档记录..." else "暂无真实归档。可在AI报告页保存当前AI报告，或在复诊报告页点击“生成PDF及微信分享”。"))
                } else {
                    archivedReports.forEachIndexed { index, item ->
                        if (index > 0) addSpace(8)
                        addView(archivedReportRow(item))
                        if (selectedArchivedReportId == item.id) {
                            addSpace(8)
                            addView(archivedReportDetail(item))
                        }
                    }
                }
            },
        )
    }

    private fun archivedReportRow(item: ArchivedReport): View =
        horizontal {
            val selected = selectedArchivedReportId == item.id
            val status = archiveReportStatus(item)
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(if (selected) adjustAlpha(P.primary, 0.08f) else P.surfaceAlt, dp(8), if (selected) adjustAlpha(P.primary, 0.22f) else P.softLine)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(
                vertical {
                    addView(label("${archiveReportKindLabel(item.kind)} · ${archivePeriodText(item)}", 15f, P.text, Typeface.BOLD))
                    addView(label("生成：${archiveCreatedAtText(item.createdAt)}", 13f, P.secondary))
                },
                weightLp(1f),
            )
            addView(chip(status.first, status.second))
            addSpace(8, horizontal = true)
            addView(label(if (selected) "收起" else "详情", 13f, P.primary, Typeface.BOLD, Gravity.CENTER))
            setOnClickListener {
                selectedArchivedReportId = if (selected) null else item.id
                render()
            }
        }

    private fun archivedReportDetail(item: ArchivedReport): View =
        vertical {
            background = rounded(P.surface, dp(8), P.line)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addPaperRow("报告类型", archiveReportKindLabel(item.kind))
            addPaperRow("统计周期", archivePeriodText(item))
            addPaperRow("生成时间", archiveCreatedAtText(item.createdAt))
            item.payload.cleanString("basic_info")?.let { addPaperRow("基本信息", it) }
            item.payload.cleanString("summary")?.let { addPaperRow("报告摘要", it) }
            item.payload.cleanString("wear_summary")?.let { addPaperRow("佩戴摘要", it) }
            item.payload.cleanString("wear_all_summary")?.let { addPaperRow("佩戴至今", it) }
            item.payload.cleanString("gap_summary")?.let { addPaperRow("缺口时段", it) }
            item.payload.cleanString("skin_summary")?.let { addPaperRow("皮肤问题", it) }
            item.payload.cleanString("growth_summary")?.let { addPaperRow("生长变化", it) }
            item.payload.cleanString("imaging_summary")?.let { addPaperRow("影像资料", it) }
            item.payload.cleanString("advice")?.let { addPaperRow("建议", it) }
            if (item.payload.has("avg_hours")) {
                addPaperRow("平均佩戴", "${formatHours(item.payload.optDouble("avg_hours", 0.0))}h/天")
            }
            if (item.payload.has("compliance_rate")) {
                addPaperRow("达标率", "${item.payload.optInt("compliance_rate", 0)}%")
            }
            item.payload.optJSONArray("gap_slots")?.let { rows ->
                val text = jsonArrayText(rows)
                if (text.isNotBlank()) addPaperRow("缺口时段", text)
            }
            if (item.payload.has("period_days")) {
                addPaperRow("统计天数", "${item.payload.optInt("period_days", 0)}天")
            }
            if (item.payload.has("skin_events")) {
                addPaperRow("皮肤记录", "${item.payload.optInt("skin_events", 0)}条")
            }
            if (item.payload.has("growth_delta_cm")) {
                addPaperRow("身高变化", "${formatHours(item.payload.optDouble("growth_delta_cm", 0.0))}cm")
            }
            item.payload.optJSONArray("weekly_summary")?.let { rows ->
                val text = jsonArrayText(rows)
                if (text.isNotBlank()) addPaperRow("本周摘要", text)
            }
            item.payload.optJSONArray("daily_records")?.let { rows ->
                if (rows.length() > 0) addPaperRow("每日记录", "已保存${rows.length()}天每日佩戴快照")
            }
            addPaperRow("PDF文件", item.pdfUrl ?: "暂未生成真实PDF文件")
        }

    private fun loadReportArchives(force: Boolean = false) {
        val targetChildId = child.id
        if (!force && (reportArchiveLoading || reportArchiveLoadedChildId == targetChildId)) {
            return
        }
        reportArchiveLoading = true
        if (archivedReports.isEmpty()) {
            reportArchiveStatusMessage = "正在读取报告归档..."
        }
        thread(name = "SpinecareReportArchiveFetch") {
            runCatching {
                parseArchivedReports(getJson(ApiConfig.endpoint("/api/v1/reports?child_id=${urlEncode(targetChildId)}")))
            }.onSuccess { records ->
                mainHandler.post {
                    if (targetChildId != child.id) {
                        reportArchiveLoading = false
                        return@post
                    }
                    archivedReports = records
                    reportArchiveLoadedChildId = targetChildId
                    reportArchiveLoading = false
                    if (reportArchiveStatusMessage == "正在读取报告归档...") {
                        reportArchiveStatusMessage = null
                    }
                    if (stage == Stage.App && currentTab == MainTab.Reports && reportTab == 2) {
                        render()
                    }
                }
            }.onFailure {
                mainHandler.post {
                    if (targetChildId != child.id) {
                        reportArchiveLoading = false
                        return@post
                    }
                    reportArchiveLoading = false
                    reportArchiveStatusMessage = "报告归档读取失败，请稍后重试"
                    if (stage == Stage.App && currentTab == MainTab.Reports && reportTab == 2) {
                        render()
                    }
                }
            }
        }
    }

    private fun parseArchivedReports(json: JSONObject): List<ArchivedReport> {
        val items = json.optJSONArray("items") ?: JSONArray()
        val result = mutableListOf<ArchivedReport>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: continue
            val payloadValue = item.opt("payload_json")
            val payload =
                when (payloadValue) {
                    is JSONObject -> payloadValue
                    is String -> runCatching { JSONObject(payloadValue) }.getOrElse { JSONObject() }
                    else -> JSONObject()
                }
            result +=
                ArchivedReport(
                    id = id,
                    kind = item.optString("kind", "unknown"),
                    periodStart = parseLocalDate(item.optString("period_start")),
                    periodEnd = parseLocalDate(item.optString("period_end")),
                    payload = payload,
                    pdfUrl = item.optString("pdf_url").takeIf { it.isNotBlank() && it != "null" },
                    createdAt = item.optString("created_at"),
                )
        }
        return result.sortedByDescending { it.createdAt }
    }

    private fun loadAlerts(force: Boolean = false) {
        val targetChildId = child.id
        if (!force && (alertsLoading || alertsLoadedChildId == targetChildId || alertsLastRequestChildId == targetChildId)) {
            return
        }
        alertsLoading = true
        alertsLastRequestChildId = targetChildId
        alertsStatusMessage = null
        thread(name = "SpinecareAlertsFetch") {
            runCatching {
                parseAlerts(getJson(ApiConfig.endpoint("/api/v1/alerts?child_id=${urlEncode(targetChildId)}")))
            }.onSuccess { items ->
                mainHandler.post {
                    if (targetChildId != child.id) {
                        alertsLoading = false
                        return@post
                    }
                    alertItems = items
                    alertsLoadedChildId = targetChildId
                    alertsLastRequestChildId = null
                    alertsLoading = false
                    alertsStatusMessage = null
                    if (stage == Stage.App && (currentTab == MainTab.Home || currentTab == MainTab.Me || currentTab == MainTab.Alerts)) {
                        render()
                    }
                }
            }.onFailure {
                mainHandler.post {
                    if (targetChildId != child.id) {
                        alertsLoading = false
                        return@post
                    }
                    alertsLastRequestChildId = null
                    alertsLoading = false
                    alertsStatusMessage = "消息读取失败，请稍后重试。"
                    if (stage == Stage.App && (currentTab == MainTab.Me || currentTab == MainTab.Alerts)) {
                        render()
                    }
                }
            }
        }
    }

    private fun parseAlerts(json: JSONObject): List<AlertItem> {
        val items = json.optJSONArray("items") ?: JSONArray()
        val result = mutableListOf<AlertItem>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id").takeIf { it.isNotBlank() } ?: "alert-$index"
            result +=
                AlertItem(
                    id = id,
                    type = item.optString("type"),
                    level = item.optString("level"),
                    title = item.optString("title").ifBlank { "消息提醒" },
                    summary = item.optString("summary"),
                    triggerDetail = item.optString("trigger_detail"),
                    status = item.optString("status"),
                    createdAt = item.optString("created_at"),
                )
        }
        return result.sortedWith(compareBy<AlertItem> { alertLevelRank(it.level) }.thenByDescending { it.createdAt })
    }

    private fun alertCenterSummaryText(): String =
        when {
            alertsLoading -> "正在读取消息"
            alertsStatusMessage != null -> alertsStatusMessage ?: "消息读取失败"
            alertsLoadedChildId == child.id -> {
                val pending = unreadAlertCount()
                when {
                    pending > 0 -> "${pending}条待处理消息"
                    alertItems.isEmpty() -> "暂无消息"
                    else -> "${alertItems.size}条消息已查看"
                }
            }
            else -> "点击查看消息"
        }

    private fun archiveAiReports() {
        if (wearCloudLoading || skinCloudLoading || growthCloudLoading || imagingCloudLoading) {
            Toast.makeText(this, "云端数据仍在读取，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        val reportItems = buildAiReportItems()
        if (reportItems.isEmpty()) {
            Toast.makeText(this, "暂无可归档的AI报告", Toast.LENGTH_SHORT).show()
            return
        }
        reportArchiveStatusMessage = "正在保存AI报告归档..."
        Toast.makeText(this, reportArchiveStatusMessage, Toast.LENGTH_SHORT).show()
        thread(name = "SpinecareAiArchiveSave") {
            runCatching {
                reportItems.forEach { item ->
                    val records = aiArchiveRecordsFor(item)
                    val body =
                        JSONObject()
                            .put("child_id", child.id)
                            .put("kind", aiArchiveKindFor(item))
                            .put("period_start", records.firstOrNull()?.date?.toString() ?: JSONObject.NULL)
                            .put("period_end", records.lastOrNull()?.date?.toString() ?: JSONObject.NULL)
                            .put("payload_json", buildAiArchivePayload(item, records))
                            .put("pdf_url", JSONObject.NULL)
                    postJson(ApiConfig.endpoint("/api/v1/reports"), body)
                }
                reportItems.size
            }.onSuccess { savedCount ->
                mainHandler.post {
                    reportArchiveStatusMessage = "已保存${savedCount}条AI报告归档"
                    reportArchiveLoadedChildId = null
                    reportTab = 2
                    Toast.makeText(this, reportArchiveStatusMessage, Toast.LENGTH_SHORT).show()
                    loadReportArchives(force = true)
                    render()
                }
            }.onFailure {
                mainHandler.post {
                    reportArchiveStatusMessage = "AI报告归档保存失败，请稍后重试"
                    Toast.makeText(this, reportArchiveStatusMessage, Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
    }

    private fun archiveVisitReport() {
        if (visitWearCloudLoading || skinCloudLoading || growthCloudLoading || imagingCloudLoading) {
            Toast.makeText(this, "复诊报告数据仍在读取，请稍候", Toast.LENGTH_SHORT).show()
            return
        }
        val reportRecords = visitReportWearRecords()
        val body =
            JSONObject()
                .put("child_id", child.id)
                .put("kind", "visit_report")
                .put("period_start", reportRecords.firstOrNull()?.date?.toString() ?: JSONObject.NULL)
                .put("period_end", reportRecords.lastOrNull()?.date?.toString() ?: JSONObject.NULL)
                .put("payload_json", buildVisitReportArchivePayload(reportRecords))
                .put("pdf_url", JSONObject.NULL)
        reportArchiveStatusMessage = "正在生成复诊报告归档..."
        Toast.makeText(this, reportArchiveStatusMessage, Toast.LENGTH_SHORT).show()
        thread(name = "SpinecareVisitArchiveSave") {
            runCatching {
                postJson(ApiConfig.endpoint("/api/v1/reports"), body)
            }.onSuccess { response ->
                mainHandler.post {
                    selectedArchivedReportId = response.optString("id").takeIf { it.isNotBlank() }
                    reportArchiveStatusMessage = "复诊报告已归档，PDF及微信分享文件生成服务待接入"
                    reportArchiveLoadedChildId = null
                    reportTab = 2
                    Toast.makeText(this, "复诊报告已归档", Toast.LENGTH_SHORT).show()
                    loadReportArchives(force = true)
                    render()
                }
            }.onFailure {
                mainHandler.post {
                    reportArchiveStatusMessage = "复诊报告归档失败，请稍后重试"
                    Toast.makeText(this, reportArchiveStatusMessage, Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
    }

    private fun buildVisitReportArchivePayload(records: List<WearRecord>): JSONObject =
        JSONObject()
            .put("title", "Spinecare Mom 复诊报告")
            .put("report_type", "复诊报告")
            .put("child_id", child.id)
            .put("generated_at", LocalDateTime.now().toString())
            .put("period_label", visitReportPeriodLabel(records))
            .put("basic_info", visitReportBasicInfo())
            .put("wear_summary", visitReportWearSummary(records))
            .put("wear_all_summary", visitReportWearAllSummary())
            .put("gap_summary", visitReportGapSummary(records))
            .put("skin_summary", visitReportSkinSummary())
            .put("growth_summary", visitReportGrowthSummary())
            .put("imaging_summary", visitReportImagingSummary())
            .put("advice", visitReportAiAdvice(records))
            .put("daily_records", dailyWearSnapshot(records))

    private fun buildAiArchivePayload(item: AiReportItem, records: List<WearRecord>): JSONObject =
        JSONObject()
            .put("title", item.title)
            .put("report_type", archiveReportKindLabel(aiArchiveKindFor(item)))
            .put("child_id", child.id)
            .put("generated_at", LocalDateTime.now().toString())
            .put("status", item.status)
            .put("summary", item.subtitle)
            .put("basic_info", visitReportBasicInfo())
            .put("wear_summary", if (records.isEmpty()) "暂无佩戴记录。" else visitReportWearSummary(records))
            .put("gap_summary", if (records.isEmpty()) "暂无小时佩戴明细。" else visitReportGapSummary(records))
            .put("skin_summary", visitReportSkinSummary())
            .put("growth_summary", visitReportGrowthSummary())
            .put("imaging_summary", visitReportImagingSummary())
            .put("weekly_summary", JSONArray(buildWeeklyAiSummary()))
            .put("daily_records", dailyWearSnapshot(records))

    private fun dailyWearSnapshot(records: List<WearRecord>): JSONArray {
        val rows = JSONArray()
        records.forEach { record ->
            rows.put(
                JSONObject()
                    .put("date", record.date.toString())
                    .put("worn_hours", roundOne(record.wornHours))
                    .put("prescribed_hours", prescribedWearHours())
                    .put("compliant", isWearCompliant(record)),
            )
        }
        return rows
    }

    private fun aiArchiveRecordsFor(item: AiReportItem): List<WearRecord> =
        when (aiArchiveKindFor(item)) {
            "ai_weekly" -> wearCloudRecords.takeLast(7)
            else -> wearCloudRecords.takeLast(30)
        }

    private fun aiArchiveKindFor(item: AiReportItem): String =
        when {
            item.title.contains("异常") -> "ai_abnormal"
            item.title.contains("30天") -> "ai_30day"
            else -> "ai_weekly"
        }

    private fun archiveReportKindLabel(kind: String): String =
        when (kind) {
            "ai_weekly" -> "周报"
            "ai_30day" -> "30天报告"
            "ai_monthly" -> "30天报告"
            "ai_abnormal" -> "异常报告"
            "visit_report" -> "复诊报告"
            "visit" -> "复诊报告"
            else -> "报告"
        }

    private fun archiveReportStatus(item: ArchivedReport): Pair<String, Int> =
        when {
            item.pdfUrl != null -> "PDF" to P.primary
            item.payload.cleanString("status") == "红色" -> "红色" to P.danger
            item.payload.cleanString("status") == "需关注" -> "需关注" to P.warning
            else -> "已归档" to P.success
        }

    private fun archivePeriodText(item: ArchivedReport): String {
        val start = item.periodStart
        val end = item.periodEnd
        return when {
            start != null && end != null && start == end -> formatDateCn(start)
            start != null && end != null -> "${formatDateCn(start)}-${formatDateCn(end)}"
            else -> item.payload.cleanString("period_label") ?: "暂无周期"
        }
    }

    private fun archiveCreatedAtText(value: String): String =
        value.replace("T", " ").substringBefore(".").take(16).ifBlank { "未知" }

    private fun jsonArrayText(rows: JSONArray): String =
        (0 until rows.length()).joinToString("；") { index -> rows.optString(index) }

    private fun JSONObject.cleanString(key: String): String? =
        optString(key, "").trim().takeIf { it.isNotBlank() && it != "null" }

    private fun visitReportWearRecords(): List<WearRecord> {
        val records = visitWearRecords.ifEmpty { wearCloudRecords }
        return if (records.size >= 90) records.takeLast(90) else records
    }

    private fun visitReportPeriodLabel(records: List<WearRecord>): String =
        when {
            visitWearCloudLoading && records.isEmpty() -> "读取中"
            records.isEmpty() -> "暂无佩戴记录"
            records.size >= 90 -> "最近90天"
            else -> "佩戴至今"
        }

    private fun visitReportBasicInfo(): String =
        "${child.nickname}，${profileAgeText()}，${child.gender}，${child.curveType}，初始Cobb ${child.cobb}°，Risser ${child.risser}，${child.braceType}，医嘱${child.prescribedHours}h/天，初诊${child.firstVisitDate}。"

    private fun visitReportWearSummary(records: List<WearRecord>): String {
        if (records.isEmpty()) {
            return if (visitWearCloudLoading) "正在读取佩戴记录..." else "暂无佩戴记录。"
        }
        val summary = summaryFromWearRecords(records)
        return "${visitReportPeriodLabel(records)}平均${formatHours(summary.avgHours)}h/天，达标${records.count { isWearCompliant(it) }}/${records.size}天，达标率${summary.complianceRate}%，达标标准${formatHours(summary.prescribedHours)}h/天。"
    }

    private fun visitReportWearAllSummary(): String {
        val allRecords = visitWearRecords.ifEmpty { wearCloudRecords }
        if (allRecords.isEmpty()) {
            return if (visitWearCloudLoading) "正在读取佩戴记录..." else "数据库暂无佩戴记录。"
        }
        val first = allRecords.first().date
        val last = allRecords.last().date
        val calendarDays = java.time.temporal.ChronoUnit.DAYS.between(first, last).toInt() + 1
        return "数据库记录自${formatDateCn(first)}至${formatDateCn(last)}，共${allRecords.size}天佩戴记录，覆盖${calendarDays.coerceAtLeast(allRecords.size)}个自然日。"
    }

    private fun visitReportGapSummary(records: List<WearRecord>): String {
        if (records.isEmpty()) {
            return "暂无小时佩戴明细。"
        }
        val gaps = topGapHours(records)
        return if (gaps.isEmpty()) {
            "暂无明显固定缺口时段。"
        } else {
            "主要缺口集中在${gaps.joinToString("、")}。"
        }
    }

    private fun visitReportSkinSummary(): String =
        if (skinLogs.isEmpty()) {
            if (skinCloudLoading) "正在读取皮肤记录..." else "暂无皮肤问题记录。"
        } else {
            skinLogs.take(3).joinToString("；") { item ->
                "${formatDateShort(item.date)} ${item.region}${item.status}${if (item.photos.isNotEmpty()) "，照片${item.photos.size}张" else ""}${item.note.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()}"
            }
        }

    private fun visitReportGrowthSummary(): String {
        val delta = growthDeltaWithinDays(31)
        val latest = growthLogs.maxByOrNull { it.date }
        return when {
            latest == null && growthCloudLoading -> "正在读取生长记录..."
            latest == null -> "暂无生长记录。"
            delta != null && delta >= 1.3 -> "最近${formatDateShort(latest.date)}身高${formatHours(latest.heightCm)}cm，近1个月增加${formatHours(delta)}cm，超过1.3cm，建议评估支具适配。"
            delta != null -> "最近${formatDateShort(latest.date)}身高${formatHours(latest.heightCm)}cm，近1个月增加${formatHours(delta)}cm。"
            else -> "最近${formatDateShort(latest.date)}身高${formatHours(latest.heightCm)}cm。"
        }
    }

    private fun visitReportImagingSummary(): String =
        if (imagingLogs.isEmpty()) {
            if (imagingCloudLoading) "正在读取影像档案..." else "暂无影像档案。"
        } else {
            imagingLogs.take(3).joinToString("；") { item ->
                "${formatDateShort(item.shotDate)} ${item.imageType}${item.note.takeIf { it.isNotBlank() }?.let { "，$it" }.orEmpty()}"
            }
        }

    private fun visitReportAiAdvice(records: List<WearRecord>): String {
        val advice = mutableListOf<String>()
        val target = prescribedWearHours()
        val severeThreshold = roundOne(target * 0.6)
        val severeStreak = longestStreakWhere(records) { target > 0.0 && it.wornHours < severeThreshold }
        val lowStreak = longestStreakWhere(records) { target > 0.0 && it.wornHours < target * 0.8 }
        val summary = summaryFromWearRecords(records)
        when {
            records.isEmpty() -> advice += "请先完成蓝牙读取并上传佩戴数据。"
            summary.complianceRate < 60 -> advice += "佩戴达标率偏低，建议复诊时说明影响佩戴的具体原因。"
            summary.complianceRate < 80 -> advice += "佩戴达标率一般，建议讨论固定缺口时段的补足方案。"
            else -> advice += "佩戴依从性较好，建议继续保持并定期复查。"
        }
        if (severeStreak >= 3) {
            advice += "存在连续${severeStreak}天低于医嘱60%，建议重点沟通。"
        } else if (lowStreak >= 3) {
            advice += "存在连续${lowStreak}天低于医嘱80%，建议排查作息或不适因素。"
        }
        if (skinLogs.any { it.status !in listOf("正常", "无异常") }) {
            advice += "携带皮肤问题照片和备注，请医生或支具师评估压迫点。"
        }
        growthDeltaWithinDays(31)?.takeIf { it >= 1.3 }?.let {
            advice += "近1个月身高增长较快，建议评估支具适配。"
        }
        if (imagingLogs.isNotEmpty()) {
            advice += "携带最近影像资料用于对比。"
        }
        return advice.distinct().take(4).joinToString("；")
    }

    private fun visitReportChartLabels(records: List<WearRecord>): Map<Int, String> {
        if (records.isEmpty()) return emptyMap()
        val step = when {
            records.size > 60 -> 30
            records.size > 35 -> 15
            records.size > 14 -> 7
            else -> 3
        }
        return records.indices
            .filter { it == 0 || it == records.lastIndex || it % step == 0 }
            .associateWith { index -> formatDateShort(records[index].date) }
    }

    private fun loadVisitReportWearData(force: Boolean = false) {
        val targetChildId = child.id
        if (!force && (visitWearCloudLoading || visitWearCloudLoadedChildId == targetChildId || visitWearCloudLastRequestChildId == targetChildId)) {
            return
        }
        visitWearCloudLoading = true
        visitWearCloudLastRequestChildId = targetChildId
        visitWearCloudStatusMessage = "正在读取复诊报告佩戴记录..."
        thread(name = "SpinecareVisitWearFetch") {
            runCatching {
                val encodedChildId = urlEncode(targetChildId)
                parseWearRecords(getJson(ApiConfig.endpoint("/api/v1/wear/records?child_id=$encodedChildId&days=3650")))
            }.onSuccess { records ->
                mainHandler.post {
                    if (targetChildId != child.id) {
                        visitWearCloudLoading = false
                        return@post
                    }
                    visitWearRecords = records
                    visitWearCloudLoadedChildId = targetChildId
                    visitWearCloudLoading = false
                    visitWearCloudStatusMessage = null
                    if (stage == Stage.App && currentTab == MainTab.Reports) {
                        render()
                    }
                }
            }.onFailure {
                mainHandler.post {
                    if (targetChildId != child.id) {
                        visitWearCloudLoading = false
                        return@post
                    }
                    visitWearCloudLoading = false
                    visitWearCloudStatusMessage = "复诊报告佩戴记录读取失败，请稍后重试"
                    if (stage == Stage.App && currentTab == MainTab.Reports) {
                        render()
                    }
                }
            }
        }
    }

    private fun LinearLayout.addLogsPage() {
        logsTab = logsTab.coerceIn(0, 2)
        addView(appHeader("记录", "皮肤、生长与影像档案", showBell = true, showBack = true, backTarget = MainTab.Home))
        addView(segmented(listOf("皮肤", "生长", "影像"), logsTab) { index ->
            logsTab = index
            selectedSkinLogId = null
            selectedImagingLogId = null
            render()
        })
        addSpace(12)
        when (logsTab) {
            0 -> addSkinLog()
            1 -> addGrowthLog()
            else -> addImagingLog()
        }
    }

    private fun LinearLayout.addSkinLog() {
        loadSkinLogs()
        addView(
            card {
                addView(cardHeader("皮肤问题记录", "发现发红、疼痛、破皮等问题时记录", null))
                addView(infoStrip("不要求每天检查；仅在发现皮肤问题时拍照并填写记录，内容会进入复诊报告。"))
                addSpace(10)
                val regions = listOf("左腰部", "右腰部", "背部", "胸腹部", "肩部", "骨盆/髋部", "其他")
                addView(multiChoiceRow("问题部位", regions, skinRegionInputs) {
                    skinRegionInputs = toggleSelection(skinRegionInputs, it)
                    render()
                })
                addSpace(10)
                val statuses = listOf("发红", "瘙痒", "疼痛", "破皮", "水泡", "其他")
                addView(multiChoiceRow("问题类型", statuses, skinStatusInputs) {
                    skinStatusInputs = toggleSelection(skinStatusInputs, it)
                    render()
                })
                addSpace(10)
                addView(field("备注", skinNoteInput, InputType.TYPE_CLASS_TEXT) { skinNoteInput = it })
                addSpace(10)
                addView(field("照片编号/说明", skinPhotoRefInput, InputType.TYPE_CLASS_TEXT) { skinPhotoRefInput = it })
                addSpace(10)
                addView(
                    horizontal {
                        addView(secondaryButton("拍照") {
                            runWithCameraPermission {
                                skinPhotoLauncher.launch(null)
                            }
                        }, weightLp(1f))
                        addSpace(10, horizontal = true)
                        addView(primaryButton("保存记录") {
                            saveSkinLogToCloud()
                        }, weightLp(1f))
                    },
                )
                skinCapturedPhoto?.let { photo ->
                    addSpace(10)
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageBitmap(photo)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = rounded(P.surfaceAlt, dp(8), P.softLine)
                        },
                        matchHeightLp(dp(128)),
                    )
                }
                skinCloudStatusMessage?.let {
                    addSpace(10)
                    addView(infoStrip(it))
                }
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("历史皮肤问题", if (skinLogs.isEmpty()) "暂无记录" else "${skinLogs.size} 条记录", null))
                if (skinLogs.isEmpty()) {
                    addView(infoStrip(if (skinCloudLoading) "正在读取皮肤记录..." else "保存后将在这里显示。"))
                } else {
                    skinLogs.take(6).forEachIndexed { index, item ->
                        if (index > 0) addSpace(8)
                        addView(skinHistoryRow(item))
                        if (selectedSkinLogId == item.id) {
                            addSpace(8)
                            addSkinLogDetail(item)
                        }
                    }
                }
            },
        )
    }

    private fun skinHistoryRow(item: SkinLog): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = rounded(
                if (selectedSkinLogId == item.id) adjustAlpha(P.danger, 0.08f) else P.surfaceAlt,
                dp(8),
                if (selectedSkinLogId == item.id) adjustAlpha(P.danger, 0.22f) else P.softLine,
            )
            setPadding(dp(12), dp(10), dp(12), dp(10))
            val photoText = if (item.photos.isNotEmpty()) " · 照片${item.photos.size}张" else ""
            addView(
                vertical {
                    addView(label("${formatDateShort(item.date)} · ${item.region}", 15f, P.text, Typeface.BOLD))
                    addView(label("${item.status}$photoText", 13f, P.secondary))
                },
                weightLp(1f),
            )
            addView(label(if (selectedSkinLogId == item.id) "收起" else "详情", 13f, P.danger, Typeface.BOLD, Gravity.CENTER))
            setOnClickListener {
                selectedSkinLogId = if (selectedSkinLogId == item.id) null else item.id
                render()
            }
        }

    private fun LinearLayout.addSkinLogDetail(item: SkinLog) {
        addView(
            vertical {
                background = rounded(P.surface, dp(8), adjustAlpha(P.danger, 0.2f))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                addView(rowText("记录时间", formatDateShort(item.date)))
                addSpace(8)
                addView(rowText("问题部位", item.region))
                addSpace(8)
                addView(rowText("问题类型", item.status))
                addSpace(8)
                addView(label("文字记录", 13f, P.text, Typeface.BOLD))
                addSpace(4)
                addView(label(item.note.ifBlank { "未填写备注" }, 14f, P.secondary).apply {
                    background = rounded(P.surfaceAlt, dp(8), P.softLine)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                })
                addSpace(10)
                addView(label("照片", 13f, P.text, Typeface.BOLD))
                addSpace(6)
                if (item.photos.isEmpty()) {
                    addView(infoStrip("本条记录未保存照片。"))
                } else {
                    item.photos.forEachIndexed { index, photo ->
                        if (index > 0) addSpace(8)
                        addView(skinPhotoItem(photo))
                    }
                }
            },
        )
    }

    private fun skinPhotoItem(photoRef: String): View =
        vertical {
            val trimmed = photoRef.trim()
            if (isLoadableSkinPhoto(trimmed)) {
                val cached = skinPhotoCache[trimmed]
                if (cached != null) {
                    addView(
                        ImageView(this@MainActivity).apply {
                            setImageBitmap(cached)
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = rounded(P.surfaceAlt, dp(8), P.softLine)
                        },
                        matchHeightLp(dp(150)),
                    )
                } else {
                    if (!skinPhotoCache.containsKey(trimmed)) {
                        loadSkinPhoto(trimmed)
                    }
                    addView(
                        label(
                            if (skinPhotoLoading.contains(trimmed)) "正在加载照片..." else "照片暂无法预览",
                            14f,
                            P.secondary,
                            Typeface.BOLD,
                            Gravity.CENTER,
                        ).apply {
                            background = rounded(P.surfaceAlt, dp(8), P.softLine)
                        },
                        matchHeightLp(dp(90)),
                    )
                }
                addSpace(5)
            }
            addView(label(trimmed.ifBlank { "照片记录" }, 12f, P.muted))
        }

    private fun isLoadableSkinPhoto(photoRef: String): Boolean {
        val lower = photoRef.lowercase(Locale.US)
        return lower.startsWith("http://") ||
            lower.startsWith("https://") ||
            lower.endsWith(".jpg") ||
            lower.endsWith(".jpeg") ||
            lower.endsWith(".png") ||
            lower.endsWith(".webp")
    }

    private fun skinPhotoUrl(photoRef: String): String =
        if (photoRef.startsWith("http://", ignoreCase = true) || photoRef.startsWith("https://", ignoreCase = true)) {
            photoRef
        } else {
            ApiConfig.endpoint("/api/v1/uploads/${urlEncode(photoRef)}")
        }

    private fun loadSkinPhoto(photoRef: String) {
        if (skinPhotoLoading.contains(photoRef) || skinPhotoCache.containsKey(photoRef)) {
            return
        }
        skinPhotoLoading += photoRef
        thread(name = "SpinecareSkinPhotoFetch") {
            val bitmap =
                runCatching {
                    URL(skinPhotoUrl(photoRef)).openStream().use { input ->
                        BitmapFactory.decodeStream(input)
                    }
                }.getOrNull()
            mainHandler.post {
                skinPhotoLoading -= photoRef
                skinPhotoCache[photoRef] = bitmap
                refreshRecordsUi()
            }
        }
    }

    private fun LinearLayout.addGrowthLog() {
        loadGrowthLogs()
        val latestGrowth = growthLogs.firstOrNull()
        val previousGrowth = growthLogs.drop(1).firstOrNull()
        val growthDelta = if (latestGrowth != null && previousGrowth != null) roundOne(latestGrowth.heightCm - previousGrowth.heightCm) else null
        addView(
            card {
                addView(
                    cardHeader(
                        "生长记录",
                        latestGrowth?.let {
                            val deltaText = growthDelta?.let { delta -> " · 较上次${if (delta >= 0) "+" else ""}${formatHours(delta)}cm" }.orEmpty()
                            "最近 ${formatDateShort(it.date)} · ${formatHours(it.heightCm)}cm$deltaText"
                        } ?: "录入身高后保存记录",
                        growthDelta?.takeIf { it > 1.0 }?.let { chip("需关注", P.warning) },
                    ),
                )
                addView(
                    horizontal {
                        gravity = Gravity.BOTTOM
                        addView(field("身高(cm)", growthHeightInput, InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL) {
                            growthHeightInput = it
                        }, weightLp(1f))
                        addSpace(10, horizontal = true)
                        addView(primaryButton("录入") {
                            saveGrowthLogToCloud()
                        }, widthHeightLp(dp(82), dp(46)))
                    },
                )
                addSpace(10)
                addView(
                    infoStrip(
                        latestGrowth?.let {
                            "上一次录入：${formatDateShort(it.date)}，身高 ${formatHours(it.heightCm)}cm"
                        } ?: if (growthCloudLoading) {
                            "正在读取上一次身高录入记录..."
                        } else {
                            "暂无上一次身高录入记录"
                        },
                    ),
                )
                addSpace(10)
                addView(field("备注", growthNoteInput, InputType.TYPE_CLASS_TEXT) { growthNoteInput = it })
                growthCloudStatusMessage?.let {
                    addSpace(10)
                    addView(infoStrip(it))
                }
                addSpace(12)
                addView(GrowthChartView(this@MainActivity, growthLogs), matchHeightLp(dp(190)))
                addView(infoStrip("身高增长较快时，复诊时可请医生或支具师评估支具适配。"))
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("历史生长记录", if (growthLogs.isEmpty()) "暂无记录" else "${growthLogs.size} 条记录", null))
                if (growthLogs.isEmpty()) {
                    addView(infoStrip(if (growthCloudLoading) "正在读取生长记录..." else "录入后将在这里显示。"))
                } else {
                    growthLogs.take(5).forEachIndexed { index, item ->
                        if (index > 0) addSpace(8)
                        addView(settingRow(formatDateShort(item.date), "${formatHours(item.heightCm)}cm${item.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()}", "记录"))
                    }
                }
            },
        )
    }

    private fun LinearLayout.addImagingLog() {
        loadImagingLogs()
        addView(
            card {
                addView(cardHeader("影像录入", "X光、站立体态照、Adams前屈照", null))
                val imageTypes = listOf("X光", "站立体态照", "Adams前屈照", "其他")
                addView(choiceRow("影像类型", imageTypes, imageTypes.indexOf(imagingTypeInput).coerceAtLeast(0)) {
                    imagingTypeInput = it
                    render()
                })
                addSpace(10)
                addView(field("拍摄日期", imagingDateInput, InputType.TYPE_CLASS_DATETIME) { imagingDateInput = it })
                addSpace(10)
                addView(field("文件地址/编号", imagingFileUrlInput, InputType.TYPE_CLASS_TEXT) { imagingFileUrlInput = it })
                addSpace(10)
                addView(
                    horizontal {
                        addView(secondaryButton("拍照") {
                            runWithCameraPermission {
                                imagingPhotoLauncher.launch(null)
                            }
                        }, weightLp(1f))
                        addSpace(10, horizontal = true)
                        addView(secondaryButton("从图库选择") {
                            imagingGalleryLauncher.launch("image/*")
                        }, weightLp(1f))
                    },
                )
                if (imagingCapturedPhoto != null || imagingSelectedPhotoUri != null) {
                    addSpace(10)
                    addView(imagingSelectedPreview(), matchHeightLp(dp(150)))
                }
                addSpace(10)
                addView(field("备注", imagingNoteInput, InputType.TYPE_CLASS_TEXT) { imagingNoteInput = it })
                imagingCloudStatusMessage?.let {
                    addSpace(10)
                    addView(infoStrip(it))
                }
                addSpace(12)
                addView(primaryButton("保存影像记录") {
                    saveImagingLogToCloud()
                }, matchLp())
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("影像档案", if (imagingLogs.isEmpty()) "暂无记录" else "${imagingLogs.size} 条记录", null))
                if (imagingLogs.isEmpty()) {
                    addView(infoStrip(if (imagingCloudLoading) "正在读取影像档案..." else "录入后将在这里显示。"))
                } else {
                    imagingLogs.take(6).forEachIndexed { index, item ->
                        if (index > 0) addSpace(8)
                        addView(imagingHistoryRow(item))
                        if (selectedImagingLogId == item.id) {
                            addSpace(8)
                            addImagingLogDetail(item)
                        }
                    }
                }
            },
        )
    }

    private fun imagingSelectedPreview(): View =
        ImageView(this).apply {
            imagingCapturedPhoto?.let { setImageBitmap(it) }
            if (imagingCapturedPhoto == null) {
                imagingSelectedPhotoUri?.let { setImageURI(it) }
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
        }

    private fun imagingHistoryRow(item: ImagingLog): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            background = rounded(
                if (selectedImagingLogId == item.id) adjustAlpha(P.primary, 0.08f) else P.surfaceAlt,
                dp(8),
                if (selectedImagingLogId == item.id) adjustAlpha(P.primary, 0.22f) else P.softLine,
            )
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(
                TextView(this@MainActivity).apply {
                    text = uiText("片")
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(P.primary)
                    background = rounded(P.primaryLight, dp(8), null)
                },
                widthHeightLp(dp(52), dp(52)),
            )
            addSpace(10, horizontal = true)
            addView(
                vertical {
                    addView(label("${item.imageType} · ${formatDateShort(item.shotDate)}", 15f, P.text, Typeface.BOLD))
                    addView(label(listOfNotNull(item.note.takeIf { it.isNotBlank() }, item.fileUrl?.takeIf { it.isNotBlank() }).joinToString(" · ").ifBlank { "已保存" }, 13f, P.secondary))
                },
                weightLp(1f),
            )
            addView(label(if (selectedImagingLogId == item.id) "收起" else "详情", 13f, P.primary, Typeface.BOLD, Gravity.CENTER))
            setOnClickListener {
                selectedImagingLogId = if (selectedImagingLogId == item.id) null else item.id
                render()
            }
        }

    private fun LinearLayout.addImagingLogDetail(item: ImagingLog) {
        addView(
            vertical {
                background = rounded(P.surface, dp(8), adjustAlpha(P.primary, 0.2f))
                setPadding(dp(12), dp(12), dp(12), dp(12))
                addView(rowText("影像类型", item.imageType))
                addSpace(8)
                addView(rowText("拍摄日期", formatDateShort(item.shotDate)))
                addSpace(8)
                addView(label("文件地址/编号", 13f, P.text, Typeface.BOLD))
                addSpace(4)
                addView(label(item.fileUrl?.takeIf { it.isNotBlank() } ?: "未填写", 14f, P.secondary).apply {
                    background = rounded(P.surfaceAlt, dp(8), P.softLine)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                })
                item.fileUrl?.takeIf { isLoadableSkinPhoto(it) }?.let { imageRef ->
                    addSpace(8)
                    addView(label("影像预览", 13f, P.text, Typeface.BOLD))
                    addSpace(4)
                    addView(skinPhotoItem(imageRef))
                }
                addSpace(8)
                addView(label("备注", 13f, P.text, Typeface.BOLD))
                addSpace(4)
                addView(label(item.note.ifBlank { "未填写备注" }, 14f, P.secondary).apply {
                    background = rounded(P.surfaceAlt, dp(8), P.softLine)
                    setPadding(dp(10), dp(9), dp(10), dp(9))
                })
            },
        )
    }

    private fun LinearLayout.addMePage() {
        loadAlerts()
        addView(appHeader("我的", "档案、设备与隐私", showBell = true))
        addView(
            card {
                addView(
                    horizontal {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(avatar(child.nickname.take(1).ifBlank { "孩" }))
                        addSpace(12, horizontal = true)
                        addView(
                            vertical {
                                addView(label(child.nickname, 18f, P.text, Typeface.BOLD))
                                addView(label("${profileAgeText()} · ${child.curveType} · 医嘱${child.prescribedHours}h/天", 13f, P.secondary))
                            },
                            weightLp(1f),
                        )
                        addView(secondaryButton("编辑") {
                            currentTab = MainTab.ProfileEdit
                            render()
                        }, widthLp(dp(76)))
                    },
                )
            },
        )
        addSpace(12)
        addView(
            card {
                addView(settingRow("消息中心", alertCenterSummaryText(), "查看") {
                    alertsBackTarget = MainTab.Me
                    currentTab = MainTab.Alerts
                    render()
                })
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("孩子模式", "青少年向成就视图", chip("成就", P.primary)))
                addView(label("根据云端真实佩戴数据生成今日进度、连续达标和阶段徽章，适合孩子自己查看。", 15f, P.secondary))
                addSpace(12)
                addView(primaryButton("进入孩子模式") {
                    currentTab = MainTab.ChildMode
                    render()
                }, matchLp())
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("隐私与同意", "未成年人健康数据保护", null))
                addSpace(10)
                addView(privacyActionRow("导出数据", "导出佩戴、记录与报告数据", "出", P.primary) {
                    currentTab = MainTab.DataExport
                    render()
                })
                addSpace(10)
                addView(privacyActionRow("删除全部数据", "申请删除本账号相关健康数据", "删", P.danger) {
                    currentTab = MainTab.DataDelete
                    render()
                })
                addSpace(10)
                addView(privacyActionRow("帮助中心", "查看使用帮助、协议与隐私政策", "?", 0xFF4C6FB8.toInt()) {
                    helpSection = HelpSection.Home
                    helpFaqExpandedIndex = null
                    currentTab = MainTab.HelpCenter
                    render()
                })
            },
        )
        addSpace(12)
        addView(dangerButton("退出登录") {
            stage = Stage.Login
            currentTab = MainTab.Home
            profileStep = 1
            render()
        }, matchLp())
    }

    private fun LinearLayout.addDataExportPage() {
        addView(appHeader("导出数据", "备份后再删除数据", showBell = false, showBack = true, backTarget = MainTab.Me))
        addSpace(12)
        addView(
            card {
                addView(cardHeader("功能目的", "导出是一份离线备份", chip("备份", P.primary)))
                addInsight("导出数据用于在删除云端数据前保存一份完整备份，避免误删后无法追溯。")
                addInsight("导出不会自动删除云端数据；请确认备份文件已保存并能打开后，再执行删除数据。")
                addInsight("备份文件包含健康相关数据，请保存到可信位置，不建议随意转发。")
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("备份内容", "来自云端数据库与本机基础设置", null))
                addView(
                    metricGrid(
                        listOf(
                            "建档" to "昵称/病情/医嘱",
                            "佩戴" to "每日与小时记录",
                            "记录" to "皮肤/生长/影像",
                            "报告" to "AI/复诊/归档",
                            "预警" to "消息与风险提示",
                            "本机" to "语言/绑定设备",
                        ),
                        columns = 2,
                        itemHeightDp = 72,
                        valueSize = 14f,
                        labelSize = 12f,
                    ),
                )
            },
        )
        addSpace(12)
        addView(
            card {
                val backupText = lastDataExportTimeText()
                val statusChip =
                    when {
                        dataExportInProgress -> chip("生成中", P.warning)
                        backupText != "暂无备份记录" -> chip("已备份", P.success)
                        else -> chip("未备份", P.muted)
                    }
                addView(cardHeader("生成备份文件", "最近备份：$backupText", statusChip))
                dataExportStatusMessage?.let {
                    addSpace(8)
                    addView(infoStrip(it))
                }
                lastDataExportSummary?.let {
                    addSpace(8)
                    addView(infoStrip(it))
                }
                addSpace(12)
                addView(
                    primaryButton(if (dataExportInProgress) "正在生成备份..." else "生成备份文件") {
                        startDataExport()
                    }.apply {
                        isEnabled = !dataExportInProgress
                    },
                    matchLp(),
                )
                addSpace(10)
                addView(infoStrip("文件将保存为 JSON 格式。保存完成后，请确认文件存在，再考虑删除数据。"))
            },
        )
    }

    private fun LinearLayout.addDataDeletePage() {
        loadDeleteRequest()
        addView(appHeader("删除全部数据", "先备份，后申请删除", showBell = false, showBack = true, backTarget = MainTab.Me))
        addSpace(12)
        addView(
            card {
                addView(cardHeader("删除范围", "删除后云端数据不可恢复", chip("高风险", P.danger)))
                addInsight("将删除当前孩子的建档、佩戴、皮肤、生长、影像、报告、预警、设备绑定及云端上传文件。")
                addInsight("删除申请提交后有24小时冷静期，冷静期内可以撤销。")
                addInsight("冷静期结束后仍需再次长按执行删除，系统不会自动误删。")
            },
        )
        addSpace(12)
        addView(
            card {
                val backupText = lastDataExportTimeText()
                val hasBackup = hasRecentDataExport()
                addView(cardHeader("备份检查", "最近备份：$backupText", chip(if (hasBackup) "已备份" else "未备份", if (hasBackup) P.success else P.warning)))
                addSpace(8)
                addView(infoStrip(if (hasBackup) "请确认备份文件已保存且可以打开，再继续删除流程。" else "未发现本机备份记录。请先导出数据，再回来申请删除。"))
                if (!hasBackup) {
                    addSpace(10)
                    addView(primaryButton("先导出数据") {
                        currentTab = MainTab.DataExport
                        render()
                    }, matchLp())
                }
            },
        )
        addSpace(12)
        addView(
            card {
                val request = dataDeleteRequest
                val status = request?.optString("status").orEmpty()
                addView(cardHeader("删除申请", deleteRequestSubtitle(request), deleteRequestChip(request)))
                dataDeleteStatusMessage?.let {
                    addSpace(8)
                    addView(infoStrip(it))
                }
                if (dataDeleteLoading) {
                    addSpace(8)
                    addView(infoStrip("正在读取删除申请状态..."))
                }
                if (request != null && status == "confirmed") {
                    addSpace(10)
                    addView(infoStrip("删除申请已提交。冷静期结束前可以撤销；冷静期结束后需再次输入确认文字并长按执行。"))
                    addSpace(10)
                    addView(field("再次输入确认文字“删除全部数据”", dataDeletePhraseInput, InputType.TYPE_CLASS_TEXT) {
                        dataDeletePhraseInput = it
                    })
                    addSpace(10)
                    addView(
                        horizontal {
                            addView(secondaryButton("撤销删除申请") {
                                cancelDeleteRequest()
                            }, weightLp(1f))
                            addSpace(10, horizontal = true)
                            addView(longPressDangerButton("长按执行删除", canExecuteDeleteRequest()) {
                                executeDeleteRequest()
                            }, weightLp(1f))
                        },
                    )
                } else if (request != null && status == "completed") {
                    addSpace(8)
                    addView(infoStrip("云端数据已删除。本机登录状态将在重新登录前保持清空。"))
                } else {
                    addSpace(10)
                    addView(deleteConfirmCheckRow("我已完成数据导出，并确认备份文件可打开", dataDeleteBackupChecked) {
                        dataDeleteBackupChecked = it
                        render()
                    })
                    addSpace(8)
                    addView(deleteConfirmCheckRow("我理解删除后云端数据无法恢复", dataDeleteIrreversibleChecked) {
                        dataDeleteIrreversibleChecked = it
                        render()
                    })
                    addSpace(8)
                    addView(deleteConfirmCheckRow("我确认删除的是当前孩子的全部云端数据", dataDeleteCurrentChildChecked) {
                        dataDeleteCurrentChildChecked = it
                        render()
                    })
                    addSpace(10)
                    addView(field("输入孩子昵称确认", dataDeleteNicknameInput, InputType.TYPE_CLASS_TEXT) {
                        dataDeleteNicknameInput = it
                    })
                    addSpace(10)
                    addView(field("输入确认文字“删除全部数据”", dataDeletePhraseInput, InputType.TYPE_CLASS_TEXT) {
                        dataDeletePhraseInput = it
                    })
                    addSpace(12)
                    addView(longPressDangerButton("长按申请删除", canSubmitDeleteRequest()) {
                        submitDeleteRequest()
                    }, matchLp())
                }
            },
        )
    }

    private fun LinearLayout.addHelpCenterPage() {
        val isHome = helpSection == HelpSection.Home
        val title =
            when (helpSection) {
                HelpSection.Home -> "帮助中心"
                HelpSection.Manual -> "用户手册"
                HelpSection.Faq -> "常见问题"
                HelpSection.Agreement -> "用户协议"
                HelpSection.Privacy -> "隐私政策"
                HelpSection.Contact -> "联系我们"
            }
        addView(
            appHeader(
                title,
                if (isHome) "使用说明、常见问题与隐私协议" else "帮助中心",
                showBell = false,
                showBack = true,
                backTarget = MainTab.Me,
                onBack = {
                    if (helpSection == HelpSection.Home) {
                        currentTab = MainTab.Me
                    } else {
                        helpSection = HelpSection.Home
                        helpFaqExpandedIndex = null
                    }
                    render()
                },
            ),
        )
        addSpace(12)
        when (helpSection) {
            HelpSection.Home -> addHelpHome()
            HelpSection.Manual -> addHelpManual()
            HelpSection.Faq -> addHelpFaq()
            HelpSection.Agreement -> addLegalDocument("用户协议", userAgreementSections())
            HelpSection.Privacy -> addLegalDocument("隐私政策", privacyPolicySections())
            HelpSection.Contact -> addContactUs()
        }
    }

    private fun LinearLayout.addHelpHome() {
        addView(
            card {
                addView(cardHeader("帮助中心", "请选择需要查看的内容", chip("离线可读", P.primary)))
                addSpace(10)
                addView(privacyActionRow("用户手册", "快速开始、建档、蓝牙、记录与报告", "册", P.primary) {
                    helpSection = HelpSection.Manual
                    render()
                })
                addSpace(10)
                addView(privacyActionRow("常见问题", "连接、数据、报告、导出与删除问题", "问", P.success) {
                    helpSection = HelpSection.Faq
                    helpFaqExpandedIndex = null
                    render()
                })
                addSpace(10)
                addView(privacyActionRow("用户协议", "服务说明、监护人责任和免责声明", "协", 0xFF4C6FB8.toInt()) {
                    helpSection = HelpSection.Agreement
                    render()
                })
                addSpace(10)
                addView(privacyActionRow("隐私政策", "数据收集、权限、导出与删除说明", "私", 0xFF7A5BB8.toInt()) {
                    helpSection = HelpSection.Privacy
                    render()
                })
                addSpace(10)
                addView(privacyActionRow("联系我们", "微信二维码与邮箱", "联", P.warning) {
                    helpSection = HelpSection.Contact
                    render()
                })
            },
        )
    }

    private fun LinearLayout.addHelpManual() {
        addView(
            card {
                addView(cardHeader("用户手册", "Spinecare Mom 使用流程", chip("手册", P.primary)))
                userManualSections().forEach { (heading, body) ->
                    addDocumentSection(heading, body)
                }
            },
        )
    }

    private fun LinearLayout.addHelpFaq() {
        addView(
            card {
                addView(cardHeader("常见问题", "点击问题查看答案", chip("${helpFaqItems().size}项", P.primary)))
                helpFaqItems().forEachIndexed { index, item ->
                    if (index > 0) addSpace(8)
                    addView(helpFaqRow(index, item.first, item.second))
                }
            },
        )
    }

    private fun LinearLayout.addLegalDocument(title: String, sections: List<Pair<String, String>>) {
        addView(
            card {
                addView(cardHeader(title, "Spinecare Mom 文档", chip("备查", P.primary)))
                sections.forEach { (heading, body) ->
                    addDocumentSection(heading, body)
                }
            },
        )
    }

    private fun LinearLayout.addContactUs() {
        addView(
            card {
                addView(cardHeader("联系我们", "微信二维码及邮箱", chip("已配置", P.success)))
                addSpace(10)
                addView(
                    vertical {
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(
                            ImageView(this@MainActivity).apply {
                                setImageResource(resources.getIdentifier("contact_wechat_qr", "drawable", packageName))
                                adjustViewBounds = true
                                scaleType = ImageView.ScaleType.FIT_CENTER
                                background = rounded(P.surface, dp(10), P.line)
                                setPadding(dp(8), dp(8), dp(8), dp(8))
                            },
                            widthHeightLp(dp(220), dp(258)),
                        )
                        addSpace(8)
                        addView(label(localizedContactName(), 15f, P.text, Typeface.BOLD, Gravity.CENTER))
                        addView(label(localizedQrNotice(), 13f, P.secondary, gravity = Gravity.CENTER))
                    },
                )
                addSpace(14)
                addView(infoStrip(localizedCompanyLine()))
                addSpace(8)
                addView(infoStrip(localizedAddressLine()))
                addSpace(8)
                addView(infoStrip(localizedEmailLine()))
                addSpace(8)
                addView(infoStrip(localizedContactTips()))
            },
        )
    }

    private fun LinearLayout.addDocumentSection(heading: String, body: String) {
        addSpace(10)
        addView(label(heading, 15f, P.text, Typeface.BOLD))
        addSpace(4)
        addView(label(body, 14f, P.secondary).apply {
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(10), dp(9), dp(10), dp(9))
        })
    }

    private fun helpFaqRow(index: Int, question: String, answer: String): View =
        vertical {
            val expanded = helpFaqExpandedIndex == index
            isClickable = true
            isFocusable = true
            background = rounded(if (expanded) adjustAlpha(P.primary, 0.08f) else P.surfaceAlt, dp(8), if (expanded) adjustAlpha(P.primary, 0.24f) else P.softLine)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(
                horizontal {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label(question, 15f, if (expanded) P.primary else P.text, Typeface.BOLD), weightLp(1f))
                    addView(label(if (expanded) "收起" else "展开", 13f, if (expanded) P.primary else P.muted, Typeface.BOLD))
                },
            )
            if (expanded) {
                addSpace(8)
                addView(label(answer, 14f, P.secondary))
            }
            setOnClickListener {
                helpFaqExpandedIndex = if (expanded) null else index
                render()
            }
        }

    private fun userManualSections(): List<Pair<String, String>> =
        languagePairs(
            zh = listOf(
                "一、产品简介" to "Spinecare Mom 是用于脊柱侧弯支具佩戴管理的 Android APP，帮助监护人记录孩子建档信息、蓝牙支具传感器佩戴数据、皮肤问题、生长记录、影像档案、AI报告、复诊报告和归档资料。本APP由绍兴维脉科技有限公司提供服务。",
                "二、使用前准备" to "请准备 Android 手机、可用网络、WM-SP# 前缀蓝牙设备以及医生或支具师提供的建档信息。首次使用建议由监护人操作，并确认已了解隐私政策、用户协议和医疗免责声明。",
                "三、首次使用流程" to "打开APP后登录或注册，按建档向导完善昵称、性别、出生日期、Cobb角、弯曲部位、Risser征、医嘱佩戴时间、支具类型和初诊日期。建档信息会同步到云端，用于首页统计、报告和AI解读。",
                "四、蓝牙设备绑定" to "进入设置页，打开蓝牙连接，扫描名称以 WM-SP# 开头的设备。连接成功后APP会保存设备，下次打开时自动尝试连接上一次设备；如20秒未发现设备，会提示到设置界面重新扫描连接。",
                "五、读取佩戴数据" to "蓝牙连接成功后，APP会自动读取设备佩戴数据并上传云端。界面会提示“正在读取设备数据，请稍候”，完成后提示“数据读取完成”。上传成功后会自动清除设备端已存储的佩戴数据。",
                "六、首页数据解读" to "首页近30天佩戴和近7天佩戴均来自云端真实数据，达标标准取自建档中的医嘱佩戴时间。条形图、圆形达标率和智能解读会按达标程度分档提示风险。",
                "七、记录功能" to "皮肤记录用于发现发红、疼痛、破皮、水泡等问题时拍照和备注；问题部位和问题类型可多选。生长记录用于录入身高并查看趋势；影像档案用于拍照或从手机图库选择影像资料。相关内容会进入复诊报告。",
                "八、报告与归档" to "AI报告、复诊报告和归档均基于云端佩戴、皮肤、生长、影像及建档信息生成。归档会保存生成当时的数据快照，后续数据变化不会自动改写已归档报告。",
                "九、导出与删除" to "导出数据用于删除前备份。删除全部数据前必须先导出备份、勾选确认项、输入孩子昵称和确认文字，并经过冷静期，避免误删除。删除完成后云端数据不可恢复。",
                "十、语言与设置" to "设置页可切换中文、英文、日语、韩语、西班牙语、法语、德语，并可管理蓝牙连接。语言选择会保存到本机，后续打开APP时继续沿用。",
                "十一、医疗安全提示" to "本APP用于家庭佩戴管理和复诊资料整理，不提供诊断、处方或支具调整结论。出现疼痛、皮肤破损、麻木、呼吸不适或其他紧急情况时，应及时联系医生或支具师。",
                "十二、联系方式" to "运营主体：绍兴维脉科技有限公司；地址：浙江省绍兴市越城区袍中北路631号；邮箱：zclei@vip.sina.com。可在“联系我们”页面扫描微信二维码添加客服。",
            ),
            en = listOf(
                "Quick Start" to "After login, complete the profile wizard: nickname, gender, birth date, Cobb angle, curve location, Risser sign, prescribed wearing hours, brace type, and first visit date. The profile is synced to the cloud for the home page, reports, and AI interpretation.",
                "Bluetooth Device Binding" to "Open Settings, enter Bluetooth Connection, and scan for devices whose names start with WM-SP#. After a successful connection, the app saves the device and will try to reconnect to it next time.",
                "Read Wearing Data" to "After Bluetooth connects, the app automatically reads wearing data from the device and uploads it to the cloud. It shows Reading device data, please wait, and then Data reading complete.",
                "Home Data Insights" to "The 30-day wearing summary and 7-day wearing chart on the home page use real cloud data. The target standard comes from the prescribed wearing hours in the profile, and colors indicate risk levels.",
                "Record Features" to "Skin records are used for photos and notes when a problem is found. Growth records store height. Imaging archives save photos taken by the camera or selected from the phone gallery. These records are included in visit reports.",
                "Reports and Archive" to "AI reports, visit reports, and archives are generated from cloud wearing data, skin records, growth records, imaging records, and profile information. An archive keeps a snapshot from the generation time.",
                "Export and Delete" to "Export data before deletion as a backup. Before deleting all data, export a backup, tick the confirmation items, enter the child nickname and confirmation text, and wait through the cooling-off period.",
                "Language and Settings" to "Use Settings to switch language and manage Bluetooth connection. The selected language is saved locally and reused when the app opens again.",
            ),
            ja = listOf(
                "クイックスタート" to "ログイン後、プロフィール作成ウィザードでニックネーム、性別、生年月日、Cobb角、弯曲部位、Risser徴候、医師指示の装着時間、装具タイプ、初診日を入力します。情報はクラウドに同期され、ホーム、レポート、AI解釈に使われます。",
                "Bluetooth機器の登録" to "設定でBluetooth接続を開き、名前が WM-SP# で始まる機器をスキャンします。接続に成功すると機器が保存され、次回起動時に自動再接続を試みます。",
                "装着データの読み取り" to "Bluetooth接続後、アプリは機器内の装着データを自動で読み取りクラウドへアップロードします。画面にはデータ読み取り中の案内と完了案内が表示されます。",
                "ホームデータの見方" to "ホームの30日装着サマリーと7日装着グラフはクラウドの実データを使用します。達成基準はプロフィールの医師指示装着時間で、色分けでリスクを示します。",
                "記録機能" to "皮膚記録は問題発見時の写真とメモに使います。成長記録は身長を保存し、画像記録は撮影またはギャラリー選択の画像を保存します。これらは再診レポートに反映されます。",
                "レポートとアーカイブ" to "AIレポート、再診レポート、アーカイブは、クラウドの装着、皮膚、成長、画像、プロフィール情報から生成されます。アーカイブは生成時点のデータスナップショットを保存します。",
                "エクスポートと削除" to "削除前にデータをバックアップとしてエクスポートします。全データ削除には、バックアップ、確認項目、子どものニックネーム、確認文字、冷却期間が必要です。",
                "言語と設定" to "設定で言語切替とBluetooth接続管理ができます。選択した言語は端末に保存され、次回起動時も使用されます。",
            ),
            ko = listOf(
                "빠른 시작" to "로그인 후 프로필 마법사에서 닉네임, 성별, 생년월일, Cobb 각도, 만곡 부위, Risser 징후, 처방 착용 시간, 보조기 종류, 초진일을 입력합니다. 프로필은 클라우드에 동기화되어 홈, 보고서, AI 해석에 사용됩니다.",
                "Bluetooth 장치 등록" to "설정에서 Bluetooth 연결을 열고 이름이 WM-SP# 로 시작하는 장치를 스캔합니다. 연결에 성공하면 앱이 장치를 저장하고 다음 실행 시 자동 재연결을 시도합니다.",
                "착용 데이터 읽기" to "Bluetooth가 연결되면 앱은 장치의 착용 데이터를 자동으로 읽어 클라우드에 업로드합니다. 화면에는 장치 데이터 읽는 중 안내와 완료 안내가 표시됩니다.",
                "홈 데이터 해석" to "홈의 30일 착용 요약과 7일 착용 차트는 클라우드 실제 데이터를 사용합니다. 목표 기준은 프로필의 처방 착용 시간이며, 색상으로 위험 수준을 표시합니다.",
                "기록 기능" to "피부 기록은 문제가 발견될 때 사진과 메모를 남기는 기능입니다. 성장 기록은 키를 저장하고, 영상 기록은 촬영 또는 갤러리 선택 이미지를 저장합니다. 관련 내용은 재진 보고서에 포함됩니다.",
                "보고서와 보관" to "AI 보고서, 재진 보고서, 보관 자료는 클라우드 착용, 피부, 성장, 영상, 프로필 정보를 기반으로 생성됩니다. 보관은 생성 당시의 데이터 스냅샷을 저장합니다.",
                "내보내기와 삭제" to "삭제 전에 데이터를 백업으로 내보내십시오. 전체 데이터 삭제 전에는 백업, 확인 항목 체크, 아이 닉네임과 확인 문구 입력, 냉각 기간이 필요합니다.",
                "언어와 설정" to "설정에서 언어를 변경하고 Bluetooth 연결을 관리할 수 있습니다. 선택한 언어는 기기에 저장되어 다음 실행 시에도 적용됩니다.",
            ),
            es = listOf(
                "Inicio rápido" to "Después de iniciar sesión, completa el asistente de perfil: apodo, sexo, fecha de nacimiento, ángulo Cobb, zona de curva, signo Risser, horas prescritas, tipo de corsé y fecha de primera visita. El perfil se sincroniza con la nube para inicio, informes e interpretación de IA.",
                "Vinculación Bluetooth" to "Abre Ajustes, entra en Conexión Bluetooth y busca dispositivos cuyo nombre empiece por WM-SP#. Tras conectarse, la app guarda el dispositivo e intentará reconectarlo la próxima vez.",
                "Leer datos de uso" to "Después de conectar Bluetooth, la app lee automáticamente los datos de uso del dispositivo y los sube a la nube. La pantalla muestra que está leyendo datos y avisa cuando termina.",
                "Lectura de datos de inicio" to "El resumen de 30 días y el gráfico de 7 días usan datos reales de la nube. El estándar de cumplimiento viene de las horas prescritas del perfil y los colores indican niveles de riesgo.",
                "Funciones de registro" to "El registro de piel sirve para fotos y notas cuando aparece un problema. Crecimiento guarda la altura. Imagen guarda fotos tomadas con la cámara o elegidas de la galería. Todo se integra en el informe de revisión.",
                "Informes y archivo" to "Los informes de IA, de revisión y archivo se generan con datos de uso, piel, crecimiento, imagen y perfil guardados en la nube. El archivo conserva una instantánea del momento de generación.",
                "Exportar y eliminar" to "Exporta los datos como copia antes de eliminar. Para borrar todo, primero exporta una copia, marca las confirmaciones, introduce el apodo y el texto de confirmación, y espera el periodo de seguridad.",
                "Idioma y ajustes" to "En Ajustes puedes cambiar el idioma y gestionar Bluetooth. El idioma elegido se guarda localmente y se mantiene al abrir la app de nuevo.",
            ),
            fr = listOf(
                "Démarrage rapide" to "Après connexion, complétez l'assistant de dossier : surnom, sexe, date de naissance, angle de Cobb, zone de courbure, signe de Risser, heures de port prescrites, type d'orthèse et date de première consultation. Le dossier est synchronisé dans le cloud pour l'accueil, les rapports et l'analyse IA.",
                "Association Bluetooth" to "Ouvrez Paramètres, puis Connexion Bluetooth, et recherchez les appareils dont le nom commence par WM-SP#. Après connexion, l'application enregistre l'appareil et tentera de s'y reconnecter au prochain lancement.",
                "Lire les données de port" to "Après connexion Bluetooth, l'application lit automatiquement les données de port de l'appareil et les envoie au cloud. L'écran indique la lecture en cours puis la fin de la lecture.",
                "Analyse de l'accueil" to "Le résumé sur 30 jours et le graphique sur 7 jours utilisent les données réelles du cloud. Le seuil vient des heures prescrites dans le dossier, et les couleurs indiquent le niveau de risque.",
                "Fonctions de suivi" to "Le suivi de peau sert aux photos et notes lorsqu'un problème apparaît. Le suivi de croissance enregistre la taille. Le dossier d'imagerie conserve les photos prises ou choisies dans la galerie. Ces éléments sont intégrés au rapport de suivi.",
                "Rapports et archives" to "Les rapports IA, rapports de suivi et archives sont générés à partir des données cloud de port, peau, croissance, imagerie et dossier. Une archive conserve l'instantané au moment de la génération.",
                "Exporter et supprimer" to "Exportez les données avant suppression comme sauvegarde. Avant de tout supprimer, exportez une copie, cochez les confirmations, saisissez le surnom de l'enfant et le texte demandé, puis respectez la période de sécurité.",
                "Langue et paramètres" to "Les paramètres permettent de changer de langue et de gérer Bluetooth. La langue choisie est enregistrée localement et réutilisée à la prochaine ouverture.",
            ),
            de = listOf(
                "Schnellstart" to "Nach der Anmeldung füllen Sie den Profilassistenten aus: Spitzname, Geschlecht, Geburtsdatum, Cobb-Winkel, Krümmungsbereich, Risser-Zeichen, verordnete Tragezeit, Orthesentyp und Datum der Erstuntersuchung. Das Profil wird mit der Cloud synchronisiert und für Startseite, Berichte und KI-Auswertung genutzt.",
                "Bluetooth-Gerät koppeln" to "Öffnen Sie Einstellungen und Bluetooth-Verbindung, und suchen Sie nach Geräten, deren Name mit WM-SP# beginnt. Nach erfolgreicher Verbindung speichert die App das Gerät und versucht beim nächsten Start eine automatische Wiederverbindung.",
                "Tragedaten lesen" to "Nach der Bluetooth-Verbindung liest die App automatisch die Tragedaten vom Gerät und lädt sie in die Cloud hoch. Die Oberfläche zeigt das Lesen der Gerätedaten und danach den Abschluss an.",
                "Startseitenanalyse" to "Die 30-Tage-Zusammenfassung und das 7-Tage-Diagramm verwenden echte Cloud-Daten. Der Zielwert stammt aus der verordneten Tragezeit im Profil, Farben zeigen Risikostufen.",
                "Aufzeichnungsfunktionen" to "Hautprotokolle dienen Fotos und Notizen bei Problemen. Wachstumsprotokolle speichern die Körpergröße. Das Bildarchiv speichert Kamera- oder Galerieaufnahmen. Diese Inhalte fließen in den Kontrollbericht ein.",
                "Berichte und Archiv" to "KI-Berichte, Kontrollberichte und Archive werden aus Cloud-Daten zu Tragen, Haut, Wachstum, Bildern und Profil erzeugt. Ein Archiv speichert den Datenstand zum Erstellungszeitpunkt.",
                "Exportieren und löschen" to "Exportieren Sie Daten vor dem Löschen als Sicherung. Vor dem Löschen aller Daten müssen Sicherung, Bestätigungen, Spitzname, Bestätigungstext und eine Sicherheitsfrist abgeschlossen sein.",
                "Sprache und Einstellungen" to "In den Einstellungen wechseln Sie die Sprache und verwalten Bluetooth. Die gewählte Sprache wird lokal gespeichert und beim nächsten Öffnen weiterverwendet.",
            ),
        )

    private fun helpFaqItems(): List<Pair<String, String>> =
        languagePairs(
            zh = listOf(
                "为什么需要先绑定蓝牙设备？" to "支具传感器中的佩戴数据需要通过蓝牙读取。绑定后APP才能自动连接设备并上传佩戴记录。",
                "蓝牙连接不上怎么办？" to "请确认手机蓝牙已打开、设备在附近且设备名称以 WM-SP# 开头。如果20秒内未发现上次设备，请到设置页重新扫描连接。",
                "首页达标率如何计算？" to "达标率按统计周期内达到医嘱佩戴时长的天数占比计算。医嘱佩戴时长来自建档信息。",
                "佩戴数据什么时候上传云端？" to "蓝牙连接成功并读取设备数据后，APP会自动上传云端。上传成功后会自动清除设备端已存储的佩戴数据。",
                "皮肤记录是否需要每天填写？" to "不需要每天检查打卡。仅在发现发红、疼痛、破皮、水泡等问题时拍照并填写记录。",
                "复诊报告的数据来源是什么？" to "复诊报告使用云端建档、佩戴记录、皮肤记录、生长记录、影像档案和报告分析结果生成。",
                "导出数据有什么用？" to "导出数据是一份离线备份。删除全部数据前应先导出并确认备份文件可打开。",
                "删除全部数据后还能恢复吗？" to "删除完成后云端健康数据不可恢复。为避免误删，APP设置了备份检查、确认文字、长按操作和24小时冷静期。",
                "APP里的AI建议能否替代医生？" to "不能。AI解读仅用于整理数据和提供健康教育提示，不能替代医生诊断、治疗方案或支具调整意见。",
            ),
            en = listOf(
                "Why bind a Bluetooth device first?" to "Wearing data stored in the brace sensor must be read through Bluetooth. After binding, the app can connect automatically and upload wearing records.",
                "What if Bluetooth cannot connect?" to "Make sure phone Bluetooth is on, the device is nearby, and the name starts with WM-SP#. If the last device is not found within 20 seconds, scan and connect again in Settings.",
                "How is the home compliance rate calculated?" to "Compliance rate is the percentage of days in the selected period that meet the prescribed wearing hours. The prescribed hours come from the profile.",
                "When is wearing data uploaded?" to "After Bluetooth connects and the device data is read, the app uploads it to the cloud automatically. After a successful upload, stored device records are cleared.",
                "Do skin records need daily check-in?" to "No. Record only when redness, pain, skin breakage, blisters, or other problems are found.",
                "What data does the visit report use?" to "It uses cloud profile data, wearing records, skin records, growth records, imaging archives, and report analysis results.",
                "What is data export for?" to "It is an offline backup. Before deleting all data, export and confirm that the backup file can be opened.",
                "Can deleted data be restored?" to "No. After deletion, cloud health data cannot be restored. To avoid mistakes, the app uses backup check, confirmation text, long press, and a 24-hour cooling-off period.",
                "Can AI advice replace doctors?" to "No. AI interpretation only organizes data and provides health education. It does not replace diagnosis, treatment, or brace adjustment by professionals.",
            ),
            ja = listOf(
                "なぜ先にBluetooth機器を登録する必要がありますか？" to "装具センサー内の装着データはBluetoothで読み取る必要があります。登録後、アプリは自動接続して装着記録をアップロードできます。",
                "Bluetoothに接続できない場合は？" to "スマートフォンのBluetoothがオンで、機器が近くにあり、名前が WM-SP# で始まることを確認してください。20秒以内に前回機器が見つからない場合は、設定で再スキャンしてください。",
                "ホームの達成率はどのように計算しますか？" to "達成率は、対象期間内で医師指示の装着時間に達した日数の割合です。指示時間はプロフィールから取得します。",
                "装着データはいつクラウドに送信されますか？" to "Bluetooth接続後に機器データを読み取ると、アプリが自動でクラウドへ送信します。送信成功後、機器内の保存済み記録を消去します。",
                "皮膚記録は毎日必要ですか？" to "毎日は不要です。発赤、痛み、皮膚損傷、水疱などを見つけた時だけ記録してください。",
                "再診レポートのデータ元は？" to "クラウドのプロフィール、装着記録、皮膚記録、成長記録、画像記録、レポート分析結果を使用します。",
                "データエクスポートの目的は？" to "オフラインバックアップです。全データ削除前にエクスポートし、バックアップファイルを開けることを確認してください。",
                "全データ削除後に復元できますか？" to "できません。削除後、クラウドの健康データは復元できません。誤削除防止のため、バックアップ確認、確認文字、長押し、24時間の冷却期間があります。",
                "AIの助言は医師の代わりになりますか？" to "なりません。AI解釈はデータ整理と健康教育の補助であり、診断、治療、装具調整の代替ではありません。",
            ),
            ko = listOf(
                "왜 Bluetooth 장치를 먼저 등록해야 하나요?" to "보조기 센서의 착용 데이터는 Bluetooth로 읽어야 합니다. 등록 후 앱이 자동으로 장치에 연결하고 착용 기록을 업로드할 수 있습니다.",
                "Bluetooth가 연결되지 않으면 어떻게 하나요?" to "휴대폰 Bluetooth가 켜져 있고 장치가 가까이에 있으며 이름이 WM-SP# 로 시작하는지 확인하세요. 20초 안에 이전 장치를 찾지 못하면 설정에서 다시 스캔하세요.",
                "홈의 달성률은 어떻게 계산하나요?" to "달성률은 통계 기간 중 처방 착용 시간을 달성한 날의 비율입니다. 처방 시간은 프로필 정보에서 가져옵니다.",
                "착용 데이터는 언제 업로드되나요?" to "Bluetooth 연결 후 장치 데이터를 읽으면 앱이 자동으로 클라우드에 업로드합니다. 업로드 성공 후 장치에 저장된 기록은 자동 삭제됩니다.",
                "피부 기록을 매일 해야 하나요?" to "아니요. 발적, 통증, 피부 손상, 물집 등 문제가 발견될 때만 기록하면 됩니다.",
                "재진 보고서는 어떤 데이터를 사용하나요?" to "클라우드 프로필, 착용 기록, 피부 기록, 성장 기록, 영상 기록, 보고서 분석 결과를 사용합니다.",
                "데이터 내보내기는 왜 필요한가요?" to "오프라인 백업입니다. 전체 데이터를 삭제하기 전에 내보내고 백업 파일이 열리는지 확인하세요.",
                "전체 삭제 후 복구할 수 있나요?" to "아니요. 삭제가 완료되면 클라우드 건강 데이터는 복구할 수 없습니다. 오삭제 방지를 위해 백업 확인, 확인 문구, 길게 누르기, 24시간 냉각 기간을 둡니다.",
                "AI 조언이 의사를 대신할 수 있나요?" to "아니요. AI 해석은 데이터 정리와 건강 교육 안내용이며, 진단, 치료, 보조기 조정 의견을 대신하지 않습니다.",
            ),
            es = listOf(
                "¿Por qué vincular primero un dispositivo Bluetooth?" to "Los datos del sensor del corsé deben leerse por Bluetooth. Tras vincularlo, la app puede conectarse automáticamente y subir los registros.",
                "¿Qué hago si Bluetooth no conecta?" to "Comprueba que Bluetooth del teléfono esté activado, el dispositivo esté cerca y el nombre empiece por WM-SP#. Si no aparece en 20 segundos, vuelve a escanear en Ajustes.",
                "¿Cómo se calcula el cumplimiento?" to "Es el porcentaje de días del periodo que alcanzan las horas prescritas. Las horas prescritas vienen del perfil.",
                "¿Cuándo se suben los datos de uso?" to "Después de conectar Bluetooth y leer el dispositivo, la app sube los datos automáticamente. Tras una subida correcta, borra los registros guardados en el dispositivo.",
                "¿Debo registrar la piel cada día?" to "No. Registra solo cuando aparezcan enrojecimiento, dolor, herida, ampollas u otros problemas.",
                "¿Qué datos usa el informe de revisión?" to "Usa perfil, registros de uso, piel, crecimiento, imágenes y resultados de análisis guardados en la nube.",
                "¿Para qué sirve exportar datos?" to "Es una copia sin conexión. Antes de borrar todo, exporta y confirma que el archivo de copia se abre correctamente.",
                "¿Puedo recuperar los datos borrados?" to "No. Tras el borrado, los datos de salud en la nube no se pueden recuperar. Para evitar errores, la app exige copia, texto de confirmación, pulsación larga y 24 horas de seguridad.",
                "¿La IA sustituye al médico?" to "No. La interpretación de IA solo organiza datos y ofrece educación sanitaria. No sustituye diagnóstico, tratamiento ni ajuste del corsé.",
            ),
            fr = listOf(
                "Pourquoi associer d'abord un appareil Bluetooth ?" to "Les données du capteur de l'orthèse doivent être lues par Bluetooth. Après association, l'application peut se connecter automatiquement et envoyer les enregistrements.",
                "Que faire si Bluetooth ne se connecte pas ?" to "Vérifiez que le Bluetooth du téléphone est activé, que l'appareil est proche et que son nom commence par WM-SP#. S'il n'est pas trouvé en 20 secondes, scannez à nouveau dans Paramètres.",
                "Comment le taux d'observance est-il calculé ?" to "C'est le pourcentage de jours de la période qui atteignent les heures prescrites. Les heures prescrites viennent du dossier.",
                "Quand les données de port sont-elles envoyées ?" to "Après connexion Bluetooth et lecture de l'appareil, l'application envoie automatiquement les données. Après succès, elle efface les enregistrements stockés sur l'appareil.",
                "Faut-il saisir la peau chaque jour ?" to "Non. Saisissez seulement en cas de rougeur, douleur, plaie, ampoule ou autre problème.",
                "Quelles données utilise le rapport de suivi ?" to "Il utilise le dossier cloud, les données de port, peau, croissance, imagerie et les résultats d'analyse.",
                "À quoi sert l'export des données ?" to "C'est une sauvegarde hors ligne. Avant de tout supprimer, exportez et vérifiez que le fichier peut être ouvert.",
                "Les données supprimées peuvent-elles être restaurées ?" to "Non. Après suppression, les données de santé cloud ne sont pas récupérables. Pour éviter une erreur, l'application impose sauvegarde, texte de confirmation, appui long et délai de 24 heures.",
                "L'IA peut-elle remplacer le médecin ?" to "Non. L'analyse IA sert à organiser les données et à fournir des conseils éducatifs. Elle ne remplace pas le diagnostic, le traitement ni l'ajustement de l'orthèse.",
            ),
            de = listOf(
                "Warum zuerst ein Bluetooth-Gerät koppeln?" to "Die Tragedaten im Orthesensensor müssen per Bluetooth gelesen werden. Nach der Kopplung kann die App automatisch verbinden und Trageaufzeichnungen hochladen.",
                "Was tun, wenn Bluetooth nicht verbindet?" to "Prüfen Sie, ob Bluetooth am Telefon aktiv ist, das Gerät in der Nähe ist und der Name mit WM-SP# beginnt. Wird das letzte Gerät in 20 Sekunden nicht gefunden, scannen Sie erneut in den Einstellungen.",
                "Wie wird die Erfüllungsrate berechnet?" to "Sie ist der Anteil der Tage im Zeitraum, an denen die verordnete Tragezeit erreicht wurde. Die verordnete Zeit stammt aus dem Profil.",
                "Wann werden Tragedaten hochgeladen?" to "Nach Bluetooth-Verbindung und Lesen der Gerätedaten lädt die App automatisch in die Cloud hoch. Nach erfolgreichem Upload werden gespeicherte Gerätedaten gelöscht.",
                "Muss das Hautprotokoll täglich ausgefüllt werden?" to "Nein. Erfassen Sie nur bei Rötung, Schmerzen, Hautverletzung, Blasen oder anderen Problemen.",
                "Welche Daten nutzt der Kontrollbericht?" to "Er nutzt Cloud-Profil, Trage-, Haut-, Wachstums- und Bildaufzeichnungen sowie Analyseergebnisse.",
                "Wozu dient der Datenexport?" to "Er ist eine Offline-Sicherung. Vor dem Löschen aller Daten exportieren und prüfen Sie, ob die Sicherungsdatei geöffnet werden kann.",
                "Können gelöschte Daten wiederhergestellt werden?" to "Nein. Nach dem Löschen sind Cloud-Gesundheitsdaten nicht wiederherstellbar. Zur Vermeidung von Fehlern nutzt die App Sicherungsprüfung, Bestätigungstext, langes Drücken und 24 Stunden Sicherheitsfrist.",
                "Kann KI den Arzt ersetzen?" to "Nein. Die KI-Auswertung ordnet Daten und gibt Gesundheitsinformationen. Sie ersetzt keine Diagnose, Behandlung oder Orthesenanpassung.",
            ),
        )

    private fun userAgreementSections(): List<Pair<String, String>> =
        languagePairs(
            zh = listOf(
                "一、协议说明" to "欢迎使用 Spinecare Mom。本协议由绍兴维脉科技有限公司与您就 Spinecare Mom APP 及相关服务的使用订立。您注册、登录、访问、安装、使用本APP或相关服务，即视为已阅读、理解并同意本协议。",
                "二、服务内容" to "本APP提供孩子建档、支具蓝牙设备绑定、佩戴数据读取与云端同步、首页统计、皮肤/生长/影像记录、AI辅助解读、复诊报告、归档、导出备份和删除全部数据等服务。",
                "三、监护人责任" to "本APP面向未成年人健康管理场景。监护人应确认有权录入和管理相关数据，确保所录入的孩子资料、医嘱信息、照片和记录真实、准确、及时更新，并妥善保管手机和账号使用权限。",
                "四、设备绑定与数据同步" to "用户应使用真实、合法取得的 WM-SP# 前缀蓝牙设备。蓝牙连接后APP会读取佩戴数据并上传云端，上传成功后可清除设备端已存储佩戴数据。因设备未开机、蓝牙异常、网络异常或用户误操作导致的数据缺失，用户应及时检查并重新同步。",
                "五、医疗免责声明" to "本APP不是医疗诊断、处方或治疗工具。AI解读、报告摘要、颜色预警、复诊资料和提醒仅供家庭健康管理参考，不替代医生诊断、医嘱、复诊安排或支具师调整意见。",
                "六、数据与报告" to "用户上传或同步的数据会用于首页统计、智能解读、复诊报告、归档和导出备份。归档报告保存生成时的数据快照，后续数据变化不会自动改写已归档报告。",
                "七、用户行为规范" to "用户不得上传违法、侵权、恶意、虚假或与本服务无关的内容，不得尝试破坏服务、绕过安全限制、冒用他人信息、伪造佩戴数据或干扰云端数据库正常运行。",
                "八、知识产权" to "本APP及相关系统、页面设计、算法逻辑、界面元素、文案、代码和报告模板等内容的知识产权归绍兴维脉科技有限公司或相关权利人所有。未经书面许可，不得复制、修改、传播、反编译或用于商业用途。",
                "九、服务变更、中断与终止" to "基于功能维护、版本升级、服务器调整、医疗安全或合规要求，我们可能调整、中断或终止部分服务。涉及重要数据处理规则变化时，应在APP内更新说明或提示用户查看。",
                "十、隐私与个人信息保护" to "我们将按照《Spinecare Mom APP隐私政策》处理个人信息和未成年人健康相关数据。隐私政策是本协议的重要组成部分，与本协议具有同等效力。",
                "十一、终止与删除" to "用户可通过导出数据和删除全部数据流程结束使用。删除完成后云端建档、佩戴、皮肤、生长、影像、报告、预警、设备绑定及上传文件不可恢复，建议提前保存备份。",
                "十二、适用法律与争议解决" to "本协议的订立、履行、解释及争议解决适用中华人民共和国法律。因本协议引起的争议，双方应先友好协商；协商不成的，可向绍兴维脉科技有限公司所在地有管辖权的人民法院提起诉讼。",
                "十三、联系我们" to "运营主体：绍兴维脉科技有限公司；地址：浙江省绍兴市越城区袍中北路631号；邮箱：zclei@vip.sina.com。也可在APP“联系我们”页面扫描微信二维码添加客服。",
            ),
            en = listOf(
                "1. Service Description" to "Spinecare Mom helps guardians record and view brace wearing, skin, growth, imaging, and report information. The app provides data organization, reminders, and assisted interpretation.",
                "2. Guardian Responsibility" to "The app is intended for minor health management scenarios. Guardians should confirm they are authorized to enter and manage relevant data and keep the information true, accurate, and current.",
                "3. Medical Disclaimer" to "The app is not a medical diagnosis tool. AI interpretation, report summaries, and reminders are for health management reference only and do not replace medical diagnosis, prescription, follow-up plans, or brace specialist advice.",
                "4. Data and Reports" to "Uploaded or synchronized data may be used for home statistics, intelligent interpretation, visit reports, archive, and export backup. Archived reports keep a snapshot at the time of generation.",
                "5. User Conduct" to "Users must not upload illegal, infringing, malicious, or unrelated content, or attempt to disrupt the service, bypass security limits, or impersonate others.",
                "6. Service Changes" to "Features may be adjusted according to testing, medical safety, and compliance needs. Important data processing changes should be explained in the app.",
                "7. Termination and Deletion" to "Users may end use through data export and Delete All Data. After deletion, cloud data cannot be restored; please keep a backup in advance.",
            ),
            ja = listOf(
                "1. サービス説明" to "Spinecare Momは、保護者が装具装着、皮膚、成長、画像、レポート情報を記録・確認するためのアプリです。データ整理、リマインダー、補助的な解釈を提供します。",
                "2. 保護者の責任" to "本アプリは未成年者の健康管理を想定しています。保護者は関連データを入力・管理する権限を確認し、情報を正確かつ最新に保つ必要があります。",
                "3. 医療免責" to "本アプリは医療診断ツールではありません。AI解釈、レポート要約、リマインダーは健康管理の参考であり、医師の診断、指示、再診計画、装具師の判断を代替しません。",
                "4. データとレポート" to "アップロードまたは同期されたデータは、ホーム統計、スマート解釈、再診レポート、アーカイブ、バックアップ出力に使用されます。アーカイブは生成時点のスナップショットを保存します。",
                "5. ユーザー行為" to "違法、権利侵害、悪意のある内容、または本サービスと無関係な内容をアップロードしてはなりません。サービス妨害、安全制限の回避、なりすましも禁止です。",
                "6. サービス変更" to "機能はテスト、医療安全、法令遵守の必要に応じて調整される場合があります。重要なデータ処理変更はアプリ内で説明します。",
                "7. 終了と削除" to "データエクスポートと全データ削除により利用を終了できます。削除後、クラウドデータは復元できないため、事前にバックアップしてください。",
            ),
            ko = listOf(
                "1. 서비스 설명" to "Spinecare Mom은 보호자가 보조기 착용, 피부, 성장, 영상, 보고서 정보를 기록하고 확인하도록 돕습니다. 앱은 데이터 정리, 알림, 보조 해석 기능을 제공합니다.",
                "2. 보호자 책임" to "이 앱은 미성년자 건강 관리 상황을 대상으로 합니다. 보호자는 관련 데이터를 입력하고 관리할 권한이 있는지 확인하고, 정보를 사실과 정확성에 맞게 최신으로 유지해야 합니다.",
                "3. 의료 면책" to "앱은 의료 진단 도구가 아닙니다. AI 해석, 보고서 요약, 알림은 건강 관리 참고용이며 의사의 진단, 처방, 재진 계획 또는 보조기 전문가 의견을 대신하지 않습니다.",
                "4. 데이터와 보고서" to "업로드 또는 동기화된 데이터는 홈 통계, 지능형 해석, 재진 보고서, 보관, 내보내기 백업에 사용될 수 있습니다. 보관 보고서는 생성 당시의 스냅샷을 저장합니다.",
                "5. 사용자 행위" to "사용자는 불법, 침해, 악의적이거나 서비스와 무관한 내용을 업로드해서는 안 되며, 서비스 방해, 보안 제한 우회, 타인 사칭을 시도해서는 안 됩니다.",
                "6. 서비스 변경" to "기능은 테스트, 의료 안전, 준수 요구에 따라 조정될 수 있습니다. 중요한 데이터 처리 규칙 변경은 앱에서 안내해야 합니다.",
                "7. 종료와 삭제" to "사용자는 데이터 내보내기와 전체 데이터 삭제 절차로 사용을 종료할 수 있습니다. 삭제 후 클라우드 데이터는 복구할 수 없으므로 미리 백업하세요.",
            ),
            es = listOf(
                "1. Descripción del servicio" to "Spinecare Mom ayuda a los tutores a registrar y consultar uso del corsé, piel, crecimiento, imágenes e informes. La app ofrece organización de datos, recordatorios e interpretación asistida.",
                "2. Responsabilidad del tutor" to "La app está pensada para la gestión de salud de menores. El tutor debe confirmar que tiene autorización para introducir y gestionar los datos y mantenerlos reales, exactos y actualizados.",
                "3. Descargo médico" to "La app no es una herramienta de diagnóstico médico. La interpretación de IA, resúmenes y recordatorios son solo referencia de gestión de salud y no sustituyen diagnóstico, prescripción, revisión ni ajuste profesional.",
                "4. Datos e informes" to "Los datos subidos o sincronizados se pueden usar para estadísticas, interpretación inteligente, informes de revisión, archivo y copia exportada. Los informes archivados conservan una instantánea.",
                "5. Conducta del usuario" to "El usuario no debe subir contenido ilegal, infractor, malicioso o ajeno al servicio, ni intentar dañar el servicio, saltar límites de seguridad o suplantar a otra persona.",
                "6. Cambios del servicio" to "Las funciones pueden ajustarse por pruebas, seguridad médica y cumplimiento. Los cambios importantes de tratamiento de datos deben explicarse dentro de la app.",
                "7. Terminación y eliminación" to "El usuario puede finalizar el uso mediante exportación y eliminación total de datos. Tras borrar, los datos de la nube no se recuperan; guarde una copia antes.",
            ),
            fr = listOf(
                "1. Description du service" to "Spinecare Mom aide les responsables légaux à enregistrer et consulter le port de l'orthèse, la peau, la croissance, les images et les rapports. L'application propose organisation des données, rappels et interprétation assistée.",
                "2. Responsabilité du responsable légal" to "L'application concerne la gestion de santé de mineurs. Le responsable légal doit confirmer son droit de saisir et gérer les données, et maintenir les informations exactes et à jour.",
                "3. Avertissement médical" to "L'application n'est pas un outil de diagnostic médical. Les analyses IA, résumés et rappels servent de référence de gestion de santé et ne remplacent pas diagnostic, prescription, suivi ou avis d'orthoprothésiste.",
                "4. Données et rapports" to "Les données envoyées ou synchronisées peuvent servir aux statistiques d'accueil, analyses intelligentes, rapports de suivi, archives et exports. Les rapports archivés gardent un instantané.",
                "5. Conduite utilisateur" to "L'utilisateur ne doit pas envoyer de contenu illégal, contrefaisant, malveillant ou sans rapport avec le service, ni tenter de perturber le service, contourner la sécurité ou usurper une identité.",
                "6. Modifications du service" to "Les fonctions peuvent évoluer selon les tests, la sécurité médicale et la conformité. Les changements importants de traitement des données doivent être expliqués dans l'application.",
                "7. Fin et suppression" to "L'utilisateur peut arrêter l'utilisation via export et suppression de toutes les données. Après suppression, les données cloud ne sont pas récupérables; conservez une sauvegarde.",
            ),
            de = listOf(
                "1. Leistungsbeschreibung" to "Spinecare Mom hilft Sorgeberechtigten, Orthesen-Tragen, Haut, Wachstum, Bilder und Berichte zu erfassen und anzusehen. Die App bietet Datenordnung, Erinnerungen und unterstützende Auswertung.",
                "2. Verantwortung der Sorgeberechtigten" to "Die App ist für Gesundheitsmanagement Minderjähriger gedacht. Sorgeberechtigte müssen berechtigt sein, Daten einzugeben und zu verwalten, und die Angaben wahr, genau und aktuell halten.",
                "3. Medizinischer Hinweis" to "Die App ist kein medizinisches Diagnosewerkzeug. KI-Auswertung, Berichtszusammenfassungen und Erinnerungen dienen nur der Gesundheitsverwaltung und ersetzen keine Diagnose, Verordnung, Nachsorge oder Orthesenanpassung.",
                "4. Daten und Berichte" to "Hochgeladene oder synchronisierte Daten können für Startstatistiken, intelligente Auswertung, Kontrollberichte, Archive und Exportsicherung genutzt werden. Archivierte Berichte speichern einen Snapshot.",
                "5. Nutzerverhalten" to "Nutzer dürfen keine illegalen, rechtsverletzenden, schädlichen oder servicefremden Inhalte hochladen und nicht versuchen, den Dienst zu stören, Sicherheit zu umgehen oder andere zu imitieren.",
                "6. Änderungen des Dienstes" to "Funktionen können nach Tests, medizinischer Sicherheit und Compliance angepasst werden. Wichtige Änderungen der Datenverarbeitung sollten in der App erläutert werden.",
                "7. Beendigung und Löschung" to "Nutzer können die Nutzung über Datenexport und Alle Daten löschen beenden. Nach der Löschung sind Cloud-Daten nicht wiederherstellbar; sichern Sie sie vorher.",
            ),
        )

    private fun privacyPolicySections(): List<Pair<String, String>> =
        languagePairs(
            zh = listOf(
                "一、引言" to "欢迎使用 Spinecare Mom。我们深知个人信息和未成年人健康相关数据的重要性，并将按照合法、正当、必要、诚信、公开透明的原则处理您的信息。本隐私政策适用于绍兴维脉科技有限公司提供的 Spinecare Mom APP 及相关服务。",
                "二、我们收集和使用的信息" to "APP可能收集监护人登录信息、孩子建档信息、Cobb角、弯曲部位、Risser征、医嘱佩戴时间、支具类型、初诊日期、蓝牙设备信息、佩戴记录、皮肤照片与备注、生长记录、影像资料、AI咨询内容、报告归档、预警消息、导出与删除流程记录及必要运行日志。",
                "三、使用目的" to "上述数据用于完成登录与建档、蓝牙设备绑定、读取和上传佩戴数据、展示佩戴趋势、判断达标情况、生成AI报告和复诊报告、保存归档、导出备份、执行删除流程、排查故障及改进服务。",
                "四、权限说明" to "蓝牙权限用于连接 WM-SP# 前缀支具传感器；相机权限用于拍摄皮肤问题或影像资料；图库权限用于选择手机相册中的影像资料；网络权限用于与云端数据库、上传目录和服务接口同步。",
                "五、共享、转让和公开披露" to "我们不会向无关第三方出售或非法提供个人信息和健康相关数据。除用户主动分享、依法要求、履行监护人授权、保护用户安全或服务运行必要外，APP不会将健康数据提供给无关第三方。如发生合并、分立、收购或资产转让，我们将要求接收方继续受本政策约束。",
                "六、存储与保护" to "数据会保存于项目独立云端数据库和上传目录。本机也会保存语言选择、最近备份时间、上次绑定设备等基础设置。我们将采取访问控制、数据库权限管理、日志审计、最小权限原则等合理措施保护数据安全。",
                "七、未成年人保护" to "本APP主要用于未成年人支具佩戴管理。未成年人数据应由监护人录入和管理。APP尽量减少不必要身份信息采集，默认不要求真实姓名或证件号码。监护人应妥善管理孩子照片、影像和健康记录。",
                "八、您的权利" to "您可在APP内查看、更正建档信息，导出主要云端数据作为备份，并按“删除全部数据”流程申请删除当前孩子相关云端数据。删除完成后数据不可恢复，请先确认备份文件可打开并妥善保存。",
                "九、导出与删除" to "导出功能会生成包含主要云端数据的备份文件，请保存到可信位置并避免随意转发。删除功能会删除当前孩子相关的云端建档、佩戴、皮肤、生长、影像、报告、预警、设备绑定和上传文件。",
                "十、政策更新" to "我们可能根据产品功能、法律法规或运营需要更新本隐私政策。涉及个人信息权益的重要变更时，应在APP中提示用户查看。",
                "十一、联系我们" to "运营主体：绍兴维脉科技有限公司；地址：浙江省绍兴市越城区袍中北路631号；邮箱：zclei@vip.sina.com。也可在APP“联系我们”页面扫描微信二维码添加客服。",
            ),
            en = listOf(
                "1. Data Collected" to "The app may collect profile information, wearing records, Bluetooth device information, skin photos and notes, growth records, imaging files, AI consultation content, report archives, and alert messages.",
                "2. Purposes" to "Data is used to show wearing trends, judge compliance, generate visit reports, provide intelligent interpretation, keep archives, export backups, and perform deletion workflows.",
                "3. Permissions" to "Bluetooth permissions connect brace devices. Camera and gallery permissions record skin or imaging materials. Network permission synchronizes data with the cloud database.",
                "4. Minor Protection" to "Minor data should be managed by guardians. The app minimizes unnecessary identity collection and does not require real names or ID numbers by default.",
                "5. Data Storage" to "Data is stored in the project's independent cloud database and upload directory. The phone also stores basic settings such as language, last backup time, and last bound device.",
                "6. Data Export" to "Export creates a JSON backup containing major cloud data. Save it in a trusted location and avoid unnecessary forwarding.",
                "7. Delete All Data" to "Deletion removes the current child's cloud profile, wearing data, records, reports, alerts, device binding, and uploaded files. It cannot be restored after completion.",
                "8. Third-party Sharing" to "Except for user-initiated sharing, legal requirements, or necessary service operation, the app should not provide health data to unrelated third parties.",
                "9. Policy Updates" to "The privacy policy may be updated with feature changes. Major changes should prompt users to review them in the app.",
            ),
            ja = listOf(
                "1. 収集するデータ" to "アプリはプロフィール情報、装着記録、Bluetooth機器情報、皮膚写真とメモ、成長記録、画像資料、AI相談内容、レポートアーカイブ、警告メッセージを収集する場合があります。",
                "2. 利用目的" to "データは装着傾向表示、達成判定、再診レポート生成、スマート解釈、アーカイブ保存、バックアップ出力、削除手続きに使われます。",
                "3. 権限説明" to "Bluetooth権限は装具機器接続に使います。カメラとギャラリー権限は皮膚または画像資料の記録に使います。ネットワーク権限はクラウド同期に使います。",
                "4. 未成年者保護" to "未成年者データは保護者が管理する必要があります。アプリは不要な本人情報の収集を抑え、既定では実名や証明書番号を要求しません。",
                "5. データ保存" to "データは本プロジェクト専用のクラウドデータベースとアップロード領域に保存されます。端末にも言語、最近のバックアップ時刻、前回登録機器などを保存します。",
                "6. データエクスポート" to "エクスポートは主要なクラウドデータを含むJSONバックアップを生成します。信頼できる場所に保存し、不必要な転送を避けてください。",
                "7. 全データ削除" to "削除は現在の子どもに関するクラウドプロフィール、装着、記録、レポート、警告、機器登録、アップロードファイルを削除します。完了後は復元できません。",
                "8. 第三者共有" to "ユーザーによる共有、法的要求、サービス運営に必要な場合を除き、アプリは健康データを無関係な第三者に提供しません。",
                "9. ポリシー更新" to "プライバシーポリシーは機能変更に伴い更新される場合があります。重大な変更はアプリ内で確認を促します。",
            ),
            ko = listOf(
                "1. 수집 데이터" to "앱은 프로필 정보, 착용 기록, Bluetooth 장치 정보, 피부 사진과 메모, 성장 기록, 영상 자료, AI 상담 내용, 보고서 보관, 경고 메시지를 수집할 수 있습니다.",
                "2. 이용 목적" to "데이터는 착용 추세 표시, 달성 여부 판단, 재진 보고서 생성, 지능형 해석 제공, 보관 저장, 백업 내보내기, 삭제 절차 수행에 사용됩니다.",
                "3. 권한 안내" to "Bluetooth 권한은 보조기 장치 연결에 사용됩니다. 카메라와 갤러리 권한은 피부 또는 영상 자료 기록에 사용됩니다. 네트워크 권한은 클라우드 데이터베이스 동기화에 사용됩니다.",
                "4. 미성년자 보호" to "미성년자 데이터는 보호자가 관리해야 합니다. 앱은 불필요한 신원 정보 수집을 최소화하며 기본적으로 실명이나 신분증 번호를 요구하지 않습니다.",
                "5. 데이터 저장" to "데이터는 프로젝트의 독립 클라우드 데이터베이스와 업로드 디렉터리에 저장됩니다. 기기에는 언어, 최근 백업 시간, 이전 등록 장치 등 기본 설정도 저장됩니다.",
                "6. 데이터 내보내기" to "내보내기는 주요 클라우드 데이터를 포함한 JSON 백업 파일을 생성합니다. 신뢰할 수 있는 위치에 저장하고 불필요한 전달을 피하세요.",
                "7. 전체 데이터 삭제" to "삭제 기능은 현재 아이와 관련된 클라우드 프로필, 착용, 기록, 보고서, 경고, 장치 등록, 업로드 파일을 삭제합니다. 완료 후 복구할 수 없습니다.",
                "8. 제3자 공유" to "사용자 직접 공유, 법적 요구, 서비스 운영에 필요한 경우를 제외하고 앱은 건강 데이터를 무관한 제3자에게 제공하지 않습니다.",
                "9. 정책 업데이트" to "개인정보 처리방침은 기능 변경에 따라 업데이트될 수 있습니다. 중요한 변경은 앱에서 확인하도록 안내해야 합니다.",
            ),
            es = listOf(
                "1. Datos recopilados" to "La app puede recopilar perfil, registros de uso, información Bluetooth, fotos y notas de piel, crecimiento, imágenes, consultas de IA, informes archivados y alertas.",
                "2. Finalidades" to "Los datos se usan para mostrar tendencias de uso, evaluar cumplimiento, generar informes, ofrecer interpretación inteligente, guardar archivos, exportar copias y ejecutar eliminación.",
                "3. Permisos" to "Bluetooth conecta el dispositivo del corsé. Cámara y galería registran piel o imágenes. Red sincroniza con la base de datos en la nube.",
                "4. Protección de menores" to "Los datos de menores deben ser gestionados por tutores. La app reduce la recogida de identidad innecesaria y no exige nombre real ni documento por defecto.",
                "5. Almacenamiento" to "Los datos se guardan en la base de datos cloud independiente del proyecto y en el directorio de subidas. El teléfono guarda ajustes como idioma, última copia y último dispositivo vinculado.",
                "6. Exportación" to "La exportación genera un archivo JSON con los principales datos de la nube. Guárdalo en un lugar confiable y evita reenviarlo sin necesidad.",
                "7. Borrar todo" to "El borrado elimina perfil cloud, uso, registros, informes, alertas, vinculación de dispositivo y archivos subidos del menor actual. No se puede restaurar al terminar.",
                "8. Compartir con terceros" to "Salvo compartición iniciada por el usuario, exigencia legal u operación necesaria del servicio, la app no debe entregar datos de salud a terceros no relacionados.",
                "9. Actualizaciones" to "La política de privacidad puede actualizarse con cambios de función. Los cambios importantes deben notificarse dentro de la app.",
            ),
            fr = listOf(
                "1. Données collectées" to "L'application peut collecter dossier, données de port, informations Bluetooth, photos et notes de peau, croissance, imagerie, consultations IA, archives de rapports et alertes.",
                "2. Finalités" to "Les données servent à afficher les tendances, évaluer l'observance, générer des rapports, fournir une analyse intelligente, archiver, exporter une sauvegarde et exécuter la suppression.",
                "3. Autorisations" to "Bluetooth sert à connecter l'orthèse. Caméra et galerie servent aux photos de peau ou d'imagerie. Le réseau sert à synchroniser la base cloud.",
                "4. Protection des mineurs" to "Les données de mineurs doivent être gérées par les responsables légaux. L'application limite les informations d'identité inutiles et ne demande pas de nom réel ni de numéro d'identité par défaut.",
                "5. Stockage" to "Les données sont stockées dans la base cloud indépendante du projet et le dossier d'envoi. Le téléphone conserve aussi langue, dernière sauvegarde et dernier appareil associé.",
                "6. Export" to "L'export crée un fichier JSON contenant les principales données cloud. Conservez-le dans un lieu fiable et évitez de le transférer inutilement.",
                "7. Suppression totale" to "La suppression efface le dossier cloud, port, enregistrements, rapports, alertes, association d'appareil et fichiers envoyés de l'enfant actuel. Elle est irréversible.",
                "8. Partage tiers" to "Sauf partage initié par l'utilisateur, obligation légale ou fonctionnement nécessaire du service, l'application ne doit pas fournir les données de santé à des tiers non concernés.",
                "9. Mises à jour" to "La politique de confidentialité peut évoluer avec les fonctions. Les changements majeurs doivent inviter l'utilisateur à les consulter dans l'application.",
            ),
            de = listOf(
                "1. Erhobene Daten" to "Die App kann Profilinformationen, Trageaufzeichnungen, Bluetooth-Geräteinformationen, Hautfotos und Notizen, Wachstumsdaten, Bildmaterial, KI-Beratungen, Berichtarchive und Warnmeldungen erfassen.",
                "2. Zwecke" to "Daten werden genutzt, um Trends zu zeigen, Erfüllung zu bewerten, Berichte zu erzeugen, intelligente Auswertung bereitzustellen, Archive zu speichern, Backups zu exportieren und Löschung auszuführen.",
                "3. Berechtigungen" to "Bluetooth dient zur Verbindung mit Orthesengeräten. Kamera und Galerie dienen Haut- oder Bildaufzeichnungen. Netzwerk dient zur Synchronisierung mit der Cloud-Datenbank.",
                "4. Schutz Minderjähriger" to "Daten Minderjähriger sollten von Sorgeberechtigten verwaltet werden. Die App minimiert unnötige Identitätsdaten und verlangt standardmäßig keinen echten Namen oder Ausweisnummer.",
                "5. Speicherung" to "Daten werden in der unabhängigen Cloud-Datenbank und im Upload-Verzeichnis des Projekts gespeichert. Das Telefon speichert außerdem Sprache, letzte Sicherung und zuletzt gekoppeltes Gerät.",
                "6. Datenexport" to "Der Export erstellt eine JSON-Sicherung mit wichtigen Cloud-Daten. Speichern Sie sie an einem vertrauenswürdigen Ort und vermeiden Sie unnötige Weitergabe.",
                "7. Alle Daten löschen" to "Die Löschung entfernt Cloud-Profil, Trage-, Aufzeichnungs-, Berichts- und Warndaten, Gerätekopplung und Uploads des aktuellen Kindes. Nach Abschluss ist keine Wiederherstellung möglich.",
                "8. Weitergabe an Dritte" to "Außer bei Nutzerfreigabe, gesetzlicher Pflicht oder notwendigem Servicebetrieb sollte die App Gesundheitsdaten nicht an unbeteiligte Dritte geben.",
                "9. Aktualisierungen" to "Die Datenschutzrichtlinie kann mit Funktionsänderungen aktualisiert werden. Wichtige Änderungen sollten in der App angezeigt werden.",
            ),
        )

    private fun languagePairs(
        zh: List<Pair<String, String>>,
        en: List<Pair<String, String>>,
        ja: List<Pair<String, String>>,
        ko: List<Pair<String, String>>,
        es: List<Pair<String, String>>,
        fr: List<Pair<String, String>>,
        de: List<Pair<String, String>>,
    ): List<Pair<String, String>> =
        when (selectedLanguageIndex.coerceIn(0, languages.lastIndex)) {
            1 -> en
            2 -> ja
            3 -> ko
            4 -> es
            5 -> fr
            6 -> de
            else -> zh
        }

    private fun localizedContactName(): String =
        languageText(
            WECHAT_CONTACT_TEXT,
            "WeChat Support: Spinecare Mom",
            "WeChatサポート: Spinecare Mom",
            "WeChat 고객지원: Spinecare Mom",
            "Soporte WeChat: Spinecare Mom",
            "Support WeChat : Spinecare Mom",
            "WeChat-Support: Spinecare Mom",
        )

    private fun localizedQrNotice(): String =
        languageText(
            "扫描上方二维码添加微信客服。",
            "Scan the QR code above to add WeChat support.",
            "上のQRコードをスキャンしてWeChatサポートを追加してください。",
            "위 QR 코드를 스캔하여 WeChat 고객지원을 추가하세요.",
            "Escanea el código QR anterior para añadir el soporte de WeChat.",
            "Scannez le QR code ci-dessus pour ajouter le support WeChat.",
            "Scannen Sie den QR-Code oben, um den WeChat-Support hinzuzufügen.",
        )

    private fun localizedCompanyLine(): String =
        languageText(
            "公司：$COMPANY_NAME_ZH",
            "Company: Shaoxing Weimai Technology Co., Ltd.",
            "会社: Shaoxing Weimai Technology Co., Ltd.",
            "회사: Shaoxing Weimai Technology Co., Ltd.",
            "Empresa: Shaoxing Weimai Technology Co., Ltd.",
            "Société : Shaoxing Weimai Technology Co., Ltd.",
            "Unternehmen: Shaoxing Weimai Technology Co., Ltd.",
        )

    private fun localizedAddressLine(): String =
        languageText(
            "地址：$COMPANY_ADDRESS_ZH",
            "Address: No. 631, Paozhong North Road, Yuecheng District, Shaoxing, Zhejiang, China",
            "住所: 中国浙江省紹興市越城区袍中北路631号",
            "주소: 중국 저장성 사오싱시 웨청구 파오중북로 631호",
            "Dirección: No. 631, Paozhong North Road, Distrito Yuecheng, Shaoxing, Zhejiang, China",
            "Adresse : No. 631, Paozhong North Road, district de Yuecheng, Shaoxing, Zhejiang, Chine",
            "Adresse: Nr. 631, Paozhong North Road, Yuecheng District, Shaoxing, Zhejiang, China",
        )

    private fun localizedEmailLine(): String =
        languageText(
            "邮箱：$CONTACT_EMAIL",
            "Email: $CONTACT_EMAIL",
            "メール: $CONTACT_EMAIL",
            "이메일: $CONTACT_EMAIL",
            "Correo: $CONTACT_EMAIL",
            "E-mail : $CONTACT_EMAIL",
            "E-Mail: $CONTACT_EMAIL",
        )

    private fun localizedContactTips(): String =
        languageText(
            "建议咨询时说明：孩子昵称、设备名、问题发生时间、相关截图或照片。紧急健康问题请及时联系医生或支具师。",
            "When contacting support, include the child nickname, device name, issue time, and related screenshots or photos. For urgent health issues, contact a doctor or brace specialist promptly.",
            "お問い合わせ時は、子どものニックネーム、機器名、問題発生時刻、関連スクリーンショットまたは写真を記載してください。緊急の健康問題は医師または装具師に早めに相談してください。",
            "문의 시 아이 닉네임, 장치명, 문제 발생 시간, 관련 스크린샷 또는 사진을 알려주세요. 긴급한 건강 문제는 의사 또는 보조기 전문가에게 즉시 문의하세요.",
            "Al contactar, indica el apodo del niño, nombre del dispositivo, hora del problema y capturas o fotos relacionadas. Para problemas urgentes de salud, contacta pronto con un médico o especialista.",
            "Lors du contact, indiquez le surnom de l'enfant, le nom de l'appareil, l'heure du problème et les captures ou photos utiles. Pour une urgence de santé, contactez rapidement un médecin ou un spécialiste.",
            "Geben Sie bei Kontaktaufnahme Spitzname des Kindes, Gerätename, Zeitpunkt des Problems sowie Screenshots oder Fotos an. Bei dringenden Gesundheitsproblemen kontaktieren Sie bitte zeitnah Arzt oder Orthesenspezialist.",
        )

    private fun LinearLayout.addProfileEditPage() {
        addView(appHeader("编辑档案", "保存后同步到云端数据库", showBell = false, showBack = true, backTarget = MainTab.Me))
        addSpace(12)
        addView(
            card {
                addView(cardHeader("基础信息", "昵称、性别与出生日期", null))
                addProfileStepOne()
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("病情信息", "Cobb角、弯曲部位与 Risser 征", null))
                addProfileStepTwo()
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("医嘱与支具", "佩戴时长、支具类型与初诊日期", null))
                addProfileStepThree()
            },
        )
        addSpace(14)
        addView(
            horizontal {
                addView(secondaryButton("取消") {
                    loadProfileFromLocal()
                    currentTab = MainTab.Me
                    render()
                }, weightLp(1f))
                addSpace(10, horizontal = true)
                addView(primaryButton("保存") {
                    saveProfileLocally(completed = true)
                    saveProfileToCloud()
                    currentTab = MainTab.Me
                    render()
                }, weightLp(1f))
            },
        )
    }

    private fun LinearLayout.addSettingsPage() {
        addView(appHeader("设置", "", showBell = false, showBack = true))
        addView(bluetoothConnectionPanel())
        addSpace(12)
        addView(
            card {
                addView(
                    cardHeader(
                        "APP 语言",
                        languageText(
                            "当前：${languages[selectedLanguageIndex]}",
                            "Current: ${languages[selectedLanguageIndex]}",
                            "現在：${languages[selectedLanguageIndex]}",
                            "현재: ${languages[selectedLanguageIndex]}",
                            "Actual: ${languages[selectedLanguageIndex]}",
                            "Actuel : ${languages[selectedLanguageIndex]}",
                            "Aktuell: ${languages[selectedLanguageIndex]}",
                        ),
                        chip("7种", P.primary),
                    ),
                )
                addView(infoStrip("界面文字会按照这里的语言显示，选择后立即保存。"))
                addSpace(8)
                addView(languageRadioGroup())
            },
        )
    }

    private fun LinearLayout.addBluetoothValidationPage() {
        val rows = bluetoothValidationDisplayRows()
        val firstPoint = rows.minOfOrNull { it.hourStart }
        val hasLatestUploadRows = shouldShowLatestUploadValidationRows()
        val firstWearPoint =
            rows.flatMap { row -> row.samples.filter { it.worn }.map { it.recordedAt } }
                .minOrNull()
                ?: rows.filter { it.wornHours > 0.0 }.minOfOrNull { it.hourStart }
        val latestSampleAt =
            rows.flatMap { row -> row.samples.map { it.recordedAt } }.maxOrNull()
                ?: rows.maxOfOrNull { it.hourStart }
        val receivedAt = latestUploadFetchedAt ?: latestSampleAt.takeUnless { hasLatestUploadRows }
        val completedAt = latestUploadCompletedAt ?: latestSampleAt.takeUnless { hasLatestUploadRows }
        val cloudLastReadAt = wearCloudRecords.mapNotNull { it.lastReadAt }.maxOrNull()
        val cloudHistoryRecord =
            wearCloudRecords
                .filter { it.historyHead != null || it.historyCount != null }
                .maxByOrNull { it.lastReadAt ?: it.date.atStartOfDay() }
        val lastReadAt =
            if (hasLatestUploadRows || deviceSyncInProgress || deviceSyncUploading) {
                latestUploadLastReadAt ?: cloudLastReadAt
            } else {
                cloudLastReadAt ?: latestUploadLastReadAt
            }
        val displayHistoryHead =
            if (hasLatestUploadRows || deviceSyncInProgress || deviceSyncUploading) {
                latestUploadHistoryHead ?: cloudHistoryRecord?.historyHead
            } else {
                cloudHistoryRecord?.historyHead ?: latestUploadHistoryHead
            }
        val displayHistoryCount =
            if (hasLatestUploadRows || deviceSyncInProgress || deviceSyncUploading) {
                latestUploadHistoryCount ?: cloudHistoryRecord?.historyCount
            } else {
                cloudHistoryRecord?.historyCount ?: latestUploadHistoryCount
            }
        val displayStatus =
            when {
                deviceSyncInProgress || deviceSyncUploading -> latestUploadStatus
                hasLatestUploadRows -> latestUploadStatus
                wearCloudLoading -> "正在读取云端佩戴数据..."
                rows.isNotEmpty() -> "已显示云端数据库保存数据"
                else -> latestUploadStatus
            }
        val displayDailyCount =
            if (hasLatestUploadRows) {
                latestUploadDailyCount
            } else {
                rows.map { it.hourStart.toLocalDate() }.distinct().size
            }
        val displayTotalWornHours =
            if (hasLatestUploadRows) {
                latestUploadTotalWornHours
            } else {
                roundOne(rows.sumOf { it.wornHours })
            }
        val displayDateRange =
            rows.map { it.hourStart.toLocalDate() }.distinct().let { dates ->
                if (dates.isEmpty()) "--" else "${dates.minOrNull()} 至 ${dates.maxOrNull()}"
            }

        addView(appHeader("数据验证", "蓝牙佩戴数据临时核对", showBell = false, showBack = true, backTarget = MainTab.Settings))
        addSpace(12)
        addView(
            card {
                addView(
                    cardHeader(
                        "本次获取",
                        latestUploadDeviceName ?: connectedBluetoothDevice?.name ?: selectedBluetoothDevice?.name ?: "未连接设备",
                        chip(validationStatusChipText(displayStatus, rows), validationStatusChipColor(displayStatus, rows)),
                    ),
                )
                addView(infoStrip("这里展示云端数据库已保存的逐小时佩戴数据；蓝牙读取并上传完成后，会立即用本次上传数据更新，便于核对时间是否正确。"))
                addSpace(10)
                addView(rowText("上传状态", displayStatus))
                addSpace(6)
                addView(rowText("云端日期范围", displayDateRange))
                addSpace(6)
                addView(rowText("上次读取数据的时间", lastReadAt?.let(::formatValidationDateTime) ?: "--"))
                addSpace(6)
                addView(rowText("当前记录位置(head)", displayHistoryHead?.toString() ?: "--"))
                addSpace(6)
                addView(rowText("本次读到的数据量", displayHistoryCount?.let { "${it}条（每条10分钟）" } ?: "--"))
                addSpace(6)
                addView(rowText("本次读到的时间量", displayHistoryCount?.let(::formatReadDataDuration) ?: "--"))
                addSpace(6)
                addView(rowText("数据起始时间", firstPoint?.let(::formatValidationDateTime) ?: "--"))
                addSpace(6)
                addView(rowText("开始佩戴时间", firstWearPoint?.let(::formatValidationDateTime) ?: "未发现佩戴点"))
                addSpace(6)
                addView(rowText("获取数据时间", receivedAt?.let(::formatValidationDateTime) ?: "--"))
                addSpace(6)
                addView(rowText("云端完成时间", completedAt?.let(::formatValidationDateTime) ?: "--"))
                addSpace(6)
                addView(rowText("上传日期记录", "${displayDailyCount}天"))
                addSpace(6)
                addView(rowText("上传佩戴合计", "${formatHours(displayTotalWornHours)}h"))
                addSpace(12)
                addView(
                    horizontal {
                        addView(secondaryButton("返回设置") {
                            currentTab = MainTab.Settings
                            render()
                        }, weightLp(1f))
                        addSpace(10, horizontal = true)
                        addView(primaryButton("重新获取并上传") {
                            requestBluetoothValidationData()
                        }.apply {
                            setBluetoothButtonEnabled(this, connectedBluetoothDevice != null && !deviceSyncInProgress && !deviceSyncUploading)
                        }, weightLp(1f))
                    },
                )
            },
        )
        addSpace(12)
        addView(
            card {
                addView(
                    cardHeader(
                        "逐小时佩戴次数",
                        if (rows.isEmpty()) "暂无云端保存数据" else "${rows.size}小时 · 云端数据库已保存",
                        chip(if (hasLatestUploadRows) "本次" else "云端", P.primary),
                    ),
                )
                if (rows.isEmpty()) {
                    addView(infoStrip(if (connectedBluetoothDevice == null) "请先在设置页连接蓝牙设备，连接后会自动读取并上传；或稍候等待云端数据读取完成。" else "点击“重新获取并上传”，读取设备数据并同时上传云端。"))
                } else {
                    rows.forEachIndexed { index, item ->
                        if (index > 0) addSpace(6)
                        addView(bluetoothValidationHourRow(item))
                    }
                }
            },
        )
    }

    private fun bluetoothConnectionPanel(): View =
        card {
            addView(
                cardHeader(
                    "蓝牙连接",
                    "扫描并连接 WM-SP# 设备，读取电量和月度佩戴记录",
                    chip(if (connectedBluetoothDevice != null) "已连接" else "未连接", if (connectedBluetoothDevice != null) P.success else P.warning),
                ),
            )
            addView(infoStrip(bluetoothStatusMessage))
            addSpace(8)
            addView(bluetoothMetricsGrid())
            addSpace(8)
            addView(
                horizontal {
                    addView(primaryButton("扫描") {
                        runWithBluetoothPermissions { spineBraceBluetooth.startScan() }
                    }.apply {
                        setBluetoothButtonEnabled(this, connectedBluetoothDevice == null)
                    }, weightLp(1f))
                    addSpace(8, horizontal = true)
                    addView(secondaryButton("连接") {
                        runWithBluetoothPermissions {
                            val device = selectedBluetoothDevice ?: bluetoothDevices.firstOrNull()
                            if (device == null) {
                                bluetoothStatusMessage = "请先扫描并选择 WM-SP# 设备"
                                refreshBluetoothUi()
                            } else {
                                selectedBluetoothDevice = device
                                spineBraceBluetooth.connect(device)
                            }
                        }
                    }.apply {
                        setBluetoothButtonEnabled(this, connectedBluetoothDevice == null && selectedBluetoothDevice != null)
                    }, weightLp(1f))
                    addSpace(8, horizontal = true)
                    addView(dangerButton("断开") {
                        runWithBluetoothPermissions { spineBraceBluetooth.disconnect() }
                    }.apply {
                        setBluetoothButtonEnabled(this, connectedBluetoothDevice != null)
                    }, weightLp(1f))
                },
            )
            addSpace(10)
            addView(secondaryButton("数据验证") {
                currentTab = MainTab.BluetoothValidation
                loadHomeWearData(force = true)
                render()
            }, matchLp())
            addSpace(10)
            addView(bluetoothDeviceList())
        }

    private fun bluetoothMetricsGrid(): View {
        val telemetry = latestBraceTelemetry
        return vertical {
            background = rounded(0xFFF9FBFB.toInt(), dp(8), P.softLine)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(label(telemetry?.batteryText ?: "--", 24f, P.text, Typeface.BOLD))
            addView(label("电量", 13f, P.secondary))
        }.also {
            it.layoutParams = matchHeightLp(dp(82))
        }
    }

    private fun bluetoothDeviceList(): View =
        vertical {
            val visibleDevices = mutableListOf<SpineBraceDevice>()
            connectedBluetoothDevice?.let(visibleDevices::add)
            bluetoothDevices.forEach { device ->
                if (visibleDevices.none { it.address.equals(device.address, ignoreCase = true) }) {
                    visibleDevices.add(device)
                }
            }
            selectedBluetoothDevice?.let { selected ->
                if (visibleDevices.none { it.address.equals(selected.address, ignoreCase = true) }) {
                    visibleDevices.add(selected)
                }
            }
            if (visibleDevices.isEmpty()) {
                addView(infoStrip("未扫描到设备。请确认支具传感器开机且蓝牙名以 WM-SP# 开头。"))
            } else {
                visibleDevices.forEachIndexed { index, device ->
                    if (index > 0) addSpace(8)
                    addView(bluetoothDeviceRow(device))
                }
            }
        }

    private fun bluetoothDeviceRow(device: SpineBraceDevice): View {
        val selected = selectedBluetoothDevice?.address.equals(device.address, ignoreCase = true)
        val connected = connectedBluetoothDevice?.address.equals(device.address, ignoreCase = true)
        return horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(if (selected || connected) adjustAlpha(P.primary, 0.08f) else P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(
                vertical {
                    addView(label(device.name, 15f, if (connected) P.primary else P.text, Typeface.BOLD))
                    addView(label("${device.address} · RSSI ${device.rssi}", 12f, P.secondary))
                },
                weightLp(1f),
            )
            addView(
                chip(
                    when {
                        connected -> "已连接"
                        selected -> "已选中"
                        else -> "选择"
                    },
                    if (connected) P.success else P.primary,
                ),
            )
            setOnClickListener {
                selectedBluetoothDevice = device
                bluetoothStatusMessage = "已选择 ${device.name}，点击连接"
                refreshBluetoothUi()
            }
        }
    }

    private fun requestBluetoothValidationData() {
        if (connectedBluetoothDevice == null) {
            bluetoothStatusMessage = "请先连接蓝牙设备，再进行数据验证"
            Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
            refreshBluetoothUi()
            return
        }
        if (deviceSyncInProgress || deviceSyncUploading) {
            bluetoothStatusMessage = "正在自动读取设备数据，请完成后再验证"
            Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
            refreshBluetoothUi()
            return
        }
        latestHistorySnapshot = null
        latestHistoryReceivedAt = null
        latestUploadValidationRows = emptyList()
        latestUploadFetchedAt = null
        latestUploadLastReadAt = null
        latestUploadHistoryHead = null
        latestUploadHistoryCount = null
        latestUploadCompletedAt = null
        latestUploadDailyCount = 0
        latestUploadTotalWornHours = 0.0
        latestUploadDeviceName = connectedBluetoothDevice?.name
        latestUploadStatus = "正在读取设备数据，准备上传云端"
        runWithBluetoothPermissions {
            startAutoDeviceDataSync()
        }
    }

    private fun bluetoothValidationHours(snapshot: SpineBraceHistorySnapshot?): List<BluetoothValidationHour> =
        bluetoothValidationHours(snapshot?.points.orEmpty())

    private fun bluetoothValidationHours(points: List<SpineBraceWearPoint>): List<BluetoothValidationHour> {
        if (points.isEmpty()) {
            return emptyList()
        }
        val grouped =
            points.groupBy { point ->
                point.recordedAt.withMinute(0).withSecond(0).withNano(0)
            }
        var cursor = grouped.keys.minOrNull() ?: return emptyList()
        val end = grouped.keys.maxOrNull() ?: return emptyList()
        val rows = mutableListOf<BluetoothValidationHour>()
        while (!cursor.isAfter(end)) {
            val samples =
                grouped[cursor]
                    .orEmpty()
                    .sortedBy { it.recordedAt }
            val wornCount = samples.count { it.worn }
            rows += BluetoothValidationHour(
                hourStart = cursor,
                wornHours = roundOne(wornCount / 6.0),
                sampleCount = samples.size.coerceIn(0, 6),
                wornCount = wornCount.coerceIn(0, 6),
                samples = samples.map { BluetoothValidationSample(it.recordedAt, it.worn) },
            )
            cursor = cursor.plusHours(1)
        }
        return rows
    }

    private fun bluetoothValidationHourRow(item: BluetoothValidationHour): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(9), dp(12), dp(9))
            addView(label(formatValidationHour(item.hourStart), 14f, P.text, Typeface.BOLD), weightLp(1f))
            addView(validationFractionChip(item))
        }

    private fun formatValidationFraction(item: BluetoothValidationHour): String {
        val sampleCount = item.sampleCount.coerceIn(0, 6)
        val wornCount = item.wornCount.coerceIn(0, sampleCount)
        val notWornCount = (sampleCount - wornCount).coerceAtLeast(0)
        return "${wornCount}/${notWornCount}"
    }

    private fun validationFractionChip(item: BluetoothValidationHour): TextView {
        val sampleCount = item.sampleCount.coerceIn(0, 6)
        val wornCount = item.wornCount.coerceIn(0, sampleCount)
        val notWornCount = (sampleCount - wornCount).coerceAtLeast(0)
        val wornText = wornCount.toString()
        val notWornText = notWornCount.toString()
        val text = "$wornText/$notWornText"
        val span = SpannableString(text).apply {
            setSpan(ForegroundColorSpan(P.success), 0, wornText.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(P.muted), wornText.length, wornText.length + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(P.danger), wornText.length + 1, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return TextView(this).apply {
            this.text = span
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            minHeight = dp(28)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(P.surface, dp(100), P.softLine)
        }
    }

    private fun formatValidationHour(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH':00'", Locale.US))

    private fun formatValidationDateTime(value: LocalDateTime): String =
        value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.US))

    private fun bluetoothValidationDisplayRows(): List<BluetoothValidationHour> {
        if (shouldShowLatestUploadValidationRows()) {
            return latestUploadValidationRows.sortedByDescending { it.hourStart }
        }
        val byHour = LinkedHashMap<LocalDateTime, BluetoothValidationHour>()
        wearCloudRecords.flatMap { it.hourlyRows }.forEach { row ->
            byHour[row.hourStart] = row
        }
        return byHour.values.sortedByDescending { it.hourStart }
    }

    private fun shouldShowLatestUploadValidationRows(): Boolean =
        latestUploadValidationRows.isNotEmpty() &&
            (deviceSyncInProgress || deviceSyncUploading) &&
            !latestUploadStatus.contains("空历史") &&
            !latestUploadStatus.contains("失败")

    private fun validationStatusChipText(
        status: String = latestUploadStatus,
        rows: List<BluetoothValidationHour> = latestUploadValidationRows,
    ): String =
        when {
            status.contains("完成") -> "完成"
            status.contains("失败") -> "失败"
            status.contains("空历史") -> "跳过"
            status.contains("云端") && rows.isNotEmpty() -> "云端"
            status.contains("上传") -> "上传中"
            status.contains("读取") -> "读取中"
            else -> "等待"
        }

    private fun validationStatusChipColor(
        status: String = latestUploadStatus,
        rows: List<BluetoothValidationHour> = latestUploadValidationRows,
    ): Int =
        when (validationStatusChipText(status, rows)) {
            "完成" -> P.success
            "失败", "跳过" -> P.danger
            "云端" -> P.primary
            "上传中", "读取中" -> P.warning
            else -> P.primary
        }

    private fun LinearLayout.addAlertsPage() {
        loadAlerts()
        addView(appHeader("消息中心", alertCenterSummaryText(), showBell = false, showBack = true, backTarget = alertsBackTarget))
        addSpace(12)
        addView(
            card {
                addView(
                    cardHeader(
                        "消息列表",
                        when {
                            alertsLoading -> "正在读取云端消息"
                            alertItems.isEmpty() && alertsLoadedChildId == child.id -> "暂无待查看消息"
                            else -> "按预警等级和时间排序"
                        },
                        chip("${unreadAlertCount()}待处理", if (unreadAlertCount() > 0) P.danger else P.success),
                    ),
                )
                when {
                    alertsLoading -> addView(infoStrip("正在读取云端消息，请稍候。"))
                    alertsStatusMessage != null -> addView(infoStrip(alertsStatusMessage ?: "消息读取失败，请稍后重试。"))
                    alertItems.isEmpty() -> addView(infoStrip("暂无消息。出现佩戴不足、皮肤问题、复诊提醒等情况后，会在这里显示。"))
                    else ->
                        alertItems.forEachIndexed { index, item ->
                            if (index > 0) addSpace(8)
                            addView(alertRow(item))
                        }
                }
            },
        )
    }

    private fun renderChildMode(): View {
        loadHomeWearData()
        val records = wearCloudRecords.sortedBy { it.date }
        val today = LocalDate.now()
        val todayRecord = records.lastOrNull { it.date == today }
        val recent7 = records.takeLast(7)
        val weekSummary = summaryFromWearRecords(recent7)
        val currentStreak = currentWearStreak(records)
        val todayHours = todayRecord?.wornHours ?: 0.0
        val todayProgress = if (todayRecord != null) wearProgressPercent(todayHours, prescribedWearHours()) else 0
        val progressText =
            when {
                todayRecord != null -> "今日进度 ${todayProgress}%"
                wearCloudLoading -> "正在读取"
                else -> "今日暂无记录"
            }
        val progressColor =
            when {
                todayRecord != null && isWearCompliant(todayRecord) -> P.success
                todayRecord != null && todayProgress >= 80 -> P.primary
                todayRecord != null -> P.warning
                else -> P.muted
            }
        val badges = childModeBadges(todayRecord, recent7, currentStreak)
        return screenPage(showBottomPadding = false) {
            addView(
                horizontal {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(secondaryButton("返回") {
                        currentTab = MainTab.Me
                        render()
                    }, widthLp(dp(92)))
                    addView(Space(this@MainActivity), weightLp(1f))
                    addView(iconButton("⌂", "返回首页") {
                        currentTab = MainTab.Home
                        render()
                    }, widthHeightLp(dp(48), dp(48)))
                },
            )
            addSpace(12)
            addView(
                vertical {
                    background = rounded(0xFF365F71.toInt(), dp(24), null)
                    setPadding(dp(22), dp(22), dp(22), dp(22))
                    addView(chip(progressText, progressColor))
                    addSpace(12)
                    addView(label(childModeHeroTitle(todayRecord, todayProgress), 25f, Color.WHITE, Typeface.BOLD))
                    addView(label(childModeHeroSubtitle(todayRecord, recent7, currentStreak), 15f, 0xFFEAF7F7.toInt()))
                },
            )
            addSpace(12)
            addView(
                metricGrid(
                    listOf(
                        dayCount(currentStreak) to "连续达标",
                        "${formatHours(weekSummary.avgHours)}h" to "近7天日均",
                        "${weekSummary.complianceRate}%" to "近7天达标",
                    ),
                ),
            )
            addSpace(12)
            addView(
                card {
                    addView(cardHeader("阶段徽章", if (records.isEmpty()) "同步数据后点亮" else "根据云端佩戴自动点亮", null))
                    addView(
                        grid(3) {
                            badges.forEach { badge ->
                                addQuick(badge) {
                                    Toast.makeText(this@MainActivity, uiText("已点亮$badge"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                    )
                },
            )
            addSpace(12)
            addView(
                card {
                    addView(label("今晚的小目标", 17f, P.text, Typeface.BOLD))
                    addSpace(8)
                    addView(label(childModeGoalText(todayRecord, recent7), 15f, P.secondary))
                },
            )
        }
    }

    private fun childModeHeroTitle(todayRecord: WearRecord?, todayProgress: Int): String {
        val name = child.nickname.ifBlank { "孩子" }
        return when {
            todayRecord == null && wearCloudLoading -> "${name}，正在读取今天的进度"
            todayRecord == null -> "${name}，今天还没有同步数据"
            isWearCompliant(todayRecord) -> "${name}，今天已经达标了"
            else -> "${name}，今天已完成${todayProgress}%"
        }
    }

    private fun childModeHeroSubtitle(todayRecord: WearRecord?, recent7: List<WearRecord>, currentStreak: Int): String =
        when {
            todayRecord == null && wearCloudLoading -> "连接云端后，会自动显示今天的佩戴进度和徽章。"
            todayRecord == null -> "先连接蓝牙同步今天的数据，让进度条亮起来。"
            isWearCompliant(todayRecord) -> "连续${currentStreak}天达标，睡前保持佩戴并做一次皮肤自查。"
            else -> {
                val gap = (prescribedWearHours() - todayRecord.wornHours).coerceAtLeast(0.0)
                val gapHint = topGapHours(recent7).firstOrNull()
                if (gapHint != null) {
                    "今天还差约${formatHours(gap)}h，优先守住$gapHint 这个容易缺口的时段。"
                } else {
                    "今天还差约${formatHours(gap)}h，把剩余目标拆成饭后和睡前两段完成。"
                }
            }
        }

    private fun childModeGoalText(todayRecord: WearRecord?, recent7: List<WearRecord>): String =
        when {
            todayRecord == null && wearCloudLoading -> "正在读取云端佩戴数据，读取完成后自动生成今晚目标。"
            todayRecord == null -> "先完成一次蓝牙读取并上传，今晚目标会根据真实数据自动生成。"
            isWearCompliant(todayRecord) -> "保持当前佩戴节奏，睡前检查腰部、背部和肩部皮肤。"
            else -> {
                val gap = (prescribedWearHours() - todayRecord.wornHours).coerceAtLeast(0.0)
                val gapHint = topGapHours(recent7).firstOrNull()
                if (gapHint != null) {
                    "今晚先补足${formatHours(gap)}h中的一部分，重点关注$gapHint。"
                } else {
                    "今晚先补足${formatHours(gap)}h中的一部分，完成后再同步一次数据。"
                }
            }
        }

    private fun childModeBadges(todayRecord: WearRecord?, recent7: List<WearRecord>, currentStreak: Int): List<String> =
        listOf(
            if (todayRecord != null && isWearCompliant(todayRecord)) "今日达标" else "今日冲刺",
            if (currentStreak >= 2) "${currentStreak}天连击" else "稳定起步",
            if (recent7.isNotEmpty() && summaryFromWearRecords(recent7).complianceRate >= 80) "本周很稳" else "继续升级",
        )

    private fun currentWearStreak(records: List<WearRecord>): Int {
        var streak = 0
        for (record in records.sortedByDescending { it.date }) {
            if (isWearCompliant(record)) {
                streak += 1
            } else {
                break
            }
        }
        return streak
    }

    private fun bottomNavigation(): View {
        return horizontal {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(10))
            background = rounded(P.surface, dp(18), P.softLine)
            elevation = dp(10).toFloat()
            listOf(
                Triple(MainTab.Home, "⌂", "首页"),
                Triple(MainTab.Reports, "▤", "报告"),
                Triple(MainTab.Consult, "◇", "咨询"),
                Triple(MainTab.Logs, "✎", "记录"),
                Triple(MainTab.Me, "●", "我的"),
            ).forEach { (tab, icon, label) ->
                addView(navButton(icon, label, tab == currentTab) {
                    currentTab = tab
                    render()
                }, weightLp(1f))
            }
        }
    }

    private fun refreshBluetoothUi() {
        if (stage == Stage.Device || (stage == Stage.App && (currentTab == MainTab.Settings || currentTab == MainTab.BluetoothValidation))) {
            render()
        }
    }

    private fun runWithBluetoothPermissions(action: () -> Unit) {
        val missing =
            requiredBluetoothPermissions().filter { permission ->
                ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED
            }
        if (missing.isEmpty()) {
            action()
        } else {
            pendingBluetoothAction = action
            bluetoothPermissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun runWithCameraPermission(action: () -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            action()
        } else {
            pendingCameraAction = action
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requiredBluetoothPermissions(): List<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    private fun autoConnectLastBluetoothDevice() {
        if (connectedBluetoothDevice != null || autoConnectInProgress) {
            return
        }
        val lastDevice = loadLastBluetoothDevice() ?: return
        autoConnectTargetDevice = lastDevice
        autoConnectInProgress = true
        selectedBluetoothDevice = lastDevice
        bluetoothStatusMessage = "正在自动连接上次设备 ${lastDevice.name}..."
        refreshBluetoothUi()
        runWithBluetoothPermissions {
            if (connectedBluetoothDevice == null) {
                spineBraceBluetooth.startScan(AUTO_CONNECT_SCAN_TIMEOUT_MS)
                scheduleAutoConnectTimeout()
            }
        }
    }

    private fun maybeConnectAutoTargetDevice(devices: List<SpineBraceDevice>) {
        val target = autoConnectTargetDevice ?: return
        if (connectedBluetoothDevice != null) {
            cancelAutoConnectTimeout()
            autoConnectInProgress = false
            autoConnectTargetDevice = null
            return
        }
        val matched =
            devices.firstOrNull { device ->
                device.matchesBluetoothDevice(target)
            } ?: return
        cancelAutoConnectTimeout()
        selectedBluetoothDevice = matched
        bluetoothStatusMessage = "正在自动连接上次设备 ${matched.name}..."
        spineBraceBluetooth.connect(matched)
    }

    private fun scheduleAutoConnectTimeout() {
        cancelAutoConnectTimeout()
        val timeoutRunnable =
            Runnable {
                if (autoConnectInProgress && connectedBluetoothDevice == null) {
                    autoConnectInProgress = false
                    autoConnectTargetDevice = null
                    spineBraceBluetooth.stopScan()
                    bluetoothStatusMessage = AUTO_CONNECT_NOT_FOUND_MESSAGE
                    setHomeBluetoothWarning(AUTO_CONNECT_NOT_FOUND_MESSAGE)
                    Toast.makeText(this, AUTO_CONNECT_NOT_FOUND_MESSAGE, Toast.LENGTH_SHORT).show()
                    refreshBluetoothUi()
                }
            }
        autoConnectTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, AUTO_CONNECT_SCAN_TIMEOUT_MS)
    }

    private fun cancelAutoConnectTimeout() {
        autoConnectTimeoutRunnable?.let(mainHandler::removeCallbacks)
        autoConnectTimeoutRunnable = null
    }

    private fun scheduleBluetoothAutoDisconnect() {
        cancelBluetoothAutoDisconnect()
        val disconnectRunnable =
            object : Runnable {
                override fun run() {
                if (connectedBluetoothDevice != null) {
                    if (deviceSyncInProgress || deviceSyncUploading) {
                        autoDisconnectRunnable = this
                        mainHandler.postDelayed(this, BLUETOOTH_AUTO_DISCONNECT_DEFER_MS)
                        return
                    }
                    pendingAutoDisconnectNotice = true
                    spineBraceBluetooth.disconnect()
                }
                }
            }
        autoDisconnectRunnable = disconnectRunnable
        mainHandler.postDelayed(disconnectRunnable, BLUETOOTH_AUTO_DISCONNECT_MS)
    }

    private fun cancelBluetoothAutoDisconnect() {
        autoDisconnectRunnable?.let(mainHandler::removeCallbacks)
        autoDisconnectRunnable = null
    }

    private fun updateBatteryHomeWarning(telemetry: SpineBraceTelemetry) {
        if (telemetry.batteryPercent in 0 until 20) {
            setHomeBluetoothWarning(LOW_BATTERY_WARNING_MESSAGE)
        } else if (bluetoothHomeWarningMessage == LOW_BATTERY_WARNING_MESSAGE) {
            setHomeBluetoothWarning(null)
        }
    }

    private fun setHomeBluetoothWarning(message: String?) {
        if (bluetoothHomeWarningMessage == message) {
            return
        }
        bluetoothHomeWarningMessage = message
        if (stage == Stage.App && currentTab == MainTab.Home) {
            render()
        }
    }

    private fun saveLastBluetoothDevice(device: SpineBraceDevice) {
        prefs.edit()
            .putString(KEY_LAST_BLUETOOTH_NAME, device.name)
            .putString(KEY_LAST_BLUETOOTH_ADDRESS, device.address)
            .apply()
    }

    private fun loadLastBluetoothDevice(): SpineBraceDevice? {
        val name = prefs.getString(KEY_LAST_BLUETOOTH_NAME, null)?.takeIf { it.isNotBlank() } ?: return null
        val address = prefs.getString(KEY_LAST_BLUETOOTH_ADDRESS, null)?.takeIf { it.isNotBlank() } ?: return null
        return SpineBraceDevice(name = name, address = address, rssi = 0)
    }

    private fun hasBoundBluetoothDevice(): Boolean =
        connectedBluetoothDevice != null || loadLastBluetoothDevice() != null

    private fun setBluetoothButtonEnabled(button: Button?, enabled: Boolean) {
        button?.isEnabled = enabled
        button?.alpha = if (enabled) 1.0f else 0.42f
    }

    private fun SpineBraceDevice.matchesBluetoothDevice(other: SpineBraceDevice): Boolean =
        address.equals(other.address, ignoreCase = true) || name.equals(other.name, ignoreCase = true)

    private fun markDeviceSyncPhase(phase: String, status: String? = null) {
        prefs.edit()
            .putString(KEY_DEVICE_SYNC_PHASE, phase)
            .putString(KEY_DEVICE_SYNC_UPDATED_AT, LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .putString(KEY_DEVICE_SYNC_DEVICE_NAME, latestUploadDeviceName ?: connectedBluetoothDevice?.name ?: selectedBluetoothDevice?.name.orEmpty())
            .putString(KEY_DEVICE_SYNC_STATUS, status ?: latestUploadStatus)
            .apply()
    }

    private fun markDeviceSyncInterrupted(status: String) {
        markDeviceSyncPhase(DEVICE_SYNC_PHASE_INTERRUPTED, status)
        latestUploadStatus = status
        bluetoothStatusMessage = status
        setHomeBluetoothWarning(DEVICE_SYNC_INTERRUPTED_WARNING_MESSAGE)
    }

    private fun clearDeviceSyncPhase() {
        prefs.edit()
            .remove(KEY_DEVICE_SYNC_PHASE)
            .remove(KEY_DEVICE_SYNC_UPDATED_AT)
            .remove(KEY_DEVICE_SYNC_DEVICE_NAME)
            .remove(KEY_DEVICE_SYNC_STATUS)
            .apply()
        if (bluetoothHomeWarningMessage == DEVICE_SYNC_INTERRUPTED_WARNING_MESSAGE) {
            setHomeBluetoothWarning(null)
        }
    }

    private fun recoverPendingDeviceSyncState() {
        val phase = prefs.getString(KEY_DEVICE_SYNC_PHASE, null)?.takeIf { it.isNotBlank() } ?: return
        val deviceName = prefs.getString(KEY_DEVICE_SYNC_DEVICE_NAME, null).orEmpty()
        val phaseText =
            when (phase) {
                DEVICE_SYNC_PHASE_READING -> "读取设备数据"
                DEVICE_SYNC_PHASE_LOCAL_SAVING -> "写入手机本地"
                DEVICE_SYNC_PHASE_LOCAL_VERIFYING -> "核对手机本地数据"
                DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD -> "等待本地上传云端"
                DEVICE_SYNC_PHASE_CLOUD_UPLOADING -> "从手机本地上传云端"
                DEVICE_SYNC_PHASE_VERIFYING -> "核对云端数据"
                DEVICE_SYNC_PHASE_CLEARING -> "清理设备历史"
                else -> "同步"
            }
        bluetoothStatusMessage =
            if (deviceName.isBlank()) {
                "上次数据获取在${phaseText}阶段未安全完成；设备历史数据已保留，请靠近设备重新连接。"
            } else {
                "上次 ${deviceName} 数据获取在${phaseText}阶段未安全完成；设备历史数据已保留，请靠近设备重新连接。"
            }
        latestUploadStatus = bluetoothStatusMessage
        setHomeBluetoothWarning(DEVICE_SYNC_INTERRUPTED_WARNING_MESSAGE)
    }

    private fun startAutoDeviceDataSync(resetRetryCounters: Boolean = true) {
        pendingHistoryUploadRunnable?.let(mainHandler::removeCallbacks)
        pendingHistoryUploadRunnable = null
        cancelHistoryReadTimeout()
        if (resetRetryCounters) {
            deviceHistoryReadRetries = 0
            deviceSyncReconnectRetries = 0
            resumingDeviceSyncAfterDisconnect = false
        } else {
            deviceHistoryReadRetries = 0
        }
        deviceSyncInProgress = true
        deviceSyncUploading = false
        deviceSyncCompleted = false
        latestHistorySnapshot = null
        latestHistoryReceivedAt = null
        latestUploadStatus = "正在读取设备数据，准备上传云端"
        latestUploadDeviceName = connectedBluetoothDevice?.name ?: selectedBluetoothDevice?.name
        bluetoothStatusMessage = deviceReadingMessage
        markDeviceSyncPhase(DEVICE_SYNC_PHASE_READING, latestUploadStatus)
        Toast.makeText(this, deviceReadingMessage, Toast.LENGTH_SHORT).show()
        refreshBluetoothUi()
        requestDeviceHistoryForSync()
    }

    private fun requestDeviceHistoryForSync() {
        val sent = spineBraceBluetooth.requestHistory(reportStatus = false)
        if (!sent) {
            deviceSyncInProgress = false
            deviceSyncUploading = false
            deviceSyncCompleted = false
            latestUploadStatus = "设备数据读取指令发送失败，请保持蓝牙连接后重试"
            bluetoothStatusMessage = latestUploadStatus
            markDeviceSyncInterrupted(latestUploadStatus)
            Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
            refreshBluetoothUi()
            return
        }
        scheduleHistoryReadTimeout()
    }

    private fun scheduleHistoryReadTimeout() {
        cancelHistoryReadTimeout()
        val snapshot = latestHistorySnapshot
        val timeoutMs =
            if (snapshot == null || snapshot.packetCount == 0) {
                DEVICE_HISTORY_INITIAL_TIMEOUT_MS
            } else {
                DEVICE_HISTORY_PACKET_IDLE_TIMEOUT_MS
            }
        val timeoutRunnable =
            Runnable {
                historyReadTimeoutRunnable = null
                if (!deviceSyncInProgress || deviceSyncUploading || deviceSyncCompleted) {
                    return@Runnable
                }
                latestHistorySnapshot?.takeIf { it.complete }?.let { snapshot ->
                    scheduleDeviceHistoryUpload(snapshot)
                    return@Runnable
                }
                val currentSnapshot = latestHistorySnapshot
                if (connectedBluetoothDevice != null && currentSnapshot?.header == null && deviceHistoryReadRetries < MAX_DEVICE_HISTORY_READ_RETRIES) {
                    deviceHistoryReadRetries += 1
                    latestHistorySnapshot = null
                    latestHistoryReceivedAt = null
                    bluetoothStatusMessage = "设备数据读取未完成，正在重新请求"
                    latestUploadStatus = bluetoothStatusMessage
                    markDeviceSyncPhase(DEVICE_SYNC_PHASE_READING, latestUploadStatus)
                    refreshBluetoothUi()
                    requestDeviceHistoryForSync()
                    return@Runnable
                }
                deviceSyncInProgress = false
                deviceSyncUploading = false
                deviceSyncCompleted = false
                resumingDeviceSyncAfterDisconnect = false
                latestUploadStatus =
                    if (currentSnapshot?.header != null && currentSnapshot.packetCount <= 1) {
                        "已收到设备头包，但未收到佩戴数据包；已停止自动重试，请保持设备靠近后重新获取。"
                    } else {
                        "设备数据读取超时，未完成上传；已停止自动重试，请保持设备靠近后重新获取。"
                    }
                bluetoothStatusMessage = latestUploadStatus
                markDeviceSyncInterrupted(latestUploadStatus)
                Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
                refreshBluetoothUi()
            }
        historyReadTimeoutRunnable = timeoutRunnable
        mainHandler.postDelayed(timeoutRunnable, timeoutMs)
    }

    private fun cancelHistoryReadTimeout() {
        historyReadTimeoutRunnable?.let(mainHandler::removeCallbacks)
        historyReadTimeoutRunnable = null
    }

    private fun handleDeviceSyncDisconnected(): Boolean {
        if ((!deviceSyncInProgress && !deviceSyncUploading) || deviceSyncCompleted || pendingAutoDisconnectNotice) {
            return false
        }
        val phase = prefs.getString(KEY_DEVICE_SYNC_PHASE, null)
        if (deviceSyncUploading &&
            pendingDeviceWearFile().exists() &&
            phase in setOf(DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, DEVICE_SYNC_PHASE_CLOUD_UPLOADING)
        ) {
            connectedBluetoothDevice = null
            latestBraceTelemetry = null
            cancelAutoConnectTimeout()
            cancelBluetoothAutoDisconnect()
            bluetoothStatusMessage = "蓝牙已断开，正在继续从手机本地上传云端"
            latestUploadStatus = bluetoothStatusMessage
            refreshBluetoothUi()
            return true
        }
        pendingHistoryUploadRunnable?.let(mainHandler::removeCallbacks)
        pendingHistoryUploadRunnable = null
        cancelHistoryReadTimeout()
        connectedBluetoothDevice = null
        latestBraceTelemetry = null
        deviceSyncUploading = false
        deviceSyncInProgress = false
        deviceSyncCompleted = false
        resumingDeviceSyncAfterDisconnect = false
        cancelAutoConnectTimeout()
        cancelBluetoothAutoDisconnect()
        bluetoothStatusMessage = "蓝牙读取中断，已停止自动重试，请重新连接后获取。"
        latestUploadStatus = "蓝牙读取中断，未完成上传。"
        markDeviceSyncInterrupted(latestUploadStatus)
        Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
        refreshBluetoothUi()
        return true
    }

    private fun retryDeviceSyncConnection(message: String) {
        val retryTarget = selectedBluetoothDevice ?: loadLastBluetoothDevice()
        if (retryTarget == null || deviceSyncReconnectRetries >= MAX_DEVICE_SYNC_RECONNECT_RETRIES) {
            deviceSyncInProgress = false
            deviceSyncUploading = false
            deviceSyncCompleted = false
            resumingDeviceSyncAfterDisconnect = false
            bluetoothStatusMessage = "蓝牙读取中断，请重新扫描连接设备后再读取。"
            latestUploadStatus = "蓝牙读取中断，未完成上传"
            Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
            refreshBluetoothUi()
            return
        }
        deviceSyncReconnectRetries += 1
        resumingDeviceSyncAfterDisconnect = true
        selectedBluetoothDevice = retryTarget
        bluetoothStatusMessage = "$message（第${deviceSyncReconnectRetries}次）"
        latestUploadStatus = bluetoothStatusMessage
        refreshBluetoothUi()
        mainHandler.postDelayed(
            {
                if (deviceSyncInProgress && !deviceSyncCompleted) {
                    runWithBluetoothPermissions {
                        spineBraceBluetooth.connect(retryTarget)
                    }
                }
            },
            DEVICE_SYNC_RECONNECT_DELAY_MS,
        )
    }

    private fun scheduleDeviceHistoryUpload(snapshot: SpineBraceHistorySnapshot) {
        if (deviceSyncUploading || deviceSyncCompleted) {
            return
        }
        if (!snapshot.complete) {
            scheduleHistoryReadTimeout()
            return
        }
        cancelHistoryReadTimeout()
        pendingHistoryUploadRunnable?.let(mainHandler::removeCallbacks)
        val runnable =
            Runnable {
                pendingHistoryUploadRunnable = null
                uploadDeviceHistorySnapshot(snapshot)
            }
        pendingHistoryUploadRunnable = runnable
        val delayMs = if (snapshot.complete || snapshot.packetCount >= 37) 800L else 2_500L
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun uploadDeviceHistorySnapshot(snapshot: SpineBraceHistorySnapshot) {
        if (deviceSyncUploading || deviceSyncCompleted) {
            return
        }
        val device = connectedBluetoothDevice
        val uploadPackage = buildDeviceWearUploadPayload(snapshot, device)
        if (uploadPackage.dailyCount == 0) {
            updateBluetoothUploadValidation(uploadPackage, "空历史，未上传云端", completed = true)
            deviceSyncInProgress = false
            deviceSyncUploading = false
            deviceSyncCompleted = true
            bluetoothStatusMessage = deviceReadCompleteMessage
            clearDeviceSyncPhase()
            loadHomeWearData(force = true)
            Toast.makeText(this, deviceReadCompleteMessage, Toast.LENGTH_SHORT).show()
            refreshBluetoothUi()
            return
        }
        if (snapshotLooksLikeClearedDeviceHistory(snapshot)) {
            updateBluetoothUploadValidation(uploadPackage, "空历史，未上传云端", completed = true)
            deviceSyncInProgress = false
            deviceSyncUploading = false
            deviceSyncCompleted = true
            bluetoothStatusMessage = deviceReadCompleteMessage
            clearDeviceSyncPhase()
            loadHomeWearData(force = true)
            Toast.makeText(this, deviceReadCompleteMessage, Toast.LENGTH_SHORT).show()
            refreshBluetoothUi()
            return
        }
        val localVerification =
            runCatching {
                markDeviceSyncPhase(DEVICE_SYNC_PHASE_LOCAL_SAVING, "正在写入手机本地存储")
                saveDeviceWearPackageToLocal(uploadPackage)
                markDeviceSyncPhase(DEVICE_SYNC_PHASE_LOCAL_VERIFYING, "正在核对手机本地数据")
                verifyLocalDeviceWearPackage(uploadPackage)
            }.getOrElse { error ->
                deviceSyncInProgress = false
                deviceSyncUploading = false
                deviceSyncCompleted = false
                bluetoothStatusMessage = "手机本地保存失败，未清理设备历史；请重新获取"
                updateBluetoothUploadValidation(uploadPackage, "手机本地保存失败：${error.message.orEmpty()}", completed = true)
                markDeviceSyncInterrupted(bluetoothStatusMessage)
                Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
                refreshBluetoothUi()
                return
            }
        if (!localVerification.verified) {
            deviceSyncInProgress = false
            deviceSyncUploading = false
            deviceSyncCompleted = false
            bluetoothStatusMessage = "手机本地核对失败，未清理设备历史；请重新获取"
            updateBluetoothUploadValidation(
                uploadPackage,
                "本地核对失败：匹配${localVerification.matchedCount}/${localVerification.expectedCount}，缺失${localVerification.missingCount}，不一致${localVerification.mismatchedCount}",
                completed = true,
            )
            markDeviceSyncInterrupted(bluetoothStatusMessage)
            Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
            refreshBluetoothUi()
            return
        }
        updateBluetoothUploadValidation(uploadPackage, "手机本地核对完成，正在清理设备历史", completed = false)
        markDeviceSyncPhase(DEVICE_SYNC_PHASE_CLEARING, "手机本地已核对，正在清理设备历史")
        refreshBluetoothUi()
        val cleared = spineBraceBluetooth.clearMonthlyDataWithCurrentTime(reportStatus = false)
        if (!cleared) {
            deviceSyncInProgress = false
            deviceSyncUploading = false
            deviceSyncCompleted = false
            bluetoothStatusMessage = "设备历史清理失败；手机本地数据已保存，请保持蓝牙连接后重新获取"
            updateBluetoothUploadValidation(uploadPackage, "设备清理失败，手机本地数据已保留", completed = true)
            markDeviceSyncInterrupted(bluetoothStatusMessage)
            Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
            refreshBluetoothUi()
            return
        }
        markDeviceSyncPhase(DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, "设备历史已清理，等待从手机本地上传云端")
        pendingAutoDisconnectNotice = true
        spineBraceBluetooth.disconnect()
        uploadLocalDeviceWearPackage(uploadPackage)
    }

    private fun pendingDeviceWearFile(): File =
        File(filesDir, LOCAL_DEVICE_WEAR_FILE_NAME)

    private fun deviceWearSessionTempFile(): File =
        File(filesDir, LOCAL_DEVICE_WEAR_SESSION_TEMP_FILE_NAME)

    private fun localDeviceWearArchiveFile(): File =
        File(filesDir, LOCAL_DEVICE_WEAR_ARCHIVE_FILE_NAME)

    private fun saveDeviceHistorySessionTemp(snapshot: SpineBraceHistorySnapshot) {
        val header = snapshot.header
        val points = JSONArray()
        snapshot.points.forEach { point ->
            points.put(
                JSONObject()
                    .put("recorded_at", point.recordedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                    .put("worn", point.worn),
            )
        }
        val rawPackets = JSONArray()
        snapshot.rawPacketsHex.forEach(rawPackets::put)
        val sequences = JSONArray()
        snapshot.payloadSequences.forEach(sequences::put)
        val envelope =
            JSONObject()
                .put("version", 1)
                .put("updated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("device_name", connectedBluetoothDevice?.name ?: selectedBluetoothDevice?.name ?: JSONObject.NULL)
                .put("device_address", connectedBluetoothDevice?.address ?: selectedBluetoothDevice?.address ?: JSONObject.NULL)
                .put("complete", snapshot.complete)
                .put("packet_count", snapshot.packetCount)
                .put("total_bits", snapshot.totalBits)
                .put("worn_bits", snapshot.wornBits)
                .put("payload_sequences", sequences)
                .put("raw_packets_hex", rawPackets)
                .put("points", points)
                .put(
                    "header",
                    JSONObject()
                        .put("head", header?.head ?: JSONObject.NULL)
                        .put("count", header?.count ?: JSONObject.NULL)
                        .put("last_read_at", header?.lastReadAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: header?.lastReadAtText ?: JSONObject.NULL)
                        .put("device_time", header?.deviceTimeText ?: JSONObject.NULL)
                        .put("next_save_seconds", header?.nextSaveSeconds ?: JSONObject.NULL),
                )
        val finalFile = deviceWearSessionTempFile()
        val tempFile = File(filesDir, "$LOCAL_DEVICE_WEAR_SESSION_TEMP_FILE_NAME.tmp")
        runCatching {
            tempFile.writeText(envelope.toString(), Charsets.UTF_8)
            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!tempFile.renameTo(finalFile)) {
                finalFile.writeText(envelope.toString(), Charsets.UTF_8)
                tempFile.delete()
            }
        }
    }

    private fun deleteDeviceHistorySessionTemp() {
        deviceWearSessionTempFile().takeIf { it.exists() }?.delete()
    }

    private fun saveDeviceWearPackageToLocal(uploadPackage: DeviceWearUploadPackage) {
        val envelope = buildDeviceWearLocalEnvelope(uploadPackage)
        val finalFile = pendingDeviceWearFile()
        val tempFile = File(filesDir, "$LOCAL_DEVICE_WEAR_FILE_NAME.tmp")
        tempFile.writeText(envelope.toString(), Charsets.UTF_8)
        if (finalFile.exists() && !finalFile.delete()) {
            throw IllegalStateException("无法替换旧的本地数据")
        }
        if (!tempFile.renameTo(finalFile)) {
            finalFile.writeText(envelope.toString(), Charsets.UTF_8)
            tempFile.delete()
        }
        appendDeviceWearPackageToArchive(envelope)
        deleteDeviceHistorySessionTemp()
    }

    private fun buildDeviceWearLocalEnvelope(uploadPackage: DeviceWearUploadPackage): JSONObject =
        JSONObject()
            .put("version", 1)
            .put("sync_id", localDeviceWearSyncId(uploadPackage))
            .put("saved_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
            .put("child_id", uploadPackage.childId)
            .put("device_name", uploadPackage.deviceName ?: JSONObject.NULL)
            .put("sample_count", uploadPackage.expectedWearPoints.size)
            .put("worn_count", uploadPackage.expectedWearPoints.count { it.worn })
            .put("cloud_upload_status", "pending")
            .put("cloud_verified", false)
            .put("source", "bluetooth_device")
            .put("raw_bluetooth_session", localSessionJsonOrNull() ?: JSONObject.NULL)
            .put("payload", uploadPackage.payload)

    private fun localSessionJsonOrNull(): JSONObject? {
        val file = deviceWearSessionTempFile()
        return if (file.exists()) {
            runCatching { JSONObject(file.readText(Charsets.UTF_8)) }.getOrNull()
        } else {
            null
        }
    }

    private fun localDeviceWearSyncId(uploadPackage: DeviceWearUploadPackage): String {
        val points = uploadPackage.expectedWearPoints.sortedBy { it.recordedAt }
        val first = points.firstOrNull()?.recordedAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).orEmpty()
        val last = points.lastOrNull()?.recordedAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).orEmpty()
        val wornCount = points.count { it.worn }
        return listOf(
            uploadPackage.childId,
            uploadPackage.deviceName.orEmpty(),
            first,
            last,
            points.size.toString(),
            wornCount.toString(),
            uploadPackage.historyHead?.toString().orEmpty(),
            uploadPackage.historyCount?.toString().orEmpty(),
        ).joinToString("|")
    }

    private fun appendDeviceWearPackageToArchive(envelope: JSONObject) {
        val archiveFile = localDeviceWearArchiveFile()
        val archive =
            if (archiveFile.exists()) {
                runCatching { JSONObject(archiveFile.readText(Charsets.UTF_8)) }.getOrElse { JSONObject() }
            } else {
                JSONObject()
            }
        val items = archive.optJSONArray("items") ?: JSONArray()
        val syncId = envelope.optString("sync_id", "")
        var replaced = false
        val mergedItems = JSONArray()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (syncId.isNotBlank() && item.optString("sync_id", "") == syncId) {
                mergedItems.put(envelope)
                replaced = true
            } else {
                mergedItems.put(item)
            }
        }
        if (!replaced) {
            mergedItems.put(envelope)
        }
        val updatedArchive =
            JSONObject()
                .put("version", 1)
                .put("updated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("items", mergedItems)
        val tempFile = File(filesDir, "$LOCAL_DEVICE_WEAR_ARCHIVE_FILE_NAME.tmp")
        tempFile.writeText(updatedArchive.toString(), Charsets.UTF_8)
        if (archiveFile.exists() && !archiveFile.delete()) {
            throw IllegalStateException("无法更新手机本地长期佩戴归档")
        }
        if (!tempFile.renameTo(archiveFile)) {
            archiveFile.writeText(updatedArchive.toString(), Charsets.UTF_8)
            tempFile.delete()
        }
    }

    private fun updateDeviceWearArchiveUploadStatus(
        uploadPackage: DeviceWearUploadPackage,
        status: String,
        cloudVerified: Boolean,
        note: String? = null,
    ) {
        val archiveFile = localDeviceWearArchiveFile()
        if (!archiveFile.exists()) {
            return
        }
        val archive = runCatching { JSONObject(archiveFile.readText(Charsets.UTF_8)) }.getOrNull() ?: return
        val items = archive.optJSONArray("items") ?: return
        val syncId = localDeviceWearSyncId(uploadPackage)
        var changed = false
        val updatedItems = JSONArray()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            if (item.optString("sync_id", "") == syncId) {
                item.put("cloud_upload_status", status)
                item.put("cloud_verified", cloudVerified)
                item.put("cloud_status_updated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                note?.let { item.put("cloud_status_note", it) }
                changed = true
            }
            updatedItems.put(item)
        }
        if (!changed) {
            return
        }
        val updatedArchive =
            JSONObject()
                .put("version", archive.optInt("version", 1))
                .put("updated_at", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .put("items", updatedItems)
        val tempFile = File(filesDir, "$LOCAL_DEVICE_WEAR_ARCHIVE_FILE_NAME.tmp")
        tempFile.writeText(updatedArchive.toString(), Charsets.UTF_8)
        if (archiveFile.exists()) {
            archiveFile.delete()
        }
        if (!tempFile.renameTo(archiveFile)) {
            archiveFile.writeText(updatedArchive.toString(), Charsets.UTF_8)
            tempFile.delete()
        }
    }

    private fun loadLocalDeviceWearPackage(): DeviceWearUploadPackage? {
        val file = pendingDeviceWearFile()
        if (!file.exists()) {
            return null
        }
        val envelope =
            runCatching {
                JSONObject(file.readText(Charsets.UTF_8))
            }.getOrNull() ?: return null
        val payload = envelope.optJSONObject("payload") ?: return null
        val rows = payloadHourlyRows(payload)
        val points = localWearPointsFromPayload(payload)
        val raw = payload.optJSONObject("raw")
        val records = payload.optJSONArray("records") ?: JSONArray()
        val deviceName =
            payload.optString("device_name", "")
                .takeIf { it.isNotBlank() && it != "null" }
        return DeviceWearUploadPackage(
            childId = payload.optString("child_id", child.id).ifBlank { child.id },
            payload = payload,
            hourlyRows = rows,
            expectedWearPoints = points,
            fetchedAt = parseCloudDateTime(envelope.optString("saved_at", "")) ?: LocalDateTime.now(),
            lastReadAt = parseWearRawLastReadAt(JSONObject().put("raw", raw ?: JSONObject())),
            historyHead = parseWearRawInt(JSONObject().put("raw", raw ?: JSONObject()), "history_head"),
            historyCount = parseWearRawInt(JSONObject().put("raw", raw ?: JSONObject()), "history_count"),
            dailyCount = records.length(),
            totalWornHours = roundOne(rows.sumOf { it.wornHours }),
            deviceName = deviceName,
        )
    }

    private fun deleteLocalDeviceWearPackage() {
        pendingDeviceWearFile().takeIf { it.exists() }?.delete()
    }

    private fun verifyLocalDeviceWearPackage(uploadPackage: DeviceWearUploadPackage): DeviceWearLocalVerification {
        val localPackage =
            loadLocalDeviceWearPackage()
                ?: return DeviceWearLocalVerification(
                    verified = false,
                    expectedCount = uploadPackage.expectedWearPoints.size,
                    matchedCount = 0,
                    missingCount = uploadPackage.expectedWearPoints.size,
                    mismatchedCount = 0,
                )
        val expected = normalizedWearPointMap(uploadPackage.expectedWearPoints)
        val local = normalizedWearPointMap(localPackage.expectedWearPoints)
        var matchedCount = 0
        var missingCount = 0
        var mismatchedCount = 0
        expected.forEach { (slot, worn) ->
            val localWorn = local[slot]
            when {
                localWorn == null -> missingCount += 1
                localWorn == worn -> matchedCount += 1
                else -> mismatchedCount += 1
            }
        }
        return DeviceWearLocalVerification(
            verified = expected.isNotEmpty() && matchedCount == expected.size && missingCount == 0 && mismatchedCount == 0,
            expectedCount = expected.size,
            matchedCount = matchedCount,
            missingCount = missingCount,
            mismatchedCount = mismatchedCount,
        )
    }

    private fun resumePendingLocalWearUploadIfNeeded(): Boolean {
        val phase = prefs.getString(KEY_DEVICE_SYNC_PHASE, null)
        if (!pendingDeviceWearFile().exists()) {
            return false
        }
        if (phase !in setOf(DEVICE_SYNC_PHASE_CLEARING, DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, DEVICE_SYNC_PHASE_CLOUD_UPLOADING)) {
            return false
        }
        val uploadPackage = loadLocalDeviceWearPackage() ?: return false
        latestUploadDeviceName = uploadPackage.deviceName
        latestUploadStatus = "检测到手机本地有待上传佩戴数据，正在上传云端"
        bluetoothStatusMessage = latestUploadStatus
        setHomeBluetoothWarning(DEVICE_SYNC_INTERRUPTED_WARNING_MESSAGE)
        updateBluetoothUploadValidation(uploadPackage, latestUploadStatus, completed = false)
        uploadLocalDeviceWearPackage(uploadPackage)
        return true
    }

    private fun uploadLocalDeviceWearPackage(uploadPackage: DeviceWearUploadPackage) {
        if (deviceSyncUploading || deviceSyncCompleted) {
            return
        }
        deviceSyncUploading = true
        bluetoothStatusMessage = deviceReadingMessage
        updateBluetoothUploadValidation(uploadPackage, "正在从手机本地上传云端", completed = false)
        updateDeviceWearArchiveUploadStatus(uploadPackage, "uploading", cloudVerified = false)
        markDeviceSyncPhase(DEVICE_SYNC_PHASE_CLOUD_UPLOADING, "正在从手机本地上传云端")
        refreshBluetoothUi()
        thread(name = "SpinecareLocalWearUpload") {
            runCatching {
                val localPackage = loadLocalDeviceWearPackage() ?: uploadPackage
                val response = postJson(ApiConfig.endpoint("/api/v1/wear/device-sync"), localPackage.payload)
                val verification =
                    if (response.optBoolean("ok", false)) {
                        verifyCloudHasUploadedWearPoints(localPackage)
                    } else {
                        DeviceWearCloudVerification(
                            verified = false,
                            expectedCount = localPackage.expectedWearPoints.size,
                            matchedCount = 0,
                            missingCount = localPackage.expectedWearPoints.size,
                            mismatchedCount = 0,
                        )
                    }
                Triple(localPackage, response, verification)
            }.onSuccess { (localPackage, response, verification) ->
                mainHandler.post {
                    deviceSyncUploading = false
                    deviceSyncInProgress = false
                    if (response.optBoolean("ok", false) && verification.verified) {
                        deviceSyncCompleted = true
                        bluetoothStatusMessage = deviceReadCompleteMessage
                        updateBluetoothUploadValidation(localPackage, "云端核对完成，手机本地归档已保留", completed = true)
                        updateDeviceWearArchiveUploadStatus(localPackage, "success", cloudVerified = true)
                        deleteLocalDeviceWearPackage()
                        clearDeviceSyncPhase()
                        loadHomeWearData(force = true)
                    } else if (response.optBoolean("ok", false)) {
                        deviceSyncCompleted = false
                        bluetoothStatusMessage = "云端核对失败；手机本地数据已保留，将稍后重试"
                        updateBluetoothUploadValidation(
                            localPackage,
                            "云端核对失败：匹配${verification.matchedCount}/${verification.expectedCount}，缺失${verification.missingCount}，不一致${verification.mismatchedCount}",
                            completed = true,
                        )
                        updateDeviceWearArchiveUploadStatus(localPackage, "failed", cloudVerified = false, note = bluetoothStatusMessage)
                        markDeviceSyncPhase(DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, bluetoothStatusMessage)
                    } else {
                        deviceSyncCompleted = false
                        bluetoothStatusMessage = "云端上传失败；手机本地数据已保留，将稍后重试"
                        updateBluetoothUploadValidation(localPackage, "云端上传失败，本地数据已保留", completed = true)
                        updateDeviceWearArchiveUploadStatus(localPackage, "failed", cloudVerified = false, note = bluetoothStatusMessage)
                        markDeviceSyncPhase(DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, bluetoothStatusMessage)
                    }
                    Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
                    refreshBluetoothUi()
                }
            }.onFailure {
                mainHandler.post {
                    deviceSyncUploading = false
                    deviceSyncInProgress = false
                    deviceSyncCompleted = false
                    bluetoothStatusMessage = "云端上传失败；手机本地数据已保留，将稍后重试"
                    updateBluetoothUploadValidation(uploadPackage, "云端上传失败，本地数据已保留", completed = true)
                    updateDeviceWearArchiveUploadStatus(uploadPackage, "failed", cloudVerified = false, note = bluetoothStatusMessage)
                    markDeviceSyncPhase(DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD, bluetoothStatusMessage)
                    Toast.makeText(this, bluetoothStatusMessage, Toast.LENGTH_SHORT).show()
                    refreshBluetoothUi()
                }
            }
        }
    }

    private fun payloadHourlyRows(payload: JSONObject): List<BluetoothValidationHour> {
        val records = payload.optJSONArray("records") ?: return emptyList()
        val rows = mutableListOf<BluetoothValidationHour>()
        for (index in 0 until records.length()) {
            val item = records.optJSONObject(index) ?: continue
            val date =
                runCatching {
                    LocalDate.parse(item.optString("date", ""))
                }.getOrNull() ?: continue
            val intervals = JSONObject().put("hourly_records", item.optJSONArray("hourly_records") ?: JSONArray())
            rows += parseWearHourlyRows(date, intervals)
        }
        return rows.sortedBy { it.hourStart }
    }

    private fun localWearPointsFromPayload(payload: JSONObject): List<SpineBraceWearPoint> {
        val records = payload.optJSONArray("records") ?: return emptyList()
        val points = mutableListOf<SpineBraceWearPoint>()
        for (recordIndex in 0 until records.length()) {
            val record = records.optJSONObject(recordIndex) ?: continue
            val hours = record.optJSONArray("hourly_records") ?: continue
            for (hourIndex in 0 until hours.length()) {
                val hour = hours.optJSONObject(hourIndex) ?: continue
                val samples = hour.optJSONArray("samples") ?: continue
                for (sampleIndex in 0 until samples.length()) {
                    val sample = samples.optJSONObject(sampleIndex) ?: continue
                    val recordedAt =
                        parseCloudDateTime(sample.optString("recorded_at", ""))
                            ?: continue
                    points += SpineBraceWearPoint(
                        recordedAt = normalizeWearSampleSlot(recordedAt),
                        worn = sample.optBoolean("worn", false),
                    )
                }
            }
        }
        return normalizedWearPointMap(points)
            .map { (recordedAt, worn) -> SpineBraceWearPoint(recordedAt, worn) }
            .sortedBy { it.recordedAt }
    }

    private fun normalizedWearPointMap(points: List<SpineBraceWearPoint>): Map<LocalDateTime, Boolean> =
        points
            .map { it.copy(recordedAt = normalizeWearSampleSlot(it.recordedAt)) }
            .groupBy { it.recordedAt }
            .mapValues { (_, samples) -> samples.all { it.worn } }

    private fun verifyCloudHasUploadedWearPoints(uploadPackage: DeviceWearUploadPackage): DeviceWearCloudVerification {
        val expected =
            normalizedWearPointMap(uploadPackage.expectedWearPoints)
        if (expected.isEmpty()) {
            return DeviceWearCloudVerification(
                verified = false,
                expectedCount = 0,
                matchedCount = 0,
                missingCount = 0,
                mismatchedCount = 0,
            )
        }
        val encodedChildId = urlEncode(uploadPackage.childId)
        val records = parseWearRecords(getJson(ApiConfig.endpoint("/api/v1/wear/records?child_id=$encodedChildId&days=90")))
        val cloudSamples = mutableMapOf<LocalDateTime, Boolean>()
        records.forEach { record ->
            record.hourlyRows.forEach { hour ->
                hour.samples.forEach { sample ->
                    cloudSamples[normalizeWearSampleSlot(sample.recordedAt)] = sample.worn
                }
            }
        }
        var matchedCount = 0
        var missingCount = 0
        var mismatchedCount = 0
        expected.forEach { (slot, worn) ->
            val cloudWorn = cloudSamples[slot]
            when {
                cloudWorn == null -> missingCount += 1
                cloudWorn == worn -> matchedCount += 1
                else -> mismatchedCount += 1
            }
        }
        return DeviceWearCloudVerification(
            verified = matchedCount == expected.size && missingCount == 0 && mismatchedCount == 0,
            expectedCount = expected.size,
            matchedCount = matchedCount,
            missingCount = missingCount,
            mismatchedCount = mismatchedCount,
        )
    }

    private fun snapshotLooksLikeClearedDeviceHistory(snapshot: SpineBraceHistorySnapshot): Boolean =
        snapshot.complete &&
            snapshot.header?.count == 0

    private fun updateBluetoothUploadValidation(
        uploadPackage: DeviceWearUploadPackage,
        status: String,
        completed: Boolean,
    ) {
        latestUploadValidationRows = uploadPackage.hourlyRows
        latestUploadFetchedAt = uploadPackage.fetchedAt
        latestUploadLastReadAt = uploadPackage.lastReadAt
        latestUploadHistoryHead = uploadPackage.historyHead
        latestUploadHistoryCount = uploadPackage.historyCount
        latestUploadCompletedAt = if (completed) LocalDateTime.now() else null
        latestUploadDailyCount = uploadPackage.dailyCount
        latestUploadTotalWornHours = uploadPackage.totalWornHours
        latestUploadDeviceName = uploadPackage.deviceName
        latestUploadStatus = status
    }

    private fun loadHomeWearData(force: Boolean = false) {
        val targetChildId = child.id
        if (!force && (wearCloudLoading || wearCloudLoadedChildId == targetChildId || wearCloudLastRequestChildId == targetChildId)) {
            return
        }
        wearCloudLoading = true
        wearCloudLastRequestChildId = targetChildId
        wearCloudStatusMessage = "正在读取云端佩戴数据..."
        thread(name = "SpinecareWearFetch") {
            runCatching {
                val encodedChildId = urlEncode(targetChildId)
                val records = parseWearRecords(getJson(ApiConfig.endpoint("/api/v1/wear/records?child_id=$encodedChildId&days=35")))
                records
            }.onSuccess { records ->
                mainHandler.post {
                    if (targetChildId != child.id) {
                        wearCloudLoading = false
                        return@post
                    }
                    wearCloudSummary = null
                    wearCloudRecords = records
                    wearCloudLoadedChildId = targetChildId
                    wearCloudLoading = false
                    wearCloudStatusMessage = null
                    if (stage == Stage.App && (currentTab == MainTab.Home || currentTab == MainTab.Consult || currentTab == MainTab.Reports || currentTab == MainTab.BluetoothValidation)) {
                        render()
                    }
                }
            }.onFailure {
                mainHandler.post {
                    if (targetChildId != child.id) {
                        wearCloudLoading = false
                        return@post
                    }
                    wearCloudLoading = false
                    wearCloudStatusMessage = "云端佩戴数据读取失败，请稍后重试"
                    if (stage == Stage.App && (currentTab == MainTab.Home || currentTab == MainTab.Consult || currentTab == MainTab.Reports || currentTab == MainTab.BluetoothValidation)) {
                        render()
                    }
                }
            }
        }
    }

    private fun parseWearRecords(json: JSONObject): List<WearRecord> {
        val items = json.optJSONArray("items") ?: JSONArray()
        val records = mutableListOf<WearRecord>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val date =
                runCatching {
                    LocalDate.parse(item.optString("record_date"))
                }.getOrNull() ?: continue
            val intervals = item.optJSONObject("intervals_json")
            records.add(
                WearRecord(
                    date = date,
                    wornHours = item.optDouble("worn_hours", 0.0).coerceIn(0.0, 24.0),
                    lastReadAt = parseWearRawLastReadAt(intervals),
                    historyHead = parseWearRawInt(intervals, "history_head"),
                    historyCount = parseWearRawInt(intervals, "history_count"),
                    hourlyRows = parseWearHourlyRows(date, intervals),
                ),
            )
        }
        return records.sortedBy { it.date }
    }

    private fun parseWearRawLastReadAt(intervals: JSONObject?): LocalDateTime? {
        val raw = intervals?.optJSONObject("raw") ?: return null
        return parseCloudDateTime(raw.optString("last_read_at", ""))
            ?: parseCloudDateTime(raw.optString("device_time", ""))
    }

    private fun parseWearRawInt(intervals: JSONObject?, key: String): Int? {
        val raw = intervals?.optJSONObject("raw") ?: return null
        if (!raw.has(key) || raw.isNull(key)) {
            return null
        }
        return raw.optInt(key, -1).takeIf { it >= 0 }
    }

    private fun parseCloudDateTime(text: String): LocalDateTime? {
        val value = text.trim()
        if (value.isEmpty() || value == "null" || value == "--") {
            return null
        }
        val normalized =
            value
                .substringBefore(".")
                .replace(" ", "T")
                .let { candidate ->
                    if (candidate.count { it == ':' } == 1) "$candidate:00" else candidate
                }
        return runCatching {
            LocalDateTime.parse(normalized)
        }.getOrNull()
    }

    private fun parseWearHourlyRows(date: LocalDate, intervals: JSONObject?): List<BluetoothValidationHour> {
        val rows = intervals?.optJSONArray("hourly_records") ?: return emptyList()
        val result = mutableListOf<BluetoothValidationHour>()
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val hourStartText = item.optString("hour_start", "")
            val hourStart =
                runCatching {
                    if (hourStartText.length >= 13 && hourStartText[4] == '-') {
                        val normalized = hourStartText.replace(" ", "T").let { value ->
                            if (value.count { it == ':' } == 1) "$value:00" else value
                        }
                        LocalDateTime.parse(normalized)
                    } else {
                        val hour = hourStartText.substringBefore(":").toIntOrNull() ?: item.optInt("hour_index", 0)
                        date.atTime(hour.coerceIn(0, 23), 0)
                    }
                }.getOrNull() ?: continue
            val sampleRows = mutableListOf<BluetoothValidationSample>()
            val samplesJson = item.optJSONArray("samples")
            if (samplesJson != null) {
                for (sampleIndex in 0 until samplesJson.length()) {
                    val sample = samplesJson.optJSONObject(sampleIndex) ?: continue
                    val recordedAtText = sample.optString("recorded_at", "")
                    val recordedAt =
                        runCatching {
                            val normalized = recordedAtText.replace(" ", "T").let { value ->
                                if (value.count { it == ':' } == 1) "$value:00" else value
                            }
                            normalizeWearSampleSlot(LocalDateTime.parse(normalized))
                        }.getOrNull() ?: continue
                    sampleRows += BluetoothValidationSample(
                        recordedAt = recordedAt,
                        worn = sample.optBoolean("worn", false),
                    )
                }
            }
            val normalizedSamples =
                sampleRows
                    .groupBy { it.recordedAt }
                    .map { (slot, samples) ->
                        BluetoothValidationSample(
                            recordedAt = slot,
                            worn = samples.all { it.worn },
                        )
                    }
                    .sortedBy { it.recordedAt }
            val parsedSampleCount = normalizedSamples.size.coerceIn(0, 6)
            val parsedWornCount = normalizedSamples.count { it.worn }.coerceIn(0, 6)
            val fallbackWornHours = item.optDouble("worn_hours", 0.0).coerceIn(0.0, 1.0)
            val fallbackWornCount =
                item.optInt(
                    "worn_count",
                    (fallbackWornHours * 6).roundToInt(),
                ).coerceIn(0, 6)
            result += BluetoothValidationHour(
                hourStart = hourStart.withMinute(0).withSecond(0).withNano(0),
                wornHours = if (normalizedSamples.isNotEmpty()) roundOne(parsedWornCount / 6.0) else fallbackWornHours,
                sampleCount = if (normalizedSamples.isNotEmpty()) parsedSampleCount else item.optInt("sample_count", 6).coerceIn(0, 6),
                wornCount = if (normalizedSamples.isNotEmpty()) parsedWornCount else fallbackWornCount,
                samples = normalizedSamples,
            )
        }
        return result.sortedBy { it.hourStart }
    }

    private fun currentThirtyDayWearSummary(): WearSummary? {
        val thirtyDayRecords = wearCloudRecords.takeLast(30)
        return if (thirtyDayRecords.isNotEmpty() || wearCloudLoadedChildId == child.id) {
            summaryFromWearRecords(thirtyDayRecords)
        } else {
            null
        }
    }

    private fun currentSevenDayWearRecords(): List<WearRecord> = wearCloudRecords.takeLast(7)

    private fun homeCloudInsights(): List<String> {
        val records35 = wearCloudRecords.takeLast(35)
        if (records35.size < 3) {
            return listOf("云端佩戴数据不足3天，暂不生成趋势判断。")
        }
        val records30 = records35.takeLast(30)
        val summary30 = summaryFromWearRecords(records30)
        val target = prescribedWearHours()
        val severeThreshold = roundOne(target * 0.6)
        val recent7 = records35.takeLast(7)
        val previous7 = records35.dropLast(7).takeLast(7)
        val recentAvg = recent7.takeIf { it.isNotEmpty() }?.let { roundOne(it.sumOf(WearRecord::wornHours) / it.size) } ?: 0.0
        val previousAvg = previous7.takeIf { it.isNotEmpty() }?.let { roundOne(it.sumOf(WearRecord::wornHours) / it.size) }
        val severeStreak = longestStreakWhere(records30) { it.wornHours < severeThreshold }
        val lowStreak = longestStreakWhere(records30) { target > 0.0 && it.wornHours < target * 0.8 }
        val skinProblems = skinLogs.filter { it.status !in listOf("正常", "无异常") }.take(3)
        val monthlyGrowthDelta = growthDeltaWithinDays(31)

        val insights = mutableListOf<String>()
        val compliant30 = records30.count { isWearCompliant(it) }
        insights += languageText(
            "云端已读取${records35.size}天佩戴记录；近30天平均${formatHours(summary30.avgHours)}/${formatHours(target)}h，达标${compliant30}/${records30.size}天，达标率${summary30.complianceRate}%。",
            "Cloud loaded ${records35.size} days of wearing records; 30-day average ${formatHours(summary30.avgHours)}/${formatHours(target)}h; met ${compliant30}/${records30.size} days; compliance ${summary30.complianceRate}%.",
            "クラウドから${records35.size}日の装着記録を読み取り済み。30日平均${formatHours(summary30.avgHours)}/${formatHours(target)}h、達成${compliant30}/${records30.size}日、達成率${summary30.complianceRate}%。",
            "클라우드에서 ${records35.size}일 착용 기록을 읽었습니다. 30일 평균 ${formatHours(summary30.avgHours)}/${formatHours(target)}h, 달성 ${compliant30}/${records30.size}일, 달성률 ${summary30.complianceRate}%.",
            "Nube cargada con ${records35.size} días de uso; media de 30 días ${formatHours(summary30.avgHours)}/${formatHours(target)}h; cumple ${compliant30}/${records30.size} días; cumplimiento ${summary30.complianceRate}%.",
            "Cloud chargé avec ${records35.size} jours de port ; moyenne 30 jours ${formatHours(summary30.avgHours)}/${formatHours(target)}h ; atteint ${compliant30}/${records30.size} jours ; observance ${summary30.complianceRate}%.",
            "Cloud mit ${records35.size} Tagen Tragedaten geladen; 30-Tage-Schnitt ${formatHours(summary30.avgHours)}/${formatHours(target)}h; erfüllt ${compliant30}/${records30.size} Tage; Erfüllung ${summary30.complianceRate}%.",
        )
        insights +=
            when {
                summary30.complianceRate >= 80 -> languageText(
                    "近30天达标率良好，建议继续保持当前佩戴节奏。",
                    "30-day compliance is good. Keep the current wearing routine.",
                    "30日達成率は良好です。現在の装着リズムを維持してください。",
                    "30일 달성률이 양호합니다. 현재 착용 리듬을 유지하세요.",
                    "El cumplimiento de 30 días es bueno. Mantén el ritmo actual de uso.",
                    "L'observance sur 30 jours est bonne. Gardez le rythme actuel de port.",
                    "Die 30-Tage-Erfüllung ist gut. Bitte den aktuellen Tragerhythmus beibehalten.",
                )
                summary30.complianceRate >= 50 -> languageText(
                    "近30天达标率一般，建议优先补足固定缺口时段。",
                    "30-day compliance is fair. Prioritize filling the recurring gap periods.",
                    "30日達成率は普通です。固定的な不足時間帯を優先して補ってください。",
                    "30일 달성률이 보통입니다. 반복되는 부족 시간대를 우선 보완하세요.",
                    "El cumplimiento de 30 días es medio. Prioriza cubrir los periodos con brechas repetidas.",
                    "L'observance sur 30 jours est moyenne. Comblez d'abord les périodes de manque récurrentes.",
                    "Die 30-Tage-Erfüllung ist mittel. Wiederkehrende Lücken zuerst ausgleichen.",
                )
                else -> languageText(
                    "近30天达标率偏低，建议尽快与医生或支具师沟通佩戴困难。",
                    "30-day compliance is low. Please discuss wearing difficulties with the doctor or brace specialist soon.",
                    "30日達成率は低めです。装着の難しさを早めに医師または装具士へ相談してください。",
                    "30일 달성률이 낮습니다. 착용 어려움을 의사 또는 보조기 전문가와 곧 상담하세요.",
                    "El cumplimiento de 30 días es bajo. Consulta pronto las dificultades de uso con el médico o el técnico ortopédico.",
                    "L'observance sur 30 jours est faible. Parlez rapidement des difficultés de port avec le médecin ou l'orthoprothésiste.",
                    "Die 30-Tage-Erfüllung ist niedrig. Bitte Trageschwierigkeiten zeitnah mit dem Arzt oder Orthopädietechniker besprechen.",
                )
            }
        previousAvg?.let { before ->
            val delta = roundOne(recentAvg - before)
            insights +=
                when {
                    delta >= 1.0 -> "近7天平均${formatHours(recentAvg)}h，较前7天增加${formatHours(delta)}h，近期佩戴有所改善。"
                    delta <= -1.0 -> "近7天平均${formatHours(recentAvg)}h，较前7天下降${formatHours(-delta)}h，需要关注近期佩戴下降。"
                    else -> "近7天平均${formatHours(recentAvg)}h，较前7天变化小于1h，近期较稳定。"
                }
        }
        if (severeStreak >= 3) {
            insights += "出现连续${severeStreak}天严重不足（低于医嘱60%，阈值${formatHours(severeThreshold)}h），建议纳入复诊沟通。"
        } else if (lowStreak >= 3) {
            insights += "出现连续${lowStreak}天低于医嘱80%，建议排查不愿佩戴、皮肤不适或作息冲突。"
        }
        topGapHours(records30).takeIf { it.isNotEmpty() }?.let { gaps ->
            insights += "小时明细显示主要缺口集中在${gaps.joinToString("、")}。"
        }
        if (skinProblems.isNotEmpty()) {
            insights += "近期皮肤问题记录：${skinProblems.joinToString("；") { "${formatDateShort(it.date)} ${it.region}${it.status}" }}，已纳入复诊报告素材。"
        }
        monthlyGrowthDelta?.takeIf { it >= 1.3 }?.let { delta ->
            insights += "近1个月身高增加${formatHours(delta)}cm，超过1.3cm，复诊时建议评估支具适配。"
        }
        return insights.take(5)
    }

    private fun consultCloudAnalysis(category: String, fallback: String): String {
        val records35 = wearCloudRecords.takeLast(35)
        if (records35.isEmpty()) {
            return when {
                wearCloudLoading || skinCloudLoading || growthCloudLoading ->
                    "正在读取云端佩戴、生长和皮肤记录，读取完成后本段分析会自动更新。"
                wearCloudLoadedChildId == child.id ->
                    "云端暂未查询到佩戴记录，无法生成佩戴趋势判断；请先完成蓝牙读取并上传云端。"
                wearCloudStatusMessage != null ->
                    wearCloudStatusMessage ?: fallback
                else -> fallback
            }
        }

        val target = prescribedWearHours()
        val records30 = records35.takeLast(30)
        val summary30 = summaryFromWearRecords(records30)
        val recent7 = records35.takeLast(7)
        val recentAvg = recent7.takeIf { it.isNotEmpty() }?.let { roundOne(it.sumOf(WearRecord::wornHours) / it.size) } ?: 0.0
        val recentCompliantDays = recent7.count { isWearCompliant(it) }
        val recentGap = if (target > 0.0) roundOne((target - recentAvg).coerceAtLeast(0.0)) else 0.0
        val gapHours = topGapHours(records30)
        val severeThreshold = roundOne(target * 0.6)
        val severeStreak = longestStreakWhere(records30) { target > 0.0 && it.wornHours < severeThreshold }
        val lowStreak = longestStreakWhere(records30) { target > 0.0 && it.wornHours < target * 0.8 }
        val skinProblems = skinLogs.filter { it.status !in listOf("正常", "无异常") }.take(2)
        val monthlyGrowthDelta = growthDeltaWithinDays(31)

        val lines = mutableListOf<String>()
        val compliant30 = records30.count { isWearCompliant(it) }
        lines += languageText(
            "云端已读取${records35.size}天佩戴记录；近30天平均${formatHours(summary30.avgHours)}h/天，医嘱${formatHours(target)}h/天，达标${compliant30}/${records30.size}天，达标率${summary30.complianceRate}%。近7天平均${formatHours(recentAvg)}h/天，达标${recentCompliantDays}/${recent7.size}天。",
            "Cloud loaded ${records35.size} days of wearing records; 30-day average ${formatHours(summary30.avgHours)}h/day, prescribed ${formatHours(target)}h/day, met ${compliant30}/${records30.size} days, compliance ${summary30.complianceRate}%. 7-day average ${formatHours(recentAvg)}h/day, met ${recentCompliantDays}/${recent7.size} days.",
            "クラウドから${records35.size}日の装着記録を読み取り済み。30日平均${formatHours(summary30.avgHours)}h/日、指示${formatHours(target)}h/日、達成${compliant30}/${records30.size}日、達成率${summary30.complianceRate}%。7日平均${formatHours(recentAvg)}h/日、達成${recentCompliantDays}/${recent7.size}日。",
            "클라우드에서 ${records35.size}일 착용 기록을 읽었습니다. 30일 평균 ${formatHours(summary30.avgHours)}h/일, 처방 ${formatHours(target)}h/일, 달성 ${compliant30}/${records30.size}일, 달성률 ${summary30.complianceRate}%. 7일 평균 ${formatHours(recentAvg)}h/일, 달성 ${recentCompliantDays}/${recent7.size}일.",
            "Nube cargada con ${records35.size} días de uso; media 30 días ${formatHours(summary30.avgHours)}h/día, prescrito ${formatHours(target)}h/día, cumple ${compliant30}/${records30.size} días, cumplimiento ${summary30.complianceRate}%. Media 7 días ${formatHours(recentAvg)}h/día, cumple ${recentCompliantDays}/${recent7.size} días.",
            "Cloud chargé avec ${records35.size} jours de port ; moyenne 30 jours ${formatHours(summary30.avgHours)}h/jour, prescription ${formatHours(target)}h/jour, atteint ${compliant30}/${records30.size} jours, observance ${summary30.complianceRate}%. Moyenne 7 jours ${formatHours(recentAvg)}h/jour, atteint ${recentCompliantDays}/${recent7.size} jours.",
            "Cloud mit ${records35.size} Tagen Tragedaten geladen; 30-Tage-Schnitt ${formatHours(summary30.avgHours)}h/Tag, verordnet ${formatHours(target)}h/Tag, erfüllt ${compliant30}/${records30.size} Tage, Erfüllung ${summary30.complianceRate}%. 7-Tage-Schnitt ${formatHours(recentAvg)}h/Tag, erfüllt ${recentCompliantDays}/${recent7.size} Tage.",
        )
        if (gapHours.isNotEmpty()) {
            lines += "小时明细显示主要缺口集中在${gapHours.joinToString("、")}。"
        }

        lines +=
            when (category) {
                "emotion" -> {
                    if (lowStreak >= 3) {
                        "结合云端趋势看，近期存在连续${lowStreak}天低于医嘱80%的情况，沟通时建议先处理具体困难，再谈达标目标。"
                    } else {
                        "结合云端趋势看，近期没有连续多天明显不足，可把沟通重点放在保持自主感和稳定提醒。"
                    }
                }
                "clinical" -> {
                    if (severeStreak >= 3) {
                        "该问题涉及医嘱或症状判断，且云端记录出现连续${severeStreak}天低于医嘱60%（阈值${formatHours(severeThreshold)}h），建议复诊时重点说明。"
                    } else {
                        "该问题涉及医嘱或症状判断，云端佩戴数据可作为复诊背景，但是否调整佩戴方案仍需医生或支具师确认。"
                    }
                }
                else -> {
                    if (recentGap > 0.0) {
                        "当前最直接的改进点是先补足近7天平均与医嘱之间约${formatHours(recentGap)}h/天的缺口。"
                    } else {
                        "近7天平均佩戴已达到或超过医嘱，可重点保持固定作息和皮肤检查。"
                    }
                }
            }

        if (skinProblems.isNotEmpty()) {
            lines += "近期皮肤记录包括：${skinProblems.joinToString("；") { "${formatDateShort(it.date)} ${it.region}${it.status}" }}，建议咨询时同步说明。"
        }
        monthlyGrowthDelta?.takeIf { it >= 1.3 }?.let { delta ->
            lines += "近1个月身高增加${formatHours(delta)}cm，超过1.3cm，建议复诊时评估支具适配。"
        }
        return lines.joinToString("\n")
    }

    private fun longestStreakWhere(records: List<WearRecord>, predicate: (WearRecord) -> Boolean): Int {
        var longest = 0
        var current = 0
        records.sortedBy { it.date }.forEach { record ->
            if (predicate(record)) {
                current += 1
                longest = kotlin.math.max(longest, current)
            } else {
                current = 0
            }
        }
        return longest
    }

    private fun topGapHours(records: List<WearRecord>): List<String> {
        val counts = mutableMapOf<Int, Double>()
        records.flatMap { it.hourlyRows }.forEach { row ->
            val missing = (1.0 - row.wornHours).coerceAtLeast(0.0)
            if (missing > 0.0) {
                counts[row.hourStart.hour] = (counts[row.hourStart.hour] ?: 0.0) + missing
            }
        }
        return counts.entries
            .sortedWith(compareByDescending<Map.Entry<Int, Double>> { it.value }.thenBy { it.key })
            .take(2)
            .map { (hour, _) -> "%02d:00-%02d:00".format(hour, (hour + 1) % 24) }
    }

    private fun growthDeltaWithinDays(days: Long): Double? {
        val sorted = growthLogs.sortedBy { it.date }
        val latest = sorted.lastOrNull() ?: return null
        val baseline = sorted.lastOrNull { !it.date.isAfter(latest.date.minusDays(days)) && it.date != latest.date }
            ?: sorted.firstOrNull { it.date != latest.date }
            ?: return null
        return roundOne(latest.heightCm - baseline.heightCm)
    }

    private fun summaryFromWearRecords(records: List<WearRecord>): WearSummary {
        if (records.isEmpty()) {
            return WearSummary(
                avgHours = 0.0,
                prescribedHours = prescribedWearHours(),
                complianceRate = 0,
                daysCounted = 0,
                longestStreak = 0,
            )
        }
        val avgHours = roundOne(records.sumOf { it.wornHours } / records.size)
        val prescribedHours = prescribedWearHours()
        val complianceRate = (records.count { isWearCompliant(it) } * 100f / records.size).roundToInt().coerceIn(0, 100)
        return WearSummary(
            avgHours = avgHours,
            prescribedHours = prescribedHours,
            complianceRate = complianceRate,
            daysCounted = records.size,
            longestStreak = longestWearStreak(records),
        )
    }

    private fun longestWearStreak(records: List<WearRecord>): Int {
        var longest = 0
        var current = 0
        records.sortedBy { it.date }.forEach { record ->
            if (isWearCompliant(record)) {
                current += 1
                longest = kotlin.math.max(longest, current)
            } else {
                current = 0
            }
        }
        return longest
    }

    private fun trendChartHourValues(records: List<WearRecord>): List<Double> =
        records
            .map { record -> record.wornHours }
            .ifEmpty { listOf(0.0) }

    private fun trendChartLabels(records: List<WearRecord>): Map<Int, String> {
        if (records.isEmpty()) {
            return emptyMap()
        }
        return records.indices
            .filter { it % 7 == 0 || it == records.lastIndex }
            .associateWith { index ->
                val date = records[index].date
                "${date.dayOfMonth}/${date.monthValue}"
            }
    }

    private fun trendChartHourLabels(records: List<WearRecord>): Map<Int, String> =
        records.indices.associateWith { index -> "${formatHours(records[index].wornHours)}h" }

    private fun wearProgressPercent(wornHours: Double, prescribedHours: Double): Int =
        if (prescribedHours > 0) {
            (wornHours * 100 / prescribedHours).roundToInt().coerceIn(0, 100)
        } else {
            0
        }

    private fun prescribedWearHours(): Double =
        child.prescribedHours.coerceIn(0, 24).toDouble()

    private fun isWearCompliant(record: WearRecord): Boolean {
        val targetHours = prescribedWearHours()
        return targetHours > 0 && record.wornHours >= targetHours
    }

    private fun roundOne(value: Double): Double =
        (value * 10).roundToInt() / 10.0

    private fun formatHours(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    private fun formatReadDataDuration(count: Int): String {
        val minutes = count.coerceAtLeast(0) * 10
        return "${minutes}分钟（${count.coerceAtLeast(0)}个10分钟点）"
    }

    private fun urlEncode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun loadSkinLogs(force: Boolean = false) {
        val targetChildId = child.id
        if (!force && (skinCloudLoading || skinCloudLoadedChildId == targetChildId)) {
            return
        }
        skinCloudLoading = true
        if (skinLogs.isEmpty()) {
            skinCloudStatusMessage = "正在读取皮肤记录..."
        }
        thread(name = "SpinecareSkinFetch") {
            runCatching {
                parseSkinLogs(getJson(ApiConfig.endpoint("/api/v1/skin-logs?child_id=${urlEncode(targetChildId)}")))
            }.onSuccess { records ->
                mainHandler.post {
                    if (targetChildId != child.id) {
                        skinCloudLoading = false
                        return@post
                    }
                    skinLogs = records
                    skinCloudLoadedChildId = targetChildId
                    skinCloudLoading = false
                    if (skinCloudStatusMessage == "正在读取皮肤记录...") {
                        skinCloudStatusMessage = null
                    }
                    refreshRecordsUi()
                }
            }.onFailure {
                mainHandler.post {
                    if (targetChildId != child.id) {
                        skinCloudLoading = false
                        return@post
                    }
                    skinCloudLoading = false
                    skinCloudStatusMessage = "皮肤记录读取失败，请稍后重试"
                    refreshRecordsUi()
                }
            }
        }
    }

    private fun saveSkinLogToCloud() {
        if (skinRegionInputs.isEmpty()) {
            skinCloudStatusMessage = "请至少选择1个问题部位"
            render()
            return
        }
        if (skinStatusInputs.isEmpty()) {
            skinCloudStatusMessage = "请至少选择1个问题类型"
            render()
            return
        }
        val note = skinNoteInput.trim().takeIf { it.isNotBlank() }
        val manualPhotoRef = skinPhotoRefInput.trim().takeIf { it.isNotBlank() }
        if (note == null && manualPhotoRef == null && skinCapturedPhoto == null) {
            skinCloudStatusMessage = "请填写备注或拍照后再保存"
            render()
            return
        }
        skinCloudStatusMessage = "正在保存皮肤记录..."
        render()
        val photo = skinCapturedPhoto
        val childId = child.id
        val region = joinSkinSelections(skinRegionInputs)
        val status = joinSkinSelections(skinStatusInputs)
        thread(name = "SpinecareSkinSave") {
            runCatching {
                val photos = JSONArray()
                manualPhotoRef?.let { photos.put(it) }
                if (photo != null) {
                    val upload = uploadBitmapPhoto(photo)
                    val uploadedId = upload.optString("id", "").takeIf { it.isNotBlank() }
                    val uploadedName = upload.optString("filename", "").takeIf { it.isNotBlank() }
                    photos.put(uploadedId ?: uploadedName ?: "uploaded-photo")
                }
                val payload =
                    JSONObject()
                        .put("child_id", childId)
                        .put("region", region)
                        .put("status", status)
                        .put("note", note ?: JSONObject.NULL)
                        .put("photos", photos)
                postJson(ApiConfig.endpoint("/api/v1/skin-logs"), payload)
            }.onSuccess { response ->
                mainHandler.post {
                    skinCloudStatusMessage =
                        if (response.optString("status") == "saved" || response.has("id")) {
                            skinNoteInput = ""
                            skinPhotoRefInput = ""
                            skinCapturedPhoto = null
                            skinRegionInputs = linkedSetOf("左腰部")
                            skinStatusInputs = linkedSetOf("发红")
                            skinCloudLoadedChildId = null
                            selectedSkinLogId = null
                            loadSkinLogs(force = true)
                            "皮肤记录已保存，并将自动整合进入复诊报告"
                        } else {
                            "皮肤记录保存失败，请稍后重试"
                        }
                    refreshRecordsUi()
                }
            }.onFailure {
                mainHandler.post {
                    skinCloudStatusMessage = "皮肤记录保存失败，请稍后重试"
                    refreshRecordsUi()
                }
            }
        }
    }

    private fun parseSkinLogs(json: JSONObject): List<SkinLog> {
        val items = json.optJSONArray("items") ?: JSONArray()
        val records = mutableListOf<SkinLog>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id", "").ifBlank { "skin-${index}-${item.optString("log_date")}" }
            val date = parseLocalDate(item.optString("log_date")) ?: continue
            val photos = mutableListOf<String>()
            val photoItems = item.optJSONArray("photos_json") ?: JSONArray()
            for (photoIndex in 0 until photoItems.length()) {
                photos += photoItems.optString(photoIndex, "").takeIf { it.isNotBlank() } ?: continue
            }
            val note = item.optString("note", "").takeUnless { item.isNull("note") }.orEmpty()
            if (isLegacyDemoSkinLog(id, note, photos)) {
                continue
            }
            records += SkinLog(
                id = id,
                date = date,
                region = normalizeSkinRegion(item.optString("region", "未填写")),
                status = normalizeSkinStatus(item.optString("status", "未填写")),
                note = note,
                photos = photos,
            )
        }
        return records.sortedByDescending { it.date }
    }

    private fun isLegacyDemoSkinLog(id: String, note: String, photos: List<String>): Boolean =
        id.startsWith("skin-202") ||
            photos.any { it == "demo-photo-placeholder" } ||
            note.contains("用于") ||
            note.contains("占位")

    private fun toggleSelection(selected: Set<String>, option: String): LinkedHashSet<String> =
        LinkedHashSet(selected).apply {
            if (contains(option)) {
                remove(option)
            } else {
                add(option)
            }
        }

    private fun joinSkinSelections(selected: Set<String>): String =
        selected.joinToString("、")

    private fun normalizeSkinRegion(value: String): String =
        normalizeSkinJoinedValue(value) { token ->
            when (token.lowercase(Locale.US)) {
                "waist_left", "left_waist" -> "左腰部"
                "waist_right", "right_waist" -> "右腰部"
                "back" -> "背部"
                "abdomen", "chest_abdomen" -> "胸腹部"
                "shoulder" -> "肩部"
                "pelvis", "hip" -> "骨盆/髋部"
                else -> token.ifBlank { "未填写" }
            }
        }

    private fun normalizeSkinStatus(value: String): String =
        normalizeSkinJoinedValue(value) { token ->
            when (token.lowercase(Locale.US)) {
                "normal" -> "正常"
                "red", "redness" -> "发红"
                "itch" -> "瘙痒"
                "pain" -> "疼痛"
                "broken", "wound" -> "破皮"
                "blister" -> "水泡"
                else -> token.ifBlank { "未填写" }
            }
        }

    private fun normalizeSkinJoinedValue(value: String, normalizeToken: (String) -> String): String {
        val tokens = value
            .split("、", "，", ",", "；", ";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("未填写") }
        return tokens.map(normalizeToken).distinct().joinToString("、")
    }

    private fun loadGrowthLogs(force: Boolean = false) {
        val targetChildId = child.id
        if (!force && (growthCloudLoading || growthCloudLoadedChildId == targetChildId)) {
            return
        }
        growthCloudLoading = true
        if (growthLogs.isEmpty()) {
            growthCloudStatusMessage = "正在读取生长记录..."
        }
        thread(name = "SpinecareGrowthFetch") {
            runCatching {
                parseGrowthLogs(getJson(ApiConfig.endpoint("/api/v1/growth-logs?child_id=${urlEncode(targetChildId)}")))
            }.onSuccess { records ->
                mainHandler.post {
                    if (targetChildId != child.id) {
                        growthCloudLoading = false
                        return@post
                    }
                    growthLogs = records
                    growthCloudLoadedChildId = targetChildId
                    growthCloudLoading = false
                    if (growthCloudStatusMessage == "正在读取生长记录...") {
                        growthCloudStatusMessage = null
                    }
                    refreshRecordsUi()
                }
            }.onFailure {
                mainHandler.post {
                    if (targetChildId != child.id) {
                        growthCloudLoading = false
                        return@post
                    }
                    growthCloudLoading = false
                    growthCloudStatusMessage = "生长记录读取失败，请稍后重试"
                    refreshRecordsUi()
                }
            }
        }
    }

    private fun saveGrowthLogToCloud() {
        val height = growthHeightInput.toDoubleOrNull()?.coerceIn(0.0, 250.0)
        if (height == null || height <= 0.0) {
            growthCloudStatusMessage = "请输入有效身高"
            render()
            return
        }
        growthCloudStatusMessage = "正在保存生长记录..."
        render()
        val note = growthNoteInput.trim().takeIf { it.isNotBlank() }
        val payload =
            JSONObject()
                .put("child_id", child.id)
                .put("height_cm", height)
                .put("note", note ?: JSONObject.NULL)
        thread(name = "SpinecareGrowthSave") {
            runCatching {
                postJson(ApiConfig.endpoint("/api/v1/growth-logs"), payload)
            }.onSuccess { response ->
                mainHandler.post {
                    growthCloudStatusMessage =
                        if (response.optString("status") == "saved" || response.has("id")) {
                            growthNoteInput = ""
                            growthCloudLoadedChildId = null
                            loadGrowthLogs(force = true)
                            "生长记录已保存"
                        } else {
                            "生长记录保存失败，请稍后重试"
                        }
                    refreshRecordsUi()
                }
            }.onFailure {
                mainHandler.post {
                    growthCloudStatusMessage = "生长记录保存失败，请稍后重试"
                    refreshRecordsUi()
                }
            }
        }
    }

    private fun parseGrowthLogs(json: JSONObject): List<GrowthLog> {
        val items = json.optJSONArray("items") ?: JSONArray()
        val records = mutableListOf<GrowthLog>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id", "").ifBlank { "growth-${index}-${item.optString("log_date")}" }
            if (isLegacyDemoGrowthLog(id, item.optString("note", ""))) {
                continue
            }
            val date = parseLocalDate(item.optString("log_date")) ?: continue
            records.add(
                GrowthLog(
                    id = id,
                    date = date,
                    heightCm = item.optDouble("height_cm", 0.0),
                    note = item.optString("note", "").takeUnless { item.isNull("note") }.orEmpty(),
                ),
            )
        }
        return records.sortedByDescending { it.date }
    }

    private fun isLegacyDemoGrowthLog(id: String, note: String): Boolean =
        id.startsWith("growth-202") ||
            note.contains("用于") ||
            note.contains("例行身高记录") ||
            note.contains("月中复测")

    private fun loadImagingLogs(force: Boolean = false) {
        val targetChildId = child.id
        if (!force && (imagingCloudLoading || imagingCloudLoadedChildId == targetChildId)) {
            return
        }
        imagingCloudLoading = true
        if (imagingLogs.isEmpty()) {
            imagingCloudStatusMessage = "正在读取影像档案..."
        }
        thread(name = "SpinecareImagingFetch") {
            runCatching {
                parseImagingLogs(getJson(ApiConfig.endpoint("/api/v1/imaging?child_id=${urlEncode(targetChildId)}")))
            }.onSuccess { records ->
                mainHandler.post {
                    if (targetChildId != child.id) {
                        imagingCloudLoading = false
                        return@post
                    }
                    imagingLogs = records
                    imagingCloudLoadedChildId = targetChildId
                    imagingCloudLoading = false
                    if (imagingCloudStatusMessage == "正在读取影像档案...") {
                        imagingCloudStatusMessage = null
                    }
                    refreshRecordsUi()
                }
            }.onFailure {
                mainHandler.post {
                    if (targetChildId != child.id) {
                        imagingCloudLoading = false
                        return@post
                    }
                    imagingCloudLoading = false
                    imagingCloudStatusMessage = "影像档案读取失败，请稍后重试"
                    refreshRecordsUi()
                }
            }
        }
    }

    private fun saveImagingLogToCloud() {
        val shotDate = parseLocalDate(imagingDateInput)
        if (shotDate == null) {
            imagingCloudStatusMessage = "请输入有效拍摄日期，格式如 2026-06-16"
            render()
            return
        }
        imagingCloudStatusMessage = "正在保存影像记录..."
        render()
        val manualFileUrl = imagingFileUrlInput.trim().takeIf { it.isNotBlank() }
        val note = imagingNoteInput.trim().takeIf { it.isNotBlank() }
        val capturedPhoto = imagingCapturedPhoto
        val selectedPhotoUri = imagingSelectedPhotoUri
        thread(name = "SpinecareImagingSave") {
            runCatching {
                val uploadedFileUrl =
                    when {
                        capturedPhoto != null -> uploadBitmapImage(capturedPhoto, "imaging").uploadedFileReference()
                        selectedPhotoUri != null -> uploadImageUri(selectedPhotoUri).uploadedFileReference()
                        else -> manualFileUrl
                    }
                val payload =
                    JSONObject()
                        .put("child_id", child.id)
                        .put("image_type", imagingTypeInput)
                        .put("file_url", uploadedFileUrl ?: JSONObject.NULL)
                        .put("shot_date", shotDate.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .put("note", note ?: JSONObject.NULL)
                postJson(ApiConfig.endpoint("/api/v1/imaging"), payload)
            }.onSuccess { response ->
                mainHandler.post {
                    imagingCloudStatusMessage =
                        if (response.optString("status") == "saved" || response.has("id")) {
                            imagingFileUrlInput = ""
                            imagingNoteInput = ""
                            imagingCapturedPhoto = null
                            imagingSelectedPhotoUri = null
                            imagingCloudLoadedChildId = null
                            selectedImagingLogId = null
                            loadImagingLogs(force = true)
                            "影像记录已保存"
                        } else {
                            "影像记录保存失败，请稍后重试"
                        }
                    refreshRecordsUi()
                }
            }.onFailure {
                mainHandler.post {
                    imagingCloudStatusMessage = "影像记录保存失败，请稍后重试"
                    refreshRecordsUi()
                }
            }
        }
    }

    private fun parseImagingLogs(json: JSONObject): List<ImagingLog> {
        val items = json.optJSONArray("items") ?: JSONArray()
        val records = mutableListOf<ImagingLog>()
        for (index in 0 until items.length()) {
            val item = items.optJSONObject(index) ?: continue
            val id = item.optString("id", "").ifBlank { "image-${index}-${item.optString("shot_date")}" }
            if (isLegacyDemoImagingLog(id, item.optString("note", ""))) {
                continue
            }
            val date = parseLocalDate(item.optString("shot_date")) ?: continue
            records.add(
                ImagingLog(
                    id = id,
                    imageType = item.optString("image_type", "影像").ifBlank { "影像" },
                    shotDate = date,
                    fileUrl = item.optString("file_url", "").takeUnless { item.isNull("file_url") || it.isBlank() },
                    note = item.optString("note", "").takeUnless { item.isNull("note") }.orEmpty(),
                ),
            )
        }
        return records.sortedByDescending { it.shotDate }
    }

    private fun isLegacyDemoImagingLog(id: String, note: String): Boolean =
        id.startsWith("image-") ||
            note.contains("家庭记录，不用于诊断") ||
            note.contains("医生建议继续支具治疗") ||
            note.contains("较4月更稳定")

    private fun refreshLogsUi() {
        if (stage == Stage.App && currentTab == MainTab.Logs) {
            render()
        }
    }

    private fun refreshRecordsUi() {
        if (stage == Stage.App && (currentTab == MainTab.Logs || currentTab == MainTab.Home || currentTab == MainTab.Reports || currentTab == MainTab.Consult)) {
            render()
        }
    }

    private fun parseLocalDate(value: String): LocalDate? =
        runCatching { LocalDate.parse(value.trim()) }.getOrNull()

    private fun formatDateShort(date: LocalDate): String =
        "${date.dayOfMonth}/${date.monthValue}"

    private fun startDataExport() {
        if (dataExportInProgress) return
        dataExportInProgress = true
        dataExportStatusMessage = "正在从云端读取数据并生成备份文件..."
        lastDataExportSummary = null
        render()
        thread(name = "SpinecareDataExport") {
            runCatching {
                val payload = buildDataExportPayload()
                val jsonText = payload.toString(2)
                val fileName = dataExportFileName()
                val summaryText = dataExportSummaryText(payload)
                Triple(jsonText, fileName, summaryText)
            }.onSuccess { (jsonText, fileName, summaryText) ->
                mainHandler.post {
                    dataExportInProgress = false
                    pendingDataExportJson = jsonText
                    pendingDataExportFileName = fileName
                    lastDataExportSummary = summaryText
                    dataExportStatusMessage = "备份文件已生成，请选择保存位置"
                    dataExportDocumentLauncher.launch(fileName)
                    if (stage == Stage.App && currentTab == MainTab.DataExport) {
                        render()
                    }
                }
            }.onFailure {
                mainHandler.post {
                    dataExportInProgress = false
                    pendingDataExportJson = null
                    pendingDataExportFileName = null
                    dataExportStatusMessage = "导出失败，请检查网络后重试"
                    Toast.makeText(this, dataExportStatusMessage, Toast.LENGTH_SHORT).show()
                    if (stage == Stage.App && currentTab == MainTab.DataExport) {
                        render()
                    }
                }
            }
        }
    }

    private fun buildDataExportPayload(): JSONObject {
        val targetChildId = child.id
        val encodedChildId = urlEncode(targetChildId)
        val profileJson = getJson(ApiConfig.endpoint("/api/v1/children/$encodedChildId"))
        val wearJson = getJson(ApiConfig.endpoint("/api/v1/wear/records?child_id=$encodedChildId&days=3650"))
        val skinJson = getJson(ApiConfig.endpoint("/api/v1/skin-logs?child_id=$encodedChildId"))
        val growthJson = getJson(ApiConfig.endpoint("/api/v1/growth-logs?child_id=$encodedChildId"))
        val imagingJson = getJson(ApiConfig.endpoint("/api/v1/imaging?child_id=$encodedChildId"))
        val reportsJson = getJson(ApiConfig.endpoint("/api/v1/reports?child_id=$encodedChildId"))
        val alertsJson = getJson(ApiConfig.endpoint("/api/v1/alerts?child_id=$encodedChildId"))

        val summary =
            JSONObject()
                .put("wear_records", wearJson.itemCount())
                .put("skin_logs", skinJson.itemCount())
                .put("growth_logs", growthJson.itemCount())
                .put("imaging_logs", imagingJson.itemCount())
                .put("reports", reportsJson.itemCount())
                .put("alerts", alertsJson.itemCount())

        return JSONObject()
            .put(
                "metadata",
                JSONObject()
                    .put("app", "Spinecare Mom")
                    .put("project", ApiConfig.projectCode)
                    .put("export_version", 1)
                    .put("exported_at", LocalDateTime.now().toString())
                    .put("child_id", targetChildId)
                    .put("purpose", "backup_before_delete")
                    .put("notice", "请确认本备份文件已保存并可打开后，再执行删除数据。"),
            )
            .put("summary", summary)
            .put("profile", profileJson)
            .put("wear_records", wearJson.optJSONArray("items") ?: JSONArray())
            .put("skin_logs", skinJson.optJSONArray("items") ?: JSONArray())
            .put("growth_logs", growthJson.optJSONArray("items") ?: JSONArray())
            .put("imaging_logs", imagingJson.optJSONArray("items") ?: JSONArray())
            .put("reports", reportsJson.optJSONArray("items") ?: JSONArray())
            .put("alerts", alertsJson.optJSONArray("items") ?: JSONArray())
            .put("local_settings", localBackupSettingsJson())
    }

    private fun localBackupSettingsJson(): JSONObject =
        JSONObject()
            .put("selected_language", languages.getOrNull(selectedLanguageIndex) ?: "简体中文")
            .put("last_bluetooth_name", prefs.getString(KEY_LAST_BLUETOOTH_NAME, null) ?: JSONObject.NULL)
            .put("last_bluetooth_address", prefs.getString(KEY_LAST_BLUETOOTH_ADDRESS, null) ?: JSONObject.NULL)
            .put("last_export_at", prefs.getString(KEY_LAST_DATA_EXPORT_AT, null) ?: JSONObject.NULL)

    private fun dataExportSummaryText(payload: JSONObject): String {
        val summary = payload.optJSONObject("summary") ?: JSONObject()
        return "本次备份：佩戴${summary.optInt("wear_records")}天，皮肤${summary.optInt("skin_logs")}条，生长${summary.optInt("growth_logs")}条，影像${summary.optInt("imaging_logs")}条，报告${summary.optInt("reports")}份，预警${summary.optInt("alerts")}条。"
    }

    private fun dataExportFileName(): String {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"))
        val safeChildId = child.id.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "child" }
        return "spinecaremom_backup_${safeChildId}_$timestamp.json"
    }

    private fun lastDataExportTimeText(): String =
        prefs.getString(KEY_LAST_DATA_EXPORT_AT, null)
            ?.replace("T", " ")
            ?.substringBefore(".")
            ?.take(16)
            ?: "暂无备份记录"

    private fun JSONObject.itemCount(): Int =
        optJSONArray("items")?.length() ?: 0

    private fun hasRecentDataExport(): Boolean =
        prefs.getString(KEY_LAST_DATA_EXPORT_AT, null).isNullOrBlank().not()

    private fun loadDeleteRequest(force: Boolean = false) {
        val targetChildId = child.id
        if (!force && (dataDeleteLoading || dataDeleteLoadedChildId == targetChildId)) {
            return
        }
        dataDeleteLoading = true
        thread(name = "SpinecareDeleteRequestFetch") {
            runCatching {
                getJson(ApiConfig.endpoint("/api/v1/delete-requests/current?child_id=${urlEncode(targetChildId)}"))
            }.onSuccess { response ->
                mainHandler.post {
                    if (targetChildId != child.id) {
                        dataDeleteLoading = false
                        return@post
                    }
                    dataDeleteRequest = response.optJSONObject("item")
                    dataDeleteLoadedChildId = targetChildId
                    dataDeleteLoading = false
                    if (stage == Stage.App && currentTab == MainTab.DataDelete) {
                        render()
                    }
                }
            }.onFailure {
                mainHandler.post {
                    if (targetChildId != child.id) {
                        dataDeleteLoading = false
                        return@post
                    }
                    dataDeleteLoading = false
                    dataDeleteStatusMessage = "删除申请状态读取失败，请稍后重试"
                    if (stage == Stage.App && currentTab == MainTab.DataDelete) {
                        render()
                    }
                }
            }
        }
    }

    private fun submitDeleteRequest() {
        if (!hasRecentDataExport()) {
            dataDeleteStatusMessage = "请先导出数据并确认备份文件可打开"
            render()
            return
        }
        if (!(dataDeleteBackupChecked && dataDeleteIrreversibleChecked && dataDeleteCurrentChildChecked)) {
            dataDeleteStatusMessage = "请先勾选全部确认项"
            render()
            return
        }
        if (dataDeleteNicknameInput.trim() != child.nickname) {
            dataDeleteStatusMessage = "孩子昵称输入不匹配，无法提交删除申请"
            render()
            return
        }
        if (dataDeletePhraseInput.trim() != DELETE_CONFIRMATION_TEXT) {
            dataDeleteStatusMessage = "确认文字必须完整输入“删除全部数据”"
            render()
            return
        }
        dataDeleteInProgress = true
        dataDeleteStatusMessage = "正在提交删除申请..."
        render()
        val payload =
            JSONObject()
                .put("child_id", child.id)
                .put("child_nickname", dataDeleteNicknameInput.trim())
                .put("backup_confirmed", dataDeleteBackupChecked)
                .put("irreversible_confirmed", dataDeleteIrreversibleChecked)
                .put("current_child_confirmed", dataDeleteCurrentChildChecked)
                .put("confirmation_text", dataDeletePhraseInput.trim())
        thread(name = "SpinecareDeleteRequestCreate") {
            runCatching {
                postJson(ApiConfig.endpoint("/api/v1/delete-requests"), payload)
            }.onSuccess { response ->
                mainHandler.post {
                    dataDeleteInProgress = false
                    dataDeleteRequest = response.optJSONObject("item")
                    dataDeleteLoadedChildId = child.id
                    dataDeleteStatusMessage = "删除申请已提交，24小时冷静期内可撤销"
                    Toast.makeText(this, dataDeleteStatusMessage, Toast.LENGTH_SHORT).show()
                    render()
                }
            }.onFailure {
                mainHandler.post {
                    dataDeleteInProgress = false
                    dataDeleteStatusMessage = "删除申请提交失败，请确认昵称和确认文字后重试"
                    Toast.makeText(this, dataDeleteStatusMessage, Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
    }

    private fun cancelDeleteRequest() {
        val requestId = dataDeleteRequest?.optString("id").orEmpty()
        if (requestId.isBlank()) return
        dataDeleteInProgress = true
        dataDeleteStatusMessage = "正在撤销删除申请..."
        render()
        thread(name = "SpinecareDeleteRequestCancel") {
            runCatching {
                postJson(ApiConfig.endpoint("/api/v1/delete-requests/${urlEncode(requestId)}/cancel"), JSONObject())
            }.onSuccess { response ->
                mainHandler.post {
                    dataDeleteInProgress = false
                    dataDeleteRequest = response.optJSONObject("item")
                    dataDeleteStatusMessage = "删除申请已撤销"
                    Toast.makeText(this, dataDeleteStatusMessage, Toast.LENGTH_SHORT).show()
                    render()
                }
            }.onFailure {
                mainHandler.post {
                    dataDeleteInProgress = false
                    dataDeleteStatusMessage = "撤销失败，请稍后重试"
                    Toast.makeText(this, dataDeleteStatusMessage, Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
    }

    private fun executeDeleteRequest() {
        val requestId = dataDeleteRequest?.optString("id").orEmpty()
        if (requestId.isBlank()) return
        if (dataDeletePhraseInput.trim() != DELETE_CONFIRMATION_TEXT) {
            dataDeleteStatusMessage = "执行删除前，请再次输入“删除全部数据”"
            render()
            return
        }
        dataDeleteInProgress = true
        dataDeleteStatusMessage = "正在执行云端删除..."
        render()
        val payload = JSONObject().put("confirmation_text", dataDeletePhraseInput.trim())
        thread(name = "SpinecareDeleteRequestExecute") {
            runCatching {
                postJson(ApiConfig.endpoint("/api/v1/delete-requests/${urlEncode(requestId)}/execute"), payload)
            }.onSuccess { response ->
                mainHandler.post {
                    dataDeleteInProgress = false
                    dataDeleteRequest = response.optJSONObject("item")
                    dataDeleteStatusMessage = "云端数据已删除"
                    Toast.makeText(this, dataDeleteStatusMessage, Toast.LENGTH_SHORT).show()
                    resetLocalAfterCloudDelete()
                    render()
                }
            }.onFailure {
                mainHandler.post {
                    dataDeleteInProgress = false
                    dataDeleteStatusMessage = "暂不能执行删除：请确认冷静期已结束并稍后重试"
                    Toast.makeText(this, dataDeleteStatusMessage, Toast.LENGTH_SHORT).show()
                    render()
                }
            }
        }
    }

    private fun resetLocalAfterCloudDelete() {
        prefs.edit().clear().apply()
        wearCloudRecords = emptyList()
        visitWearRecords = emptyList()
        skinLogs = emptyList()
        growthLogs = emptyList()
        imagingLogs = emptyList()
        archivedReports = emptyList()
        selectedBluetoothDevice = null
        connectedBluetoothDevice = null
        dataDeleteRequest = null
        dataDeleteNicknameInput = ""
        dataDeletePhraseInput = ""
        dataDeleteBackupChecked = false
        dataDeleteIrreversibleChecked = false
        dataDeleteCurrentChildChecked = false
        loginPhone = ""
        verificationCode = ""
        stage = Stage.Login
        currentTab = MainTab.Home
        profileStep = 1
    }

    private fun canSubmitDeleteRequest(): Boolean =
        hasRecentDataExport() &&
            dataDeleteBackupChecked &&
            dataDeleteIrreversibleChecked &&
            dataDeleteCurrentChildChecked &&
            !dataDeleteInProgress

    private fun canExecuteDeleteRequest(): Boolean =
        dataDeleteRequest?.optString("status") == "confirmed" && !dataDeleteInProgress

    private fun deleteRequestSubtitle(request: JSONObject?): String =
        when (request?.optString("status")) {
            "confirmed" -> "冷静期至：${deleteRequestTimeText(request.optString("scheduled_delete_at"))}"
            "cancelled" -> "已撤销：${deleteRequestTimeText(request.optString("cancelled_at"))}"
            "completed" -> "已删除：${deleteRequestTimeText(request.optString("completed_at"))}"
            else -> "暂无删除申请"
        }

    private fun deleteRequestChip(request: JSONObject?): View =
        when (request?.optString("status")) {
            "confirmed" -> chip("冷静期", P.warning)
            "cancelled" -> chip("已撤销", P.muted)
            "completed" -> chip("已删除", P.danger)
            else -> chip("未申请", P.muted)
        }

    private fun deleteRequestTimeText(value: String): String =
        value.replace("T", " ").substringBefore(".").take(16).ifBlank { "未知" }

    private fun saveProfileToCloud() {
        profileSyncStatusMessage = "正在保存建档信息到云端..."
        val payload = buildProfilePayload()
        thread(name = "SpinecareProfileUpload") {
            runCatching {
                postJson(ApiConfig.endpoint("/api/v1/children/profile"), payload)
            }.onSuccess { response ->
                mainHandler.post {
                    profileSyncStatusMessage =
                        if (response.optBoolean("ok", false)) {
                            "建档信息已保存到云端"
                        } else {
                            "建档信息保存失败，请稍后重试"
                        }
                    Toast.makeText(this, profileSyncStatusMessage, Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                mainHandler.post {
                    profileSyncStatusMessage = "建档信息保存失败，请稍后重试"
                    Toast.makeText(this, profileSyncStatusMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun buildProfilePayload(): JSONObject {
        val payload =
            JSONObject()
                .put("child_id", child.id)
                .put("phone", loginPhone)
                .put("verification_code", verificationCode)
                .put("login_method", loginMethod)
                .put("consent_accepted", consentChecked)
                .put("nickname", child.nickname)
                .put("gender", child.gender)
                .put("cobb_initial", child.cobb)
                .put("curve_type", child.curveType)
                .put("risser", child.risser)
                .put("prescribed_hours", child.prescribedHours)
                .put("brace_type", child.braceType)
                .put("app_project", ApiConfig.projectCode)
                .put(
                    "raw",
                    JSONObject()
                        .put("source", "android_profile_wizard")
                        .put("phone_input", loginPhone)
                        .put("verification_code_input", verificationCode)
                        .put("login_method", loginMethod),
                )
        payload.put("birth_date", child.birthDate.takeIf(::isIsoDate) ?: JSONObject.NULL)
        payload.put("first_visit_date", child.firstVisitDate.takeIf(::isIsoDate) ?: JSONObject.NULL)
        return payload
    }

    private fun isProfileCompleted(): Boolean =
        prefs.getBoolean(KEY_PROFILE_COMPLETED, false)

    private fun loadProfileFromLocal() {
        loginPhone = prefs.getString(KEY_LOGIN_PHONE, loginPhone) ?: loginPhone
        verificationCode = prefs.getString(KEY_VERIFICATION_CODE, verificationCode) ?: verificationCode
        loginMethod = prefs.getString(KEY_LOGIN_METHOD, loginMethod) ?: loginMethod
        consentChecked = prefs.getBoolean(KEY_CONSENT_CHECKED, consentChecked)
        agreementReadConfirmed = prefs.getBoolean(KEY_LOGIN_AGREEMENT_READ, consentChecked)
        child.id = prefs.getString(KEY_PROFILE_ID, child.id) ?: child.id
        child.nickname = prefs.getString(KEY_PROFILE_NICKNAME, child.nickname) ?: child.nickname
        child.gender = prefs.getString(KEY_PROFILE_GENDER, child.gender) ?: child.gender
        child.birthDate = prefs.getString(KEY_PROFILE_BIRTH_DATE, child.birthDate) ?: child.birthDate
        child.curveType = prefs.getString(KEY_PROFILE_CURVE_TYPE, child.curveType) ?: child.curveType
        child.cobb = prefs.getInt(KEY_PROFILE_COBB, child.cobb).coerceIn(0, 120)
        child.risser = prefs.getString(KEY_PROFILE_RISSER, child.risser) ?: child.risser
        child.prescribedHours = prefs.getInt(KEY_PROFILE_PRESCRIBED_HOURS, child.prescribedHours).coerceIn(0, 24)
        child.braceType = prefs.getString(KEY_PROFILE_BRACE_TYPE, child.braceType) ?: child.braceType
        child.firstVisitDate = prefs.getString(KEY_PROFILE_FIRST_VISIT_DATE, child.firstVisitDate) ?: child.firstVisitDate
    }

    private fun saveProfileLocally(completed: Boolean) {
        prefs.edit()
            .putBoolean(KEY_PROFILE_COMPLETED, completed)
            .putString(KEY_LOGIN_PHONE, loginPhone)
            .putString(KEY_VERIFICATION_CODE, verificationCode)
            .putString(KEY_LOGIN_METHOD, loginMethod)
            .putBoolean(KEY_CONSENT_CHECKED, consentChecked)
            .putBoolean(KEY_LOGIN_AGREEMENT_READ, agreementReadConfirmed)
            .putString(KEY_PROFILE_ID, child.id)
            .putString(KEY_PROFILE_NICKNAME, child.nickname)
            .putString(KEY_PROFILE_GENDER, child.gender)
            .putString(KEY_PROFILE_BIRTH_DATE, child.birthDate)
            .putString(KEY_PROFILE_CURVE_TYPE, child.curveType)
            .putInt(KEY_PROFILE_COBB, child.cobb)
            .putString(KEY_PROFILE_RISSER, child.risser)
            .putInt(KEY_PROFILE_PRESCRIBED_HOURS, child.prescribedHours)
            .putString(KEY_PROFILE_BRACE_TYPE, child.braceType)
            .putString(KEY_PROFILE_FIRST_VISIT_DATE, child.firstVisitDate)
            .apply()
    }

    private fun isRemoteProfileUsable(profile: JSONObject): Boolean =
        profile.has("id") || profile.has("nickname") || profile.has("birth_date") || profile.has("cobb_initial")

    private fun applyProfileFromJson(profile: JSONObject) {
        child.id = profile.optNonBlankString("id", child.id)
        child.nickname = profile.optNonBlankString("nickname", child.nickname)
        child.gender = normalizeProfileGender(profile.optNonBlankString("gender", child.gender))
        child.birthDate = profile.optIsoDate("birth_date", child.birthDate)
        child.cobb = profile.optNullableInt("cobb_initial", child.cobb).coerceIn(0, 120)
        child.curveType = normalizeCurveType(profile.optNonBlankString("curve_type", child.curveType))
        child.risser = profile.optNonBlankString("risser", child.risser)
        child.prescribedHours = profile.optNullableDouble("prescribed_hours", child.prescribedHours.toDouble()).roundToInt().coerceIn(0, 24)
        child.braceType = normalizeBraceType(profile.optNonBlankString("brace_type", child.braceType))
        child.firstVisitDate = profile.optIsoDate("first_visit_date", child.firstVisitDate)
        loginPhone = profile.optNonBlankString("guardian_phone", loginPhone)
        verificationCode = profile.optNonBlankString("verification_code", verificationCode)
        loginMethod = profile.optNonBlankString("login_method", loginMethod)
        if (profile.has("consent_accepted") && !profile.isNull("consent_accepted")) {
            consentChecked = profile.optBoolean("consent_accepted", consentChecked)
            if (consentChecked) {
                agreementReadConfirmed = true
            }
        }
    }

    private fun JSONObject.optNonBlankString(key: String, fallback: String): String =
        if (has(key) && !isNull(key)) {
            optString(key, fallback).trim().ifBlank { fallback }
        } else {
            fallback
        }

    private fun JSONObject.optIsoDate(key: String, fallback: String): String {
        val value = optNonBlankString(key, fallback)
        return value.takeIf(::isIsoDate) ?: fallback
    }

    private fun JSONObject.optNullableInt(key: String, fallback: Int): Int =
        if (has(key) && !isNull(key)) optInt(key, fallback) else fallback

    private fun JSONObject.optNullableDouble(key: String, fallback: Double): Double =
        if (has(key) && !isNull(key)) optDouble(key, fallback) else fallback

    private fun normalizeProfileGender(value: String): String =
        when (value.lowercase(Locale.US)) {
            "male", "m", "boy" -> "男"
            "female", "f", "girl" -> "女"
            else -> value.takeIf { it in listOf("男", "女") } ?: "女"
        }

    private fun normalizeCurveType(value: String): String =
        when (value.lowercase(Locale.US)) {
            "thoracic" -> "胸弯"
            "lumbar" -> "腰弯"
            "thoracolumbar", "thoraco_lumbar", "thoraco-lumbar" -> "胸腰弯"
            "double", "double_curve", "double-curve" -> "双弯"
            else -> value.ifBlank { child.curveType }
        }

    private fun normalizeBraceType(value: String): String =
        when (value.lowercase(Locale.US)) {
            "rigid", "hard" -> "硬支具"
            "soft" -> "软支具"
            "unknown" -> "未知"
            else -> value.ifBlank { child.braceType }
        }

    private fun isIsoDate(value: String): Boolean =
        Regex("""\d{4}-\d{2}-\d{2}""").matches(value.trim())

    private fun wearPointsForUpload(snapshot: SpineBraceHistorySnapshot): List<SpineBraceWearPoint> {
        return snapshot.points
            .sortedBy { it.recordedAt }
            .map { it.copy(recordedAt = normalizeWearSampleSlot(it.recordedAt)) }
            .groupBy { it.recordedAt }
            .map { (slot, samples) ->
                SpineBraceWearPoint(
                    recordedAt = slot,
                    worn = samples.all { it.worn },
                )
            }
            .sortedBy { it.recordedAt }
    }

    private fun normalizeWearSampleSlot(value: LocalDateTime): LocalDateTime {
        val minute = (value.minute / 10) * 10
        return value.withMinute(minute).withSecond(0).withNano(0)
    }

    private fun buildDeviceWearUploadPayload(
        snapshot: SpineBraceHistorySnapshot,
        device: SpineBraceDevice?,
    ): DeviceWearUploadPackage {
        val uploadPoints = wearPointsForUpload(snapshot)
        val hourlyRows = bluetoothValidationHours(uploadPoints)
        val hourlyRowsByDate = hourlyRows.groupBy { it.hourStart.toLocalDate() }
        val records = JSONArray()
        uploadPoints
            .groupBy { it.recordedAt.toLocalDate() }
            .toSortedMap()
            .forEach { (date, points) ->
                val wornCount = points.count { it.worn }
                val wornHours = ((wornCount * 10f / 60f) * 10f).roundToInt() / 10f
                val hourlyRecords = JSONArray()
                hourlyRowsByDate[date].orEmpty().forEach { item ->
                    val samples = JSONArray()
                    item.samples.forEach { sample ->
                        samples.put(
                            JSONObject()
                                .put("recorded_at", sample.recordedAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                .put("worn", sample.worn),
                        )
                    }
                    hourlyRecords.put(
                        JSONObject()
                            .put("hour_start", formatValidationHour(item.hourStart))
                            .put("worn_hours", item.wornHours)
                            .put("sample_count", item.sampleCount)
                            .put("worn_count", item.wornCount)
                            .put("samples", samples),
                    )
                }
                records.put(
                    JSONObject()
                        .put("date", date.format(DateTimeFormatter.ISO_LOCAL_DATE))
                        .put("worn_hours", wornHours)
                        .put("sample_count", points.size)
                        .put("worn_count", wornCount)
                        .put("sample_interval_minutes", 10)
                        .put("hourly_records", hourlyRecords),
                )
            }

        val raw =
            JSONObject()
                .put("packet_count", snapshot.packetCount)
                .put("total_bits", snapshot.totalBits)
                .put("worn_bits", snapshot.wornBits)
                .put("complete", snapshot.complete)
                .put("app_project", ApiConfig.projectCode)
                .put("history_zero_bit_means_worn", SpineBraceBluetoothManager.HISTORY_ZERO_BIT_MEANS_WORN)
                .put("current_telemetry_included", false)
                .put("current_telemetry_saved_to_wear_records", false)
                .put("current_telemetry_worn", latestBraceTelemetry?.worn ?: JSONObject.NULL)
                .put("device_history_clear_required_after_cloud_verify", false)
                .put("device_history_clear_policy", "clear_after_local_verify_before_cloud_upload")
        val lastReadAt = snapshot.header?.lastReadAt
        snapshot.header?.let { header ->
            raw.put("last_read_at", lastReadAt?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: header.lastReadAtText)
            raw.put("last_read_time_source", "04_header_time_written_by_01_clear_command")
            raw.put("device_time", header.deviceTimeText)
            raw.put("history_head", header.head)
            raw.put("history_count", header.count)
            raw.put("next_save_seconds", header.nextSaveSeconds)
        }

        val payload =
            JSONObject()
                .put("child_id", child.id)
                .put("device_name", device?.name ?: JSONObject.NULL)
                .put("device_address", device?.address ?: JSONObject.NULL)
                .put("records", records)
                .put("raw", raw)
        return DeviceWearUploadPackage(
            childId = child.id,
            payload = payload,
            hourlyRows = hourlyRows,
            expectedWearPoints = uploadPoints,
            fetchedAt = latestHistoryReceivedAt ?: LocalDateTime.now(),
            lastReadAt = lastReadAt,
            historyHead = snapshot.header?.head,
            historyCount = snapshot.header?.count,
            dailyCount = records.length(),
            totalWornHours = roundOne(hourlyRows.sumOf { it.wornHours }),
            deviceName = device?.name,
        )
    }

    private fun postJson(urlString: String, body: JSONObject): JSONObject {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { output ->
                output.write(body.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val responseText =
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code $responseText")
            }
            if (responseText.isBlank()) JSONObject().put("ok", true) else JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadBitmapPhoto(bitmap: Bitmap): JSONObject {
        return uploadBitmapImage(bitmap, "skin")
    }

    private fun uploadBitmapImage(bitmap: Bitmap, prefix: String): JSONObject {
        val buffer = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 82, buffer)
        return uploadImageBytes(buffer.toByteArray(), "${prefix}_${System.currentTimeMillis()}.jpg", "image/jpeg")
    }

    private fun uploadImageUri(uri: Uri): JSONObject {
        val mimeType = contentResolver.getType(uri) ?: "image/jpeg"
        val extension = imageExtensionFromMimeType(mimeType)
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("无法读取选择的图片")
        return uploadImageBytes(bytes, "imaging_${System.currentTimeMillis()}$extension", mimeType)
    }

    private fun uploadImageBytes(bytes: ByteArray, filename: String, mimeType: String): JSONObject {
        val boundary = "SpinecareMom${System.currentTimeMillis()}"
        val lineEnd = "\r\n"
        val connection = (URL(ApiConfig.endpoint("/api/v1/uploads")).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            connection.outputStream.use { output ->
                output.write("--$boundary$lineEnd".toByteArray(Charsets.UTF_8))
                output.write("Content-Disposition: form-data; name=\"file\"; filename=\"$filename\"$lineEnd".toByteArray(Charsets.UTF_8))
                output.write("Content-Type: $mimeType$lineEnd$lineEnd".toByteArray(Charsets.UTF_8))
                output.write(bytes)
                output.write(lineEnd.toByteArray(Charsets.UTF_8))
                output.write("--$boundary--$lineEnd".toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val responseText =
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code $responseText")
            }
            if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun imageExtensionFromMimeType(mimeType: String): String =
        when (mimeType.lowercase(Locale.US)) {
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/gif" -> ".gif"
            else -> ".jpg"
        }

    private fun JSONObject.uploadedFileReference(): String? =
        optString("id", "").takeIf { it.isNotBlank() }
            ?: optString("filename", "").takeIf { it.isNotBlank() }

    private fun getJson(urlString: String): JSONObject {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
        }
        return try {
            val code = connection.responseCode
            val responseText =
                (if (code in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader(Charsets.UTF_8)
                    ?.use { it.readText() }
                    .orEmpty()
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code $responseText")
            }
            if (responseText.isBlank()) JSONObject() else JSONObject(responseText)
        } finally {
            connection.disconnect()
        }
    }

    private fun sendQuestion(raw: String) {
        val question = raw.trim()
        if (question.isBlank()) return
        chatMessages.add(ChatMessage.User(question))
        chatMessages.add(ChatMessage.Ai(createAiReply(question)))
        currentTab = MainTab.Consult
        render()
    }

    private fun createAiReply(question: String): AiReply {
        val emergency = emergencyKeywords.any { question.contains(it) }
        if (emergency) {
            return AiReply(
                summary = "出现红线症状时应尽快联系医生或支具师",
                analysis = "问题包含需要及时处理的风险描述，云端数据仅用于辅助整理背景，不替代医生判断。",
                advice = listOf("保留症状照片和发生时间", "尽快联系主治医生或支具师", "若症状加重或伴随全身不适，及时就诊"),
                needDoctor = true,
                doctorReason = "命中强制就医关键词，AI 不做诊断或停戴判断，需要由医生或支具师评估。",
                category = "clinical",
            )
        }

        if (question.contains("笑") || question.contains("不肯") || question.contains("焦虑")) {
            return AiReply(
                summary = "孩子抗拒时，先降低对抗感，再把佩戴拆成可完成的小目标",
                analysis = "将根据云端近期佩戴趋势判断抗拒是否已经影响依从性，并结合缺口时段给出沟通建议。",
                advice = listOf("和孩子约定一个可选择的提醒时间", "把下午缺口拆成30分钟一段", "达标后只反馈进步，不反复追问"),
                needDoctor = false,
                doctorReason = "",
                category = "emotion",
            )
        }

        if (question.contains("体育") || question.contains("运动")) {
            return AiReply(
                summary = "运动安排需要遵循医生医嘱，APP 可帮助整理复诊问题",
                analysis = "将根据云端医嘱佩戴时长、近期达标情况和缺口时段辅助整理运动相关复诊问题。",
                advice = listOf("把体育课项目和时长记录下来", "复诊时询问哪些运动需要脱戴", "运动后检查皮肤摩擦点并补足可佩戴时段"),
                needDoctor = true,
                doctorReason = "涉及运动期间是否脱戴或调整佩戴方案，需要主治医生或支具师确认。",
                category = "clinical",
            )
        }

        return AiReply(
            summary = "可以先从最明显的缺口时段补起，不需要一次改变全部习惯",
            analysis = "将根据云端近30天和近7天佩戴数据识别主要缺口，并与医嘱佩戴时长比较。",
            advice = listOf("下午14点开启一次短提醒", "把放学后第一小时设为固定佩戴段", "睡前用30秒检查皮肤和支具位置"),
            needDoctor = false,
            doctorReason = "",
            category = "education",
        )
    }

    private fun screenPage(showBottomPadding: Boolean, build: LinearLayout.() -> Unit): ScrollView {
        return ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(
                vertical {
                    setPadding(dp(16), dp(18), dp(16), if (showBottomPadding) dp(20) else dp(18))
                    build()
                },
                matchLp(),
            )
        }
    }

    private fun pageHeader(title: String, subtitle: String): View =
        vertical {
            addView(label(subtitle, 13f, P.secondary))
            addView(label(title, 24f, P.text, Typeface.BOLD))
        }

    private fun appHeader(
        title: String,
        subtitle: String,
        showBell: Boolean,
        showBack: Boolean = false,
        showSettings: Boolean = false,
        backTarget: MainTab = MainTab.Home,
        onBack: (() -> Unit)? = null,
    ): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                vertical {
                    if (subtitle.isNotBlank()) {
                        addView(label(subtitle, 13f, P.secondary))
                    }
                    addView(label(title, 24f, P.text, Typeface.BOLD))
                },
                weightLp(1f),
            )
            if (showBell) {
                addView(bellButton(), widthHeightLp(dp(48), dp(48)))
            }
            if (showBack) {
                if (showBell) addSpace(8, horizontal = true)
                addView(secondaryButton("返回") {
                    if (onBack != null) {
                        onBack()
                    } else {
                        currentTab = backTarget
                        render()
                    }
                }, widthLp(dp(76)))
            }
            if (showSettings) {
                if (showBell || showBack) addSpace(8, horizontal = true)
                addView(iconButton("⚙", "设置") {
                    currentTab = MainTab.Settings
                    render()
                }, widthHeightLp(dp(48), dp(48)))
            }
        }

    private fun bellButton(): View =
        FrameLayout(this).apply {
            addView(
                iconButton("🔔", "消息") {
                    alertsBackTarget = currentTab
                    currentTab = MainTab.Alerts
                    render()
                },
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            if (unreadAlertCount() > 0) {
                addView(
                    View(this@MainActivity).apply {
                        background = rounded(P.danger, dp(99), Color.WHITE)
                    },
                    FrameLayout.LayoutParams(dp(13), dp(13), Gravity.TOP or Gravity.END).apply {
                        topMargin = dp(5)
                        rightMargin = dp(5)
                    },
                )
            }
        }

    private fun unreadAlertCount(): Int =
        if (alertsLoadedChildId == child.id) {
            alertItems.count { it.isPendingAlert() }
        } else {
            0
        }

    private fun brandMark(): View =
        TextView(this).apply {
            text = "+"
            textSize = 34f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(P.primary)
            background = rounded(P.primaryLight, dp(22), null)
        }.also {
            it.layoutParams = widthHeightLp(dp(74), dp(74))
        }

    private fun card(build: LinearLayout.() -> Unit): LinearLayout =
        vertical {
            background = rounded(P.surface, dp(12), P.softLine)
            elevation = dp(2).toFloat()
            setPadding(dp(16), dp(16), dp(16), dp(16))
            build()
        }

    private fun cardHeader(title: String, subtitle: String?, trailing: View?): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            addView(
                vertical {
                    addView(label(title, 17f, P.text, Typeface.BOLD))
                    if (subtitle != null) addView(label(subtitle, 13f, P.secondary))
                },
                weightLp(1f),
            )
            if (trailing != null) addView(trailing)
        }

    private fun deviceRow(name: String, subtitle: String): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.line)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            addView(
                TextView(this@MainActivity).apply {
                    text = "BT"
                    textSize = 13f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(P.primary)
                    background = rounded(P.primaryLight, dp(12), null)
                },
                widthHeightLp(dp(40), dp(40)),
            )
            addSpace(10, horizontal = true)
            addView(
                vertical {
                    addView(label(name, 15f, P.text, Typeface.BOLD))
                    addView(label(subtitle, 13f, P.secondary))
                },
                weightLp(1f),
            )
            addView(secondaryButton("绑定") {
                stage = Stage.Profile
                render()
            }, widthLp(dp(72)))
        }

    private fun field(
        label: String,
        value: String,
        inputType: Int,
        onChanged: ((String) -> Unit)? = null,
    ): View =
        vertical {
            addView(label(label, 13f, P.secondary, Typeface.BOLD))
            addSpace(5)
            addView(
                EditText(this@MainActivity).apply {
                    setText(value)
                    textSize = 15f
                    setSingleLine(true)
                    this.inputType = inputType
                    setTextColor(P.text)
                    setHintTextColor(P.muted)
                    background = rounded(P.surfaceAlt, dp(8), P.line)
                    setPadding(dp(12), 0, dp(12), 0)
                    if (onChanged != null) {
                        addTextChangedListener(
                            object : TextWatcher {
                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                    onChanged(s?.toString().orEmpty().trim())
                                }

                                override fun afterTextChanged(s: Editable?) = Unit
                            },
                        )
                    }
                },
                matchHeightLp(dp(46)),
            )
        }

    private fun choiceRow(
        title: String,
        options: List<String>,
        selectedIndex: Int,
        onSelect: ((String) -> Unit)? = null,
    ): View =
        vertical {
            addView(label(title, 13f, P.secondary, Typeface.BOLD))
            addSpace(6)
            addView(
                grid(if (options.size > 4) 3 else 2) {
                    options.forEachIndexed { index, option ->
                        addView(
                            Button(this@MainActivity).apply {
                                text = uiText(option)
                                transformationMethod = null
                                textSize = 14f
                                setTextColor(if (index == selectedIndex) P.primary else P.secondary)
                                background = rounded(if (index == selectedIndex) P.primaryLight else P.surfaceAlt, dp(8), P.line)
                                setOnClickListener { onSelect?.invoke(option) }
                            },
                            matchHeightLp(dp(42)),
                        )
                    }
                },
            )
        }

    private fun multiChoiceRow(
        title: String,
        options: List<String>,
        selected: Set<String>,
        onToggle: (String) -> Unit,
    ): View =
        vertical {
            addView(label(title, 13f, P.secondary, Typeface.BOLD))
            addSpace(6)
            addView(
                grid(if (options.size > 4) 3 else 2) {
                    options.forEach { option ->
                        val checked = selected.contains(option)
                        addView(
                            Button(this@MainActivity).apply {
                                text = uiText(option)
                                transformationMethod = null
                                textSize = 14f
                                typeface = Typeface.DEFAULT_BOLD
                                setTextColor(if (checked) P.primary else P.secondary)
                                background = rounded(if (checked) P.primaryLight else P.surfaceAlt, dp(8), if (checked) P.primary else P.line)
                                setOnClickListener { onToggle(option) }
                            },
                            matchHeightLp(dp(42)),
                        )
                    }
                },
            )
        }

    private fun metricGrid(
        items: List<Pair<String, String>>,
        columns: Int = 3,
        itemHeightDp: Int = 78,
        valueSize: Float = 20f,
        labelSize: Float = 12f,
    ): View =
        grid(columns) {
            items.forEach { (value, label) ->
                addView(
                    vertical {
                        background = rounded(0xFFF9FBFB.toInt(), dp(8), P.softLine)
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                        addView(label(value, valueSize, P.text, Typeface.BOLD).apply {
                            includeFontPadding = false
                        })
                        addSpace(4)
                        addView(label(label, labelSize, P.secondary).apply {
                            includeFontPadding = false
                            maxLines = 2
                        })
                    },
                    matchHeightLp(dp(itemHeightDp)),
                )
            }
        }

    private fun LinearLayout.addQuick(text: String, onClick: () -> Unit) {
        addView(
            Button(this@MainActivity).apply {
                this.text = uiText(text)
                transformationMethod = null
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(P.primary)
                background = rounded(P.surface, dp(12), P.softLine)
                setOnClickListener { onClick() }
            },
            matchHeightLp(dp(82)),
        )
    }

    private fun privacyActionRow(
        title: String,
        subtitle: String,
        icon: String,
        accent: Int,
        onClick: () -> Unit,
    ): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            isClickable = true
            isFocusable = true
            contentDescription = title
            minimumHeight = dp(76)
            elevation = dp(4).toFloat()
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = quickCardBackground(accent)
            addView(
                TextView(this@MainActivity).apply {
                    text = uiText(icon)
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    includeFontPadding = false
                    setTextColor(Color.WHITE)
                    background = rounded(accent, dp(12), null)
                },
                widthHeightLp(dp(44), dp(44)),
            )
            addSpace(12, horizontal = true)
            addView(
                vertical {
                    addView(label(title, 15f, P.text, Typeface.BOLD).apply {
                        includeFontPadding = false
                    })
                    addSpace(4)
                    addView(label(subtitle, 13f, P.secondary).apply {
                        includeFontPadding = false
                        maxLines = 2
                    })
                },
                weightLp(1f),
            )
            addSpace(8, horizontal = true)
            addView(label(">", 18f, accent, Typeface.BOLD, Gravity.CENTER))
            setOnClickListener {
                Toast.makeText(this@MainActivity, uiText("已选择$title"), Toast.LENGTH_SHORT).show()
                mainHandler.postDelayed({ onClick() }, 140L)
            }
        }

    private fun LinearLayout.addHomeQuick(
        title: String,
        icon: String,
        subtitle: String,
        accent: Int,
        onClick: () -> Unit,
    ) {
        addView(
            horizontal {
                gravity = Gravity.CENTER_VERTICAL
                isClickable = true
                isFocusable = true
                contentDescription = title
                minimumHeight = dp(92)
                elevation = dp(5).toFloat()
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = quickCardBackground(accent)

                val iconView =
                    TextView(this@MainActivity).apply {
                        text = uiText(icon)
                        textSize = if (icon.length > 1) 14f else 18f
                        typeface = Typeface.DEFAULT_BOLD
                        gravity = Gravity.CENTER
                        includeFontPadding = false
                        setTextColor(Color.WHITE)
                        background = rounded(accent, dp(12), null)
                    }
                val titleView =
                    label(title, 15f, P.text, Typeface.BOLD).apply {
                        includeFontPadding = false
                        maxLines = 1
                    }
                val statusView =
                    label(subtitle, 12f, accent, Typeface.BOLD).apply {
                        includeFontPadding = false
                        maxLines = 1
                    }

                addView(iconView, widthHeightLp(dp(42), dp(42)))
                addSpace(10, horizontal = true)
                addView(
                    vertical {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(titleView)
                        addSpace(5)
                        addView(statusView)
                    },
                    weightLp(1f),
                )

                setOnClickListener {
                    isEnabled = false
                    elevation = dp(8).toFloat()
                    background = rounded(adjustAlpha(accent, 0.18f), dp(14), accent)
                    iconView.background = rounded(P.surface, dp(12), accent)
                    iconView.setTextColor(accent)
                    titleView.setTextColor(accent)
                    statusView.text = uiText("正在打开")
                    statusView.setTextColor(accent)
                    Toast.makeText(this@MainActivity, uiText("已选择$title"), Toast.LENGTH_SHORT).show()
                    mainHandler.postDelayed({ onClick() }, 160L)
                }
            },
            matchHeightLp(dp(92)),
        )
    }

    private fun quickCardBackground(accent: Int): android.graphics.drawable.StateListDrawable =
        android.graphics.drawable.StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(adjustAlpha(accent, 0.22f), dp(14), accent))
            addState(intArrayOf(-android.R.attr.state_enabled), rounded(adjustAlpha(accent, 0.18f), dp(14), accent))
            addState(intArrayOf(), rounded(P.surface, dp(14), adjustAlpha(accent, 0.22f)))
        }

    private fun chip(text: String, color: Int): TextView =
        TextView(this).apply {
            this.text = uiText(text)
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(color)
            gravity = Gravity.CENTER
            minHeight = dp(28)
            setPadding(dp(10), 0, dp(10), 0)
            background = rounded(adjustAlpha(color, 0.14f), dp(100), null)
        }

    private fun chipButton(text: String, onClick: () -> Unit): View =
        Button(this).apply {
            this.text = uiText(text)
            transformationMethod = null
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(
                android.content.res.ColorStateList(
                    arrayOf(
                        intArrayOf(android.R.attr.state_pressed),
                        intArrayOf(-android.R.attr.state_enabled),
                        intArrayOf(),
                    ),
                    intArrayOf(Color.WHITE, Color.WHITE, P.primary),
                ),
            )
            background =
                android.graphics.drawable.StateListDrawable().apply {
                    addState(intArrayOf(android.R.attr.state_pressed), rounded(P.primary, dp(100), P.primary))
                    addState(intArrayOf(-android.R.attr.state_enabled), rounded(P.success, dp(100), P.success))
                    addState(intArrayOf(), rounded(P.primaryLight, dp(100), P.primaryLight))
                }
            setOnClickListener {
                isEnabled = false
                this.text = uiText("已发送")
                mainHandler.postDelayed({ onClick() }, 140L)
            }
        }

    private fun alertBanner(title: String, subtitle: String, color: Int, onClick: () -> Unit): View =
        Button(this).apply {
            text = "${uiText(title)}\n${uiText(subtitle)}"
            transformationMethod = null
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            setPadding(dp(16), 0, dp(16), 0)
            background = rounded(color, dp(12), null)
            setOnClickListener { onClick() }
        }.also {
            it.layoutParams = matchHeightLp(dp(62))
        }

    private fun LinearLayout.addInsight(text: String) {
        addView(
            horizontal {
                setPadding(0, dp(6), 0, dp(6))
                addView(chip("•", P.primary), widthHeightLp(dp(28), dp(28)))
                addSpace(8, horizontal = true)
                addView(label(text, 15f, P.secondary), weightLp(1f))
            },
        )
    }

    private fun userBubble(text: String): View =
        horizontal {
            gravity = Gravity.END
            addView(
                label(text, 15f, Color.WHITE).apply {
                    background = rounded(P.primary, dp(16), null)
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                },
                bubbleLp(),
            )
        }

    private fun aiCard(reply: AiReply): View =
        card {
            addView(
                horizontal {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(chip("结构化回答", P.primary))
                    addView(Space(this@MainActivity), weightLp(1f))
                    addView(chip(if (reply.needDoctor) "建议就医" else "健康教育", if (reply.needDoctor) P.danger else P.success))
                },
            )
            addSpace(10)
            addAiSection("一句话总结", reply.summary)
            addAiSection("结合${child.nickname}数据的分析", consultCloudAnalysis(reply.category, reply.analysis))
            addAiSection("可执行建议", reply.advice.joinToString("\n") { "• $it" })
            if (reply.needDoctor) {
                addView(
                    vertical {
                        background = rounded(adjustAlpha(P.danger, 0.1f), dp(8), adjustAlpha(P.danger, 0.24f))
                        setPadding(dp(12), dp(10), dp(12), dp(10))
                        addView(label("就医提示", 15f, P.danger, Typeface.BOLD))
                        addView(label(reply.doctorReason, 14f, P.secondary))
                        addSpace(8)
                        addView(secondaryButton("加入复诊问题清单") {}, matchLp())
                    },
                )
            }
            addSpace(8)
            addView(label(disclaimer, 12f, P.muted))
        }

    private fun LinearLayout.addAiSection(title: String, body: String) {
        addView(
            vertical {
                background = rounded(0xFFF9FBFB.toInt(), dp(8), null)
                setPadding(dp(10), dp(9), dp(10), dp(9))
                addView(label(title, 14f, P.text, Typeface.BOLD))
                addView(label(body, 14f, P.secondary))
            },
        )
        addSpace(8)
    }

    private fun reportRow(title: String, subtitle: String, status: String, color: Int): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(
                vertical {
                    addView(label(title, 15f, P.text, Typeface.BOLD))
                    addView(label(subtitle, 13f, P.secondary))
                },
                weightLp(1f),
            )
            addView(chip(status, color))
        }

    private fun LinearLayout.addPaperRow(title: String, body: String) {
        addView(
            horizontal {
                addView(label(title, 13f, P.text, Typeface.BOLD), widthLp(dp(86)))
                addView(label(body, 13f, P.secondary), weightLp(1f))
            },
        )
        addSpace(8)
    }

    private fun imageRow(title: String, subtitle: String): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(
                TextView(this@MainActivity).apply {
                    text = uiText("片")
                    textSize = 16f
                    typeface = Typeface.DEFAULT_BOLD
                    gravity = Gravity.CENTER
                    setTextColor(P.primary)
                    background = rounded(P.primaryLight, dp(8), null)
                },
                widthHeightLp(dp(52), dp(52)),
            )
            addSpace(10, horizontal = true)
            addView(
                vertical {
                    addView(label(title, 15f, P.text, Typeface.BOLD))
                    addView(label(subtitle, 13f, P.secondary))
                },
                weightLp(1f),
            )
            addView(label(">", 18f, P.muted, Typeface.BOLD))
        }

    private fun settingRow(title: String, subtitle: String, status: String, onClick: (() -> Unit)? = null): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(
                vertical {
                    addView(label(title, 15f, P.text, Typeface.BOLD))
                    addView(label(subtitle, 13f, P.secondary))
                },
                weightLp(1f),
            )
            addView(chip(status, if (status == "正常") P.success else P.primary))
            if (onClick != null) setOnClickListener { onClick() }
        }

    private fun toggleRow(title: String, subtitle: String, on: Boolean, onClick: () -> Unit): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(
                vertical {
                    addView(label(title, 15f, P.text, Typeface.BOLD))
                    addView(label(subtitle, 13f, P.secondary))
                },
                weightLp(1f),
            )
            addView(chip(if (on) "开" else "关", if (on) P.primary else P.muted))
            setOnClickListener { onClick() }
        }

    private fun languageRadioGroup(): View =
        RadioGroup(this).apply {
            orientation = RadioGroup.VERTICAL
            setPadding(0, dp(2), 0, 0)
            languages.forEachIndexed { index, language ->
                addView(
                    RadioButton(this@MainActivity).apply {
                        id = View.generateViewId()
                        tag = index
                        text = language
                        textSize = 15f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(P.text)
                        buttonTintList = android.content.res.ColorStateList.valueOf(P.primary)
                        background = rounded(P.surfaceAlt, dp(8), P.softLine)
                        setPadding(dp(12), 0, dp(12), 0)
                        isChecked = index == selectedLanguageIndex
                    },
                    matchHeightLp(dp(48)),
                )
                if (index < languages.lastIndex) {
                    addSpace(dp(8))
                }
            }
            setOnCheckedChangeListener { group, checkedId ->
                val selectedIndex = group.findViewById<View>(checkedId)?.tag as? Int ?: return@setOnCheckedChangeListener
                if (selectedIndex != selectedLanguageIndex) {
                    selectedLanguageIndex = selectedIndex
                    saveSelectedLanguageIndex(selectedIndex)
                    render()
                }
            }
        }

    private fun loadSelectedLanguageIndex(): Int =
        prefs.getInt(KEY_SELECTED_LANGUAGE_INDEX, 0).coerceIn(0, languages.lastIndex)

    private fun saveSelectedLanguageIndex(index: Int) {
        prefs.edit().putInt(KEY_SELECTED_LANGUAGE_INDEX, index.coerceIn(0, languages.lastIndex)).apply()
    }

    private fun alertRow(level: String, title: String, subtitle: String, color: Int): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(chip(level, color), widthLp(dp(48)))
            addSpace(10, horizontal = true)
            addView(
                vertical {
                    addView(label(title, 15f, P.text, Typeface.BOLD))
                    addView(label(subtitle, 13f, P.secondary))
                },
                weightLp(1f),
            )
        }

    private fun alertRow(item: AlertItem): View =
        vertical {
            val color = alertLevelColor(item.level)
            background = rounded(P.surfaceAlt, dp(8), adjustAlpha(color, 0.18f))
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(
                horizontal {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(chip(alertLevelText(item.level), color), widthLp(dp(48)))
                    addSpace(10, horizontal = true)
                    addView(
                        vertical {
                            addView(label(item.title, 15f, P.text, Typeface.BOLD))
                            item.summary.takeIf { it.isNotBlank() && it != "null" }?.let { summary ->
                                addView(label(summary, 13f, P.secondary))
                            }
                        },
                        weightLp(1f),
                    )
                    addSpace(8, horizontal = true)
                    addView(chip(alertStatusText(item), if (item.isPendingAlert()) P.warning else P.success))
                },
            )
            item.triggerDetail.takeIf { it.isNotBlank() && it != "null" }?.let { detail ->
                addSpace(8)
                addView(label(detail, 13f, P.secondary).apply {
                    background = rounded(P.surface, dp(8), P.softLine)
                    setPadding(dp(10), dp(8), dp(10), dp(8))
                })
            }
            addSpace(8)
            addView(
                horizontal {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(label("时间：${archiveCreatedAtText(item.createdAt)}", 12f, P.muted), weightLp(1f))
                    if (item.isPendingAlert()) {
                        val handling = handlingAlertIds.contains(item.id)
                        addSpace(8, horizontal = true)
                        addView(
                            primaryButton(if (handling) "处理中" else "处理") {
                                if (!handling) {
                                    handleAlert(item)
                                }
                            }.apply {
                                isEnabled = !handling
                                alpha = if (handling) 0.55f else 1f
                            },
                            widthLp(dp(84)),
                        )
                    }
                },
            )
        }

    private fun handleAlert(item: AlertItem) {
        if (!item.isPendingAlert() || !handlingAlertIds.add(item.id)) {
            return
        }
        render()
        thread(name = "SpinecareAlertHandle") {
            runCatching {
                postJson(ApiConfig.endpoint("/api/v1/alerts/${urlEncode(item.id)}/handle"), JSONObject())
            }.onSuccess {
                mainHandler.post {
                    handlingAlertIds.remove(item.id)
                    alertItems = alertItems.map { alert -> if (alert.id == item.id) alert.copy(status = "handled") else alert }
                    render()
                    loadAlerts(force = true)
                    Toast.makeText(this, "消息已处理", Toast.LENGTH_SHORT).show()
                }
            }.onFailure {
                mainHandler.post {
                    handlingAlertIds.remove(item.id)
                    alertsStatusMessage = "消息处理失败，请稍后重试。"
                    render()
                    loadAlerts(force = true)
                    Toast.makeText(this, alertsStatusMessage, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun AlertItem.isPendingAlert(): Boolean {
        val normalized = status.trim().lowercase(Locale.US)
        if (normalized.isBlank() || normalized == "null") {
            return true
        }
        return normalized !in setOf("handled", "processed", "resolved", "read", "closed", "done") &&
            !status.contains("已处理") &&
            !status.contains("已读") &&
            !status.contains("关闭")
    }

    private fun alertStatusText(item: AlertItem): String =
        if (item.isPendingAlert()) "待处理" else "已处理"

    private fun alertLevelText(level: String): String =
        when (level.trim().lowercase(Locale.US)) {
            "red" -> "红"
            "yellow" -> "黄"
            "green" -> "绿"
            else -> "消息"
        }

    private fun alertLevelColor(level: String): Int =
        when (level.trim().lowercase(Locale.US)) {
            "red" -> P.danger
            "yellow" -> P.warning
            "green" -> P.success
            else -> P.primary
        }

    private fun alertLevelRank(level: String): Int =
        when (level.trim().lowercase(Locale.US)) {
            "red" -> 0
            "yellow" -> 1
            "green" -> 2
            else -> 3
        }

    private fun avatar(text: String): View =
        TextView(this).apply {
            this.text = text
            textSize = 22f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = rounded(P.primary, dp(18), null)
        }.also {
            it.layoutParams = widthHeightLp(dp(56), dp(56))
        }

    private fun navButton(icon: String, text: String, active: Boolean, onClick: () -> Unit): View =
        vertical {
            gravity = Gravity.CENTER
            isClickable = true
            isFocusable = true
            minimumHeight = dp(58)
            setPadding(dp(4), dp(4), dp(4), dp(5))
            background =
                if (active) {
                    rounded(P.primaryLight, dp(16), adjustAlpha(P.primary, 0.2f))
                } else {
                    rounded(Color.TRANSPARENT, dp(16), null)
                }
            elevation = if (active) dp(5).toFloat() else 0f
            addView(
                View(this@MainActivity).apply {
                    background = rounded(if (active) P.primary else Color.TRANSPARENT, dp(99), null)
                },
                widthHeightLp(dp(22), dp(3)),
            )
            addSpace(2)
            addView(label(icon, 17f, if (active) P.primary else P.secondary, Typeface.BOLD, Gravity.CENTER).apply {
                includeFontPadding = false
            })
            addSpace(1)
            addView(label(text, 12f, if (active) P.primary else P.secondary, Typeface.BOLD, Gravity.CENTER).apply {
                includeFontPadding = false
            })
            setOnClickListener { onClick() }
        }

    private fun primaryButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = uiText(text)
            transformationMethod = null
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(P.primary, dp(8), null)
            setOnClickListener { onClick() }
        }

    private fun secondaryButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = uiText(text)
            transformationMethod = null
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(P.primary)
            background = rounded(P.primaryLight, dp(8), adjustAlpha(P.primary, 0.18f))
            setOnClickListener { onClick() }
        }

    private fun iconButton(text: String, description: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            contentDescription = uiText(description)
            transformationMethod = null
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(P.primary)
            background = rounded(P.primaryLight, dp(8), adjustAlpha(P.primary, 0.18f))
            setOnClickListener { onClick() }
        }

    private fun dangerButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = uiText(text)
            transformationMethod = null
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(P.danger, dp(8), null)
            setOnClickListener { onClick() }
        }

    private fun longPressDangerButton(text: String, enabled: Boolean, onLongClick: () -> Unit): Button =
        Button(this).apply {
            this.text = uiText(text)
            transformationMethod = null
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            isEnabled = enabled
            setTextColor(if (enabled) Color.WHITE else P.muted)
            background = rounded(if (enabled) P.danger else P.surfaceAlt, dp(8), if (enabled) null else P.softLine)
            setOnClickListener {
                Toast.makeText(this@MainActivity, uiText("请长按按钮确认操作"), Toast.LENGTH_SHORT).show()
            }
            setOnLongClickListener {
                if (enabled) onLongClick()
                true
            }
        }

    private fun deleteConfirmCheckRow(text: String, checked: Boolean, onChanged: (Boolean) -> Unit): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(8), dp(8), dp(10), dp(8))
            addView(
                CheckBox(this@MainActivity).apply {
                    isChecked = checked
                    buttonTintList = android.content.res.ColorStateList.valueOf(P.danger)
                    setOnCheckedChangeListener { _, isChecked -> onChanged(isChecked) }
                },
                widthHeightLp(dp(42), dp(42)),
            )
            addSpace(6, horizontal = true)
            addView(label(text, 14f, P.secondary, Typeface.BOLD), weightLp(1f))
            setOnClickListener { onChanged(!checked) }
        }

    private fun progressLine(value: Int): View =
        FrameLayout(this).apply {
            background = rounded(0xFFE4EEEE.toInt(), dp(100), null)
            addView(
                View(this@MainActivity).apply { background = rounded(P.primary, dp(100), null) },
                FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT).apply {
                    width = dp((260 * value / 100f).roundToInt())
                },
            )
        }.also {
            it.layoutParams = matchHeightLp(dp(10))
        }

    private fun rowText(left: String, right: String): View =
        horizontal {
            addView(label(left, 15f, P.text, Typeface.BOLD), weightLp(1f))
            addView(label(right, 13f, P.secondary))
        }

    private fun infoStrip(text: String): View =
        label(text, 14f, P.secondary).apply {
            background = rounded(0xFFEDF4F4.toInt(), dp(8), P.line)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }

    private fun segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit): View =
        horizontal {
            background = rounded(0xFFEAF0F0.toInt(), dp(12), null)
            setPadding(dp(4), dp(4), dp(4), dp(4))
            labels.forEachIndexed { index, label ->
                addView(
                    Button(this@MainActivity).apply {
                        text = uiText(label)
                        transformationMethod = null
                        textSize = 13f
                        typeface = Typeface.DEFAULT_BOLD
                        setTextColor(if (index == selected) P.primary else P.secondary)
                        background = rounded(if (index == selected) P.surface else Color.TRANSPARENT, dp(8), null)
                        setOnClickListener { onSelect(index) }
                    },
                    weightHeightLp(1f, dp(40)),
                )
            }
        }

    private fun grid(columns: Int, build: LinearLayout.() -> Unit): LinearLayout {
        val container = vertical()
        val temp = mutableListOf<View>()
        val collector = object : LinearLayout(this) {
            override fun addView(child: View?) {
                if (child != null) temp.add(child)
            }

            override fun addView(child: View?, params: ViewGroup.LayoutParams?) {
                if (child != null) {
                    child.layoutParams = params
                    temp.add(child)
                }
            }
        }
        collector.build()
        temp.chunked(columns).forEach { rowItems ->
            container.addView(
                horizontal {
                    rowItems.forEachIndexed { index, item ->
                        if (index > 0) addSpace(8, horizontal = true)
                        addView(item, LinearLayout.LayoutParams(0, item.layoutParams?.height ?: ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    }
                    repeat(columns - rowItems.size) {
                        addSpace(8, horizontal = true)
                        addView(Space(this@MainActivity), weightLp(1f))
                    }
                },
            )
            if (rowItems != temp.takeLast(rowItems.size)) {
                container.addSpace(8)
            }
        }
        return container
    }

    private fun label(
        text: String,
        size: Float,
        color: Int,
        style: Int = Typeface.NORMAL,
        gravity: Int = Gravity.START,
    ): TextView =
        TextView(this).apply {
            this.text = uiText(text)
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.DEFAULT, style)
            this.gravity = gravity
            includeFontPadding = true
            setLineSpacing(dp(1).toFloat(), 1f)
        }

    private fun vertical(build: LinearLayout.() -> Unit = {}): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            build()
        }

    private fun horizontal(build: LinearLayout.() -> Unit = {}): LinearLayout =
        LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            build()
        }

    private fun LinearLayout.addSpace(size: Int, horizontal: Boolean = false) {
        addView(
            Space(this@MainActivity),
            if (horizontal) LinearLayout.LayoutParams(size, 1) else LinearLayout.LayoutParams(1, size),
        )
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int?): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
    }

    private fun complianceLevelColor(percent: Int): Int =
        when {
            percent >= 90 -> P.primary
            percent >= 75 -> P.success
            percent >= 50 -> P.warning
            else -> P.danger
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun matchLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun matchHeightLp(height: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, height)

    private fun widthLp(width: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT)

    private fun widthHeightLp(width: Int, height: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(width, height)

    private fun squareLp(size: Int): LinearLayout.LayoutParams = widthHeightLp(size, size)

    private fun weightLp(weight: Float): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight)

    private fun weightHeightLp(weight: Float, height: Int): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams(0, height, weight)

    private fun bubbleLp(): LinearLayout.LayoutParams =
        LinearLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.78f).roundToInt(), ViewGroup.LayoutParams.WRAP_CONTENT)

    private class ProgressRingView(
        context: android.content.Context,
        private val progress: Int,
        private val caption: String,
        private val progressColor: Int,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val size = min(width, height).toFloat()
            val stroke = size * 0.13f
            val cx = width / 2f
            val cy = height / 2f
            rect.set(cx - size / 2f + stroke, cy - size / 2f + stroke, cx + size / 2f - stroke, cy + size / 2f - stroke)

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.BUTT
            paint.strokeWidth = stroke
            paint.color = 0xFFE2EEEE.toInt()
            canvas.drawArc(rect, -90f, 360f, false, paint)
            paint.color = progressColor
            canvas.drawArc(rect, -90f, progress * 3.6f, false, paint)

            paint.style = Paint.Style.FILL
            paint.textAlign = Paint.Align.CENTER
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.textSize = size * 0.22f
            paint.color = P.text
            canvas.drawText("$progress%", cx, cy - size * 0.02f, paint)
            paint.typeface = Typeface.DEFAULT
            paint.textSize = size * 0.1f
            paint.color = P.secondary
            canvas.drawText(caption, cx, cy + size * 0.14f, paint)
        }
    }

    private class BarChartView(
        context: android.content.Context,
        private val values: List<Number>,
        private val dateLabels: Map<Int, String> = emptyMap(),
        private val topLabels: Map<Int, String> = emptyMap(),
        private val targetValue: Double = 82.0,
        private val maxValue: Double = 100.0,
    ) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val chartValues = values.map { it.toDouble().coerceAtLeast(0.0) }
            if (chartValues.isEmpty()) {
                return
            }
            val topPad = if (topLabels.isEmpty()) height * 0.14f else dpLocal(28).toFloat()
            val bottomPad = if (dateLabels.isEmpty()) height * 0.08f else dpLocal(30).toFloat()
            val chartHeight = height - topPad - bottomPad
            val gap = dpLocal(5).toFloat()
            val barWidth = (width - gap * (chartValues.size - 1)) / chartValues.size
            val chartMaxValue = listOf(maxValue, targetValue, chartValues.maxOrNull() ?: 0.0, 1.0).maxOrNull() ?: 1.0

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpLocal(1).toFloat()
            paint.color = adjustAlphaStatic(P.primary, 0.42f)
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
            val targetRatio = (targetValue / chartMaxValue).coerceIn(0.0, 1.0).toFloat()
            val targetY = topPad + chartHeight * (1f - targetRatio)
            canvas.drawLine(0f, targetY, width.toFloat(), targetY, paint)
            paint.pathEffect = null

            chartValues.forEachIndexed { index, value ->
                val left = index * (barWidth + gap)
                val valueRatio = (value / chartMaxValue).coerceIn(0.0, 1.0).toFloat()
                val barHeight = chartHeight * valueRatio
                val top = topPad + chartHeight - barHeight
                paint.style = Paint.Style.FILL
                paint.color = wearLevelColor(value, targetValue)
                canvas.drawRoundRect(left, top, left + barWidth, topPad + chartHeight, dpLocal(7).toFloat(), dpLocal(7).toFloat(), paint)
                topLabels[index]?.let { text ->
                    paint.style = Paint.Style.FILL
                    paint.typeface = Typeface.DEFAULT_BOLD
                    paint.textSize = spLocal(10)
                    paint.textAlign = Paint.Align.CENTER
                    paint.color = P.text
                    val minBaseline = paint.textSize + dpLocal(2)
                    val baseline = (top - dpLocal(6)).coerceAtLeast(minBaseline)
                    canvas.drawText(text, left + barWidth / 2f, baseline, paint)
                }
            }
            if (dateLabels.isNotEmpty()) {
                paint.style = Paint.Style.FILL
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textSize = spLocal(10)
                paint.color = P.secondary
                val baseline = height - dpLocal(7).toFloat()
                dateLabels.forEach { (index, text) ->
                    if (index in values.indices) {
                        val left = index * (barWidth + gap)
                        val center = left + barWidth / 2f
                        paint.textAlign =
                            when (index) {
                                0 -> Paint.Align.LEFT
                                chartValues.lastIndex -> Paint.Align.RIGHT
                                else -> Paint.Align.CENTER
                            }
                        val x =
                            when (index) {
                                0 -> 0f
                                chartValues.lastIndex -> width.toFloat()
                                else -> center
                            }
                        canvas.drawText(text, x, baseline, paint)
                    }
                }
            }
        }

        private fun dpLocal(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

        private fun spLocal(value: Int): Float = value * resources.displayMetrics.scaledDensity

        private fun wearLevelColor(value: Double, target: Double): Int {
            if (target <= 0.0) {
                return P.danger
            }
            val ratio = value / target
            return when {
                ratio >= 1.0 -> P.primary
                ratio >= 0.85 -> P.success
                ratio >= 0.7 -> P.warning
                else -> P.danger
            }
        }
    }

    private class GrowthChartView(context: android.content.Context, records: List<GrowthLog>) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()
        private val chartRecords = records.sortedBy { it.date }.takeLast(8)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val left = dpLocal(34).toFloat()
            val right = width - dpLocal(18).toFloat()
            val top = dpLocal(34).toFloat()
            val bottom = height - dpLocal(34).toFloat()
            paint.strokeWidth = dpLocal(1).toFloat()
            paint.color = P.line
            repeat(4) { i ->
                val y = top + (bottom - top) * i / 3f
                canvas.drawLine(left, y, right, y, paint)
            }

            if (chartRecords.isEmpty()) {
                paint.style = Paint.Style.FILL
                paint.textSize = dpLocal(13).toFloat()
                paint.typeface = Typeface.DEFAULT_BOLD
                paint.color = P.muted
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("暂无身高记录", width / 2f, (top + bottom) / 2f, paint)
                return
            }

            val heights = chartRecords.map { it.heightCm }
            val minHeight = heights.minOrNull() ?: 0.0
            val maxHeight = heights.maxOrNull() ?: minHeight
            val rawRange = maxHeight - minHeight
            val chartMin: Double
            val chartMax: Double
            if (rawRange < 0.1) {
                chartMin = minHeight - 1.0
                chartMax = maxHeight + 1.0
            } else {
                val padding = rawRange * 0.22
                chartMin = minHeight - padding
                chartMax = maxHeight + padding
            }
            val chartRange = (chartMax - chartMin).takeIf { it > 0.0 } ?: 1.0
            val points =
                chartRecords.mapIndexed { index, item ->
                    val x =
                        if (chartRecords.size == 1) {
                            (left + right) / 2f
                        } else {
                            left + (right - left) * index / (chartRecords.size - 1).toFloat()
                        }
                    val ratio = ((item.heightCm - chartMin) / chartRange).coerceIn(0.0, 1.0).toFloat()
                    val y = bottom - (bottom - top) * ratio
                    Triple(x, y, item)
                }

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpLocal(4).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = P.primary
            if (points.size > 1) {
                path.reset()
                points.forEachIndexed { index, point ->
                    if (index == 0) {
                        path.moveTo(point.first, point.second)
                    } else {
                        path.lineTo(point.first, point.second)
                    }
                }
                canvas.drawPath(path, paint)
            }

            paint.style = Paint.Style.FILL
            points.forEach { (x, y, item) ->
                paint.color = Color.WHITE
                canvas.drawCircle(x, y, dpLocal(7).toFloat(), paint)
                paint.color = P.primary
                canvas.drawCircle(x, y, dpLocal(5).toFloat(), paint)

                paint.typeface = Typeface.DEFAULT_BOLD
                paint.textSize = dpLocal(11).toFloat()
                paint.textAlign = Paint.Align.CENTER
                paint.color = P.text
                val labelBaseline = (y - dpLocal(12)).coerceAtLeast(paint.textSize + dpLocal(2))
                canvas.drawText(formatHeightLabel(item.heightCm), x, labelBaseline, paint)
            }

            paint.textSize = dpLocal(11).toFloat()
            paint.typeface = Typeface.DEFAULT_BOLD
            paint.color = P.secondary
            points.forEachIndexed { index, (x, _, item) ->
                if (chartRecords.size <= 6 || index == 0 || index == chartRecords.lastIndex || index % 2 == 0) {
                    paint.textAlign =
                        when (index) {
                            0 -> Paint.Align.LEFT
                            chartRecords.lastIndex -> Paint.Align.RIGHT
                            else -> Paint.Align.CENTER
                        }
                    val labelX =
                        when (index) {
                            0 -> left
                            chartRecords.lastIndex -> right
                            else -> x
                        }
                    canvas.drawText("${item.date.monthValue}/${item.date.dayOfMonth}", labelX, height - dpLocal(8).toFloat(), paint)
                }
            }
        }

        private fun dpLocal(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun formatHeightLabel(value: Double): String =
        if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            String.format(Locale.US, "%.1f", value)
        }

    }

    private fun uiText(text: String): String {
        val language = selectedLanguageIndex.coerceIn(0, languages.lastIndex)
        if (language == 0 || text.isBlank()) return text
        exactUiTranslations[text]?.getOrNull(language - 1)?.let { return it }
        var result = text
        uiPhraseTranslations
            .mapNotNull { (source, targets) -> targets.getOrNull(language - 1)?.let { source to it } }
            .sortedByDescending { it.first.length }
            .forEach { (source, target) ->
                result = result.replace(source, target)
            }
        return result
    }

    private fun languageText(
        zh: String,
        en: String,
        ja: String,
        ko: String,
        es: String,
        fr: String,
        de: String,
    ): String =
        when (selectedLanguageIndex.coerceIn(0, languages.lastIndex)) {
            1 -> en
            2 -> ja
            3 -> ko
            4 -> es
            5 -> fr
            6 -> de
            else -> zh
        }

    companion object {
        private const val PREFS_NAME = "spinecaremom_prefs"
        private const val KEY_LAST_BLUETOOTH_NAME = "last_bluetooth_name"
        private const val KEY_LAST_BLUETOOTH_ADDRESS = "last_bluetooth_address"
        private const val KEY_SELECTED_LANGUAGE_INDEX = "selected_language_index"
        private const val KEY_PROFILE_COMPLETED = "profile_completed"
        private const val KEY_PROFILE_ID = "profile_id"
        private const val KEY_PROFILE_NICKNAME = "profile_nickname"
        private const val KEY_PROFILE_GENDER = "profile_gender"
        private const val KEY_PROFILE_BIRTH_DATE = "profile_birth_date"
        private const val KEY_PROFILE_CURVE_TYPE = "profile_curve_type"
        private const val KEY_PROFILE_COBB = "profile_cobb"
        private const val KEY_PROFILE_RISSER = "profile_risser"
        private const val KEY_PROFILE_PRESCRIBED_HOURS = "profile_prescribed_hours"
        private const val KEY_PROFILE_BRACE_TYPE = "profile_brace_type"
        private const val KEY_PROFILE_FIRST_VISIT_DATE = "profile_first_visit_date"
        private const val KEY_LOGIN_PHONE = "login_phone"
        private const val KEY_VERIFICATION_CODE = "verification_code"
        private const val KEY_LOGIN_METHOD = "login_method"
        private const val KEY_CONSENT_CHECKED = "consent_checked"
        private const val KEY_LOGIN_AGREEMENT_READ = "login_agreement_read"
        private const val KEY_LAST_DATA_EXPORT_AT = "last_data_export_at"
        private const val KEY_DEVICE_SYNC_PHASE = "device_sync_phase"
        private const val KEY_DEVICE_SYNC_UPDATED_AT = "device_sync_updated_at"
        private const val KEY_DEVICE_SYNC_DEVICE_NAME = "device_sync_device_name"
        private const val KEY_DEVICE_SYNC_STATUS = "device_sync_status"
        private const val DEVICE_SYNC_PHASE_READING = "reading"
        private const val DEVICE_SYNC_PHASE_LOCAL_SAVING = "local_saving"
        private const val DEVICE_SYNC_PHASE_LOCAL_VERIFYING = "local_verifying"
        private const val DEVICE_SYNC_PHASE_PENDING_CLOUD_UPLOAD = "pending_cloud_upload"
        private const val DEVICE_SYNC_PHASE_CLOUD_UPLOADING = "cloud_uploading"
        private const val DEVICE_SYNC_PHASE_VERIFYING = "verifying"
        private const val DEVICE_SYNC_PHASE_CLEARING = "clearing"
        private const val DEVICE_SYNC_PHASE_INTERRUPTED = "interrupted"
        private const val LOCAL_DEVICE_WEAR_SESSION_TEMP_FILE_NAME = "device_wear_session.tmp"
        private const val LOCAL_DEVICE_WEAR_FILE_NAME = "pending_device_wear_sync.json"
        private const val LOCAL_DEVICE_WEAR_ARCHIVE_FILE_NAME = "device_wear_local_archive.json"
        private const val DELETE_CONFIRMATION_TEXT = "删除全部数据"
        private const val COMPANY_NAME_ZH = "绍兴维脉科技有限公司"
        private const val COMPANY_ADDRESS_ZH = "浙江省绍兴市越城区袍中北路631号"
        private const val CONTACT_EMAIL = "zclei@vip.sina.com"
        private const val WECHAT_CONTACT_TEXT = "微信客服：Spinecare Mom"
        private const val AUTO_CONNECT_SCAN_TIMEOUT_MS = 20_000L
        private const val BLUETOOTH_AUTO_DISCONNECT_MS = 10 * 60 * 1_000L
        private const val BLUETOOTH_AUTO_DISCONNECT_DEFER_MS = 60_000L
        private const val DEVICE_HISTORY_INITIAL_TIMEOUT_MS = 20_000L
        private const val DEVICE_HISTORY_PACKET_IDLE_TIMEOUT_MS = 30_000L
        private const val MAX_DEVICE_HISTORY_READ_RETRIES = 0
        private const val DEVICE_SYNC_RECONNECT_DELAY_MS = 1_500L
        private const val MAX_DEVICE_SYNC_RECONNECT_RETRIES = 2
        private const val LOW_BATTERY_WARNING_MESSAGE = "电池电量小于20%，请更换电池"
        private const val AUTO_CONNECT_NOT_FOUND_MESSAGE = "没有发现蓝牙设备，请到设置界面扫描连接设备"
        private const val DEVICE_SYNC_INTERRUPTED_WARNING_MESSAGE = "上次数据获取未完成，设备数据已保留，请靠近设备重新连接"

        private val exactUiTranslations = mapOf(
            "处理" to listOf("Handle", "処理", "처리", "Procesar", "Traiter", "Bearbeiten"),
            "处理中" to listOf("Handling", "処理中", "처리 중", "Procesando", "Traitement", "Wird bearbeitet"),
            "消息已处理" to listOf("Message handled", "メッセージを処理しました", "메시지가 처리되었습니다", "Mensaje procesado", "Message traité", "Nachricht bearbeitet"),
            "消息处理失败，请稍后重试。" to listOf(
                "Failed to handle message. Please try again later.",
                "メッセージの処理に失敗しました。後でもう一度お試しください。",
                "메시지 처리에 실패했습니다. 나중에 다시 시도해 주세요.",
                "No se pudo procesar el mensaje. Inténtelo de nuevo más tarde.",
                "Impossible de traiter le message. Veuillez réessayer plus tard.",
                "Nachricht konnte nicht bearbeitet werden. Bitte versuchen Sie es später erneut.",
            ),
            "我已阅读并同意《隐私政策》《监护人授权告知》" to listOf(
                "I have read and agree to the Privacy Policy and Guardian Authorization Notice",
                "「プライバシーポリシー」と「保護者同意通知」を読み、同意します",
                "개인정보 처리방침 및 보호자 동의 안내를 읽고 동의합니다",
                "He leído y acepto la Política de privacidad y el Aviso de autorización del tutor",
                "J'ai lu et j'accepte la politique de confidentialité et l'autorisation du tuteur",
                "Ich habe die Datenschutzrichtlinie und die Erziehungsberechtigten-Erklärung gelesen und stimme zu",
            ),
            "我已阅读并同意《用户协议》《隐私政策》" to listOf(
                "I have read and agree to the User Agreement and Privacy Policy",
                "「ユーザー契約」と「プライバシーポリシー」を読み、同意します",
                "이용약관 및 개인정보 처리방침을 읽고 동의합니다",
                "He leído y acepto el Acuerdo de usuario y la Política de privacidad",
                "J'ai lu et j'accepte l'accord utilisateur et la politique de confidentialité",
                "Ich habe die Nutzungsvereinbarung und Datenschutzrichtlinie gelesen und stimme zu",
            ),
            "用户协议与隐私政策" to listOf(
                "User Agreement and Privacy Policy",
                "ユーザー契約とプライバシーポリシー",
                "이용약관 및 개인정보 처리방침",
                "Acuerdo de usuario y Política de privacidad",
                "Accord utilisateur et politique de confidentialité",
                "Nutzungsvereinbarung und Datenschutzrichtlinie",
            ),
            "登录前请先阅读用户协议和隐私政策。阅读完毕后勾选同意，才可以登录。" to listOf(
                "Please read the User Agreement and Privacy Policy before login. After reading, tick agreement to log in.",
                "ログイン前にユーザー契約とプライバシーポリシーをお読みください。読了後に同意を選択するとログインできます。",
                "로그인 전에 이용약관 및 개인정보 처리방침을 읽어 주세요. 읽은 후 동의에 체크해야 로그인할 수 있습니다.",
                "Lee el Acuerdo de usuario y la Política de privacidad antes de iniciar sesión. Después marca la aceptación para entrar.",
                "Veuillez lire l'accord utilisateur et la politique de confidentialité avant la connexion. Cochez l'accord après lecture pour vous connecter.",
                "Bitte lesen Sie vor der Anmeldung Nutzungsvereinbarung und Datenschutzrichtlinie. Danach Zustimmung markieren, um sich anzumelden.",
            ),
            "请先阅读用户协议，阅读完毕后再勾选同意。" to listOf(
                "Please read the User Agreement first, then tick agreement after reading.",
                "先にユーザー契約を読み、読了後に同意を選択してください。",
                "먼저 이용약관을 읽은 후 동의에 체크하세요.",
                "Lee primero el Acuerdo de usuario y luego marca la aceptación.",
                "Veuillez d'abord lire l'accord utilisateur, puis cocher l'accord.",
                "Bitte zuerst die Nutzungsvereinbarung lesen und danach Zustimmung markieren.",
            ),
            "请勾选已阅读并同意后再登录。" to listOf(
                "Please tick that you have read and agreed before logging in.",
                "読了と同意にチェックしてからログインしてください。",
                "읽고 동의함에 체크한 후 로그인하세요.",
                "Marca que has leído y aceptado antes de iniciar sesión.",
                "Veuillez cocher que vous avez lu et accepté avant de vous connecter.",
                "Bitte vor der Anmeldung bestätigen, dass Sie gelesen und zugestimmt haben.",
            ),
            "查看用户协议" to listOf("View Agreement", "契約を見る", "약관 보기", "Ver acuerdo", "Voir l'accord", "Vereinbarung anzeigen"),
            "收起用户协议" to listOf("Collapse Agreement", "契約を閉じる", "약관 접기", "Contraer acuerdo", "Réduire l'accord", "Vereinbarung einklappen"),
            "我已阅读完毕" to listOf("I have finished reading", "読み終えました", "읽기를 완료했습니다", "He terminado de leer", "J'ai terminé la lecture", "Ich habe fertig gelesen"),
            "已阅读，可以勾选同意并登录" to listOf(
                "Reading confirmed. You can tick agreement and log in.",
                "読了を確認しました。同意にチェックしてログインできます。",
                "읽기 확인 완료. 동의에 체크하고 로그인할 수 있습니다.",
                "Lectura confirmada. Puedes marcar la aceptación e iniciar sesión.",
                "Lecture confirmée. Vous pouvez cocher l'accord et vous connecter.",
                "Lesen bestätigt. Sie können Zustimmung markieren und sich anmelden.",
            ),
            "已阅读，请勾选同意后登录" to listOf(
                "Reading confirmed. Please tick agreement before logging in.",
                "読了を確認しました。ログイン前に同意を選択してください。",
                "읽기 확인 완료. 로그인 전에 동의에 체크하세요.",
                "Lectura confirmada. Marca la aceptación antes de iniciar sesión.",
                "Lecture confirmée. Veuillez cocher l'accord avant de vous connecter.",
                "Lesen bestätigt. Bitte vor der Anmeldung Zustimmung markieren.",
            ),
            "已阅读" to listOf("Read", "読了", "읽음", "Leído", "Lu", "Gelesen"),
            "待阅读" to listOf("To read", "未読", "읽기 필요", "Pendiente", "À lire", "Zu lesen"),
            "输入问题..." to listOf(
                "Enter a question...",
                "質問を入力...",
                "질문 입력...",
                "Escribe una pregunta...",
                "Saisissez une question...",
                "Frage eingeben...",
            ),
            "请长按按钮确认操作" to listOf(
                "Long press the button to confirm",
                "ボタンを長押しして確認してください",
                "버튼을 길게 눌러 확인하세요",
                "Mantén pulsado el botón para confirmar",
                "Appuyez longuement sur le bouton pour confirmer",
                "Halten Sie die Schaltfläche gedrückt, um zu bestätigen",
            ),
            LOW_BATTERY_WARNING_MESSAGE to listOf(
                "Battery is below 20%. Please replace the battery.",
                "電池残量が20%未満です。電池を交換してください。",
                "배터리가 20% 미만입니다. 배터리를 교체하세요.",
                "La batería está por debajo del 20 %. Reemplácela.",
                "La batterie est inférieure à 20 %. Veuillez la remplacer.",
                "Der Batteriestand liegt unter 20 %. Bitte Batterie ersetzen.",
            ),
            AUTO_CONNECT_NOT_FOUND_MESSAGE to listOf(
                "No Bluetooth device found. Please scan and connect in Settings.",
                "Bluetoothデバイスが見つかりません。設定画面でスキャンして接続してください。",
                "블루투스 기기를 찾지 못했습니다. 설정에서 스캔 후 연결하세요.",
                "No se encontró ningún dispositivo Bluetooth. Escanea y conecta en Ajustes.",
                "Aucun appareil Bluetooth trouvé. Scannez et connectez dans Paramètres.",
                "Kein Bluetooth-Gerät gefunden. Bitte in den Einstellungen scannen und verbinden.",
            ),
            "未连接设备，请扫描 WM-SP# 脊柱侧弯设备。" to listOf(
                "Not connected. Please scan for a WM-SP# scoliosis device.",
                "未接続です。WM-SP#脊柱側弯デバイスをスキャンしてください。",
                "연결되지 않았습니다. WM-SP# 척추측만 장치를 스캔하세요.",
                "Sin conexión. Escanea un dispositivo de escoliosis WM-SP#.",
                "Non connecté. Scannez un appareil de scoliose WM-SP#.",
                "Nicht verbunden. Bitte nach einem WM-SP#-Skoliosegerät suchen.",
            ),
            "正在扫描 WM-SP# 脊柱侧弯设备..." to listOf(
                "Scanning for WM-SP# scoliosis devices...",
                "WM-SP#脊柱側弯デバイスをスキャン中...",
                "WM-SP# 척추측만 장치를 스캔 중...",
                "Escaneando dispositivos de escoliosis WM-SP#...",
                "Recherche d'appareils de scoliose WM-SP#...",
                "Suche nach WM-SP#-Skoliosegeräten...",
            ),
            "扫描并连接 WM-SP# 设备，读取电量和月度佩戴记录" to listOf(
                "Scan and connect a WM-SP# device to read battery and monthly wearing records",
                "WM-SP#デバイスをスキャンして接続し、電池残量と月間装着記録を読み取ります",
                "WM-SP# 장치를 스캔하고 연결하여 배터리와 월간 착용 기록을 읽습니다",
                "Escanea y conecta un WM-SP# para leer batería y registros mensuales de uso",
                "Scannez et connectez un WM-SP# pour lire la batterie et les dossiers mensuels de port",
                "WM-SP# scannen und verbinden, um Batterie und monatliche Tragedaten zu lesen",
            ),
            "未扫描到设备。请确认支具传感器开机且蓝牙名以 WM-SP# 开头。" to listOf(
                "No device found. Make sure the brace sensor is on and its Bluetooth name starts with WM-SP#.",
                "デバイスが見つかりません。装具センサーの電源が入り、Bluetooth名がWM-SP#で始まることを確認してください。",
                "장치를 찾지 못했습니다. 보조기 센서가 켜져 있고 Bluetooth 이름이 WM-SP#로 시작하는지 확인하세요.",
                "No se encontró ningún dispositivo. Confirma que el sensor esté encendido y que el nombre Bluetooth empiece por WM-SP#.",
                "Aucun appareil trouvé. Vérifiez que le capteur est allumé et que son nom Bluetooth commence par WM-SP#.",
                "Kein Gerät gefunden. Prüfen Sie, ob der Sensor eingeschaltet ist und der Bluetooth-Name mit WM-SP# beginnt.",
            ),
            "请先扫描并选择 WM-SP# 设备" to listOf(
                "Please scan and select a WM-SP# device first",
                "先にWM-SP#デバイスをスキャンして選択してください",
                "먼저 WM-SP# 장치를 스캔하고 선택하세요",
                "Primero escanea y selecciona un dispositivo WM-SP#",
                "Scannez puis sélectionnez d'abord un appareil WM-SP#",
                "Bitte zuerst ein WM-SP#-Gerät scannen und auswählen",
            ),
            "APP 语言" to listOf("App Language", "アプリ言語", "앱 언어", "Idioma de la app", "Langue de l'app", "App-Sprache"),
            "界面文字会按照这里的语言显示，选择后立即保存。" to listOf(
                "Interface text follows the language selected here and is saved immediately.",
                "画面の文字はここで選んだ言語で表示され、選択後すぐ保存されます。",
                "화면 문구는 여기서 선택한 언어로 표시되며 즉시 저장됩니다.",
                "El texto de la interfaz usará el idioma elegido aquí y se guardará al instante.",
                "Le texte de l'interface suit la langue choisie ici et est enregistré immédiatement.",
                "Die Oberfläche verwendet die hier gewählte Sprache und speichert sie sofort.",
            ),
            "已连接" to listOf("Connected", "接続済み", "연결됨", "Conectado", "Connecté", "Verbunden"),
            "未连接" to listOf("Disconnected", "未接続", "연결 안 됨", "Desconectado", "Non connecté", "Nicht verbunden"),
            "已选中" to listOf("Selected", "選択済み", "선택됨", "Seleccionado", "Sélectionné", "Ausgewählt"),
            "选择" to listOf("Select", "選択", "선택", "Seleccionar", "Sélectionner", "Auswählen"),
            "7种" to listOf("7 languages", "7言語", "7개 언어", "7 idiomas", "7 langues", "7 Sprachen"),
            "正在读取设备数据 ，请稍候" to listOf(
                "Reading device data. Please wait.",
                "デバイスデータを読み取り中です。お待ちください。",
                "장치 데이터를 읽는 중입니다. 잠시만 기다려 주세요.",
                "Leyendo datos del dispositivo. Espera un momento.",
                "Lecture des données de l'appareil. Veuillez patienter.",
                "Gerätedaten werden gelesen. Bitte warten.",
            ),
            "数据读取完成" to listOf(
                "Data reading complete.",
                "データ読み取りが完了しました。",
                "데이터 읽기가 완료되었습니다.",
                "Lectura de datos completada.",
                "Lecture des données terminée.",
                "Datenlesen abgeschlossen.",
            ),
            "肤" to listOf("SK", "皮", "피부", "PIEL", "PEAU", "HAUT"),
            "长" to listOf("HT", "身", "키", "ALT", "TAIL", "GR"),
            "片" to listOf("IMG", "画", "영상", "IMG", "IMG", "BILD"),
            "报" to listOf("RPT", "報", "보고", "INF", "RAP", "BER"),
            "出" to listOf("EXP", "出", "내보", "EXP", "EXP", "EXP"),
            "删" to listOf("DEL", "削", "삭제", "BOR", "SUP", "LÖS"),
            "册" to listOf("MAN", "冊", "설명", "MAN", "MAN", "HAN"),
            "问" to listOf("FAQ", "問", "질문", "FAQ", "FAQ", "FAQ"),
            "协" to listOf("AGR", "契", "약관", "ACU", "ACC", "VER"),
            "私" to listOf("PRI", "私", "개인", "PRI", "PRI", "DAT"),
            "联" to listOf("CON", "連", "문의", "CON", "CON", "KON"),
            "孩" to listOf("KID", "子", "아이", "NIÑO", "ENF", "KIND"),
        )

        private val uiPhraseTranslations = linkedMapOf(
            "待处理" to listOf("Pending", "未処理", "대기 중", "Pendiente", "En attente", "Ausstehend"),
            "已处理" to listOf("Handled", "処理済み", "처리됨", "Procesado", "Traité", "Bearbeitet"),
            "删除全部数据" to listOf("Delete All Data", "すべてのデータを削除", "전체 데이터 삭제", "Eliminar todos los datos", "Supprimer toutes les données", "Alle Daten löschen"),
            "帮助中心" to listOf("Help Center", "ヘルプセンター", "도움말 센터", "Centro de ayuda", "Centre d'aide", "Hilfezentrum"),
            "用户手册" to listOf("User Manual", "ユーザーマニュアル", "사용자 설명서", "Manual de usuario", "Manuel utilisateur", "Benutzerhandbuch"),
            "常见问题" to listOf("FAQ", "よくある質問", "자주 묻는 질문", "Preguntas frecuentes", "Questions fréquentes", "Häufige Fragen"),
            "用户协议" to listOf("User Agreement", "ユーザー契約", "이용약관", "Acuerdo de usuario", "Accord utilisateur", "Nutzungsvereinbarung"),
            "隐私政策" to listOf("Privacy Policy", "プライバシーポリシー", "개인정보 처리방침", "Política de privacidad", "Politique de confidentialité", "Datenschutzrichtlinie"),
            "联系我们" to listOf("Contact Us", "お問い合わせ", "문의하기", "Contáctanos", "Nous contacter", "Kontakt"),
            "微信客服" to listOf("WeChat Support", "WeChatサポート", "WeChat 고객지원", "Soporte WeChat", "Support WeChat", "WeChat-Support"),
            "邮箱" to listOf("Email", "メール", "이메일", "Correo", "E-mail", "E-Mail"),
            "待配置" to listOf("To be configured", "未設定", "설정 예정", "Pendiente", "À configurer", "Noch zu konfigurieren"),
            "已配置" to listOf("Configured", "設定済み", "설정됨", "Configurado", "Configuré", "Konfiguriert"),
            "生成" to listOf("Generate", "生成", "생성", "Generar", "Générer", "Erzeugen"),
            "医嘱" to listOf("Prescribed ", "医師指示", "처방 ", "Prescrito ", "Prescription ", "Verordnet "),
            "结合" to listOf("Based on ", "基づく", "기반 ", "Basado en ", "Basé sur ", "Basierend auf "),
            "脊护妈妈助手" to listOf("Spinecare Mom", "Spinecare Mom", "Spinecare Mom", "Spinecare Mom", "Spinecare Mom", "Spinecare Mom"),
            "守护孩子的每一小时佩戴" to listOf("Protect every wearing hour", "装着の一時間一時間を見守ります", "아이의 모든 착용 시간을 지킵니다", "Cuida cada hora de uso", "Accompagne chaque heure de port", "Jede Tragestunde begleiten"),
            "手机号" to listOf("Phone", "電話番号", "휴대폰 번호", "Teléfono", "Téléphone", "Telefon"),
            "验证码" to listOf("Code", "認証コード", "인증 코드", "Código", "Code", "Code"),
            "获取验证码" to listOf("Get Code", "コード取得", "코드 받기", "Obtener código", "Obtenir le code", "Code anfordern"),
            "— 或 —" to listOf("— or —", "— または —", "— 또는 —", "— o —", "— ou —", "— oder —"),
            "暂时跳过" to listOf("Skip for now", "今はスキップ", "지금은 건너뛰기", "Omitir por ahora", "Ignorer pour l'instant", "Vorerst überspringen"),
            "继续建档" to listOf("Continue Profile", "プロフィール作成を続行", "프로필 계속 작성", "Continuar perfil", "Continuer le dossier", "Profil fortsetzen"),
            "建档向导" to listOf("Profile Wizard", "プロフィールウィザード", "프로필 마법사", "Asistente de perfil", "Assistant de dossier", "Profilassistent"),
            "基础信息" to listOf("Basic Info", "基本情報", "기본 정보", "Información básica", "Infos de base", "Basisdaten"),
            "病情信息" to listOf("Condition Info", "病状情報", "상태 정보", "Información clínica", "Infos cliniques", "Befunddaten"),
            "医嘱与支具" to listOf("Prescription and Brace", "医師指示と装具", "처방 및 보조기", "Prescripción y corsé", "Prescription et orthèse", "Verordnung und Orthese"),
            "进入首页" to listOf("Enter Home", "ホームへ", "홈으로", "Entrar al inicio", "Aller à l'accueil", "Zur Startseite"),
            "设备绑定" to listOf("Device Binding", "機器登録", "장치 등록", "Vincular dispositivo", "Association d'appareil", "Gerät koppeln"),
            "绑定" to listOf("Bind", "登録", "등록", "Vincular", "Associer", "Koppeln"),
            "近35天云端数据已注入" to listOf("35-day cloud data loaded", "35日分のクラウドデータ読込済み", "35일 클라우드 데이터 로드됨", "Datos cloud de 35 días cargados", "35 jours de données cloud chargés", "35 Tage Cloud-Daten geladen"),
            "结构化回答" to listOf("Structured Answer", "構造化回答", "구조화 답변", "Respuesta estructurada", "Réponse structurée", "Strukturierte Antwort"),
            "健康教育" to listOf("Health Education", "健康教育", "건강 교육", "Educación sanitaria", "Éducation santé", "Gesundheitsinformation"),
            "一句话总结" to listOf("One-line Summary", "一言要約", "한 줄 요약", "Resumen breve", "Résumé en une phrase", "Kurzfassung"),
            "结合朵朵数据的分析" to listOf("Analysis Based on Duoduo's Data", "朵朵さんのデータに基づく分析", "두오두오 데이터 기반 분석", "Análisis con datos de Duoduo", "Analyse avec les données de Duoduo", "Analyse mit Duoduos Daten"),
            "数据的分析" to listOf("Data Analysis", "データ分析", "데이터 분석", "Análisis de datos", "Analyse des données", "Datenanalyse"),
            "可执行建议" to listOf("Actionable Advice", "実行できる提案", "실행 가능한 조언", "Consejos prácticos", "Conseils pratiques", "Umsetzbare Hinweise"),
            "少戴2小时有影响吗？" to listOf("Does wearing 2 hours less matter?", "2時間少ないと影響しますか？", "2시간 덜 착용하면 영향이 있나요?", "¿Afecta usar 2 horas menos?", "2 heures de moins ont-elles un impact ?", "Sind 2 Stunden weniger relevant?"),
            "少戴会影响矫正效果，建议尽量补足医嘱时长" to listOf("Wearing less can affect correction; try to make up the prescribed time", "装着不足は矯正効果に影響するため、指示時間を補ってください", "착용 부족은 교정 효과에 영향을 줄 수 있어 처방 시간을 보완하세요", "Usar menos puede afectar la corrección; intenta completar el tiempo prescrito", "Un port insuffisant peut affecter la correction ; complétez le temps prescrit", "Weniger Tragen kann die Korrektur beeinflussen; verordnete Zeit möglichst ausgleichen"),
            "当前最直接的改进点是先补足近7天平均与医嘱之间约" to listOf("The most direct improvement is to close the gap of about ", "最も直接的な改善点は、直近7日平均と指示時間の差約", "가장 직접적인 개선점은 최근 7일 평균과 처방 간 약 ", "La mejora directa es cubrir una brecha de aprox. ", "L'amélioration directe est de combler environ ", "Direkteste Verbesserung ist, die Lücke von ca. "),
            "的缺口" to listOf(" gap", "の不足", "의 부족분", " de brecha", " de manque", " Lücke"),
            "包括" to listOf("include", "を含む", "포함", "incluyen", "incluent", "umfassen"),
            "建议咨询时同步说明" to listOf("mention this during consultation", "相談時に併せて説明してください", "상담 시 함께 설명하세요", "menciónalo en la consulta", "à signaler pendant la consultation", "bei Beratung erwähnen"),
            "下午14点设一次佩戴提醒" to listOf("Set a wearing reminder at 14:00", "14時に装着リマインダーを設定", "14시에 착용 알림 설정", "Pon un recordatorio a las 14:00", "Programmer un rappel à 14:00", "Trageerinnerung um 14:00 einstellen"),
            "放学后先穿戴再写作业" to listOf("Wear the brace before homework after school", "放課後は宿題前に装着", "하교 후 숙제 전에 착용", "Después de clase, ponerse el corsé antes de deberes", "Après l'école, mettre l'orthèse avant les devoirs", "Nach der Schule vor Hausaufgaben anlegen"),
            "睡前检查支具是否压迫皮肤" to listOf("Check for skin pressure before sleep", "就寝前に皮膚圧迫を確認", "자기 전 피부 압박 확인", "Revisar presión en la piel antes de dormir", "Vérifier la pression cutanée avant le coucher", "Vor dem Schlafen Hautdruck prüfen"),
            "拍照" to listOf("Take Photo", "撮影", "촬영", "Hacer foto", "Prendre photo", "Foto aufnehmen"),
            "查看" to listOf("View", "表示", "보기", "Ver", "Voir", "Ansehen"),
            "成就" to listOf("Achievement", "達成", "성취", "Logro", "Réussite", "Erfolg"),
            "青少年向成就视图" to listOf("Teen achievement view", "青少年向け達成ビュー", "청소년용 성취 보기", "Vista de logros para adolescentes", "Vue de réussite ado", "Erfolgsansicht für Jugendliche"),
            "今日佩戴进度、连续达标天数和阶段徽章会以青少年视角呈现。" to listOf("Today's wearing progress, compliance streak, and badges are shown for teens.", "今日の装着進捗、連続達成日数、バッジを青少年向けに表示します。", "오늘 착용 진행률, 연속 달성일, 배지를 청소년 시각으로 표시합니다.", "El progreso de hoy, la racha y las insignias se muestran para adolescentes.", "Le progrès du jour, la série et les badges sont présentés pour adolescents.", "Heutiger Fortschritt, Serien und Abzeichen werden jugendgerecht angezeigt."),
            "2条未读预警" to listOf("2 unread alerts", "未読アラート2件", "읽지 않은 알림 2개", "2 alertas sin leer", "2 alertes non lues", "2 ungelesene Warnungen"),
            "AI 周报月报与复诊材料" to listOf("AI weekly/monthly reports and visit materials", "AI週報・月報と再診資料", "AI 주간/월간 보고서 및 재진 자료", "Informes IA semanales/mensuales y revisión", "Rapports IA hebdo/mensuels et suivi", "KI-Wochen-/Monatsberichte und Kontrollmaterial"),
            "周报" to listOf("Weekly Report", "週報", "주간 보고서", "Informe semanal", "Rapport hebdomadaire", "Wochenbericht"),
            "月报" to listOf("Monthly Report", "月報", "월간 보고서", "Informe mensual", "Rapport mensuel", "Monatsbericht"),
            "30天报告" to listOf("30-day Report", "30日レポート", "30일 보고서", "Informe de 30 días", "Rapport 30 jours", "30-Tage-Bericht"),
            "30天" to listOf("30 days", "30日", "30일", "30 días", "30 jours", "30 Tage"),
            "1条" to listOf("1 item", "1件", "1개", "1 registro", "1 élément", "1 Eintrag"),
            "2条" to listOf("2 items", "2件", "2개", "2 registros", "2 éléments", "2 Einträge"),
            "3条" to listOf("3 items", "3件", "3개", "3 registros", "3 éléments", "3 Einträge"),
            "异常报告" to listOf("Abnormality Report", "異常レポート", "이상 보고서", "Informe de anomalías", "Rapport d'anomalie", "Auffälligkeitsbericht"),
            "缺口" to listOf("Gaps", "不足", "부족", "Déficits", "Manques", "Lücken"),
            "平均" to listOf("Avg", "平均", "평균", "Media", "Moy.", "Schnitt"),
            "近期佩戴有改善" to listOf("recent wearing has improved", "最近の装着は改善しています", "최근 착용이 개선되었습니다", "el uso reciente ha mejorado", "le port récent s'améliore", "das jüngste Tragen hat sich verbessert"),
            "皮肤问题" to listOf("Skin Problems", "皮膚問題", "피부 문제", "Problemas de piel", "Problèmes cutanés", "Hautprobleme"),
            "皮肤、生长与影像档案" to listOf("Skin, Growth and Imaging Archive", "皮膚・成長・画像アーカイブ", "피부, 성장 및 영상 기록", "Piel, crecimiento e imágenes", "Peau, croissance et imagerie", "Haut, Wachstum und Bildarchiv"),
            "不要求每天检查；仅在发现皮肤问题时拍照并填写记录，内容会进入复诊报告。" to listOf("Daily check-in is not required. Take a photo and record only when a skin problem is found; the content will be included in the visit report.", "毎日の確認は不要です。皮膚問題を見つけた時だけ撮影して記録し、再診レポートに反映されます。", "매일 확인할 필요는 없습니다. 피부 문제가 발견될 때만 사진과 기록을 남기며 재진 보고서에 포함됩니다.", "No requiere registro diario. Solo toma una foto y registra cuando encuentres un problema de piel; se integrará en el informe.", "Le contrôle quotidien n'est pas requis. Photographiez et notez seulement en cas de problème cutané; ce sera intégré au rapport.", "Tägliche Kontrolle ist nicht erforderlich. Nur bei Hautproblemen Foto und Eintrag erfassen; dies wird im Kontrollbericht berücksichtigt."),
            "近30天佩戴" to listOf("Last 30 Days", "直近30日", "최근 30일", "Últimos 30 días", "30 derniers jours", "Letzte 30 Tage"),
            "近7天佩戴" to listOf("Last 7 Days", "直近7日", "최근 7일", "Últimos 7 días", "7 derniers jours", "Letzte 7 Tage"),
            "智能解读" to listOf("Smart Insights", "スマート解釈", "지능형 해석", "Interpretación inteligente", "Analyse intelligente", "Intelligente Auswertung"),
            "历史趋势" to listOf("History Trend", "履歴トレンド", "기록 추세", "Tendencia histórica", "Tendance historique", "Historischer Trend"),
            "历史记录" to listOf("History", "履歴", "기록", "Historial", "Historique", "Verlauf"),
            "佩戴统计" to listOf("Wearing Statistics", "装着統計", "착용 통계", "Estadísticas de uso", "Statistiques de port", "Tragestatistik"),
            "目标完成" to listOf("Target Completion", "目標達成", "목표 달성", "Cumplimiento del objetivo", "Objectif atteint", "Zielerfüllung"),
            "最长连续达标" to listOf("Longest Streak", "最長連続達成", "최장 연속 달성", "Racha más larga", "Plus longue série", "Längste Serie"),
            "近30天有数据" to listOf("30-day Data", "30日データ", "30일 데이터", "Datos de 30 días", "Données 30 jours", "30-Tage-Daten"),
            "有数据" to listOf("Data Days", "データあり", "데이터 있음", "Días con datos", "Jours avec données", "Datentage"),
            "正在读取云端数据" to listOf("Reading cloud data", "クラウドデータ読み取り中", "클라우드 데이터 읽는 중", "Leyendo datos de la nube", "Lecture des données cloud", "Cloud-Daten werden gelesen"),
            "暂无云端数据" to listOf("No cloud data", "クラウドデータなし", "클라우드 데이터 없음", "Sin datos en la nube", "Aucune donnée cloud", "Keine Cloud-Daten"),
            "云端数据" to listOf("Cloud Data", "クラウドデータ", "클라우드 데이터", "Datos en la nube", "Données cloud", "Cloud-Daten"),
            "问题拍照" to listOf("Problem Photo", "問題写真", "문제 사진", "Foto del problema", "Photo du problème", "Problemfoto"),
            "身高趋势" to listOf("Height Trend", "身長トレンド", "키 추세", "Tendencia de altura", "Tendance de taille", "Größentrend"),
            "影像上传" to listOf("Image Upload", "画像アップロード", "영상 업로드", "Subir imagen", "Envoi d'image", "Bild hochladen"),
            "智能咨询" to listOf("AI Consult", "AI相談", "AI 상담", "Consulta IA", "Conseil IA", "KI-Beratung"),
            "复诊材料" to listOf("Visit Materials", "再診資料", "재진 자료", "Material de revisión", "Documents de suivi", "Kontrollmaterial"),
            "35天日均" to listOf("35-day Avg", "35日平均", "35일 평균", "Media 35 días", "Moyenne 35 jours", "35-Tage-Schnitt"),
            "总体达标率" to listOf("Overall Compliance", "総合達成率", "전체 달성률", "Cumplimiento total", "Observance globale", "Gesamterfüllung"),
            "最长连续" to listOf("Longest Streak", "最長連続", "최장 연속", "Racha más larga", "Plus longue série", "Längste Serie"),
            "达标率" to listOf("Compliance", "達成率", "달성률", "Cumplimiento", "Observance", "Erfüllung"),
            "达标标准" to listOf("Target standard", "達成基準", "달성 기준", "Estándar objetivo", "Seuil cible", "Zielstandard"),
            "达标" to listOf("Met", "達成", "달성", "Cumple", "Atteint", "Erfüllt"),
            "h/天" to listOf("h/day", "h/日", "h/일", "h/día", "h/jour", "h/Tag"),
            "条记录" to listOf(" records", "件の記録", "개 기록", " registros", " dossiers", " Einträge"),
            "张" to listOf(" photos", "枚", "장", " fotos", " photos", " Fotos"),
            "照片" to listOf("Photo", "写真", "사진", "Foto", "Photo", "Foto"),
            "未填写备注" to listOf("No note entered", "メモ未入力", "메모 없음", "Sin nota", "Aucune note", "Keine Notiz"),
            "未填写" to listOf("Not filled", "未入力", "미입력", "Sin completar", "Non renseigné", "Nicht ausgefüllt"),
            "已保存" to listOf("Saved", "保存済み", "저장됨", "Guardado", "Enregistré", "Gespeichert"),
            "保存后将在这里显示" to listOf("Saved records will appear here", "保存後ここに表示されます", "저장 후 여기에 표시됩니다", "Los registros guardados aparecerán aquí", "Les éléments enregistrés apparaîtront ici", "Gespeicherte Einträge erscheinen hier"),
            "录入后将在这里显示" to listOf("Entries will appear here", "入力後ここに表示されます", "입력 후 여기에 표시됩니다", "Las entradas aparecerán aquí", "Les saisies apparaîtront ici", "Einträge erscheinen hier"),
            "正在读取皮肤记录" to listOf("Reading skin records", "皮膚記録を読み取り中", "피부 기록 읽는 중", "Leyendo registros de piel", "Lecture des dossiers de peau", "Hautprotokolle werden gelesen"),
            "正在读取生长记录" to listOf("Reading growth records", "成長記録を読み取り中", "성장 기록 읽는 중", "Leyendo crecimiento", "Lecture des données de croissance", "Wachstumsdaten werden gelesen"),
            "正在读取影像档案" to listOf("Reading imaging archive", "画像記録を読み取り中", "영상 기록 읽는 중", "Leyendo archivo de imágenes", "Lecture du dossier d'imagerie", "Bildarchiv wird gelesen"),
            "皮肤问题记录" to listOf("Skin Problem Record", "皮膚問題記録", "피부 문제 기록", "Registro de problema de piel", "Dossier de problème cutané", "Hautproblem-Protokoll"),
            "发现发红、疼痛、破皮等问题时记录" to listOf("Record redness, pain, skin breakage, or similar issues", "発赤、痛み、皮膚損傷などを記録", "발적, 통증, 피부 손상 등을 기록", "Registra enrojecimiento, dolor o heridas", "Enregistrer rougeur, douleur ou plaie", "Rötung, Schmerz oder Hautschaden erfassen"),
            "问题部位" to listOf("Problem Area", "問題部位", "문제 부위", "Zona del problema", "Zone du problème", "Problembereich"),
            "问题类型" to listOf("Problem Type", "問題タイプ", "문제 유형", "Tipo de problema", "Type de problème", "Problemtyp"),
            "备注" to listOf("Note", "メモ", "메모", "Nota", "Note", "Notiz"),
            "照片编号/说明" to listOf("Photo ID/Description", "写真番号/説明", "사진 번호/설명", "ID/descr. de foto", "ID/description photo", "Foto-ID/Beschreibung"),
            "历史皮肤问题" to listOf("Skin Problem History", "皮膚問題履歴", "피부 문제 기록", "Historial de piel", "Historique cutané", "Hautproblem-Verlauf"),
            "记录时间" to listOf("Record Time", "記録時刻", "기록 시간", "Hora de registro", "Heure d'enregistrement", "Erfassungszeit"),
            "文字记录" to listOf("Text Note", "文字記録", "텍스트 기록", "Nota escrita", "Note texte", "Textnotiz"),
            "身高(cm)" to listOf("Height (cm)", "身長(cm)", "키(cm)", "Altura (cm)", "Taille (cm)", "Größe (cm)"),
            "上一次录入" to listOf("Previous entry", "前回入力", "이전 입력", "Registro anterior", "Saisie précédente", "Vorheriger Eintrag"),
            "暂无上一次身高录入记录" to listOf("No previous height entry", "前回の身長記録なし", "이전 키 기록 없음", "Sin altura previa", "Aucune taille précédente", "Keine vorherige Größe"),
            "历史生长记录" to listOf("Growth History", "成長履歴", "성장 기록", "Historial de crecimiento", "Historique de croissance", "Wachstumsverlauf"),
            "影像录入" to listOf("Imaging Entry", "画像入力", "영상 입력", "Entrada de imagen", "Saisie d'imagerie", "Bilderfassung"),
            "影像类型" to listOf("Imaging Type", "画像タイプ", "영상 유형", "Tipo de imagen", "Type d'imagerie", "Bildtyp"),
            "拍摄日期" to listOf("Shot Date", "撮影日", "촬영일", "Fecha de captura", "Date de prise", "Aufnahmedatum"),
            "文件地址/编号" to listOf("File URL/ID", "ファイルURL/番号", "파일 주소/번호", "URL/ID de archivo", "URL/ID du fichier", "Datei-URL/ID"),
            "从图库选择" to listOf("Choose from Gallery", "ギャラリーから選択", "갤러리에서 선택", "Elegir de galería", "Choisir dans la galerie", "Aus Galerie wählen"),
            "保存影像记录" to listOf("Save Imaging Record", "画像記録を保存", "영상 기록 저장", "Guardar imagen", "Enregistrer l'imagerie", "Bildaufzeichnung speichern"),
            "影像预览" to listOf("Image Preview", "画像プレビュー", "영상 미리보기", "Vista previa", "Aperçu d'image", "Bildvorschau"),
            "档案、设备与隐私" to listOf("Profile, Device and Privacy", "プロフィール、機器、プライバシー", "프로필, 장치 및 개인정보", "Perfil, dispositivo y privacidad", "Dossier, appareil et confidentialité", "Profil, Gerät und Datenschutz"),
            "消息中心" to listOf("Messages", "メッセージ", "메시지", "Mensajes", "Messages", "Nachrichten"),
            "孩子模式" to listOf("Child Mode", "子どもモード", "아이 모드", "Modo infantil", "Mode enfant", "Kindermodus"),
            "进入孩子模式" to listOf("Enter Child Mode", "子どもモードへ", "아이 모드로 이동", "Entrar en modo infantil", "Entrer en mode enfant", "Kindermodus öffnen"),
            "退出登录" to listOf("Log Out", "ログアウト", "로그아웃", "Cerrar sesión", "Déconnexion", "Abmelden"),
            "当前" to listOf("Current", "現在", "현재", "Actual", "Actuel", "Aktuell"),
            "已关联档案" to listOf("profile linked", "プロフィール連携済み", "프로필 연결됨", "perfil vinculado", "dossier lié", "Profil verknüpft"),
            "个性化" to listOf("Personalized", "個別化", "개인화", "Personalizado", "Personnalisé", "Personalisiert"),
            "少戴2h有影响吗" to listOf("Does 2h less matter?", "2時間少ないと影響しますか", "2시간 덜 착용하면 영향이 있나요?", "¿Afecta usar 2 h menos?", "2 h de moins ont-elles un impact ?", "Sind 2 h weniger relevant?"),
            "皮肤红了怎么办" to listOf("What if skin is red?", "皮膚が赤い時は？", "피부가 빨개지면?", "¿Qué hago si la piel se enrojece?", "Que faire si la peau rougit ?", "Was tun bei Rötung?"),
            "能上体育课吗" to listOf("Can attend PE?", "体育に参加できますか", "체육 수업 가능?", "¿Puede hacer educación física?", "Peut-il faire sport ?", "Darf Sport gemacht werden?"),
            "被同学笑话怎么办" to listOf("What if classmates tease?", "同級生にからかわれたら？", "친구들이 놀리면?", "¿Si se burlan en clase?", "Que faire en cas de moqueries ?", "Was bei Hänseleien?"),
            "依从性报告" to listOf("Compliance Report", "遵守状況レポート", "순응도 보고서", "Informe de cumplimiento", "Rapport d'observance", "Compliance-Bericht"),
            "基于云端真实数据自动生成" to listOf("Generated from real cloud data", "クラウド実データから自動生成", "실제 클라우드 데이터 기반 자동 생성", "Generado con datos reales de la nube", "Généré à partir des données cloud réelles", "Aus echten Cloud-Daten erzeugt"),
            "本周摘要" to listOf("Weekly Summary", "今週の要約", "이번 주 요약", "Resumen semanal", "Résumé hebdomadaire", "Wochenübersicht"),
            "复诊报告预览" to listOf("Visit Report Preview", "再診レポートプレビュー", "재진 보고서 미리보기", "Vista previa del informe", "Aperçu du rapport de suivi", "Kontrollbericht-Vorschau"),
            "周期" to listOf("Period", "期間", "기간", "Periodo", "Période", "Zeitraum"),
            "归档逻辑" to listOf("Archive Logic", "アーカイブロジック", "보관 로직", "Lógica de archivo", "Logique d'archive", "Archivlogik"),
            "归档列表" to listOf("Archive List", "アーカイブ一覧", "보관 목록", "Lista de archivo", "Liste des archives", "Archivliste"),
            "报告类型" to listOf("Report Type", "レポート種別", "보고서 유형", "Tipo de informe", "Type de rapport", "Berichtstyp"),
            "统计周期" to listOf("Statistics Period", "統計期間", "통계 기간", "Periodo estadístico", "Période statistique", "Statistikzeitraum"),
            "生成时间" to listOf("Generated Time", "生成時刻", "생성 시간", "Hora de generación", "Heure de génération", "Erstellzeit"),
            "报告摘要" to listOf("Report Summary", "レポート要約", "보고서 요약", "Resumen del informe", "Résumé du rapport", "Berichtszusammenfassung"),
            "云端已读取" to listOf("Cloud loaded ", "クラウド読み取り済み ", "클라우드에서 읽음 ", "Nube cargada ", "Cloud chargé ", "Cloud geladen "),
            "天佩戴记录" to listOf(" days of wearing records", "日の装着記録", "일 착용 기록", " días de uso", " jours de port", " Tage Tragedaten"),
            "近30天平均" to listOf("30-day average ", "30日平均 ", "30일 평균 ", "Media 30 días ", "Moyenne 30 jours ", "30-Tage-Schnitt "),
            "近30天达标率良好" to listOf("30-day compliance is good", "30日達成率は良好", "30일 달성률이 양호함", "El cumplimiento de 30 días es bueno", "L'observance sur 30 jours est bonne", "30-Tage-Erfüllung ist gut"),
            "近30天达标率一般" to listOf("30-day compliance is fair", "30日達成率は普通", "30일 달성률이 보통임", "El cumplimiento de 30 días es medio", "L'observance sur 30 jours est moyenne", "30-Tage-Erfüllung ist mittel"),
            "近30天达标率偏低" to listOf("30-day compliance is low", "30日達成率は低め", "30일 달성률이 낮음", "El cumplimiento de 30 días es bajo", "L'observance sur 30 jours est faible", "30-Tage-Erfüllung ist niedrig"),
            "建议继续保持当前佩戴节奏" to listOf("please keep the current wearing routine", "現在の装着リズムを維持してください", "현재 착용 리듬을 유지하세요", "mantén el ritmo actual de uso", "gardez le rythme actuel de port", "bitte aktuellen Tragerhythmus beibehalten"),
            "建议优先补足固定缺口时段" to listOf("prioritize filling fixed gap periods", "固定の不足時間を優先して補ってください", "고정된 부족 시간대를 먼저 보완하세요", "prioriza cubrir las franjas con déficit fijo", "priorisez les créneaux de manque fixes", "feste Lückenzeiten zuerst ausgleichen"),
            "建议尽快与" to listOf("please contact ", "早めに", "가능하면 빨리 ", "contacta pronto con ", "contactez rapidement ", "bitte zeitnah "),
            "沟通佩戴困难" to listOf("about wearing difficulties", "に装着の困難を相談してください", "에게 착용 어려움을 상담하세요", "sobre las dificultades de uso", "au sujet des difficultés de port", "zu Trageschwierigkeiten kontaktieren"),
            "近7天平均" to listOf("7-day average ", "7日平均 ", "7일 평균 ", "Media 7 días ", "Moyenne 7 jours ", "7-Tage-Schnitt "),
            "较前7天增加" to listOf("increased vs previous 7 days by ", "前7日より増加 ", "이전 7일보다 증가 ", "aumentó frente a los 7 días previos ", "en hausse vs 7 jours précédents ", "mehr als vorige 7 Tage um "),
            "较前7天下降" to listOf("decreased vs previous 7 days by ", "前7日より低下 ", "이전 7일보다 감소 ", "bajó frente a los 7 días previos ", "en baisse vs 7 jours précédents ", "weniger als vorige 7 Tage um "),
            "近期佩戴有所改善" to listOf("recent wearing has improved", "最近の装着は改善しています", "최근 착용이 개선되었습니다", "el uso reciente ha mejorado", "le port récent s'améliore", "das jüngste Tragen hat sich verbessert"),
            "需要关注近期佩戴下降" to listOf("watch the recent wearing decline", "最近の装着低下に注意してください", "최근 착용 감소를 확인하세요", "vigila la disminución reciente", "surveillez la baisse récente du port", "den jüngsten Rückgang beachten"),
            "较前7天变化小于1h" to listOf("change vs previous 7 days is under 1h", "前7日比の変化は1時間未満", "이전 7일 대비 변화가 1시간 미만", "cambio menor de 1 h frente a 7 días previos", "variation inférieure à 1 h vs 7 jours précédents", "Änderung unter 1 h gegenüber Vorwoche"),
            "近期较稳定" to listOf("recently stable", "最近は安定", "최근 안정적", "recientemente estable", "récemment stable", "zuletzt stabil"),
            "出现连续" to listOf("There are ", "連続", "연속 ", "Hay ", "Il y a ", "Es gibt "),
            "天严重不足" to listOf(" days of severe shortage", "日の深刻な不足", "일 심각한 부족", " días de déficit grave", " jours de déficit sévère", " Tage starke Untererfüllung"),
            "低于医嘱" to listOf("below prescription", "医師指示未満", "처방 미만", "por debajo de la prescripción", "sous la prescription", "unter Verordnung"),
            "阈值" to listOf("threshold", "しきい値", "임계값", "umbral", "seuil", "Schwelle"),
            "建议纳入复诊沟通" to listOf("include this in the follow-up discussion", "再診時の相談に含めてください", "재진 상담에 포함하세요", "inclúyelo en la revisión", "à inclure dans le suivi", "in der Kontrolle besprechen"),
            "小时明细显示主要缺口集中在" to listOf("Hourly details show main gaps at ", "時間別明細では主な不足は", "시간별 상세에서 주요 부족 시간은 ", "El detalle horario muestra déficits en ", "Le détail horaire montre les manques à ", "Stundendetails zeigen Hauptlücken um "),
            "本周主要缺口集中在" to listOf("This week's main gaps are at ", "今週の主な不足は", "이번 주 주요 부족 시간은 ", "Los principales déficits de la semana están en ", "Les principaux manques de la semaine sont à ", "Diese Woche liegen die Hauptlücken um "),
            "近期皮肤问题记录" to listOf("Recent skin problem records", "最近の皮膚問題記録", "최근 피부 문제 기록", "Registros recientes de piel", "Problèmes cutanés récents", "Aktuelle Hautprobleme"),
            "近期皮肤记录" to listOf("Recent skin records", "最近の皮膚記録", "최근 피부 기록", "Registros recientes de piel", "Dossiers cutanés récents", "Aktuelle Hautprotokolle"),
            "已纳入复诊报告素材" to listOf("included in visit report materials", "再診レポート資料に含まれます", "재진 보고서 자료에 포함됨", "incluido en el informe de revisión", "intégré au rapport de suivi", "im Kontrollbericht berücksichtigt"),
            "已纳入复诊关注" to listOf("included in follow-up focus", "再診時の確認事項に含まれます", "재진 관심 항목에 포함됨", "incluido en el seguimiento", "intégré aux points de suivi", "in Kontrollfokus aufgenommen"),
            "近1个月身高增加" to listOf("Height increased in the last month by ", "直近1か月の身長増加 ", "최근 1개월 키 증가 ", "La altura aumentó en el último mes ", "La taille a augmenté sur 1 mois de ", "Größenzunahme im letzten Monat "),
            "超过1.3cm" to listOf("over 1.3 cm", "1.3cm超", "1.3cm 초과", "más de 1,3 cm", "plus de 1,3 cm", "über 1,3 cm"),
            "复诊时建议评估支具适配" to listOf("evaluate brace fit at follow-up", "再診時に装具適合を評価してください", "재진 시 보조기 적합성을 평가하세요", "evalúa el ajuste del corsé en revisión", "évaluer l'adaptation de l'orthèse au suivi", "Orthesenpassform bei Kontrolle prüfen"),
            "最近影像记录为" to listOf("Latest imaging record is ", "最新画像記録は", "최근 영상 기록은 ", "El último registro de imagen es ", "Le dernier dossier d'imagerie est ", "Letzte Bildaufzeichnung ist "),
            "可作为复诊材料补充" to listOf("can supplement visit materials", "再診資料の補足になります", "재진 자료로 보완 가능", "puede complementar el material de revisión", "peut compléter le dossier de suivi", "kann Kontrollmaterial ergänzen"),
            "男" to listOf("Male", "男", "남", "Masculino", "Masculin", "Männlich"),
            "女" to listOf("Female", "女", "여", "Femenino", "Féminin", "Weiblich"),
            "胸腰弯" to listOf("Thoracolumbar curve", "胸腰椎カーブ", "흉요추 만곡", "Curva toracolumbar", "Courbure thoraco-lombaire", "Thorakolumbale Krümmung"),
            "胸弯" to listOf("Thoracic curve", "胸椎カーブ", "흉추 만곡", "Curva torácica", "Courbure thoracique", "Thorakale Krümmung"),
            "腰弯" to listOf("Lumbar curve", "腰椎カーブ", "요추 만곡", "Curva lumbar", "Courbure lombaire", "Lumbale Krümmung"),
            "硬支具" to listOf("Rigid brace", "硬性装具", "경성 보조기", "Corsé rígido", "Orthèse rigide", "Starre Orthese"),
            "软支具" to listOf("Soft brace", "軟性装具", "연성 보조기", "Corsé blando", "Orthèse souple", "Weiche Orthese"),
            "左腰部" to listOf("Left waist", "左腰部", "왼쪽 허리", "Cintura izquierda", "Taille gauche", "Linke Taille"),
            "右腰部" to listOf("Right waist", "右腰部", "오른쪽 허리", "Cintura derecha", "Taille droite", "Rechte Taille"),
            "背部" to listOf("Back", "背部", "등", "Espalda", "Dos", "Rücken"),
            "胸腹部" to listOf("Chest/abdomen", "胸腹部", "흉복부", "Pecho/abdomen", "Thorax/abdomen", "Brust/Bauch"),
            "肩部" to listOf("Shoulder", "肩", "어깨", "Hombro", "Épaule", "Schulter"),
            "骨盆/髋部" to listOf("Pelvis/hip", "骨盤/股関節", "골반/고관절", "Pelvis/cadera", "Bassin/hanche", "Becken/Hüfte"),
            "其他" to listOf("Other", "その他", "기타", "Otro", "Autre", "Andere"),
            "发红" to listOf("Redness", "発赤", "발적", "Enrojecimiento", "Rougeur", "Rötung"),
            "瘙痒" to listOf("Itching", "かゆみ", "가려움", "Picor", "Démangeaison", "Juckreiz"),
            "疼痛" to listOf("Pain", "痛み", "통증", "Dolor", "Douleur", "Schmerz"),
            "破皮" to listOf("Skin break", "皮膚損傷", "피부 손상", "Herida", "Plaie", "Hautverletzung"),
            "水泡" to listOf("Blister", "水疱", "물집", "Ampolla", "Ampoule", "Blase"),
            "无异常" to listOf("No abnormality", "異常なし", "이상 없음", "Sin anomalías", "Aucune anomalie", "Keine Auffälligkeit"),
            "X光" to listOf("X-ray", "X線", "X-ray", "Radiografía", "Radiographie", "Röntgen"),
            "站立体态照" to listOf("Standing posture photo", "立位姿勢写真", "서있는 자세 사진", "Foto de postura de pie", "Photo de posture debout", "Standhaltungsfoto"),
            "Adams前屈照" to listOf("Adams forward bend photo", "Adams前屈写真", "Adams 전굴 사진", "Foto de flexión Adams", "Photo de flexion Adams", "Adams-Vorbeugetest-Foto"),
            "快速开始" to listOf("Quick Start", "クイックスタート", "빠른 시작", "Inicio rápido", "Démarrage rapide", "Schnellstart"),
            "蓝牙设备绑定" to listOf("Bluetooth Device Binding", "Bluetoothデバイスのバインド", "블루투스 기기 바인딩", "Vinculación Bluetooth", "Association Bluetooth", "Bluetooth-Gerät koppeln"),
            "读取佩戴数据" to listOf("Read Wearing Data", "装着データの読み取り", "착용 데이터 읽기", "Leer datos de uso", "Lire les données de port", "Tragedaten lesen"),
            "首页数据解读" to listOf("Home Data Insights", "ホームデータ解説", "홈 데이터 해석", "Interpretación de inicio", "Analyse de l'accueil", "Startseitenanalyse"),
            "记录功能" to listOf("Record Features", "記録機能", "기록 기능", "Funciones de registro", "Fonctions de suivi", "Aufzeichnungsfunktionen"),
            "报告与归档" to listOf("Reports and Archive", "レポートとアーカイブ", "보고서 및 보관", "Informes y archivo", "Rapports et archives", "Berichte und Archiv"),
            "导出与删除" to listOf("Export and Delete", "エクスポートと削除", "내보내기 및 삭제", "Exportar y eliminar", "Exporter et supprimer", "Exportieren und löschen"),
            "语言与设置" to listOf("Language and Settings", "言語と設定", "언어 및 설정", "Idioma y ajustes", "Langue et paramètres", "Sprache und Einstellungen"),
            "功能目的" to listOf("Purpose", "目的", "목적", "Objetivo", "Objectif", "Zweck"),
            "备份内容" to listOf("Backup Contents", "バックアップ内容", "백업 내용", "Contenido de copia", "Contenu de sauvegarde", "Sicherungsinhalt"),
            "生成备份文件" to listOf("Create Backup File", "バックアップファイルを生成", "백업 파일 생성", "Crear archivo de copia", "Créer un fichier de sauvegarde", "Sicherungsdatei erstellen"),
            "删除范围" to listOf("Deletion Scope", "削除範囲", "삭제 범위", "Alcance de eliminación", "Périmètre de suppression", "Löschumfang"),
            "备份检查" to listOf("Backup Check", "バックアップ確認", "백업 확인", "Comprobación de copia", "Vérification de sauvegarde", "Sicherungsprüfung"),
            "删除申请" to listOf("Deletion Request", "削除申請", "삭제 요청", "Solicitud de eliminación", "Demande de suppression", "Löschantrag"),
            "长按申请删除" to listOf("Long Press to Request", "長押しして申請", "길게 눌러 요청", "Mantén pulsado para solicitar", "Appui long pour demander", "Gedrückt halten zum Beantragen"),
            "长按执行删除" to listOf("Long Press to Delete", "長押しして削除", "길게 눌러 삭제", "Mantén pulsado para eliminar", "Appui long pour supprimer", "Gedrückt halten zum Löschen"),
            "导出数据" to listOf("Export Data", "データをエクスポート", "데이터 내보내기", "Exportar datos", "Exporter les données", "Daten exportieren"),
            "帮助中心文档" to listOf("Help Documents", "ヘルプ文書", "도움말 문서", "Documentos de ayuda", "Documents d'aide", "Hilfedokumente"),
            "隐私与同意" to listOf("Privacy and Consent", "プライバシーと同意", "개인정보 및 동의", "Privacidad y consentimiento", "Confidentialité et consentement", "Datenschutz und Einwilligung"),
            "未成年人健康数据保护" to listOf("Minor Health Data Protection", "未成年者健康データ保護", "미성년자 건강 데이터 보호", "Protección de datos de menores", "Protection des données de santé des mineurs", "Schutz von Gesundheitsdaten Minderjähriger"),
            "导出佩戴、记录与报告数据" to listOf("Export wearing, record and report data", "装着・記録・レポートデータをエクスポート", "착용, 기록 및 보고서 데이터 내보내기", "Exportar datos de uso, registros e informes", "Exporter les données de port, dossiers et rapports", "Trage-, Aufzeichnungs- und Berichtsdaten exportieren"),
            "申请删除本账号相关健康数据" to listOf("Request deletion of this account's health data", "このアカウントの健康データ削除を申請", "이 계정의 건강 데이터 삭제 요청", "Solicitar eliminación de datos de salud", "Demander la suppression des données de santé", "Löschung der Gesundheitsdaten beantragen"),
            "查看使用帮助、协议与隐私政策" to listOf("View help, agreement and privacy policy", "ヘルプ、契約、プライバシーを表示", "도움말, 약관 및 개인정보 보기", "Ver ayuda, acuerdo y privacidad", "Voir l'aide, l'accord et la confidentialité", "Hilfe, Vereinbarung und Datenschutz anzeigen"),
            "首页" to listOf("Home", "ホーム", "홈", "Inicio", "Accueil", "Start"),
            "报告" to listOf("Reports", "レポート", "보고서", "Informes", "Rapports", "Berichte"),
            "咨询" to listOf("Consult", "相談", "상담", "Consulta", "Conseil", "Beratung"),
            "记录" to listOf("Records", "記録", "기록", "Registros", "Dossiers", "Einträge"),
            "我的" to listOf("Me", "マイページ", "내 정보", "Yo", "Moi", "Ich"),
            "设置" to listOf("Settings", "設定", "설정", "Ajustes", "Paramètres", "Einstellungen"),
            "返回" to listOf("Back", "戻る", "뒤로", "Volver", "Retour", "Zurück"),
            "编辑" to listOf("Edit", "編集", "편집", "Editar", "Modifier", "Bearbeiten"),
            "保存" to listOf("Save", "保存", "저장", "Guardar", "Enregistrer", "Speichern"),
            "取消" to listOf("Cancel", "キャンセル", "취소", "Cancelar", "Annuler", "Abbrechen"),
            "下一步" to listOf("Next", "次へ", "다음", "Siguiente", "Suivant", "Weiter"),
            "上一步" to listOf("Previous", "前へ", "이전", "Anterior", "Précédent", "Zurück"),
            "跳过" to listOf("Skip", "スキップ", "건너뛰기", "Omitir", "Ignorer", "Überspringen"),
            "登录 / 注册" to listOf("Log in / Register", "ログイン / 登録", "로그인 / 가입", "Iniciar sesión / Registrarse", "Connexion / Inscription", "Anmelden / Registrieren"),
            "微信一键登录" to listOf("WeChat Login", "WeChatログイン", "WeChat 로그인", "Inicio con WeChat", "Connexion WeChat", "WeChat-Anmeldung"),
            "昵称" to listOf("Nickname", "ニックネーム", "닉네임", "Apodo", "Surnom", "Spitzname"),
            "性别" to listOf("Gender", "性別", "성별", "Sexo", "Sexe", "Geschlecht"),
            "出生日期" to listOf("Birth Date", "生年月日", "생년월일", "Fecha de nacimiento", "Date de naissance", "Geburtsdatum"),
            "弯曲部位" to listOf("Curve Location", "カーブ部位", "만곡 부위", "Zona de curva", "Zone de courbure", "Krümmungsbereich"),
            "医嘱佩戴时间" to listOf("Prescribed Hours", "医師指示の装着時間", "처방 착용 시간", "Horas prescritas", "Heures prescrites", "Verordnete Tragezeit"),
            "支具类型" to listOf("Brace Type", "装具タイプ", "보조기 유형", "Tipo de corsé", "Type d'orthèse", "Orthesentyp"),
            "初诊日期" to listOf("First Visit Date", "初診日", "초진일", "Fecha de primera visita", "Date de première consultation", "Datum der Erstuntersuchung"),
            "蓝牙连接" to listOf("Bluetooth Connection", "Bluetooth接続", "블루투스 연결", "Conexión Bluetooth", "Connexion Bluetooth", "Bluetooth-Verbindung"),
            "语言选择" to listOf("Language", "言語", "언어", "Idioma", "Langue", "Sprache"),
            "扫描" to listOf("Scan", "スキャン", "스캔", "Escanear", "Scanner", "Scannen"),
            "连接" to listOf("Connect", "接続", "연결", "Conectar", "Connecter", "Verbinden"),
            "断开" to listOf("Disconnect", "切断", "연결 해제", "Desconectar", "Déconnecter", "Trennen"),
            "电量" to listOf("Battery", "電池", "배터리", "Batería", "Batterie", "Batterie"),
            "数据验证" to listOf("Data Validation", "データ検証", "데이터 검증", "Validación de datos", "Validation des données", "Datenprüfung"),
            "皮肤记录" to listOf("Skin Record", "皮膚記録", "피부 기록", "Registro de piel", "Suivi de la peau", "Hautprotokoll"),
            "生长记录" to listOf("Growth Record", "成長記録", "성장 기록", "Registro de crecimiento", "Suivi de croissance", "Wachstumsprotokoll"),
            "影像档案" to listOf("Imaging Archive", "画像アーカイブ", "영상 기록", "Archivo de imágenes", "Dossier d'imagerie", "Bildarchiv"),
            "复诊报告" to listOf("Visit Report", "再診レポート", "재진 보고서", "Informe de revisión", "Rapport de suivi", "Kontrollbericht"),
            "归档" to listOf("Archive", "アーカイブ", "보관", "Archivo", "Archive", "Archiv"),
            "用户" to listOf("User", "ユーザー", "사용자", "Usuario", "Utilisateur", "Benutzer"),
            "数据" to listOf("Data", "データ", "데이터", "Datos", "Données", "Daten"),
            "佩戴" to listOf("Wearing", "装着", "착용", "Uso", "Port", "Tragen"),
            "皮肤" to listOf("Skin", "皮膚", "피부", "Piel", "Peau", "Haut"),
            "生长" to listOf("Growth", "成長", "성장", "Crecimiento", "Croissance", "Wachstum"),
            "影像" to listOf("Imaging", "画像", "영상", "Imágenes", "Imagerie", "Bildgebung"),
            "报告" to listOf("Report", "レポート", "보고서", "Informe", "Rapport", "Bericht"),
            "预警" to listOf("Alert", "アラート", "알림", "Alerta", "Alerte", "Warnung"),
            "备份" to listOf("Backup", "バックアップ", "백업", "Copia", "Sauvegarde", "Sicherung"),
            "云端" to listOf("Cloud", "クラウド", "클라우드", "Nube", "Cloud", "Cloud"),
            "保存到云端" to listOf("Saved to cloud", "クラウドに保存", "클라우드에 저장", "Guardado en la nube", "Enregistré dans le cloud", "In der Cloud gespeichert"),
            "正在读取" to listOf("Reading", "読み取り中", "읽는 중", "Leyendo", "Lecture", "Wird gelesen"),
            "读取完成" to listOf("Read complete", "読み取り完了", "읽기 완료", "Lectura completada", "Lecture terminée", "Lesen abgeschlossen"),
            "正在保存" to listOf("Saving", "保存中", "저장 중", "Guardando", "Enregistrement", "Wird gespeichert"),
            "保存成功" to listOf("Saved", "保存完了", "저장됨", "Guardado", "Enregistré", "Gespeichert"),
            "失败" to listOf("Failed", "失敗", "실패", "Error", "Échec", "Fehlgeschlagen"),
            "暂无" to listOf("No", "なし", "없음", "Sin", "Aucun", "Keine"),
            "已选择" to listOf("Selected", "選択済み", "선택됨", "Seleccionado", "Sélectionné", "Ausgewählt"),
            "正在打开" to listOf("Opening", "開いています", "여는 중", "Abriendo", "Ouverture", "Wird geöffnet"),
            "展开" to listOf("Expand", "展開", "펼치기", "Expandir", "Déplier", "Erweitern"),
            "收起" to listOf("Collapse", "折りたたむ", "접기", "Contraer", "Replier", "Einklappen"),
            "详情" to listOf("Details", "詳細", "상세", "Detalles", "Détails", "Details"),
            "正常" to listOf("Normal", "正常", "정상", "Normal", "Normal", "Normal"),
            "需关注" to listOf("Needs Attention", "要注意", "주의 필요", "Requiere atención", "À surveiller", "Beachten"),
            "红色" to listOf("Red", "赤", "빨간색", "Rojo", "Rouge", "Rot"),
            "良好" to listOf("Good", "良好", "양호", "Bueno", "Bon", "Gut"),
            "偏低" to listOf("Low", "低い", "낮음", "Bajo", "Faible", "Niedrig"),
            "请先" to listOf("Please first", "先に", "먼저", "Primero", "Veuillez d'abord", "Bitte zuerst"),
            "医生" to listOf("Doctor", "医師", "의사", "Médico", "Médecin", "Arzt"),
            "支具师" to listOf("Brace specialist", "装具士", "보조기 전문가", "Técnico ortopédico", "Orthoprothésiste", "Orthopädietechniker"),
            "不能替代" to listOf("Cannot replace", "代替できません", "대체할 수 없습니다", "No sustituye", "Ne remplace pas", "Ersetzt nicht"),
        )

        private fun adjustAlphaStatic(color: Int, factor: Float): Int {
            val alpha = (Color.alpha(color) * factor).roundToInt()
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
