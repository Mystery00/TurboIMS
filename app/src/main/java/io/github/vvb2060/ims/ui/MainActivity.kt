package io.github.vvb2060.ims.ui

import android.content.Intent
import android.widget.Toast
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SettingsBackupRestore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.vvb2060.ims.R
import io.github.vvb2060.ims.model.Feature
import io.github.vvb2060.ims.model.FeatureValue
import io.github.vvb2060.ims.model.FeatureValueType
import io.github.vvb2060.ims.model.ImsCapabilityStatus
import io.github.vvb2060.ims.model.ShizukuStatus
import io.github.vvb2060.ims.model.SimSelection
import io.github.vvb2060.ims.model.SystemInfo
import io.github.vvb2060.ims.viewmodel.MainViewModel
import kotlinx.coroutines.launch

class MainActivity : BaseActivity() {
    private val viewModel: MainViewModel by viewModels()

    @Composable
    override fun content() {
        val context = LocalContext.current

        val scrollBehavior =
            TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

        val systemInfo by viewModel.systemInfo.collectAsStateWithLifecycle()
        val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()
        val allSimList by viewModel.allSimList.collectAsStateWithLifecycle()

        var selectedSim by remember { mutableStateOf<SimSelection?>(null) }
        var showShizukuUpdateDialog by remember { mutableStateOf(false) }
        val featureSwitches = remember { mutableStateMapOf<Feature, FeatureValue>() }

        LaunchedEffect(shizukuStatus) {
            if (shizukuStatus == ShizukuStatus.NEED_UPDATE) {
                showShizukuUpdateDialog = true
            }
        }
        LaunchedEffect(allSimList) {
            if (selectedSim == null) {
                selectedSim = allSimList.firstOrNull { it.subId != -1 } ?: allSimList.firstOrNull()
            }
        }
        LaunchedEffect(selectedSim) {
            if (selectedSim != null) {
                featureSwitches.clear()
                val savedConfig = viewModel.loadConfiguration(selectedSim!!.subId)
                if (savedConfig != null) {
                    featureSwitches.putAll(savedConfig)
                } else {
                    featureSwitches.putAll(viewModel.loadDefaultPreferences())
                }
            }
        }

        var showSystemConfigDialog by remember { mutableStateOf(false) }
        var systemConfigData by remember { mutableStateOf<ImsCapabilityStatus?>(null) }
        var isRefreshingSystemConfig by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()

        if (showSystemConfigDialog && systemConfigData != null) {
            SystemConfigDialog(
                onDismissRequest = { showSystemConfigDialog = false },
                status = systemConfigData!!,
                simName = selectedSim?.showTitle ?: "",
                isRefreshing = isRefreshingSystemConfig,
                onRefresh = {
                    scope.launch {
                        if (selectedSim != null) {
                            isRefreshingSystemConfig = true
                            val data = viewModel.loadRealSystemConfig(selectedSim!!.subId)
                            if (data != null) {
                                systemConfigData = data
                            } else {
                                Toast.makeText(
                                    context,
                                    R.string.load_system_config_error,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            isRefreshingSystemConfig = false
                        }
                    }
                }
            )
        }

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0.dp),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Text(
                                stringResource(id = R.string.app_name),
                                fontSize = 28.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(id = R.string.for_pixel),
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                )
            }) { innerPadding ->
            Column(
                modifier = Modifier
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                SystemInfoCard(
                    systemInfo,
                    shizukuStatus,
                    onRefresh = {
                        viewModel.updateShizukuStatus()
                        viewModel.loadSimList()
                    },
                    onRequestShizukuPermission = {
                        viewModel.requestShizukuPermission(0)
                    },
                    onLogcatClick = {
                        startActivity(
                            Intent(
                                this@MainActivity,
                                LogcatActivity::class.java
                            )
                        )
                    },
                    onViewSystemConfigClick = {
                        if (selectedSim != null) {
                            scope.launch {
                                val data = viewModel.loadRealSystemConfig(selectedSim!!.subId)
                                if (data != null) {
                                    systemConfigData = data
                                    showSystemConfigDialog = true
                                } else {
                                    Toast.makeText(
                                        context,
                                        R.string.load_system_config_error,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    },
                    onResetIms = {
                        selectedSim?.let { viewModel.onResetIms(it) }
                    },
                    selectedSim = selectedSim
                )
                SimCardSelectionCard(selectedSim, allSimList, onSelectSim = {
                    selectedSim = it
                }, onRefreshSimList = {
                    viewModel.loadSimList()
                    Toast.makeText(context, R.string.sim_list_refresh, Toast.LENGTH_SHORT).show()
                })
                FeaturesCard(
                    isSelectAllSim = selectedSim?.subId == -1,
                    featureSwitches,
                    onFeatureSwitchChange = { feature, value ->
                        featureSwitches[feature] = value
                    },
                    loadFeatureHistory = {
                        featureSwitches.clear()
                        val savedConfig = selectedSim?.let { viewModel.loadConfiguration(it.subId) }
                        if (savedConfig != null) {
                            featureSwitches.putAll(savedConfig)
                            Toast.makeText(
                                context,
                                R.string.load_config_history_success,
                                Toast.LENGTH_SHORT
                            ).show()
                        } else {
                            featureSwitches.putAll(viewModel.loadDefaultPreferences())
                            Toast.makeText(
                                context,
                                R.string.load_config_default_success,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    },
                    resetFeatures = {
                        featureSwitches.clear()
                        featureSwitches.putAll(viewModel.loadDefaultPreferences())
                        Toast.makeText(
                            context,
                            R.string.load_config_default_success,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
                Buttons(
                    isApplyButtonEnabled = selectedSim != null,
                    onApplyConfiguration = {
                        if (shizukuStatus != ShizukuStatus.READY) {
                            Toast.makeText(
                                context,
                                R.string.shizuku_not_running_msg,
                                Toast.LENGTH_LONG
                            ).show()
                            return@Buttons
                        }
                        viewModel.onApplyConfiguration(selectedSim!!, featureSwitches)
                    },
                    onResetConfiguration = {
                        if (shizukuStatus != ShizukuStatus.READY) {
                            Toast.makeText(
                                context,
                                R.string.shizuku_not_running_msg,
                                Toast.LENGTH_LONG
                            ).show()
                            return@Buttons
                        }
                        viewModel.onResetConfiguration(selectedSim!!)
                    }
                )
                Tips()
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))

                if (showShizukuUpdateDialog) {
                    ShizukuUpdateDialog {
                        showShizukuUpdateDialog = false
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.updateShizukuStatus()
    }
}

/**
 *系统信息卡片
 * 显示软件版本、Android 版本、Shizuku 状态等。
 */
@Composable
fun SystemInfoCard(
    systemInfo: SystemInfo,
    shizukuStatus: ShizukuStatus,
    onRefresh: () -> Unit,
    onRequestShizukuPermission: () -> Unit,
    onLogcatClick: () -> Unit,
    onViewSystemConfigClick: () -> Unit = {},
    onResetIms: () -> Unit = {},
    selectedSim: SimSelection? = null
) {
    val uriHandler = LocalUriHandler.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // ... (existing header row code) ...
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(id = R.string.system_info),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1F))
                IconButton(onClick = {
                    uriHandler.openUri("https://github.com/Mystery00/TurboIMS")
                }) {
                    Icon(painterResource(R.drawable.ic_github), null)
                }
                IconButton(onClick = onLogcatClick) {
                    Icon(imageVector = Icons.Default.BugReport, null)
                }
            }
            Text(
                stringResource(R.string.app_version, systemInfo.appVersionName),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.device_model, systemInfo.deviceModel),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.android_version, systemInfo.androidVersion),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.system_build_version, systemInfo.systemVersion),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.security_patch_version, systemInfo.securityPatchVersion),
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            val shizukuStatusText = when (shizukuStatus) {
                ShizukuStatus.CHECKING -> stringResource(R.string.shizuku_checking)
                ShizukuStatus.NOT_RUNNING -> stringResource(R.string.shizuku_not_running)
                ShizukuStatus.NO_PERMISSION -> stringResource(R.string.shizuku_no_permission)
                ShizukuStatus.READY -> stringResource(R.string.shizuku_ready)
                else -> ""
            }
            val shizukuStatusColor = when (shizukuStatus) {
                ShizukuStatus.NOT_RUNNING -> Color.Red
                ShizukuStatus.NO_PERMISSION -> Color(0xFFFF9800)
                else -> Color(0xFF4CAF50)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.shizuku_status, shizukuStatusText),
                    fontSize = 14.sp,
                    color = shizukuStatusColor
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Button(
                    shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                    onClick = onRefresh,
                ) {
                    Text(text = stringResource(id = R.string.refresh_permission))
                }
                Button(
                    enabled = shizukuStatus == ShizukuStatus.NO_PERMISSION,
                    shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                    onClick = onRequestShizukuPermission,
                ) {
                    Text(text = stringResource(id = R.string.request_permission))
                }
            }

            // View System Config & Restart IMS Buttons
            if (shizukuStatus == ShizukuStatus.READY) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        onClick = onViewSystemConfigClick,
                        enabled = selectedSim?.subId != -1,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                        shape = ButtonGroupDefaults.connectedLeadingButtonShape,
                    ) {
                        Text(text = stringResource(id = R.string.view_system_config))
                    }

                    Button(
                        onClick = onResetIms,
                        enabled = selectedSim?.subId != -1,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = ButtonGroupDefaults.connectedTrailingButtonShape,
                    ) {
                        Text(text = stringResource(id = R.string.restart_ims))
                    }
                }
            }

        }
    }
}

