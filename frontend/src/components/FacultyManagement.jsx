import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Checkbox, Upload, message, Tooltip, Space } from 'antd';
import {
    UploadOutlined,
    PlusOutlined,
    DownloadOutlined,
    DeleteOutlined,
    EditOutlined
} from '@ant-design/icons';
import * as FacultyService from '../services/facultyService';

const FacultyManagement = () => {
    const [faculties, setFaculties] = useState([]);
    const [searchText, setSearchText] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [editingFaculty, setEditingFaculty] = useState(null);
    const [form] = Form.useForm();

    const filteredFaculties = faculties.filter(f =>
        f.code?.toLowerCase().includes(searchText.toLowerCase()) ||
        f.name?.toLowerCase().includes(searchText.toLowerCase())
    );

    const fetchFaculties = async () => {
        setLoading(true);
        try {
            const res = await FacultyService.getFaculties();
            setFaculties(res.data || res);
        } catch {
            message.error('Không thể tải danh sách khoa');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchFaculties(); }, []);

    const handleEdit = (record) => {
        setEditingFaculty(record);
        form.setFieldsValue({ ...record, allowSkipAssignment: !!record.allowSkipAssignment });
        setIsModalOpen(true);
    };

    const handleAddNew = () => {
        setEditingFaculty(null);
        form.resetFields();
        setIsModalOpen(true);
    };

    const handleSave = async (values) => {
        try {
            if (editingFaculty) {
                await FacultyService.updateFaculty(editingFaculty.id, values);
                message.success('Cập nhật khoa thành công');
            } else {
                await FacultyService.createFaculty(values);
                message.success('Thêm khoa thành công');
            }
            setIsModalOpen(false);
            setEditingFaculty(null);
            form.resetFields();
            fetchFaculties();
        } catch {
            message.error(editingFaculty ? 'Lỗi cập nhật khoa' : 'Lỗi thêm khoa');
        }
    };

    const handleDelete = async (id) => {
        Modal.confirm({
            title: 'Bạn có chắc chắn muốn xóa?',
            okText: 'Xóa',
            okType: 'danger',
            cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await FacultyService.deleteFaculty(id);
                    message.success('Đã xóa khoa');
                    fetchFaculties();
                } catch {
                    message.error('Lỗi xóa khoa');
                }
            }
        });
    };

    const handleDownloadTemplate = async () => {
        try {
            const response = await FacultyService.downloadTemplate();
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'faculty_template.xlsx');
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch {
            message.error('Lỗi tải file mẫu');
        }
    };

    const handleUpload = async ({ file, onSuccess, onError }) => {
        try {
            await FacultyService.importFaculty(file);
            message.success('Import thành công');
            fetchFaculties();
            onSuccess("Ok");
        } catch (error) {
            message.error('Import thất bại');
            onError(error);
        }
    };

    const columns = [
        {
            title: 'Mã Khoa',
            dataIndex: 'code',
            key: 'code',
            width: '20%',
            render: (text) => <span style={{ fontWeight: 600, color: '#005a8d' }}>{text}</span>
        },
        {
            title: 'Tên Khoa/Viện',
            dataIndex: 'name',
            key: 'name',
            render: (text) => <span style={{ fontWeight: 500 }}>{text}</span>
        },
        {
            title: 'Hành động',
            key: 'action',
            width: 120,
            align: 'right',
            render: (_, record) => (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Tooltip title="Chỉnh sửa">
                        <Button
                            type="text"
                            icon={<EditOutlined />}
                            style={{ color: '#666' }}
                            onClick={() => handleEdit(record)}
                        />
                    </Tooltip>
                    <Tooltip title="Xóa">
                        <Button
                            type="text"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={() => handleDelete(record.id)}
                        />
                    </Tooltip>
                </div>
            ),
        },
    ];

    return (
        <div style={{ width: '100%' }}>
            <div style={{ marginBottom: 24, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <Input
                    placeholder="Tìm kiếm theo mã hoặc tên khoa..."
                    variant="filled"
                    allowClear
                    style={{ width: 300, borderRadius: 6 }}
                    onChange={e => setSearchText(e.target.value)}
                />
                <Space.Compact>
                        <Tooltip title="Tải file Excel mẫu để điền dữ liệu">
                            <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>File mẫu</Button>
                        </Tooltip>
                        <Upload customRequest={handleUpload} showUploadList={false}>
                            <Tooltip title="Import danh sách khoa từ Excel">
                                <Button icon={<UploadOutlined />}>Import Excel</Button>
                            </Tooltip>
                        </Upload>
                        <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>Thêm mới</Button>
                    </Space.Compact>
            </div>

            <Table
                columns={columns}
                dataSource={filteredFaculties}
                rowKey="id"
                loading={loading}
                pagination={{ pageSize: 8, placement: 'bottomRight', style: { marginTop: 24 } }}
            />

            <Modal
                title={editingFaculty ? "Cập nhật Khoa/Viện" : "Thêm Khoa mới"}
                open={isModalOpen}
                onCancel={() => {
                    setIsModalOpen(false);
                    setEditingFaculty(null);
                    form.resetFields();
                }}
                footer={null}
                width={420}
                centered
            >
                <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 20 }}>
                    <Form.Item
                        name="code"
                        label="Mã Khoa"
                        rules={[{ required: true, message: 'Vui lòng nhập mã khoa' }]}
                    >
                        <Input placeholder="VD: CNTT" variant="filled" disabled={!!editingFaculty} />
                    </Form.Item>
                    <Form.Item
                        name="name"
                        label="Tên Khoa"
                        rules={[{ required: true, message: 'Vui lòng nhập tên khoa' }]}
                    >
                        <Input placeholder="VD: Khoa Công nghệ thông tin" variant="filled" />
                    </Form.Item>
                    {editingFaculty && (
                        <Form.Item name="allowSkipAssignment" valuePropName="checked">
                            <Checkbox>Cho phép bỏ qua phân công (phân công sau khi có TKB)</Checkbox>
                        </Form.Item>
                    )}
                    <Form.Item style={{ textAlign: 'right', marginBottom: 0, marginTop: 24 }}>
                        <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">
                            {editingFaculty ? "Cập nhật" : "Lưu lại"}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default FacultyManagement;
