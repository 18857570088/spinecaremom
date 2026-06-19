from __future__ import annotations

from pathlib import Path
import shutil

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    Image,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)


ROOT = Path(__file__).resolve().parents[1]
OUT_DIR = ROOT / "output" / "pdf"
I18N_DIR = OUT_DIR / "i18n"
INDIVIDUAL_DIR = I18N_DIR / "individual"
QR_PATH = ROOT / "spinecare-android" / "app" / "src" / "main" / "res" / "drawable" / "contact_wechat_qr.png"

APP_NAME = "Spinecare Mom"
VERSION = "V1.1"
EFFECTIVE_DATE = "2026-06-17"
COMPANY = "绍兴维脉科技有限公司"
ADDRESS = "浙江省绍兴市越城区袍中北路631号"
EMAIL = "zclei@vip.sina.com"


def register_fonts() -> tuple[str, str]:
    regular_candidates = [
        Path(r"C:\Windows\Fonts\msyh.ttc"),
        Path(r"C:\Windows\Fonts\Deng.ttf"),
        Path(r"C:\Windows\Fonts\NotoSansSC-VF.ttf"),
        Path(r"C:\Windows\Fonts\simsun.ttc"),
    ]
    bold_candidates = [
        Path(r"C:\Windows\Fonts\msyhbd.ttc"),
        Path(r"C:\Windows\Fonts\Dengb.ttf"),
        Path(r"C:\Windows\Fonts\simhei.ttf"),
        Path(r"C:\Windows\Fonts\NotoSansSC-VF.ttf"),
    ]

    regular = next((p for p in regular_candidates if p.exists()), None)
    bold = next((p for p in bold_candidates if p.exists()), regular)
    if regular is None:
        return "Helvetica", "Helvetica-Bold"

    pdfmetrics.registerFont(TTFont("SC-Regular", str(regular)))
    if bold is not None:
        pdfmetrics.registerFont(TTFont("SC-Bold", str(bold)))
    else:
        pdfmetrics.registerFont(TTFont("SC-Bold", str(regular)))
    return "SC-Regular", "SC-Bold"


FONT_REGULAR, FONT_BOLD = register_fonts()


def build_styles():
    base = getSampleStyleSheet()
    return {
        "title": ParagraphStyle(
            "title",
            parent=base["Title"],
            fontName=FONT_BOLD,
            fontSize=21,
            leading=28,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#1B4D5C"),
            wordWrap="CJK",
            spaceAfter=8,
        ),
        "meta": ParagraphStyle(
            "meta",
            parent=base["BodyText"],
            fontName=FONT_REGULAR,
            fontSize=9.5,
            leading=15,
            alignment=TA_CENTER,
            textColor=colors.HexColor("#66707A"),
            wordWrap="CJK",
            spaceAfter=10,
        ),
        "h1": ParagraphStyle(
            "h1",
            parent=base["Heading2"],
            fontName=FONT_BOLD,
            fontSize=13.5,
            leading=19,
            textColor=colors.HexColor("#183F4A"),
            wordWrap="CJK",
            spaceBefore=8,
            spaceAfter=5,
        ),
        "body": ParagraphStyle(
            "body",
            parent=base["BodyText"],
            fontName=FONT_REGULAR,
            fontSize=10.4,
            leading=17,
            textColor=colors.HexColor("#24313A"),
            alignment=TA_LEFT,
            wordWrap="CJK",
            spaceAfter=5,
        ),
        "small": ParagraphStyle(
            "small",
            parent=base["BodyText"],
            fontName=FONT_REGULAR,
            fontSize=9,
            leading=14,
            textColor=colors.HexColor("#66707A"),
            alignment=TA_CENTER,
            wordWrap="CJK",
        ),
    }


STYLES = build_styles()


def footer(canvas, doc):
    canvas.saveState()
    canvas.setFont(FONT_REGULAR, 8)
    canvas.setFillColor(colors.HexColor("#7A858C"))
    canvas.drawString(18 * mm, 12 * mm, f"{APP_NAME} | {COMPANY}")
    canvas.drawRightString(192 * mm, 12 * mm, f"第 {doc.page} 页")
    canvas.restoreState()


def safe(text: str) -> str:
    return (
        text.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\n", "<br/>")
    )


def section_story(title: str, sections: list[tuple[str, str]], subtitle: str | None = None):
    story = [
        Paragraph(safe(title), STYLES["title"]),
        Paragraph(
            safe(subtitle or f"版本：{VERSION}    生效日期：{EFFECTIVE_DATE}"),
            STYLES["meta"],
        ),
        Spacer(1, 4),
    ]
    for heading, body in sections:
        story.append(Paragraph(safe(heading), STYLES["h1"]))
        for paragraph in body.split("\n"):
            paragraph = paragraph.strip()
            if paragraph:
                story.append(Paragraph(safe(paragraph), STYLES["body"]))
        story.append(Spacer(1, 2))
    return story


