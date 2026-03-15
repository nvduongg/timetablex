import React, { useState, useEffect } from 'react';
import { Button, Table, Modal, Form, Select, Input, InputNumber, Row, Col, message, Typography, Flex, Tag } from 'antd';
import { PlusOutlined, SearchOutlined, ReadOutlined } from '@ant-design/icons';
import * as MajorService from '../services/majorService';
import * as CurriculumService from '../services/curriculumService';
import * as CourseService from '../services/courseService';
import * as CohortService from '../services/cohortService';
import RoadmapManagement from './RoadmapManagement';

const { Text } = Typography;
const { Option } = Select;

const CurriculumManagement = () => {
    const [viewMode, setViewMode] = useState('list'); // 'list' | 'roadmap'
    const [roadmapCurrId, setRoadmapCurrId] = useState(null);
    const [curriculums, setCurriculums] = useState([]);
    const [majors, setMajors] = useState([]);
    const [courses, setCourses] = useState([]);
    const [cohorts, setCohorts] = useState([]);
    const [searchText, setSearchText] = useState('');
    const [isCreateModalOpen, setIsCreateModalOpen] = useState(false);
    const [form] = Form.useForm();

    const fetchInitData = async () => {
        const [currRes, majorRes, courseRes, cohortRes] = await Promise.allSettled([
            CurriculumService.getCurriculums(),
            MajorService.getMajors(),
            CourseService.getCourses(),
            CohortService.getActiveCohorts()
        ]);
        if (currRes.status === 'fulfilled')
            setCurriculums(Array.isArray(currRes.value?.data) ? currRes.value.data : []);
        else message.error('Lỗi tải danh sách CTĐT');
        if (majorRes.status === 'fulfilled')
            setMajors(Array.isArray(majorRes.value?.data) ? majorRes.value.data : []);
        else message.error('Lỗi tải danh sách Ngành');
        if (courseRes.status === 'fulfilled')
            setCourses(Array.isArray(courseRes.value?.data) ? courseRes.value.data : []);
        else message.error('Lỗi tải danh sách môn học.');
        if (cohortRes.status === 'fulfilled')
            setCohorts(Array.isArray(cohortRes.value?.data) ? cohortRes.value.data : []);
        else message.error('Lỗi tải danh sách Niên khóa.');
    };

    useEffect(() => {
        fetchInitData();
    }, []);

    const handleCreate = async (values) => {
        const selectedCohort = cohorts.find(c => c.id === values.cohortId);
        const payload = {
            ...values,
            major: { id: values.majorId },
            cohortRef: values.cohortId ? { id: values.cohortId } : undefined,
            cohort: values.cohort || selectedCohort?.code,
            admissionYear: values.admissionYear || selectedCohort?.admissionYear,
        };
        try {
            await CurriculumService.createCurriculum(payload);
            message.success('Tạo khung CTĐT thành công');
            setIsCreateModalOpen(false);
            form.resetFields();
            fetchInitData();
        } catch (e) {
            message.error('Lỗi tạo khung');
        }
    };

    const filteredCurriculums = curriculums.filter(c =>
        (c.name || '').toLowerCase().includes(searchText.toLowerCase())
    );

    const columns = [
        {
            title: 'Tên CTĐT',
            dataIndex: 'name',
            ellipsis: { showTitle: false },
            render: (text, record) => (
                <Flex vertical gap={2} style={{ minWidth: 0 }}>
                    <Text strong ellipsis>{text}</Text>
                    <Text type="secondary" style={{ fontSize: 12 }} ellipsis>
                        Khóa {record.cohort}
                        {record.admissionYear ? ` (Nhập học ${record.admissionYear})` : ''}
                        {' • '}{record.major?.name}
                    </Text>
                </Flex>
            )
        },
        {
            title: 'Số môn',
            dataIndex: 'details',
            align: 'center',
            width: 80,
            render: (details) => <Tag color="blue" style={{ border: 'none', borderRadius: 4 }}>{details?.length || 0}</Tag>
        },
        {
            title: 'Tổng tín chỉ',
            key: 'totalCredits',
            align: 'center',
            width: 110,
            render: (_, record) => {
                const total = (record.details || []).reduce((sum, d) => sum + (d.course?.credits || 0), 0);
                return <Tag color="purple" style={{ border: 'none', borderRadius: 4 }}>{total} TC</Tag>;
            }
        },
        {
            title: 'Thao tác',
            key: 'action',
            align: 'right',
            width: 130,
            render: (_, record) => (
                <Text
                    type="link"
                    style={{ cursor: 'pointer', color: '#005a8d', fontWeight: 500, display: 'inline-flex', alignItems: 'center', gap: 4 }}
                    onClick={() => {
                        setRoadmapCurrId(record.id);
                        setViewMode('roadmap');
                    }}
                >
                    Xem chi tiết
                </Text>
            )
        }
    ];

    if (viewMode === 'roadmap') {
        return (
            <div style={{ width: '100%' }}>
                <RoadmapManagement
                    initialCurrId={roadmapCurrId}
                    onBack={() => {
                        fetchInitData(); // Refresh list before going back
                        setViewMode('list');
                        setRoadmapCurrId(null);
                    }}
                />
            </div>
        );
    }

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24, alignItems: 'center' }}>
                <Input
                    placeholder="Tìm kiếm CTĐT theo tên, khóa..."
                    prefix={<SearchOutlined style={{ color: '#b0b0b0' }} />}
                    variant="filled"
                    value={searchText}
                    onChange={e => setSearchText(e.target.value)}
                    style={{ width: 300, borderRadius: 6 }}
                />
                <Button type="primary" icon={<PlusOutlined />} onClick={() => setIsCreateModalOpen(true)}>
                    Thiết lập CTĐT mới
                </Button>
            </div>

            <Table
                dataSource={filteredCurriculums}
                columns={columns}
                rowKey="id"
                pagination={{ pageSize: 10, placement: 'bottomRight', showSizeChanger: false }}
            />

            <Modal
                title="Thiết lập Khung CTĐT Mới"
                open={isCreateModalOpen}
                onCancel={() => { setIsCreateModalOpen(false); form.resetFields(); }}
                footer={null}
                width={420}
                centered
            >
                <Form form={form} layout="vertical" onFinish={handleCreate} style={{ marginTop: 20 }}>
                    <Form.Item name="majorId" label="Thuộc Ngành" rules={[{ required: true, message: 'Chọn ngành' }]}>
                        <Select placeholder="Chọn ngành đào tạo" variant="filled">
                            {majors.map(m => (
                                <Option key={m.id} value={m.id}>{m.name} ({m.code})</Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item name="name" label="Tên CTĐT" rules={[{ required: true, message: 'Nhập tên' }]}>
                        <Input placeholder="VD: K18 - Công nghệ thông tin (Chuẩn)" variant="filled" />
                    </Form.Item>
                    <Row gutter={12}>
                        <Col span={12}>
                            <Form.Item
                                name="cohortId"
                                label="Khóa áp dụng"
                                rules={[{ required: true, message: 'Chọn khóa' }]}
                            >
                                <Select
                                    placeholder="Chọn niên khóa"
                                    variant="filled"
                                    onChange={(value) => {
                                        const c = cohorts.find(co => co.id === value);
                                        if (c?.admissionYear) {
                                            form.setFieldsValue({ admissionYear: c.admissionYear });
                                        }
                                    }}
                                >
                                    {cohorts.map(c => (
                                        <Option key={c.id} value={c.id}>
                                            {c.code}{c.admissionYear ? ` (${c.admissionYear})` : ''}
                                        </Option>
                                    ))}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={12}>
                            <Form.Item
                                name="admissionYear"
                                label="Năm nhập học"
                                rules={[{ required: true, message: 'Nhập năm' }]}
                                tooltip="Năm khóa này bắt đầu nhập học. Dùng để tự động tính toán học kỳ khi lập kế hoạch mở lớp."
                            >
                                <InputNumber
                                    min={2000} max={2100}
                                    placeholder="VD: 2024"
                                    variant="filled"
                                    style={{ width: '100%' }}
                                />
                            </Form.Item>
                        </Col>
                    </Row>
                    <Form.Item style={{ textAlign: 'right', marginTop: 8, marginBottom: 0 }}>
                        <Button onClick={() => { setIsCreateModalOpen(false); form.resetFields(); }} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">Tạo mới</Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default CurriculumManagement;
