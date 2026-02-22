import React, { useState, useEffect } from 'react';
import { App, Table, Button, Modal, Form, Select, InputNumber, Tag, Space, Row, Col, Tooltip, Alert, Upload, Flex, Typography } from 'antd';
import {
    RobotOutlined,
    SendOutlined,
    SyncOutlined,
    FilterOutlined,
    UploadOutlined,
    DownloadOutlined,
    EditOutlined
} from '@ant-design/icons';
import * as SemesterService from '../services/semesterService';
import * as OfferingService from '../services/offeringService';
import * as CurriculumService from '../services/curriculumService';

const { Option } = Select;
const { Text } = Typography;

const CourseOfferingManagement = () => {
    const { message } = App.useApp();
    const [offerings, setOfferings] = useState([]);
    const [semesters, setSemesters] = useState([]);
    const [currentSemesterId, setCurrentSemesterId] = useState(null);
    const [cohorts, setCohorts] = useState([]);
    const [statusFilter, setStatusFilter] = useState('ALL');
    const [editModal, setEditModal] = useState({ open: false, record: null });
    const [isGenerateModalOpen, setIsGenerateModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [selectedRowKeys, setSelectedRowKeys] = useState([]);
    const [form] = Form.useForm();
    const [editForm] = Form.useForm();

    const normStatus = (s) => String(s || '').trim().toUpperCase();
    // Có thể gửi duyệt: chỉ DRAFT hoặc REJECTED
    const isOfferSendable = (o) => {
        const s = normStatus(o?.status);
        return s === 'DRAFT' || s === 'REJECTED';
    };
    // Có thể chỉnh sửa: tất cả trừ APPROVED (kể cả WAITING_APPROVAL để thu hồi và gửi lại)
    const isOfferEditable = (o) => {
        const s = normStatus(o?.status);
        return s !== 'APPROVED';
    };

    useEffect(() => {
        SemesterService.getSemesters().then(res => {
            setSemesters(res.data);
            const active = res.data.find(s => s.isActive);
            if (active) setCurrentSemesterId(active.id);
        });
        CurriculumService.getCohorts().then(res => {
            setCohorts(res.data);
        });
    }, []);

    const fetchOfferings = async () => {
        if (!currentSemesterId) return;
        setLoading(true);
        try {
            const res = await OfferingService.getOfferingsBySemester(currentSemesterId);
            setOfferings(res.data || []);
        } catch {
            message.error('Lỗi tải dữ liệu kế hoạch');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchOfferings(); }, [currentSemesterId]);

    const handleSendForApproval = async () => {
        if (!currentSemesterId) {
            message.warning('Vui lòng chọn học kỳ');
            return;
        }
        const candidates = offerings.filter(isOfferSendable);
        const raw = selectedRowKeys.length > 0 ? selectedRowKeys : candidates.map(o => o.id);
        const draftIds = raw.map(x => Number(x)).filter(id => !isNaN(id) && id > 0);
        if (draftIds.length === 0) {
            message.info('Không có học phần nào ở trạng thái Nháp / Khoa yêu cầu chỉnh sửa để gửi duyệt');
            return;
        }
        setLoading(true);
        try {
            await OfferingService.sendForApproval(currentSemesterId, draftIds);
            message.success('Đã gửi yêu cầu duyệt. Khoa/Viện sẽ nhận thông báo và xác nhận trong vòng 03 ngày làm việc.');
            setSelectedRowKeys([]);
            fetchOfferings();
        } catch (e) {
            const errMsg = e?.response?.data?.message || e?.response?.data?.error || e?.message || 'Lỗi gửi yêu cầu duyệt';
            message.error(typeof errMsg === 'string' ? errMsg : 'Lỗi gửi yêu cầu duyệt');
        } finally {
            setLoading(false);
        }
    };

    const handleDownloadTemplate = async () => {
        try {
            const res = await OfferingService.downloadOfferingsTemplate();
            const url = window.URL.createObjectURL(new Blob([res.data]));
            const a = document.createElement('a');
            a.href = url;
            a.download = 'BM.DT.01.01_Danh_sach_hoc_phan_du_kien.xlsx';
            a.click();
            window.URL.revokeObjectURL(url);
            message.success('Đã tải file mẫu');
        } catch {
            message.error('Lỗi tải mẫu');
        }
    };

    const handleUpload = async ({ file, onSuccess }) => {
        if (!currentSemesterId) {
            message.warning('Vui lòng chọn học kỳ trước khi import');
            onSuccess();
            return;
        }
        setUploading(true);
        try {
            await OfferingService.importOfferingsExcel(currentSemesterId, file);
            message.success('Import danh sách học phần dự kiến thành công');
            fetchOfferings();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi import file');
        } finally {
            setUploading(false);
            onSuccess();
        }
    };

    const handleGenerate = async (values) => {
        setLoading(true);
        try {
            await OfferingService.generateAutomatedPlan({
                semesterId: currentSemesterId,
                cohort: values.cohort,
                semesterIndex: values.index
            });
            message.success('Hệ thống đã tính toán xong kế hoạch!');
            setIsGenerateModalOpen(false);
            fetchOfferings();
        } catch {
            message.error('Lỗi khi chạy thuật toán gợi ý');
        } finally {
            setLoading(false);
        }
    };

    const openEditModal = (record) => {
        setEditModal({ open: true, record });
        editForm.setFieldsValue({
            studentDemand: record.studentDemand,
            theoryClassCount: record.theoryClassCount,
            practiceClassCount: record.practiceClassCount
        });
    };

    const handleSaveEdit = async () => {
        if (!editModal.record) return;
        const values = await editForm.validateFields().catch(() => null);
        if (!values) return;
        setLoading(true);
        try {
            await OfferingService.updateOfferingPlan(editModal.record.id, values);
            message.success('Đã cập nhật kế hoạch học phần');
            setEditModal({ open: false, record: null });
            fetchOfferings();
        } catch (e) {
            message.error(e?.response?.data?.message || e?.response?.data || 'Lỗi cập nhật kế hoạch');
        } finally {
            setLoading(false);
        }
    };

    const columns = [
        {
            title: 'Mã HP', dataIndex: ['course', 'code'], width: 100,
            render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span>
        },
        {
            title: 'Tên môn học', dataIndex: ['course', 'name'],
            render: t => <span style={{ fontWeight: 500 }}>{t}</span>
        },
        {
            title: 'Khoa phụ trách', dataIndex: ['faculty', 'name'],
            render: t => <Tag style={{ border: 'none', background: '#f5f5f5', color: '#666' }}>{t}</Tag>
        },
        {
            title: 'Nhu cầu (SV)', dataIndex: 'studentDemand',
            align: 'center', width: 120,
            render: t => <span style={{ fontWeight: 600 }}>{t}</span>
        },
        {
            title: 'Lớp LT', dataIndex: 'theoryClassCount',
            align: 'center', width: 90,
            render: (val) => val > 0 ? (
                <Tag color="geekblue" style={{ fontSize: 13, border: 'none' }}>
                    {val}
                </Tag>
            ) : <span style={{ color: '#ccc' }}>-</span>
        },
        {
            title: 'Lớp TH', dataIndex: 'practiceClassCount',
            align: 'center', width: 90,
            render: (val) => val > 0 ? (
                <Tag color="purple" style={{ fontSize: 13, border: 'none' }}>
                    {val}
                </Tag>
            ) : <span style={{ color: '#ccc' }}>-</span>
        },
        {
            title: 'Trạng thái', dataIndex: 'status', width: 160,
            render: (status, r) => {
                const s = normStatus(status);
                let color = 'default';
                let label = 'Nháp';
                if (s === 'WAITING_APPROVAL') { color = 'orange'; label = 'Chờ Khoa duyệt'; }
                else if (s === 'APPROVED') { color = 'green'; label = 'Khoa đã chốt'; }
                else if (s === 'REJECTED') { color = 'red'; label = 'Khoa yêu cầu chỉnh sửa'; }

                return (
                    <Space orientation="vertical" size={2}>
                        <Tag color={color} style={{ border: 'none', fontWeight: 500 }}>
                            {label}
                        </Tag>
                        {r.rejectionComment && (
                            <Tooltip title={r.rejectionComment}>
                                <span style={{ fontSize: 11, color: '#ff4d4f' }}>
                                    Ghi chú: {r.rejectionComment.slice(0, 40)}{r.rejectionComment.length > 40 ? '…' : ''}
                                </span>
                            </Tooltip>
                        )}
                    </Space>
                );
            }
        },
        {
            title: 'Ngày gửi', dataIndex: 'submittedAt', width: 110,
            render: (t) => t ? new Date(t).toLocaleDateString('vi-VN') : '—'
        },
        {
            title: 'Thao tác', key: 'action', width: 80, align: 'right',
            render: (_, r) =>
                isOfferEditable(r) ? (
                    <Tooltip title={
                        normStatus(r?.status) === 'WAITING_APPROVAL'
                            ? 'Chỉnh sửa và thu hồi về Nháp để gửi duyệt lại'
                            : 'Chỉnh sửa số lớp / nhu cầu'
                    }>
                        <Button
                            type="text"
                            icon={<EditOutlined />}
                            onClick={() => openEditModal(r)}
                        />
                    </Tooltip>
                ) : null
        }
    ];

    const rowSelection = {
        selectedRowKeys,
        onChange: setSelectedRowKeys,
        getCheckboxProps: (record) => ({ disabled: !isOfferSendable(record) })
    };

    const filteredOfferings = offerings.filter(o => {
        if (statusFilter === 'ALL') return true;
        return normStatus(o.status) === statusFilter;
    });

    return (
        <div style={{ width: '100%' }}>
            <div
                style={{
                    display: 'flex',
                    justifyContent: 'space-between',
                    alignItems: 'center',
                    gap: 16,
                    marginBottom: 24,
                    flexWrap: 'nowrap',
                    minWidth: 0
                }}
            >
                <Space
                    size={8}
                    style={{ display: 'flex', alignItems: 'center', minWidth: 0, flex: 1 }}
                >
                    <FilterOutlined style={{ color: '#666' }} />
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

                    <Select
                        variant="filled"
                        style={{ minWidth: 180 }}
                        value={statusFilter}
                        onChange={setStatusFilter}
                        placeholder="Trạng thái"
                    >
                        <Option value="ALL">Tất cả</Option>
                        <Option value="DRAFT">Nháp</Option>
                        <Option value="WAITING_APPROVAL">Chờ Khoa duyệt</Option>
                        <Option value="REJECTED">Khoa yêu cầu chỉnh sửa</Option>
                        <Option value="APPROVED">Khoa đã chốt</Option>
                    </Select>
                </Space>

                <Space size={8} wrap={false} style={{ flexShrink: 0 }}>
                    <Space.Compact>
                        <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
                            BM.ĐT.01.01
                        </Button>
                        <Upload accept=".xlsx,.xls" showUploadList={false} customRequest={handleUpload}>
                            <Button icon={<UploadOutlined />} loading={uploading}>
                                Import
                            </Button>
                        </Upload>
                    </Space.Compact>
                    <Space.Compact>
                        <Button
                            type="primary"
                            icon={<SendOutlined />}
                            onClick={() => handleSendForApproval()}
                            loading={loading}
                            disabled={
                                !currentSemesterId ||
                                !offerings.some(isOfferSendable)
                            }
                        >
                            Gửi duyệt
                        </Button>
                        <Button type="primary" icon={<RobotOutlined />} onClick={() => setIsGenerateModalOpen(true)}>
                            Gợi ý
                        </Button>
                    </Space.Compact>
                </Space>
            </div>

            <Alert
                title="Module 1: Lập kế hoạch & Dự kiến"
                description="P.ĐT nhập/import danh sách học phần dự kiến (BM.ĐT.01.01), thiết lập số lớp LT/TH, rồi gửi cho Khoa/Viện xác nhận trong vòng 03 ngày làm việc."
                type="info"
                showIcon
                style={{ marginBottom: 20, border: 'none', background: '#e6f7ff' }}
            />

            <Table
                rowSelection={rowSelection}
                dataSource={filteredOfferings}
                columns={columns}
                rowKey="id"
                loading={loading}
                size="middle"
                pagination={{ pageSize: 10, placement: 'bottomRight' }}
            />

            <Modal
                title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <RobotOutlined style={{ color: '#005a8d' }} />
                        <span>Cấu hình Gợi ý Tự động</span>
                    </div>
                }
                open={isGenerateModalOpen}
                onCancel={() => setIsGenerateModalOpen(false)}
                footer={null}
                width={500}
                centered
            >
                <div style={{ marginBottom: 20 }}>
                    <Alert
                        title="Hệ thống sẽ quét toàn bộ dữ liệu Sinh viên và Khung CTĐT để tính toán nhu cầu mở lớp dự kiến."
                        type="info"
                        showIcon
                        style={{ border: 'none', background: '#e6f7ff' }}
                    />
                </div>

                <Form form={form} layout="vertical" onFinish={handleGenerate}>
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item name="cohort" label="Áp dụng cho Khóa" rules={[{ required: true }]}>
                                <Select placeholder="Chọn khóa" loading={cohorts.length === 0}>
                                    {cohorts.map(k => (
                                        <Option key={k} value={k}>{k}</Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item name="index" label="Học kỳ thứ (trong CTĐT)" rules={[{ required: true, message: 'Nhập số' }]}>
                                <InputNumber min={1} max={12} style={{ width: '100%' }} placeholder="VD: 1" variant="filled" />
                            </Form.Item>
                        </Col>
                    </Row>

                    <div style={{ textAlign: 'right', marginTop: 16 }}>
                        <Button onClick={() => setIsGenerateModalOpen(false)} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit" icon={<SyncOutlined spin={loading} />} loading={loading}>
                            Bắt đầu quét & Tính toán
                        </Button>
                    </div>
                </Form>
            </Modal>

            <Modal
                title="Chỉnh sửa kế hoạch học phần"
                open={editModal.open}
                onCancel={() => setEditModal({ open: false, record: null })}
                onOk={handleSaveEdit}
                okText="Lưu thay đổi"
                cancelText="Hủy"
                confirmLoading={loading}
                width={420}
                centered
            >
                <Form form={editForm} layout="vertical" style={{ marginTop: 8 }}>
                    <Form.Item label="Nhu cầu sinh viên (SV)" name="studentDemand" rules={[{ required: true, message: 'Nhập nhu cầu sinh viên' }]}>
                        <InputNumber min={0} style={{ width: '100%' }} />
                    </Form.Item>
                    <Row gutter={16}>
                        <Col span={12}>
                            <Form.Item label="Số lớp Lý thuyết" name="theoryClassCount" rules={[{ required: true, message: 'Nhập số lớp LT' }]}>
                                <InputNumber min={0} style={{ width: '100%' }} />
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item label="Số lớp Thực hành" name="practiceClassCount" rules={[{ required: true, message: 'Nhập số lớp TH' }]}>
                                <InputNumber min={0} style={{ width: '100%' }} />
                            </Form.Item>
                        </Col>
                    </Row>
                </Form>
            </Modal>
        </div>
    );
};

export default CourseOfferingManagement;
