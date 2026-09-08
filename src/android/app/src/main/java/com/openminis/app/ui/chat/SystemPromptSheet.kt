package com.openminis.app.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SystemPromptSheet(
    systemMdContent: String,
    appendSystemMdContent: String,
    defaultPrompt: String,
    onSave: (systemMd: String, appendSystemMd: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var systemText by remember { mutableStateOf(systemMdContent.ifEmpty { defaultPrompt }) }
    var appendText by remember { mutableStateOf(appendSystemMdContent) }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: SYSTEM.md, 1: APPEND_SYSTEM.md

    StandardChatSheet(
        title = "系统提示词设置",
        onDismiss = onDismiss,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "SYSTEM.md",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.5.sp,
                        )
                    },
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            "APPEND_SYSTEM.md",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.5.sp,
                        )
                    },
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (selectedTab == 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "基础系统提示词 (SYSTEM.md)",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            TextButton(onClick = { systemText = defaultPrompt }) {
                                Text("重置为默认", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        OutlinedTextField(
                            value = systemText,
                            onValueChange = { systemText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(340.dp),
                            placeholder = { Text("输入 SYSTEM.md 系统提示词内容...") },
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Text(
                            text = "追加提示词 (APPEND_SYSTEM.md)：自动追加在系统提示词末尾",
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        OutlinedTextField(
                            value = appendText,
                            onValueChange = { appendText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            placeholder = { Text("输入 APPEND_SYSTEM.md 追加提示词，例如：\n- 始终使用中文回复\n- 代码完整输出，切勿缩略\n- 每次完成任务后总结要点") },
                            shape = RoundedCornerShape(10.dp),
                            textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 12.5.sp),
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "💡 提示：APPEND_SYSTEM.md 会存储在持久化记忆中，每次对话都会自动附加生效。",
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("取消")
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(onClick = {
                    onSave(systemText.trim(), appendText.trim())
                }) {
                    Text("保存 (Save)")
                }
            }
        }
    }
}
