# 用途：根据 docs/答辩PPT.md 的页面结构生成答辩演示文稿。
# 运行：python scripts/build_ppt.py
# 输出：docs/答辩PPT.pptx
from pathlib import Path

from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.enum.shapes import MSO_SHAPE
from pptx.enum.text import MSO_ANCHOR, PP_ALIGN
from pptx.util import Inches, Pt

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "docs" / "答辩PPT.pptx"

BLUE = RGBColor(0x1F, 0x4E, 0x79)
ACCENT = RGBColor(0x2F, 0x80, 0xED)
GRAY = RGBColor(0x59, 0x59, 0x59)
LIGHT = RGBColor(0xF2, 0xF6, 0xFC)
WHITE = RGBColor(0xFF, 0xFF, 0xFF)
LINE = RGBColor(0xD6, 0xE2, 0xF0)
FONT = "Microsoft YaHei"

prs = Presentation()
prs.slide_width = Inches(13.333)
prs.slide_height = Inches(7.5)
BLANK = prs.slide_layouts[6]


def add_text(slide, left, top, width, height, text, size=18, color=GRAY,
             bold=False, align=PP_ALIGN.LEFT, anchor=MSO_ANCHOR.TOP, line_spacing=1.15):
    box = slide.shapes.add_textbox(Inches(left), Inches(top), Inches(width), Inches(height))
    frame = box.text_frame
    frame.word_wrap = True
    frame.vertical_anchor = anchor
    frame.margin_left = 0
    frame.margin_right = 0
    frame.margin_top = 0
    frame.margin_bottom = 0
    lines = text.split("\n") if isinstance(text, str) else text
    for i, line in enumerate(lines):
        paragraph = frame.paragraphs[0] if i == 0 else frame.add_paragraph()
        paragraph.alignment = align
        paragraph.line_spacing = line_spacing
        run = paragraph.add_run()
        run.text = line
        run.font.name = FONT
        run.font.size = Pt(size)
        run.font.bold = bold
        run.font.color.rgb = color
    return box


def add_rect(slide, left, top, width, height, fill, line=None, shape=MSO_SHAPE.RECTANGLE):
    rect = slide.shapes.add_shape(shape, Inches(left), Inches(top), Inches(width), Inches(height))
    rect.fill.solid()
    rect.fill.fore_color.rgb = fill
    if line is None:
        rect.line.fill.background()
    else:
        rect.line.color.rgb = line
    rect.shadow.inherit = False
    return rect


def add_title(slide, title, subtitle=None):
    add_rect(slide, 0.6, 0.5, 0.12, 0.55, ACCENT)
    add_text(slide, 0.9, 0.46, 10.8, 0.6, title, size=28, color=BLUE, bold=True)
    add_rect(slide, 0.9, 1.12, 11.8, 0.03, LINE)
    if subtitle:
        add_text(slide, 0.9, 1.22, 11.8, 0.4, subtitle, size=13, color=GRAY)


def add_bullets(slide, left, top, width, height, items, size=17, color=GRAY):
    box = slide.shapes.add_textbox(Inches(left), Inches(top), Inches(width), Inches(height))
    frame = box.text_frame
    frame.word_wrap = True
    frame.margin_left = 0
    frame.margin_top = 0
    for i, item in enumerate(items):
        paragraph = frame.paragraphs[0] if i == 0 else frame.add_paragraph()
        paragraph.line_spacing = 1.35
        paragraph.space_after = Pt(8)
        run = paragraph.add_run()
        run.text = "•  " + item
        run.font.name = FONT
        run.font.size = Pt(size)
        run.font.color.rgb = color
    return box


def add_footer(slide, index):
    add_text(slide, 0.9, 7.02, 8.0, 0.3, "毕业论文（设计）过程管理平台", size=10, color=RGBColor(0x9A, 0x9A, 0x9A))
    add_text(slide, 11.8, 7.02, 1.0, 0.3, str(index), size=10, color=RGBColor(0x9A, 0x9A, 0x9A), align=PP_ALIGN.RIGHT)


def add_image_fit(slide, path, left, top, max_w, max_h):
    from PIL import Image

    with Image.open(path) as image:
        w, h = image.size
    ratio = min(max_w / w, max_h / h)
    width = w * ratio
    height = h * ratio
    x = left + (max_w - width) / 2
    y = top + (max_h - height) / 2
    slide.shapes.add_picture(str(path), Inches(x), Inches(y), Inches(width), Inches(height))


