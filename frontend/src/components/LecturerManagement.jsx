import React, { useState, useEffect } from 'react';
import { Table, Button, Modal, Form, Input, Select, Upload, message, Space, Tag, Transfer, Drawer, Tooltip } from 'antd';
import { 
    UploadOutlined, 
    PlusOutlined, 
    DownloadOutlined, 
    DeleteOutlined, 
    UserOutlined, 
    BookOutlined,
    EditOutlined
} from '@ant-design/icons';
import * as LecturerService from '../services/lecturerService';
import * as FacultyService from '../services/facultyService';
import * as CourseService from '../services/courseService';

const { Option } = Select;

const LecturerManagement = () => {
    const [lecturers, setLecturers] = useState([]);
    const [faculties, setFaculties] = useState([]);
    const [allCourses, setAllCourses] = useState([]);
    const [searchText, setSearchText] = useState('');
    const [isModalOpen, setIsModalOpen] = useState(false);
    const [isDrawerOpen, setIsDrawerOpen] = useState(false);
    const [loading, setLoading] = useState(false);
    const [editingLecturer, setEditingLecturer] = useState(null);
    const [selectedLecturerForComp, setSelectedLecturerForComp] = useState(null);
    const [targetKeys, setTargetKeys] = useState([]);
    const [form] = Form.useForm();

    const filteredLecturers = lecturers.filter(l =>
        l.name?.toLowerCase().includes(searchText.toLowerCase()) ||
        l.email?.toLowerCase().includes(searchText.toLowerCase()) ||
        l.faculty?.name?.toLowerCase().includes(searchText.toLowerCase())
    );

    const fetchData = async () => {
        setLoading(true);
        try {
            const [lRes, fRes, cRes] = await Promise.all([
                LecturerService.getLecturers(),
                FacultyService.getFaculties(),
                CourseService.getCourses()
            ]);
            setLecturers(Array.isArray(lRes.data) ? lRes.data : []);
            setFaculties(Array.isArray(fRes.data) ? fRes.data : []);
            setAllCourses(Array.isArray(cRes.data) ? cRes.data : []);
        } catch { 
            message.error('Lỗi tải dữ liệu'); 
        } finally {
            setLoading(false);
        }
    };

    useEffect(() => { fetchData(); }, []);

    const handleAddNew = () => {
        setEditingLecturer(null);
        form.resetFields();
        setIsModalOpen(true);
    };

    const handleEditInfo = (record) => {
        setEditingLecturer(record);
        form.setFieldsValue({ ...record, facultyId: record.faculty?.id || record.facultyId });
        setIsModalOpen(true);
    };

    const handleSaveInfo = async (values) => {
        const payload = { ...values, faculty: { id: values.facultyId } };
        try {
            if (editingLecturer) {
                await LecturerService.updateLecturer(editingLecturer.id, payload);
                message.success('Cập nhật thông tin thành công');
            } else {
                await LecturerService.createLecturer(payload);
                message.success('Thêm giảng viên thành công');
            }
            setIsModalOpen(false);
            setEditingLecturer(null);
            form.resetFields();
            fetchData();
        } catch { message.error('Lỗi lưu thông tin'); }
    };

    const handleDelete = async (id) => {
        Modal.confirm({
            title: 'Xác nhận xóa',
            content: 'Bạn có chắc muốn xóa giảng viên này?',
            okText: 'Xóa', okType: 'danger', cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await LecturerService.deleteLecturer(id);
                    message.success('Đã xóa giảng viên');
                    fetchData();
                } catch { message.error('Lỗi xóa giảng viên'); }
            }
        });
    };

    const openCompetencyDrawer = (record) => {
        setSelectedLecturerForComp(record);
        setTargetKeys(record.courses ? record.courses.map(c => c.id.toString()) : []);
        setIsDrawerOpen(true);
    };

    const handleSaveCompetency = async () => {
        try {
            await LecturerService.updateCompetency(selectedLecturerForComp.id, targetKeys.map(k => Number(k)));
            message.success('Cập nhật chuyên môn thành công');
            setIsDrawerOpen(false);
            fetchData();
        } catch { message.error('Lỗi cập nhật chuyên môn'); }
    };

    const handleDownloadTemplate = async () => {
        try {
            const res = await LecturerService.downloadTemplate();
            const url = window.URL.createObjectURL(new Blob([res.data], { type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }));
            const link = document.createElement('a'); link.href = url; link.download = 'lecturer_template.xlsx';
            link.click();
        } catch { message.error('Lỗi tải mẫu'); }
    };

    const handleUpload = async ({ file, onSuccess }) => {
        try {
            await LecturerService.importLecturer(file);
            message.success('Import thành công');
            fetchData();
            onSuccess("ok");
        } catch { message.error('Import lỗi'); }
    };

    const transferDataSource = allCourses.map(c => ({
        key: c.id.toString(),
        title: `${c.code} - ${c.name}`,
        description: c.faculty?.name,
        tag: c.faculty?.code
    }));

    const columns = [
        { 
            title: 'Họ tên', dataIndex: 'name', width: 220,
            render: t => <span style={{fontWeight: 600, color:'#005a8d'}}>{t}</span> 
        },
        { 
            title: 'Email', dataIndex: 'email',
            render: t => <span style={{color: '#666'}}>{t}</span>
        },
        { 
            title: 'Khoa', dataIndex: 'faculty',
            render: f => <Tag style={{border:'none', background:'#f5f5f5', color:'#666'}}>{f ? f.name : '---'}</Tag>
        },
        { 
            title: 'Khả năng giảng dạy', key: 'competency', width: 180,
            render: (_, r) => {
                const count = r.courses?.length || 0;
                return (
                    <Tag color={count > 0 ? "green" : "default"} style={{border:'none', fontWeight: 500}}>
                        {count} môn học
                    </Tag>
                );
            }
        },
        {
            title: 'Thao tác', key: 'action', width: 140, align: 'right',
            render: (_, r) => (
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 8 }}>
                    <Tooltip title="Gán chuyên môn">
                        <Button type="text" style={{color: '#faad14'}} icon={<BookOutlined />} onClick={() => openCompetencyDrawer(r)} />
                    </Tooltip>
                    <Tooltip title="Sửa thông tin">
                        <Button type="text" icon={<EditOutlined />} style={{color: '#666'}} onClick={() => handleEditInfo(r)} />
                    </Tooltip>
                    <Tooltip title="Xóa">
                        <Button type="text" danger icon={<DeleteOutlined />} onClick={() => handleDelete(r.id)} />
                    </Tooltip>
                </div>
            )
        }
    ];

    return (
        <div style={{ width: '100%' }}>
            <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: 24, alignItems: 'center' }}>
                <Input placeholder="Tìm kiếm theo tên, email hoặc khoa..." variant="filled" allowClear style={{ width: 320, borderRadius: 6 }} onChange={e => setSearchText(e.target.value)} />
                <Space.Compact>
                        <Tooltip title="Tải file Excel mẫu">
                            <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>File mẫu</Button>
                        </Tooltip>
                        <Upload customRequest={handleUpload} showUploadList={false}>
                            <Tooltip title="Import danh sách giảng viên từ Excel">
                                <Button icon={<UploadOutlined />}>Import Excel</Button>
                            </Tooltip>
                        </Upload>
                        <Button type="primary" icon={<PlusOutlined />} onClick={handleAddNew}>Thêm mới</Button>
                    </Space.Compact>
            </div>

            <Table 
                dataSource={filteredLecturers} columns={columns} rowKey="id" loading={loading} size="middle"
                pagination={{ pageSize: 8, placement: 'bottomRight', style: {marginTop: 24} }}
            />

            <Modal 
                title={editingLecturer ? "Cập nhật Giảng viên" : "Thêm Giảng viên"} 
                open={isModalOpen} onCancel={() => setIsModalOpen(false)} footer={null} width={500} centered
            >
                <Form form={form} layout="vertical" onFinish={handleSaveInfo} style={{marginTop: 20}}>
                    <Form.Item name="name" label="Họ và Tên" rules={[{required: true, message: 'Nhập họ tên'}]}>
                        <Input placeholder="Nguyễn Văn A" prefix={<UserOutlined style={{color:'#bfbfbf'}} />} variant="filled" />
                    </Form.Item>
                    <Form.Item name="email" label="Email" rules={[{required: true, type: 'email', message: 'Email không hợp lệ'}]}>
                        <Input placeholder="email@phenikaa-uni.edu.vn" variant="filled" />
                    </Form.Item>
                    <Form.Item name="facultyId" label="Thuộc Khoa" rules={[{required: true, message: 'Chọn khoa'}]}>
                        <Select placeholder="Chọn khoa công tác" variant="filled">
                            {faculties.map(f => <Option key={f.id} value={f.id}>{f.name}</Option>)}
                        </Select>
                    </Form.Item>
                    <Form.Item style={{textAlign: 'right', marginTop: 24, marginBottom: 0}}>
                        <Button onClick={() => setIsModalOpen(false)} style={{marginRight: 8}}>Hủy</Button>
                        <Button type="primary" htmlType="submit">
                            {editingLecturer ? "Cập nhật" : "Lưu lại"}
                        </Button>
                    </Form.Item>
                </Form>
            </Modal>

            <Drawer 
                title={
                    <div style={{display:'flex', flexDirection:'column'}}>
                        <span style={{fontSize: 16, fontWeight: 600}}>Phân công chuyên môn</span>
                        <span style={{fontSize: 13, fontWeight: 400, color: '#666'}}>
                            Giảng viên: <span style={{color: '#005a8d'}}>{selectedLecturerForComp?.name}</span>
                        </span>
                    </div>
                }
                width={720} open={isDrawerOpen} onClose={() => setIsDrawerOpen(false)}
                styles={{body: {paddingBottom: 80}}}
                extra={
                    <Space>
                        <Button onClick={() => setIsDrawerOpen(false)}>Hủy</Button>
                        <Button type="primary" onClick={handleSaveCompetency}>Lưu thay đổi</Button>
                    </Space>
                }
            >
                <div style={{marginBottom: 16, color: '#666', fontSize: 13}}>
                    <i>* Chọn các học phần mà giảng viên này có đủ năng lực giảng dạy từ danh sách bên trái.</i>
                </div>
                <Transfer
                    dataSource={transferDataSource}
                    titles={['Kho Học phần', 'Được phân công']}
                    targetKeys={targetKeys}
                    onChange={(nextTargetKeys) => setTargetKeys(nextTargetKeys)}
                    render={(item) => (
                        <div style={{display:'flex', flexDirection:'column'}}>
                            <span>{item.title}</span>
                            <span style={{fontSize: 11, color: '#999'}}>{item.description}</span>
                        </div>
                    )}
                    showSearch
                    listStyle={{ width: '100%', height: 450 }}
                    filterOption={(inputValue, item) => item.title.toLowerCase().indexOf(inputValue.toLowerCase()) > -1}
                />
            </Drawer>
        </div>
    );
};

export default LecturerManagement;
