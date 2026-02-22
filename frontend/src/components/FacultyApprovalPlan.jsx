import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Select, message, Tag, Space, Alert, Input, Flex, Typography } from 'antd';
import { CheckOutlined, CloseOutlined, FilterOutlined } from '@ant-design/icons';
import * as SemesterService from '../services/semesterService';
import * as OfferingService from '../services/offeringService';
import * as FacultyService from '../services/facultyService';

const { Option } = Select;
const { Text } = Typography;
const { TextArea } = Input;

/**
 * Trang cho Khoa/Viện: Xem danh sách học phần dự kiến P.ĐT đã gửi, Xác nhận hoặc Từ chối (kèm ghi chú).
 * Ràng buộc: hoàn tất trong 03 ngày làm việc sau khi P.ĐT gửi.
 * Khi đăng nhập Khoa: tự động chọn khoa của user, không cần dropdown.
 */
const FacultyApprovalPlan = ({ auth }) => {
    const isFacultyUser = auth?.role === 'FACULTY';
    const userFacultyId = auth?.facultyId || null;

    const [semesters, setSemesters] = useState([]);
    const [faculties, setFaculties] = useState([]);
    const [currentSemesterId, setCurrentSemesterId] = useState(null);
    const [currentFacultyId, setCurrentFacultyId] = useState(userFacultyId);
    const [offerings, setOfferings] = useState([]);
    const [loading, setLoading] = useState(false);
    const [rejectModal, setRejectModal] = useState({ open: false, offering: null });
    const [form] = Form.useForm();

    useEffect(() => {
        SemesterService.getSemesters().then(res => {
            setSemesters(res.data || []);
            const active = (res.data || []).find(s => s.isActive);
            if (active) setCurrentSemesterId(active.id);
        });
        FacultyService.getFaculties().then(res => setFaculties(res.data || []));
    }, []);

    const fetchOfferings = async () => {
        if (!currentSemesterId || !currentFacultyId) {
            setOfferings([]);
            return;
        }
        setLoading(true);
        try {
            const res = await OfferingService.getOfferingsBySemester(currentSemesterId, {
                facultyId: currentFacultyId,
                status: 'WAITING_APPROVAL'
            });
            setOfferings(res.data || []);
        } catch (e) {
            message.error('Lỗi tải danh sách');
            setOfferings([]);
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchOfferings(); }, [currentSemesterId, currentFacultyId]);

    const handleApprove = async (offering) => {
        try {
            await OfferingService.updateOfferingStatus(offering.id, 'APPROVED');
            message.success('Đã xác nhận học phần: ' + (offering.course?.name || offering.id));
            fetchOfferings();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi xác nhận');
        }
    };

    const handleRejectOpen = (offering) => {
        setRejectModal({ open: true, offering });
        form.setFieldsValue({ rejectionComment: '' });
    };

    const handleRejectSubmit = async () => {
        const values = await form.validateFields().catch(() => null);
        if (!values || !rejectModal.offering) return;
        try {
            await OfferingService.updateOfferingStatus(
                rejectModal.offering.id,
                'REJECTED',
                values.rejectionComment || undefined
            );
            message.success('Đã gửi phản hồi từ chối / yêu cầu chỉnh sửa');
            setRejectModal({ open: false, offering: null });
            fetchOfferings();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi gửi phản hồi');
        }
    };

    const columns = [
        { title: 'Mã HP', dataIndex: ['course', 'code'], width: 100, render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span> },
        { title: 'Tên môn học', dataIndex: ['course', 'name'], render: t => <span style={{ fontWeight: 500 }}>{t}</span> },
        { title: 'Nhu cầu (SV)', dataIndex: 'studentDemand', align: 'center', width: 100 },
        { title: 'Lớp LT', dataIndex: 'theoryClassCount', align: 'center', width: 80, render: v => v ?? '—' },
        { title: 'Lớp TH', dataIndex: 'practiceClassCount', align: 'center', width: 80, render: v => v ?? '—' },
        {
            title: 'Ngày P.ĐT gửi',
            dataIndex: 'submittedAt',
            width: 120,
            render: t => t ? new Date(t).toLocaleDateString('vi-VN') : '—'
        },
        {
            title: 'Thao tác',
            key: 'action',
            width: 180,
            align: 'right',
            render: (_, r) => (
                <Space>
                    <Button type="primary" size="small" icon={<CheckOutlined />} onClick={() => handleApprove(r)}>
                        Xác nhận
                    </Button>
                    <Button size="small" danger icon={<CloseOutlined />} onClick={() => handleRejectOpen(r)}>
                        Từ chối / Yêu cầu sửa
                    </Button>
                </Space>
            )
        }
    ];

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 24, flexWrap: 'wrap', gap: 12 }}>
                <Space wrap>
                    <span style={{ fontWeight: 500, color: '#666' }}><FilterOutlined /> Học kỳ:</span>
                    <Select
                        variant="filled"
                        style={{ width: 180 }}
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
                    {!isFacultyUser && (
                        <>
                            <span style={{ fontWeight: 500, color: '#666', marginLeft: 8 }}>Khoa/Viện:</span>
                            <Select
                                variant="filled"
                                style={{ width: 'auto' }}
                                value={currentFacultyId}
                                onChange={setCurrentFacultyId}
                                placeholder="Chọn Khoa"
                            >
                                {faculties.map(f => (
                                    <Option key={f.id} value={f.id}>{f.name} ({f.code})</Option>
                                ))}
                            </Select>
                        </>
                    )}
                </Space>
            </div>

            <Alert
                title="Quy trình duyệt cần hoàn tất trong vòng 03 ngày làm việc sau khi P.ĐT gửi danh sách."
                type="info"
                showIcon
                style={{ marginBottom: 16, border: 'none', background: '#e6f7ff' }}
            />

            <Table
                dataSource={offerings}
                columns={columns}
                rowKey="id"
                loading={loading}
                pagination={{ pageSize: 10, placement: 'bottomRight' }}
                locale={{ emptyText: currentFacultyId ? 'Không có học phần nào chờ duyệt.' : (isFacultyUser ? 'Chọn học kỳ để xem danh sách.' : 'Chọn Khoa để xem danh sách P.ĐT gửi duyệt.') }}
            />

            <Modal
                title="Phản hồi / Yêu cầu chỉnh sửa"
                open={rejectModal.open}
                onCancel={() => setRejectModal({ open: false, offering: null })}
                onOk={handleRejectSubmit}
                okText="Gửi phản hồi"
                cancelText="Hủy"
                destroyOnHidden
            >
                <p style={{ color: '#666', marginBottom: 12 }}>
                    Nếu không đồng ý với sĩ số hoặc danh sách, vui lòng nêu lý do / yêu cầu chỉnh sửa để P.ĐT xử lý.
                </p>
                <Form form={form} layout="vertical">
                    <Form.Item name="rejectionComment" label="Ghi chú / Lý do từ chối" rules={[{ required: true, message: 'Vui lòng nhập ghi chú' }]}>
                        <TextArea rows={4} placeholder="Nhập lý do từ chối hoặc yêu cầu chỉnh sửa..." />
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default FacultyApprovalPlan;
