const app = document.querySelector("#app");
const bottomNav = document.querySelector("#bottomNav");
const phone = document.querySelector(".phone");

const disclaimer =
  "本回答仅供健康科普与参考，不替代医生的诊断与医嘱；如有疑虑请及时咨询主治医生或支具师。";

const emergencyKeywords = [
  "呼吸困难",
  "喘不上气",
  "胸闷气短",
  "无法呼吸",
  "皮肤破溃",
  "破皮",
  "流脓",
  "溃烂",
  "水泡破了",
  "疼痛持续",
  "疼了好几天",
  "夜里疼醒",
  "麻木",
  "无力",
  "晕倒",
  "高烧",
  "伤口感染"
];

const state = {
  stage: "login",
  route: "home",
  consent: true,
  wizardStep: 1,
  reportTab: "ai",
  logsTab: "skin",
  remindersOn: true,
  skinReminderOn: true,
  child: {
    nickname: "朵朵",
    age: 12,
    gender: "女",
    curveType: "胸腰弯",
    cobb: 25,
    risser: "2",
    prescribedHours: 20,
    braceType: "硬支具"
  },
  chat: [
    {
      role: "user",
      text: "少戴2小时有影响吗？"
    },
    {
      role: "ai",
      card: {
        summary: "少戴会影响矫正效果，建议尽量补足医嘱时长",
        analysis:
          "朵朵近7天平均17.2小时，医嘱20小时，主要缺口在14-17点和睡前。佩戴时间越接近医嘱，越有利于维持矫正效果。",
        advice: ["下午14点设一次佩戴提醒", "放学后先穿戴再写作业", "睡前检查支具是否压迫皮肤"],
        need_doctor: false,
        doctor_reason: "",
        add_to_visit_list: false,
        category: "education",
        disclaimer
      }
    }
  ],
  alerts: [
    {
      level: "red",
      title: "连续5天佩戴严重不足",
      summary: "近5天低于医嘱60%，建议查看缺口并联系医生或支具师。",
      time: "今天 08:20",
      unread: true
    },
    {
      level: "yellow",
      title: "左腰部连续2天发红",
      summary: "请观察摩擦点，持续不适时及时咨询支具师。",
      time: "昨天 20:15",
      unread: true
    },
    {
      level: "green",
      title: "复诊提醒",
      summary: "距上次影像检查已6个月，可预约复查。",
      time: "6月10日",
      unread: false
    }
  ]
};

const navItems = [
  { route: "home", label: "首页", icon: "house" },
  { route: "reports", label: "报告", icon: "file-text" },
  { route: "consult", label: "咨询", icon: "message-circle", primary: true },
  { route: "logs", label: "记录", icon: "calendar-check" },
  { route: "me", label: "我的", icon: "user-round" }
];

