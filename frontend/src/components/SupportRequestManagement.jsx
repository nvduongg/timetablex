import React, { useState, useEffect } from 'react';
import { App, Table, Select, Tag, Alert } from 'antd';
import { UserOutlined } from '@ant-design/icons';
import * as SemesterService from '../services/semesterService';
import * as ClassSectionService from '../services/classSectionService';

/**
 * P.ĐT xem danh sách yêu cầu hỗ trợ GV (chưa phân công).
 * Khoa A gửi yêu cầu → chuyển Khoa quản lý chuyên môn (course.faculty) → Khoa đó phân công trong màn Phân công giảng dạy.
 */
const SupportRequestManagement = () => {
  const { message } = App.useApp();
  const [supportRequests, setSupportRequests] = useState([]);
  const [semesters, setSemesters] = useState([]);
  const [currentSemesterId, setCurrentSemesterId] = useState(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    SemesterService.getSemesters().then(res => {
      setSemesters(res.data || []);
      const active = (res.data || []).find(s => s.isActive);
      if (active) setCurrentSemesterId(active.id);
    });
  }, []);

  const fetchSupportRequests = async () => {
    if (!currentSemesterId) return;
    setLoading(true);
    try {
      const res = await ClassSectionService.getSupportRequests(currentSemesterId);
      setSupportRequests(res.data || []);
    } catch {
      setSupportRequests([]);
      message.error('Lỗi tải danh sách yêu cầu hỗ trợ');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchSupportRequests();
  }, [currentSemesterId]);

  const columns = [
    { title: 'Mã lớp HP', dataIndex: 'code', width: 130, render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span> },
    {
      title: 'Loại', dataIndex: 'sectionType', width: 80,
      render: t => <Tag color={t === 'LT' ? 'geekblue' : 'purple'}>{t}</Tag>
    },
    {
      title: 'Học phần',
      render: (_, r) => (
        <div>
          <div>{r.courseOffering?.course?.code} - {r.courseOffering?.course?.name}</div>
        </div>
      )
    },
    {
      title: 'Khoa yêu cầu',
      render: (_, r) => r.courseOffering?.faculty?.name || '—'
    },
    {
      title: 'Khoa xử lý',
      render: (_, r) => (
        <Tag color="blue">{r.courseOffering?.course?.faculty?.name || '—'}</Tag>
      )
    },
    {
      title: 'Ghi chú',
      dataIndex: 'supportRequestComment',
      ellipsis: true,
      render: t => t ? <span style={{ fontSize: 12, color: '#666' }}>{t}</span> : '—'
    }
  ];

  const currentSemester = semesters.find(s => s.id === currentSemesterId);

  return (
    <div style={{ width: '100%' }}>
      <div style={{ marginBottom: 20, display: 'flex', alignItems: 'center', gap: 12 }}>
        <Select
          variant="filled"
          style={{ width: 220 }}
          value={currentSemesterId}
          onChange={setCurrentSemesterId}
          placeholder="Chọn học kỳ"
          optionLabelProp="label"
        >
          {semesters.map(s => (
            <Select.Option key={s.id} value={s.id} label={s.name}>{s.name} {s.isActive && '(Hiện tại)'}</Select.Option>
          ))}
        </Select>
        <Tag color="orange">{supportRequests.length} yêu cầu chưa xử lý</Tag>
      </div>

      <Alert
        title="Quy trình yêu cầu hỗ trợ GV"
        description={
          <ul style={{ margin: 0, paddingLeft: 20 }}>
            <li>Khoa A (phụ trách kế hoạch) thiếu GV → gửi yêu cầu trong màn Phân công giảng dạy</li>
            <li>Hệ thống chuyển tới Khoa quản lý chuyên môn (course.faculty)</li>
            <li>Khoa đó xem trong màn Phân công giảng dạy và phân công GV của mình</li>
            <li>P.ĐT xem danh sách này để giám sát tiến độ</li>
          </ul>
        }
        type="info"
        showIcon
        icon={<UserOutlined />}
        style={{ marginBottom: 20, border: 'none', background: '#e6f7ff' }}
      />

      <Table
        dataSource={supportRequests}
        columns={columns}
        rowKey="id"
        loading={loading}
        size="middle"
        pagination={{ pageSize: 15, showTotal: (t) => `Tổng ${t} yêu cầu` }}
        locale={{ emptyText: currentSemesterId ? 'Không có yêu cầu hỗ trợ nào' : 'Chọn học kỳ' }}
      />
    </div>
  );
};

export default SupportRequestManagement;