def write_pdf(path: Path, title: str, sections: list[tuple[str, str]], subtitle: str | None = None, with_qr: bool = False):
    path.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(path),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=18 * mm,
        bottomMargin=18 * mm,
        title=title,
        author=COMPANY,
    )
    story = section_story(title, sections, subtitle)
    if with_qr and QR_PATH.exists():
        story.append(Spacer(1, 8))
        story.append(Image(str(QR_PATH), width=58 * mm, height=58 * mm, hAlign="CENTER"))
        story.append(Paragraph("扫描二维码添加微信客服", STYLES["small"]))
    doc.build(story, onFirstPage=footer, onLaterPages=footer)


def write_combined(path: Path):
    path.parent.mkdir(parents=True, exist_ok=True)
    doc = SimpleDocTemplate(
        str(path),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=18 * mm,
        bottomMargin=18 * mm,
        title=f"{APP_NAME} 帮助中心与协议文档",
        author=COMPANY,
    )
    story = []
    for title, sections in [
        ("Spinecare Mom APP用户手册", MANUAL_SECTIONS),
        ("Spinecare Mom APP用户协议", AGREEMENT_SECTIONS),
        ("Spinecare Mom APP隐私政策", PRIVACY_SECTIONS),
        ("Spinecare Mom 联系我们", CONTACT_SECTIONS),
    ]:
        if story:
            story.append(PageBreak())
        story.extend(section_story(title, sections))
        if title.endswith("联系我们") and QR_PATH.exists():
            story.append(Spacer(1, 8))
            story.append(Image(str(QR_PATH), width=58 * mm, height=58 * mm, hAlign="CENTER"))
            story.append(Paragraph("扫描二维码添加微信客服", STYLES["small"]))
    doc.build(story, onFirstPage=footer, onLaterPages=footer)


MANUAL_SECTIONS = [
    (
        "一、产品简介",
        "Spinecare Mom 是用于脊柱侧弯支具佩戴管理的 Android APP，帮助监护人记录孩子建档信息、蓝牙支具传感器佩戴数据、皮肤问题、生长记录、影像档案、AI报告、复诊报告和归档资料。\n"
        f"本APP由{COMPANY}提供服务，适用于家庭佩戴管理、日常记录和复诊资料整理场景。",
    ),
    (
        "二、使用前准备",
        "请准备 Android 手机、可用网络、DR-Z-T0 前缀蓝牙设备以及医生或支具师提供的建档信息。首次使用建议由监护人操作，并确认已了解隐私政策、用户协议和医疗免责声明。",
    ),
    (
        "三、首次使用流程",
        "打开APP后登录或注册，按建档向导完善昵称、性别、出生日期、Cobb角、弯曲部位、Risser征、医嘱佩戴时间、支具类型和初诊日期。建档信息会同步到云端，用于首页统计、报告和AI解读。",
    ),
    (
        "四、蓝牙设备绑定",
        "进入设置页，打开蓝牙连接，扫描名称以 DR-Z-T0 开头的设备。连接成功后APP会保存设备，下次打开时自动尝试连接上一次设备；如20秒未发现设备，会提示到设置界面重新扫描连接。",
    ),
    (
        "五、读取佩戴数据",
        "蓝牙连接成功后，APP会自动读取设备佩戴数据并上传云端。界面会提示“正在读取设备数据，请稍候”，完成后提示“数据读取完成”。上传成功后会自动清除设备端已存储的佩戴数据。",
    ),
    (
        "六、首页数据解读",
        "首页近30天佩戴和近7天佩戴均来自云端真实数据，达标标准取自建档中的医嘱佩戴时间。条形图、圆形达标率和智能解读会按达标程度分档提示风险。",
    ),
    (
        "七、记录功能",
        "皮肤记录用于发现发红、疼痛、破皮、水泡等问题时拍照和备注；问题部位和问题类型可多选。生长记录用于录入身高并查看趋势；影像档案用于拍照或从手机图库选择影像资料。相关内容会进入复诊报告。",
    ),
    (
        "八、报告与归档",
        "AI报告、复诊报告和归档均基于云端佩戴、皮肤、生长、影像及建档信息生成。归档会保存生成当时的数据快照，后续数据变化不会自动改写已归档报告。",
    ),
    (
        "九、导出与删除",
        "导出数据用于删除前备份。删除全部数据前必须先导出备份、勾选确认项、输入孩子昵称和确认文字，并经过冷静期，避免误删除。删除完成后云端数据不可恢复。",
    ),
    (
        "十、语言与设置",
        "设置页可切换中文、英文、日语、韩语、西班牙语、法语、德语，并可管理蓝牙连接。语言选择会保存到本机，后续打开APP时继续沿用。",
    ),
    (
        "十一、医疗安全提示",
        "本APP用于家庭佩戴管理和复诊资料整理，不提供诊断、处方或支具调整结论。出现疼痛、皮肤破损、麻木、呼吸不适或其他紧急情况时，应及时联系医生或支具师。",
    ),
    (
        "十二、售后与反馈",
        f"反馈问题时建议提供孩子昵称、设备名称、问题发生时间、相关截图或照片。运营主体：{COMPANY}；地址：{ADDRESS}；邮箱：{EMAIL}。可在APP“联系我们”页面扫描微信二维码添加客服。",
    ),
]


