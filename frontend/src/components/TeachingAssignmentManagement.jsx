import React, { useState, useEffect } from 'react';
import { App, Table, Select, Tag, Space, Alert, Tooltip, Button, Modal, Input, Flex, Typography } from 'antd';
import { UserOutlined, FilterOutlined, ThunderboltOutlined, BarChartOutlined, QuestionCircleOutlined } from '@ant-design/icons';
import * as SemesterService from '../services/semesterService';
import * as FacultyService from '../services/facultyService';
import * as ClassSectionService from '../services/classSectionService';
import * as LecturerService from '../services/lecturerService';
import TeachingLoadStatsModal from './TeachingLoadStatsModal';

const { Option } = Select;
const { Text } = Typography;

/**
 * Module 3: Phân công giảng dạy – Khoa/Viện gán Giảng viên vào từng lớp học phần.
 * Có tùy chọn "Bỏ qua" để phân công sau khi có TKB dự kiến (nếu Khoa được phép).
 */
const TeachingAssignmentManagement = ({ auth }) => {
    const { message } = App.useApp();
    const isFaculty = auth?.role === 'FACULTY';
    const defaultFacultyId = auth?.facultyId || null;

    const [sections, setSections] = useState([]);
    const [semesters, setSemesters] = useState([]);
    const [faculties, setFaculties] = useState([]);
    const [lecturers, setLecturers] = useState([]);
    const [currentSemesterId, setCurrentSemesterId] = useState(null);
    const [currentFacultyId, setCurrentFacultyId] = useState(defaultFacultyId);
    const [loading, setLoading] = useState(false);
    const [skipAllowed, setSkipAllowed] = useState(false);
    const [teachingLoad, setTeachingLoad] = useState([]);
    const [statsModalOpen, setStatsModalOpen] = useState(false);
    const [supportModalOpen, setSupportModalOpen] = useState(false);
    const [selectedSectionForSupport, setSelectedSectionForSupport] = useState(null);
    const [supportComment, setSupportComment] = useState('');
    const [selectedRowKeys, setSelectedRowKeys] = useState([]);
    const [statusFilter, setStatusFilter] = useState('ALL');

    useEffect(() => {
        SemesterService.getSemesters().then(res => {
            setSemesters(res.data || []);
            const active = (res.data || []).find(s => s.isActive);
            if (active) setCurrentSemesterId(active.id);
        });
        if (!isFaculty) FacultyService.getFaculties().then(res => setFaculties(res.data || []));
    }, [isFaculty]);

    useEffect(() => {
        if (isFaculty && defaultFacultyId) setCurrentFacultyId(defaultFacultyId);
    }, [isFaculty, defaultFacultyId]);

    const fetchSections = async () => {
        if (!currentSemesterId || (!currentFacultyId && isFaculty)) return;
        setLoading(true);
        try {
            const res = await ClassSectionService.getClassSectionsBySemester(currentSemesterId, currentFacultyId);
            const data = res.data || [];
            setSections(data);
            setSelectedRowKeys([]); // reset lựa chọn khi reload danh sách
        } catch {
            message.error('Lỗi tải danh sách lớp học phần');
        } finally {
            setLoading(false);
        }
    };

    const fetchLecturers = async () => {
        if (!currentFacultyId) return;
        try {
            const res = await LecturerService.getLecturers(currentFacultyId, {
                semesterId: currentSemesterId,
                forAssignment: true
            });
            setLecturers(res.data || []);
        } catch {
            setLecturers([]);
        }
    };

    const fetchTeachingLoad = async () => {
        if (!currentSemesterId) return;
        if (isFaculty && !currentFacultyId) return;
        try {
            const res = await ClassSectionService.getTeachingLoad(currentSemesterId, currentFacultyId);
            setTeachingLoad(res.data || []);
        } catch {
            setTeachingLoad([]);
        }
    };

    useEffect(() => {
        fetchSections();
        if (currentFacultyId) {
            fetchLecturers();
            fetchTeachingLoad();
            ClassSectionService.isFacultySkipAllowed(currentFacultyId)
                .then(r => setSkipAllowed(r.data?.allowed ?? false))
                .catch(() => setSkipAllowed(false));
        }
    }, [currentSemesterId, currentFacultyId]);

    const handleAutoAssign = async () => {
        if (!currentSemesterId || (!currentFacultyId && isFaculty)) {
            message.warning('Vui lòng chọn học kỳ và Khoa');
            return;
        }
        setLoading(true);
        try {
            const res = await ClassSectionService.autoAssignLecturers(currentSemesterId, currentFacultyId);
            const count = res.data?.assignedCount ?? 0;
            message.success(res.data?.message || `Đã tự động phân công ${count} lớp`);
            fetchSections();
            fetchTeachingLoad();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi tự động phân công');
        } finally {
            setLoading(false);
        }
    };

    const handleAssign = async (sectionId, lecturerId, skip) => {
        try {
            await ClassSectionService.assignLecturerToSection(sectionId, {
                lecturerId: skip ? null : lecturerId,
                skipAssignment: !!skip
            });
            message.success('Đã cập nhật phân công');
            fetchSections();
            fetchTeachingLoad();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi cập nhật phân công');
        }
    };

    const handleOpenStats = async () => {
        await fetchTeachingLoad();
        setStatsModalOpen(true);
    };

    const handleRequestSupport = (section) => {
        setSelectedSectionForSupport(section);
        setSupportComment('');
        setSupportModalOpen(true);
    };

    const handleSubmitSupportRequest = async () => {
        if (!selectedSectionForSupport) return;
        try {
            const ids = selectedSectionForSupport._bulkIds || [selectedSectionForSupport.id];
            await Promise.all(ids.map(id => ClassSectionService.requestSupport(id, supportComment)));
            message.success(`Đã gửi yêu cầu hỗ trợ GV cho ${ids.length} lớp tới P.ĐT`);
            setSupportModalOpen(false);
            setSelectedSectionForSupport(null);
            setSupportComment('');
            setSelectedRowKeys([]);
            fetchSections();
        } catch (e) {
            message.error(e?.response?.data?.message || 'Lỗi gửi yêu cầu hỗ trợ');
        }
    };

    // Gửi yêu cầu hỗ trợ hàng loạt cho các lớp đang chọn và chưa được phân công GV
    const handleBulkRequestSupport = async () => {
        const target = sections.filter(
            s =>
                selectedRowKeys.includes(s.id) &&
                !s.lecturer &&
                !s.skipAssignment &&
                !s.needsSupport
        );
        if (target.length === 0) {
            message.info('Không có lớp nào chưa phân công để gửi yêu cầu hỗ trợ');
            return;
        }
        setSupportComment('');
        // Lưu tạm một bản ghi đại diện (hiển thị trong modal), phần gửi sẽ áp dụng cho tất cả
        setSelectedSectionForSupport({
            ...target[0],
            _bulkIds: target.map(t => t.id),
        });
        setSupportModalOpen(true);
    };

    const rowSelection = {
        selectedRowKeys,
        onChange: (keys) => setSelectedRowKeys(keys),
    };

    const filteredSections = sections.filter(s => {
        switch (statusFilter) {
            case 'UNASSIGNED':
                // Chưa phân công: không có GV và không skip (bao gồm cả các lớp cần hỗ trợ)
                return !s.lecturer && !s.skipAssignment;
            case 'ASSIGNED':
                return !!s.lecturer;
            case 'SKIPPED':
                return !!s.skipAssignment;
            case 'NEED_SUPPORT':
                // Các lớp đã yêu cầu hỗ trợ nhưng chưa được phân công
                return !!s.needsSupport && !s.lecturer;
            default:
                return true;
        }
    });

    const columns = [
        { title: 'Mã lớp HP', dataIndex: 'code', width: 130, render: t => <span style={{ fontWeight: 600, color: '#005a8d' }}>{t}</span> },
        {
            title: 'Loại', dataIndex: 'sectionType', width: 90,
            render: t => <Tag color={t === 'LT' ? 'geekblue' : 'purple'} style={{ border: 'none' }}>{t === 'LT' ? 'LT' : 'TH'}</Tag>
        },
        {
            title: 'Học phần', key: 'course',
            render: (_, r) => (
                <Space direction="vertical" size={0}>
                    <span style={{ fontWeight: 500 }}>{r.courseOffering?.course?.code || '—'}</span>
                    <span style={{ fontSize: 12, color: '#666' }}>{r.courseOffering?.course?.name || '—'}</span>
                </Space>
            )
        },
        {
            title: 'Giảng viên phân công', key: 'lecturer', width: 380,
            render: (_, r) => {
                const courseId = r.courseOffering?.course?.id;
                const courseOfferingFacultyId = r.courseOffering?.faculty?.id; // Khoa yêu cầu
                const courseManagingFacultyId = r.courseOffering?.course?.faculty?.id; // Khoa quản lý chuyên môn
                const currentUserFacultyId = currentFacultyId; // Khoa hiện tại đang đăng nhập

                // Kiểm tra xem lớp này có phải từ khoa khác yêu cầu hỗ trợ không
                const isFromOtherFaculty = r.needsSupport && courseOfferingFacultyId !== currentUserFacultyId;

                // Lọc GV có chuyên môn dạy môn này
                const qualifiedLecturers = lecturers.filter(l =>
                    l.courses && l.courses.some(c => c.id === courseId)
                );

                // Phân loại: Khoa quản lý chuyên môn (khoa hiện tại) vs Khoa khác
                // Đối với lớp cần hỗ trợ, dùng course.faculty (khoa quản lý chuyên môn)
                // Đối với lớp bình thường, dùng courseOffering.faculty (khoa phụ trách kế hoạch)
                const referenceFacultyId = r.needsSupport ? courseManagingFacultyId : courseOfferingFacultyId;
                const isOwnFaculty = (l) => l.faculty?.id === referenceFacultyId || l.faculty?.id === currentUserFacultyId;
                const ownFacultyLecturers = qualifiedLecturers.filter(isOwnFaculty);
                const otherFacultyLecturers = qualifiedLecturers.filter(l => !isOwnFaculty(l));

                return (
                    <Space direction="vertical" size={4} style={{ width: '100%' }}>
                        {isFromOtherFaculty && (
                            <div style={{ marginBottom: 4 }}>
                                <Tag color="red" icon={<QuestionCircleOutlined />} style={{ fontSize: 11 }}>
                                    Yêu cầu hỗ trợ từ {r.courseOffering?.faculty?.name}
                                </Tag>
                                {r.supportRequestComment && (
                                    <div style={{ fontSize: 11, color: '#666', fontStyle: 'italic', marginTop: 2 }}>
                                        {r.supportRequestComment}
                                    </div>
                                )}
                            </div>
                        )}
                        <Select
                            placeholder="Chọn giảng viên"
                            variant="filled"
                            style={{ width: '100%', minWidth: 250 }}
                            allowClear
                            showSearch
                            optionFilterProp="label"
                            optionLabelProp="label" // Fix: Hiển thị tên (label) thay vì ID
                            value={r.skipAssignment ? '__SKIP__' : (r.lecturer?.id ?? null)}
                            onChange={(val) => {
                                if (val === '__SKIP__') handleAssign(r.id, null, true);
                                else handleAssign(r.id, val || null, false);
                            }}
                            dropdownRender={(menu) => (
                                <>
                                    {menu}
                                    {otherFacultyLecturers.length > 0 && (
                                        <div style={{ padding: '8px 12px', borderTop: '1px solid #f0f0f0', background: '#fff7e6', fontSize: 11, color: '#666' }}>
                                            <Tag color="orange" style={{ fontSize: 10, marginRight: 4 }}>Chéo khoa</Tag>
                                            GV từ khoa khác có chuyên môn dạy môn này
                                        </div>
                                    )}
                                </>
                            )}
                        >
                            {skipAllowed && <Option value="__SKIP__"><Tag color="orange">Bỏ qua (phân công sau)</Tag></Option>}
                            <Option value={null}>— Chưa phân công —</Option>
                            {ownFacultyLecturers.length > 0 && (
                                <Option disabled style={{ fontWeight: 600, color: '#333', fontSize: 11, padding: '4px 0' }}>
                                    ── {r.needsSupport ? (r.courseOffering?.course?.faculty?.name || 'Khoa quản lý chuyên môn') : (r.courseOffering?.faculty?.name || 'Khoa chủ quản')} ──
                                </Option>
                            )}
                            {ownFacultyLecturers.map(l => (
                                <Option key={l.id} value={l.id} label={`${l.name || 'N/A'} (${l.faculty?.code || ''})`}>
                                    <Space size={4}>
                                        <span>{l.name || `GV #${l.id}`}</span>
                                        <Tag color="blue" style={{ fontSize: 10, border: 'none', margin: 0, padding: '0 4px' }}>
                                            {l.faculty?.code || ''}
                                        </Tag>
                                    </Space>
                                </Option>
                            ))}
                            {otherFacultyLecturers.length > 0 && (
                                <Option disabled style={{ fontWeight: 600, color: '#666', fontSize: 11, padding: '4px 0', marginTop: 4 }}>
                                    ── Khoa khác (dùng chung) ──
                                </Option>
                            )}
                            {otherFacultyLecturers.map(l => (
                                <Option key={l.id} value={l.id} label={`${l.name || 'N/A'} (${l.faculty?.code || ''})`}>
                                    <Space size={4}>
                                        <span>{l.name || `GV #${l.id}`}</span>
                                        <Tag color="orange" style={{ fontSize: 10, border: 'none', margin: 0, padding: '0 4px' }}>
                                            {l.faculty?.code || ''}
                                        </Tag>
                                    </Space>
                                </Option>
                            ))}
                            {qualifiedLecturers.length === 0 && (
                                <Option disabled style={{ color: '#999', fontSize: 12 }}>
                                    Không có GV nào có chuyên môn dạy môn này
                                </Option>
                            )}
                        </Select>
                    </Space>
                );
            }
        },
        {
            title: 'Trạng thái', key: 'status', width: 130,
            render: (_, r) => {
                if (r.skipAssignment) return <Tag color="orange">Bỏ qua</Tag>;
                if (r.lecturer) return <Tag color="green">Đã phân công</Tag>;
                return <Tag>Chưa phân công</Tag>;
            }
        }
    ];

    const currentSemester = semesters.find(s => s.id === currentSemesterId);
    const currentFaculty = faculties.find(f => f.id === currentFacultyId);

    return (
        <div style={{ width: '100%' }}>
            <Flex
                justify="space-between"
                align="center"
                wrap="nowrap"
                style={{
                    marginBottom: 24,
                }}
            >
                <Space size={8}>
                    {/* Học kỳ */}
                    <Select
                        variant="filled"
                        style={{ width: 180 }}
                        value={currentSemesterId}
                        onChange={setCurrentSemesterId}
                        placeholder="Học kỳ"
                        optionLabelProp="label"
                    >
                        {semesters.map(s => (
                            <Option key={s.id} value={s.id} label={s.name}>
                                <Flex justify="space-between" align="center" style={{ width: '100%' }}>
                                    <Text>{s.name}</Text>
                                    {s.isActive && (
                                        <Text style={{ fontSize: 11, color: '#52c41a', display: 'flex', alignItems: 'center', gap: 4 }}>
                                            <span style={{ fontSize: 14 }}>●</span> Hiện tại
                                        </Text>
                                    )}
                                </Flex>
                            </Option>
                        ))}
                    </Select>

                    {/* Chọn Khoa (Nếu là PĐT) */}
                    {!isFaculty ? (
                        <Select
                            variant="filled"
                            style={{ width: 200 }}
                            value={currentFacultyId}
                            onChange={setCurrentFacultyId}
                            placeholder="Chọn Khoa/Viện"
                            showSearch
                            optionFilterProp="children"
                        >
                            {faculties.map(f => <Option key={f.id} value={f.id}>{f.name} ({f.code})</Option>)}
                        </Select>
                    ) : (
                        currentFacultyId && (
                            <Tag
                                style={{
                                    margin: 0,
                                    height: 38,
                                    display: 'flex',
                                    alignItems: 'center',
                                    padding: '0 16px',
                                    background: '#f2f4f6',
                                    border: 'none',
                                    color: '#005a8d',
                                    fontWeight: 500,
                                    fontSize: 14,
                                    borderRadius: 6
                                }}
                            >
                                {auth?.facultyName || 'Khoa của tôi'}
                            </Tag>
                        )
                    )}

                    {/* Trạng thái phân công */}
                    <Select
                        variant="filled"
                        style={{ width: 170 }}
                        value={statusFilter}
                        onChange={setStatusFilter}
                    >
                        <Option value="ALL">Tất cả trạng thái</Option>
                        <Option value="UNASSIGNED">Chưa phân công</Option>
                        <Option value="ASSIGNED">Đã phân công</Option>
                        <Option value="SKIPPED">Bỏ qua</Option>
                        <Option value="NEED_SUPPORT">Đã yêu cầu hỗ trợ</Option>
                    </Select>
                </Space>

                <Space size={8}>
                    <Tooltip title="Tự động gán giảng viên theo chuyên môn & cân bằng tải">
                        <Button
                            type="primary"
                            icon={<ThunderboltOutlined />}
                            onClick={handleAutoAssign}
                            loading={loading}
                            disabled={!currentSemesterId || (!currentFacultyId && isFaculty)}
                        >
                            Tự động gán
                        </Button>
                    </Tooltip>

                    <Button
                        icon={<QuestionCircleOutlined />}
                        onClick={handleBulkRequestSupport}
                        disabled={selectedRowKeys.length === 0}
                    >
                        Hỗ trợ ({selectedRowKeys.length})
                    </Button>

                    <Button
                        icon={<BarChartOutlined />}
                        onClick={handleOpenStats}
                        disabled={!currentSemesterId || (!currentFacultyId && isFaculty)}
                    >
                        Thống kê tải
                    </Button>
                </Space>
            </Flex>

            <Alert title="Module 3: Phân công giảng dạy" description="Tự động phân công dựa trên Khoa + Chuyên môn giảng viên, cân bằng tải. Sau khi chạy có thể chỉnh sửa thủ công. Hoàn thành trong 1 tuần."
                type="info" showIcon icon={<UserOutlined />} style={{ marginBottom: 20, border: 'none', background: '#e6f7ff' }} />

            <TeachingLoadStatsModal
                open={statsModalOpen}
                onClose={() => setStatsModalOpen(false)}
                teachingLoad={teachingLoad}
                semesterName={currentSemester?.name}
                facultyName={currentFaculty?.name || auth?.facultyName}
            />

            <Modal
                title="Yêu cầu hỗ trợ Giảng viên"
                open={supportModalOpen}
                onOk={handleSubmitSupportRequest}
                onCancel={() => {
                    setSupportModalOpen(false);
                    setSelectedSectionForSupport(null);
                    setSupportComment('');
                }}
                okText="Gửi yêu cầu"
                cancelText="Hủy"
                width={720}
                styles={{
                    body: { padding: '18px 24px', background: '#fafbff' }
                }}
            >
                {selectedSectionForSupport && (
                    <Space direction="vertical" size={18} style={{ width: '100%' }}>
                        <div
                            style={{
                                display: 'flex',
                                gap: 16,
                                padding: 14,
                                borderRadius: 10,
                                background: '#ffffff',
                                border: '1px solid #f0f0f0',
                            }}
                        >
                            <div style={{ flex: 1 }}>
                                <div style={{ fontSize: 12, fontWeight: 600, color: '#666', marginBottom: 4 }}>
                                    Thông tin lớp học phần
                                </div>
                                <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, fontSize: 13 }}>
                                    <Tag color="blue" style={{ border: 'none', marginRight: 0 }}>
                                        {selectedSectionForSupport.code}
                                    </Tag>
                                    <span>
                                        {selectedSectionForSupport.courseOffering?.course?.code} -{' '}
                                        {selectedSectionForSupport.courseOffering?.course?.name}
                                    </span>
                                </div>
                                {selectedSectionForSupport._bulkIds && selectedSectionForSupport._bulkIds.length > 1 && (
                                    <div style={{ marginTop: 6, fontSize: 12, color: '#8c8c8c' }}>
                                        Áp dụng cho{' '}
                                        <strong>{selectedSectionForSupport._bulkIds.length}</strong> lớp học phần đang được chọn.
                                    </div>
                                )}
                            </div>
                            <div
                                style={{
                                    flexBasis: 220,
                                    padding: 10,
                                    borderRadius: 8,
                                    background: '#fff7e6',
                                    border: '1px dashed #ffd591',
                                    fontSize: 12,
                                    color: '#8c6b39',
                                }}
                            >
                                <div style={{ fontWeight: 600, marginBottom: 4 }}>
                                    Lưu ý luồng xử lý
                                </div>
                                <div>
                                    Yêu cầu sẽ được gửi đến <strong>P.ĐT</strong>, sau đó chuyển tiếp cho{' '}
                                    <strong>Khoa quản lý chuyên môn</strong> để phân công giảng viên phù hợp.
                                </div>
                            </div>
                        </div>

                        <div
                            style={{
                                padding: 14,
                                borderRadius: 10,
                                background: '#ffffff',
                                border: '1px solid #f0f0f0',
                            }}
                        >
                            <div style={{ marginBottom: 6, fontWeight: 500, fontSize: 13 }}>
                                Ghi chú cho P.ĐT / Khoa chuyên môn <span style={{ fontWeight: 400, color: '#999' }}>(tùy chọn)</span>
                            </div>
                            <div style={{ fontSize: 12, color: '#999', marginBottom: 6 }}>
                                Ví dụ: “Khoa không có giảng viên có chuyên môn về học phần này”, “Đề xuất mượn giảng viên từ Khoa CNTT”…
                            </div>
                            <Input.TextArea
                                rows={4}
                                placeholder="Mô tả ngắn gọn lý do và bối cảnh cần hỗ trợ giảng viên..."
                                value={supportComment}
                                onChange={(e) => setSupportComment(e.target.value)}
                                maxLength={500}
                                showCount
                            />
                        </div>
                    </Space>
                )}
            </Modal>

            {(!currentFacultyId && !isFaculty) ? (
                <Alert title="Chọn Khoa/Viện để xem danh sách lớp học phần cần phân công" type="warning" showIcon style={{ marginTop: 16 }} />
            ) : (
                <Table
                    dataSource={filteredSections}
                    columns={columns}
                    rowKey="id"
                    loading={loading}
                    size="middle"
                    rowSelection={rowSelection}
                    pagination={{ pageSize: 10, placement: 'bottomRight', showTotal: (t) => `Tổng ${t} lớp` }}
                />
            )}
        </div>
    );
};

export default TeachingAssignmentManagement;
