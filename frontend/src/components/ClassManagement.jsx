import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, InputNumber, Upload, message, Tag, Tooltip } from 'antd';
import { 
    UploadOutlined, 
    PlusOutlined, 
    DownloadOutlined, 
    DeleteOutlined,
    EditOutlined 
} from '@ant-design/icons';
import * as ClassService from '../services/classService';
import * as MajorService from '../services/majorService';

const { Option } = Select;

const ClassManagement = () => {
    const [classes, setClasses] = useState([]);
    const [majors, setMajors] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [editingClass, setEditingClass] = useState(null);
    const [form] = Form.useForm();

    const fetchData = async () => {
        setLoading(true);
        try {
            const [classRes, majorRes] = await Promise.all([
                ClassService.getClasses(),
                MajorService.getMajors()
            ]);
            setClasses(classRes.data || classRes);
            setMajors(majorRes.data || majorRes);
        } catch {
            message.error('Lỗi tải dữ liệu');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchData(); }, []);

    const handleAddNew = () => {
        setEditingClass(null);
        form.resetFields();
        setIsModalOpen(true);
    };

    const handleEdit = (record) => {
        setEditingClass(record);
        form.setFieldsValue({
            ...record,
            majorId: record.major?.id || record.majorId
        });
        setIsModalOpen(true);
    };

    const handleSave = async (values) => {
        const payload = { ...values, major: { id: values.majorId } };
        try {
            if (editingClass) {
                await ClassService.updateClass(editingClass.id, payload);
                message.success('Cập nhật lớp thành công');
            } else {
                await ClassService.createClass(payload);
                message.success('Thêm lớp thành công');
            }
            setIsModalOpen(false);
            setEditingClass(null);
            form.resetFields();
            fetchData();
        } catch {
            message.error(editingClass ? 'Lỗi cập nhật lớp' : 'Lỗi thêm lớp');
        }
    };

    const handleDelete = async (id) => {
        Modal.confirm({
            title: 'Xác nhận xóa',
            content: 'Bạn có chắc muốn xóa lớp biên chế này?',
            okText: 'Xóa',
            okType: 'danger',
            cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await ClassService.deleteClass(id);
                    message.success('Đã xóa lớp');
                    fetchData();
                } catch {
                    message.error('Lỗi xóa lớp');
                }
            }
        });
    };

    const handleDownloadTemplate = async () => {
        try {
            const response = await ClassService.downloadTemplate();
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url; 
            link.setAttribute('download', 'class_template.xlsx');
            document.body.appendChild(link); 
            link.click(); 
            link.remove();
        } catch {
            message.error('Lỗi tải file mẫu');
        }
    };

    const handleUpload = async ({ file, onSuccess, onError }) => {
        try {
            await ClassService.importClass(file);
            message.success('Import thành công');
            fetchData();
            onSuccess("Ok");
        } catch (error) {
            message.error('Lỗi Import');
            onError(error);
        }
    };

    const columns = [
        { 
            title: 'Mã Lớp', dataIndex: 'code', key: 'code', width: '15%', 
            render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span> 
        },
        { 
            title: 'Tên Lớp', dataIndex: 'name', key: 'name',
            render: t => <span style={{ fontWeight: 500 }}>{t}</span>
        },
        { 
            title: 'Khóa', dataIndex: 'cohort', key: 'cohort', width: 100,
            render: t => <Tag style={{ border: 'none', background: '#f5f5f5', color: '#666' }}>{t}</Tag>
        },
        { 
            title: 'Sĩ số', dataIndex: 'studentCount', key: 'studentCount', width: 100, align: 'center',
            render: t => <span style={{ fontWeight: 600 }}>{t}</span>
        },
        { 
            title: 'Thuộc Ngành', dataIndex: 'major', key: 'major',
            render: (major) => (
                <Tag style={{ border: 'none', background: '#e6f7ff', color: '#005a8d', fontWeight: 500 }}>
                    {major ? major.name : 'N/A'}
                </Tag>
            )
        },
        {
            title: 'Hành động', key: 'action', width: 120, align: 'right',
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
                <Input placeholder="Tìm kiếm lớp..." variant="filled" style={{ width: 280, borderRadius: 6 }} />
                <div style={{ display: 'flex', gap: 10 }}>
                    <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>Mẫu</Button>
                    <Upload customRequest={handleUpload} showUploadList={false}>
                        <Button icon={<UploadOutlined />}>Import</Button>
                    </Upload>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>Thêm mới</Button>
                </div>
            </div>

            <Table 
                dataSource={classes} columns={columns} rowKey="id" loading={loading}
                pagination={{ pageSize: 8, placement: 'bottomRight', style: { marginTop: 24 } }}
            />

            <Modal 
                title={editingClass ? "Cập nhật Lớp biên chế" : "Thêm Lớp biên chế"} 
                open={isModalOpen} 
                onCancel={() => { setIsModalOpen(false); setEditingClass(null); form.resetFields(); }} 
                footer={null} width={500} centered
            >
                <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 20 }}>
                    <Form.Item name="majorId" label="Thuộc Ngành" rules={[{ required: true, message: 'Vui lòng chọn ngành' }]}>
                        <Select placeholder="Chọn ngành" variant="filled">
                            {majors.map(m => (
                                <Option key={m.id} value={m.id}>{m.name} ({m.code})</Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item name="code" label="Mã Lớp" rules={[{ required: true, message: 'Vui lòng nhập mã lớp' }]}>
                        <Input placeholder="VD: K17-CNTT1" variant="filled" />
                    </Form.Item>
                    <Form.Item name="name" label="Tên Lớp" rules={[{ required: true, message: 'Vui lòng nhập tên lớp' }]}>
                        <Input placeholder="VD: Công nghệ thông tin 1 - K17" variant="filled" />
                    </Form.Item>
                    <div style={{ display: 'flex', gap: 16 }}>
                        <Form.Item name="cohort" label="Khóa" style={{ flex: 1 }}>
                            <Input placeholder="VD: K17" variant="filled" />
                        </Form.Item>
                        <Form.Item name="studentCount" label="Sĩ số" style={{ flex: 1 }}>
                            <InputNumber style={{ width: '100%' }} min={1} placeholder="0" variant="filled" />
                        </Form.Item>
                    </div>
                    <Form.Item style={{ textAlign: 'right', marginTop: 16, marginBottom: 0 }}>
                        <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">
                            {editingClass ? "Cập nhật" : "Lưu lại"}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default ClassManagement;