AGREEMENT_SECTIONS = [
    ("一、协议说明", f"欢迎使用 Spinecare Mom。本协议由{COMPANY}与您就 Spinecare Mom APP 及相关服务的使用订立。您注册、登录、访问、安装、使用本APP或相关服务，即视为已阅读、理解并同意本协议。"),
    ("二、服务内容", "本APP提供孩子建档、支具蓝牙设备绑定、佩戴数据读取与云端同步、首页统计、皮肤/生长/影像记录、AI辅助解读、复诊报告、归档、导出备份和删除全部数据等服务。"),
    ("三、监护人责任", "本APP面向未成年人健康管理场景。监护人应确认有权录入和管理相关数据，确保所录入的孩子资料、医嘱信息、照片和记录真实、准确、及时更新，并妥善保管手机和账号使用权限。"),
    ("四、设备绑定与数据同步", "用户应使用真实、合法取得的 DR-Z-T0 前缀蓝牙设备。蓝牙连接后APP会读取佩戴数据并上传云端，上传成功后可清除设备端已存储佩戴数据。因设备未开机、蓝牙异常、网络异常或用户误操作导致的数据缺失，用户应及时检查并重新同步。"),
    ("五、医疗免责声明", "本APP不是医疗诊断、处方或治疗工具。AI解读、报告摘要、颜色预警、复诊资料和提醒仅供家庭健康管理参考，不替代医生诊断、医嘱、复诊安排或支具师调整意见。"),
    ("六、数据与报告", "用户上传或同步的数据会用于首页统计、智能解读、复诊报告、归档和导出备份。归档报告保存生成时的数据快照，后续数据变化不会自动改写已归档报告。"),
    ("七、用户行为规范", "用户不得上传违法、侵权、恶意、虚假或与本服务无关的内容，不得尝试破坏服务、绕过安全限制、冒用他人信息、伪造佩戴数据或干扰云端数据库正常运行。"),
    ("八、知识产权", f"本APP及相关系统、页面设计、算法逻辑、界面元素、文案、代码和报告模板等内容的知识产权归{COMPANY}或相关权利人所有。未经书面许可，不得复制、修改、传播、反编译或用于商业用途。"),
    ("九、服务变更、中断与终止", "基于功能维护、版本升级、服务器调整、医疗安全或合规要求，我们可能调整、中断或终止部分服务。涉及重要数据处理规则变化时，应在APP内更新说明或提示用户查看。"),
    ("十、隐私与个人信息保护", "我们将按照《Spinecare Mom APP隐私政策》处理个人信息和未成年人健康相关数据。隐私政策是本协议的重要组成部分，与本协议具有同等效力。"),
    ("十一、终止与删除", "用户可通过导出数据和删除全部数据流程结束使用。删除完成后云端建档、佩戴、皮肤、生长、影像、报告、预警、设备绑定及上传文件不可恢复，建议提前保存备份。"),
    ("十二、适用法律与争议解决", f"本协议的订立、履行、解释及争议解决适用中华人民共和国法律。因本协议引起的争议，双方应先友好协商；协商不成的，可向{COMPANY}所在地有管辖权的人民法院提起诉讼。"),
    ("十三、联系我们", f"运营主体：{COMPANY}；地址：{ADDRESS}；邮箱：{EMAIL}。也可在APP“联系我们”页面扫描微信二维码添加客服。"),
]


