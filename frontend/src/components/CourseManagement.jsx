import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, message, Tag, Row, Col, Tooltip, Space, Upload } from 'antd';
import { 
    PlusOutlined, 
    DeleteOutlined, 
    EditOutlined,
    DownloadOutlined,
    UploadOutlined
} from '@ant-design/icons';
import * as CourseService from '../services/courseService';
import * as FacultyService from '../services/facultyService';

const { Option } = Select;

const methodColors = {
    'OFFLINE': 'default',
    'ONLINE_ELEARNING': 'green',
    'ONLINE_COURSERA': 'geekblue',
    'HYBRID': 'purple'
};

const methodLabels = {
    'OFFLINE': 'Trực tiếp',
    'ONLINE_ELEARNING': 'E-Learning (100% online)',
    'ONLINE_COURSERA': 'Coursera (Hybrid)',
    'HYBRID': 'Kết hợp'
};

const CourseManagement = () => {
    const [courses, setCourses] = useState([]);
    const [faculties, setFaculties] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [uploading, setUploading] = useState(false);
    const [editingCourse, setEditingCourse] = useState(null);
    const [form] = Form.useForm();

    const fetchData = async () => {
        setLoading(true);
        try {
            const [cRes, fRes] = await Promise.all([
                CourseService.getCourses(), 
                FacultyService.getFaculties()
            ]);
            setCourses(Array.isArray(cRes.data) ? cRes.data : []);
            setFaculties(Array.isArray(fRes.data) ? fRes.data : []);
        } catch {
            message.error('Lỗi tải dữ liệu');
            setCourses([]); 
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchData(); }, []);

    const handleAddNew = () => {
        setEditingCourse(null);
        form.resetFields();
        form.setFieldsValue({ 
            requiredRoomType: 'LT', 
            learningMethod: 'OFFLINE',
            theoryCredits: 0, 
            practiceCredits: 0,
            selfStudyCredits: 0,
            credits: 0 
        });
        setIsModalOpen(true);
    };

    const handleEdit = (record) => {
        setEditingCourse(record);
        const sharedIds = (record.sharedFaculties || []).map(f => f.id);
        form.setFieldsValue({
            ...record,
            facultyId: record.faculty?.id || record.facultyId,
            sharedFacultyIds: sharedIds
        });
        setIsModalOpen(true);
    };

    const handleSave = async (values) => {
        const payload = { 
            ...values, 
            faculty: { id: values.facultyId },
            sharedFacultyIds: values.sharedFacultyIds || []
        };
        try {
            if (editingCourse) {
                await CourseService.updateCourse(editingCourse.id, payload);
                message.success('Cập nhật thành công');
            } else {
                await CourseService.createCourse(payload);
                message.success('Thêm mới thành công');
            }
            setIsModalOpen(false);
            setEditingCourse(null);
            form.resetFields();
            fetchData();
        } catch (error) {
            message.error(error?.response?.data?.message || error?.response?.data || (editingCourse ? 'Lỗi cập nhật' : 'Lỗi thêm mới'));
        }
    };

    const handleDelete = async (id) => {
        Modal.confirm({
            title: 'Xác nhận xóa',
            content: 'Bạn có chắc chắn muốn xóa học phần này?',
            okText: 'Xóa', okType: 'danger', cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await CourseService.deleteCourse(id);
                    message.success('Đã xóa học phần');
                    fetchData();
                } catch (error) { 
                    message.error(error?.response?.data?.message || error?.response?.data || 'Lỗi xóa học phần'); 
                }
            }
        });
    };

    const handleDownloadTemplate = async () => {
        try {
            const res = await CourseService.downloadTemplate();
            const url = window.URL.createObjectURL(new Blob([res.data]));
            const a = document.createElement('a');
            a.href = url;
            a.download = 'course_template.xlsx';
            a.click();
            window.URL.revokeObjectURL(url);
            message.success('Đã tải file mẫu danh sách học phần');
        } catch {
            message.error('Lỗi khi tải file mẫu');
        }
    };

    const handleUpload = async ({ file, onSuccess }) => {
        setUploading(true);
        try {
            await CourseService.importCourse(file);
            message.success('Import danh sách học phần thành công');
            fetchData();
        } catch (e) {
            message.error(e?.response?.data?.message || e?.response?.data || 'Lỗi import file. Kiểm tra lại định dạng.');
        } finally {
            setUploading(false);
            onSuccess();
        }
    };

    // Tự động tính tổng TC = LT + TH (không tính tự học)
    const handleCreditsChange = () => {
        const lt = form.getFieldValue('theoryCredits') || 0;
        const th = form.getFieldValue('practiceCredits') || 0;
        form.setFieldsValue({ credits: lt + th });
    };

    const columns = [
        {
            title: 'Mã HP', dataIndex: 'code', width: 100,
            render: t => <span style={{fontWeight: 600, color: '#005a8d'}}>{t}</span>
        },
        { 
            title: 'Tên Học phần', dataIndex: 'name', width: 250,
            render: (text, record) => (
                <div>
                    <div style={{fontWeight: 500}}>{text}</div>
                    {record.learningMethod && record.learningMethod !== 'OFFLINE' && (
                        <Tag color={methodColors[record.learningMethod] || 'default'} style={{marginTop: 4, fontSize: 10, border: 'none'}}>
                            {methodLabels[record.learningMethod] || record.learningMethod}
                        </Tag>
                    )}
                </div>
            )
        },
        {
            title: 'Tín chỉ (Tổng/LT/TH/Tự học)',
            key: 'credits',
            width: 240,
            render: (_, r) => (
                <Space orientation="vertical" size={0}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                        <Tag color="geekblue" style={{ border: 'none', fontWeight: 700 }}>
                            {r.credits} TC Tổng
                        </Tag>
                    </div>
                    <span style={{ fontSize: 11, color: '#888' }}>
                        LT: <b>{r.theoryCredits}</b> - TH: <b>{r.practiceCredits}</b> - Tự học: <b>{r.selfStudyCredits || 0}</b>
                    </span>
                </Space>
            )
        },
        {
            title: 'Yêu cầu phòng', dataIndex: 'requiredRoomType', width: 180,
            render: (type) => {
                // Các loại CÓ xếp lịch phòng học
                const scheduled = {
                    LT:     { color: '#389e0d', bg: '#f6ffed', text: 'LT — Giảng đường' },
                    PM:     { color: '#096dd9', bg: '#e6f7ff', text: 'PM — Phòng máy' },
                    TN:     { color: '#d46b08', bg: '#fff7e6', text: 'TN — Thí nghiệm' },
                    SB:     { color: '#08979c', bg: '#e6fffb', text: 'SB — Sân bãi' },
                    XT:     { color: '#874d00', bg: '#fffbe6', text: 'XT — Phòng nghe nói' },
                    BV:     { color: '#c41d7f', bg: '#fff0f6', text: 'BV — Bệnh viện' },
                    ONLINE: { color: '#0050b3', bg: '#e6f0ff', text: 'ONLINE — Trực tuyến' },
                };
                // Các loại KHÔNG xếp lịch phòng học
                const unscheduled = {
                    TT:     { color: '#9254de', bg: '#f9f0ff', text: 'TT — Thực tập DN' },
                    TL:     { color: '#722ed1', bg: '#efdbff', text: 'TL — Tiểu luận' },
                    DA:     { color: '#cf1322', bg: '#fff1f0', text: 'DA — Đồ án / Khóa luận TN' },
                    // Tương thích ngược với mã cũ
                    DN:     { color: '#9254de', bg: '#f9f0ff', text: 'TT — Thực tập DN' },
                };
                const c = scheduled[type] || unscheduled[type] || { color: '#666', bg: '#f5f5f5', text: type || '—' };
                const isExcluded = !!unscheduled[type];
                return (
                    <Tooltip title={isExcluded ? 'Không tự động xếp lịch phòng học' : 'Sẽ được xếp lịch phòng học'}>
                        <Tag style={{ border: 'none', background: c.bg, color: c.color, fontWeight: 500 }}>
                            {c.text}{isExcluded ? ' ✕' : ''}
                        </Tag>
                    </Tooltip>
                );
            }
        },
        {
            title: 'Khoa quản lý', dataIndex: 'faculty',
            render: f => <Tag style={{ border: 'none', background: '#f5f5f5', color: '#666' }}>{f ? f.name : '---'}</Tag>
        },
        {
            title: 'Khoa dùng chung', dataIndex: 'sharedFaculties',
            width: 180,
            render: (arr) => (arr && arr.length > 0) ? (
                <Space wrap size={[4, 4]}>
                    {arr.map(f => <Tag key={f.id} color="blue" style={{ border: 'none', fontSize: 11 }}>{f.code}</Tag>)}
                </Space>
            ) : <span style={{ color: '#ccc' }}>—</span>
        },
        {
            title: 'Hành động', key: 'action', width: 100, align: 'right',
            render: (_, record) => (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Tooltip title="Chỉnh sửa">
                        <Button type="text" icon={<EditOutlined />} style={{ color: '#666' }} onClick={() => handleEdit(record)} />
                    </Tooltip>
                    <Tooltip title="Xóa">
                        <Button type="text" danger icon={<DeleteOutlined />} onClick={() => handleDelete(record.id)} />
                    </Tooltip>
                </div>
            )
        }
    ];

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24, alignItems: 'center' }}>
                <Input placeholder="Tìm kiếm học phần..." variant="filled" style={{ width: 300, borderRadius: 6 }} />
                <Space size={8}>
                    <Space.Compact>
                        <Tooltip title="Tải file Excel mẫu để điền dữ liệu">
                            <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
                                File mẫu
                            </Button>
                        </Tooltip>
                        <Upload accept=".xlsx,.xls" showUploadList={false} customRequest={handleUpload}>
                            <Tooltip title="Import danh sách học phần từ Excel">
                                <Button icon={<UploadOutlined />} loading={uploading}>
                                    Import Excel
                                </Button>
                            </Tooltip>
                        </Upload>
                    </Space.Compact>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>Thêm mới</Button>
                </Space>
            </div>

            <Table 
                dataSource={courses} 
                columns={columns} 
                rowKey="id" 
                loading={loading}
                size="middle"
                pagination={{ pageSize: 8 }}
            />

            <Modal 
                title={editingCourse ? "Cập nhật Học phần" : "Thêm Học phần mới"} 
                open={isModalOpen} 
                onCancel={() => setIsModalOpen(false)} 
                footer={null} 
                width={700}
                centered
            >
                <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 20 }}>
                    <Row gutter={16}>
                        <Col span={10}>
                            <Form.Item name="facultyId" label="Khoa quản lý" rules={[{ required: true, message: 'Chọn khoa' }]}>
                                <Select placeholder="Chọn khoa" showSearch optionFilterProp="children" variant="filled">
                                    {faculties.map(f => <Option key={f.id} value={f.id}>{f.name}</Option>)}
                                </Select>
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="code" label="Mã HP" rules={[{ required: true }]}>
                                <Input placeholder="INT101" variant="filled" />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item name="learningMethod" label="Hình thức đào tạo" rules={[{ required: true }]}>
                                <Select variant="filled">
                                    <Option value="OFFLINE">Offline (Truyền thống)</Option>
                                    <Option value="ONLINE_ELEARNING">E-Learning (100% online, Canvas + MS Teams)</Option>
                                    <Option value="ONLINE_COURSERA">Coursera (Hybrid: online ở nhà + offline tại trường theo yêu cầu môn)</Option>
                                    <Option value="HYBRID">Hybrid (Kết hợp khác)</Option>
                                </Select>
                            </Form.Item>
                        </Col>
                    </Row>
                    
                    <Row gutter={16}>
                        <Col span={16}>
                            <Form.Item name="name" label="Tên học phần" rules={[{ required: true }]}>
                                <Input placeholder="VD: Lập trình hướng đối tượng" variant="filled" />
                            </Form.Item>
                        </Col>
                        <Col span={8}>
                            <Form.Item
                                name="requiredRoomType"
                                label="Yêu cầu phòng / địa điểm"
                                rules={[{ required: true }]}
                                tooltip="Các loại có dấu ✕ sẽ KHÔNG được tự động xếp lịch phòng học (sinh viên tự thực hiện với GV)"
                            >
                                <Select variant="filled" placeholder="Chọn loại">
                                    <Select.OptGroup label="✅ Có xếp lịch phòng học">
                                        <Option value="LT">LT — Giảng đường lý thuyết</Option>
                                        <Option value="PM">PM — Phòng máy tính</Option>
                                        <Option value="TN">TN — Thí nghiệm khoa học</Option>
                                        <Option value="SB">SB — Sân bãi / Thể dục</Option>
                                        <Option value="XT">XT — Phòng nghe nói (Language Lab)</Option>
                                        <Option value="BV">BV — Bệnh viện / Y tế lâm sàng</Option>
                                        <Option value="ONLINE">ONLINE — Môi trường trực tuyến</Option>
                                    </Select.OptGroup>
                                    <Select.OptGroup label="✕ Không xếp lịch phòng (tự học / xét riêng)">
                                        <Option value="TT">TT — Thực tập doanh nghiệp</Option>
                                        <Option value="TL">TL — Tiểu luận</Option>
                                        <Option value="DA">DA — Đồ án môn học / Khóa luận tốt nghiệp</Option>
                                    </Select.OptGroup>
                                </Select>
                            </Form.Item>
                        </Col>
                    </Row>

                    <Row gutter={16}>
                        <Col span={6}>
                            <Form.Item name="theoryCredits" label="TC Lý thuyết">
                                <InputNumber min={0} step={0.5} style={{width: '100%'}} variant="filled" onChange={handleCreditsChange} />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="practiceCredits" label="TC Thực hành">
                                <InputNumber min={0} step={0.5} style={{width: '100%'}} variant="filled" onChange={handleCreditsChange} />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="selfStudyCredits" label="TC Tự học">
                                <InputNumber min={0} step={0.5} style={{width: '100%'}} variant="filled" onChange={handleCreditsChange} />
                            </Form.Item>
                        </Col>
                        <Col span={6}>
                            <Form.Item name="credits" label="Tổng TC" rules={[{ required: true }]}>
                                <InputNumber min={0} step={0.5} style={{width: '100%'}} variant="filled" readOnly />
                            </Form.Item>
                        </Col>
                    </Row>

                    <Form.Item 
                        name="sharedFacultyIds" 
                        label="Khoa dùng chung (GV có thể dạy môn này)"
                        tooltip="VD: Tin học văn phòng thuộc HTTT, nhưng GV từ KHMT, CNTT cũng có thể dạy. Có thể import qua Excel cột 'Mã Khoa dùng chung' (IT,CS,MATH)"
                    >
                        <Select
                            mode="multiple"
                            placeholder="Chọn các Khoa có thể cung cấp GV"
                            optionFilterProp="label"
                            variant="filled"
                            options={faculties
                                .filter(f => f.id !== form.getFieldValue('facultyId'))
                                .map(f => ({ value: f.id, label: `${f.name} (${f.code})` }))}
                        />
                    </Form.Item>

                    <Form.Item style={{ textAlign: 'right', marginTop: 16, marginBottom: 0 }}>
                        <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">
                            {editingCourse ? "Cập nhật" : "Lưu vào kho"}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default CourseManagement;