function icon(name) {
  return `<i data-lucide="${name}" aria-hidden="true"></i>`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function refreshIcons() {
  if (window.lucide) {
    window.lucide.createIcons();
  }
}

function render() {
  const appReady = state.stage === "app";
  const childFocus = appReady && state.route === "child";
  phone.classList.toggle("app-ready", appReady && !childFocus);
  phone.classList.toggle("child-focus", childFocus);

  if (state.stage === "login") {
    app.innerHTML = renderLogin();
    renderNav(false);
  } else if (state.stage === "device") {
    app.innerHTML = renderDeviceBinding();
    renderNav(false);
  } else if (state.stage === "profile") {
    app.innerHTML = renderProfileWizard();
    renderNav(false);
  } else {
    app.innerHTML = renderAppRoute();
    renderNav(!childFocus);
  }

  refreshIcons();
}

function renderNav(visible) {
  bottomNav.classList.toggle("is-hidden", !visible);
  if (!visible) {
    bottomNav.innerHTML = "";
    return;
  }

  bottomNav.innerHTML = navItems
    .map((item) => {
      const isActive = state.route === item.route;
      return `
        <button
          class="nav-item ${item.primary ? "is-primary" : ""} ${isActive ? "is-active" : ""}"
          type="button"
          data-route="${item.route}"
          aria-label="${item.label}"
        >
          ${icon(item.icon)}
          <span>${item.label}</span>
        </button>
      `;
    })
    .join("");
}

function renderLogin() {
  return `
    <section class="brand-panel">
      <div class="brand-card">
        <div class="brand-head">
          <div class="brand-mark">${icon("heart-pulse")}</div>
          <div>
            <h1>脊护妈妈助手</h1>
            <p>守护孩子的每一小时佩戴</p>
          </div>
        </div>

        <div class="form-stack">
          <div class="field">
            <label for="phone">手机号</label>
            <input id="phone" class="input" inputmode="tel" value="138 0000 2026" />
          </div>
          <div class="inline-field">
            <div class="field">
              <label for="code">验证码</label>
              <input id="code" class="input" inputmode="numeric" value="062614" maxlength="6" />
            </div>
            <button class="secondary-button" type="button">获取验证码</button>
          </div>
          <button class="primary-button" type="button" data-action="login" ${state.consent ? "" : "disabled"}>
            ${icon("log-in")}登录 / 注册
          </button>
          <div class="divider"><span>或</span></div>
          <button class="secondary-button" type="button" data-action="login">
            ${icon("message-circle")}微信一键登录
          </button>
          <label class="policy-row" for="consent">
            <input id="consent" type="checkbox" ${state.consent ? "checked" : ""} />
            <span>
              我已阅读并同意
              <a class="link" href="#" aria-label="隐私政策">《隐私政策》</a>
              和
              <a class="link" href="#" aria-label="监护人授权告知">《监护人授权告知》</a>
            </span>
          </label>
        </div>
      </div>
    </section>
  `;
}

function renderDeviceBinding() {
  return `
    <section>
      ${pageTitle("设备绑定", "蓝牙扫描或手动输入序列号")}

      <div class="card">
        <div class="card-header">
          <div>
            <h2 class="card-title">附近设备</h2>
            <p class="caption">蓝牙已开启，正在同步信号</p>
          </div>
          <span class="tag green">${icon("bluetooth")}可连接</span>
        </div>

        <div class="device-list">
          ${deviceRow("SpineSensor-A12", "信号强 · 电量82%", "绑定", "bindDevice")}
          ${deviceRow("SpineSensor-B08", "信号中 · 电量46%", "绑定", "bindDevice")}
        </div>
      </div>

      <div class="card">
        <div class="field">
          <label for="serial">手动输入序列号</label>
          <input id="serial" class="input" value="SCM-2026-0614" />
        </div>
        <div class="wizard-actions">
          <button class="secondary-button" type="button" data-action="skipDevice">
            ${icon("clock")}暂时跳过
          </button>
          <button class="primary-button" type="button" data-action="bindDevice">
            ${icon("link")}确认绑定
          </button>
        </div>
      </div>

      <div class="info-strip">
        ${icon("info")}
        <span>绑定后会首次同步佩戴数据，未绑定时首页保留绑定提示。</span>
      </div>
    </section>
  `;
}

function deviceRow(name, meta, label, action) {
  return `
    <div class="device-row">
      <div class="device-icon">${icon("radio-tower")}</div>
      <div>
        <strong>${name}</strong>
        <p class="caption">${meta}</p>
      </div>
      <button class="secondary-button" type="button" data-action="${action}">${label}</button>
    </div>
  `;
}

function renderProfileWizard() {
  const step = state.wizardStep;
  const progress = `${Math.round((step / 3) * 100)}%`;
  return `
    <section>
      <div class="step-header">
        ${pageTitle("建档向导", `${step}/3 · ${profileStepLabel(step)}`)}
        <div class="progress-line"><span style="width:${progress}"></span></div>
      </div>

      <div class="card">
        ${step === 1 ? renderProfileStepOne() : ""}
        ${step === 2 ? renderProfileStepTwo() : ""}
        ${step === 3 ? renderProfileStepThree() : ""}
      </div>

      <div class="wizard-actions">
        ${
          step > 1
            ? `<button class="secondary-button" type="button" data-action="wizardPrev">${icon("arrow-left")}上一步</button>`
            : ""
        }
        ${
          step < 3
            ? `<button class="primary-button" type="button" data-action="wizardNext">${icon("arrow-right")}下一步</button>`
            : `<button class="primary-button" type="button" data-action="finishProfile">${icon("check")}进入首页</button>`
        }
      </div>
    </section>
  `;
}

function profileStepLabel(step) {
  return ["基础信息", "病情信息", "医嘱与支具"][step - 1];
}

function renderProfileStepOne() {
  return `
    <div class="form-stack">
      <div class="field">
        <label for="nickname">昵称</label>
        <input id="nickname" class="input" maxlength="10" value="${state.child.nickname}" />
      </div>
      <div class="field">
        <label>性别</label>
        <div class="choice-grid">
          <button class="choice" type="button">男</button>
          <button class="choice is-active" type="button">女</button>
        </div>
      </div>
      <div class="field">
        <label for="birthday">出生日期</label>
        <input id="birthday" class="input" type="date" value="2014-03-18" />
      </div>
    </div>
  `;
}

function renderProfileStepTwo() {
  return `
    <div class="form-stack">
      <div class="field">
        <label for="cobb">Cobb 角(初始)</label>
        <input id="cobb" class="input" type="number" min="0" max="100" value="${state.child.cobb}" />
      </div>
      <div class="field">
        <label>弯曲部位</label>
        <div class="choice-grid">
          <button class="choice" type="button">胸弯</button>
          <button class="choice" type="button">腰弯</button>
          <button class="choice is-active" type="button">胸腰弯</button>
          <button class="choice" type="button">双弯</button>
        </div>
      </div>
      <div class="field">
        <label for="risser">Risser 征</label>
        <select id="risser" class="select">
          <option>未知</option>
          <option>0</option>
          <option>1</option>
          <option selected>2</option>
          <option>3</option>
          <option>4</option>
          <option>5</option>
        </select>
      </div>
      <div class="info-strip">${icon("circle-help")}<span>Cobb 角和 Risser 征通常能在影像报告或病历记录中找到。</span></div>
    </div>
  `;
}

function renderProfileStepThree() {
  return `
    <div class="form-stack">
      <div class="field">
        <label for="hours">医嘱佩戴时长</label>
        <input id="hours" class="input" type="number" min="0" max="24" value="${state.child.prescribedHours}" />
      </div>
      <div class="field">
        <label for="brace">支具类型</label>
        <select id="brace" class="select">
          <option selected>硬支具</option>
          <option>软支具</option>
          <option>未知</option>
        </select>
      </div>
      <div class="field">
        <label for="firstVisit">初诊日期</label>
        <input id="firstVisit" class="input" type="date" value="2025-10-20" />
      </div>
      <div class="info-strip">${icon("shield-check")}<span>档案会用于看板、报告和 AI 个性化上下文，默认不包含真实姓名与证件信息。</span></div>
    </div>
  `;
}

function renderAppRoute() {
  const routes = {
    home: renderHome,
    consult: renderConsult,
    reports: renderReports,
    logs: renderLogs,
    me: renderMe,
    alerts: renderAlerts,
    child: renderChildMode
  };

  return (routes[state.route] || routes.home)();
}

function pageTitle(title, subtitle, actions = "") {
  return `
    <div class="topbar page-title">
      <div>
        <p class="kicker">${subtitle}</p>
        <h1>${title}</h1>
      </div>
      ${actions}
    </div>
  `;
}

function appHeader(title, subtitle = "当前孩子", backAction = "") {
  const backButton = backAction
    ? `<button class="icon-button" type="button" data-action="${backAction}" aria-label="返回">${icon("arrow-left")}</button>`
    : "";
  return `
    <div class="topbar">
      <div class="row-between" style="justify-content:flex-start">
        ${backButton}
        <div>
          <p class="kicker">${subtitle}</p>
          <h1>${title}</h1>
        </div>
      </div>
      <div class="header-actions">
        <button class="icon-button" type="button" data-route="alerts" aria-label="消息中心">
          ${icon("bell")}
          <span class="red-dot"></span>
        </button>
      </div>
    </div>
  `;
}

function renderHome() {
  return `
    <section>
      ${appHeader(state.child.nickname, "首页 · Spinecare Mom")}

      <button class="alert-banner red" type="button" data-route="alerts">
        ${icon("triangle-alert")}
        <span><strong>连续5天佩戴不足</strong><span>低于医嘱60%，点击查看处置建议</span></span>
        ${icon("chevron-right")}
      </button>

      <div class="card">
        <div class="card-header">
          <div>
            <h2 class="card-title">本周佩戴</h2>
            <p class="caption">平均 17.2/20 h · 有数据7天</p>
          </div>
          <span class="tag yellow">达标率 86%</span>
        </div>
        <div class="wear-card">
          <div class="progress-ring" style="--progress:86">
            <div class="ring-inner">
              <div>
                <strong>86%</strong>
                <span>达标率</span>
              </div>
            </div>
          </div>
          <div class="progress-stack">
            <div>
              <div class="row-between"><strong>目标完成</strong><span class="caption">17.2h</span></div>
              <div class="progress-bar" style="--value:86%"><span></span></div>
            </div>
            <div class="metric-grid">
              <div class="metric"><strong>4天</strong><span>最长连续达标</span></div>
              <div class="metric"><strong>+1.5h</strong><span>较上周</span></div>
              <div class="metric"><strong>2段</strong><span>主要缺口</span></div>
            </div>
          </div>
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <div>
            <h2 class="card-title">本月趋势</h2>
            <p class="caption">平均17.1h · 达标22/30天</p>
          </div>
          <span class="tag green">${icon("trending-up")}向好</span>
        </div>
        <div class="bar-chart">
          ${renderBars([58, 72, 86, 64, 91, 95, 88, 76, 82, 94, 63, 79, 96, 90, 85])}
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <h2 class="card-title">智能解读</h2>
          <span class="tag">${icon("sparkles")}AI</span>
        </div>
        <div class="insight-list">
          ${insight("clock-3", "缺口时段集中在14-17点和21-22点。")}
          ${insight("thumbs-up", "比上周平均多1.5小时，节奏正在恢复。")}
          ${insight("alarm-clock", "下午放学后增加一次佩戴提醒，睡前保留皮肤检查。")}
          ${insight("heart", "今天先把最容易做到的一段补回来，不需要一次解决全部问题。")}
        </div>
      </div>

      <div class="quick-grid">
        ${quickAction("logs", "scan-heart", "皮肤打卡")}
        ${quickAction("logs", "ruler", "生长记录")}
        ${quickAction("consult", "message-circle", "问AI")}
        ${quickAction("reports", "file-output", "复诊报告")}
      </div>

      <div class="card">
        <div class="card-header">
          <h2 class="card-title">历史趋势</h2>
          <span class="caption">近90天</span>
        </div>
        <div class="metric-grid">
          <div class="metric"><strong>16.8h</strong><span>历史日均</span></div>
          <div class="metric"><strong>71%</strong><span>总体达标率</span></div>
          <div class="metric"><strong>12天</strong><span>最长连续</span></div>
        </div>
      </div>
    </section>
  `;
}

function renderBars(values) {
  return values
    .map((value) => {
      const cls = value >= 82 ? "good" : value >= 70 ? "warn" : "bad";
      return `<span class="bar ${cls}" style="--height:${value}%"></span>`;
    })
    .join("");
}

function insight(iconName, text) {
  return `<div class="insight-item">${icon(iconName)}<span>${text}</span></div>`;
}

function quickAction(route, iconName, label) {
  return `
    <button class="quick-action" type="button" data-route="${route}">
      ${icon(iconName)}
      <span>${label}</span>
    </button>
  `;
}

function renderConsult() {
  return `
    <section>
      ${appHeader("咨询", `当前：${state.child.nickname}(已关联档案)`)}

      <div class="card">
        <div class="card-header">
          <h2 class="card-title">常见问题</h2>
          <span class="tag green">近7天数据已注入</span>
        </div>
        <div class="chip-row">
          ${questionChip("少戴2h有影响吗")}
          ${questionChip("皮肤红了怎么办")}
          ${questionChip("能上体育课吗")}
          ${questionChip("被同学笑话怎么办")}
        </div>
      </div>

      <div class="chat-area">
        ${state.chat.map(renderMessage).join("")}
      </div>

      <div class="composer">
        <input id="questionInput" class="input" placeholder="输入问题..." autocomplete="off" />
        <button class="send-button" type="button" data-action="sendQuestion" aria-label="发送">
          ${icon("send")}
        </button>
      </div>
    </section>
  `;
}

function questionChip(text) {
  return `<button class="chip" type="button" data-action="askChip" data-question="${text}">${text}</button>`;
}

function renderMessage(message) {
  if (message.role === "user") {
    return `<div class="bubble user">${escapeHtml(message.text)}</div>`;
  }
  return renderAiCard(message.card);
}

function renderAiCard(card) {
  return `
    <article class="ai-card">
      <div class="row-between">
        <span class="tag">${icon("bot")}结构化回答</span>
        <span class="status-badge ${card.need_doctor ? "red" : "green"}">
          ${card.need_doctor ? "建议就医" : "健康教育"}
        </span>
      </div>
      <section class="ai-section">
        <strong>一句话总结</strong>
        <p>${escapeHtml(card.summary)}</p>
      </section>
      <section class="ai-section">
        <strong>结合${state.child.nickname}数据的分析</strong>
        <p>${escapeHtml(card.analysis)}</p>
      </section>
      <section class="ai-section">
        <strong>可执行建议</strong>
        <ul>${card.advice.map((item) => `<li>${escapeHtml(item)}</li>`).join("")}</ul>
      </section>
      ${
        card.need_doctor
          ? `<div class="doctor-box">${icon("stethoscope")}<div><strong>就医提示</strong><p>${escapeHtml(
              card.doctor_reason
            )}</p><button class="secondary-button" type="button">加入复诊问题清单</button></div></div>`
          : ""
      }
      <p class="caption">${escapeHtml(card.disclaimer)}</p>
      <div class="row-between">
        <button class="text-button" type="button">${icon("thumbs-up")}有帮助</button>
        <button class="text-button" type="button">${icon("thumbs-down")}需改进</button>
      </div>
    </article>
  `;
}

function renderReports() {
  return `
    <section>
      ${appHeader("报告", "AI 周报月报与复诊材料")}
      <div class="segmented">
        <button class="segment ${state.reportTab === "ai" ? "is-active" : ""}" type="button" data-action="reportTab" data-tab="ai">AI报告</button>
        <button class="segment ${state.reportTab === "visit" ? "is-active" : ""}" type="button" data-action="reportTab" data-tab="visit">复诊报告</button>
        <button class="segment ${state.reportTab === "archive" ? "is-active" : ""}" type="button" data-action="reportTab" data-tab="archive">归档</button>
      </div>
      ${state.reportTab === "ai" ? renderAiReports() : ""}
      ${state.reportTab === "visit" ? renderVisitReport() : ""}
      ${state.reportTab === "archive" ? renderReportArchive() : ""}
    </section>
  `;
}

function renderAiReports() {
  return `
    <div class="card">
      <div class="card-header">
        <div>
          <h2 class="card-title">依从性报告</h2>
          <p class="caption">每周一与每月1日自动生成</p>
        </div>
        <button class="secondary-button" type="button">${icon("refresh-cw")}生成</button>
      </div>
      <div class="report-list">
        ${reportCard("周报", "6月8日-6月14日", "达标率86% · 缺口14-17点", "green")}
        ${reportCard("月报", "2026年6月", "平均17.1h · 达标22/30天", "yellow")}
        ${reportCard("周报", "6月1日-6月7日", "达标率71% · 较前周+1.5h", "green")}
      </div>
    </div>
    <div class="card">
      <div class="card-header">
        <h2 class="card-title">本周摘要</h2>
        <span class="tag">${icon("sparkles")}AI</span>
      </div>
      <div class="insight-list">
        ${insight("check-circle-2", "本周佩戴节奏较上周改善，达标天数增加。")}
        ${insight("clock-3", "主要缺口仍在下午放学后，需要更稳定的提醒。")}
        ${insight("scan-heart", "左腰部发红需继续观察，建议记录照片变化。")}
      </div>
    </div>
  `;
}

function reportCard(kind, period, summary, color) {
  return `
    <button class="report-card" type="button">
      <span class="mini-icon">${icon(kind === "月报" ? "calendar-days" : "calendar-range")}</span>
      <span>
        <h3>${kind} · ${period}</h3>
        <p>${summary}</p>
      </span>
      <span class="status-badge ${color}">${color === "green" ? "已生成" : "需关注"}</span>
    </button>
  `;
}

function renderVisitReport() {
  return `
    <div class="card">
      <div class="card-header">
        <div>
          <h2 class="card-title">复诊报告预览</h2>
          <p class="caption">周期：近30天</p>
        </div>
        <span class="tag">${icon("qr-code")}二维码</span>
      </div>
      <div class="paper-preview">
        <h3>Spinecare Mom 复诊报告</h3>
        ${paperRow("基本信息", "朵朵，12岁，胸腰弯，初始Cobb 25°，Risser 2。")}
        ${paperRow("佩戴摘要", "近30天平均17.1h/天，达标22天，统计30天。")}
        ${paperRow("趋势图", miniChart())}
        ${paperRow("缺口时段", "14:00-17:00、21:00-22:00。")}
        ${paperRow("皮肤与生长", "左腰部连续发红2天；近1个月身高+1.2cm。")}
        ${paperRow("医生填写区", "______________________________")}
      </div>
      <div class="wizard-actions">
        <button class="secondary-button" type="button">${icon("share-2")}分享微信</button>
        <button class="primary-button" type="button">${icon("download")}导出PDF</button>
      </div>
    </div>
  `;
}

function paperRow(label, content) {
  return `<div class="paper-row"><strong>${label}</strong><span>${content}</span></div>`;
}

function miniChart() {
  return `<div class="mini-chart">${[62, 74, 84, 68, 91, 88, 93, 77, 82, 90]
    .map((h) => `<span style="--height:${h}%"></span>`)
    .join("")}</div>`;
}

function renderReportArchive() {
  return `
    <div class="card">
      <div class="card-header">
        <h2 class="card-title">2026年6月</h2>
        <button class="secondary-button" type="button">${icon("sliders-horizontal")}筛选</button>
      </div>
      <div class="report-list">
        ${reportCard("周报", "6月8日-6月14日", "已存档 · 医生可查看", "green")}
        ${reportCard("周报", "6月1日-6月7日", "已存档 · 医生可查看", "green")}
        ${reportCard("月报", "2026年5月", "已存档 · 医生可查看", "green")}
      </div>
    </div>
  `;
}

function renderLogs() {
  return `
    <section>
      ${appHeader("记录", "皮肤、生长与影像档案")}
      <div class="segmented log-tabs">
        <button class="segment ${state.logsTab === "skin" ? "is-active" : ""}" type="button" data-action="logsTab" data-tab="skin">皮肤</button>
        <button class="segment ${state.logsTab === "growth" ? "is-active" : ""}" type="button" data-action="logsTab" data-tab="growth">生长</button>
        <button class="segment ${state.logsTab === "imaging" ? "is-active" : ""}" type="button" data-action="logsTab" data-tab="imaging">影像</button>
      </div>
      ${state.logsTab === "skin" ? renderSkinLog() : ""}
      ${state.logsTab === "growth" ? renderGrowthLog() : ""}
      ${state.logsTab === "imaging" ? renderImagingLog() : ""}
    </section>
  `;
}

function renderSkinLog() {
  return `
    <div class="card">
      <div class="card-header">
        <div>
          <h2 class="card-title">今日皮肤打卡</h2>
          <p class="caption">部位、状态与照片</p>
        </div>
        <span class="status-badge yellow">左腰发红</span>
      </div>
      <div class="body-map">
        <div class="body-figure" aria-hidden="true">
          <span class="body-dot green back"></span>
          <span class="body-dot yellow waist"></span>
        </div>
        <div class="form-stack">
          <div class="field">
            <label>部位</label>
            <div class="choice-grid">
              <button class="choice" type="button">左</button>
              <button class="choice" type="button">右</button>
              <button class="choice" type="button">背</button>
              <button class="choice is-active" type="button">腰</button>
            </div>
          </div>
          <div class="field">
            <label>状态</label>
            <div class="status-grid">
              <button class="status-choice" type="button">正常</button>
              <button class="status-choice is-active" type="button">发红</button>
              <button class="status-choice" type="button">瘙痒</button>
              <button class="status-choice" type="button">破损</button>
              <button class="status-choice" type="button">水泡</button>
              <button class="status-choice" type="button">疼痛</button>
            </div>
          </div>
        </div>
      </div>
      <div class="photo-grid" style="margin-top:12px">
        <button class="photo-slot" type="button">${icon("camera")}</button>
        <button class="photo-slot" type="button">${icon("image-plus")}</button>
        <button class="photo-slot" type="button">${icon("plus")}</button>
      </div>
      <button class="primary-button" type="button" style="width:100%;margin-top:12px">${icon("check")}保存打卡</button>
    </div>
    <div class="card">
      <div class="card-header">
        <h2 class="card-title">本周日历</h2>
        <span class="caption">绿/黄/红</span>
      </div>
      <div class="calendar-strip">
        ${["一", "二", "三", "四", "五", "六", "日"]
          .map((d, i) => {
            const color = i === 4 ? "red" : i >= 2 ? "yellow" : "";
            return `<div class="day-cell"><span>${d}</span><span class="day-dot ${color}"></span></div>`;
          })
          .join("")}
      </div>
    </div>
  `;
}

function renderGrowthLog() {
  return `
    <div class="card">
      <div class="card-header">
        <div>
          <h2 class="card-title">生长记录</h2>
          <p class="caption">本月身高 +1.2cm</p>
        </div>
        <span class="status-badge yellow">需关注</span>
      </div>
      <div class="inline-field">
        <div class="field">
          <label for="height">身高(cm)</label>
          <input id="height" class="input" type="number" value="154.2" />
        </div>
        <button class="primary-button" type="button">${icon("plus")}录入</button>
      </div>
      ${growthChart()}
      <div class="info-strip">${icon("info")}<span>1个月内增长超过1cm，复诊时可请医生或支具师评估支具适配。</span></div>
    </div>
  `;
}

function growthChart() {
  return `
    <svg class="growth-chart" viewBox="0 0 360 190" role="img" aria-label="身高增长曲线">
      <line class="grid" x1="30" y1="42" x2="340" y2="42"></line>
      <line class="grid" x1="30" y1="92" x2="340" y2="92"></line>
      <line class="grid" x1="30" y1="142" x2="340" y2="142"></line>
      <path class="range" d="M32 126 C90 116 132 100 178 90 C230 78 276 62 338 48 L338 84 C276 96 230 112 178 124 C130 136 90 148 32 156 Z"></path>
      <path class="line" d="M34 150 C84 140 120 129 160 112 S248 78 326 54"></path>
      <circle class="point" cx="34" cy="150" r="6"></circle>
      <circle class="point" cx="118" cy="130" r="6"></circle>
      <circle class="point" cx="200" cy="96" r="6"></circle>
      <circle class="point" cx="326" cy="54" r="6"></circle>
      <text x="30" y="176">3月</text>
      <text x="112" y="176">4月</text>
      <text x="194" y="176">5月</text>
      <text x="310" y="176">6月</text>
    </svg>
  `;
}

function renderImagingLog() {
  return `
    <div class="card">
      <div class="card-header">
        <div>
          <h2 class="card-title">影像档案</h2>
          <p class="caption">X光、站立体态照、Adams前屈照</p>
        </div>
        <button class="secondary-button" type="button">${icon("upload")}上传</button>
      </div>
      <div class="timeline">
        ${imageItem("X光", "2026-06-01", "Cobb 25°，医生建议继续支具治疗。", "file-scan")}
        ${imageItem("站立体态照", "2026-05-01", "肩线较4月更稳定，等待复诊确认。", "image")}
        ${imageItem("Adams前屈照", "2026-04-02", "家庭记录，不用于诊断。", "scan-line")}
      </div>
    </div>
  `;
}

function imageItem(type, date, note, iconName) {
  return `
    <button class="timeline-item" type="button">
      <span class="image-thumb">${icon(iconName)}</span>
      <span>
        <h3>${type} · ${date}</h3>
        <p>${note}</p>
      </span>
      ${icon("chevron-right")}
    </button>
  `;
}

function renderMe() {
  return `
    <section>
      ${appHeader("我的", "档案、设备与隐私")}
      <div class="card profile-card">
        <div class="avatar">朵</div>
        <div>
          <h2 class="card-title">${state.child.nickname}</h2>
          <p class="caption">${state.child.age}岁 · ${state.child.curveType} · 医嘱${state.child.prescribedHours}h/天</p>
        </div>
        <button class="secondary-button" type="button">${icon("repeat-2")}切换</button>
      </div>

      <div class="card">
        <div class="settings-list">
          ${settingRow("radio-tower", "设备管理", "SpineSensor-A12 · 电量82% · 今天08:12同步", "green")}
          ${settingRow("alarm-clock", "佩戴提醒", "14:00、21:00 两个缺口时段", "toggle-reminders")}
          ${settingRow("scan-heart", "打卡提醒", "每日20:30 皮肤检查", "toggle-skin")}
          ${settingRow("bell", "消息中心", "2条未读预警", "alerts")}
        </div>
      </div>

      <div class="card">
        <div class="card-header">
          <h2 class="card-title">孩子模式</h2>
          <span class="tag">${icon("sparkles")}成就视图</span>
        </div>
        <p class="body-text">今日佩戴进度、连续达标天数和阶段徽章会以青少年视角呈现。</p>
        <button class="primary-button" type="button" data-route="child" style="width:100%;margin-top:12px">
          ${icon("smile")}进入孩子模式
        </button>
      </div>

      <div class="card">
        <div class="card-header">
          <h2 class="card-title">隐私与同意</h2>
          <span class="status-badge green">已授权</span>
        </div>
        <div class="privacy-grid">
          ${privacyButton("shield-check", "授权记录")}
          ${privacyButton("download", "导出数据")}
          ${privacyButton("trash-2", "删除数据")}
          ${privacyButton("circle-help", "帮助中心")}
        </div>
      </div>

      <button class="danger-button" type="button" data-action="logout" style="width:100%;margin-top:12px">
        ${icon("log-out")}退出登录
      </button>
    </section>
  `;
}

function settingRow(iconName, title, desc, action) {
  const right =
    action === "toggle-reminders"
      ? `<button class="switch ${state.remindersOn ? "is-on" : ""}" type="button" data-action="toggleReminders" aria-label="佩戴提醒"></button>`
      : action === "toggle-skin"
        ? `<button class="switch ${state.skinReminderOn ? "is-on" : ""}" type="button" data-action="toggleSkinReminder" aria-label="打卡提醒"></button>`
        : action === "alerts"
          ? `<button class="secondary-button" type="button" data-route="alerts">查看</button>`
          : `<span class="status-badge ${action || ""}">正常</span>`;

  return `
    <div class="setting-row">
      <span class="mini-icon">${icon(iconName)}</span>
      <span>
        <h3>${title}</h3>
        <p>${desc}</p>
      </span>
      ${right}
    </div>
  `;
}

function privacyButton(iconName, label) {
  return `<button class="privacy-button" type="button">${icon(iconName)}<span>${label}</span></button>`;
}

function renderAlerts() {
  return `
    <section>
      ${appHeader("消息中心", "全部 / 未读", "backHome")}
      <div class="alert-list">
        ${state.alerts.map(alertCard).join("")}
      </div>
      <div class="card">
        <div class="card-header">
          <h2 class="card-title">预警详情</h2>
          <span class="status-badge red">红色</span>
        </div>
        <div class="insight-list">
          ${insight("activity", "触发原因：连续5天佩戴低于医嘱60%。")}
          ${insight("clock-3", "相关数据：近5天平均11.6h/天，目标20h/天。")}
          ${insight("stethoscope", "建议操作：查看缺口分析；如持续困难，联系主治医生或支具师。")}
        </div>
        <button class="primary-button" type="button" style="width:100%;margin-top:12px" data-action="markAlertsRead">
          ${icon("check")}我已处理
        </button>
      </div>
    </section>
  `;
}

function alertCard(alert) {
  return `
    <div class="alert-card">
      <span class="status-badge ${alert.level}">${alert.level === "red" ? "红" : alert.level === "yellow" ? "黄" : "绿"}</span>
      <span>
        <h3>${alert.title}${alert.unread ? " · 未读" : ""}</h3>
        <p>${alert.summary}</p>
      </span>
      <span class="caption">${alert.time}</span>
    </div>
  `;
}

function renderChildMode() {
  return `
    <section class="kid-screen">
      <div class="topbar">
        <button class="icon-button" type="button" data-route="me" aria-label="返回我的">${icon("arrow-left")}</button>
        <button class="secondary-button" type="button" data-route="home">${icon("home")}首页</button>
      </div>
      <div class="kid-hero">
        <span class="tag green">${icon("zap")}今日进度 82%</span>
        <h1>朵朵，今天已经很接近目标了</h1>
        <p>再完成3.6小时，就能点亮今天的进度环。</p>
      </div>
      <div class="streak-grid">
        <div class="streak-card"><strong>4</strong><span class="caption">连续达标</span></div>
        <div class="streak-card"><strong>17.2h</strong><span class="caption">本周日均</span></div>
        <div class="streak-card"><strong>86%</strong><span class="caption">本周达标</span></div>
      </div>
      <div class="card">
        <div class="card-header">
          <h2 class="card-title">阶段徽章</h2>
          <span class="caption">本月</span>
        </div>
        <div class="badge-row">
          <div class="achievement">${icon("award")}稳定达人</div>
          <div class="achievement">${icon("flame")}4天连击</div>
          <div class="achievement">${icon("star")}复诊准备</div>
        </div>
      </div>
      <div class="card">
        <h2 class="card-title">今晚的小目标</h2>
        <p class="body-text" style="margin-top:8px">洗漱前穿戴，睡前检查腰部皮肤。完成后自动更新今天的进度。</p>
      </div>
    </section>
  `;
}

function createAiReply(question) {
  const hitEmergency = emergencyKeywords.some((keyword) => question.includes(keyword));
  if (hitEmergency) {
    return {
      summary: "出现红线症状时应尽快联系医生或支具师",
      analysis:
        "你的问题中包含需要及时处理的风险描述。APP 会按红色预警处理，并建议把症状、持续时间和照片整理给医生。",
      advice: ["保留症状照片和发生时间", "尽快联系主治医生或支具师", "若症状加重或伴随全身不适，及时就诊"],
      need_doctor: true,
      doctor_reason: "命中强制就医关键词，AI 不做诊断或停戴判断，需要由医生或支具师评估。",
      add_to_visit_list: true,
      category: "clinical",
      disclaimer
    };
  }

  if (question.includes("笑") || question.includes("不肯") || question.includes("焦虑")) {
    return {
      summary: "孩子抗拒时，先降低对抗感，再把佩戴拆成可完成的小目标",
      analysis:
        "朵朵近7天已有改善迹象。青春期孩子更在意自主感，直接催促容易让佩戴变成冲突。",
      advice: ["和孩子约定一个可选择的提醒时间", "把下午缺口拆成30分钟一段", "达标后只反馈进步，不反复追问"],
      need_doctor: false,
      doctor_reason: "",
      add_to_visit_list: false,
      category: "emotion",
      disclaimer
    };
  }

  if (question.includes("体育") || question.includes("运动")) {
    return {
      summary: "运动安排需要遵循医生医嘱，APP 可帮助整理复诊问题",
      analysis:
        "朵朵医嘱每日佩戴20小时，运动是否脱戴与支具类型、课程强度、医生方案有关，AI 不能替代医生判断。",
      advice: ["把体育课项目和时长记录下来", "复诊时询问哪些运动需要脱戴", "运动后检查皮肤摩擦点并补足可佩戴时段"],
      need_doctor: true,
      doctor_reason: "涉及运动期间是否脱戴或调整佩戴方案，需要主治医生或支具师确认。",
      add_to_visit_list: true,
      category: "clinical",
      disclaimer
    };
  }

  return {
    summary: "可以先从最明显的缺口时段补起，不需要一次改变全部习惯",
    analysis:
      "朵朵近7天平均17.2小时，距离医嘱20小时还有约2.8小时缺口，主要集中在下午和睡前。",
    advice: ["下午14点开启一次短提醒", "把放学后第一小时设为固定佩戴段", "睡前用30秒检查皮肤和支具位置"],
    need_doctor: false,
    doctor_reason: "",
    add_to_visit_list: false,
    category: "education",
    disclaimer
  };
}

function sendQuestion(question) {
  const clean = question.trim();
  if (!clean) return;
  state.chat.push({ role: "user", text: clean });
  state.chat.push({ role: "ai", card: createAiReply(clean) });
  state.route = "consult";
  render();
  requestAnimationFrame(() => {
    app.scrollTop = app.scrollHeight;
  });
}

document.addEventListener("click", (event) => {
  const routeButton = event.target.closest("[data-route]");
  if (routeButton) {
    event.preventDefault();
    state.stage = "app";
    state.route = routeButton.dataset.route;
    render();
    return;
  }

  const actionButton = event.target.closest("[data-action]");
  if (!actionButton) return;

  const action = actionButton.dataset.action;
  if (action === "login" && state.consent) {
    state.stage = "device";
  }
  if (action === "bindDevice" || action === "skipDevice") {
    state.stage = "profile";
  }
  if (action === "wizardNext") {
    state.wizardStep = Math.min(3, state.wizardStep + 1);
  }
  if (action === "wizardPrev") {
    state.wizardStep = Math.max(1, state.wizardStep - 1);
  }
  if (action === "finishProfile") {
    state.stage = "app";
    state.route = "home";
  }
  if (action === "reportTab") {
    state.reportTab = actionButton.dataset.tab;
  }
  if (action === "logsTab") {
    state.logsTab = actionButton.dataset.tab;
  }
  if (action === "askChip") {
    sendQuestion(actionButton.dataset.question);
    return;
  }
  if (action === "sendQuestion") {
    sendQuestion(document.querySelector("#questionInput")?.value || "");
    return;
  }
  if (action === "toggleReminders") {
    state.remindersOn = !state.remindersOn;
  }
  if (action === "toggleSkinReminder") {
    state.skinReminderOn = !state.skinReminderOn;
  }
  if (action === "markAlertsRead") {
    state.alerts = state.alerts.map((alert) => ({ ...alert, unread: false }));
  }
  if (action === "backHome") {
    state.route = "home";
  }
  if (action === "logout") {
    state.stage = "login";
    state.route = "home";
    state.wizardStep = 1;
  }

  render();
});

document.addEventListener("change", (event) => {
  if (event.target.matches("#consent")) {
    state.consent = event.target.checked;
    render();
  }
});

document.addEventListener("keydown", (event) => {
  if (event.key === "Enter" && event.target.matches("#questionInput")) {
    sendQuestion(event.target.value);
  }
});

render();