PRIVACY_SECTIONS = [
    ("一、引言", f"欢迎使用 Spinecare Mom。我们深知个人信息和未成年人健康相关数据的重要性，并将按照合法、正当、必要、诚信、公开透明的原则处理您的信息。本隐私政策适用于{COMPANY}提供的 Spinecare Mom APP 及相关服务。"),
    ("二、我们收集和使用的信息", "APP可能收集监护人登录信息、孩子建档信息、Cobb角、弯曲部位、Risser征、医嘱佩戴时间、支具类型、初诊日期、蓝牙设备信息、佩戴记录、皮肤照片与备注、生长记录、影像资料、AI咨询内容、报告归档、预警消息、导出与删除流程记录及必要运行日志。"),
    ("三、使用目的", "上述数据用于完成登录与建档、蓝牙设备绑定、读取和上传佩戴数据、展示佩戴趋势、判断达标情况、生成AI报告和复诊报告、保存归档、导出备份、执行删除流程、排查故障及改进服务。"),
    ("四、权限说明", "蓝牙权限用于连接 DR-Z-T0 前缀支具传感器；相机权限用于拍摄皮肤问题或影像资料；图库权限用于选择手机相册中的影像资料；网络权限用于与云端数据库、上传目录和服务接口同步。"),
    ("五、共享、转让和公开披露", "我们不会向无关第三方出售或非法提供个人信息和健康相关数据。除用户主动分享、依法要求、履行监护人授权、保护用户安全或服务运行必要外，APP不会将健康数据提供给无关第三方。如发生合并、分立、收购或资产转让，我们将要求接收方继续受本政策约束。"),
    ("六、存储与保护", "数据会保存于项目独立云端数据库和上传目录。本机也会保存语言选择、最近备份时间、上次绑定设备等基础设置。我们将采取访问控制、数据库权限管理、日志审计、最小权限原则等合理措施保护数据安全。"),
    ("七、未成年人保护", "本APP主要用于未成年人支具佩戴管理。未成年人数据应由监护人录入和管理。APP尽量减少不必要身份信息采集，默认不要求真实姓名或证件号码。监护人应妥善管理孩子照片、影像和健康记录。"),
    ("八、您的权利", "您可在APP内查看、更正建档信息，导出主要云端数据作为备份，并按“删除全部数据”流程申请删除当前孩子相关云端数据。删除完成后数据不可恢复，请先确认备份文件可打开并妥善保存。"),
    ("九、导出与删除", "导出功能会生成包含主要云端数据的备份文件，请保存到可信位置并避免随意转发。删除功能会删除当前孩子相关的云端建档、佩戴、皮肤、生长、影像、报告、预警、设备绑定和上传文件。"),
    ("十、政策更新", "我们可能根据产品功能、法律法规或运营需要更新本隐私政策。涉及个人信息权益的重要变更时，应在APP中提示用户查看。"),
    ("十一、联系我们", f"运营主体：{COMPANY}；地址：{ADDRESS}；邮箱：{EMAIL}。也可在APP“联系我们”页面扫描微信二维码添加客服。"),
]


CONTACT_SECTIONS = [
    ("运营主体", COMPANY),
    ("联系地址", ADDRESS),
    ("电子邮箱", EMAIL),
    ("微信客服", "请扫描下方二维码添加微信客服。咨询时建议说明孩子昵称、设备名称、问题发生时间、相关截图或照片。紧急健康问题请及时联系医生或支具师。"),
]


def main():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    INDIVIDUAL_DIR.mkdir(parents=True, exist_ok=True)

    outputs = {
        "manual": OUT_DIR / "SpinecareMom_用户手册.pdf",
        "agreement": OUT_DIR / "SpinecareMom_用户协议.pdf",
        "privacy": OUT_DIR / "SpinecareMom_隐私政策.pdf",
        "contact": OUT_DIR / "SpinecareMom_联系我们.pdf",
        "archive": OUT_DIR / "SpinecareMom_帮助中心与协议文档_备查.pdf",
    }
    write_pdf(outputs["manual"], "Spinecare Mom APP用户手册", MANUAL_SECTIONS)
    write_pdf(outputs["agreement"], "Spinecare Mom APP用户协议", AGREEMENT_SECTIONS)
    write_pdf(outputs["privacy"], "Spinecare Mom APP隐私政策", PRIVACY_SECTIONS)
    write_pdf(outputs["contact"], "Spinecare Mom 联系我们", CONTACT_SECTIONS, with_qr=True)
    write_combined(outputs["archive"])

    copy_map = {
        outputs["manual"]: INDIVIDUAL_DIR / "SpinecareMom_zh_manual.pdf",
        outputs["agreement"]: INDIVIDUAL_DIR / "SpinecareMom_zh_agreement.pdf",
        outputs["privacy"]: INDIVIDUAL_DIR / "SpinecareMom_zh_privacy.pdf",
        outputs["contact"]: INDIVIDUAL_DIR / "SpinecareMom_zh_contact.pdf",
        outputs["archive"]: I18N_DIR / "SpinecareMom_help_center_archive_zh.pdf",
    }
    for src, dst in copy_map.items():
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)

    for path in outputs.values():
        print(path)


if __name__ == "__main__":
    main()