def add_table(slide, left, top, width, height, rows, col_widths=None, font_size=13):
    table = slide.shapes.add_table(len(rows), len(rows[0]), Inches(left), Inches(top),
                                   Inches(width), Inches(height)).table
    for r, row in enumerate(rows):
        for c, value in enumerate(row):
            cell = table.cell(r, c)
            cell.text = str(value)
            cell.fill.solid()
            cell.fill.fore_color.rgb = BLUE if r == 0 else (LIGHT if r % 2 else WHITE)
            for paragraph in cell.text_frame.paragraphs:
                paragraph.alignment = PP_ALIGN.CENTER
                for run in paragraph.runs:
                    run.font.name = FONT
                    run.font.size = Pt(font_size)
                    run.font.bold = r == 0
                    run.font.color.rgb = WHITE if r == 0 else GRAY
            cell.vertical_anchor = MSO_ANCHOR.MIDDLE
    if col_widths:
        for c, width_in in enumerate(col_widths):
            table.columns[c].width = Inches(width_in)
    return table


# 1 封面
slide = prs.slides.add_slide(BLANK)
add_rect(slide, 0, 0, 13.333, 7.5, BLUE)
add_rect(slide, 0.9, 2.5, 0.14, 1.9, ACCENT)
add_text(slide, 1.25, 2.35, 11.2, 0.9, "毕业论文（设计）过程管理平台", size=42, color=WHITE, bold=True)
add_text(slide, 1.25, 3.35, 11.2, 0.6, "把毕业设计的每一步都留在系统里", size=22, color=RGBColor(0xD6, 0xE2, 0xF0))
add_text(slide, 1.25, 4.6, 8.0, 0.4, "软件工程专业实践课程", size=15, color=RGBColor(0xB8, 0xCC, 0xE4))
add_text(slide, 1.25, 5.1, 8.0, 0.4, "姓名：＿＿＿＿＿    学号：＿＿＿＿＿    指导教师：＿＿＿＿＿", size=14, color=RGBColor(0xB8, 0xCC, 0xE4))
add_text(slide, 1.25, 6.4, 8.0, 0.4, "2026 年 11 月", size=14, color=RGBColor(0xB8, 0xCC, 0xE4))

# 2 问题与目标
slide = prs.slides.add_slide(BLANK)
add_title(slide, "问题与目标", "聚焦过程管理，不追求覆盖全部教务功能")
add_rect(slide, 0.9, 1.8, 5.6, 4.9, LIGHT)
add_rect(slide, 6.9, 1.8, 5.6, 4.9, LIGHT)
add_text(slide, 1.2, 2.0, 5.0, 0.5, "三个痛点", size=20, color=BLUE, bold=True)
add_bullets(slide, 1.2, 2.6, 4.9, 3.6, [
    "学生不清楚当前进度和下一步任务",
    "材料通过聊天工具传递，版本与退回原因难追溯",
    "导师需要逐个询问学生，缺少全局视图",
], size=17)
add_text(slide, 7.2, 2.0, 5.0, 0.5, "三个目标", size=20, color=BLUE, bold=True)
add_bullets(slide, 7.2, 2.6, 4.9, 3.6, [
    "过程可追踪：六步固定流程",
    "材料可审核：版本保留、意见可回看",
    "进度可汇总：一屏看到所有学生",
], size=17)
add_footer(slide, 2)

# 3 六步流程
slide = prs.slides.add_slide(BLANK)
add_title(slide, "六步流程", "每一步都有明确的提交物与完成条件")
steps = ["需求文档", "原型", "设计评审", "周报", "代码仓库", "测试报告"]
left = 0.75
for i, step in enumerate(steps):
    box = add_rect(slide, left + i * 2.05, 1.85, 1.72, 0.95, BLUE if i < 3 else ACCENT, shape=MSO_SHAPE.ROUNDED_RECTANGLE)
    add_text(slide, left + i * 2.05, 2.03, 1.72, 0.5, step, size=15, color=WHITE, bold=True, align=PP_ALIGN.CENTER)
    if i < 5:
        add_text(slide, left + i * 2.05 + 1.72, 2.02, 0.33, 0.5, "→", size=20, color=ACCENT, bold=True, align=PP_ALIGN.CENTER)
add_text(slide, 0.9, 3.15, 5.6, 0.4, "学生：提交材料、周报、仓库、测试报告", size=15, color=GRAY)
add_text(slide, 7.0, 3.15, 5.6, 0.4, "导师：审核、评审、批阅、退回", size=15, color=GRAY)
add_table(slide, 0.9, 3.75, 11.6, 2.35, [
    ["规则", "说明"],
    ["完成条件", "每步由真实业务数据判定，不提供手工修改进度入口"],
    ["退回重交", "导师退回必须填写意见，学生修改后重新提交"],
    ["进度口径", "按“连续完成前缀”计算，当前步骤未完成时后续步骤不计入"],
], col_widths=[2.0, 9.6], font_size=14)
add_footer(slide, 3)

