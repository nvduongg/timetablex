import React, { useState, useEffect } from 'react';
import {
    Button, Card, Form, Select, Input, message, Typography, Row, Col, Tag, Empty, Upload, Modal, Flex
} from 'antd';
import { PlusOutlined, DeleteOutlined, UploadOutlined, DownloadOutlined, SearchOutlined, ArrowLeftOutlined } from '@ant-design/icons';
import * as CurriculumService from '../services/curriculumService';
import * as CourseService from '../services/courseService';

const { Text } = Typography;
const { Option } = Select;

const RoadmapManagement = ({ initialCurrId = null, onBack }) => {
    const [curriculums, setCurriculums] = useState([]);
    const [selectedCurrData, setSelectedCurrData] = useState(null);
    const [courses, setCourses] = useState([]);
    const [courseSearchText, setCourseSearchText] = useState('');
    const [isAddCourseModalOpen, setIsAddCourseModalOpen] = useState(false);
    const [addCourseForm] = Form.useForm();

    const selectedCurrId = selectedCurrData?.id?.toString();

    const fetchCurriculums = async () => {
        try {
            const res = await CurriculumService.getCurriculums();
            setCurriculums(Array.isArray(res?.data) ? res.data : []);
        } catch (e) {
            message.error('Lỗi tải danh sách CTĐT');
        }
    };

    const fetchCourses = async () => {
        try {
            const res = await CourseService.getCourses();
            setCourses(Array.isArray(res?.data) ? res.data : []);
        } catch (e) {
            message.error('Không tải được danh sách môn học.');
        }
    };

    useEffect(() => {
        fetchCurriculums();
    }, []);

    useEffect(() => {
        if (initialCurrId) {
            const curr = curriculums.find(c => c.id?.toString() === String(initialCurrId));
            setSelectedCurrData(curr);
        }
    }, [initialCurrId, curriculums]);

    // Đồng bộ selectedCurrData khi fetch lại (sau add/remove/import)
    useEffect(() => {
        if (selectedCurrData?.id && curriculums.length > 0) {
            const fresh = curriculums.find(c => c.id === selectedCurrData.id);
            if (fresh) setSelectedCurrData(fresh);
        }
    }, [curriculums]);

    const handleSelectCurriculum = (curr) => {
        setSelectedCurrData(curr);
    };

    const handleRemoveDetail = (detailId) => {
        Modal.confirm({
            title: 'Xóa môn học?',
            content: 'Bạn muốn xóa môn này khỏi học kỳ hiện tại?',
            okText: 'Xóa',
            okType: 'danger',
            cancelText: 'Hủy',
            onOk: async () => {
                try {
                    await CurriculumService.removeDetail(detailId);
                    message.success('Đã xóa môn học');
                    fetchCurriculums();
                } catch (e) {
                    message.error('Lỗi xóa chi tiết');
                }
            }
        });
    };

    const handleDownloadTemplate = async () => {
        try {
            const res = await CurriculumService.downloadRoadmapTemplate();
            const url = window.URL.createObjectURL(new Blob([res.data], {
                type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
            }));
            const link = document.createElement('a');
            link.href = url;
            link.download = 'roadmap_template.xlsx';
            link.click();
            window.URL.revokeObjectURL(url);
            message.success('Đã tải mẫu Excel');
        } catch (e) {
            message.error('Lỗi tải mẫu');
        }
    };

    const handleImportRoadmap = async ({ file, onSuccess }) => {
        if (!selectedCurrId) return;
        try {
            await CurriculumService.importRoadmap(selectedCurrId, file);
            message.success('Import lộ trình thành công');
            fetchCurriculums();
            onSuccess('ok');
        } catch (e) {
            message.error('Import thất bại');
        }
    };

    const handleAddCourse = async (values) => {
        if (!selectedCurrId) return;
        try {
            await CurriculumService.addDetail(selectedCurrId, values.courseId, String(values.semesterIndex));
            message.success('Đã thêm môn học vào lộ trình');
            setIsAddCourseModalOpen(false);
            addCourseForm.resetFields();
            fetchCurriculums();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Thêm môn thất bại');
        }
    };

    const availableCourses = courses.filter(c =>
        !selectedCurrData?.details?.some(d => d.course?.id === c.id)
    );

    const isDetailInSemester = (detail, semester) => {
        if (!detail?.semesterIndex) return false;
        const tokens = String(detail.semesterIndex).split(',').map(t => t.trim()).filter(Boolean);
        return tokens.includes(String(semester));
    };

    // Lọc môn học theo tìm kiếm (mã hoặc tên)
    const matchesCourseSearch = (detail) => {
        if (!courseSearchText.trim()) return true;
        const q = courseSearchText.toLowerCase().trim();
        const code = (detail?.course?.code || '').toLowerCase();
        const name = (detail?.course?.name || '').toLowerCase();
        return code.includes(q) || name.includes(q);
    };

    // 3 kỳ/năm: HK 1-3 = Năm 1, 4-6 = Năm 2, 7-9 = Năm 3, 10-12 = Năm 4
    const getSemesterLabel = (n) => {
        const year = Math.ceil(n / 3);
        const term = ((n - 1) % 3) + 1;
        return `Năm ${year} - Kỳ ${term}`;
    };

    return (
        <div style={{ width: '100%' }}>
            {/* Toolbar */}
            <div style={{ display: 'flex', flexWrap: 'wrap', gap: 12, alignItems: 'center', justifyContent: 'space-between', marginBottom: 24 }}>
                <Flex wrap="wrap" gap={12} align="center">
                    {onBack && (
                        <Button icon={<ArrowLeftOutlined />} onClick={onBack}>
                            Quay lại
                        </Button>
                    )}
                    <Select
                        placeholder="Chọn chương trình đào tạo"
                        showSearch
                        optionFilterProp="label"
                        value={selectedCurrData ? selectedCurrData.id : undefined}
                        onChange={(id) => handleSelectCurriculum(curriculums.find(c => c.id === id))}
                        style={{ width: 320, minWidth: 260 }}
                        options={curriculums.map(c => ({
                            value: c.id,
                            label: `${c.name} (Khóa ${c.cohort || '-'})`,
                            key: c.id
                        }))}
                        notFoundContent="Không tìm thấy CTĐT"
                    />
                    {selectedCurrData && (
                        <Input
                            placeholder="Tìm môn học (mã hoặc tên)..."
                            prefix={<SearchOutlined style={{ color: '#b0b0b0' }} />}
                            variant="filled"
                            value={courseSearchText}
                            onChange={e => setCourseSearchText(e.target.value)}
                            style={{ width: 280, borderRadius: 6 }}
                            allowClear
                        />
                    )}
                </Flex>
                {selectedCurrData && (
                    <Flex wrap="wrap" gap={8} align="center">
                        <Button icon={<PlusOutlined />} onClick={() => setIsAddCourseModalOpen(true)}>
                            Thêm môn
                        </Button>
                        <Button icon={<DownloadOutlined />} onClick={handleDownloadTemplate}>
                            Mẫu Excel
                        </Button>
                        <Upload customRequest={handleImportRoadmap} showUploadList={false}>
                            <Button type="primary" icon={<UploadOutlined />}>
                                Import
                            </Button>
                        </Upload>
                    </Flex>
                )}
            </div>

            {/* Content */}
            {!selectedCurrData ? (
                <div style={{ padding: 80, textAlign: 'center', background: '#fafafa', borderRadius: 8, border: '1px dashed #e8e8e8' }}>
                    <Empty
                        image={Empty.PRESENTED_IMAGE_SIMPLE}
                        description="Chọn chương trình đào tạo từ dropdown để xem Khung CTĐT"
                    />
                </div>
            ) : (
                <div>
                    {/* Header CTĐT */}
                    <div style={{
                        padding: '16px 20px',
                        marginBottom: 20,
                        background: 'linear-gradient(135deg, #f0f7ff 0%, #ffffff 100%)',
                        borderRadius: 8,
                        border: '1px solid #e6f4ff'
                    }}>
                        <Flex wrap="wrap" gap={12} align="center">
                            <Text strong style={{ fontSize: 18, color: '#005a8d' }}>{selectedCurrData.name}</Text>
                            <Tag style={{ border: 'none', background: '#e6f4ff', color: '#005a8d', fontWeight: 500 }}>Khóa {selectedCurrData.cohort}</Tag>
                            <Tag style={{ border: 'none', background: '#f5f5f5', color: '#595959' }}>{selectedCurrData.major?.name}</Tag>
                            <Tag color="blue" style={{ border: 'none', fontWeight: 500 }}>
                                {selectedCurrData.details?.length || 0} môn
                            </Tag>
                        </Flex>
                    </div>

                    {/* Lưới học kỳ - Chỉ hiển thị các kỳ có môn học */}
                    <Row gutter={[16, 16]}>
                        {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].filter(sem => {
                            // Chỉ hiển thị kỳ có môn học (và khớp với tìm kiếm nếu có)
                            const hasCourses = (selectedCurrData?.details || []).some(d => 
                                isDetailInSemester(d, sem) && matchesCourseSearch(d)
                            );
                            return hasCourses;
                        }).map(sem => {
                            const details = (selectedCurrData?.details || [])
                                .filter(d => isDetailInSemester(d, sem) && matchesCourseSearch(d));
                            const totalCredits = details.reduce((sum, d) => sum + (d.course?.credits || 0), 0);

                            return (
                                <Col xs={24} sm={24} md={12} lg={12} xl={8} key={sem}>
                                    <Card
                                        variant="borderless"
                                        size="small"
                                        title={
                                            <Flex justify="space-between" align="center">
                                                <Text strong style={{ color: '#005a8d', fontSize: 14 }}>
                                                    HK{sem} · {getSemesterLabel(sem)}
                                                </Text>
                                                <Tag color="blue" style={{ border: 'none', margin: 0 }}>
                                                    {totalCredits} TC
                                                </Tag>
                                            </Flex>
                                        }
                                        styles={{ body: { padding: '12px 16px' } }}
                                        style={{
                                            background: details.length > 0 ? '#ffffff' : '#fafafa',
                                            borderRadius: 8,
                                            height: '100%',
                                            border: details.length === 0 ? '1px dashed #d9d9d9' : '1px solid #f0f0f0',
                                            boxShadow: details.length > 0 ? '0 1px 2px rgba(0,0,0,0.04)' : 'none'
                                        }}
                                    >
                                        <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                                            {details.length === 0 ? (
                                                <div style={{ padding: 16, textAlign: 'center', color: '#999', fontSize: 13 }}>
                                                    Chưa có môn · Nhấn "Thêm môn" để bổ sung
                                                </div>
                                            ) : details.map(d => (
                                                <Flex
                                                    key={d.id}
                                                    justify="space-between"
                                                    align="center"
                                                    style={{
                                                        padding: '10px 12px',
                                                        background: '#fafbff',
                                                        borderRadius: 6,
                                                        border: '1px solid #e8eeff'
                                                    }}
                                                >
                                                    <div style={{ flex: 1, minWidth: 0 }}>
                                                        <Text strong style={{ fontSize: 12, color: '#666' }}>{d.course?.code}</Text>
                                                        <br />
                                                        <Text ellipsis style={{ fontSize: 13 }}>{d.course?.name}</Text>
                                                    </div>
                                                    <Flex align="center" gap={8}>
                                                        <Tag style={{ border: 'none', margin: 0 }}>{d.course?.credits} TC</Tag>
                                                        <Button
                                                            type="text"
                                                            danger
                                                            size="small"
                                                            icon={<DeleteOutlined />}
                                                            onClick={() => handleRemoveDetail(d.id)}
                                                        />
                                                    </Flex>
                                                </Flex>
                                            ))}
                                        </div>
                                    </Card>
                                </Col>
                            );
                        })}
                    </Row>

                    {(!selectedCurrData.details || selectedCurrData.details.length === 0) && (
                        <div style={{ padding: 48, textAlign: 'center', background: '#fafafa', borderRadius: 8, border: '1px dashed #e8e8e8', marginTop: 16 }}>
                            <Empty
                                image={Empty.PRESENTED_IMAGE_SIMPLE}
                                description="Chưa có môn học. Import Excel hoặc thêm môn thủ công."
                            />
                        </div>
                    )}
                </div>
            )}

            {/* Modal thêm môn */}
            <Modal
                title="Thêm môn học"
                open={isAddCourseModalOpen}
                onCancel={() => { setIsAddCourseModalOpen(false); addCourseForm.resetFields(); }}
                afterOpenChange={(open) => open && fetchCourses()}
                footer={null}
                width={400}
                centered
            >
                <Form form={addCourseForm} layout="vertical" onFinish={handleAddCourse} style={{ marginTop: 16 }}>
                    <Form.Item name="semesterIndex" label="Học kỳ (3 kỳ/năm)" rules={[{ required: true }]}>
                        <Select placeholder="Chọn học kỳ" variant="filled">
                            {[1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12].map(s => (
                                <Option key={s} value={s}>HK{s} · {getSemesterLabel(s)}</Option>
                            ))}
                        </Select>
                    </Form.Item>
                    <Form.Item name="courseId" label="Môn học" rules={[{ required: true, message: 'Chọn môn học' }]}>
                        <Select
                            placeholder="Chọn môn học"
                            variant="filled"
                            showSearch
                            optionFilterProp="label"
                            notFoundContent={courses.length === 0 ? 'Chưa có môn học.' : 'Tất cả môn đã có trong khung.'}
                            options={availableCourses.map(c => ({
                                value: c.id,
                                label: `${c.code || ''} - ${c.name || ''}`,
                                key: c.id
                            }))}
                        />
                    </Form.Item>
                    <Form.Item style={{ textAlign: 'right', marginBottom: 0, marginTop: 24 }}>
                        <Button onClick={() => { setIsAddCourseModalOpen(false); addCourseForm.resetFields(); }} style={{ marginRight: 8 }}>Hủy</Button>
                        <Button type="primary" htmlType="submit">Thêm vào lộ trình</Button>
                    </Form.Item>
                </Form>
            </Modal>
        </div>
    );
};

export default RoadmapManagement;
