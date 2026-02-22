import React, { useState, useEffect } from 'react';
import { App, Table, Button, Modal, Form, Input, Select, InputNumber, Upload, Tag, Tooltip } from 'antd';
import { 
    UploadOutlined, 
    PlusOutlined, 
    DownloadOutlined, 
    DeleteOutlined, 
    HomeOutlined,
    EditOutlined 
} from '@ant-design/icons';
import * as RoomService from '../services/roomService';

const { Option } = Select;

const RoomManagement = () => {
    const { message, modal } = App.useApp();
    const [rooms, setRooms] = useState([]);
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [editingRoom, setEditingRoom] = useState(null);
    const [form] = Form.useForm();

    const fetchRooms = async () => {
        setLoading(true);
        try {
            const res = await RoomService.getRooms();
            setRooms(res.data || res);
        } catch {
            message.error('Lỗi tải danh sách phòng');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchRooms(); }, []);

    const handleAddNew = () => {
        setEditingRoom(null);
        form.resetFields();
        form.setFieldsValue({ type: 'LT', capacity: 60 });
        setIsModalOpen(true);
    };

    const handleEdit = (record) => {
        setEditingRoom(record);
        form.setFieldsValue(record);
        setIsModalOpen(true);
    };

    const handleSave = async (values) => {
        try {
            if (editingRoom) {
                await RoomService.updateRoom(editingRoom.id, values);
                message.success('Cập nhật phòng thành công');
            } else {
                await RoomService.createRoom(values);
                message.success('Thêm phòng thành công');
            }
            setIsModalOpen(false);
            setEditingRoom(null);
            form.resetFields();
            fetchRooms();
        } catch {
            message.error(editingRoom ? 'Lỗi cập nhật phòng' : 'Lỗi thêm phòng');
        }
    };

    const handleDelete = async (id) => {
        modal.confirm({
            title: 'Xác nhận xóa',
            content: 'Bạn có chắc muốn xóa phòng học này?',
            okText: 'Xóa', okType: 'danger', cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await RoomService.deleteRoom(id);
                    message.success('Đã xóa phòng');
                    fetchRooms();
                } catch {
                    message.error('Lỗi xóa phòng');
                }
            }
        });
    };

    const handleDownloadTemplate = async () => {
        try {
            const response = await RoomService.downloadTemplate();
            const url = window.URL.createObjectURL(new Blob([response.data]));
            const link = document.createElement('a');
            link.href = url;
            link.setAttribute('download', 'room_template.xlsx');
            document.body.appendChild(link);
            link.click();
            link.remove();
        } catch {
            message.error('Lỗi tải mẫu');
        }
    };

    const handleUpload = async ({ file, onSuccess, onError }) => {
        try {
            await RoomService.importRoom(file);
            message.success('Import thành công');
            fetchRooms();
            onSuccess("Ok");
        } catch (error) {
            message.error('Import thất bại');
            onError(error);
        }
    };

    const columns = [
        {
            title: 'Tên Phòng', dataIndex: 'name', key: 'name', width: '25%',
            render: (text) => (
                <span style={{ fontWeight: 600, color: '#005a8d', display: 'flex', alignItems: 'center', gap: 8 }}>
                  {text}
                </span>
            )
        },
        {
            title: 'Sức chứa', dataIndex: 'capacity', key: 'capacity',
            sorter: (a, b) => a.capacity - b.capacity,
            render: (val) => <span style={{ fontWeight: 500 }}>{val} chỗ</span>
        },
        {
            title: 'Loại phòng', dataIndex: 'type', key: 'type',
            filters: [
                { text: 'Lý thuyết (LT)',         value: 'LT' },
                { text: 'Phòng máy (PM)',          value: 'PM' },
                { text: 'Thí nghiệm (TN)',         value: 'TN' },
                { text: 'Sân bãi / Thể chất (SB)', value: 'SB' },
                { text: 'Xưởng thực hành (XT)',    value: 'XT' },
                { text: 'Bệnh viện / Y tế (BV)',   value: 'BV' },
                { text: 'Thực tập DN (DN)',         value: 'DN' },
                { text: 'Trực tuyến (ONLINE)',      value: 'ONLINE' },
            ],
            onFilter: (value, record) => record.type === value,
            render: (type) => {
                const config = {
                    LT:     { color: '#389e0d', bg: '#f6ffed', text: 'Lý thuyết' },
                    PM:     { color: '#096dd9', bg: '#e6f7ff', text: 'Phòng máy' },
                    TN:     { color: '#d46b08', bg: '#fff7e6', text: 'Thí nghiệm' },
                    SB:     { color: '#08979c', bg: '#e6fffb', text: 'Sân bãi / Thể chất' },
                    XT:     { color: '#874d00', bg: '#fffbe6', text: 'Xưởng thực hành' },
                    BV:     { color: '#c41d7f', bg: '#fff0f6', text: 'Bệnh viện / Y tế' },
                    DN:     { color: '#531dab', bg: '#f9f0ff', text: 'Thực tập DN' },
                    ONLINE: { color: '#0050b3', bg: '#e6f0ff', text: 'Trực tuyến' },
                };
                const c = config[type] || { color: '#666', bg: '#f5f5f5', text: type };
                return <Tag style={{ border: 'none', background: c.bg, color: c.color, fontWeight: 500 }}>{c.text}</Tag>;
            }
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
            ),
        },
    ];

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24, alignItems: 'center' }}>
                <Input placeholder="Tìm kiếm phòng..." variant="filled" style={{ width: 280, borderRadius: 6 }} />
                <div style={{ display: 'flex', gap: 10 }}>
                    <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>Mẫu</Button>
                    <Upload customRequest={handleUpload} showUploadList={false}>
                        <Button icon={<UploadOutlined />}>Import</Button>
                    </Upload>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>Thêm mới</Button>
                </div>
            </div>

            <Table 
                columns={columns} dataSource={rooms} rowKey="id" loading={loading}
                pagination={{ pageSize: 10, placement: 'bottomRight', style: { marginTop: 24 } }}
            />

            <Modal
                title={editingRoom ? "Cập nhật Phòng học" : "Thêm Phòng học mới"}
                open={isModalOpen}
                onCancel={() => { setIsModalOpen(false); setEditingRoom(null); form.resetFields(); }}
                footer={null} width={500} centered
            >
                <Form form={form} layout="vertical" onFinish={handleSave} style={{ marginTop: 20 }}>
                    <Form.Item name="name" label="Tên Phòng" rules={[{ required: true, message: 'Nhập tên phòng' }]}>
                        <Input placeholder="VD: A2-301" variant="filled" />
                    </Form.Item>
                    <div style={{ display: 'flex', gap: 16 }}>
                        <Form.Item name="capacity" label="Sức chứa" style={{ flex: 1 }} rules={[{ required: true }]}>
                            <InputNumber min={1} style={{ width: '100%' }} variant="filled" />
                        </Form.Item>
                        <Form.Item name="type" label="Loại phòng / địa điểm" style={{ flex: 2 }} rules={[{ required: true }]}>
                            <Select variant="filled" placeholder="Chọn loại">
                                <Option value="LT">Lý thuyết — Giảng đường</Option>
                                <Option value="PM">Phòng máy tính</Option>
                                <Option value="TN">Thí nghiệm khoa học</Option>
                                <Option value="SB">Sân bãi / Nhà thể chất</Option>
                                <Option value="XT">Xưởng thực hành</Option>
                                <Option value="BV">Bệnh viện / Cơ sở y tế</Option>
                                <Option value="DN">Cơ sở thực tập DN</Option>
                                <Option value="ONLINE">Môi trường trực tuyến</Option>
                            </Select>
                        </Form.Item>
                    </div>
                    <Form.Item style={{ textAlign: 'right', marginTop: 24, marginBottom: 0 }}>
                        <Button onClick={() => setIsModalOpen(false)} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">{editingRoom ? "Cập nhật" : "Lưu lại"}</Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default RoomManagement;