# 4 技术架构
slide = prs.slides.add_slide(BLANK)
add_title(slide, "技术架构", "前后端分离，本地即可完整运行")
layers = [
    ("浏览器：Vue 3 + Element Plus", 1.75, ACCENT),
    ("HTTP / JSON + JWT", 2.55, WHITE),
    ("Spring Boot 3：Controller → Service → Mapper", 3.25, BLUE),
    ("H2 / MySQL        uploads/        GitHub / Gitee API", 4.55, RGBColor(0xE8, 0xEF, 0xF8)),
]
for text, top, color in layers:
    add_rect(slide, 2.1, top, 5.4, 0.62, color, line=BLUE if color == WHITE else None, shape=MSO_SHAPE.ROUNDED_RECTANGLE)
    add_text(slide, 2.25, top + 0.13, 5.1, 0.4, text,
             size=15 if len(text) < 40 else 13,
             color=WHITE if color in (ACCENT, BLUE) else BLUE, bold=True, align=PP_ALIGN.CENTER)
add_text(slide, 8.0, 1.8, 4.4, 0.4, "选型与规模", size=18, color=BLUE, bold=True)
add_bullets(slide, 8.0, 2.3, 4.4, 3.8, [
    "Java 21 + Spring Boot 3.3.5",
    "Vue 3 + Vite + Element Plus",
    "JWT HS256 + 拦截器鉴权",
    "6 张表、18 个接口",
    "约 3000 行后端、2300 行前端",
], size=15)
add_text(slide, 2.1, 5.45, 9.8, 0.6, "H2 让系统无需外部数据库即可启动；MySQL 建表脚本作为后续部署预留。",
         size=14, color=GRAY)
add_footer(slide, 4)

# 5 数据模型与进度
slide = prs.slides.add_slide(BLANK)
add_title(slide, "数据模型与进度计算", "不建阶段完成表：进度由业务数据实时推导")
add_text(slide, 0.9, 1.6, 5.0, 0.4, "6 张表", size=18, color=BLUE, bold=True)
add_bullets(slide, 0.9, 2.05, 5.2, 3.8, [
    "sys_user：用户与角色",
    "project：课题与学生/导师关系",
    "artifact：材料，追加版本不覆盖",
    "design_review：多轮评审",
    "weekly_report：课题 + 周次唯一",
    "repo_binding：仓库与同步状态",
], size=15)
add_rect(slide, 6.4, 1.65, 6.0, 4.55, LIGHT)
add_text(slide, 6.7, 1.85, 5.4, 0.4, "进度来源", size=18, color=BLUE, bold=True)
add_table(slide, 6.7, 2.35, 5.4, 3.55, [
    ["步骤", "完成依据"],
    ["需求 / 原型", "最新材料已通过"],
    ["设计评审", "前置材料 + 最新评审 PASS"],
    ["周报", "至少 1 篇已批阅"],
    ["代码仓库", "已绑定且同步成功"],
    ["测试报告", "最新报告已通过"],
], col_widths=[1.7, 3.7], font_size=13)
add_text(slide, 0.9, 6.25, 11.6, 0.5, "总览、详情、学生端调用同一进度服务，避免统计口径分裂。",
         size=14, color=ACCENT, bold=True)
add_footer(slide, 5)

# 6 学生端
slide = prs.slides.add_slide(BLANK)
add_title(slide, "学生端：六步进度与材料历史", "让学生清楚“现在在哪、下一步做什么、为什么被退回”")
add_bullets(slide, 0.9, 1.9, 4.5, 4.6, [
    "顶部六步进度，状态实时更新",
    "材料 / 设计评审 / 周报 / 仓库 / 测试报告五个 Tab",
    "文件或外链二选一，历史版本保留",
    "退回后查看评语并原地重交",
    "仓库同步失败可手工填写",
], size=15)
add_image_fit(slide, ROOT / "docs" / "screenshots" / "week6-student-complete.png", 5.7, 1.55, 6.9, 5.2)
add_footer(slide, 6)

