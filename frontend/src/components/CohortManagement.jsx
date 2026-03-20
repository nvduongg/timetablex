import React, { useEffect, useState } from 'react';
import { Table, Button, Modal, Form, Input, InputNumber, Switch, message, Tooltip, Space, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import * as CohortService from '../services/cohortService';

const CohortManagement = () => {
    const [cohorts, setCohorts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [searchText, setSearchText] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [editingCohort, setEditingCohort] = useState(null);
    const [form] = Form.useForm();

    const fetchCohorts = async () => {
        setLoading(true);
        try {
            const res = await CohortService.getCohorts();
            const data = res.data || res;
            setCohorts(Array.isArray(data) ? data : []);
        } catch {
            message.error('Không thể tải danh sách niên khóa');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => {
        fetchCohorts();
    }, []);

    const handleAddNew = () => {
        setEditingCohort(null);
        form.resetFields();
        form.setFieldsValue({ active: true });
        setIsModalOpen(true);
    };

    const handleEdit = (record) => {
        setEditingCohort(record);
        form.setFieldsValue({
            code: record.code,
            name: record.name,
            admissionYear: record.admissionYear,
            active: record.active !== false,
            note: record.note,
        });
        setIsModalOpen(true);
    };

    const handleSave = async (values) => {
        try {
            if (editingCohort) {
                await CohortService.updateCohort(editingCohort.id, values);
                message.success('Cập nhật niên khóa thành công');
            } else {
                await CohortService.createCohort(values);
                message.success('Thêm niên khóa thành công');
            }
            setIsModalOpen(false);
            setEditingCohort(null);
            form.resetFields();
            fetchCohorts();
        } catch {
            message.error(editingCohort ? 'Lỗi cập nhật niên khóa' : 'Lỗi thêm niên khóa');
        }
    };

    const handleDelete = async (id) => {
        Modal.confirm({
            title: 'Xác nhận xóa',
            content: 'Bạn có chắc muốn xóa niên khóa này? Việc này có thể ảnh hưởng tới dữ liệu lớp, CTĐT và kế hoạch mở lớp.',
            okText: 'Xóa',
            okType: 'danger',
            cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await CohortService.deleteCohort(id);
                    message.success('Đã xóa niên khóa');
                    fetchCohorts();
                } catch {
                    message.error('Không thể xóa niên khóa (có thể đang được sử dụng)');
                }
            }
        });
    };

    const filteredCohorts = cohorts.filter(c => {
        const q = searchText.toLowerCase();
        return (
            (c.code || '').toLowerCase().includes(q) ||
            (c.name || '').toLowerCase().includes(q) ||
            String(c.admissionYear || '').includes(q)
        );
    });

    const columns = [
        {
            title: 'Mã khóa',
            dataIndex: 'code',
            key: 'code',
            width: 120,
            render: (text) => <span style={{ fontWeight: 600, color: '#005a8d' }}>{text}</span>,
        },
        {
            title: 'Tên hiển thị',
            dataIndex: 'name',
            key: 'name',
            render: (text, record) => (
                <div>
                    <div style={{ fontWeight: 500 }}>{text || `Khóa ${record.code}`}</div>
                    {record.note && (
                        <div style={{ fontSize: 11, color: '#888' }}>
                            {record.note}
                        </div>
                    )}
                </div>
            ),
        },
        {
            title: 'Năm nhập học',
            dataIndex: 'admissionYear',
            key: 'admissionYear',
            width: 200,
            align: 'center',
            render: (y) => y ? <Tag color="blue" style={{ border: 'none' }}>{y}</Tag> : <span style={{ color: '#ccc' }}>—</span>,
        },
        {
            title: 'Trạng thái',
            dataIndex: 'active',
            key: 'active',
            width: 130,
            align: 'center',
            render: (active) => (
                <Tag color={active === false ? 'default' : 'green'} style={{ border: 'none' }}>
                    {active === false ? 'Ngừng dùng' : 'Đang dùng'}
                </Tag>
            ),
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
                    placeholder="Tìm kiếm theo mã, tên, năm nhập học..."
                    variant="filled"
                    allowClear
                    style={{ width: 320, borderRadius: 6 }}
                    onChange={e => setSearchText(e.target.value)}
                />
                <Space.Compact>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>
                        Thêm niên khóa
                    </Button>
                </Space.Compact>
            </div>

            <Table
                columns={columns}
                dataSource={filteredCohorts}
                rowKey="id"
                loading={loading}
                pagination={{ pageSize: 8, placement: 'bottomRight', style: { marginTop: 24 } }}
            />

            <Modal
                title={editingCohort ? 'Cập nhật Niên khóa' : 'Thêm Niên khóa mới'}
                open={isModalOpen}
                onCancel={() => {
                    setIsModalOpen(false);
                    setEditingCohort(null);
                    form.resetFields();
                }}
                footer={null}
                width={420}
                centered
            >
                <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 20 }}>
                    <Form.Item
                        name="code"
                        label="Mã khóa"
                        rules={[{ required: true, message: 'Vui lòng nhập mã khóa (VD: K18)' }]}
                    >
                        <Input placeholder="VD: K18" variant="filled" />
                    </Form.Item>
                    <Form.Item
                        name="name"
                        label="Tên hiển thị"
                    >
                        <Input placeholder="VD: Đại học chính quy Khóa 17" variant="filled" />
                    </Form.Item>
                    <Form.Item
                        name="admissionYear"
                        label="Năm nhập học"
                        rules={[{ required: true, message: 'Vui lòng nhập năm nhập học' }]}
                    >
                        <InputNumber
                            min={2000}
                            max={2100}
                            style={{ width: '100%' }}
                            placeholder="VD: 2024"
                            variant="filled"
                        />
                    </Form.Item>
                    <Form.Item
                        name="active"
                        label="Trạng thái"
                        valuePropName="checked"
                    >
                        <Switch checkedChildren="Đang dùng" unCheckedChildren="Ngừng dùng" />
                    </Form.Item>
                    <Form.Item
                        name="note"
                        label="Ghi chú"
                    >
                        <Input.TextArea placeholder="Ghi chú thêm (tùy chọn)" autoSize={{ minRows: 2, maxRows: 4 }} />
                    </Form.Item>
                    <Form.Item style={{ textAlign: 'right', marginBottom: 0, marginTop: 8 }}>
                        <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>
                            Hủy
                        </Button>
                        <Button type="primary" htmlType="submit">
                            {editingCohort ? 'Cập nhật' : 'Lưu lại'}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default CohortManagement;

