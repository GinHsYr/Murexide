package com.juhao.murexide.ui.mine

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.viewmodel.compose.viewModel
import com.juhao.murexide.ui.components.*
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditProfileScreen(
    token: String,
    onBackClick: () -> Unit,
    viewModel: MineViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return MineViewModel(token) as T
            }
        }
    )
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val scrollState = rememberScrollState()

    var nickname by remember { mutableStateOf("") }
    var introduction by remember { mutableStateOf("") }
    var gender by remember { mutableIntStateOf(3) }
    var birthday by remember { mutableLongStateOf(0L) }
    var province by remember { mutableStateOf("") }
    var city by remember { mutableStateOf("") }
    var district by remember { mutableStateOf("") }
    var locationCode by remember { mutableStateOf("") }

    // 初始化数据
    LaunchedEffect(uiState) {
        val state = uiState
        if (state is MineUiState.Success) {
            nickname = state.userInfo.name
            introduction = state.introduction
            state.userProfile?.let {
                gender = it.gender
                birthday = it.birthday
                province = it.province
                city = it.city
                district = it.district
                locationCode = it.locationCode
            }
        }
    }

    // 监听事件
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is MineViewModel.MineEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
                is MineViewModel.MineEvent.ProfileUpdated -> {
                    onBackClick()
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                title = { Text("编辑资料") },
                navigationIcon = {
                    StyledIconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                scrollBehavior = scrollBehavior,
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.updateNickname(nickname)
                            viewModel.updateProfile(
                                introduction = introduction,
                                gender = gender,
                                birthday = birthday,
                                province = province,
                                city = city,
                                district = district,
                                locationCode = locationCode
                            )
                        }
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                label = { Text("昵称") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = introduction,
                onValueChange = { introduction = it },
                label = { Text("个性签名") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                maxLines = 5
            )

            Text("性别", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1 to "男", 2 to "女", 3 to "保密").forEach { (value, label) ->
                    FilterChip(
                        selected = gender == value,
                        onClick = { gender = value },
                        label = { Text(label) }
                    )
                }
            }

            OutlinedTextField(
                value = province,
                onValueChange = { province = it },
                label = { Text("省份") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = city,
                onValueChange = { city = it },
                label = { Text("城市") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = district,
                onValueChange = { district = it },
                label = { Text("区县") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = locationCode,
                onValueChange = { locationCode = it },
                label = { Text("邮编") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        }
    }
}