# 7 导师总览
slide = prs.slides.add_slide(BLANK)
add_title(slide, "导师总览：一屏看到进度、待办与动态", "统计由后端批量聚合，六步矩阵复用统一进度服务")
add_bullets(slide, 0.9, 1.9, 4.5, 4.6, [
    "四张卡片：学生数、待审核、待批阅、平均完成率",
    "六步圆点矩阵 + 当前阶段 + 待办徽标",
    "关键词搜索、状态筛选、进度/待办排序",
    "详情抽屉完成材料审核、评审、周报批阅",
    "最近动态取四类业务时间最大值",
], size=15)
add_image_fit(slide, ROOT / "docs" / "screenshots" / "week5-advisor-overview.png", 5.7, 1.55, 6.9, 5.2)
add_footer(slide, 7)

# 8 安全与异常
slide = prs.slides.add_slide(BLANK)
add_title(slide, "安全边界与异常路径", "不越权、不信任前端、不暴露存储路径")
add_rect(slide, 0.9, 1.7, 5.6, 4.8, LIGHT)
add_text(slide, 1.2, 1.9, 5.0, 0.4, "安全设计", size=19, color=BLUE, bold=True)
add_bullets(slide, 1.2, 2.4, 5.0, 3.9, [
    "BCrypt 存储密码",
    "JWT 鉴权 + 角色路径隔离",
    "Service 层校验课题归属",
    "上传白名单、20MB、UUID 文件名",
    "下载鉴权与路径校验",
    "仓库 API 主机白名单，降低 SSRF 风险",
], size=14)
add_rect(slide, 6.9, 1.7, 5.6, 4.8, LIGHT)
add_text(slide, 7.2, 1.9, 5.0, 0.4, "异常路径", size=19, color=BLUE, bold=True)
add_table(slide, 7.2, 2.4, 5.0, 3.6, [
    ["异常", "处理"],
    ["重复提交", "400 + 明确提示"],
    ["退回未填评语", "前后端双重校验"],
    ["Token 过期", "401，回登录页"],
    ["仓库同步失败", "FAILED + 手工兜底"],
    ["统计越界", "400"],
], col_widths=[1.8, 3.2], font_size=13)
add_footer(slide, 8)

# 9 测试结果
slide = prs.slides.add_slide(BLANK)
add_title(slide, "测试与验证结果", "全部在本地复现，命令与脚本随仓库交付")
add_table(slide, 0.9, 1.75, 7.2, 3.4, [
    ["测试类型", "数量", "结果"],
    ["后端自动化测试", "36", "全部通过"],
    ["端到端接口检查", "42", "42/42"],
    ["手工验收场景", "8", "全部通过"],
    ["安全检查", "8 项", "通过"],
    ["页面运行错误", "2 个页面", "0"],
], col_widths=[3.4, 1.8, 2.0], font_size=14)
add_rect(slide, 8.5, 1.75, 4.0, 3.4, LIGHT)
add_text(slide, 8.8, 1.95, 3.4, 0.4, "覆盖重点", size=17, color=BLUE, bold=True)
add_bullets(slide, 8.8, 2.4, 3.4, 2.5, [
    "登录与 401/403",
    "跨导师数据隔离",
    "退回、重交、重复提交",
    "仓库兜底与统计校验",
], size=13)
add_text(slide, 0.9, 5.5, 11.6, 0.9, "线上部署因暂无服务器未执行；本地构建、启动与演示数据均可复现。",
         size=15, color=ACCENT, bold=True)
add_footer(slide, 9)

# 10 总结
slide = prs.slides.add_slide(BLANK)
add_title(slide, "总结与展望", "先冻结范围，把一致性做好，再考虑扩展")
add_rect(slide, 0.9, 1.75, 5.6, 4.55, LIGHT)
add_text(slide, 1.2, 1.95, 5.0, 0.4, "已完成", size=19, color=BLUE, bold=True)
add_bullets(slide, 1.2, 2.45, 5.0, 3.6, [
    "六步闭环 + 学生/导师双端",
    "统一进度计算 + 导师批量总览",
    "36 个测试、42 项端到端检查",
    "需求、设计、测试、手册、脚本齐全",
], size=14)
add_rect(slide, 6.9, 1.75, 5.6, 4.55, LIGHT)
add_text(slide, 7.2, 1.95, 5.0, 0.4, "边界与后续", size=19, color=BLUE, bold=True)
add_bullets(slide, 7.2, 2.45, 5.0, 3.6, [
    "暂无服务器，未做线上部署",
    "私有仓库需接入 OAuth",
    "文件存储未接对象存储",
    "未做消息通知与性能压测",
], size=14)
add_text(slide, 0.9, 6.45, 11.6, 0.4, "谢谢各位老师", size=20, color=ACCENT, bold=True, align=PP_ALIGN.CENTER)
add_footer(slide, 10)

OUT.parent.mkdir(parents=True, exist_ok=True)
prs.save(OUT)
print(f"saved {OUT}")