/**
 * SIM 卡选择卡片
 * 列出所有可用的 SIM 卡供用户选择。
 */
@Composable
fun SimCardSelectionCard(
    selectedSim: SimSelection?,
    allSimList: List<SimSelection>,
    onSelectSim: (SimSelection) -> Unit,
    onRefreshSimList: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(id = R.string.sim_card),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1F))
                IconButton(onClick = onRefreshSimList) {
                    Icon(
                        imageVector = Icons.Rounded.Cached,
                        contentDescription = "Refresh"
                    )
                }
            }
            Column {
                allSimList.forEach { sim ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .selectable(
                                selected = (selectedSim == sim),
                                onClick = { onSelectSim(sim) }),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedSim == sim),
                            onClick = { onSelectSim(sim) })
                        Text(sim.showTitle)
                    }
                }
            }
        }
    }
}

/**
 * 此时功能配置卡片
 * 动态加载并显示所支持的 IMS 功能开关。
 */
@Composable
fun FeaturesCard(
    isSelectAllSim: Boolean,
    featureSwitches: Map<Feature, FeatureValue>,
    onFeatureSwitchChange: (Feature, FeatureValue) -> Unit,
    loadFeatureHistory: () -> Unit,
    resetFeatures: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(id = R.string.features_config),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.weight(1F))
                if (!isSelectAllSim) {
                    IconButton(onClick = loadFeatureHistory) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = "Load History"
                        )
                    }
                }
                IconButton(onClick = resetFeatures) {
                    Icon(
                        imageVector = Icons.Rounded.SettingsBackupRestore,
                        contentDescription = "Reload"
                    )
                }
            }

            val showFeatures = Feature.entries.toMutableList()
            if (isSelectAllSim) {
                showFeatures.remove(Feature.CARRIER_NAME)
            }
            showFeatures.forEachIndexed { index, feature ->
                val title = stringResource(feature.showTitleRes)
                val description = stringResource(feature.showDescriptionRes)
                when (feature.valueType) {
                    FeatureValueType.STRING -> {
                        StringFeatureItem(
                            title = title,
                            description = description,
                            initInput = (featureSwitches[feature]?.data ?: "") as String,
                            onInputChange = {
                                onFeatureSwitchChange(
                                    feature,
                                    FeatureValue(it, feature.valueType)
                                )
                            },
                        )
                    }

                    FeatureValueType.BOOLEAN -> {
                        BooleanFeatureItem(
                            title = title,
                            description = description,
                            checked = (featureSwitches[feature]?.data ?: true) as Boolean,
                            onCheckedChange = {
                                onFeatureSwitchChange(
                                    feature,
                                    FeatureValue(it, feature.valueType)
                                )
                            }
                        )
                    }
                }
                if (index < Feature.entries.lastIndex) {
                    HorizontalDivider(thickness = 0.5.dp)
                }
            }
        }
    }
}

