package com.zclei.spinecaremom

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Space
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import kotlin.math.min
import kotlin.math.roundToInt

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
        Alerts,
        ChildMode,
    }

    private data class ChildProfile(
        val nickname: String = "朵朵",
        val age: Int = 12,
        val gender: String = "女",
        val curveType: String = "胸腰弯",
        val cobb: Int = 25,
        val risser: String = "2",
        val prescribedHours: Int = 20,
        val braceType: String = "硬支具",
    )

    private data class AiReply(
        val summary: String,
        val analysis: String,
        val advice: List<String>,
        val needDoctor: Boolean,
        val doctorReason: String,
        val category: String,
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
    private var consentChecked = true
    private var remindersOn = true
    private var skinReminderOn = true
    private val child = ChildProfile()
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
                    analysis = "朵朵近7天平均17.2小时，医嘱20小时，主要缺口在14-17点和睡前。佩戴时间越接近医嘱，越有利于维持矫正效果。",
                    advice = listOf("下午14点设一次佩戴提醒", "放学后先穿戴再写作业", "睡前检查支具是否压迫皮肤"),
                    needDoctor = false,
                    doctorReason = "",
                    category = "education",
                ),
            ),
        )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureWindow(window)
        render()
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
                            addView(field("手机号", "138 0000 2026", InputType.TYPE_CLASS_PHONE))
                            addSpace(10)
                            addView(
                                horizontal {
                                    gravity = Gravity.BOTTOM
                                    addView(field("验证码", "062614", InputType.TYPE_CLASS_NUMBER), weightLp(1f))
                                    addSpace(8, horizontal = true)
                                    addView(secondaryButton("获取验证码") {}, widthLp(dp(116)))
                                },
                            )
                            addSpace(12)
                            addView(primaryButton("登录 / 注册") {
                                if (consentChecked) {
                                    stage = Stage.Device
                                    render()
                                }
                            }, matchLp())
                            addSpace(8)
                            addView(label("— 或 —", 13f, P.muted, Typeface.NORMAL, Gravity.CENTER))
                            addSpace(8)
                            addView(secondaryButton("微信一键登录") {
                                stage = Stage.Device
                                render()
                            }, matchLp())
                            addSpace(10)
                            addView(
                                CheckBox(this@MainActivity).apply {
                                    text = "我已阅读并同意《隐私政策》《监护人授权告知》"
                                    setTextColor(P.secondary)
                                    textSize = 13f
                                    isChecked = consentChecked
                                    buttonTintList = android.content.res.ColorStateList.valueOf(P.primary)
                                    setOnCheckedChangeListener { _, checked -> consentChecked = checked }
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

    private fun renderDeviceBinding(): View {
        return screenPage(showBottomPadding = false) {
            addView(pageHeader("设备绑定", "蓝牙扫描或手动输入序列号"))
            addView(
                card {
                    addView(cardHeader("附近设备", "蓝牙已开启，正在同步信号", chip("可连接", P.success)))
                    addView(deviceRow("SpineSensor-A12", "信号强 · 电量82%"))
                    addSpace(8)
                    addView(deviceRow("SpineSensor-B08", "信号中 · 电量46%"))
                },
            )
            addSpace(12)
            addView(
                card {
                    addView(field("手动输入序列号", "SCM-2026-0614", InputType.TYPE_CLASS_TEXT))
                    addSpace(12)
                    addView(
                        horizontal {
                            addView(secondaryButton("暂时跳过") {
                                stage = Stage.Profile
                                render()
                            }, weightLp(1f))
                            addSpace(10, horizontal = true)
                            addView(primaryButton("确认绑定") {
                                stage = Stage.Profile
                                render()
                            }, weightLp(1f))
                        },
                    )
                },
            )
            addSpace(12)
            addView(infoStrip("绑定后会首次同步佩戴数据，未绑定时首页保留绑定提示。"))
        }
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
        addView(field("昵称", child.nickname, InputType.TYPE_CLASS_TEXT))
        addSpace(10)
        addView(choiceRow("性别", listOf("男", "女"), 1))
        addSpace(10)
        addView(field("出生日期", "2014-03-18", InputType.TYPE_CLASS_DATETIME))
    }

    private fun LinearLayout.addProfileStepTwo() {
        addView(field("Cobb 角(初始)", child.cobb.toString(), InputType.TYPE_CLASS_NUMBER))
        addSpace(10)
        addView(choiceRow("弯曲部位", listOf("胸弯", "腰弯", "胸腰弯", "双弯"), 2))
        addSpace(10)
        addView(choiceRow("Risser 征", listOf("未知", "0", "1", "2", "3", "4", "5"), 3))
        addSpace(10)
        addView(infoStrip("Cobb 角和 Risser 征通常能在影像报告或病历记录中找到。"))
    }

    private fun LinearLayout.addProfileStepThree() {
        addView(field("医嘱佩戴时长", child.prescribedHours.toString(), InputType.TYPE_CLASS_NUMBER))
        addSpace(10)
        addView(choiceRow("支具类型", listOf("硬支具", "软支具", "未知"), 0))
        addSpace(10)
        addView(field("初诊日期", "2025-10-20", InputType.TYPE_CLASS_DATETIME))
        addSpace(10)
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
                                MainTab.Alerts -> addAlertsPage()
                                MainTab.ChildMode -> Unit
                            }
                        },
                        matchLp(),
                    )
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
            if (currentTab != MainTab.Alerts) {
                addView(bottomNavigation(), matchHeightLp(dp(76)))
            }
        }
        return shell
    }

    private fun LinearLayout.addHomePage() {
        addView(appHeader(child.nickname, "首页 · Spinecare Mom", showBell = true))
        addView(alertBanner("连续5天佩戴不足", "低于医嘱60%，点击查看处置建议", P.danger) {
            currentTab = MainTab.Alerts
            render()
        })
        addSpace(12)
        addView(
            card {
                addView(cardHeader("本周佩戴", "平均 17.2/20 h · 有数据7天", chip("达标率 86%", P.warning)))
                addView(
                    horizontal {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(ProgressRingView(this@MainActivity, 86, "达标率"), squareLp(dp(118)))
                        addSpace(14, horizontal = true)
                        addView(
                            vertical {
                                addView(rowText("目标完成", "17.2h"))
                                addView(progressLine(86))
                                addSpace(12)
                                addView(metricGrid(listOf("4天" to "最长连续达标", "+1.5h" to "较上周", "2段" to "主要缺口")))
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
                addView(cardHeader("本月趋势", "平均17.1h · 达标22/30天", chip("向好", P.success)))
                addView(BarChartView(this@MainActivity, listOf(58, 72, 86, 64, 91, 95, 88, 76, 82, 94, 63, 79, 96, 90, 85)), matchHeightLp(dp(150)))
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("智能解读", null, chip("AI", P.primary)))
                addInsight("缺口时段集中在14-17点和21-22点。")
                addInsight("比上周平均多1.5小时，节奏正在恢复。")
                addInsight("下午放学后增加一次佩戴提醒，睡前保留皮肤检查。")
                addInsight("今天先把最容易做到的一段补回来，不需要一次解决全部问题。")
            },
        )
        addSpace(12)
        addView(
            grid(2) {
                addQuick("皮肤打卡") { currentTab = MainTab.Logs; logsTab = 0; render() }
                addQuick("生长记录") { currentTab = MainTab.Logs; logsTab = 1; render() }
                addQuick("问AI") { currentTab = MainTab.Consult; render() }
                addQuick("复诊报告") { currentTab = MainTab.Reports; reportTab = 1; render() }
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("历史趋势", "近90天", null))
                addView(metricGrid(listOf("16.8h" to "历史日均", "71%" to "总体达标率", "12天" to "最长连续")))
            },
        )
    }

    private fun LinearLayout.addConsultPage() {
        addView(appHeader("咨询", "当前：${child.nickname}(已关联档案)", showBell = true))
        addView(
            card {
                addView(cardHeader("常见问题", "近7天数据已注入", chip("个性化", P.success)))
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
                    hint = "输入问题..."
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
        addView(
            card {
                addView(cardHeader("依从性报告", "每周一与每月1日自动生成", chip("生成", P.primary)))
                addView(reportRow("周报 · 6月8日-6月14日", "达标率86% · 缺口14-17点", "已生成", P.success))
                addSpace(8)
                addView(reportRow("月报 · 2026年6月", "平均17.1h · 达标22/30天", "需关注", P.warning))
                addSpace(8)
                addView(reportRow("周报 · 6月1日-6月7日", "达标率71% · 较前周+1.5h", "已生成", P.success))
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("本周摘要", null, chip("AI", P.primary)))
                addInsight("本周佩戴节奏较上周改善，达标天数增加。")
                addInsight("主要缺口仍在下午放学后，需要更稳定的提醒。")
                addInsight("左腰部发红需继续观察，建议记录照片变化。")
            },
        )
    }

    private fun LinearLayout.addVisitReport() {
        addView(
            card {
                addView(cardHeader("复诊报告预览", "周期：近30天", chip("二维码", P.primary)))
                addView(
                    vertical {
                        background = rounded(P.surface, dp(8), P.line)
                        setPadding(dp(14), dp(14), dp(14), dp(14))
                        addView(label("Spinecare Mom 复诊报告", 17f, P.text, Typeface.BOLD, Gravity.CENTER))
                        addSpace(10)
                        addPaperRow("基本信息", "朵朵，12岁，胸腰弯，初始Cobb 25°，Risser 2。")
                        addPaperRow("佩戴摘要", "近30天平均17.1h/天，达标22天，统计30天。")
                        addView(BarChartView(this@MainActivity, listOf(62, 74, 84, 68, 91, 88, 93, 77, 82, 90)), matchHeightLp(dp(78)))
                        addPaperRow("缺口时段", "14:00-17:00、21:00-22:00。")
                        addPaperRow("皮肤与生长", "左腰部连续发红2天；近1个月身高+1.2cm。")
                        addPaperRow("医生填写区", "______________________________")
                    },
                )
                addSpace(12)
                addView(
                    horizontal {
                        addView(secondaryButton("分享微信") {}, weightLp(1f))
                        addSpace(10, horizontal = true)
                        addView(primaryButton("导出PDF") {}, weightLp(1f))
                    },
                )
            },
        )
    }

    private fun LinearLayout.addReportArchive() {
        addView(
            card {
                addView(cardHeader("2026年6月", "按月筛选", chip("筛选", P.primary)))
                addView(reportRow("周报 · 6月8日-6月14日", "已存档 · 医生可查看", "已生成", P.success))
                addSpace(8)
                addView(reportRow("周报 · 6月1日-6月7日", "已存档 · 医生可查看", "已生成", P.success))
                addSpace(8)
                addView(reportRow("月报 · 2026年5月", "已存档 · 医生可查看", "已生成", P.success))
            },
        )
    }

    private fun LinearLayout.addLogsPage() {
        addView(appHeader("记录", "皮肤、生长与影像档案", showBell = true))
        addView(segmented(listOf("皮肤", "生长", "影像"), logsTab) { index ->
            logsTab = index
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
        addView(
            card {
                addView(cardHeader("今日皮肤打卡", "部位、状态与照片", chip("左腰发红", P.warning)))
                addView(
                    horizontal {
                        addView(BodyMapView(this@MainActivity), widthHeightLp(dp(112), dp(186)))
                        addSpace(14, horizontal = true)
                        addView(
                            vertical {
                                addView(choiceRow("部位", listOf("左", "右", "背", "腰"), 3))
                                addSpace(10)
                                addView(choiceRow("状态", listOf("正常", "发红", "瘙痒", "破损", "水泡", "疼痛"), 1))
                            },
                            weightLp(1f),
                        )
                    },
                )
                addSpace(12)
                addView(
                    grid(3) {
                        repeat(3) { index ->
                            addView(photoSlot(if (index == 0) "拍照" else "上传"))
                        }
                    },
                )
                addSpace(12)
                addView(primaryButton("保存打卡") {}, matchLp())
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("本周日历", "绿/黄/红", null))
                addView(
                    grid(7) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEachIndexed { index, day ->
                            addView(dayCell(day, when {
                                index == 4 -> P.danger
                                index >= 2 -> P.warning
                                else -> P.success
                            }))
                        }
                    },
                )
            },
        )
    }

    private fun LinearLayout.addGrowthLog() {
        addView(
            card {
                addView(cardHeader("生长记录", "本月身高 +1.2cm", chip("需关注", P.warning)))
                addView(
                    horizontal {
                        addView(field("身高(cm)", "154.2", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL), weightLp(1f))
                        addSpace(10, horizontal = true)
                        addView(primaryButton("录入") {}, widthLp(dp(82)))
                    },
                )
                addSpace(12)
                addView(GrowthChartView(this@MainActivity), matchHeightLp(dp(190)))
                addView(infoStrip("1个月内增长超过1cm，复诊时可请医生或支具师评估支具适配。"))
            },
        )
    }

    private fun LinearLayout.addImagingLog() {
        addView(
            card {
                addView(cardHeader("影像档案", "X光、站立体态照、Adams前屈照", chip("上传", P.primary)))
                addView(imageRow("X光 · 2026-06-01", "Cobb 25°，医生建议继续支具治疗。"))
                addSpace(8)
                addView(imageRow("站立体态照 · 2026-05-01", "肩线较4月更稳定，等待复诊确认。"))
                addSpace(8)
                addView(imageRow("Adams前屈照 · 2026-04-02", "家庭记录，不用于诊断。"))
            },
        )
    }

    private fun LinearLayout.addMePage() {
        addView(appHeader("我的", "档案、设备与隐私", showBell = true))
        addView(
            card {
                addView(
                    horizontal {
                        gravity = Gravity.CENTER_VERTICAL
                        addView(avatar("朵"))
                        addSpace(12, horizontal = true)
                        addView(
                            vertical {
                                addView(label(child.nickname, 18f, P.text, Typeface.BOLD))
                                addView(label("${child.age}岁 · ${child.curveType} · 医嘱${child.prescribedHours}h/天", 13f, P.secondary))
                            },
                            weightLp(1f),
                        )
                        addView(secondaryButton("切换") {}, widthLp(dp(76)))
                    },
                )
            },
        )
        addSpace(12)
        addView(
            card {
                addView(settingRow("设备管理", "SpineSensor-A12 · 电量82% · 今天08:12同步", "正常"))
                addSpace(8)
                addView(toggleRow("佩戴提醒", "14:00、21:00 两个缺口时段", remindersOn) {
                    remindersOn = !remindersOn
                    render()
                })
                addSpace(8)
                addView(toggleRow("打卡提醒", "每日20:30 皮肤检查", skinReminderOn) {
                    skinReminderOn = !skinReminderOn
                    render()
                })
                addSpace(8)
                addView(settingRow("消息中心", "2条未读预警", "查看") {
                    currentTab = MainTab.Alerts
                    render()
                })
            },
        )
        addSpace(12)
        addView(
            card {
                addView(cardHeader("孩子模式", "青少年向成就视图", chip("成就", P.primary)))
                addView(label("今日佩戴进度、连续达标天数和阶段徽章会以青少年视角呈现。", 15f, P.secondary))
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
                addView(cardHeader("隐私与同意", "未成年人健康数据保护", chip("已授权", P.success)))
                addView(
                    grid(2) {
                        listOf("授权记录", "导出数据", "删除数据", "帮助中心").forEach { addQuick(it) {} }
                    },
                )
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

    private fun LinearLayout.addAlertsPage() {
        addView(appHeader("消息中心", "全部 / 未读", showBell = false, showBack = true))
        addView(alertRow("红", "连续5天佩戴严重不足", "近5天低于医嘱60%，建议查看缺口并联系医生或支具师。", P.danger))
        addSpace(8)
        addView(alertRow("黄", "左腰部连续2天发红", "请观察摩擦点，持续不适时及时咨询支具师。", P.warning))
        addSpace(8)
        addView(alertRow("绿", "复诊提醒", "距上次影像检查已6个月，可预约复查。", P.success))
        addSpace(12)
        addView(
            card {
                addView(cardHeader("预警详情", "红色预警", chip("红色", P.danger)))
                addInsight("触发原因：连续5天佩戴低于医嘱60%。")
                addInsight("相关数据：近5天平均11.6h/天，目标20h/天。")
                addInsight("建议操作：查看缺口分析；如持续困难，联系主治医生或支具师。")
                addSpace(12)
                addView(primaryButton("我已处理") {
                    currentTab = MainTab.Home
                    render()
                }, matchLp())
            },
        )
    }

    private fun renderChildMode(): View {
        return screenPage(showBottomPadding = false) {
            addView(
                horizontal {
                    gravity = Gravity.CENTER_VERTICAL
                    addView(secondaryButton("返回") {
                        currentTab = MainTab.Me
                        render()
                    }, widthLp(dp(92)))
                    addView(Space(this@MainActivity), weightLp(1f))
                    addView(secondaryButton("首页") {
                        currentTab = MainTab.Home
                        render()
                    }, widthLp(dp(92)))
                },
            )
            addSpace(12)
            addView(
                vertical {
                    background = rounded(0xFF365F71.toInt(), dp(24), null)
                    setPadding(dp(22), dp(22), dp(22), dp(22))
                    addView(chip("今日进度 82%", P.success))
                    addSpace(12)
                    addView(label("朵朵，今天已经很接近目标了", 25f, Color.WHITE, Typeface.BOLD))
                    addView(label("再完成3.6小时，就能点亮今天的进度环。", 15f, 0xFFEAF7F7.toInt()))
                },
            )
            addSpace(12)
            addView(metricGrid(listOf("4" to "连续达标", "17.2h" to "本周日均", "86%" to "本周达标")))
            addSpace(12)
            addView(
                card {
                    addView(cardHeader("阶段徽章", "本月", null))
                    addView(
                        grid(3) {
                            listOf("稳定达人", "4天连击", "复诊准备").forEach { addQuick(it) {} }
                        },
                    )
                },
            )
            addSpace(12)
            addView(
                card {
                    addView(label("今晚的小目标", 17f, P.text, Typeface.BOLD))
                    addSpace(8)
                    addView(label("洗漱前穿戴，睡前检查腰部皮肤。完成后自动更新今天的进度。", 15f, P.secondary))
                },
            )
        }
    }

    private fun bottomNavigation(): View {
        return horizontal {
            setPadding(dp(10), dp(8), dp(10), dp(10))
            setBackgroundColor(P.surface)
            listOf(
                MainTab.Home to "首页",
                MainTab.Reports to "报告",
                MainTab.Consult to "咨询",
                MainTab.Logs to "记录",
                MainTab.Me to "我的",
            ).forEach { (tab, label) ->
                addView(navButton(label, tab == currentTab, tab == MainTab.Consult) {
                    currentTab = tab
                    render()
                }, weightLp(1f))
            }
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
                analysis = "你的问题中包含需要及时处理的风险描述。APP 会按红色预警处理，并建议把症状、持续时间和照片整理给医生。",
                advice = listOf("保留症状照片和发生时间", "尽快联系主治医生或支具师", "若症状加重或伴随全身不适，及时就诊"),
                needDoctor = true,
                doctorReason = "命中强制就医关键词，AI 不做诊断或停戴判断，需要由医生或支具师评估。",
                category = "clinical",
            )
        }

        if (question.contains("笑") || question.contains("不肯") || question.contains("焦虑")) {
            return AiReply(
                summary = "孩子抗拒时，先降低对抗感，再把佩戴拆成可完成的小目标",
                analysis = "朵朵近7天已有改善迹象。青春期孩子更在意自主感，直接催促容易让佩戴变成冲突。",
                advice = listOf("和孩子约定一个可选择的提醒时间", "把下午缺口拆成30分钟一段", "达标后只反馈进步，不反复追问"),
                needDoctor = false,
                doctorReason = "",
                category = "emotion",
            )
        }

        if (question.contains("体育") || question.contains("运动")) {
            return AiReply(
                summary = "运动安排需要遵循医生医嘱，APP 可帮助整理复诊问题",
                analysis = "朵朵医嘱每日佩戴20小时，运动是否脱戴与支具类型、课程强度、医生方案有关，AI 不能替代医生判断。",
                advice = listOf("把体育课项目和时长记录下来", "复诊时询问哪些运动需要脱戴", "运动后检查皮肤摩擦点并补足可佩戴时段"),
                needDoctor = true,
                doctorReason = "涉及运动期间是否脱戴或调整佩戴方案，需要主治医生或支具师确认。",
                category = "clinical",
            )
        }

        return AiReply(
            summary = "可以先从最明显的缺口时段补起，不需要一次改变全部习惯",
            analysis = "朵朵近7天平均17.2小时，距离医嘱20小时还有约2.8小时缺口，主要集中在下午和睡前。",
            advice = listOf("下午14点开启一次短提醒", "把放学后第一小时设为固定佩戴段", "睡前用30秒检查皮肤和支具位置"),
            needDoctor = false,
            doctorReason = "",
            category = "education",
        )
    }

    private fun screenPage(showBottomPadding: Boolean, build: LinearLayout.() -> Unit): ScrollView {
        return ScrollView(this).apply {
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

    private fun appHeader(title: String, subtitle: String, showBell: Boolean, showBack: Boolean = false): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            if (showBack) {
                addView(secondaryButton("返回") {
                    currentTab = MainTab.Home
                    render()
                }, widthLp(dp(76)))
                addSpace(10, horizontal = true)
            }
            addView(
                vertical {
                    addView(label(subtitle, 13f, P.secondary))
                    addView(label(title, 24f, P.text, Typeface.BOLD))
                },
                weightLp(1f),
            )
            if (showBell) {
                addView(secondaryButton("铃") {
                    currentTab = MainTab.Alerts
                    render()
                }, widthHeightLp(dp(48), dp(48)))
            }
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

    private fun field(label: String, value: String, inputType: Int): View =
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
                },
                matchHeightLp(dp(46)),
            )
        }

    private fun choiceRow(title: String, options: List<String>, selectedIndex: Int): View =
        vertical {
            addView(label(title, 13f, P.secondary, Typeface.BOLD))
            addSpace(6)
            addView(
                grid(if (options.size > 4) 3 else 2) {
                    options.forEachIndexed { index, option ->
                        addView(
                            Button(this@MainActivity).apply {
                                text = option
                                transformationMethod = null
                                textSize = 14f
                                setTextColor(if (index == selectedIndex) P.primary else P.secondary)
                                background = rounded(if (index == selectedIndex) P.primaryLight else P.surfaceAlt, dp(8), P.line)
                            },
                            matchHeightLp(dp(42)),
                        )
                    }
                },
            )
        }

    private fun metricGrid(items: List<Pair<String, String>>): View =
        grid(3) {
            items.forEach { (value, label) ->
                addView(
                    vertical {
                        background = rounded(0xFFF9FBFB.toInt(), dp(8), P.softLine)
                        setPadding(dp(10), dp(10), dp(10), dp(10))
                        addView(label(value, 20f, P.text, Typeface.BOLD))
                        addView(label(label, 12f, P.secondary))
                    },
                    matchHeightLp(dp(78)),
                )
            }
        }

    private fun LinearLayout.addQuick(text: String, onClick: () -> Unit) {
        addView(
            Button(this@MainActivity).apply {
                this.text = text
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

    private fun chip(text: String, color: Int): TextView =
        TextView(this).apply {
            this.text = text
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
            this.text = text
            transformationMethod = null
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(P.primary)
            background = rounded(P.primaryLight, dp(100), P.primaryLight)
            setOnClickListener { onClick() }
        }

    private fun alertBanner(title: String, subtitle: String, color: Int, onClick: () -> Unit): View =
        Button(this).apply {
            text = "$title\n$subtitle"
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
            addAiSection("结合${child.nickname}数据的分析", reply.analysis)
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

    private fun photoSlot(text: String): View =
        TextView(this).apply {
            this.text = text
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(P.muted)
            background = dashedLike()
        }.also {
            it.layoutParams = matchHeightLp(dp(76))
        }

    private fun dayCell(day: String, color: Int): View =
        vertical {
            gravity = Gravity.CENTER
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            addView(label(day, 12f, P.secondary, gravity = Gravity.CENTER))
            addView(
                View(this@MainActivity).apply { background = rounded(color, dp(99), null) },
                widthHeightLp(dp(8), dp(8)),
            )
        }.also {
            it.layoutParams = matchHeightLp(dp(52))
        }

    private fun imageRow(title: String, subtitle: String): View =
        horizontal {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(P.surfaceAlt, dp(8), P.softLine)
            setPadding(dp(12), dp(12), dp(12), dp(12))
            addView(
                TextView(this@MainActivity).apply {
                    text = "片"
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

    private fun navButton(text: String, active: Boolean, primary: Boolean, onClick: () -> Unit): View =
        Button(this).apply {
            this.text = text
            transformationMethod = null
            textSize = if (primary) 14f else 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (primary || active) P.primary else P.secondary)
            background = rounded(
                when {
                    primary -> P.primaryLight
                    active -> P.primaryLight
                    else -> Color.TRANSPARENT
                },
                dp(16),
                null,
            )
            setOnClickListener { onClick() }
        }

    private fun primaryButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            transformationMethod = null
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(P.primary, dp(8), null)
            setOnClickListener { onClick() }
        }

    private fun secondaryButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            transformationMethod = null
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(P.primary)
            background = rounded(P.primaryLight, dp(8), adjustAlpha(P.primary, 0.18f))
            setOnClickListener { onClick() }
        }

    private fun dangerButton(text: String, onClick: () -> Unit): Button =
        Button(this).apply {
            this.text = text
            transformationMethod = null
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            background = rounded(P.danger, dp(8), null)
            setOnClickListener { onClick() }
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
                        text = label
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
            this.text = text
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

    private fun dashedLike(): GradientDrawable =
        GradientDrawable().apply {
            setColor(0xFFF3F7F7.toInt())
            cornerRadius = dp(8).toFloat()
            setStroke(dp(1), 0xFFCBD9D9.toInt(), dp(6).toFloat(), dp(4).toFloat())
        }

    private fun adjustAlpha(color: Int, factor: Float): Int {
        val alpha = (Color.alpha(color) * factor).roundToInt()
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
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

    private class ProgressRingView(context: android.content.Context, private val progress: Int, private val caption: String) : View(context) {
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
            paint.color = P.primary
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

    private class BarChartView(context: android.content.Context, private val values: List<Int>) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val topPad = height * 0.14f
            val bottomPad = height * 0.08f
            val chartHeight = height - topPad - bottomPad
            val gap = dpLocal(5).toFloat()
            val barWidth = (width - gap * (values.size - 1)) / values.size

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpLocal(1).toFloat()
            paint.color = adjustAlphaStatic(P.primary, 0.42f)
            paint.pathEffect = android.graphics.DashPathEffect(floatArrayOf(8f, 8f), 0f)
            val targetY = topPad + chartHeight * 0.24f
            canvas.drawLine(0f, targetY, width.toFloat(), targetY, paint)
            paint.pathEffect = null

            values.forEachIndexed { index, value ->
                val left = index * (barWidth + gap)
                val barHeight = chartHeight * (value / 100f)
                val top = topPad + chartHeight - barHeight
                paint.style = Paint.Style.FILL
                paint.color = when {
                    value >= 82 -> P.primary
                    value >= 70 -> 0xFFF0CF66.toInt()
                    else -> 0xFFE58B82.toInt()
                }
                canvas.drawRoundRect(left, top, left + barWidth, topPad + chartHeight, dpLocal(7).toFloat(), dpLocal(7).toFloat(), paint)
            }
        }

        private fun dpLocal(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    }

    private class BodyMapView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val body = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            body.set(width * 0.16f, height * 0.18f, width * 0.84f, height * 0.94f)
            paint.style = Paint.Style.FILL
            paint.color = 0xFFEEF5F5.toInt()
            canvas.drawRoundRect(body, width * 0.24f, width * 0.24f, paint)
            paint.color = P.primaryLight
            canvas.drawCircle(width / 2f, height * 0.12f, width * 0.22f, paint)
            drawDot(canvas, width * 0.5f, height * 0.44f, P.success)
            drawDot(canvas, width * 0.42f, height * 0.72f, P.warning)
        }

        private fun drawDot(canvas: Canvas, x: Float, y: Float, color: Int) {
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawCircle(x, y, width * 0.105f, paint)
            paint.color = color
            canvas.drawCircle(x, y, width * 0.075f, paint)
        }
    }

    private class GrowthChartView(context: android.content.Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val left = dpLocal(26).toFloat()
            val right = width - dpLocal(16).toFloat()
            val top = dpLocal(24).toFloat()
            val bottom = height - dpLocal(28).toFloat()
            paint.strokeWidth = dpLocal(1).toFloat()
            paint.color = P.line
            repeat(3) { i ->
                val y = top + (bottom - top) * i / 2f
                canvas.drawLine(left, y, right, y, paint)
            }

            val x1 = left
            val x2 = left + (right - left) * 0.28f
            val x3 = left + (right - left) * 0.56f
            val x4 = right
            val y1 = bottom - dpLocal(8)
            val y2 = bottom - dpLocal(28)
            val y3 = bottom - dpLocal(64)
            val y4 = top + dpLocal(20)

            paint.style = Paint.Style.FILL
            paint.color = adjustAlphaStatic(P.success, 0.12f)
            path.reset()
            path.moveTo(left, bottom - dpLocal(20))
            path.cubicTo(x2, bottom - dpLocal(42), x3, top + dpLocal(56), right, top + dpLocal(18))
            path.lineTo(right, top + dpLocal(54))
            path.cubicTo(x3, top + dpLocal(86), x2, bottom - dpLocal(20), left, bottom)
            path.close()
            canvas.drawPath(path, paint)

            paint.style = Paint.Style.STROKE
            paint.strokeWidth = dpLocal(4).toFloat()
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            paint.color = P.primary
            path.reset()
            path.moveTo(x1, y1)
            path.cubicTo(x2, y2, x3, y3, x4, y4)
            canvas.drawPath(path, paint)

            paint.style = Paint.Style.FILL
            listOf(x1 to y1, x2 to y2, x3 to y3, x4 to y4).forEach { (x, y) ->
                paint.color = Color.WHITE
                canvas.drawCircle(x, y, dpLocal(7).toFloat(), paint)
                paint.color = P.primary
                canvas.drawCircle(x, y, dpLocal(5).toFloat(), paint)
            }

            paint.textSize = dpLocal(11).toFloat()
            paint.color = P.secondary
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText("3月", x1, height - dpLocal(8).toFloat(), paint)
            canvas.drawText("4月", x2, height - dpLocal(8).toFloat(), paint)
            canvas.drawText("5月", x3, height - dpLocal(8).toFloat(), paint)
            canvas.drawText("6月", x4, height - dpLocal(8).toFloat(), paint)
        }

        private fun dpLocal(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()
    }

    companion object {
        private fun adjustAlphaStatic(color: Int, factor: Float): Int {
            val alpha = (Color.alpha(color) * factor).roundToInt()
            return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
        }
    }
}
