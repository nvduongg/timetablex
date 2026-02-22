import React, { useState, useEffect } from 'react';
import { App, Table, Select, Tag, Space, Alert, Flex, Typography } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import * as SemesterService from '../services/semesterService';
import * as ClassSectionService from '../services/classSectionService';

const { Option } = Select;
const { Text } = Typography;

/**
 * Quản lý yêu cầu hỗ trợ GV - Dành cho P.ĐT
 * Xem danh sách các lớp học phần cần hỗ trợ GV. Hệ thống tự động chuyển yêu cầu về Khoa quản lý chuyên môn để phân công.
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

    useEffect(() => {
        if (currentSemesterId) {
            fetchSupportRequests();
        }
    }, [currentSemesterId]);

    const fetchSupportRequests = async () => {
        if (!currentSemesterId) return;
        setLoading(true);
        try {
            const res = await ClassSectionService.getSupportRequests(currentSemesterId);
            setSupportRequests(res.data || []);
        } catch (e) {
            message.error('Lỗi tải danh sách yêu cầu hỗ trợ');
            setSupportRequests([]);
        } finally {
            setLoading(false);
        }
    };


    const columns = [
        {
            title: 'Mã lớp HP',
            dataIndex: 'code',
            width: 130,
            render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span>
        },
        {
            title: 'Loại',
            dataIndex: 'sectionType',
            width: 90,
            render: t => <Tag color={t === 'LT' ? 'geekblue' : 'purple'} style={{ border: 'none' }}>{t === 'LT' ? 'LT' : 'TH'}</Tag>
        },
        {
            title: 'Học phần',
            key: 'course',
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <span style={{ fontWeight: 500 }}>{r.courseOffering?.course?.code || '—'}</span>
                    <span style={{ fontSize: 12, color: '#666' }}>{r.courseOffering?.course?.name || '—'}</span>
                </Space>
            )
        },
        {
            title: 'Khoa yêu cầu',
            key: 'requestingFaculty',
            render: (_, r) => (
                <Tag color="blue">{r.courseOffering?.faculty?.name || '—'}</Tag>
            )
        },
        {
            title: 'Khoa quản lý chuyên môn',
            key: 'managingFaculty',
            render: (_, r) => {
                const managingFaculty = r.courseOffering?.course?.faculty;
                return managingFaculty ? (
                    <Tag color="green">{managingFaculty.name}</Tag>
                ) : (
                    <Tag color="default">—</Tag>
                );
            }
        },
        {
            title: 'Ghi chú',
            dataIndex: 'supportRequestComment',
            render: (text) => text ? (
                <span style={{ fontSize: 12, color: '#666', fontStyle: 'italic' }}>{text}</span>
            ) : '—'
        },
        {
            title: 'Trạng thái',
            key: 'status',
            width: 120,
            render: (_, r) => {
                if (r.lecturer) {
                    return <Tag color="green">Đã phân công</Tag>;
                }
                return <Tag color="orange">Đang chờ Khoa chuyên môn</Tag>;
            }
        }
    ];

    const currentSemester = semesters.find(s => s.id === currentSemesterId);

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24 }}>
                <Space>
                    <Select
                        variant="filled"
                        style={{ minWidth: 200 }}
                        value={currentSemesterId}
                        onChange={setCurrentSemesterId}
                        placeholder="Học kỳ"
                        optionLabelProp="label"
                    >
                        {semesters.map(s => (
                            <Option key={s.id} value={s.id} label={s.name}>
                                <Flex justify="space-between" align="center" style={{ width: '100%' }}>
                                    <Text>{s.name}</Text>
                                    {s.isActive && (
                                        <Text style={{ fontSize: 11, color: '#52c41a', display: 'flex', alignItems: 'center', gap: 4 }}>
                                            <span style={{ fontSize: 14 }}>●</span> Hiện tại
                                        </Text>
                                    )}
                                </Flex>
                            </Option>
                        ))}
                    </Select>
                </Space>
            </div>

            <Alert
                title="Quản lý yêu cầu hỗ trợ Giảng viên"
                description={
                    <div>
                        <div style={{ marginBottom: 4 }}>
                            Danh sách các lớp học phần mà Khoa/Viện không có giảng viên dạy được.
                        </div>
                        <div style={{ fontSize: 12, color: '#666', marginTop: 4 }}>
                            <strong>Quy trình:</strong> Khi Khoa yêu cầu hỗ trợ, hệ thống tự động gửi thông báo đến{' '}
                            <strong>Khoa quản lý chuyên môn</strong> của học phần. Khoa quản lý chuyên môn sẽ vào Module 3 để phân công giảng viên phù hợp.
                        </div>
                    </div>
                }
                type="info"
                showIcon
                icon={<ExclamationCircleOutlined />}
                style={{ marginBottom: 20, border: 'none', background: '#e6f7ff' }}
            />

            <Table
                dataSource={supportRequests}
                columns={columns}
                rowKey="id"
                loading={loading}
                size="middle"
                pagination={{ pageSize: 10, placement: 'bottomRight', showTotal: (t) => `Tổng ${t} yêu cầu` }}
            />
        </div>
    );
};

export default SupportRequestManagement;
