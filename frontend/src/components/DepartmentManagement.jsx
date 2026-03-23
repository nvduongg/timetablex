import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, Upload, message, Tag, Tooltip, Space } from 'antd';
import {
    UploadOutlined,
    PlusOutlined,
    DownloadOutlined,
    DeleteOutlined,
    EditOutlined,
    ApartmentOutlined
} from '@ant-design/icons';
import * as DepartmentService from '../services/departmentService';
import * as FacultyService from '../services/facultyService';

const { Option } = Select;

const DepartmentManagement = () => {
    const [departments, setDepartments] = useState([]);
    const [faculties, setFaculties] = useState([]);
    const [searchText, setSearchText] = useState('');
    const [filterFacultyId, setFilterFacultyId] = useState(null);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [editingDept, setEditingDept] = useState(null);
    const [form] = Form.useForm();

    const filteredDepts = departments.filter(d =>
        (!filterFacultyId || d.faculty?.id === filterFacultyId) &&
        (
            d.code?.toLowerCase().includes(searchText.toLowerCase()) ||
            d.name?.toLowerCase().includes(searchText.toLowerCase()) ||
            d.faculty?.name?.toLowerCase().includes(searchText.toLowerCase())
        )
    );

    const fetchData = async () => {
        setLoading(true);
        try {
            const [deptRes, facRes] = await Promise.all([
                DepartmentService.getDepartments(),
                FacultyService.getFaculties()
            ]);
            setDepartments(deptRes.data || deptRes);
            setFaculties(facRes.data || facRes);
        } catch {
            message.error('Không thể tải dữ liệu');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchData(); }, []);

    const handleAddNew = () => {
        setEditingDept(null);
        form.resetFields();
        setIsModalOpen(true);
    };

    const handleEdit = (record) => {
        setEditingDept(record);
        form.setFieldsValue({
            ...record,
            facultyId: record.faculty?.id
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
            if (editingDept) {
                await DepartmentService.updateDepartment(editingDept.id, payload);
                message.success('Cập nhật bộ môn thành công');
            } else {
                await DepartmentService.createDepartment(payload);
                message.success('Thêm bộ môn thành công');
            }
            setIsModalOpen(false);
            setEditingDept(null);
            form.resetFields();
            fetchData();
        } catch {
            message.error(editingDept ? 'Lỗi cập nhật bộ môn' : 'Lỗi thêm bộ môn');
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
                    await DepartmentService.deleteDepartment(id);
                    message.success('Đã xóa bộ môn');
                    fetchData();
                } catch {
                    message.error('Lỗi xóa bộ môn');
                }
            }
        });
    };

    const handleDownloadTemplate = async () => {
        try {
            const response = await DepartmentService.downloadTemplate();
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'department_template.xlsx');
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch {
            message.error('Lỗi tải file mẫu');
        }
    };

    const handleUpload = async ({ file, onSuccess, onError }) => {
        try {
            await DepartmentService.importDepartment(file);
            message.success('Import thành công');
            fetchData();
            onSuccess('Ok');
        } catch (error) {
            message.error('Import thất bại');
            onError(error);
        }
    };

    const columns = [
        {
            title: 'Mã Bộ môn',
            dataIndex: 'code',
            key: 'code',
            width: '15%',
            render: (text) => <span style={{ fontWeight: 600, color: '#005a8d' }}>{text}</span>
        },
        {
            title: 'Tên Bộ môn',
            dataIndex: 'name',
            key: 'name',
            render: (text) => (
                <span style={{ fontWeight: 500 }}>
                    <ApartmentOutlined style={{ marginRight: 6, color: '#aaa' }} />
                    {text}
                </span>
            )
        },
        {
            title: 'Thuộc Khoa',
            dataIndex: 'faculty',
            key: 'faculty',
            render: (faculty) => (
                <Tag style={{ border: 'none', background: '#e6f7ff', color: '#005a8d', fontWeight: 500 }}>
                    {faculty ? `${faculty.name}` : 'N/A'}
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
            <div style={{ marginBottom: 24, display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: 12 }}>
                <div style={{ display: 'flex', gap: 8 }}>
                    <Input
                        placeholder="Tìm kiếm theo mã, tên bộ môn hoặc khoa..."
                        variant="filled"
                        allowClear
                        style={{ width: 320, borderRadius: 6 }}
                        onChange={e => setSearchText(e.target.value)}
                    />
                    <Select
                        allowClear
                        placeholder="Lọc theo Khoa"
                        variant="filled"
                        style={{ width: 200 }}
                        onChange={val => setFilterFacultyId(val || null)}
                    >
                        {faculties.map(f => (
                            <Option key={f.id} value={f.id}>{f.name}</Option>
                        ))}
                    </Select>
                </div>
                <Space.Compact>
                    <Tooltip title="Tải file Excel mẫu để điền dữ liệu">
                        <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>File mẫu</Button>
                    </Tooltip>
                    <Upload customRequest={handleUpload} showUploadList={false}>
                        <Tooltip title="Import danh sách bộ môn từ Excel">
                            <Button icon={<UploadOutlined />}>Import Excel</Button>
                        </Tooltip>
                    </Upload>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>Thêm mới</Button>
                </Space.Compact>
            </div>

            <Table
                columns={columns}
                dataSource={filteredDepts}
                rowKey="id"
                loading={loading}
                pagination={{ pageSize: 10, placement: 'bottomRight', style: { marginTop: 24 } }}
            />

            <Modal
                title={editingDept ? "Cập nhật Bộ môn" : "Thêm Bộ môn Mới"}
                open={isModalOpen}
                onCancel={() => {
                    setIsModalOpen(false);
                    setEditingDept(null);
                    form.resetFields();
                }}
                footer={null}
                width={440}
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
                        label="Mã Bộ môn"
                        rules={[{ required: true, message: 'Vui lòng nhập mã bộ môn' }]}
                    >
                        <Input placeholder="VD: KHMT, HTTT" variant="filled" />
                    </Form.Item>
                    <Form.Item
                        name="name"
                        label="Tên Bộ môn"
                        rules={[{ required: true, message: 'Vui lòng nhập tên bộ môn' }]}
                    >
                        <Input placeholder="VD: Bộ môn Khoa học máy tính" variant="filled" />
                    </Form.Item>
                    <Form.Item style={{ textAlign: 'right', marginBottom: 0, marginTop: 24 }}>
                        <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">
                            {editingDept ? "Cập nhật" : "Lưu lại"}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default DepartmentManagement;
