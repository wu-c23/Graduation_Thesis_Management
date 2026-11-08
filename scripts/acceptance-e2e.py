# 用途：对正在运行的后端执行端到端验收，共 42 项检查。
# 前置：使用全新 H2 数据库启动后端（种子账号 stu001 / stu002 / tea001），
#       脚本会真实写入材料、周报、仓库与测试报告，请勿在演示库上重复执行。
# 运行：python scripts/acceptance-e2e.py
import json
import urllib.error
import urllib.request
import uuid

BASE = 'http://localhost:8080/api'
checks = []


def check(name, condition, detail=''):
    checks.append((name, bool(condition), detail))
    print(('PASS ' if condition else 'FAIL ') + name + (f'  [{detail}]' if detail else ''))
    if not condition:
        raise AssertionError(name + ' ' + str(detail))


def call(method, path, token=None, body=None, content_type=None):
    headers = {}
    if token:
        headers['Authorization'] = 'Bearer ' + token
    if content_type:
        headers['Content-Type'] = content_type
    req = urllib.request.Request(BASE + path, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=25) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        raw = e.read().decode('utf-8')
        try:
            return e.code, json.loads(raw)
        except json.JSONDecodeError:
            return e.code, raw


def login(username, password='123456'):
    status, body = call('POST', '/auth/login', body=json.dumps({
        'username': username, 'password': password
    }).encode(), content_type='application/json')
    return status, body


