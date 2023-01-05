package com.zhufucdev.motion_emulator.ui.manager

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.zhufucdev.motion_emulator.R
import com.zhufucdev.motion_emulator.data.Motion
import com.zhufucdev.motion_emulator.data.userDisplay
import com.zhufucdev.motion_emulator.ui.theme.paddingCommon

@Composable
fun MotionEditor(target: Motion, viewModel: ManagerViewModel<Motion>) {
    Box(Modifier.padding(paddingCommon)) {
        BasicEdit(
            id = target.id,
            name = target.userDisplay,
            onNameChanged = {
                viewModel.onModify(target.copy(name = it))
            },
            icon = { Icon(painterResource(R.drawable.ic_baseline_smartphone_24), contentDescription = null) }
        )
    }
}