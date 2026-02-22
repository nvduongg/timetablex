import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, DatePicker, Switch, message, Space, Tag, Tooltip, Upload } from 'antd';
import { 
    PlusOutlined, 
    EditOutlined, 
    DeleteOutlined, 
    CalendarOutlined, 
    CheckCircleOutlined,
    StopOutlined,
    DownloadOutlined,
    UploadOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';
import * as SemesterService from '../services/semesterService';

const { RangePicker } = DatePicker;

const SemesterManagement = () => {
    const [semesters, setSemesters] = useState([]);
    const [searchText, setSearchText] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [editingItem, setEditingItem] = useState(null);
    const [form] = Form.useForm();

    const fetchSemesters = async () => {
        setLoading(true);
        try {
            const res = await SemesterService.getSemesters();
            const data = Array.isArray(res.data) ? res.data : [];
            setSemesters(data.sort((a, b) => new Date(b.startDate) - new Date(a.startDate)));
        } catch {
            message.error('Lỗi tải dữ liệu');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchSemesters(); }, []);

    const filteredData = semesters.filter(item => 
        item.name.toLowerCase().includes(searchText.toLowerCase()) || 
        item.code.toLowerCase().includes(searchText.toLowerCase())
    );

    const handleAddNew = () => {
        setEditingItem(null);
        form.resetFields();
        form.setFieldValue('isActive', false);
        setIsModalOpen(true);
    };

    const handleEdit = (record) => {
        setEditingItem(record);
        form.setFieldsValue({
            ...record,
            dateRange: [dayjs(record.startDate), dayjs(record.endDate)]
        });
        setIsModalOpen(true);
    };

    const handleSave = async (values) => {
        const payload = {
            ...values,
            startDate: values.dateRange[0].format('YYYY-MM-DD'),
            endDate: values.dateRange[1].format('YYYY-MM-DD'),
        };
        delete payload.dateRange;

        try {
            if (editingItem) {
                await SemesterService.updateSemester(editingItem.id, payload);
                message.success('Cập nhật thành công');
            } else {
                await SemesterService.createSemester(payload);
                message.success('Tạo học kỳ mới thành công');
            }
            setIsModalOpen(false);
            setEditingItem(null);
            fetchSemesters();
        } catch {
            message.error('Lỗi lưu dữ liệu');
        }
    };

    const handleDelete = async (id) => {
        Modal.confirm({
            title: 'Xác nhận xóa',
            content: 'Bạn có chắc muốn xóa học kỳ này? Dữ liệu thời khóa biểu liên quan có thể bị ảnh hưởng.',
            okText: 'Xóa',
            okType: 'danger',
            cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await SemesterService.deleteSemester(id);
                    message.success('Đã xóa học kỳ');
                    fetchSemesters();
                } catch {
                    message.error('Lỗi xóa dữ liệu');
                }
            }
        });
    };

    const columns = [
        { 
            title: 'Mã HK', dataIndex: 'code', width: 120, 
            render: t => <span style={{color: '#005a8d', fontWeight: 600}}>{t}</span> 
        },
        { 
            title: 'Tên Học kỳ', dataIndex: 'name',
            render: t => <span style={{fontWeight: 500}}>{t}</span> 
        },
        { 
            title: 'Thời gian', 
            key: 'time', 
            render: (_, r) => (
                <Tag style={{border: 'none', background: '#f5f5f5', color: '#666', padding: '4px 10px', fontSize: 13}}>
                    <CalendarOutlined style={{marginRight: 6}}/>
                    {dayjs(r.startDate).format('DD/MM/YYYY')} - {dayjs(r.endDate).format('DD/MM/YYYY')}
                </Tag>
            )
        },
        {
            title: 'Trạng thái',
            dataIndex: 'isActive',
            width: 150,
            render: (isActive) => isActive ? (
                <Tag icon={<CheckCircleOutlined />} color="success" style={{border: 'none', fontWeight: 500}}>
                    Đang hoạt động
                </Tag>
            ) : (
                <Tag icon={<StopOutlined />} style={{border: 'none', color: '#999'}}>
                    Đã đóng
                </Tag>
            )
        },
        {
            title: 'Hành động', key: 'action', width: 120, align: 'right',
            render: (_, r) => (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Tooltip title="Chỉnh sửa">
                        <Button 
                            type="text" 
                            icon={<EditOutlined />} 
                            style={{ color: '#666' }} 
                            onClick={() => handleEdit(r)} 
                        />
                    </Tooltip>
                    {/* Không cho xóa học kỳ đang chạy */}
                    <Tooltip title={r.isActive ? "Không thể xóa HK đang chạy" : "Xóa"}>
                        <Button 
                            type="text" 
                            danger 
                            icon={<DeleteOutlined />} 
                            disabled={r.isActive} 
                            onClick={() => handleDelete(r.id)} 
                        />
                    </Tooltip>
                </div>
            )
        }
    ];

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24, alignItems: 'center', flexWrap: 'wrap', gap: 12 }}>
                <Input 
                    placeholder="Tìm kiếm học kỳ..." 
                    variant="filled" 
                    style={{ width: 300, borderRadius: 6 }} 
                    onChange={e => setSearchText(e.target.value)}
                />
                <Space>
                    <Button icon={<DownloadOutlined />} onClick={async () => {
                        try {
                            const res = await SemesterService.downloadTemplate();
                            const url = window.URL.createObjectURL(new Blob([res.data]));
                            const a = document.createElement('a');
                            a.href = url;
                            a.download = 'semester_template.xlsx';
                            a.click();
                            window.URL.revokeObjectURL(url);
                            message.success('Đã tải mẫu');
                        } catch { message.error('Lỗi tải mẫu'); }
                    }}>
                        Mẫu Excel
                    </Button>
                    <Upload
                        customRequest={async ({ file, onSuccess, onError }) => {
                            try {
                                await SemesterService.importSemester(file);
                                message.success('Import thành công');
                                fetchSemesters();
                                onSuccess();
                            } catch (e) {
                                message.error('Import thất bại');
                                onError(e);
                            }
                        }}
                        showUploadList={false}
                    >
                        <Button icon={<UploadOutlined />}>Import</Button>
                    </Upload>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>
                        Tạo Học kỳ mới
                    </Button>
                </Space>
            </div>

            <Table 
                dataSource={filteredData} 
                columns={columns} 
                rowKey="id" 
                loading={loading}
                size="middle"
                pagination={{ pageSize: 8, placement: 'bottomRight', style: {marginTop: 24} }}
            />

            <Modal 
                title={editingItem ? "Cập nhật Học kỳ" : "Tạo Học kỳ mới"} 
                open={isModalOpen} 
                onCancel={() => setIsModalOpen(false)} 
                footer={null}
                width={500}
                centered
            >
                <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 20 }}>
                    <Form.Item name="code" label="Mã Học kỳ" rules={[{ required: true, message: 'Nhập mã học kỳ' }]}>
                        <Input placeholder="VD: 2025_1" variant="filled" />
                    </Form.Item>
                    
                    <Form.Item name="name" label="Tên hiển thị" rules={[{ required: true, message: 'Nhập tên học kỳ' }]}>
                        <Input placeholder="VD: Học kỳ 1 Năm học 2025-2026" variant="filled" />
                    </Form.Item>
                    
                    <Form.Item name="dateRange" label="Thời gian bắt đầu - kết thúc" rules={[{ required: true, message: 'Chọn thời gian' }]}>
                        <RangePicker style={{ width: '100%' }} format="DD/MM/YYYY" variant="filled" />
                    </Form.Item>
                    
                    <Form.Item name="isActive" label="Trạng thái kích hoạt" valuePropName="checked">
                        <Switch checkedChildren="Hoạt động" unCheckedChildren="Đóng" />
                    </Form.Item>
                    
                    <Form.Item style={{ textAlign: 'right', marginTop: 24, marginBottom: 0 }}>
                        <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">
                            {editingItem ? "Cập nhật" : "Lưu lại"}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default SemesterManagement;