def upload(token, mtype, content, filename, metrics=None):
    boundary = '----w5' + uuid.uuid4().hex
    parts = [
        ('--' + boundary + '\r\n').encode(),
        b'Content-Disposition: form-data; name="type"\r\n\r\n' + mtype.encode() + b'\r\n',
        ('--' + boundary + '\r\n').encode(),
        (f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n').encode(),
        b'Content-Type: application/octet-stream\r\n\r\n' + content + b'\r\n'
    ]
    for key, value in (metrics or {}).items():
        parts.extend([
            ('--' + boundary + '\r\n').encode(),
            f'Content-Disposition: form-data; name="{key}"\r\n\r\n'.encode(),
            str(value).encode() + b'\r\n'
        ])
    parts.append(('--' + boundary + '--\r\n').encode())
    status, body = call('POST', '/student/artifacts', token, b''.join(parts),
                        'multipart/form-data; boundary=' + boundary)
    assert status == 200, body
    return body['data']['id']


def review_artifact(token, artifact_id, status, feedback='', score=90):
    code, data = call('POST', f'/advisor/artifacts/{artifact_id}/review', token,
                      json.dumps({'status': status, 'feedback': feedback, 'score': score}).encode(),
                      'application/json')
    assert code == 200, data
    return data


def weekly(token, week_no, content):
    code, data = call('POST', '/student/weekly-reports', token,
                      json.dumps({'weekNo': week_no, 'content': content}).encode(),
                      'application/json')
    return code, data


def weekly_review(token, report_id, status, comment=''):
    code, data = call('POST', f'/advisor/weekly-reports/{report_id}/review', token,
                      json.dumps({'status': status, 'comment': comment}).encode(),
                      'application/json')
    assert code == 200, data
    return data['data']


def project(token):
    code, data = call('GET', '/student/project', token)
    assert code == 200, data
    return data['data']


def overview(token):
    code, data = call('GET', '/advisor/students', token)
    assert code == 200, data
    return data['data']


# 1. 登录与角色隔离
code, body = login('stu001')
check('学生登录成功', code == 200 and body['data']['user']['role'] == 'STUDENT')
stu = body['data']['token']
code, body = login('tea001')
check('导师登录成功', code == 200 and body['data']['user']['role'] == 'ADVISOR')
tea = body['data']['token']
code, body = login('stu001', 'wrong-password')
check('错误密码返回 401', code == 401 and body['message'] == '用户名或密码错误')
code, body = call('GET', '/advisor/students', stu)
check('学生访问导师接口返回 403', code == 403)
code, body = call('GET', '/student/project', tea)
check('导师访问学生接口返回 403', code == 403)

initial = overview(tea)
check('总览初始学生数为 2', initial['studentCount'] == 2, initial['studentCount'])
check('总览初始完成人数为 0', initial['completedStudentCount'] == 0)
check('总览初始平均完成率为 0', initial['averageCompletionRate'] == 0)
check('总览每名学生返回六步矩阵', len(initial['students'][0]['steps']) == 6)
check('总览初始最近动态为空', initial['students'][0]['lastActiveAt'] is None)

# 2. 需求：退回 → 重交 → 通过
req = upload(stu, 'REQUIREMENT', '需求说明书 v1'.encode('utf-8'), '需求说明书.pdf')
review_artifact(tea, req, 'RETURNED', '请补充逾期处理流程。')
state = project(stu)
check('需求退回后第一步为已退回', state['steps'][0]['status'] == 'RETURNED')
check('学生能看到退回原因', state['artifacts'][0]['feedback'] == '请补充逾期处理流程。')
req2 = upload(stu, 'REQUIREMENT', '需求说明书 v2'.encode('utf-8'), '需求说明书-v2.pdf')
check('需求重交生成新版本', req2 != req)
review_artifact(tea, req2, 'APPROVED', '需求完整，同意进入原型阶段。')
check('需求通过后第一步完成', project(stu)['steps'][0]['status'] == 'COMPLETED')

# 3. 原型与设计评审前置条件
proto = upload(stu, 'PROTOTYPE', b'PNG-DEMO', '图书管理系统原型.png')
code, body = call('POST', '/advisor/projects/1/design-review', tea,
                  json.dumps({'score': 90, 'result': 'PASS', 'comment': '提前评审'}).encode(),
                  'application/json')
check('原型未通过时设计评审被拒绝', code == 400
      and '需求文档与原型均通过后' in body['message'], body.get('message'))
review_artifact(tea, proto, 'APPROVED', '页面结构完整。')
code, body = call('POST', '/advisor/projects/1/design-review', tea,
                  json.dumps({'score': 93, 'result': 'PASS', 'comment': '设计合理，可以进入开发阶段。'}).encode(),
                  'application/json')
check('设计评审 PASS 成功', code == 200 and body['data']['result'] == 'PASS')
check('前三步完成', project(stu)['completedStepCount'] == 3)

# 4. 周报提交、重复提交与批阅
code, body = weekly(stu, 1, '完成需求分析与原型设计，确定三条核心流程。')
check('周报提交成功', code == 200 and body['data']['status'] == 'SUBMITTED')
report_id = body['data']['id']
code, body = weekly(stu, 1, '重复提交')
check('待批阅周报不可重复提交', code == 400 and '正在等待导师批阅' in body['message'])
weekly_review(tea, report_id, 'REVIEWED', '进展清晰，注意补充异常分支测试计划。')
state = project(stu)
check('周报批阅后第四步完成', state['steps'][3]['status'] == 'COMPLETED')
check('学生能看到周报批阅意见', state['weeklyReports'][0]['comment'].startswith('进展清晰'))

# 5. 仓库绑定与手工兜底
code, body = call('POST', '/student/repo-binding', stu,
                  json.dumps({'repoUrl': 'https://gitlab.com/demo/repo'}).encode(),
                  'application/json')
check('非 GitHub/Gitee 仓库被拒绝', code == 400 and 'GitHub' in body['message'])
code, body = call('POST', '/student/repo-binding', stu,
                  json.dumps({'repoUrl': 'https://github.com/wu-c23/Graduation_Thesis_Management.git'}).encode(),
                  'application/json')
check('仓库绑定并规范化地址', code == 200 and body['data']['repoHost'] == 'GITHUB')
code, body = call('POST', '/student/repo-binding/manual-count', stu,
                  json.dumps({'commitCount': 42, 'latestCommitAt': '2026-10-20T10:00:00'}).encode(),
                  'application/json')
check('手工填写提交数成功', code == 200 and body['data']['syncStatus'] == 'SUCCESS')
check('第五步完成', project(stu)['steps'][4]['status'] == 'COMPLETED')

# 6. 测试报告：退回 → 重交 → 通过
report = upload(stu, 'TEST_REPORT', '测试报告 v1'.encode('utf-8'), '测试报告.pdf',
                {'testCaseCount': 48, 'testPassRate': 96, 'defectCount': 3})
review_artifact(tea, report, 'RETURNED', '请补充边界用例并更新缺陷统计。')
state = project(stu)
check('测试报告退回后第六步为已退回', state['steps'][5]['status'] == 'RETURNED')
report2 = upload(stu, 'TEST_REPORT', '测试报告 v2'.encode('utf-8'), '测试报告-v2.pdf',
                 {'testCaseCount': 50, 'testPassRate': 98, 'defectCount': 2})
review_artifact(tea, report2, 'APPROVED', '测试覆盖充分，缺陷已闭环。')
state = project(stu)
check('测试统计保存正确', state['artifacts'][0]['testCaseCount'] == 50
      and state['artifacts'][0]['testPassRate'] == 98)
check('第六步完成', state['steps'][5]['status'] == 'COMPLETED')
check('六步全部完成', state['completedStepCount'] == 6 and state['allCompleted'] is True)

# 7. 异常校验
code, body = call('POST', '/student/artifacts', stu,
                  b'--x\r\nContent-Disposition: form-data; name="type"\r\n\r\nTEST_REPORT\r\n'
                  b'--x\r\nContent-Disposition: form-data; name="testPassRate"\r\n\r\n101\r\n'
                  b'--x\r\nContent-Disposition: form-data; name="file"; filename="bad.pdf"\r\n'
                  b'Content-Type: application/pdf\r\n\r\nx\r\n--x--\r\n',
                  'multipart/form-data; boundary=x')
check('通过率越界被拒绝', code == 400 and '0–100' in body['message'], body.get('message'))

# 8. 新周报不使已完成步骤回退，并反映到总览待办
code, body = weekly(stu, 2, '完成核心接口与测试数据准备。')
week2_id = body['data']['id']
state = project(stu)
check('新周报不使第四步回退', state['steps'][3]['status'] == 'COMPLETED')
overview_pending = overview(tea)
check('总览待批阅周报为 1', overview_pending['pendingWeeklyReportCount'] == 1)
check('总览待办学生数为 1', overview_pending['pendingStudentCount'] == 1)
weekly_review(tea, week2_id, 'REVIEWED', '已批阅，按计划推进。')
overview_done = overview(tea)
check('批阅后总览待办清零', overview_done['pendingWeeklyReportCount'] == 0
      and overview_done['pendingStudentCount'] == 0)
check('总览完成人数为 1', overview_done['completedStudentCount'] == 1)
check('总览平均完成率为 50%', overview_done['averageCompletionRate'] == 50,
      overview_done['averageCompletionRate'])
check('总览第一名学生为 6/6', overview_done['students'][0]['completedStepCount'] == 6)
check('总览第一名学生最近动态非空', overview_done['students'][0]['lastActiveAt'] is not None)
check('总览第二名学生仍为 0/6', overview_done['students'][1]['completedStepCount'] == 0)

detail = call('GET', '/advisor/students/1', tea)[1]['data']
check('导师详情含 2 篇周报', len(detail['weeklyReports']) == 2)
check('导师详情含仓库与测试报告', detail['repoBinding']['commitCount'] == 42
      and len(detail['artifacts']) == 5)
check('导师详情六步完成', detail['completedStepCount'] == 6)

print()
print(f'{sum(1 for _, ok, _ in checks if ok)}/{len(checks)} checks passed')
