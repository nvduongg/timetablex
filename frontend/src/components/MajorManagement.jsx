import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, Upload, message, Tag, Tooltip, Space } from 'antd';
import {
    UploadOutlined,
    PlusOutlined,
    DownloadOutlined,
    DeleteOutlined,
    EditOutlined
} from '@ant-design/icons';
import * as MajorService from '../services/majorService';
import * as FacultyService from '../services/facultyService';

const { Option } = Select;

const MajorManagement = () => {
    const [majors, setMajors] = useState([]);
    const [faculties, setFaculties] = useState([]);
    const [searchText, setSearchText] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [editingMajor, setEditingMajor] = useState(null);
    const [form] = Form.useForm();

    const filteredMajors = majors.filter(m =>
        m.code?.toLowerCase().includes(searchText.toLowerCase()) ||
        m.name?.toLowerCase().includes(searchText.toLowerCase()) ||
        m.faculty?.name?.toLowerCase().includes(searchText.toLowerCase())
    );

    const fetchData = async () => {
        setLoading(true);
        try {
            const [majorRes, facultyRes] = await Promise.all([
                MajorService.getMajors(),
                FacultyService.getFaculties()
            ]);
            setMajors(majorRes.data || majorRes);
            setFaculties(facultyRes.data || facultyRes);
        } catch {
            message.error('Không thể tải dữ liệu');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchData(); }, []);

    const handleAddNew = () => {
        setEditingMajor(null);
        form.resetFields();
        setIsModalOpen(true);
    };

    const handleEdit = (record) => {
        setEditingMajor(record);
        // record.faculty là object { id, name }, form cần field facultyId dạng số
        form.setFieldsValue({
            ...record,
            facultyId: record.faculty?.id || record.facultyId
        });
        setIsModalOpen(true);
    };

    const handleSave = async (values) => {
        const payload = {
            code: values.code,
            name: values.name,
            faculty: { id: values.facultyId }
        };
        try {
            if (editingMajor) {
                await MajorService.updateMajor(editingMajor.id, payload);
                message.success('Cập nhật ngành thành công');
            } else {
                await MajorService.createMajor(payload);
                message.success('Thêm ngành thành công');
            }
            setIsModalOpen(false);
            setEditingMajor(null);
            form.resetFields();
            fetchData();
        } catch {
            message.error(editingMajor ? 'Lỗi cập nhật ngành' : 'Lỗi thêm ngành');
        }
    };

    const handleDelete = async (id) => {
        Modal.confirm({
            title: 'Xác nhận xóa',
            content: 'Hành động này không thể hoàn tác.',
            okText: 'Xóa',
            okType: 'danger',
            cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await MajorService.deleteMajor(id);
                    message.success('Đã xóa ngành');
                    fetchData();
                } catch {
                    message.error('Lỗi xóa ngành');
                }
            }
        });
    };

    const handleDownloadTemplate = async () => {
        try {
            const response = await MajorService.downloadTemplate();
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'major_template.xlsx');
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch {
            message.error('Lỗi tải file mẫu');
        }
    };

    const handleUpload = async ({ file, onSuccess, onError }) => {
        try {
            await MajorService.importMajor(file);
            message.success('Import thành công');
            fetchData();
            onSuccess("Ok");
        } catch (error) {
            message.error('Import thất bại');
            onError(error);
        }
    };

    const columns = [
        {
            title: 'Mã Ngành',
            dataIndex: 'code',
            key: 'code',
            width: '15%',
            render: (text) => <span style={{ fontWeight: 600, color: '#005a8d' }}>{text}</span>
        },
        {
            title: 'Tên Ngành',
            dataIndex: 'name',
            key: 'name',
            render: (text) => <span style={{ fontWeight: 500 }}>{text}</span>
        },
        {
            title: 'Thuộc Khoa',
            dataIndex: 'faculty',
            key: 'faculty',
            render: (faculty) => (
                <Tag style={{ border: 'none', background: '#e6f7ff', color: '#005a8d', fontWeight: 500 }}>
                    {faculty ? faculty.name : 'N/A'}
                </Tag>
            )
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
                    placeholder="Tìm kiếm theo mã, tên ngành hoặc khoa..."
                    variant="filled"
                    allowClear
                    style={{ width: 320, borderRadius: 6 }}
                    onChange={e => setSearchText(e.target.value)}
                />
                <Space.Compact>
                        <Tooltip title="Tải file Excel mẫu để điền dữ liệu">
                            <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>File mẫu</Button>
                        </Tooltip>
                        <Upload customRequest={handleUpload} showUploadList={false}>
                            <Tooltip title="Import danh sách ngành từ Excel">
                                <Button icon={<UploadOutlined />}>Import Excel</Button>
                            </Tooltip>
                        </Upload>
                        <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>Thêm mới</Button>
                    </Space.Compact>
            </div>

            <Table
                columns={columns}
                dataSource={filteredMajors}
                rowKey="id"
                loading={loading}
                pagination={{ pageSize: 8, placement: 'bottomRight', style: { marginTop: 24 } }}
            />

            <Modal
                title={editingMajor ? "Cập nhật Ngành" : "Thêm Ngành Mới"}
                open={isModalOpen}
                onCancel={() => {
                    setIsModalOpen(false);
                    setEditingMajor(null);
                    form.resetFields();
                }}
                footer={null}
                width={420}
                centered
            >
                <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 20 }}>
                    <Form.Item
                        name="facultyId"
                        label="Thuộc Khoa/Viện"
                        rules={[{ required: true, message: 'Vui lòng chọn khoa' }]}
                    >
                        <Select placeholder="Chọn khoa quản lý" variant="filled">
                            {faculties.map(f => (
                                <Option key={f.id} value={f.id}>
                                    {f.name} ({f.code})
                                </Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item
                        name="code"
                        label="Mã Ngành"
                        rules={[{ required: true, message: 'Vui lòng nhập mã ngành' }]}
                    >
                        <Input placeholder="VD: 7480201" variant="filled" />
                    </Form.Item>
                    <Form.Item
                        name="name"
                        label="Tên Ngành"
                        rules={[{ required: true, message: 'Vui lòng nhập tên ngành' }]}
                    >
                        <Input placeholder="VD: Công nghệ thông tin" variant="filled" />
                    </Form.Item>
                    <Form.Item style={{ textAlign: 'right', marginBottom: 0, marginTop: 24 }}>
                        <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">
                            {editingMajor ? "Cập nhật" : "Lưu lại"}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default MajorManagement;
