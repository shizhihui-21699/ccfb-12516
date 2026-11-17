-- 种子数据：5 个部门、20 名员工。MERGE ... KEY(id) 保证重复启动不报错。
MERGE INTO departments (id, name) KEY(id) VALUES
 (1, '财务部'),
 (2, '人力资源部'),
 (3, '研发中心'),
 (4, '市场部'),
 (5, '行政部');

MERGE INTO employees (id, name, email, department_id) KEY(id) VALUES
 (1,  '王芳',  'wang.fang@corp.local',  1),
 (2,  '李强',  'li.qiang@corp.local',   1),
 (3,  '张敏',  'zhang.min@corp.local',  1),
 (4,  '陈杰',  'chen.jie@corp.local',   1),
 (5,  '刘洋',  'liu.yang@corp.local',   2),
 (6,  '杨丽',  'yang.li@corp.local',    2),
 (7,  '赵磊',  'zhao.lei@corp.local',   2),
 (8,  '黄静',  'huang.jing@corp.local', 2),
 (9,  '周涛',  'zhou.tao@corp.local',   3),
 (10, '吴婷',  'wu.ting@corp.local',    3),
 (11, '徐鹏',  'xu.peng@corp.local',    3),
 (12, '孙悦',  'sun.yue@corp.local',    3),
 (13, '马超',  'ma.chao@corp.local',    4),
 (14, '朱琳',  'zhu.lin@corp.local',    4),
 (15, '胡军',  'hu.jun@corp.local',     4),
 (16, '郭雪',  'guo.xue@corp.local',    4),
 (17, '何斌',  'he.bin@corp.local',     5),
 (18, '高燕',  'gao.yan@corp.local',    5),
 (19, '林峰',  'lin.feng@corp.local',   5),
 (20, '罗娜',  'luo.na@corp.local',     5);
