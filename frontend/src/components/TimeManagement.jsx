import React, { useState, useEffect } from 'react';
import { App, Table, Button, Modal, Form, Input, TimePicker, InputNumber, Tabs, Upload, Tooltip, Tag, Space } from 'antd';
import { 
    PlusOutlined, 
    EditOutlined, 
    DeleteOutlined, 
    UploadOutlined, 
    DownloadOutlined, 
    ClockCircleOutlined 
} from '@ant-design/icons';
import dayjs from 'dayjs';
import * as TimeService from '../services/timeService';

const TimeManagement = () => {
    const { message } = App.useApp();
    const [slots, setSlots] = useState([]);
    const [shifts, setShifts] = useState([]);
    const [loading, setLoading] = useState(false);
    const [activeTab, setActiveTab] = useState('1');
    const [searchText, setSearchText] = useState('');
    const [isSlotModalOpen, setIsSlotModalOpen] = useState(false);
    const [isShiftModalOpen, setIsShiftModalOpen] = useState(false);
    const [editingItem, setEditingItem] = useState(null);
    const [slotForm] = Form.useForm();
    const [shiftForm] = Form.useForm();

    const fetchData = async () => {
        setLoading(true);
        try {
            const [sRes, shRes] = await Promise.all([
                TimeService.getSlots(), 
                TimeService.getShifts()
            ]);
            setSlots((sRes.data || sRes).sort((a, b) => a.periodIndex - b.periodIndex));
            setShifts(shRes.data || shRes);
        } catch {
            message.error('Lỗi tải dữ liệu');
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchData(); }, []);

    const filteredSlots = slots.filter(item => 
        item.name.toLowerCase().includes(searchText.toLowerCase()) || 
        item.periodIndex.toString().includes(searchText)
    );

    const filteredShifts = shifts.filter(item => 
        item.name.toLowerCase().includes(searchText.toLowerCase())
    );

    const handleAddNew = () => {
        if (activeTab === '1') openSlotModal(null);
        else openShiftModal(null);
    };

    const handleSlotSubmit = async (values) => {
        const payload = {
            ...values,
            startTime: values.startTime.format('HH:mm'),
            endTime: values.endTime.format('HH:mm'),
        };
        try {
            if (editingItem) {
                await TimeService.updateSlot(editingItem.id, payload);
                message.success('Cập nhật tiết thành công');
            } else {
                await TimeService.createSlot(payload);
                message.success('Thêm tiết thành công');
            }
            setIsSlotModalOpen(false);
            setEditingItem(null);
            fetchData();
        } catch { message.error('Lỗi lưu tiết học'); }
    };

    const openSlotModal = (record = null) => {
        setEditingItem(record);
        if (record) {
            slotForm.setFieldsValue({
                ...record,
                startTime: dayjs(record.startTime, 'HH:mm'),
                endTime: dayjs(record.endTime, 'HH:mm'),
            });
        } else {
            slotForm.resetFields();
        }
        setIsSlotModalOpen(true);
    };

    const handleShiftSubmit = async (values) => {
        try {
            if (editingItem) {
                await TimeService.updateShift(editingItem.id, values);
                message.success('Cập nhật ca thành công');
            } else {
                await TimeService.createShift(values);
                message.success('Thêm ca thành công');
            }
            setIsShiftModalOpen(false);
            setEditingItem(null);
            fetchData();
        } catch { message.error('Lỗi lưu ca học'); }
    };

    const openShiftModal = (record = null) => {
        setEditingItem(record);
        if (record) shiftForm.setFieldsValue(record);
        else shiftForm.resetFields();
        setIsShiftModalOpen(true);
    };

    const handleUpload = async ({ file, onSuccess }) => {
        try {
            await TimeService.importData(file);
            message.success('Import thành công');
            fetchData();
            onSuccess("Ok");
        } catch { message.error('Import lỗi'); }
    };

    const handleDownloadTemplate = async () => {
        try {
            const res = await TimeService.downloadTemplate();
            const url = window.URL.createObjectURL(new Blob([res.data], { 
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' 
            }));
            const link = document.createElement('a'); link.href = url; link.download = 'time_template.xlsx';
            link.click();
        } catch { message.error('Lỗi tải mẫu'); }
    };

    const slotColumns = [
        { 
            title: 'Tiết số', dataIndex: 'periodIndex', width: 100, align: 'center',
            render: t => <span style={{fontWeight: 600, color: '#005a8d', fontSize: 15}}>{t}</span>
        },
        { 
            title: 'Tên Tiết', dataIndex: 'name',
            render: t => <span style={{fontWeight: 500}}>{t}</span>
        },
        { 
            title: 'Thời gian', key: 'time',
            render: (_, r) => (
                <Tag style={{border: 'none', background: '#f5f5f5', color: '#666', fontSize: 13, padding: '4px 10px'}}>
                    <ClockCircleOutlined style={{marginRight: 6}} />
                    {r.startTime} - {r.endTime}
                </Tag>
            )
        },
        {
            title: 'Hành động', key: 'action', width: 120, align: 'right',
            render: (_, record) => (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Tooltip title="Sửa">
                        <Button type="text" icon={<EditOutlined />} style={{ color: '#666' }} onClick={() => openSlotModal(record)} />
                    </Tooltip>
                    <Tooltip title="Xóa">
                        <Button type="text" danger icon={<DeleteOutlined />} onClick={() => {
                            Modal.confirm({
                                title: 'Xóa tiết học?',
                                content: 'Hành động này không thể hoàn tác.',
                                okText: 'Xóa', okType: 'danger', cancelText: 'Hủy',
                                onOk: () => TimeService.deleteSlot(record.id).then(() => { message.success('Đã xóa'); fetchData(); })
                            });
                        }} />
                    </Tooltip>
                </div>
            )
        }
    ];

    const shiftColumns = [
        { 
            title: 'Tên Ca', dataIndex: 'name',
            render: t => <span style={{fontWeight: 600, color: '#005a8d'}}>{t}</span> 
        },
        { 
            title: 'Phạm vi tiết', key: 'range',
            render: (_, r) => (
                <div style={{display: 'flex', gap: 8}}>
                    <Tag color="blue" style={{border: 'none', background: '#e6f7ff', color: '#005a8d'}}>
                        Bắt đầu: Tiết {r.startPeriod}
                    </Tag>
                    <span style={{color: '#999'}}>→</span>
                    <Tag color="cyan" style={{border: 'none', background: '#e6fffb', color: '#006d75'}}>
                        Kết thúc: Tiết {r.endPeriod}
                    </Tag>
                </div>
            )
        },
        {
            title: 'Hành động', key: 'action', width: 120, align: 'right',
            render: (_, record) => (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Tooltip title="Sửa">
                        <Button type="text" icon={<EditOutlined />} style={{ color: '#666' }} onClick={() => openShiftModal(record)} />
                    </Tooltip>
                    <Tooltip title="Xóa">
                        <Button type="text" danger icon={<DeleteOutlined />} onClick={() => {
                            Modal.confirm({
                                title: 'Xóa ca học?',
                                content: 'Hành động này không thể hoàn tác.',
                                okText: 'Xóa', okType: 'danger', cancelText: 'Hủy',
                                onOk: () => TimeService.deleteShift(record.id).then(() => { message.success('Đã xóa'); fetchData(); })
                            });
                        }} />
                    </Tooltip>
                </div>
            )
        }
    ];

    const items = [
        {
            key: '1',
            label: 'Danh sách Tiết học',
            children: <Table dataSource={filteredSlots} columns={slotColumns} rowKey="id" loading={loading} pagination={false} size="middle" />,
        },
        {
            key: '2',
            label: 'Cấu hình Ca học (Shifts)',
            children: <Table dataSource={filteredShifts} columns={shiftColumns} rowKey="id" loading={loading} pagination={false} size="middle" />,
        },
    ];

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 16, alignItems: 'center' }}>
                <Input 
                    placeholder={activeTab === '1' ? "Tìm kiếm tiết học..." : "Tìm kiếm ca học..."}
                    variant="filled" style={{ width: 300, borderRadius: 6 }}
                    value={searchText} onChange={(e) => setSearchText(e.target.value)} allowClear
                />
                <Space.Compact>
                    <Tooltip title="Tải file Excel mẫu">
                        <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>File mẫu</Button>
                    </Tooltip>
                    <Upload customRequest={handleUpload} showUploadList={false}>
                        <Tooltip title="Import tiết/ca từ Excel">
                            <Button icon={<UploadOutlined />}>Import Excel</Button>
                        </Tooltip>
                    </Upload>
                    <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>
                        {activeTab === '1' ? "Thêm Tiết" : "Thêm Ca"}
                    </Button>
                </Space.Compact>
            </div>

            <Tabs 
                defaultActiveKey="1" items={items}
                onChange={(key) => { setActiveTab(key); setSearchText(''); }}
            />

            <Modal 
                title={editingItem ? "Cập nhật Tiết học" : "Thêm Tiết học"} 
                open={isSlotModalOpen} onCancel={() => setIsSlotModalOpen(false)} footer={null} width={450} centered
            >
                <Form form={slotForm} layout="vertical" onFinish={handleSlotSubmit} style={{marginTop: 20}}>
                    <Form.Item name="periodIndex" label="Tiết số (Index)" rules={[{ required: true, message: 'Nhập số thứ tự' }]}>
                        <InputNumber min={1} style={{ width: '100%' }} variant="filled" placeholder="VD: 1" />
                    </Form.Item>
                    <Form.Item name="name" label="Tên hiển thị" rules={[{ required: true, message: 'Nhập tên tiết' }]}>
                        <Input placeholder="VD: Tiết 1" variant="filled" />
                    </Form.Item>
                    <div style={{ display: 'flex', gap: 16 }}>
                        <Form.Item name="startTime" label="Bắt đầu" style={{flex:1}} rules={[{ required: true, message: 'Chọn giờ' }]}>
                            <TimePicker format="HH:mm" style={{ width: '100%' }} variant="filled" placeholder="07:00" />
                        </Form.Item>
                        <Form.Item name="endTime" label="Kết thúc" style={{flex:1}} rules={[{ required: true, message: 'Chọn giờ' }]}>
                            <TimePicker format="HH:mm" style={{ width: '100%' }} variant="filled" placeholder="07:50" />
                        </Form.Item>
                    </div>
                    <Form.Item style={{ textAlign: 'right', marginTop: 16, marginBottom: 0 }}>
                        <Button onClick={() => setIsSlotModalOpen(false)} style={{marginRight: 8}}>Hủy</Button>
                        <Button type="primary" htmlType="submit">{editingItem ? 'Cập nhật' : 'Lưu lại'}</Button>
                    </Form.Item>
                </Form>
            </Modal>

            <Modal 
                title={editingItem ? "Cập nhật Ca học" : "Thêm Ca học"} 
                open={isShiftModalOpen} onCancel={() => setIsShiftModalOpen(false)} footer={null} width={450} centered
            >
                <Form form={shiftForm} layout="vertical" onFinish={handleShiftSubmit} style={{marginTop: 20}}>
                    <Form.Item name="name" label="Tên Ca" rules={[{ required: true, message: 'Nhập tên ca' }]}>
                        <Input placeholder="VD: Ca Sáng" variant="filled" />
                    </Form.Item>
                    <div style={{ display: 'flex', gap: 16 }}>
                        <Form.Item name="startPeriod" label="Từ tiết" style={{flex:1}} rules={[{ required: true, message: 'Chọn tiết' }]}>
                            <InputNumber min={1} style={{ width: '100%' }} variant="filled" />
                        </Form.Item>
                        <Form.Item name="endPeriod" label="Đến tiết" style={{flex:1}} rules={[{ required: true, message: 'Chọn tiết' }]}>
                            <InputNumber min={1} style={{ width: '100%' }} variant="filled" />
                        </Form.Item>
                    </div>
                    <Form.Item style={{ textAlign: 'right', marginTop: 16, marginBottom: 0 }}>
                        <Button onClick={() => setIsShiftModalOpen(false)} style={{marginRight: 8}}>Hủy</Button>
                        <Button type="primary" htmlType="submit">{editingItem ? 'Cập nhật' : 'Lưu lại'}</Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default TimeManagement;