@Composable
fun StringFeatureItem(
    title: String,
    description: String,
    initInput: String,
    onInputChange: (String) -> Unit
) {
    var input by remember { mutableStateOf(initInput) }
    LaunchedEffect(initInput) {
        input = initInput
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            modifier = Modifier
                .weight(1F),
            value = input,
            onValueChange = {
                input = it
                onInputChange(it)
            },
            label = {
                Text(
                    title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
            },
            placeholder = {
                Text(description)
            },
            singleLine = true,
            maxLines = 1,
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(
                onDone = {
                    onInputChange(input)
                }
            ),
        )
    }
}

@Composable
fun BooleanFeatureItem(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(description, fontSize = 13.sp, color = MaterialTheme.colorScheme.outline)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
fun Buttons(
    isApplyButtonEnabled: Boolean,
    onApplyConfiguration: () -> Unit,
    onResetConfiguration: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Button(
            modifier = Modifier
                .height(56.dp)
                .weight(1F),
            onClick = onApplyConfiguration,
            enabled = isApplyButtonEnabled,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = ButtonGroupDefaults.connectedLeadingButtonShape,
        ) {
            Text(
                stringResource(R.string.apply_config),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Button(
            modifier = Modifier
                .height(56.dp)
                .weight(1F),
            onClick = onResetConfiguration,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
            shape = ButtonGroupDefaults.connectedTrailingButtonShape,
        ) {
            Text(
                stringResource(R.string.reset_config),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
fun Tips() {
    Column(modifier = Modifier.padding(16.dp)) {
        Text(
            stringResource(id = R.string.tip),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline,
        )
        val lines = stringArrayResource(id = R.array.tips)
        for (text in lines) {
            val t = text.removePrefix("!")
            Text(
                t,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.outline,
                fontWeight = if (text.startsWith("!")) FontWeight.Bold else null
            )
        }
        val annotatedString = buildAnnotatedString {
            append(stringResource(R.string.tip_country_iso_prefix))
            withLink(
                LinkAnnotation.Url(
                    url = "https://github.com/ryfineZ/carrier-ims-for-pixel",
                    styles = TextLinkStyles(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.primary,
                            textDecoration = TextDecoration.Underline
                        )
                    )
                )
            ) {
                append(stringResource(R.string.tip_country_iso_app_name))
            }
        }
        Text(text = annotatedString, fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
    }
}

@Composable
fun ShizukuUpdateDialog(dismissDialog: () -> Unit) {
    AlertDialog(
        onDismissRequest = dismissDialog,
        title = { Text("Shizuku") },
        text = { Text(stringResource(id = R.string.update_shizuku)) },
        confirmButton = {
            TextButton(onClick = dismissDialog) {
                Text(stringResource(id = android.R.string.ok))
            }
        }
    )
}

@Composable
fun SystemConfigDialog(
    onDismissRequest: () -> Unit,
    status: ImsCapabilityStatus,
    simName: String = "",
    isRefreshing: Boolean = false,
    onRefresh: () -> Unit = {},
) {
    // 推导当前 IMS 注册技术：优先从语音能力判断，其次从网络状态推断
    val regTechUnknown = stringResource(R.string.ims_reg_tech_unknown)
    val regTechDisplay = remember(status) {
        val techs = buildList {
            if (status.isVoWifiAvailable) add("WiFi")
            if (status.isVoNrAvailable) add("NR")
            if (status.isVolteAvailable) add("LTE")
        }
        when {
            techs.isNotEmpty() -> techs.joinToString(" / ")
            status.isRegistered && status.isNrSaAvailable -> "NR SA"
            status.isRegistered && status.isNrNsaAvailable -> "NR NSA"
            status.isRegistered -> regTechUnknown
            else -> "—"
        }
    }

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Column {
                Text(stringResource(id = R.string.system_config_title))
                if (simName.isNotBlank()) {
                    Text(
                        text = simName,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        },
        text = {
            Column {
                ImsStatusRow(
                    label = "IMS",
                    isAvailable = status.isRegistered,
                    availableText = stringResource(R.string.ims_status_registered),
                    unavailableText = stringResource(R.string.ims_status_not_registered)
                )
                // IMS 注册技术（文本行，非布尔）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${stringResource(R.string.ims_reg_tech)}: ",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = regTechDisplay,
                        fontSize = 14.sp,
                    )
                }
                ImsStatusRow(label = "VoLTE", isAvailable = status.isVolteAvailable)
                ImsStatusRow(label = "VoWiFi", isAvailable = status.isVoWifiAvailable)
                ImsStatusRow(label = "VoNR", isAvailable = status.isVoNrAvailable)
                ImsStatusRow(
                    label = stringResource(R.string.vt),
                    isAvailable = status.isVtAvailable
                )
                ImsStatusRow(
                    label = stringResource(R.string.ims_cap_nr_nsa),
                    isAvailable = status.isNrNsaAvailable
                )
                ImsStatusRow(
                    label = stringResource(R.string.ims_cap_nr_sa),
                    isAvailable = status.isNrSaAvailable
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.ims_data_snapshot_note),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismissRequest) {
                Text(stringResource(id = android.R.string.ok))
            }
        },
        dismissButton = {
            if (isRefreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    strokeWidth = 2.dp,
                )
            } else {
                TextButton(onClick = onRefresh) {
                    Icon(
                        imageVector = Icons.Rounded.Cached,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.refresh))
                }
            }
        },
    )
}

@Composable
private fun ImsStatusRow(
    label: String,
    isAvailable: Boolean,
    availableText: String = stringResource(R.string.status_available),
    unavailableText: String = stringResource(R.string.status_unavailable),
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label: ",
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = if (isAvailable) availableText else unavailableText,
            fontSize = 14.sp,
            color = if (isAvailable) Color(0xFF4CAF50) else Color.Red
        )
    }
}